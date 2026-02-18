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
import net.minecraft.network.play.client.C0BPacketEntityAction;

import java.util.Comparator;
import java.util.List;

public class Autoblock extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty range = new PercentProperty("Range", 45);
    public final IntProperty maxHurtTime = new IntProperty("Max Hurt Time", 8, 0, 10);
    public final IntProperty maxHoldDuration = new IntProperty("Max Hold Ticks", 5, 1, 20);
    public final IntProperty maxLagDuration = new IntProperty("Lag Comp Ticks", 3, 0, 10);
    public final BooleanProperty onlySword = new BooleanProperty("Only Sword", true);
    public final BooleanProperty onlyWhenSwinging = new BooleanProperty("Only Swinging", true);

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
        float realRange = range.getValue().floatValue
