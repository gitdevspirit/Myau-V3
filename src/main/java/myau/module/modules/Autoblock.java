package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.Random;

public class Autoblock extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode;
    public final BooleanProperty requirePress;
    public final BooleanProperty requireAttack;
    public final FloatProperty blockRange;
    public final FloatProperty minCPS;
    public final FloatProperty maxCPS;

    private boolean blockingState = false;
    private boolean fakeBlockState = false;
    private boolean isBlocking = false;
    private boolean blinkReset = false;
    private int blockTick = 0;
    private long blockDelayMS = 0L;

    public Autoblock() {
        super("Autoblock", false);

        this.mode = new ModeProperty(
                "mode",
                2,
                new String[]{"NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK", "INTERACT", "SWAP", "LEGIT", "FAKE"}
        );
        this.requirePress = new BooleanProperty("require-press", false);
        this.requireAttack = new BooleanProperty("require-attack", false);
        this.blockRange = new FloatProperty("block-range", 6.0F, 3.0F, 8.0F);
        this.minCPS = new FloatProperty("min-aps", 8.0F, 1.0F, 20.0F);
        this.maxCPS = new FloatProperty("max-aps", 10.0F, 1.0F, 20.0F);
    }

    private long getBlockDelay() {
        return (long) (1000.0F / RandomUtil.nextLong(this.minCPS.getValue().longValue(), this.maxCPS.getValue().longValue()));
    }

    private boolean canAutoblock() {
        if (!ItemUtil.isHoldingSword()) return false;
        if (this.requirePress.getValue() && !PlayerUtil.isUsingItem()) return false;
        if (this.requireAttack.getValue() && !KillAura.isAttacking) return false;
        return true;
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof net.minecraft.entity.EntityLivingBase
                                && RotationUtil.distanceToEntity((net.minecraft.entity.EntityLivingBase) entity) <= this.blockRange.getValue()
                );
    }

    private void startBlock(ItemStack stack) {
        if (stack == null) return;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && !stack.hasDisplayName()) return i;
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            resetState();
            return;
        }

        if (event.getType() == EventType.POST && this.blinkReset) {
            this.blinkReset = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }

        if (event.getType() != EventType.PRE) return;

        if (this.blockDelayMS > 0L) this.blockDelayMS -= 50L;

        boolean canBlock = this.canAutoblock() && this.hasValidTarget();
        if (!canBlock) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
            this.blockTick = 0;
            return;
        }

        boolean swap = false;
        boolean blocked = false;

        switch (this.mode.getValue()) {
            case 0: // NONE
                if (PlayerUtil.isUsingItem()) {
                    this.isBlocking = true;
                    if (!this.blockingState && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        swap = true;
                    }
                } else {
                    this.isBlocking = false;
                    if (this.blockingState && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        this.stopBlock();
                    }
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.fakeBlockState = false;
                break;

            case 1: // VANILLA
                if (this.hasValidTarget()) {
                    if (!this.blockingState && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        swap = true;
                    }
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = true;
                    this.fakeBlockState = false;
                } else {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = false;
                    this.fakeBlockState = false;
                }
                break;

            case 2: // SPOOF
                if (this.hasValidTarget()) {
                    int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                    if (Myau.playerStateManager.digging
                            || Myau.playerStateManager.placing
                            || mc.thePlayer.inventory.currentItem != item
                            || this.blockingState && this.blockTick != 0
                            || this.blockDelayMS > 0L && this.blockDelayMS <= 50L) {
                        this.blockTick = 0;
                    } else {
                        int slot = this.findEmptySlot(item);
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                        swap = true;
                        this.blockTick = 1;
                    }
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = true;
                    this.fakeBlockState = false;
                } else {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = false;
                    this.fakeBlockState = false;
                }
                break;

            case 3: // HYPIXEL
                if (this.hasValidTarget()) {
                    if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        switch (this.blockTick) {
                            case 0:
                                if (!this.blockingState) swap = true;
                                blocked = true;
                                this.blockTick = 1;
                                break;
                            case 1:
                                if (this.blockingState) {
                                    if (Myau.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                                        int randomSlot = new Random().nextInt(9);
                                        while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                            randomSlot = new Random().nextInt(9);
                                        }
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                    }
                                    this.stopBlock();
                                }
                                if (this.blockDelayMS <= 50L) this.blockTick = 0;
                                break;
                            default:
                                this.blockTick = 0;
                        }
                    }
                    this.isBlocking = true;
                    this.fakeBlockState = true;
                } else {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = false;
                    this.fakeBlockState = false;
                }
                break;

            case 4: // BLINK
                if (this.hasValidTarget()) {
                    if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        switch (this.blockTick) {
                            case 0:
                                if (!this.blockingState) swap = true;
                                this.blinkReset = true;
                                this.blockTick = 1;
                                break;
                            case 1:
                                if (this.blockingState) {
                                    this.stopBlock();
                                }
                                if (this.blockDelayMS <= 50L) this.blockTick = 0;
                                break;
                            default:
                                this.blockTick = 0;
                        }
                    }
                    this.isBlocking = true;
                    this.fakeBlockState = true;
                } else {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.isBlocking = false;
                    this.fakeBlockState = false;
                }
                break;

            case 5: // INTERACT
                if (this.hasValidTarget()) {
                    int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                    if (mc.thePlayer.inventory.currentItem == item && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                        switch (this.blockTick) {
                            case 0:
                                if (!this.blockingState) swap = true;
                                this.blinkReset = true;
                                this.blockTick = 1;
                                break;
                            case 1:
                                if (this.blockingState) {
                                    int slot = this.findEmptySlot(item);
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                                    ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
                                }
                                if (this.blockDelay
