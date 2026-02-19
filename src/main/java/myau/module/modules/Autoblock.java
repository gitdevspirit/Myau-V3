package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ----------------------------------------------------------------
    // SETTINGS (visible in GUI)
    // ----------------------------------------------------------------
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 10,
            "NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK",
            "INTERACT", "SWAP", "LEGIT", "FAKE", "LAGRANGE",
            "GRIM", "SLINKY"
    ));

    public final SliderSetting blockRange   = register(new SliderSetting("Block Range",   6.0,   3.0,   8.0,   0.1));
    public final SliderSetting minCPS       = register(new SliderSetting("Min APS",       6.0,   1.0,  20.0,   1.0));
    public final SliderSetting maxCPS       = register(new SliderSetting("Max APS",       9.0,   1.0,  20.0,   1.0));
    public final SliderSetting releaseDelay = register(new SliderSetting("Release Delay", 2.0,   1.0,   5.0,   0.5));

    public final BooleanSetting requirePress  = register(new BooleanSetting("Require Press",  false));
    public final BooleanSetting requireAttack = register(new BooleanSetting("Require Attack", true));
    public final BooleanSetting autoRelease   = register(new BooleanSetting("Auto Release",   true));

    // SLINKY settings
    public final SliderSetting  slinkyMaxHold      = register(new SliderSetting("Slinky Max Hold",     4.0,   1.0,  10.0,  0.5));
    public final SliderSetting  slinkyMaxHurt      = register(new SliderSetting("Slinky Max Hurt",     8.0,   1.0,  10.0,  0.5));
    public final SliderSetting  slinkyLagChance    = register(new SliderSetting("Slinky Lag Chance",  35.0,   0.0, 100.0,  1.0));
    public final SliderSetting  slinkyLagDuration  = register(new SliderSetting("Slinky Lag Duration",150.0,  0.0, 500.0, 10.0));
    public final BooleanSetting slinkyReblockInstant = register(new BooleanSetting("Slinky Reblock Instant", true));
    public final BooleanSetting slinkyRightClick   = register(new BooleanSetting("Slinky Right Click", true));
    public final BooleanSetting slinkyLeftClick    = register(new BooleanSetting("Slinky Left Click",  false));
    public final BooleanSetting slinkyDamaged      = register(new BooleanSetting("Slinky Damaged",     true));

    // ----------------------------------------------------------------
    // INTERNAL STATE
    // ----------------------------------------------------------------
    private boolean blockingState  = false;
    private boolean fakeBlockState = false;
    private boolean isBlocking     = false;
    private boolean blinkReset     = false;
    private int     blockTick      = 0;
    private long    blockDelayMS   = 0L;
    private int     releaseTick    = 0;
    private int     lastAttackTick = 0;

    public Autoblock() {
        super("Autoblock", false);
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------
    private int   getMode()           { return mode.getIndex(); }
    private float getBlockRange()     { return (float) blockRange.getValue(); }
    private float getMinCPS()         { return (float) minCPS.getValue(); }
    private float getMaxCPS()         { return (float) maxCPS.getValue(); }
    private float getReleaseDelay()   { return (float) releaseDelay.getValue(); }

    private long getBlockDelay() {
        return (long)(1000.0F / RandomUtil.nextLong(
                (long) getMinCPS(),
                (long) getMaxCPS()
        ));
    }

    private boolean canAutoblock() {
        if (!ItemUtil.isHoldingSword()) return false;
        if (requirePress.getValue() && !PlayerUtil.isUsingItem()) return false;
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (requireAttack.getValue() && !ka.isAttackAllowed()) return false;
        return true;
    }

    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream().anyMatch(
                entity -> entity instanceof net.minecraft.entity.EntityLivingBase
                        && RotationUtil.distanceToEntity((net.minecraft.entity.EntityLivingBase) entity)
                        <= getBlockRange()
        );
    }

    private void startBlock(ItemStack stack) {
        if (stack == null) return;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        this.blockingState = true;
        this.releaseTick = (int) getReleaseDelay();
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
        this.releaseTick   = 0;
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++)
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null)
                return i;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (i != currentSlot && stack != null && !stack.hasDisplayName())
                return i;
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    // ----------------------------------------------------------------
    // MAIN UPDATE
    // ----------------------------------------------------------------
    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            resetState();
            return;
        }

        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (ka.isAttackAllowed()) this.lastAttackTick = 0;
        else this.lastAttackTick++;

        if (event.getType() == EventType.POST && this.blinkReset) {
            this.blinkReset = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true,  BlinkModules.AUTO_BLOCK);
        }

        if (event.getType() != EventType.PRE) return;

        if (this.blockDelayMS > 0L) this.blockDelayMS -= 50L;

        if (autoRelease.getValue() && this.blockingState && this.releaseTick > 0) {
            this.releaseTick--;
            if (this.releaseTick <= 0) stopBlock();
        }

        boolean canBlock = this.canAutoblock() && this.hasValidTarget();
        if (!canBlock) {
            if (this.blockingState) stopBlock();
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking    = false;
            this.fakeBlockState = false;
            this.blockTick     = 0;
            return;
        }

        boolean swap = false;

        switch (getMode()) {

            case 0: // NONE
                this.isBlocking    = false;
                this.fakeBlockState = false;
                break;

            case 1: // VANILLA
                if (!this.blockingState) swap = true;
                this.isBlocking    = true;
                this.fakeBlockState = false;
                break;

            case 2: // SPOOF
                if (this.lastAttackTick <= 2) {
                    int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                    int slot = this.findEmptySlot(item);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                    swap = true;
                }
                this.isBlocking    = true;
                this.fakeBlockState = false;
                break;

            case 3: // HYPIXEL
                if (this.lastAttackTick > 3) {
                    switch (this.blockTick) {
                        case 0: swap = true; this.blockTick = 1; break;
                        case 1: if (this.blockDelayMS <= 50L) this.blockTick = 0; break;
                    }
                }
                this.isBlocking    = true;
                this.fakeBlockState = true;
                break;

            case 4: // BLINK
                if (this.lastAttackTick > 2) {
                    switch (this.blockTick) {
                        case 0:
                            swap = true;
                            this.blinkReset = true;
                            this.blockTick  = 1;
                            break;
                        case 1:
                            if (this.blockDelayMS <= 50L) this.blockTick = 0;
                            break;
                    }
                }
                this.isBlocking    = true;
                this.fakeBlockState = true;
                break;

            case 5: // INTERACT
                if (this.lastAttackTick <= 3) {
                    int current = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                    int empty   = this.findEmptySlot(current);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(empty));
                    ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(empty);
                    swap = true;
                }
                this.isBlocking    = true;
                this.fakeBlockState = true;
                break;

            case 6: // SWAP
                if (this.lastAttackTick <= 2) {
                    int cur       = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                    int emptySlot = this.findEmptySlot(cur);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(emptySlot));
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(cur));
                    swap = true;
                }
                this.isBlocking    = true;
                this.fakeBlockState = true;
                break;

            case 7: // LEGIT
                if (this.lastAttackTick <= 1) swap = true;
                this.isBlocking    = true;
                this.fakeBlockState = false;
                break;

            case 8: // FAKE
                this.isBlocking    = false;
                this.fakeBlockState = true;
                break;

            case 9: // LAGRANGE
                int ping      = PingUtil.getPing();
                int lagWindow = Math.min(100, Math.max(30, ping));
                if (this.lastAttackTick <= 4) {
                    switch (this.blockTick) {
                        case 0:
                            swap = true;
                            this.blockDelayMS = lagWindow;
                            this.blockTick    = 1;
                            break;
                        case 1:
                            if (this.blockDelayMS <= 0L) this.blockTick = 2;
                            break;
                        case 2:
                            if (ka.isAttackAllowed()) this.blockTick = 0;
                            break;
                    }
                }
                this.isBlocking    = true;
                this.fakeBlockState = true;
                break;

            case 10: // GRIM
                if (this.lastAttackTick <= 1 && !this.blockingState) swap = true;
                this.isBlocking    = true;
                this.fakeBlockState = false;
                break;

            case 11: // SLINKY
                boolean allow = false;

                if (slinkyRightClick.getValue() && PlayerUtil.isUsingItem())                allow = true;
                if (slinkyLeftClick.getValue()  && mc.gameSettings.keyBindAttack.isKeyDown()) allow = true;
                if (slinkyDamaged.getValue()    && mc.thePlayer.hurtTime > 0)               allow = true;
                if (mc.thePlayer.hurtTime > (float) slinkyMaxHurt.getValue())               allow = false;

                if (!allow) {
                    if (this.blockingState) stopBlock();
                    this.isBlocking    = false;
                    this.fakeBlockState = false;
                    break;
                }

                if (RandomUtil.nextInt(0, 100) < (float) slinkyLagChance.getValue()) {
                    this.blockDelayMS = (long) slinkyLagDuration.getValue();
                }

                if (slinkyReblockInstant.getValue()) {
                    swap = !this.blockingState;
                } else {
                    if (this.blockDelayMS <= 0L) swap = true;
                }

                this.releaseTick = (int) slinkyMaxHold.getValue();
                this.isBlocking    = true;
                this.fakeBlockState = false;
                break;
        }

        if (swap && this.blockDelayMS <= 0L) {
            this.blockDelayMS += this.getBlockDelay() + RandomUtil.nextInt(20, 50);
            this.startBlock(mc.thePlayer.getHeldItem());
        }
    }

    private void resetState() {
        stopBlock();
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking    = false;
        this.fakeBlockState = false;
        this.blockTick     = 0;
        this.blockDelayMS  = 0L;
        this.releaseTick   = 0;
        this.lastAttackTick = 0;
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @Override
    public void onDisabled() {
        resetState();
    }

    @Override
    public String[] getSuffix() {
        return isBlocking ? new String[]{mode.getValue()} : new String[0];
    }
}
