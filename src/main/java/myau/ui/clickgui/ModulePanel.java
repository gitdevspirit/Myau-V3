package myau.ui.clickgui;

import myau.Myau;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

public class ModulePanel {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private SidebarCategory category;
    private final List<Module> modules = new ArrayList<>();

    // Scroll
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Layout
    private final int moduleHeight = 20;
    private final int spacing = 4;

    public ModulePanel(SidebarCategory category) {
        setCategory(category);
    }

    public void setCategory(SidebarCategory category) {
        this.category = category;
        modules.clear();

        // Load modules from module manager by category name
        for (Module m : Myau.moduleManager.modules.values()) {
            if (m.category.equalsIgnoreCase(category.getName())) {
                modules.add(m);
            }
        }
    }

    public void render(int x, int y, int mouseX, int mouseY, String searchText) {

        // Panel background
        Gui.drawRect(x, y, x + 300, y + 200, 0xAA111111);

        int offsetY = y + 10 - scrollOffset;

        // Filter modules by search text
        for (Module m : modules) {
            if (!searchText.isEmpty() && !m.getName().toLowerCase().contains(searchText.toLowerCase())) {
                continue;
            }

            // Module box
            Gui.drawRect(x + 10, offsetY, x + 290, offsetY + moduleHeight, 0xFF1E1E1E);

            // Highlight on hover
            if (mouseX >= x + 10 && mouseX <= x + 290 && mouseY >= offsetY && mouseY <= offsetY + moduleHeight) {
                Gui.drawRect(x + 10, offsetY, x + 290, offsetY + moduleHeight, 0x22FFFFFF);
            }

            // Module name
            mc.fontRendererObj.drawString(
                    m.getName(),
                    x + 15,
                    offsetY + 6,
                    m.isEnabled() ? 0xFF55AAFF : 0xFFFFFFFF
            );

            offsetY += moduleHeight + spacing;
        }

        // Calculate max scroll
        maxScroll = Math.max(0, (offsetY - y - 200));
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {

        int x = 130;
        int y = 60;

        int offsetY = y + 10 - scrollOffset;

        for (Module m : modules) {

            // Skip filtered modules
            // (SearchBar handles filtering)
            // If you want filtering here too, add the same check as render()

            int boxX1 = x + 10;
            int boxX2 = x + 290;
            int boxY1 = offsetY;
            int boxY2 = offsetY + moduleHeight;

            if (mouseX >= boxX1 && mouseX <= boxX2 && mouseY >= boxY1 && mouseY <= boxY2) {

                if (button == 0) {
                    // Left click = toggle module
                    m.toggle();
                }

                if (button == 1) {
                    // Right click = open settings (later)
                    // TODO: open module settings panel
                }

                return;
            }

            offsetY += moduleHeight + spacing;
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        // Reserved for sliders, color pickers, etc.
    }

    public void keyTyped(char typedChar, int keyCode) {
        // Reserved for text boxes inside module settings
    }

    public void handleScroll(int mouseX, int mouseY, int wheel) {
        if (wheel != 0) {
            scrollOffset -= wheel / 10;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }
}
