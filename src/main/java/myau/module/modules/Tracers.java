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
import myau.property.properties.SliderProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.stream.Collectors;

public class Tracers extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final BooleanProperty drawLines = new BooleanProperty("lines", true);
    public final BooleanProperty drawArrows = new BooleanProperty("arrows", false);
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);
    public final SliderProperty arrowRadius = new SliderProperty("radius", 50.0, 30.0, 200.0, 5.0);
    public final ModeProperty arrowMode = new ModeProperty("arrow", 2, new String[]{"Caret", "Greater than", "Triangle"});
    public final BooleanProperty showDistance = new BooleanProperty("distance", true);
    public final BooleanProperty hideTeammates = new BooleanProperty("hide teammates", true);
    public final BooleanProperty enemiesOnly = new BooleanProperty("enemies only", false);
    public final BooleanProperty renderOnlyOffScreen = new BooleanProperty("only offscreen", false);
    public final BooleanProperty renderInGUIs = new BooleanProperty("in GUIs", false);

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer == mc.thePlayer || entityPlayer == mc.getRenderViewEntity()) {
            return false;
        } else {
            if (TeamUtil.isBot(entityPlayer)) {
                if (!this.showBots.getValue()) return false;
            }
            if (TeamUtil.isSameTeam(entityPlayer) && this.hideTeammates.getValue()) {
                return false;
            }
            if (TeamUtil.isFriend(entityPlayer)) {
                if (!this.showFriends.getValue()) return false;
            }
            if (TeamUtil.isTarget(entityPlayer)) {
                if (!this.showEnemies.getValue()) return false;
            } else {
                if (!this.showPlayers.getValue()) return false;
            }
            if (this.enemiesOnly.getValue() && !TeamUtil.isTarget(entityPlayer)) {
                return false;
            }
            return true;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Myau.friendManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Myau.targetManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else {
            switch (this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                case 2:
                    int color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
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
        if (this.isEnabled() && this.drawLines.getValue()) {
            RenderUtil.enableRenderState();
            Vec3 position;
            if (mc.gameSettings.thirdPersonView == 0) {
                position = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                position = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                                        )
                                )
                        );
            }
            position = new Vec3(position.xCoord, position.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), position.zCoord);
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                Color color = this.getEntityColor(player, (float) this.opacity.getValue() / 100.0F);
                double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - (player.isSneaking() ? 0.125 : 0.0);
                double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks());
                RenderUtil.drawLine3D(
                        position,
                        x,
                        y + (double) player.getEyeHeight(),
                        z,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        (float) color.getAlpha() / 255.0F,
                        1.5F
                );
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.drawArrows.getValue()) {
            if (mc.currentScreen != null && !this.renderInGUIs.getValue()) return;

            ScaledResolution sr = new ScaledResolution(mc);
            HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
            float hudScale = hud.scale.getValue();

            GlStateManager.pushMatrix();
            GlStateManager.scale(hudScale, hudScale, 0.0F);
            GlStateManager.translate((float) sr.getScaledWidth() / 2.0F / hudScale, (float) sr.getScaledHeight() / 2.0F / hudScale, 0.0F);

            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                float yawBetween = RotationUtil.getYawBetween(
                        RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks()),
                        RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks()),
                        RenderUtil.lerpDouble(player.posX, player.prevPosX, event.getPartialTicks()),
                        RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.getPartialTicks())
                );
                if (mc.gameSettings.thirdPersonView == 2) {
                    yawBetween += 180.0F;
                }
                float arrowDirX = (float) Math.sin(Math.toRadians(yawBetween));
                float arrowDirY = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0F;
                float opacityVal = this.opacity.getValue().floatValue() / 100.0F;
                yawBetween = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween));
                if (yawBetween < 30.0F) {
                    opacityVal = 0.0F;
                } else if (yawBetween < 60.0F) {
                    opacityVal *= (yawBetween - 30.0F) / 30.0F;
                }
                if (opacityVal == 0.0F) continue;
                if (this.renderOnlyOffScreen.getValue() && yawBetween < 90.0F) continue;

                Color color = this.getEntityColor(player, opacityVal);
                int rgb = color.getRGB();
                float red = (float) color.getRed() / 255.0F;
                float green = (float) color.getGreen() / 255.0F;
                float blue = (float) color.getBlue() / 255.0F;
                float alpha = (float) color.getAlpha() / 255.0F;

                float r = (float) this.arrowRadius.getInput();

                GlStateManager.pushMatrix();
                GlStateManager.translate(r * arrowDirX + 1.0F, r * arrowDirY + 1.0F, 0.0F);
                float rotation = (float) (Math.atan2(arrowDirY, arrowDirX) * (180.0 / Math.PI) + 90.0);
                GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
                RenderUtil.enableRenderState();

                switch (this.arrowMode.getValue()) {
                    case 0: // Caret
                        GL11.glColor4f(red, green, blue, alpha);
                        GL11.glEnable(3042);
                        GL11.glDisable(3553);
                        GL11.glBlendFunc(770, 771);
                        GL11.glEnable(2848);
                        double halfAngle = 0.6108652353286743;
                        double size = 9.0;
                        double offsetY = 5.0;
                        GL11.glLineWidth(3.0F);
                        GL11.glBegin(3);
                        GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                        GL11.glVertex2d(0.0, -offsetY);
                        GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                        GL11.glEnd();
                        GL11.glEnable(3553);
                        GL11.glDisable(3042);
                        GL11.glDisable(2848);
                        break;
                    case 1: // Greater than
                        GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
                        GlStateManager.scale(1.5F, 1.5F, 1.5F);
                        mc.fontRendererObj.drawString(">", -2.0F, -4.0F, rgb, false);
                        break;
                    case 2: // Triangle
                        RenderUtil.drawTriangle(0.0F, 0.0F, 0.0F, 10.0F, rgb);
                        break;
                }

                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();

                if (this.showDistance.getValue()) {
                    String text = (int) mc.thePlayer.getDistanceToEntity(player) + "m";
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(r * arrowDirX, r * arrowDirY - 13.0F, 0.0F);
                    GlStateManager.scale(0.8F, 0.8F, 0.8F);
                    mc.fontRendererObj.drawString(text, - (float) mc.fontRendererObj.getStringWidth(text) / 2, -4.0F, -1, true);
                    GlStateManager.popMatrix();
                }
            }

            GlStateManager.popMatrix();
        }
    }
}
