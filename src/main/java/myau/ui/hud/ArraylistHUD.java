package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.GL11;

public class ArraylistHUD {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void render() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return; // don't render inside menus/clickgui

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hudModule = (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hudModule == null || !hudModule.isEnabled()) return;

        List<Module> enabled = new ArrayList<>(Myau.moduleManager.modules.values());
        enabled.removeIf(m -> !m.isToggled()); // change to isEnabled() if your method is different

        // Sort longest → shortest (classic arraylist look)
        enabled.sort(Comparator.comparingInt(m -> -mc.fontRendererObj.getStringWidth(m.getName())));

        int baseY = 4;               // starting y from top
        int spacing = mc.fontRendererObj.FONT_HEIGHT + 3;

        for (int i = 0; i < enabled.size(); i++) {
            Module mod = enabled.get(i);
            String name = mod.getName(); // change to getDisplayName() if you use suffixes

            int textWidth = mc.fontRendererObj.getStringWidth(name);

            float anim = mod.getAnimationProgress();
            float slideOffset = (1.0f - anim) * 60.0f; // slide distance in pixels

            float x = sr.getScaledWidth() - textWidth - 6 - slideOffset;
            int y = baseY + i * spacing;

            // Color from HUD module + per-line offset for gradient
            Color color = hudModule.getColor(System.currentTimeMillis() + (i * 180L));

            // Fade alpha with animation
            int alpha = (int) (255 * MathHelper.clamp(anim, 0.0f, 1.0f));
            int textColor = (color.getRGB() & 0x00FFFFFF) | (alpha << 24);

            // Background – also fades
            int bgAlpha = (int) (110 * anim);
            drawRect(
                (int) x - 5,
                y - 2,
                (int) x + textWidth + 5,
                y + mc.fontRendererObj.FONT_HEIGHT + 1,
                (bgAlpha << 24) | 0x000000   // black with variable alpha
            );

            // Draw text with shadow
            mc.fontRendererObj.drawStringWithShadow(name, x, y, textColor);
        }
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int temp = left;
            left = right;
            right = temp;
        }
        if (top < bottom) {
            int temp = top;
            top = bottom;
            bottom = temp;
        }

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(left, bottom);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // reset color
    }
}
