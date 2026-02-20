package myau.ui.clickgui;

import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.Setting;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

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

    private int scrollOffset = 0;
    private int visibleHeight = 200;

    private static final int ROW_WIDTH = 220;
    private static final int SCROLL_BTN_SIZE = 14;

    public ModulePanel(SidebarCategory category) {
        this.category = category;
    }

    public void setCategory(SidebarCategory cat) {
        this.category = cat;
        expandedModule = null;
        scrollOffset = 0;
    }

    public void setVisibleHeight(int h) {
        this.visibleHeight = Math.max(50, h);
    }

    public void scrollUp() {
        scrollOffset = Math.max(0, scrollOffset - 20);
    }

    public void scrollDown() {
        int maxScroll = Math.max(0, getContentHeight() - visibleHeight + 40);
        scrollOffset = Math.min(maxScroll, scrollOffset + 20);
    }

    public void handleScroll(int delta) {
        if (delta > 0) scrollUp();
        else scrollDown();
    }

    // ----------------------------------------------------------------
    // RENDER
    // ----------------------------------------------------------------
    public void render(int x, int y, int mouseX, int mouseY, String search) {
        int width  = ROW_WIDTH;
        int offsetY = y - scrollOffset;

        // No scissor — just skip rows outside visible area
        int clipTop    = y;
        int clipBottom = y + visibleHeight;

        for (Module module : category.getModules()) {
            if (search != null && !search.isEmpty()) {
                if (!module.getName().toLowerCase().contains(search.toLowerCase())) continue;
            }

            int height = 16;

            boolean inView = !(offsetY + height + getSettingsHeight(module) < clipTop || offsetY > clipBottom);

            if (!inView) {
                offsetY += height + 1;
                if (expandedModule == module) offsetY += getSettingsHeight(module);
                continue;
            }

            boolean hovered =
                    mouseX >= x && mouseX <= x + width &&
                    mouseY >= offsetY && mouseY <= offsetY + height;

            int rowColor;
            if (module.isEnabled())  rowColor = 0xFF0D2137;
            else if (hovered)        rowColor = 0xFF2A2A2A;
            else                     rowColor = 0xFF1A1A1A;
            RoundedUtils.drawRoundedRect(x, offsetY, width, height, 4, rowColor);

            if (module.isEnabled()) {
                drawSolidRect(x, offsetY, x + 3, offsetY + height, 0xFF55AAFF);
            }

            int nameColor = module.isEnabled() ? 0xFF55AAFF : (hovered ? 0xFFCCCCCC : 0xFFFFFFFF);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString(module.getName(), x + 7, offsetY + 5, nameColor);

            boolean enabled = module.isEnabled();
            toggleAnim.putIfAbsent(module, enabled ? 1f : 0f);
            float anim = toggleAnim.get(module);
            anim += ((enabled ? 1f : 0f) - anim) * 0.2f;
            toggleAnim.put(module, anim);

            int toggleX = x + width - 26;
            int toggleY = offsetY + 4;
            RoundedUtils.drawRoundedRect(toggleX, toggleY, 20, 8, 4, blend(0xFF555555, 0xFF55AAFF, anim));
            RoundedUtils.drawRoundedRect(toggleX + 2 + (int)(anim * 8), toggleY + 1, 6, 6, 3, 0xFFFFFFFF);

            if (!module.getSettings().isEmpty()) {
                String arrow = expandedModule == module ? "v" : ">";
                GL11.glColor4f(1f, 1f, 1f, 1f);
                mc.fontRendererObj.drawString(arrow, x + width - 38, offsetY + 5, 0xFF888888);
            }

            offsetY += height + 1;

            // ----------------------------------------------------------------
            // SETTINGS
            // ----------------------------------------------------------------
            if (expandedModule == module) {
                for (Setting setting : module.getSettings()) {

                    if (setting instanceof SliderSetting) {
                        SliderSetting slider = (SliderSetting) setting;
                        int rowH = 28;

                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 4, 0xFF999999);

                        String valStr = formatDouble(slider.getValue());
                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(valStr,
                                x + width - mc.fontRendererObj.getStringWidth(valStr) - 8,
                                offsetY + 4, 0xFF55AAFF);

                        int barX = x + 10;
                        int barY = offsetY + 17;
                        int barW = width - 24;

                        RoundedUtils.drawRoundedRect(barX, barY, barW, 4, 2, 0xFF444444);
                        int fillW = Math.max(4, (int)(barW * slider.getPercent()));
                        RoundedUtils.drawRoundedRect(barX, barY, fillW, 4, 2, 0xFF55AAFF);
                        RoundedUtils.drawRoundedRect(barX + fillW - 4, barY - 3, 8, 10, 4, 0xFFFFFFFF);

                        if (draggingSlider == slider) {
                            sliderRenderX = barX;
                            sliderRenderWidth = barW;
                        }

                        offsetY += rowH + 2;

                    } else if (setting instanceof DropdownSetting) {
                        DropdownSetting dropdown = (DropdownSetting) setting;
                        int rowH = 16;

                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 4, 0xFF999999);

                        String val = "< " + dropdown.getValue() + " >";
                        int valW = mc.fontRendererObj.getStringWidth(val);
                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(val, x + width - valW - 8, offsetY + 4, 0xFF55AAFF);

                        offsetY += rowH + 1;

                    } else if (setting instanceof BooleanSetting) {
                        BooleanSetting bool = (BooleanSetting) setting;
                        int rowH = 16;

                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 4, 0xFF999999);

                        float bAnim = bool.getValue() ? 1f : 0f;
                        int bToggleX = x + width - 30;
                        int bToggleY = offsetY + 4;
                        RoundedUtils.drawRoundedRect(bToggleX, bToggleY, 18, 8, 4, blend(0xFF555555, 0xFF55AAFF, bAnim));
                        RoundedUtils.drawRoundedRect(bToggleX + 2 + (int)(bAnim * 6), bToggleY + 1, 6, 6, 3, 0xFFFFFFFF);

                        offsetY += rowH + 1;

                    } else if (setting instanceof KeybindSetting) {
                        KeybindSetting kb = (KeybindSetting) setting;
                        int rowH = 16;

                        RoundedUtils.drawRoundedRect(x + 6, offsetY, width - 6, rowH, 3, 0xFF202020);

                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(setting.getName(), x + 10, offsetY + 4, 0xFF999999);

                        boolean isListening = listeningKeybind == kb;
                        String keyLabel = isListening ? "[ ... ]" : "[ " + kb.getDisplayName() + " ]";
                        int keyColor = isListening ? 0xFFFFAA00 : 0xFF55AAFF;
                        int labelW = mc.fontRendererObj.getStringWidth(keyLabel);
                        GL11.glColor4f(1f, 1f, 1f, 1f);
                        mc.fontRendererObj.drawString(keyLabel, x + width - labelW - 8, offsetY + 4, keyColor);

                        offsetY += rowH + 1;
                    }
                }
                offsetY += 2;
            }
        }

        // ----------------------------------------------------------------
        // SCROLL BUTTONS on the right side
        // ----------------------------------------------------------------
        int totalH = getContentHeight();
        if (totalH > visibleHeight) {
            int btnX = x + width + 4;

            // Up button
            boolean upHovered = mouseX >= btnX && mouseX <= btnX + SCROLL_BTN_SIZE &&
                                mouseY >= y && mouseY <= y + SCROLL_BTN_SIZE;
            RoundedUtils.drawRoundedRect(btnX, y, SCROLL_BTN_SIZE, SCROLL_BTN_SIZE, 3,
                    upHovered ? 0xFF333333 : 0xFF222222);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString("^", btnX + 3, y + 3, 0xFF55AAFF);

            // Down button
            int downBtnY = y + visibleHeight - SCROLL_BTN_SIZE;
            boolean downHovered = mouseX >= btnX && mouseX <= btnX + SCROLL_BTN_SIZE &&
                                  mouseY >= downBtnY && mouseY <= downBtnY + SCROLL_BTN_SIZE;
            RoundedUtils.drawRoundedRect(btnX, downBtnY, SCROLL_BTN_SIZE, SCROLL_BTN_SIZE, 3,
                    downHovered ? 0xFF333333 : 0xFF222222);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString("v", btnX + 3, downBtnY + 3, 0xFF55AAFF);

            // Scrollbar track between buttons
            int trackY  = y + SCROLL_BTN_SIZE + 2;
            int trackH  = visibleHeight - SCROLL_BTN_SIZE * 2 - 4;
            int thumbH  = Math.max(16, (int)((float) visibleHeight / totalH * trackH));
            int thumbY  = trackY + (int)((float) scrollOffset / Math.max(1, totalH - visibleHeight) * (trackH - thumbH));
            drawSolidRect(btnX + 5, trackY, btnX + 8, trackY + trackH, 0xFF333333);
            drawSolidRect(btnX + 5, thumbY, btnX + 8, thumbY + thumbH, 0xFF55AAFF);
        }
    }

    // ----------------------------------------------------------------
    // MOUSE CLICKED
    // ----------------------------------------------------------------
    public void mouseClicked(int panelX, int panelY, int mouseX, int mouseY, int button) {
        int x = panelX;
        int width = ROW_WIDTH;

        // Scroll button clicks
        int totalH = getContentHeight();
        if (totalH > visibleHeight) {
            int btnX = x + width + 4;

            // Up button
            if (mouseX >= btnX && mouseX <= btnX + SCROLL_BTN_SIZE &&
                mouseY >= panelY && mouseY <= panelY + SCROLL_BTN_SIZE) {
                scrollUp();
                return;
            }

            // Down button
            int downBtnY = panelY + visibleHeight - SCROLL_BTN_SIZE;
            if (mouseX >= btnX && mouseX <= btnX + SCROLL_BTN_SIZE &&
                mouseY >= downBtnY && mouseY <= downBtnY + SCROLL_BTN_SIZE) {
                scrollDown();
                return;
            }
        }

        int offsetY = panelY - scrollOffset;

        for (Module module : category.getModules()) {
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
                        int barY = offsetY + 17;
                        int barW = width - 24;

                        if (button == 0 &&
                            mouseX >= barX && mouseX <= barX + barW &&
                            mouseY >= barY - 3 && mouseY <= barY + 10) {
                            draggingSlider = slider;
                            sliderRenderX = barX;
                            sliderRenderWidth = barW;
                            updateSlider(mouseX);
                        }

                        offsetY += 30;

                    } else if (setting instanceof DropdownSetting) {
                        DropdownSetting dropdown = (DropdownSetting) setting;

                        if (mouseX >= x + 6 && mouseX <= x + width &&
                            mouseY >= offsetY && mouseY <= offsetY + 16) {
                            if (button == 0) dropdown.next();
                            else if (button == 1) dropdown.prev();
                        }

                        offsetY += 17;

                    } else if (setting instanceof BooleanSetting) {
                        BooleanSetting bool = (BooleanSetting) setting;

                        if (button == 0 &&
                            mouseX >= x + 6 && mouseX <= x + width &&
                            mouseY >= offsetY && mouseY <= offsetY + 16) {
                            bool.toggle();
                        }

                        offsetY += 17;

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
        if (draggingSlider != null) updateSlider(mouseX);
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
    // CONTENT HEIGHT
    // ----------------------------------------------------------------
    public int getContentHeight() {
        int h = 0;
        for (Module module : category.getModules()) {
            h += 17;
            if (expandedModule == module) h += getSettingsHeight(module);
        }
        return h;
    }

    private int getSettingsHeight(Module module) {
        int h = 0;
        for (Setting setting : module.getSettings()) {
            if (setting instanceof SliderSetting)        h += 30;
            else if (setting instanceof DropdownSetting) h += 17;
            else if (setting instanceof BooleanSetting)  h += 17;
            else if (setting instanceof KeybindSetting)  h += 17;
        }
        return h + 2;
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

    private void drawSolidRect(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private int blend(int col1, int col2, float t) {
        int a1 = (col1 >> 24) & 0xFF, r1 = (col1 >> 16) & 0xFF, g1 = (col1 >> 8) & 0xFF, b1 = col1 & 0xFF;
        int a2 = (col2 >> 24) & 0xFF, r2 = (col2 >> 16) & 0xFF, g2 = (col2 >> 8) & 0xFF, b2 = col2 & 0xFF;
        return ((int)(a1+(a2-a1)*t) << 24) | ((int)(r1+(r2-r1)*t) << 16) | ((int)(g1+(g2-g1)*t) << 8) | (int)(b1+(b2-b1)*t);
    }
}
