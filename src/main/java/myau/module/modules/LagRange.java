package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import myau.util.AttackData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldSettings.GameType;

import java.util.ArrayList;

public class LagRange extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final TimerUtil attackTimer = new TimerUtil();
    private AttackData target = null;

    // Range
    public final SliderSetting   range        = new SliderSetting("Range",        10.0, 3.0, 100.0, 0.5);
    public final SliderSetting   fov          = new SliderSetting("FOV",           360,   30,   360,   1);

    // CPS
    public final SliderSetting   minCPS       = new SliderSetting("Min CPS",        4,    1,    20,   1);
    public final SliderSetting   maxCPS       = new SliderSetting("Max CPS",        8,    1,    20,   1);

    // Behaviour
    public final BooleanSetting  weaponsOnly  = new BooleanSetting("Weapons Only",  true);
    public final BooleanSetting  allowTools   = new BooleanSetting("Allow Tools",   false);
    public final BooleanSetting  botCheck     = new BooleanSetting("Bot Check",     true);
    public final BooleanSetting  teams        = new BooleanSetting("Teams",         true);
    public final BooleanSetting  throughWalls = new BooleanSetting("Through Walls", true);

    // Display
    public final DropdownSetting showPosition = new DropdownSetting("Show Position", 0, "NONE", "DEFAULT", "HUD");

    public LagRange() {
        super("LagRange", false);
        register(range);
        register(fov);
        register(minCPS);
        register(maxCPS);
        register(weaponsOnly);
        register(allowTools);
        register(botCheck);
        register(teams);
        register(throughWalls);
        register(showPosition);
    }

    @Override
    public void onDisabled() {
        target = null;
        attackTimer.reset();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getAttackDelay() {
        int min = Math.min((int) minCPS.getValue(), (int) maxCPS.getValue());
        int max = Math.max((int) minCPS.getValue(), (int) maxCPS.getValue());
        return 1000L / RandomUtil.nextLong(min, max);
    }

    public EntityLivingBase getTarget() {
        return target != null ? target.getEntity() : null;
    }

    private boolean canAttack() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (weaponsOnly.getValue()
                && !ItemUtil.hasRawUnbreakingEnchant()
                && !(allowTools.getValue() && ItemUtil.isHoldingTool())) {
            return false;
        }
        return true;
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null) return false;
        if (!mc.theWorld.loadedEntityList.contains(entity)) return false;
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
        if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) return false;
        if (entity.deathTime > 0 || entity.getHealth() <= 0) return false;

        // LagRange only targets players
        if (!(entity instanceof EntityOtherPlayerMP)) return false;

        EntityPlayer player = (EntityPlayer) entity;

        // Distance check using range slider (no vanilla reach limit)
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        if (distance > range.getValue()) return false;

        // FOV check
        if (fov.getValue() < 360 && RotationUtil.angleToEntity(entity) > (float) fov.getValue() / 2.0f) return false;

        // Wall check
        if (!throughWalls.getValue() && !mc.thePlayer.canEntityBeSeen(entity)) return false;

        // Friend / team / bot
        if (TeamUtil.isFriend(player)) return false;
        if (teams.getValue() && TeamUtil.isSameTeam(player)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(player)) return false;

        return true;
    }

    private AttackData findTarget() {
        if (mc.theWorld == null) return null;

        ArrayList<AttackData> targets = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!isValidTarget(living)) continue;
            targets.add(new AttackData(living));
        }

        if (targets.isEmpty()) return null;

        // Sort by nearest first
        targets.sort((a, b) -> Double.compare(
                mc.thePlayer.getDistanceToEntity(a.getEntity()),
                mc.thePlayer.getDistanceToEntity(b.getEntity())));

        return targets.get(0);
    }

    private void performAttack() {
        if (target == null) return;
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return;

        EntityLivingBase targetEntity = target.getEntity();

        // Swing arm client-side
        mc.thePlayer.swingItem();

        // Fire attack event so other modules can hook in
        AttackEvent event = new AttackEvent(targetEntity);
        EventManager.call(event);

        // Sync held item, then send raw attack packet — no range gate (that's the point)
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(targetEntity, Action.ATTACK));

        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(targetEntity);
        }

        attackTimer.reset();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Refresh target every tick
        if (target == null || !isValidTarget(target.getEntity())) {
            target = findTarget();
        }

        if (target != null && canAttack()) {
            performAttack();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || target == null) return;

        int modeIndex = showPosition.getIndex();
        if (modeIndex == 0) return; // NONE

        EntityLivingBase t = target.getEntity();
        if (t == null) return;

        if (modeIndex == 1) { // DEFAULT — draw ESP box at target
            IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
            double rx = rm.getRenderPosX();
            double ry = rm.getRenderPosY();
            double rz = rm.getRenderPosZ();

            AxisAlignedBB bb = t.getEntityBoundingBox().offset(-rx, -ry, -rz);
            RenderUtil.drawBoundingBox(bb, 0, 191, 255, 200, 1.5f);
        }
        // modeIndex == 2 (HUD) is handled by your HUD renderer via getTarget()
    }
}