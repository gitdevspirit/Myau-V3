package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.Comparator;

public class ArraylistHUD {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void render() {
        ScaledResolution sr = new ScaledResolution(mc);

        Myau.moduleManager.modules.values().stream()
                .filter(Module::isEnabled)
                .sorted(Comparator.comparing(
                        (Module m) -> mc.fontRendererObj.getStringWidth(m.getName())
                ).reversed())
                .forEach(module -> {

                    HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
                    Color color = hud.getColor(System.currentTimeMillis());

                    String name = module.getName();

                    int x = sr.getScaledWidth() - mc.fontRendererObj.getStringWidth(name) - 4;
                    int y = 10 + (mc.fontRendererObj.FONT_HEIGHT + 2) *
                            getModuleIndex(module);

                    mc.fontRendererObj.drawStringWithShadow(
                            name,
                            x,
                            y,
                            color.getRGB()
                    );
                });
    }

    private int getModuleIndex(Module module) {
        int index = 0;
        for (Module m : Myau.moduleManager.modules.values()) {
            if (m.isEnabled()) {
                if (m == module) break;
                index++;
            }
        }
        return index;
    }
}
