package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.IntProperty;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.EnumHand;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AutoBlock - Auto right-click blocks while allowing KillAura to continue attacking.
 * Uses packet blocking to avoid canceling left-clicks.
 */
public class Autoblock extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty range = new PercentProperty("Range", 45); // 0–100 → 0–10 blocks
    public final IntProperty maxHurtTime = new IntProperty("Max Hurt Time", 8, 0, 10);
    public final IntProperty maxHoldDuration = new IntProperty("Max Hold Ticks", 5, 1, 20);
    public final IntProperty maxLagDuration = new IntProperty("Lag Comp Ticks", 3, 0, 10);
    public final BooleanProperty onlySword = new BooleanProperty("Only Sword", true);
    public final BooleanProperty onlyWhenSwinging = new BooleanProperty("Only Swinging", true);
    public final BooleanProperty allowAttacks = new BooleanProperty("Allow Attacks", true); // NEW: keep KillAura hitting

    private int blockTicks = 0;
    private boolean isBlocking = false;

    public Autoblock() {
        super("Autoblock", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled()) {
            stopBlocking();
            return;
        }

        if (mc.thePlayer == null || !mc.thePlayer.onGround) {
            stopBlocking();
            return;
        }

        if (onlySword.getValue() && !isHoldingSword()) {
            stopBlocking();
            return;
        }

        EntityLivingBase target = getClosestEnemy();
        if (target == null) {
            stopBlocking();
            return;
        }

        double distance = mc.thePlayer.getDistanceToEntity(target);
        float realRange = range.getValue().floatValue() / 10f;

        if (distance > realRange) {
            stopBlocking();
            return;
        }

        if (onlyWhenSwinging.getValue() && target.swingProgressInt <= 0) {
            stopBlocking();
            return;
        }

        if (mc.thePlayer.hurtTime > maxHurtTime.getValue()) {
            stopBlocking();
            return;
        }

        // Start/continue blocking
        if (blockTicks < maxHoldDuration.getValue()) {
            startBlocking();
            blockTicks++;
        }

        // Lag compensation
        if (blockTicks > 0 && distance > realRange + 0.5) {
            blockTicks--;
            if (blockTicks > 0) {
                startBlocking();
            }
        }
    }

    private boolean isHoldingSword() {
        return mc.thePlayer.getCurrentEquippedItem() != null
                && mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    private EntityLivingBase getClosestEnemy() {
        List<Entity> entities = mc.theWorld.loadedEntityList;

        return entities.stream()
                .filter(e -> e instanceof EntityLivingBase
                        && e != mc.thePlayer
                        && !(e instanceof EntityPlayer && TeamUtil.isFriend((EntityPlayer) e))
                        && mc.thePlayer.canEntityBeSeen(e))
                .map(e -> (EntityLivingBase) e)
                .min(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                .orElse(null);
    }

    private void startBlocking() {
        if (!isBlocking && mc.thePlayer.getCurrentEquippedItem() != null) {
            // Use packet for blocking — doesn't cancel attacks as much
            mc.thePlayer.sendQueue.addToSendQueue(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
            isBlocking = true;
        }
    }

    private void stopBlocking() {
        if (isBlocking) {
            mc.thePlayer.stopUsingItem();
            isBlocking = false;
            blockTicks = 0;
        }
    }

    @Override
    public void onDisable() {
        stopBlocking();
        blockTicks = 0;
    }

    @Override
    public String[] getSuffix() {
        return isBlocking ? new String[]{"BLOCKING"} : new String[0];
    }
}
