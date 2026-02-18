package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Freelook extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Hold", "Toggle"});
    public final PercentProperty sensitivity = new PercentProperty("Sensitivity", 100);
    public final BooleanProperty smoothReturn = new BooleanProperty("Smooth Return", true);
    public final PercentProperty returnSpeed = new PercentProperty("Return Speed", 50);

    private boolean isActive = false;
    private boolean wasPressed = false;

    private float cameraYaw;
    private float cameraPitch;

    private float realYaw;
    private float realPitch;

    private int previousPerspective = 0;

    public Freelook() {
        super("Freelook", true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;

        // Activation logic
        if (mode.getValue() == 0) {
            isActive = Keyboard.isKeyDown(Keyboard.KEY_F);
        } else {
            if (Keyboard.isKeyDown(Keyboard.KEY_F)) {
                if (!wasPressed) {
                    isActive = !isActive;
                    wasPressed = true;
                }
            } else {
                wasPressed = false;
            }
        }

        // When activated
        if (isActive && mc.currentScreen == null) {

            // First tick of activation
            if (!wasPressed) {
                realYaw = mc.thePlayer.rotationYaw;
                realPitch = mc.thePlayer.rotationPitch;

                cameraYaw = realYaw;
                cameraPitch = realPitch;

                previousPerspective = mc.gameSettings.thirdPersonView;
                mc.gameSettings.thirdPersonView = 1; // third-person freelook
            }

            // Mouse movement
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();

            float sens = sensitivity.getValue().floatValue() / 100f;

            cameraYaw += dx * 0.15f * sens;
            cameraPitch -= dy * 0.15f * sens;
            cameraPitch = Math.max(-90, Math.min(90, cameraPitch));

            // Freeze real rotation
            mc.thePlayer.rotationYaw = realYaw;
            mc.thePlayer.rotationPitch = realPitch;

        } else if (!isActive) {

            // Smooth return
            if (smoothReturn.getValue()) {
                float speed = returnSpeed.getValue().floatValue() / 100f * 0.25f;

                cameraYaw += (realYaw - cameraYaw) * speed;
                cameraPitch += (realPitch - cameraPitch) * speed;
            } else {
                cameraYaw = realYaw;
                cameraPitch = realPitch;
            }

            // Restore perspective
            mc.gameSettings.thirdPersonView = previousPerspective;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;

        if (isActive) {
            mc.thePlayer.rotationYawHead = cameraYaw;
            mc.thePlayer.renderYawOffset = cameraYaw;
        }
    }

    @Override
    public void onDisabled() {
        isActive = false;
        wasPressed = false;

        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    @Override
    public String[] getSuffix() {
        if (!isActive) return new String[0];
        return new String[]{ mode.getValue() == 0 ? "HOLD" : "ON" };
    }
}
