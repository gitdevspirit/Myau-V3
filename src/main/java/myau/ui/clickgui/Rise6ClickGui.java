package myau.ui.clickgui;

import myau.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

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

    // ------------------------------------------------------------
    // BLUR ENABLED HERE (world only)
    // ------------------------------------------------------------
    @Override
    public void initGui() {
        super.initGui();
        mc.entityRenderer.loadShader(new ResourceLocation("shaders/post/blur.json"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        // ------------------------------------------------------------
        // STOP BLUR BEFORE DRAWING GUI (keeps GUI sharp)
        // ------------------------------------------------------------
        mc.entityRenderer.stopUseShader();

        ScaledResolution sr = new ScaledResolution(mc);

        // Smooth opening animation
        openAnim += (1f - openAnim) * 0.15f;

        int guiX = (int)(130 + (20 * (1 - openAnim))); // slide from right
        int guiAlpha = (int)(180 * openAnim);           // fade in

        // Fade overlay (darkens blurred world)
        drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), (guiAlpha << 24));

        // Sidebar
        int yOffset = 40;
        for (SidebarCategory cat : categories) {
            cat.render(10, yOffset, mouseX, mouseY, selectedCategory == cat);
            yOffset += 28;
        }

        // Search bar
        searchBar.render(130, 30, mouseX, mouseY);

        // Module panel (animated X)
        modulePanel.render(guiX, 60, mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ------------------------------------------------------------
    // BLUR DISABLED WHEN GUI CLOSES
    // ------------------------------------------------------------
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        mc.entityRenderer.stopUseShader();
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

        searchBar.mouseClicked(mouseX, mouseY, button);
        modulePanel.mouseClicked(mouseX, mouseY, button);
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
