package myau.module.modules;

import myau.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.Map;

public class BackTrack extends Module {

    private final Map<Integer, Vec3> realPositions = new HashMap<>();
    private Vec3 lastRealPos = null;
    private EntityLivingBase target = null;

    public BackTrack() {
        // Matches your Module constructor: name + enabled (default off)
        // If you want it enabled by default: super("BackTrack", true);
        // If hidden: super("BackTrack", false, true);
        super("BackTrack", false);
    }

    // Packet handler – call from your event system / Mixin
    public void onPacket(Packet<?> packet) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            // In 1.8.9 MCP: use func_149451_c() for entity ID
            Entity e = mc.theWorld.getEntityByID(p.func_149451_c());

            if (e != null) {
                int id = e.getEntityId();  // or p.func_149451_c()
                Vec3 pos = new Vec3(e.posX, e.posY, e.posZ);

                // Delta moves: func_149062_c() = deltaX, etc.
                pos = pos.addVector(
                        (double) p.func_149062_c() / 32.0,
                        (double) p.func_149061_d() / 32.0,
                        (double) p.func_149064_e() / 32.0
                );

                realPositions.put(id, pos);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;

            // Teleport: func_149451_c() = entityId, func_149449_d() = x, etc.
            realPositions.put(
                    p.func_149451_c(),
                    new Vec3(
                            (double) p.func_149449_d() / 32.0,
                            (double) p.func_149448_e() / 32.0,
                            (double) p.func_149446_f() / 32.0
                    )
            );
        }
    }

    // Update logic (hook to your tick/update event)
    public void onUpdate() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // Example closest target finder (improve with your actual combat target selector)
        this.target = null;
        double closest = Double.MAX_VALUE;

        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityLivingBase) {
                EntityLivingBase e = (EntityLivingBase) o;
                if (e != mc.thePlayer && !e.isDead) {
                    double dist = mc.thePlayer.getDistanceToEntity(e);
                    if (dist < closest) {
                        closest = dist;
                        this.target = e;
                    }
                }
            }
        }

        if (this.target != null) {
            Vec3 real = realPositions.get(this.target.getEntityId());

            if (real != null) {
                double distReal = mc.thePlayer.getDistanceSq(real.xCoord, real.yCoord, real.zCoord);
                double distCurrent = mc.thePlayer.getDistanceToEntity(this.target);

                if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime) {
                    this.lastRealPos = real;
                }

                // Example: backtrack if server pos differs significantly
                // if (distReal > distCurrent + 0.5) { ... extend hit range or log ... }
            }
        }
    }

    // No @Override needed – your Module has onDisabled() as hook
    public void onDisabled() {
        realPositions.clear();
        lastRealPos = null;
        target = null;
    }

    // Optional: onEnabled() if you need init logic
    // public void onEnabled() { ... }
}
