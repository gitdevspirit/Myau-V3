package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;

/**
 * Eagle — speed-optimised rewrite
 *
 * Philosophy:
 *   Sneak for exactly ONE tick the moment a simulated step would leave solid
 *   ground, then immediately release. No movement slowdown is applied ever.
 *   This gives the server just enough sneak signal to register edge placement
 *   while keeping the player at full bridging speed the entire time.
 *
 * Settings:
 *   edge-offset   — how close to the edge (in blocks) before we sneak.
 *                   Lower = less frequent sneaking. 0.1 is the sweet spot.
 *   blocks-only   — only run while holding a placeable block (recommended on).
 *   pitch-bypass  — disable edge sneak while looking down (active bridging).
 *                   Turn OFF for ninja/godbridge style where you look straight.
 */
public class Eagle extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty   edgeOffset   = new FloatProperty ("edge-offset",   0.1f, 0.0f, 1.0f);
    public final BooleanProperty blocksOnly   = new BooleanProperty("blocks-only",  true);
    /**
     * When true, the edge check is skipped while the player is looking down
     * (pitch > 60). During normal bridging you aim down to place; sneaking
     * then only slows you down unnecessarily.
     */
    public final BooleanProperty pitchBypass  = new BooleanProperty("pitch-bypass", true);

    // Sneak lasts exactly 1 tick — this flag triggers the immediate release.
    private boolean sneakedLastTick = false;

    public Eagle() {
        super("Eagle", false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Edge detection
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isNearEdge() {
        float offset = edgeOffset.getValue();
        if (offset <= 0f)                                        return false;
        if (!mc.thePlayer.onGround)                              return false;
        if (blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) return false;

        // pitch-bypass: actively bridging (looking down) — don't interfere.
        if (pitchBypass.getValue() && mc.thePlayer.rotationPitch > 60.0f) return false;

        double px  = mc.thePlayer.posX;
        double py  = mc.thePlayer.posY;
        double pz  = mc.thePlayer.posZ;

        // Simulate one tick of horizontal movement from current inputs
        double mx  = mc.thePlayer.motionX;
        double mz  = mc.thePlayer.motionZ;
        float  fwd = mc.thePlayer.movementInput.moveForward;
        float  str = mc.thePlayer.movementInput.moveStrafe;
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        mx += (-Math.sin(yaw) * fwd + Math.cos(yaw) * str) * 0.02;
        mz += ( Math.cos(yaw) * fwd + Math.sin(yaw) * str) * 0.02;

        double simX   = px + mx;
        double simZ   = pz + mz;
        int    floorY = (int) Math.floor(py) - 1; // one block below feet

        // Check the four AABB corners expanded by offset
        double w = 0.3 + offset;
        return cornerIsAir(simX + w, floorY, simZ + w)
            || cornerIsAir(simX + w, floorY, simZ - w)
            || cornerIsAir(simX - w, floorY, simZ + w)
            || cornerIsAir(simX - w, floorY, simZ - w);
    }

    private boolean cornerIsAir(double x, int y, double z) {
        try {
            BlockPos pos   = new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
            Block    block = mc.theWorld.getChunkFromBlockCoords(pos).getBlock(pos);
            return block == null || block instanceof BlockAir;
        } catch (Exception e) {
            return false; // unloaded chunk — don't sneak into void
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Event handler
    // ─────────────────────────────────────────────────────────────────────────

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;

        // Previous tick was a sneak tick — release immediately, no cooldown.
        if (sneakedLastTick) {
            sneakedLastTick = false;
            mc.thePlayer.movementInput.sneak = false;
            return;
        }

        if (isNearEdge()) {
            // Sneak this tick only.
            // Intentionally NOT multiplying moveForward/moveStrafe:
            // we want zero speed penalty — the sneak flag is purely for the
            // server-side placement hitbox, not to slow the player down.
            mc.thePlayer.movementInput.sneak = true;
            sneakedLastTick = true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onDisabled() {
        sneakedLastTick = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String[] getSuffix() {
        return new String[]{ String.format("%.2fb", edgeOffset.getValue()) };
    }
}
