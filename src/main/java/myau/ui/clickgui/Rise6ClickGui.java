package myau.ui.clickgui;

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

    public Rise6ClickGui(
            List<Module> combatModules,
            List<Module> movementModules,
            List<Module> playerModules,
            List<Module> renderModules,
            List<Module> miscModules
    ) {
        categories.add(new SidebarCategory("Combat", combatModules));
        categories.add(new SidebarCategory("Movement", movementModules));
        categories.add(new SidebarCategory("Player", playerModules));
        categories.add(new SidebarCategory("Render", renderModules));
        categories.add(new SidebarCategory("Misc", miscModules));

        selectedCategory = categories.get(0);

        searchBar = new SearchBar();
        modulePanel = new ModulePanel(selectedCategory);
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        ScaledResolution sr = new ScaledResolution(mc);

        openAnim += (1f - openAnim) * 0.15f;

        int guiX = (int)(130 + (20 * (1 - openAnim)));
        int guiAlpha = (int)(180 * openAnim);

        // Fade overlay
        drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), (guiAlpha << 24));

        // Sidebar
        int yOffset = 40;
        for (SidebarCategory cat : categories) {
            cat.render(10, yOffset, mouseX, mouseY, selectedCategory == cat);
            yOffset += 28;
        }

        // Rounded background panel
        RoundedUtils.drawRoundedRect(guiX - 10, 20, 300, 260, 8, 0xCC0F0F0F);

        // Search bar
        searchBar.render(guiX, 30, mouseX, mouseY);

        // Module panel
        modulePanel.render(guiX, 60, mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {

        int yOffset = 40;
        for (SidebarCategory cat : categories) {
            if (mouseX >= 10 && mouseX <= 110 &&
                mouseY >= yOffset && mouseY <= yOffset + 22) {
                selectedCategory = cat;
                modulePanel.setCategory(cat);
                return;
            }
            yOffset += 28;
        }

        int guiX = (int)(130 + (20 * (1 - openAnim)));

        searchBar.mouseClicked(mouseX, mouseY, button);
        modulePanel.mouseClicked(guiX, 60, mouseX, mouseY, button);
    }

    // ✅ NEW — forwards drag events to slider
    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        modulePanel.mouseClickMove(mouseX);
    }

    // ✅ NEW — releases slider drag
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
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
