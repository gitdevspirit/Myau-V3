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
    public final BooleanProperty invertPitch = new BooleanProperty("Invert Pitch", false);
    public final BooleanProperty smoothReturn = new BooleanProperty("Smooth Return", true);
    public final PercentProperty returnSpeed = new PercentProperty("Return Speed", 50);

    private boolean isActive = false;
    private boolean wasPressed = false;

    private float cameraYaw;
    private float cameraPitch;
    private float realYaw;
    private float realPitch;

    private float prevCameraYaw;   // for smooth interpolation if needed
    private float prevCameraPitch;

    public Freelook() {
        super("Freelook", true);
        this.setKey(Keyboard.KEY_6);  // default to 6
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;

        boolean keyDown = Keyboard.isKeyDown(this.getKey());

        if (mode.getValue() == 0) { // Hold
            isActive = keyDown && mc.currentScreen == null;
        } else { // Toggle
            if (keyDown && !wasPressed) {
                isActive = !isActive;
                wasPressed = true;
            }
            if (!keyDown) {
                wasPressed = false;
            }
        }

        if (isActive && mc.currentScreen == null) {

            // On activation start: capture real angles
            if (!wasPressed || !wasActiveLastTick) {
                realYaw   = mc.thePlayer.rotationYaw;
                realPitch = mc.thePlayer.rotationPitch;
                cameraYaw   = realYaw;
                cameraPitch = realPitch;
                prevCameraYaw   = cameraYaw;
                prevCameraPitch = cameraPitch;
            }

            // Mouse deltas
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();

            float sens = sensitivity.getValue().floatValue() / 100f * 0.15f;

            cameraYaw += dx * sens;

            float pitchDelta = dy * sens;
            if (invertPitch.getValue()) {
                pitchDelta = -pitchDelta;
            }
            cameraPitch -= pitchDelta;  // MC pitch is negative-up

            cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));

            // Freeze player body / movement direction completely
            mc.thePlayer.rotationYaw   = realYaw;
            mc.thePlayer.rotationPitch = realPitch;
            mc.thePlayer.prevRotationYaw   = realYaw;
            mc.thePlayer.prevRotationPitch = realPitch;

            // Also freeze render offsets that affect movement/legs
            mc.thePlayer.renderYawOffset     = realYaw;
            mc.thePlayer.rotationYawHead     = realYaw;  // head starts at body direction
        }

        // When deactivating → smooth return to real angles
        if (!isActive) {
            if (smoothReturn.getValue()) {
                float speed = returnSpeed.getValue().floatValue() / 100f * 0.4f;  // slightly faster feel
                cameraYaw   += (realYaw   - cameraYaw)   * speed;
                cameraPitch += (realPitch - cameraPitch) * speed;

                // Snap when very close to avoid micro-jitter
                if (Math.abs(cameraYaw - realYaw) < 0.4f && Math.abs(cameraPitch - realPitch) < 0.4f) {
                    cameraYaw   = realYaw;
                    cameraPitch = realPitch;
                }
            } else {
                cameraYaw   = realYaw;
                cameraPitch = realPitch;
            }
        }

        wasActiveLastTick = isActive;
    }

    private boolean wasActiveLastTick = false;

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;

        if (isActive || (smoothReturn.getValue() && Math.abs(cameraYaw - realYaw) > 0.1f)) {
            // Apply to head only → this rotates the camera/view in first person
            // without turning body/movement direction
            mc.thePlayer.rotationYawHead = cameraYaw;

            // Optional: also set renderYawOffset if legs/arms look wrong in 1st person mods
            // mc.thePlayer.renderYawOffset = cameraYaw;  // usually leave this at realYaw
        } else {
            // Ensure reset when inactive and no smoothing left
            mc.thePlayer.rotationYawHead = realYaw;
        }
    }

    @Override
    public void onDisabled() {
        isActive = false;
        wasPressed = false;
        wasActiveLastTick = false;

        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
            mc.thePlayer.renderYawOffset = mc.thePlayer.rotationYaw;
        }
    }

    @Override
    public String[] getSuffix() {
        if (!isActive) return new String[0];
        return new String[]{ mode.getValue() == 0 ? "HOLD" : "ON" };
    }
}
