package myau.ui.components;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.HUD;
import myau.property.Property;
import myau.property.properties.*;
import myau.ui.Component;
import myau.ui.dataset.impl.FloatSlider;
import myau.ui.dataset.impl.IntSlider;
import myau.ui.dataset.impl.PercentageSlider;
import myau.ui.utils.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {

    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    private final ArrayList<Component> settings;
    public boolean panelExpand;

    // Rise‑style animations
    private float hoverAnim = 0f;
    private float toggleAnim = 0f;
    private float expandAnim = 0f;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;

        int y = offsetY + 16;

        if (!Myau.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> baseProperty : Myau.propertyManager.properties.get(mod.getClass())) {

                if (baseProperty instanceof BooleanProperty) {
                    settings.add(new CheckBoxComponent((BooleanProperty) baseProperty, this, y));
                    y += 14;

                } else if (baseProperty instanceof FloatProperty) {
                    settings.add(new SliderComponent(new FloatSlider((FloatProperty) baseProperty), this, y));
                    y += 14;

                } else if (baseProperty instanceof IntProperty) {
                    settings.add(new SliderComponent(new IntSlider((IntProperty) baseProperty), this, y));
                    y += 14;

                } else if (baseProperty instanceof PercentProperty) {
                    settings.add(new SliderComponent(new PercentageSlider((PercentProperty) baseProperty), this, y));
                    y += 14;

                } else if (baseProperty instanceof ModeProperty) {
                    settings.add(new ModeComponent((ModeProperty) baseProperty, this, y));
                    y += 14;

                } else if (baseProperty instanceof ColorProperty) {
                    settings.add(new ColorSliderComponent((ColorProperty) baseProperty, this, y));
                    y += 14;

                } else if (baseProperty instanceof TextProperty) {
                    settings.add(new TextComponent((TextProperty) baseProperty, this, y));
                    y += 14;
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
        int y = this.offsetY + 18;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    public void draw(AtomicInteger offset) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;

        // Hover animation
        boolean hovered = isHovered(Minecraft.getMinecraft().mouseHelper.deltaX, Minecraft.getMinecraft().mouseHelper.deltaY);
        hoverAnim += ((hovered ? 1f : 0f) - hoverAnim) * 0.2f;

        // Toggle animation
        toggleAnim += ((mod.isEnabled() ? 1f : 0f) - toggleAnim) * 0.2f;

        // Expand animation
        expandAnim += ((panelExpand ? 1f : 0f) - expandAnim) * 0.2f;

        int baseX = category.getX();
        int baseY = category.getY() + offsetY;

        // === Rise‑style module background ===
        Color bg = new Color(245, 245, 245, (int) (230 + hoverAnim * 25));
        GuiUtils.drawRoundedRect(baseX + 4, baseY, category.getWidth() - 8, 16, 6, bg);

        // === Module name ===
        fr.drawString(mod.getName(), baseX + 10, baseY + 4, new Color(40, 40, 40).getRGB());

        // === Toggle switch ===
        int toggleX = baseX + category.getWidth() - 22;
        int toggleY = baseY + 4;

        // Background of toggle
        GuiUtils.drawRoundedRect(toggleX, toggleY, 14, 8, 4,
                new Color(200, 200, 200, 180));

        // Knob
        GuiUtils.drawRoundedRect(
                (int) (toggleX + toggleAnim * 6),
                toggleY,
                8, 8, 4,
                toggleAnim > 0.5 ? new Color(60, 162, 253) : new Color(180, 180, 180)
        );

        // === Expand arrow ===
        fr.drawString(panelExpand ? "-" : "+", baseX + category.getWidth() - 10, baseY + 4, new Color(60, 60, 60).getRGB());

        // === Draw settings ===
        if (expandAnim > 0.01 && !settings.isEmpty()) {
            for (Component c : settings) {
                if (c.isVisible()) {
                    c.draw(offset);
                    offset.incrementAndGet();
                }
            }
        }
    }

    public int getHeight() {
        if (!panelExpand && expandAnim < 0.01) {
            return 18;
        } else {
            int h = 18;
            for (Component c : settings) {
                if (c.isVisible()) {
                    h += c.getHeight();
                }
            }
            return h;
        }
    }

    public void update(int mousePosX, int mousePosY) {
        if (!panelExpand) return;
        for (Component c : settings) {
            if (c.isVisible()) {
                c.update(mousePosX, mousePosY);
            }
        }
    }

    public void mouseDown(int x, int y, int button) {
        if (isHovered(x, y) && button == 0) {
            mod.toggle();
        }

        if (isHovered(x, y) && button == 1) {
            panelExpand = !panelExpand;
        }

        if (!panelExpand) return;

        for (Component c : settings) {
            if (c.isVisible()) {
                c.mouseDown(x, y, button);
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (!panelExpand) return;
        for (Component c : settings) {
            if (c.isVisible()) {
                c.mouseReleased(x, y, button);
            }
        }
    }

    public void keyTyped(char chatTyped, int keyCode) {
        if (!panelExpand) return;
        for (Component c : settings) {
            if (c.isVisible()) {
                c.keyTyped(chatTyped, keyCode);
            }
        }
    }

    public boolean isHovered(int x, int y) {
        return x > category.getX() + 4 &&
               x < category.getX() + category.getWidth() - 4 &&
               y > category.getY() + offsetY &&
               y < category.getY() + offsetY + 16;
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
// force rebuild
