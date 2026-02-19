package myau.mixin;

import myau.ui.hud.HUD;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public class MixinGuiIngameHUD {

    private final ArraylistHUD hud = new HUD();

    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void renderHUD(float partialTicks, CallbackInfo ci) {
        hud.render();
    }
}
