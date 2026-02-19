package myau.ui.clickgui;

import myau.config.GuiConfig;
import myau.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Rise6ClickGui extends GuiScreen {

    private final List<SidebarCategory> categories = new ArrayList<>();
    private SidebarCategory selectedCategory;

    private float openAnim = 0f;

    private SearchBar searchBar;
    private ModulePanel modulePanel;

    // ----------------------------------------------------------------
    // DRAG STATE
    // ----------------------------------------------------------------
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private int posX;
    private int posY;

    private static final int SIDEBAR_WIDTH   = 115;
    private static final int PANEL_WIDTH     = 300;
    private static final int DRAG_BAR_HEIGHT = 16;
    private static final int GAP             = 5; // gap between sidebar and main panel

    public Rise6ClickGui(
            List<Module> combatModules,
            List<Module> movementModules,
            List<Module> playerModules,
            List<Module> renderModules,
            List<Module> miscModules
    ) {
        categories.add(new SidebarCategory("Combat",   combatModules));
        categories.add(new SidebarCategory("Movement", movementModules));
        categories.add(new SidebarCategory("Player",   playerModules));
        categories.add(new SidebarCategory("Render",   renderModules));
        categories.add(new SidebarCategory("Misc",     miscModules));

        selectedCategory = categories.get(0);

        searchBar   = new SearchBar();
        modulePanel = new ModulePanel(selectedCategory);

        GuiConfig.load();
        posX = GuiConfig.guiX;
        posY = GuiConfig.guiY;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        ScaledResolution sr = new ScaledResolution(mc);

        openAnim += (1f - openAnim) * 0.15f;
        int guiAlpha = (int)(180 * openAnim);

        // Dark screen overlay
        drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), (guiAlpha << 24));

        // ----------------------------------------------------------------
        // SIDEBAR — moves with posX/posY
        // ----------------------------------------------------------------
        int sidebarX = posX - SIDEBAR_WIDTH - GAP - 10;
        int sidebarY = posY;
        int sidebarHeight = categories.size() * 28 + 20;

        // Sidebar background
        RoundedUtils.drawRoundedRect(sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarHeight, 8, 0xDD0A0A0A);

        // Sidebar category labels
        int yOffset = sidebarY + 10;
        for (SidebarCategory cat : categories) {
            cat.render(sidebarX + 5, yOffset, mouseX, mouseY, selectedCategory == cat);
            yOffset += 28;
        }

        // ----------------------------------------------------------------
        // MAIN PANEL — dynamic height
        // ----------------------------------------------------------------
        int panelHeight = modulePanel.getContentHeight() + 60;
        RoundedUtils.drawRoundedRect(posX - 10, posY, PANEL_WIDTH, panelHeight, 8, 0xDD0A0A0A);

        // Drag handle bar
        RoundedUtils.drawRoundedRect(posX - 10, posY, PANEL_WIDTH, DRAG_BAR_HEIGHT, 8, 0xDD1A1A1A);
        mc.fontRendererObj.drawString("§7✦ Myau", posX, posY + 4, 0xFFAAAAAA);

        // Search bar
        searchBar.render(posX, posY + 20, mouseX, mouseY);

        // Module list
        modulePanel.render(posX, posY + 50, mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        GuiConfig.guiX = posX;
        GuiConfig.guiY = posY;
        GuiConfig.save();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {

        // Drag bar
        if (button == 0 &&
            mouseX >= posX - 10 && mouseX <= posX - 10 + PANEL_WIDTH &&
            mouseY >= posY && mouseY <= posY + DRAG_BAR_HEIGHT) {

            dragging = true;
            dragOffsetX = mouseX - posX;
            dragOffsetY = mouseY - posY;
            return;
        }

        // Sidebar clicks — now relative to sidebarX
        int sidebarX = posX - SIDEBAR_WIDTH - GAP - 10;
        int yOffset = posY + 10;
        for (SidebarCategory cat : categories) {
            if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH &&
                mouseY >= yOffset && mouseY <= yOffset + 22) {
                selectedCategory = cat;
                modulePanel.setCategory(cat);
                return;
            }
            yOffset += 28;
        }

        searchBar.mouseClicked(mouseX, mouseY, button);
        modulePanel.mouseClicked(posX, posY + 50, mouseX, mouseY, button);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            ScaledResolution sr = new ScaledResolution(mc);
            posX = Math.max(SIDEBAR_WIDTH + GAP + 20,
                    Math.min(sr.getScaledWidth() - PANEL_WIDTH + 10, mouseX - dragOffsetX));
            posY = Math.max(0,
                    Math.min(sr.getScaledHeight() - 100, mouseY - dragOffsetY));
        } else {
            modulePanel.mouseClickMove(mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        modulePanel.mouseReleased();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        if (searchBar.keyTyped(typedChar, keyCode)) return;
        modulePanel.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
