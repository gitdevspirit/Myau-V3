// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package myau.module.modules;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.MSTimer;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class BackTrack extends Module {
   private static final Minecraft mc = Minecraft.func_71410_x();

   public final BooleanProperty legit = new BooleanProperty("Legit", false);
   public final BooleanProperty releaseOnHit = new BooleanProperty("Release On Hit", true);
   public final IntProperty delay = new IntProperty("Delay", 400, 0, 1000, 10);
   public final FloatProperty hitRange = new FloatProperty("Range", 3.0F, 3.0F, 10.0F, 0.1F);
   public final BooleanProperty onlyIfNeeded = new BooleanProperty("Only If Needed", true);
   public final BooleanProperty esp = new BooleanProperty("ESP", true);
   public final ModeProperty espMode = new ModeProperty("ESP Mode", 0, new String[]{"Hitbox", "None"});

   private final Queue<Packet> incomingPackets = new LinkedList();
   private final Queue<Packet> outgoingPackets = new LinkedList();
   private final Map<Integer, Vec3> realPositions = new HashMap();
   private final MSTimer timer = new MSTimer();
   private KillAura killAura;
   private EntityLivingBase target;
   private Vec3 lastRealPos;

   public BackTrack() {
      super("BackTrack", false);
      register(legit);
      register(releaseOnHit);
      register(delay);
      register(hitRange);
      register(onlyIfNeeded);
      register(esp);
      register(espMode);
   }

   public void onEnabled() {
      Module m = Myau.moduleManager.getModule(KillAura.class);
      if (m instanceof KillAura) {
         this.killAura = (KillAura)m;
      }
      this.incomingPackets.clear();
      this.outgoingPackets.clear();
      this.realPositions.clear();
      this.lastRealPos = null;
      this.timer.reset();
   }

   public void onDisabled() {
      this.releaseAll();
      this.incomingPackets.clear();
      this.outgoingPackets.clear();
      this.realPositions.clear();
      this.lastRealPos = null;
   }

   @EventTarget
   public void onPacket(PacketEvent event) {
      if (this.isEnabled() && mc.field_71439_g != null && mc.field_71441_e != null && this.killAura != null) {
         Module scaffold = Myau.moduleManager.getModule(Scaffold.class);
         if (scaffold != null && scaffold.isEnabled()) {
            this.releaseAll();
            this.incomingPackets.clear();
            this.outgoingPackets.clear();
         } else {
            this.target = this.killAura.getTarget();
            if (event.getType() == EventType.RECEIVE) {
               this.handleIncoming(event);
            } else if (event.getType() == EventType.SEND) {
               this.handleOutgoing(event);
            }
         }
      }
   }

   private void handleIncoming(PacketEvent event) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof S14PacketEntity) {
         S14PacketEntity p = (S14PacketEntity)packet;
         Entity e = p.func_149065_a(mc.field_71441_e);
         if (e == null) {
            return;
         }
         int id = e.func_145782_y();
         Vec3 pos = (Vec3)this.realPositions.getOrDefault(id, new Vec3(0.0, 0.0, 0.0));
         this.realPositions.put(id, pos.func_72441_c((double)p.func_149062_c() / 32.0, (double)p.func_149061_d() / 32.0, (double)p.func_149064_e() / 32.0));
      }
      if (packet instanceof S18PacketEntityTeleport) {
         S18PacketEntityTeleport p = (S18PacketEntityTeleport)packet;
         this.realPositions.put(p.func_149451_c(), new Vec3((double)p.func_149449_d() / 32.0, (double)p.func_149448_e() / 32.0, (double)p.func_149446_f() / 32.0));
      }
      if (this.shouldQueue()) {
         if (this.blockIncoming(packet)) {
            this.incomingPackets.add(packet);
            event.setCancelled(true);
         }
      } else {
         this.releaseIncoming();
      }
   }

   private void handleOutgoing(PacketEvent event) {
      Packet<?> packet = event.getPacket();
      if ((Boolean)this.legit.getValue()) {
         if (this.shouldQueue()) {
            if (this.blockOutgoing(packet)) {
               this.outgoingPackets.add(packet);
               event.setCancelled(true);
            }
         } else {
            this.releaseOutgoing();
         }
      }
   }

   @EventTarget
   public void onUpdate(UpdateEvent e) {
      if (this.isEnabled() && mc.field_71439_g != null) {
         if (this.target != this.killAura.getTarget()) {
            this.releaseAll();
            this.lastRealPos = null;
         }
         if (this.target != null) {
            Vec3 real = (Vec3)this.realPositions.get(this.target.func_145782_y());
            if (real != null) {
               double distReal = mc.field_71439_g.func_70011_f(real.field_72450_a, real.field_72448_b, real.field_72449_c);
               double distCurrent = (double)mc.field_71439_g.func_70032_d(this.target);
               if (mc.field_71439_g.field_70738_aO > 0 && mc.field_71439_g.field_70737_aN == mc.field_71439_g.field_70738_aO) {
                  this.releaseAll();
               }
               if (distReal > (float) hitRange.getValue() || this.timer.hasTimePassed((int) delay.getValue())) {
                  this.releaseAll();
               }
               if ((Boolean)this.onlyIfNeeded.getValue()) {
                  if (distCurrent <= distReal) {
                     this.releaseAll();
                  }
                  if (this.lastRealPos != null) {
                     double lastDist = mc.field_71439_g.func_70011_f(this.lastRealPos.field_72450_a, this.lastRealPos.field_72448_b, this.lastRealPos.field_72449_c);
                     if (distReal < lastDist) {
                        this.releaseAll();
                     }
                  }
               }
               if ((Boolean)this.legit.getValue() && (Boolean)this.releaseOnHit.getValue() && this.target.field_70737_aN == 1) {
                  this.releaseAll();
               }
               this.lastRealPos = real;
            }
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent e) {
      if ((Boolean)this.esp.getValue()) {
         if (this.espMode.getIndex() != 0) {
            if (this.target != null) {
               Vec3 real = (Vec3)this.realPositions.get(this.target.func_145782_y());
               if (real != null) {
                  double x = real.field_72450_a - mc.func_175598_ae().field_78730_l;
                  double y = real.field_72448_b - mc.func_175598_ae().field_78731_m;
                  double z = real.field_72449_c - mc.func_175598_ae().field_78728_n;
                  AxisAlignedBB box = new AxisAlignedBB(x - (double)(this.target.field_70130_N / 2.0F), y, z - (double)(this.target.field_70130_N / 2.0F), x + (double)(this.target.field_70130_N / 2.0F), y + (double)this.target.field_70131_O, z + (double)(this.target.field_70130_N / 2.0F));
                  GlStateManager.func_179094_E();
                  GlStateManager.func_179090_x();
                  GlStateManager.func_179097_i();
                  GlStateManager.func_179132_a(false);
                  GlStateManager.func_179131_c(1.0F, 0.0F, 0.0F, 0.4F);
                  RenderGlobal.func_181563_a(box, 255, 0, 0, 153);
                  GlStateManager.func_179132_a(true);
                  GlStateManager.func_179126_j();
                  GlStateManager.func_179098_w();
                  GlStateManager.func_179121_F();
               }
            }
         }
      }
   }

   private boolean shouldQueue() {
      if (this.target == null) {
         return false;
      } else {
         Vec3 real = (Vec3)this.realPositions.get(this.target.func_145782_y());
         if (real == null) {
            return false;
         } else {
            double distReal;
            double distCurrent;
            if ((Boolean)this.onlyIfNeeded.getValue()) {
               distReal = mc.field_71439_g.func_70011_f(real.field_72450_a, real.field_72448_b, real.field_72449_c);
               distCurrent = (double)mc.field_71439_g.func_70032_d(this.target);
               return distReal < distCurrent;
            } else {
               distReal = mc.field_71439_g.func_70011_f(real.field_72450_a, real.field_72448_b, real.field_72449_c);
               distCurrent = (double)mc.field_71439_g.func_70032_d(this.target);
               return distReal + 0.15 < distCurrent && !this.timer.hasTimePassed((int)this.delay.getValue());
            }
         }
      }
   }

   private void releaseIncoming() {
      if (mc.func_147114_u() != null) {
         while(!this.incomingPackets.isEmpty()) {
            ((Packet)this.incomingPackets.poll()).func_148833_a(mc.func_147114_u());
         }
         this.timer.reset();
      }
   }

   private void releaseOutgoing() {
      while(!this.outgoingPackets.isEmpty()) {
         PacketUtil.sendPacketNoEvent((Packet)this.outgoingPackets.poll());
      }
      this.timer.reset();
   }

   private void releaseAll() {
      this.releaseIncoming();
      this.releaseOutgoing();
   }

   private boolean blockIncoming(Packet<?> p) {
      if (!(Boolean)this.onlyIfNeeded.getValue()) {
         if (!(p instanceof S12PacketEntityVelocity) && !(p instanceof S27PacketExplosion)) {
            return p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport || p instanceof S19PacketEntityHeadLook || p instanceof S0FPacketSpawnMob;
         } else {
            return false;
         }
      } else {
         return p instanceof S12PacketEntityVelocity || p instanceof S27PacketExplosion || p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport || p instanceof S19PacketEntityHeadLook || p instanceof S0FPacketSpawnMob;
      }
   }

   private boolean blockOutgoing(Packet<?> p) {
      return p instanceof C03PacketPlayer || p instanceof C02PacketUseEntity || p instanceof C0APacketAnimation || p instanceof C0BPacketEntityAction || p instanceof C08PacketPlayerBlockPlacement || p instanceof C07PacketPlayerDigging || p instanceof C09PacketHeldItemChange || p instanceof C00PacketKeepAlive || p instanceof C01PacketPing;
   }
}
