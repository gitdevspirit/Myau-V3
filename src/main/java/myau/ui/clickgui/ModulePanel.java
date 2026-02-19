package myau.ui.clickgui;

import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.Setting;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

public class ModulePanel {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private SidebarCategory category;

    private final Map<Module, Float> toggleAnim = new HashMap<>();

    private Module expandedModule = null;

    private SliderSetting draggingSlider = null;
    private int sliderRenderX = 0;
    private int sliderRenderWidth = 0;

    private KeybindSetting listeningKeybind = null;

    public ModulePanel(SidebarCategory category) {
        this.category = category;
    }

    public void setCategory(SidebarCategory category) {
        this.category = category;
        expandedModule = null;
    }

    // ----------------------------------------------------------------
    // RENDER
    // ----------------------------------------------------------------
    public void render(int x, int y, int mouseX, int mouseY, String search) {
        int offsetY = y;

        for (Module module : category.getModules()) {
            if (search != null && !search.isEmpty()) {
                if (!module.getName().toLowerCase().contains(search.toLowerCase())) continue;
            }

            int width = 160;
            int height = 16;

            boolean hovered =
                    mouseX >= x && mouseX <= x + width &&
                    mouseY >= offsetY && mouseY <= offsetY + height;

            // Module row background
            RoundedUtils.drawRoundedRect(x, offsetY, width, height, 4, hovered ? 0xFF2A2A2A : 0xFF1A1A1A);

            // Module name
            mc.fontRendererObj.drawString(module.getName(), x + 5, offsetY + 5, 0xFFFFFFFF);

            // Toggle animation
            boolean enabled = module.isEnabled();
            toggleAnim.putIfAbsent(module, enabled ? 1f : 0f);
            float anim = toggleAnim.get(module);
            anim += ((enabled ? 1f : 0f) - anim) * 0.2f;
            toggleAnim.put(module, anim);

            int toggleX = x + width - 26;
            int toggleY = offsetY + 4;
            RoundedUtils.drawRoundedRect(toggleX, toggleY, 20, 8, 4, blend(0xFF555555, 0xFF55AAFF, anim));
            RoundedUtils.drawRoundedRect(toggleX + 2 + (int)(anim * 8), toggleY + 1, 6, 6, 3, 0xFFFFFFFF);

            // Expand arrow
            if (!module.getSettings().isEmpty()) {
                String arrow = expandedModule == module ? "v" : ">";
                mc.fontRendererObj.drawString(arrow, x + width - 38, offsetY + 5, 0xFF888888);
            }

            offsetY += height + 1;

            // Settings
            if (expandedModule == module) {
                for (Setting setting : module.getSettings()) {

                    if (setting instanceof SliderSetting) {
                        SliderSetting slider = (SliderSetting) setting;

                        int rowH = 22;
                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        // Name
                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 3, 0xFF999999);

                        // Value
                        String valStr = formatDouble(slider.getValue());
                        mc.fontRendererObj.drawString(valStr,
                                x + width - mc.fontRendererObj.getStringWidth(valStr) - 8,
                                offsetY + 3, 0xFF55AAFF);

                        // Slider bar
                        int barX = x + 10;
                        int barY = offsetY + 14;
                        int barW = width - 20;
                        int barH = 3;

                        RoundedUtils.drawRoundedRect(barX, barY, barW, barH, 1, 0xFF444444);

                        int fillW = Math.max(1, (int)(barW * slider.getPercent()));
                        RoundedUtils.drawRoundedRect(barX, barY, fillW, barH, 1, 0xFF55AAFF);

                        // Knob
                        int knobX = barX + fillW - 3;
                        RoundedUtils.drawRoundedRect(knobX, barY - 2, 6, 7, 3, 0xFFFFFFFF);

                        if (draggingSlider == slider) {
                            sliderRenderX = barX;
                            sliderRenderWidth = barW;
                        }

                        offsetY += rowH + 1;

                    } else if (setting instanceof KeybindSetting) {
                        KeybindSetting kb = (KeybindSetting) setting;

                        int rowH = 16;
                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 4, 0xFF999999);

                        boolean isListening = listeningKeybind == kb;
                        String keyLabel = isListening ? "[ ... ]" : "[ " + kb.getDisplayName() + " ]";
                        int keyColor = isListening ? 0xFFFFAA00 : 0xFF55AAFF;
                        int labelW = mc.fontRendererObj.getStringWidth(keyLabel);
                        mc.fontRendererObj.drawString(keyLabel, x + width - labelW - 8, offsetY + 4, keyColor);

                        offsetY += rowH + 1;
                    }
                }
                offsetY += 2;
            }
        }
    }

    // ----------------------------------------------------------------
    // MOUSE CLICKED
    // ----------------------------------------------------------------
    public void mouseClicked(int panelX, int panelY, int mouseX, int mouseY, int button) {
        int x = panelX;
        int offsetY = panelY;

        for (Module module : category.getModules()) {
            int width = 160;
            int height = 16;

            boolean hovered =
                    mouseX >= x && mouseX <= x + width &&
                    mouseY >= offsetY && mouseY <= offsetY + height;

            if (hovered) {
                if (button == 0) {
                    module.toggle();
                } else if (button == 1) {
                    if (!module.getSettings().isEmpty()) {
                        expandedModule = (expandedModule == module) ? null : module;
                        draggingSlider = null;
                        listeningKeybind = null;
                    }
                }
                return;
            }

            offsetY += height + 1;

            if (expandedModule == module) {
                for (Setting setting : module.getSettings()) {

                    if (setting instanceof SliderSetting) {
                        SliderSetting slider = (SliderSetting) setting;

                        int barX = x + 10;
                        int barY = offsetY + 14;
                        int barW = width - 20;

                        if (button == 0 &&
                            mouseX >= barX && mouseX <= barX + barW &&
                            mouseY >= barY - 2 && mouseY <= barY + 9) {

                            draggingSlider = slider;
                            sliderRenderX = barX;
                            sliderRenderWidth = barW;
                            updateSlider(mouseX);
                        }

                        offsetY += 23;

                    } else if (setting instanceof KeybindSetting) {
                        KeybindSetting kb = (KeybindSetting) setting;

                        if (button == 0 &&
                            mouseX >= x + 6 && mouseX <= x + width &&
                            mouseY >= offsetY && mouseY <= offsetY + 16) {

                            listeningKeybind = (listeningKeybind == kb) ? null : kb;
                            if (listeningKeybind != null) kb.startListening();
                        }

                        offsetY += 17;
                    }
                }
                offsetY += 2;
            }
        }
    }

    // ----------------------------------------------------------------
    // MOUSE DRAG & RELEASE
    // ----------------------------------------------------------------
    public void mouseClickMove(int mouseX) {
        if (draggingSlider != null) {
            updateSlider(mouseX);
        }
    }

    public void mouseReleased() {
        draggingSlider = null;
    }

    // ----------------------------------------------------------------
    // KEY TYPED
    // ----------------------------------------------------------------
    public void keyTyped(char typedChar, int keyCode) {
        if (listeningKeybind != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                listeningKeybind.setKeyCode(0);
            } else {
                listeningKeybind.setKeyCode(keyCode);
            }
            listeningKeybind = null;
        }
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------
    private void updateSlider(int mouseX) {
        float pct = (float)(mouseX - sliderRenderX) / sliderRenderWidth;
        pct = Math.max(0f, Math.min(1f, pct));
        double newVal = draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin());
        draggingSlider.setValue(newVal);
    }

    private String formatDouble(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.format("%.2f", v);
    }

    private int blend(int col1, int col2, float t) {
        int a1 = (col1 >> 24) & 0xFF, r1 = (col1 >> 16) & 0xFF, g1 = (col1 >> 8) & 0xFF, b1 = col1 & 0xFF;
        int a2 = (col2 >> 24) & 0xFF, r2 = (col2 >> 16) & 0xFF, g2 = (col2 >> 8) & 0xFF, b2 = col2 & 0xFF;
        return ((int)(a1+(a2-a1)*t) << 24) | ((int)(r1+(r2-r1)*t) << 16) | ((int)(g1+(g2-g1)*t) << 8) | (int)(b1+(b2-b1)*t);
    }
}
