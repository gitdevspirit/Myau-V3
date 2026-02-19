package myau.ui.clickgui;

import myau.config.Config;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigPanel {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final File CONFIG_DIR = new File("./config/Myau/");

    private List<String> configs = new ArrayList<>();
    private String activeConfig = Config.lastConfig;

    public ConfigPanel() {
        refresh();
    }

    public void refresh() {
        configs.clear();
        if (CONFIG_DIR.exists()) {
            for (File f : CONFIG_DIR.listFiles()) {
                if (f.getName().endsWith(".json") && !f.getName().equals("gui.json")) {
                    configs.add(f.getName().replace(".json", ""));
                }
            }
        }
    }

    public void render(int x, int y, int mouseX, int mouseY) {
        if (configs.isEmpty()) {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString("§7No configs found", x + 8, y + 6, 0xFF666666);
            return;
        }

        int offsetY = y;
        for (String config : configs) {
            boolean selected = config.equals(activeConfig);
            boolean hovered  = mouseX >= x && mouseX <= x + 100 &&
                               mouseY >= offsetY && mouseY <= offsetY + 18;

            // Background
            int bg = selected ? 0xFF1A3A5C : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A);
            RoundedUtils.drawRoundedRect(x, offsetY, 100, 18, 4, bg);

            // Blue accent if selected
            if (selected) {
                RoundedUtils.drawRoundedRect(x, offsetY, 3, 18, 2, 0xFF55AAFF);
            }

            // Config name
            GL11.glColor4f(1f, 1f, 1f, 1f);
            int textColor = selected ? 0xFF55AAFF : (hovered ? 0xFFCCCCCC : 0xFFAAAAAA);
            mc.fontRendererObj.drawString(config, x + 8, offsetY + 5, textColor);

            // Load button
            if (hovered && !selected) {
                GL11.glColor4f(1f, 1f, 1f, 1f);
                mc.fontRendererObj.drawString("§aLoad", x + 72, offsetY + 5, 0xFF55FF55);
            }

            offsetY += 20;
        }
    }

    public void mouseClicked(int x, int y, int mouseX, int mouseY, int button) {
        int offsetY = y;
        for (String config : configs) {
            if (button == 0 &&
                mouseX >= x && mouseX <= x + 100 &&
                mouseY >= offsetY && mouseY <= offsetY + 18) {

                activeConfig = config;
                new Config(config, false).load();
                return;
            }
            offsetY += 20;
        }
    }

    public int getContentHeight() {
        return Math.max(configs.size() * 20 + 8, 20);
    }
}
