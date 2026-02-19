package myau.ui.clickgui;

import myau.module.Module;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

public class ModulePanel {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private SidebarCategory category;

    // Animation state per module
    private final Map<Module, Float> toggleAnim = new HashMap<>();

    public ModulePanel(SidebarCategory category) {
        this.category = category;
    }

    public void setCategory(SidebarCategory category) {
        this.category = category;
    }

    public void render(int x, int y, int mouseX, int mouseY, String search) {

        int offsetY = y;

        for (Module module : category.getModules()) {

            // Search filter
            if (search != null && !search.isEmpty()) {
                if (!module.getName().toLowerCase().contains(search.toLowerCase())) {
                    continue;
                }
            }

            int width = 120;
            int height = 18;

            boolean hovered =
                    mouseX >= x && mouseX <= x + width &&
                    mouseY >= offsetY && mouseY <= offsetY + height;

            // Background color
            int bg = hovered ? 0xFF2A2A2A : 0xFF1A1A1A;

            // Rounded module button
            RoundedUtils.drawRoundedRect(
                    x,
                    offsetY,
                    width,
                    height,
                    5,
                    bg
            );

            // Module name
            mc.fontRendererObj.drawString(
                    module.getName(),
                    x + 6,
                    offsetY + 6,
                    0xFFFFFFFF
            );

            // ------------------------------------------------------------
            // ANIMATED ROUNDED TOGGLE SWITCH
            // ------------------------------------------------------------

            boolean enabled = module.isEnabled();

            // Initialize animation state if missing
            toggleAnim.putIfAbsent(module, enabled ? 1f : 0f);

            // Smooth animation
            float anim = toggleAnim.get(module);
            float target = enabled ? 1f : 0f;
            anim += (target - anim) * 0.2f; // easing
            toggleAnim.put(module, anim);

            int toggleX = x + width - 28;
            int toggleY = offsetY + 4;

            // Background pill color (fade between grey → blue)
            int offColor = 0xFF555555;
            int onColor = 0xFF55AAFF;

            int blendedColor = blend(offColor, onColor, anim);

            // Draw pill
            RoundedUtils.drawRoundedRect(
                    toggleX,
                    toggleY,
                    22,
                    10,
                    5,
                    blendedColor
            );

            // Knob position (slides smoothly)
            float knobX = toggleX + 2 + (anim * 10);

            // Knob circle
            RoundedUtils.drawRoundedRect(
                    knobX,
                    toggleY + 1,
                    8,
                    8,
                    4,
                    0xFFFFFFFF
            );

            offsetY += height + 4;
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {

        int x = Rise6ClickGuiStatic.guiX; // if you store guiX globally
        int y = 60;

        int offsetY = y;

        for (Module module : category.getModules()) {

            int width = 120;
            int height = 18;

            boolean hovered =
                    mouseX >= x && mouseX <= x + width &&
                    mouseY >= offsetY && mouseY <= offsetY + height;

            if (hovered && button == 0) {
                module.toggle();
                return;
            }

            offsetY += height + 4;
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        // No key handling needed for modules
    }

    // ------------------------------------------------------------
    // COLOR BLENDING UTILITY
    // ------------------------------------------------------------
    private int blend(int col1, int col2, float t) {
        int a1 = (col1 >> 24) & 0xFF;
        int r1 = (col1 >> 16) & 0xFF;
        int g1 = (col1 >> 8) & 0xFF;
        int b1 = col1 & 0xFF;

        int a2 = (col2 >> 24) & 0xFF;
        int r2 = (col2 >> 16) & 0xFF;
        int g2 = (col2 >> 8) & 0xFF;
        int b2 = col2 & 0xFF;

        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
