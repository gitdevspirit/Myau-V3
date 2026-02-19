package myau.ui.hud;

import myau.module.Module;
import myau.module.ModuleManager;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.util.HashMap;
import java.util.Map;

public class HUD {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // Animation state per module
    private final Map<Module, Float> slideAnim = new HashMap<>();
    private final Map<Module, Float> alphaAnim = new HashMap<>();

    public void render() {

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();

        int y = 5;

        // Sort modules by width (longest first)
        ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .sorted((a, b) -> {
                    int w1 = mc.fontRendererObj.getStringWidth(a.getName());
                    int w2 = mc.fontRendererObj.getStringWidth(b.getName());
                    return Integer.compare(w2, w1);
                })
                .forEach(module -> {

                    String name = module.getName();
                    int textWidth = mc.fontRendererObj.getStringWidth(name);
                    int boxWidth = textWidth + 10;

                    // Initialize animations
                    slideAnim.putIfAbsent(module, 0f);
                    alphaAnim.putIfAbsent(module, 0f);

                    // Target animation values
                    float slideTarget = boxWidth;
                    float alphaTarget = 255f;

                    // Smooth animation
                    float slide = slideAnim.get(module);
                    slide += (slideTarget - slide) * 0.2f;
                    slideAnim.put(module, slide);

                    float alpha = alphaAnim.get(module);
                    alpha += (alphaTarget - alpha) * 0.2f;
                    alphaAnim.put(module, alpha);

                    // Right‑side X position
                    int x = (int)(screenWidth - slide);

                    // Background (rounded)
                    int bgColor = (int)(alpha) << 24 | 0x101010;
                    RoundedUtils.drawRoundedRect(
                            x - 2,
                            y - 2,
                            boxWidth,
                            14,
                            4,
                            bgColor
                    );

                    // Static blue accent bar
                    RoundedUtils.drawRoundedRect(
                            x - 4,
                            y - 2,
                            2,
                            14,
                            2,
                            0xFF55AAFF
                    );

                    // Text
                    mc.fontRendererObj.drawString(
                            name,
                            x + 3,
                            y + 3,
                            0xFFFFFFFF
                    );

                    y += 16;
                });
    }
}
