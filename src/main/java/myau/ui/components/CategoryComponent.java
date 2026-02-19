package myau.ui.components;

import myau.module.Module;
import myau.ui.Component;
import myau.ui.utils.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CategoryComponent {
    private final int MAX_HEIGHT = 300;

    public ArrayList<Component> modulesInCategory = new ArrayList<>();
    public String categoryName;
    private boolean categoryOpened;
    private int width;
    private int y;
    private int x;
    private final int bh;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean pin = false;
    private double marginY, marginX;
    private int scroll = 0;
    private double animScroll = 0;
    private int height = 0;

    // Rise‑style animation
    private float openAnim = 0f;

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = 110;
        this.x = 5;
        this.y = 5;
        this.bh = 16;
        this.xx = 0;
        this.categoryOpened = false;
        this.dragging = false;
        int tY = this.bh + 3;
        this.marginX = 90;
        this.marginY = 5;

        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += 18;
        }
    }

    public ArrayList<Component> getModules() {
        return this.modulesInCategory;
    }

    public void setX(int n) {
        this.x = n;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void mousePressed(boolean d) {
        this.dragging = d;
    }

    public boolean isPin() {
        return this.pin;
    }

    public void setPin(boolean on) {
        this.pin = on;
    }

    public boolean isOpened() {
        return this.categoryOpened;
    }

    public void setOpened(boolean on) {
        this.categoryOpened = on;
    }

    public void render(FontRenderer renderer) {
        update();

        // Rise‑style open animation
        float target = categoryOpened ? 1f : 0f;
        openAnim += (target - openAnim) * 0.2f;

        height = 0;
        for (Component moduleRenderManager : this.modulesInCategory) {
            height += moduleRenderManager.getHeight();
        }

        int maxScroll = Math.max(0, height - MAX_HEIGHT);
        if (scroll > maxScroll) scroll = maxScroll;
        if (animScroll > maxScroll) animScroll = maxScroll;
        animScroll += (scroll - animScroll) * 0.2;

        // === Rise 6 Panel Background ===
        drawRoundedRect(x - 2, y - 2, width + 4, bh + 4 + (int)(MAX_HEIGHT * openAnim), 10, new Color(255, 255, 255, 230));

        // === Soft Shadow ===
        drawShadow(x - 2, y - 2, width + 4, bh + 4 + (int)(MAX_HEIGHT * openAnim));

        // === Header ===
        renderer.drawString(this.categoryName, x + 6, y + 5, new Color(40, 40, 40).getRGB());

        // Expand arrow
        renderer.drawString(openAnim > 0.5 ? "-" : "+", x + width - 12, y + 5, new Color(40, 40, 40).getRGB());

        // === Module List ===
        if (openAnim > 0.01 && !modulesInCategory.isEmpty()) {
            int renderHeight = 0;
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            double scale = sr.getScaleFactor();
            int bottom = this.y + this.bh + MAX_HEIGHT + 3;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int) (this.x * scale), (int) ((sr.getScaledHeight() - bottom) * scale), (int) (this.width * scale), (int) (MAX_HEIGHT * scale));

            for (Component c2 : this.modulesInCategory) {
                int compHeight = c2.getHeight();
                if (renderHeight + compHeight > animScroll &&
                        renderHeight < animScroll + MAX_HEIGHT) {
                    int drawY = (int) (renderHeight - animScroll);
                    c2.setComponentStartAt(this.bh + 6 + drawY);
                    c2.draw(new AtomicInteger(0));
                }
                renderHeight += compHeight;
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // Rise‑style scroll bar
            if (height > MAX_HEIGHT) {
                float scrollY = (float) this.y + this.bh + 6 + (float) (animScroll * MAX_HEIGHT / height);
                drawRoundedRect(this.x + this.width - 4, (int) scrollY, 3, (int) ((float) MAX_HEIGHT * MAX_HEIGHT / height), 2, new Color(180, 180, 180, 120));
            }
        }
    }

    public void update() {
        int offset = this.bh + 6;
        for (Component component : this.modulesInCategory) {
            component.setComponentStartAt(offset);
            offset += component.getHeight();
        }
    }

    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getWidth() { return this.width; }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
        }
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.bh;
    }

    public boolean mousePressed(int x, int y) {
        return isHovered(x, y);
    }

    public boolean insideArea(int x, int y) {
        return isHovered(x, y);
    }

    public String getName() { return categoryName; }

    public void setLocation(int parseInt, int parseInt1) {
        this.x = parseInt;
        this.y = parseInt1;
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        if (!categoryOpened || height <= MAX_HEIGHT) return;

        int areaTop = this.y + this.bh;
        int areaBottom = this.y + this.bh + MAX_HEIGHT;

        if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= areaTop && mouseY <= areaBottom) {
            scroll -= scrollAmount * 12;
            scroll = Math.max(0, Math.min(scroll, height - MAX_HEIGHT));
        }
    }

    // === Rise‑style rounded rectangle ===
    private void drawRoundedRect(int x, int y, int w, int h, int r, Color c) {
        GuiUtils.drawRoundedRect(x, y, w, h, r, c);
}

    // === Rise‑style shadow ===
    private void drawShadow(int x, int y, int w, int h) {
        GuiUtils.drawShadow(x, y, w, h, 12);
    }
}
