package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.KnockbackEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean pendingExplosion = false;

    public Velocity() {
        super("Velocity", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava()
                || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);

        return mc.thePlayer.onGround
                && (!killAura.isEnabled() || !autoblock.isBlocking());
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled()) return;

        if (this.pendingExplosion) {
            event.setCancelled(true);
            this.pendingExplosion = false;
            return;
        }

        if (this.fakeCheck.getValue() && this.isInLiquidOrWeb()) {
            event.setCancelled(true);
            return;
        }

        if (this.canDelay()) {
            event.setCancelled(true);
            return;
        }

        if (this.debugLog.getValue()) {
            System.out.println("[Velocity] Knockback event triggered.");
        }
    }
}
