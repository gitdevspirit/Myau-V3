package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import myau.util.AttackData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class KillAura extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));

    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private long attackDelayMS = 0L;
    private int lastTickProcessed;

    private boolean isPlayerTarget(EntityLivingBase entity) {
        return entity != null && entity != mc.thePlayer;
    }

    public final ModeProperty mode;
    public final ModeProperty sort;
    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final ModeProperty showTarget;
    public final ModeProperty debugLog;

    public KillAura() {
        super("KillAura", false);

        this.mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
        this.sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});

        this.swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("fov", 360, 30, 360);
        this.minCPS = new IntProperty("min-aps", 14, 1, 20);
        this.maxCPS = new IntProperty("max-aps", 14, 1, 20);
        this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);

        this.rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT", "LOCK_VIEW"});
        this.moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
        this.smoothing = new PercentProperty("smoothing", 0);
        this.angleStep = new IntProperty("angle-step", 90, 30, 180);

        this.throughWalls = new BooleanProperty("through-walls", true);
        this.requirePress = new BooleanProperty("require-press", false);
        this.allowMining = new BooleanProperty("allow-mining", true);
        this.weaponsOnly = new BooleanProperty("weapons-only", true);
        this.allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("inventory-check", true);
        this.botCheck = new BooleanProperty("bot-check", true);
        this.players = new BooleanProperty("players", true);
        this.bosses = new BooleanProperty("bosses", false);
        this.mobs = new BooleanProperty("mobs", false);
        this.animals = new BooleanProperty("animals", false);
        this.golems = new BooleanProperty("golems", false);
        this.silverfish = new BooleanProperty("silverfish", false);
        this.teams = new BooleanProperty("teams", true);
        this.showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
        this.debugLog = new ModeProperty("debug-log", 0, new String[]{"NONE", "HEALTH"});
    }

    private long getAttackDelay() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) return false;

        if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue()
                    || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        return false;
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;

        if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {

            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) return false;
            if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) return false;

            AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
            if (autoHeal.isEnabled() && autoHeal.isSwitching()) return false;

            BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
            if (bedNuker.isEnabled() && bedNuker.isReady()) return false;

            if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;

            if (this.requirePress.getValue()) {
                return PlayerUtil.isAttacking();
            } else {
                return !this.allowMining.getValue()
                        || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK)
                        || !PlayerUtil.isAttacking();
            }
        }
        return false;
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) return false;
        if (entityLivingBase == mc.thePlayer || entityLivingBase == mc.thePlayer.ridingEntity) return false;
        if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) return false;
        if (entityLivingBase.deathTime > 0) return false;
        if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) return false;
        if (!this.throughWalls.getValue() && RotationUtil.rayTrace(entityLivingBase) != null) return false;

        if (entityLivingBase instanceof EntityOtherPlayerMP) {
            if (!this.players.getValue()) return false;
            if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) return false;
            if (this.teams.getValue() && TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) return false;
            if (this.botCheck.getValue() && TeamUtil.isBot((EntityPlayer) entityLivingBase)) return false;
            return true;
        }

        if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither)
            return this.bosses.getValue();

        if (entityLivingBase instanceof EntityMob || entityLivingBase instanceof EntitySlime) {
            if (entityLivingBase instanceof EntitySilverfish)
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            return this.mobs.getValue();
        }

        if (entityLivingBase instanceof EntityAnimal
                || entityLivingBase instanceof EntityBat
                || entityLivingBase instanceof EntitySquid
                || entityLivingBase instanceof EntityVillager)
            return this.animals.getValue();

        if (entityLivingBase instanceof EntityIronGolem)
            return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));

        return false;
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= this.attackRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= this.swingRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= this.attackRange.getValue();
    }

    private boolean performAttack(float yaw, float pitch) {
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing)
            return false;

        if (this.attackDelayMS > 0L)
            return false;

        this.attackDelayMS += this.getAttackDelay();
        mc.thePlayer.swingItem();

        if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
            return false;
        }

        AttackEvent event = new AttackEvent(this.target.getEntity());
        EventManager.call(event);

        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));

        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR)
            PlayerUtil.attackEntity(this.target.getEntity());

        this.hitRegistered = true;
        return true;
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled())
            return;

        if (event.getType() == EventType.PRE) {

            if (this.attackDelayMS > 0L)
                this.attackDelayMS -= 50L;

            boolean attack = this.target != null && this.canAttack();
            boolean attacked = false;

            if (attack && this.isBoxInSwingRange(this.target.getBox())) {

                if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                    float[] rotations = RotationUtil.getRotationsToBox(
                            this.target.getBox(),
                            event.getYaw(),
                            event.getPitch(),
                            (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                            (float) this.smoothing.getValue() / 100.0F
                    );

                    event.setRotation(rotations[0], rotations[1], 1);

                    if (this.rotations.getValue() == 3)
                        Myau.rotationManager.setRotation(rotations[0], rotations[1], 1, true);

                    if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3)
                        event.setPervRotation(rotations[0], 1);
                }

                attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled())
            return;

        switch (event.getType()) {
            case PRE:
                if (this.target == null
                        || !this.isValidTarget(this.target.getEntity())
                        || !this.isBoxInAttackRange(this.target.getBox())
                        || !this.isBoxInSwingRange(this.target.getBox())
                        || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {

                    this.target = this.findTarget();
                    this.timer.reset();
                }
                break;
            case POST:
                break;
        }
    }

    private AttackData findTarget() {
        ArrayList<AttackData> targets = new ArrayList<>();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;

            if (!this.isValidTarget(living)) continue;

            targets.add(new AttackData(living));  // Use existing 1-arg constructor
        }

        if (targets.isEmpty()) return null;

        // Sort based on selected mode - compute distance/angle on-the-fly
        switch (this.sort.getValue()) {
            case 0: // DISTANCE
                targets.sort((a, b) -> Double.compare(
                        RotationUtil.distanceToBox(a.getBox()),
                        RotationUtil.distanceToBox(b.getBox())
                ));
                break;
            case 1: // HEALTH
                targets.sort((a, b) -> Float.compare(a.getEntity().getHealth(), b.getEntity().getHealth()));
                break;
            case 2: // HURT_TIME
                targets.sort((a, b) -> Integer.compare(a.getEntity().hurtTime, b.getEntity().hurtTime));
                break;
            case 3: // FOV
                targets.sort((a, b) -> Float.compare(
                        RotationUtil.angleToEntity(a.getEntity()),
                        RotationUtil.angleToEntity(b.getEntity())
                ));
                break;
        }

        return targets.get(0);
    }
}
