package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.module.ModuleCategory;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.SliderProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Category: Combat
 *
 * Modes
 * ──────
 * HYPIXEL  – sends a real C08 place packet then immediately cancels with C07,
 *            visually blocks on the server without placing a block. Works on
 *            Hypixel and most anti-cheat patches that require a real packet.
 *
 * LEGIT    – uses mc.rightClickMouse() / simulates a right-click every tick
 *            when blocking conditions are met. Looks most human but is slower
 *            to engage since it requires a proper item-use tick.
 *
 * LAG      – after unblocking, queues a C08 packet but withholds it for a
 *            random duration (up to "Max lag duration") so the server sees the
 *            sword as blocked for longer than the client holds it.
 *
 * 
 */
public class Autoblock extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ═══════════════════════════════════════════════════════════════════════
    //  Properties
    // ═══════════════════════════════════════════════════════════════════════

    // ── Mode ──────────────────────────────────────────────────────────────
    public final ModeProperty mode = new ModeProperty(
            "mode", 1,
            new String[]{"Hypixel", "Legit", "Lag"});

    // ── Range ─────────────────────────────────────────────────────────────
    /** Distance from nearest target at which we begin trying to block. */
    public final SliderProperty range = new SliderProperty(
            "range", 3.2, 1.0, 6.0, 0.1);

    // ── Timing ────────────────────────────────────────────────────────────
    /**
     * Block this many ms BEFORE the hurt timer expires.
     * Rule of thumb: ping + 100 ms.
     */
    public final SliderProperty maxHurtTime = new SliderProperty(
            "max-hurt-time", 210.0, 0.0, 500.0, 5.0);

    /**
     * How long (ms) to stay blocked per engagement.
     * Keep at least 50 ms below max-hurt-time.
     */
    public final SliderProperty maxHoldDuration = new SliderProperty(
            "max-hold-duration", 150.0, 50.0, 500.0, 5.0);

    // ── Animation ─────────────────────────────────────────────────────────
    /** Always render the sword as blocked client-side (cosmetic only). */
    public final BooleanProperty forceAnimation = new BooleanProperty(
            "force-animation", false);

    /** Only show the force-animation when a target is within range. */
    public final BooleanProperty animOnlyInRange = new BooleanProperty(
            "anim-only-in-range", true);

    // ── Lag sub-settings ──────────────────────────────────────────────────
    /**
     * Probability (0–100 %) that a lag packet is queued after unblocking.
     * Set to 0 to disable lag entirely.
     */
    public final PercentProperty lagChance = new PercentProperty(
            "lag-chance", 75);

    /**
     * Maximum time (ms) to hold the lag packet before releasing it.
     */
    public final SliderProperty maxLagDuration = new SliderProperty(
            "max-lag-duration", 120.0, 20.0, 400.0, 10.0);

    /**
     * When true, the lag is cancelled immediately if the player tries to
     * attack so hits are never delayed.
     */
    public final BooleanProperty preventDelayAttacks = new BooleanProperty(
            "no-attack-delay", true);

    /**
     * Re-engage blocking directly after the lag packet fires.
     * Recommended only when lag-chance is at or near 100 %.
     */
    public final BooleanProperty blockAgainAfterLag = new BooleanProperty(
            "block-again-after-lag", false);

    // ── Conditions ────────────────────────────────────────────────────────
    /** Only block while LMB is held. */
    public final BooleanProperty requireLMB = new BooleanProperty(
            "require-lmb", false);

    /** Only block while RMB is held. */
    public final BooleanProperty requireRMB = new BooleanProperty(
            "require-rmb", false);

    /** Only block after recently taking damage. */
    public final BooleanProperty requireDamaged = new BooleanProperty(
            "require-damaged", false);

    // ═══════════════════════════════════════════════════════════════════════
    //  Internal state
    // ═══════════════════════════════════════════════════════════════════════

    /** Whether we are currently in a blocking engagement. */
    private boolean blocking        = false;

    /** Timestamp when we started the current block engagement (ms). */
    private long    blockStartTime  = 0L;

    /** Whether a lag packet is currently queued/pending. */
    private boolean lagging         = false;

    /** Timestamp when the lag packet was queued (ms). */
    private long    lagStartTime    = 0L;

    /** Chosen random duration for the current lag window (ms). */
    private long    lagDuration     = 0L;

    /** Whether the player took damage recently (within ~10 ticks). */
    private int     damagedTicks    = 0;

    /** Whether an attack event fired this tick (used by no-attack-delay). */
    private boolean attackedThisTick = false;

    // ═══════════════════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════════════════

    public AutoBlock() {
        super("AutoBlock", ModuleCategory.COMBAT, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns true if the player is holding a sword or axe. */
    private boolean isHoldingWeapon() {
        if (mc.thePlayer == null) return false;
        net.minecraft.item.ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        return held.getItem() instanceof ItemSword || held.getItem() instanceof ItemTool;
    }

    /** Returns the nearest valid (alive, enemy) player within range, or null. */
    private EntityPlayer getNearestTarget() {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        double rangeVal = range.getInput();
        EntityPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        List<EntityPlayer> players = mc.theWorld.playerEntities.stream()
                .filter(p -> p != mc.thePlayer
                        && p.deathTime == 0
                        && !p.isDead
                        && TeamUtil.isTarget(p))
                .collect(Collectors.toList());
        for (EntityPlayer p : players) {
            double dist = mc.thePlayer.getDistanceToEntity(p);
            if (dist <= rangeVal && dist < nearestDist) {
                nearest = p;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    /**
     * True if hurt-timer conditions mean a hit could land soon.
     * hurtResistantTime counts down from 20 (one second) in ticks; each tick
     * is ~50 ms. We convert to ms and check against the user's threshold.
     */
    private boolean isAboutToTakeDamage() {
        if (mc.thePlayer == null) return false;
        // hurtResistantTime: 0 = can take full damage right now
        //                   20 = just took damage (full immunity)
        int hurtTicks = mc.thePlayer.hurtResistantTime;
        long hurtMs   = hurtTicks * 50L; // ticks → ms
        // We want to block when the cooldown is BELOW our threshold
        // i.e. they can hurt us soon (hurtMs < maxHurtTime)
        return hurtMs < (long) maxHurtTime.getInput();
    }

    /** Returns true if all user-configured conditions are satisfied. */
    private boolean conditionsMet() {
        if (requireLMB.getValue()     && !Mouse.isButtonDown(0))  return false;
        if (requireRMB.getValue()     && !Mouse.isButtonDown(1))  return false;
        if (requireDamaged.getValue() && damagedTicks <= 0)       return false;
        return true;
    }

    /** Sends the server a block packet without placing an actual block.
     *  This is the Hypixel-compatible method. */
    private void sendBlockPacket() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;
        mc.thePlayer.sendQueue.addToSendQueue(
                new C08PacketPlayerBlockPlacement(
                        new BlockPos(-1, -1, -1),
                        255,
                        mc.thePlayer.getHeldItem(),
                        0.0F, 0.0F, 0.0F));
    }

    /** Sends the packet that cancels the block from the server's perspective. */
    private void sendUnblockPacket() {
        if (mc.thePlayer == null) return;
        mc.thePlayer.sendQueue.addToSendQueue(
                new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN));
    }

    /** Starts a client-side block using the appropriate method for the current mode. */
    private void startBlocking() {
        if (blocking) return;
        blocking       = true;
        blockStartTime = System.currentTimeMillis();

        switch (mode.getValue()) {
            case 0: // Hypixel
            case 2: // Lag  (uses same engage method; lag fires on release)
                sendBlockPacket();
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), 72000);
                break;
            case 1: // Legit — simulate a right-click press
                mc.rightClickMouse();
                break;
        }
    }

    /** Stops blocking and optionally queues a lag packet. */
    private void stopBlocking(boolean allowLag) {
        if (!blocking) return;
        blocking = false;

        boolean doLag = allowLag
                && mode.getValue() == 2           // Lag mode
                && lagChance.getValue() > 0
                && RandomUtils.nextInt(0, 100) < lagChance.getValue();

        switch (mode.getValue()) {
            case 0: // Hypixel — just release
                mc.thePlayer.clearItemInUse();
                sendUnblockPacket();
                break;
            case 1: // Legit — release use-item key state
                mc.thePlayer.clearItemInUse();
                break;
            case 2: // Lag
                if (doLag) {
                    // Withhold the unblock packet — start lag window
                    lagging     = true;
                    lagStartTime = System.currentTimeMillis();
                    lagDuration  = (long) RandomUtils.nextDouble(
                            20.0,
                            maxLagDuration.getInput());
                    // Client-side: clear use so animation resets on client only
                    mc.thePlayer.clearItemInUse();
                } else {
                    mc.thePlayer.clearItemInUse();
                    sendUnblockPacket();
                }
                break;
        }
    }

    /** Flushes the pending lag packet (sends the server the unblock). */
    private void flushLag(boolean reblockAfter) {
        if (!lagging) return;
        lagging = false;
        sendUnblockPacket();
        if (reblockAfter && blockAgainAfterLag.getValue()) {
            startBlocking();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Events
    // ═══════════════════════════════════════════════════════════════════════

    @EventTarget(Priority.NORMAL)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null)      return;

        attackedThisTick = false; // reset each tick; AttackEvent sets it during the tick

        // Decay "recently damaged" counter
        if (damagedTicks > 0) damagedTicks--;

        // ── Lag window management ─────────────────────────────────────────
        if (lagging) {
            long elapsed = System.currentTimeMillis() - lagStartTime;
            boolean timeout      = elapsed >= lagDuration;
            boolean attackCancel = preventDelayAttacks.getValue() && attackedThisTick;
            if (timeout || attackCancel) {
                flushLag(true);
            }
            // While lagging, don't start a new block this tick
            return;
        }

        // ── Force animation (cosmetic client-side) ───────────────────────
        if (forceAnimation.getValue()) {
            boolean inRange = getNearestTarget() != null;
            if (!animOnlyInRange.getValue() || inRange) {
                if (mc.thePlayer.getHeldItem() != null
                        && mc.thePlayer.getItemInUseDuration() == 0) {
                    mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), 72000);
                }
            }
        }

        // ── Main block logic ──────────────────────────────────────────────
        if (!isHoldingWeapon())  { stopBlocking(true); return; }
        if (!conditionsMet())    { stopBlocking(true); return; }

        EntityPlayer target = getNearestTarget();
        boolean targetInRange = target != null;
        boolean shouldBlock   = targetInRange && isAboutToTakeDamage();

        if (shouldBlock) {
            long heldMs = blocking ? (System.currentTimeMillis() - blockStartTime) : 0L;
            if (!blocking) {
                startBlocking();
            } else if (heldMs >= (long) maxHoldDuration.getInput()) {
                // Held long enough — release and let lag logic decide
                stopBlocking(true);
            }
        } else {
            stopBlocking(true);
        }
    }

    /**
     * Listen for outgoing attack packets so "prevent delaying attacks" can
     * flush the lag window immediately when the player swings.
     */
    @EventTarget(Priority.HIGH)
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        attackedThisTick = true;
        if (preventDelayAttacks.getValue() && lagging) {
            flushLag(false);
        }
    }

    /**
     * Track incoming damage packets to populate the "require-damaged" condition.
     */
    @EventTarget(Priority.NORMAL)
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == EventType.PRE) return; // only care about inbound
        // S19PacketEntityStatus with data == 2 means entity hurt
        if (event.getPacket() instanceof net.minecraft.network.play.server.S19PacketEntityStatus) {
            net.minecraft.network.play.server.S19PacketEntityStatus pkt =
                    (net.minecraft.network.play.server.S19PacketEntityStatus) event.getPacket();
            if (pkt.getOpCode() == 2
                    && mc.theWorld != null
                    && pkt.getEntity(mc.theWorld) == mc.thePlayer) {
                damagedTicks = 10; // ~500 ms window after taking damage
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void onDisabled() {
        if (blocking) {
            blocking = false;
            if (mc.thePlayer != null) mc.thePlayer.clearItemInUse();
            if (mode.getValue() != 1) sendUnblockPacket();
        }
        if (lagging) {
            lagging = false;
            sendUnblockPacket();
        }
        damagedTicks     = 0;
        attackedThisTick = false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Property validation
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "max-hold-duration":
                // Soft-warn: hold duration should stay below hurt time
                if (maxHoldDuration.getInput() >= maxHurtTime.getInput()) {
                    maxHoldDuration.setValue(maxHurtTime.getInput() - 50.0);
                }
                break;
            case "lag-chance":
                // lag-chance only meaningful in Lag mode but no hard clamp needed
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HUD suffix  →  shows mode tag
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public String[] getSuffix() {
        String[] modeNames = {"Hypixel", "Legit", "Lag"};
        String modeName = modeNames[mode.getValue()];
        String lagTag   = (mode.getValue() == 2 && lagChance.getValue() > 0)
                ? " " + lagChance.getValue() + "%" : "";
        return new String[]{ modeName + lagTag };
    }
}
