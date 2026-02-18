package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Tracers extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Color / visibility properties ─────────────────────────────────────────
    public final ModeProperty colorMode = new ModeProperty("color", 0,
            new String[]{"DEFAULT", "TEAMS", "HUD", "BEDWARS"});
    public final BooleanProperty drawLines = new BooleanProperty("lines", true);
    public final BooleanProperty drawArrows = new BooleanProperty("arrows", false);
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final PercentProperty arrowRadius = new PercentProperty("radius", 50);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);

    // ── Arrow properties ──────────────────────────────────────────────────────
    public final ModeProperty arrowMode = new ModeProperty("arrow", 3,
            new String[]{"Caret", "Greater than", "Triangle", "Slinky"});

    // ── Misc properties ───────────────────────────────────────────────────────
    public final BooleanProperty showDistance = new BooleanProperty("distance", true);
    public final BooleanProperty hideTeammates = new BooleanProperty("hide teammates", true);
    public final BooleanProperty enemiesOnly = new BooleanProperty("enemies only", false);
    public final BooleanProperty renderOnlyOffScreen = new BooleanProperty("only offscreen", false);
    public final BooleanProperty renderInGUIs = new BooleanProperty("in GUIs", false);

    // ── BedWars team-color mapping ────────────────────────────────────────────
    private static final Map<String, Color> BEDWARS_TEAM_COLORS = new HashMap<>();
    static {
        BEDWARS_TEAM_COLORS.put("Red", new Color(255, 50, 50));
        BEDWARS_TEAM_COLORS.put("Blue", new Color(50, 80, 255));
        BEDWARS_TEAM_COLORS.put("Green", new Color(50, 200, 50));
        BEDWARS_TEAM_COLORS.put("Yellow", new Color(255, 220, 30));
        BEDWARS_TEAM_COLORS.put("Aqua", new Color(50, 220, 220));
        BEDWARS_TEAM_COLORS.put("White", new Color(230, 230, 230));
        BEDWARS_TEAM_COLORS.put("Pink", new Color(255, 100, 180));
        BEDWARS_TEAM_COLORS.put("Gray", new Color(130, 130, 130));
        BEDWARS_TEAM_COLORS.put("Orange", new Color(255, 140, 20));
        BEDWARS_TEAM_COLORS.put("Purple", new Color(160, 50, 220));
        BEDWARS_TEAM_COLORS.put("Maroon", new Color(180, 30, 30));
        BEDWARS_TEAM_COLORS.put("Teal", new Color(30, 180, 150));
        BEDWARS_TEAM_COLORS.put("Lime", new Color(120, 255, 30));
        BEDWARS_TEAM_COLORS.put("Brown", new Color(140, 80, 30));
        BEDWARS_TEAM_COLORS.put("Silver", new Color(180, 180, 180));
        BEDWARS_TEAM_COLORS.put("Crimson", new Color(200, 20, 60));
    }

    private Color getBedWarsTeamColor(EntityPlayer player, float alpha) {
        if (mc.theWorld == null) return null;
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScorePlayerTeam team = sb.getPlayersTeam(player.getName());
        if (team == null) return null;
        String teamName = team.getTeamName();
        for (Map.Entry<String, Color> entry : BEDWARS_TEAM_COLORS.entrySet()) {
            if (teamName.toLowerCase().contains(entry.getKey().toLowerCase())) {
                Color c = entry.getValue();
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255.0F));
            }
        }
        String prefix = team.getColorPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return TeamUtil.getTeamColor(player, alpha);
        }
        return null;
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) return false;
        if (entityPlayer == mc.thePlayer || entityPlayer == mc.getRenderViewEntity()) return false;
        if (TeamUtil.isBot(entityPlayer) && !this.showBots.getValue()) return false;
        if (TeamUtil.isSameTeam(entityPlayer) && this.hideTeammates.getValue()) return false;
        if (TeamUtil.isFriend(entityPlayer) && !this.showFriends.getValue()) return false;
        if (TeamUtil.isTarget(entityPlayer) && !this.showEnemies.getValue()) return false;
        if (!TeamUtil.isTarget(entityPlayer) && !this.showPlayers.getValue()) return false;
        if (this.enemiesOnly.getValue() && !TeamUtil.isTarget(entityPlayer)) return false;
        return true;
    }

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Myau.friendManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Myau.targetManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        } else {
            switch (this.colorMode.getValue()) {
                case 0: return TeamUtil.getTeamColor(entityPlayer, alpha);
                case 1: {
                    int teamColor = TeamUtil.isSameTeam(entityPlayer)
                            ? ChatColors.BLUE.toAwtColor()
                            : ChatColors.RED.toAwtColor();
                    return new Color(teamColor & Color.WHITE.getRGB() | (int)(alpha * 255.0F) << 24, true);
                }
                case 2: {
                    int color = ((HUD) Myau.moduleManager.modules.get(HUD.class))
                            .getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color & Color.WHITE.getRGB() | (int)(alpha * 255.0F) << 24, true);
                }
                case 3: {
                    Color bwColor = getBedWarsTeamColor(entityPlayer, alpha);
                    if (bwColor != null) return bwColor;
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                }
                default:
                    return new Color(1.0F, 1.0F, 1.0F, alpha);
            }
        }
    }

    public Tracers() {
        super("Tracers", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.drawLines.getValue()) return;
        RenderUtil.enableRenderState();
        Vec3 position;
        if (mc.gameSettings.thirdPersonView == 0) {
            position = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationPitch,
                            mc.getRenderViewEntity().prevRotationPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))))
                    .rotateYaw((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationYaw,
                            mc.getRenderViewEntity().prevRotationYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))));
        } else {
            position = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))))
                    .rotateYaw((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))));
        }
        position = new Vec3(position.xCoord,
                position.yCoord + mc.getRenderViewEntity().getEyeHeight(),
                position.zCoord);

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRender((EntityPlayer) e))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList())) {
            Color color = getEntityColor(player, (float) opacity.getValue() / 100.0F);
            double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks());
            double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks())
                     - (player.isSneaking() ? 0.125 : 0.0);
            double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks());
            RenderUtil.drawLine3D(position,
                    x, y + player.getEyeHeight(), z,
                    color.getRed() / 255.0F,
                    color.getGreen() / 255.0F,
                    color.getBlue() / 255.0F,
                    color.getAlpha() / 255.0F,
                    1.5F);
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || !this.drawArrows.getValue()) return;
        if (mc.currentScreen != null && !this.renderInGUIs.getValue()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        float hudScale = hud.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(hudScale, hudScale, 0.0F);
        GlStateManager.translate(sr.getScaledWidth() / 2.0F / hudScale,
                                 sr.getScaledHeight() / 2.0F / hudScale, 0.0F);

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRender((EntityPlayer) e))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList())) {

            float yawBetween = RotationUtil.getYawBetween(
                    RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks()),
                    RenderUtil.lerpDouble(player.posX, player.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.getPartialTicks()));
            if (mc.gameSettings.thirdPersonView == 2) yawBetween += 180.0F;

            float arrowDirX = (float) Math.sin(Math.toRadians(yawBetween));
            float arrowDirY = (float) (Math.cos(Math.toRadians(yawBetween)) * -1.0F);

            float opacityVal = opacity.getValue().floatValue() / 100.0F;
            float absYaw = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween));
            if (absYaw < 30.0F) {
                opacityVal = 0.0F;
            } else if (absYaw < 60.0F) {
                opacityVal *= (absYaw - 30.0F) / 30.0F;
            }
            if (opacityVal <= 0.0F) continue;
            if (renderOnlyOffScreen.getValue() && absYaw < 90.0F) continue;

            Color color = getEntityColor(player, opacityVal);
            int rgb = color.getRGB();
            float red   = color.getRed()   / 255.0f;
            float green = color.getGreen() / 255.0f;
            float blue  = color.getBlue()  / 255.0f;
            float alpha = color.getAlpha() / 255.0f;

            float percent = arrowRadius.getValue().floatValue();
            float r = 30.0f + (percent / 100.0f) * 170.0f;

            float rotation = (float) (Math.atan2(arrowDirY, arrowDirX) * (180.0 / Math.PI) + 90.0);

            GlStateManager.pushMatrix();
            GlStateManager.translate(r * arrowDirX + 1.0F, r * arrowDirY + 1.0F, 0.0F);
            GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);

            RenderUtil.enableRenderState();

            switch (arrowMode.getValue()) {
                case 0: // Caret
                    GL11.glColor4f(red, green, blue, alpha);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    double halfAngle = 0.6108652353286743;
                    double size = 9.0;
                    double offsetY = 5.0;
                    GL11.glLineWidth(3.0F);
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                    GL11.glVertex2d(0.0, -offsetY);
                    GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                    GL11.glEnd();
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glDisable(GL11.GL_LINE_SMOOTH);
                    break;

                case 1: // Greater than
                    GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
                    GlStateManager.scale(1.5F, 1.5F, 1.5F);
                    mc.fontRendererObj.drawString(">", -2.0F, -4.0F, rgb, false);
                    break;

                case 2: // Triangle
                    RenderUtil.drawTriangle(0.0F, 0.0F, 0.0F, 10.0F, rgb);
                    break;

                case 3: // Slinky — wide solid filled arrow (refined)
                    final float halfWidth = 15.0F;   // total base ~30 px — very wide
                    final float height    = 7.5F;    // short & chunky
                    final float tipInset  = 2.0F;    // soft tip curve

                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    // Solid main fill
                    GL11.glColor4f(red, green, blue, alpha);
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    GL11.glVertex2f(0.0F, -height);
                    GL11.glVertex2f(-halfWidth, 0.0F);
                    GL11.glVertex2f(halfWidth, 0.0F);
                    GL11.glEnd();

                    // Subtle highlight near tip
                    float tipHighlight = 0.18F;
                    GL11.glColor4f(
                        Math.min(1.0f, red   + tipHighlight),
                        Math.min(1.0f, green + tipHighlight),
                        Math.min(1.0f, blue  + tipHighlight),
                        alpha * 0.7f
                    );
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    GL11.glVertex2f(0.0F, -height + tipInset);
                    GL11.glVertex2f(-halfWidth * 0.6F, -1.0F);
                    GL11.glVertex2f( halfWidth * 0.6F, -1.0F);
                    GL11.glEnd();

                    // Thin dark outline
                    float outlineDarken = 0.28F;
                    GL11.glColor4f(red * outlineDarken, green * outlineDarken, blue * outlineDarken, alpha);
                    GL11.glLineWidth(1.6F);
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    GL11.glVertex2f(0.0F, -height);
                    GL11.glVertex2f(-halfWidth, 0.0F);
                    GL11.glVertex2f(halfWidth, 0.0F);
                    GL11.glEnd();

                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_BLEND);
                    break;
            }

            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();

            // Distance label
            if (showDistance.getValue()) {
                String text = (int) mc.thePlayer.getDistanceToEntity(player) + "m";
                GlStateManager.pushMatrix();
                GlStateManager.translate(r * arrowDirX, r * arrowDirY - 13.0F, 0.0F);
                GlStateManager.scale(0.8F, 0.8F, 0.8F);
                mc.fontRendererObj.drawString(text,
                        -(float) mc.fontRendererObj.getStringWidth(text) / 2,
                        -4.0F, -1, true);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.popMatrix();
    }
}
