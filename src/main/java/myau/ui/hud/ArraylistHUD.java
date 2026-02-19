package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.Comparator;

public class ArraylistHUD {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void render() {
        ScaledResolution sr = new ScaledResolution(mc);

        // Stream over all modules correctly
        Myau.moduleManager.modules.values().stream()
                .filter(Module::isEnabled)
                .sorted(Comparator.comparing(m -> mc.fontRendererObj.getStringWidth(m.getName())).reversed())
                .forEach(module -> {
                    String name = module.getName();

                    // Get HUD color
                    Color color = Myau.moduleManager.getModule(myau.module.modules.HUD.class)
                            .getColor(System.currentTimeMillis());

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
