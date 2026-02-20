package myau.ui.clickgui;

import myau.config.GuiConfig;
import myau.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Rise6ClickGui extends GuiScreen {

    private final List<SidebarCategory> categories = new ArrayList<>();
    private SidebarCategory selectedCategory;

    private float openAnim = 0f;

    private SearchBar searchBar;
    private ModulePanel modulePanel;
    private ConfigPanel configPanel;

    private boolean showConfigs = false;

    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private int posX;
    private int posY;

    private static final int SIDEBAR_WIDTH   = 120;
    private static final int PANEL_WIDTH     = 260;
    private static final int DRAG_BAR_HEIGHT = 16;
    private static final int TOTAL_WIDTH     = SIDEBAR_WIDTH + PANEL_WIDTH;

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
        configPanel = new ConfigPanel();

        GuiConfig.load();
        posX = GuiConfig.guiX;
        posY = GuiConfig.guiY;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    private int getCategoryHeight() { return categories.size() * 24 + 28; }
    private int getConfigBtnY()     { return posY + getCategoryHeight() + 8; }
    private int getConfigPanelH()   { return showConfigs ? configPanel.getContentHeight() : 0; }
    private int getSidebarHeight()  { return getCategoryHeight() + 8 + 16 + getConfigPanelH() + 8; }
    private int getPanelHeight()    { return Math.max(modulePanel.getContentHeight() + 50, getSidebarHeight()); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        openAnim += (1f - openAnim) * 0.15f;

        int panelHeight = getPanelHeight();

        modulePanel.setVisibleHeight(panelHeight - 50);

        // Rounded dark background only behind GUI
        RoundedUtils.drawRoundedRect(
                posX - 8,
                posY - 8,
                TOTAL_WIDTH + 16,
                panelHeight + 16,
                12,
                0xCC000000
        );

        // Outer background
        RoundedUtils.drawRoundedRect(posX, posY, TOTAL_WIDTH, panelHeight, 10, 0xF0080808);

        // Sidebar background
        RoundedUtils.drawRoundedRect(posX, posY, SIDEBAR_WIDTH, panelHeight, 10, 0xF0111111);

        // Title
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.fontRendererObj.drawString("§b§lMyau", posX + 10, posY + 8, 0xFFFFFFFF);

        // Categories
        int yOffset = posY + 28;
        for (SidebarCategory cat : categories) {
            boolean selected = selectedCategory == cat;
            boolean hovered  = mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                               mouseY >= yOffset - 2 && mouseY <= yOffset + 18;

            if (selected) {
                RoundedUtils.drawRoundedRect(posX + 6, yOffset - 2, SIDEBAR_WIDTH - 12, 20, 4, 0xFF1A3A5C);
                drawRect(posX + 4, yOffset, posX + 7, yOffset + 14, 0xFF55AAFF);
            } else if (hovered) {
                RoundedUtils.drawRoundedRect(posX + 6, yOffset - 2, SIDEBAR_WIDTH - 12, 20, 4, 0xFF1A1A1A);
            }

            int textColor = selected ? 0xFF55AAFF : (hovered ? 0xFFCCCCCC : 0xFF888888);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString(cat.getName(), posX + 14, yOffset + 3, textColor);

            yOffset += 24;
        }

        // Configs button
        int configBtnY = getConfigBtnY();
        boolean configHovered = mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                                mouseY >= configBtnY && mouseY <= configBtnY + 16;

        RoundedUtils.drawRoundedRect(posX + 6, configBtnY, SIDEBAR_WIDTH - 12, 16, 4,
                showConfigs ? 0xFF1A3A5C : (configHovered ? 0xFF1A1A1A : 0xFF161616));

        if (showConfigs) {
            drawRect(posX + 4, configBtnY + 2, posX + 7, configBtnY + 14, 0xFF55AAFF);
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.fontRendererObj.drawString(
                showConfigs ? "§bConfigs" : "§7Configs",
                posX + 14, configBtnY + 4,
                showConfigs ? 0xFF55AAFF : (configHovered ? 0xFFCCCCCC : 0xFF888888));

        if (showConfigs) {
            configPanel.render(posX + 6, configBtnY + 20, mouseX, mouseY);
        }

        // Main panel
        int panelX = posX + SIDEBAR_WIDTH + 8;

        searchBar.render(panelX, posY + 10, mouseX, mouseY);
        drawRect(panelX, posY + 30, posX + TOTAL_WIDTH - 8, posY + 31, 0xFF222222);
        modulePanel.render(panelX, posY + 38, mouseX, mouseY, searchBar.getText());

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
public void handleMouseInput() throws IOException {
    // Read wheel BEFORE super consumes the event
    int delta = Mouse.getEventDWheel();
    if (delta != 0) {
        modulePanel.handleScroll(delta);
    }
    super.handleMouseInput();
}

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {

        if (configPanel.isContextMenuOpen()) {
            configPanel.mouseClicked(posX + 6, getConfigBtnY() + 20, mouseX, mouseY, button);
            return;
        }

        // Drag bar
        if (button == 0 &&
            mouseX >= posX && mouseX <= posX + TOTAL_WIDTH &&
            mouseY >= posY && mouseY <= posY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragOffsetX = mouseX - posX;
            dragOffsetY = mouseY - posY;
            return;
        }

        // Category clicks
        int yOffset = posY + 28;
        for (SidebarCategory cat : categories) {
            if (mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                mouseY >= yOffset - 2 && mouseY <= yOffset + 18) {
                selectedCategory = cat;
                modulePanel.setCategory(cat);
                return;
            }
            yOffset += 24;
        }

        // Configs button
        int configBtnY = getConfigBtnY();
        if (button == 0 &&
            mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
            mouseY >= configBtnY && mouseY <= configBtnY + 16) {
            showConfigs = !showConfigs;
            if (showConfigs) configPanel.refresh();
            return;
        }

        if (showConfigs) {
            configPanel.mouseClicked(posX + 6, configBtnY + 20, mouseX, mouseY, button);
        }

        int panelX = posX + SIDEBAR_WIDTH + 8;
        searchBar.mouseClicked(mouseX, mouseY, button);
        modulePanel.mouseClicked(panelX, posY + 38, mouseX, mouseY, button);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            ScaledResolution sr = new ScaledResolution(mc);
            posX = Math.max(0, Math.min(sr.getScaledWidth()  - TOTAL_WIDTH, mouseX - dragOffsetX));
            posY = Math.max(0, Math.min(sr.getScaledHeight() - 100,         mouseY - dragOffsetY));
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
