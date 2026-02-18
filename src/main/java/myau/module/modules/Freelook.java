package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

/**
 * FreeLook - Allows free camera rotation while keeping movement direction locked.
 * Hold or toggle mode, smooth return option.
 */
public class Freelook extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Properties ───────────────────────────────────────────────────────────────
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{
        "Hold",      // Hold key to freelook
        "Toggle"     // Press once to toggle on/off
    });

    public final PercentProperty sensitivity = new PercentProperty("Sensitivity", 100);

    public final BooleanProperty smoothReturn = new BooleanProperty("Smooth Return", true);

    public final PercentProperty returnSpeed = new PercentProperty("Return Speed", 50);

    // ── Internal state ───────────────────────────────────────────────────────────
    private boolean isActive = false;
    private float currentYawOffset = 0.0f;
    private float currentPitchOffset = 0.0f;
    private float targetYawOffset = 0.0f;
    private float targetPitchOffset = 0.0f;

    public Freelook() {
        super("Freelook", true);
        setKeybind(Keyboard.KEY_F);
    }

    @Override
    public void onEnable() {
        resetOffsets();
    }

    @Override
    public void onDisable() {
        resetOffsets();
        isActive = false;
    }

    private void resetOffsets() {
        currentYawOffset = 0.0f;
        currentPitchOffset = 0.0f;
        targetYawOffset = 0.0f;
        targetPitchOffset = 0.0f;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.PRE) return;

        // ── Activation logic ─────────────────────────────────────────────────────
        if (mode.getValue() == 0) { // Hold
            isActive = isKeybindDown();
        } else { // Toggle
            if (isKeyPressedThisTick()) {
                isActive = !isActive;
            }
        }

        // ── Smooth return when freelook ends ─────────────────────────────────────
        if (!isActive && smoothReturn.getValue()) {
            targetYawOffset = 0.0f;
            targetPitchOffset = 0.0f;

            float speed = returnSpeed.getValue().floatValue() / 100f * 0.25f;
            currentYawOffset   += (targetYawOffset   - currentYawOffset)   * speed;
            currentPitchOffset += (targetPitchOffset - currentPitchOffset) * speed;

            if (Math.abs(currentYawOffset)   < 0.05f) currentYawOffset   = 0.0f;
            if (Math.abs(currentPitchOffset) < 0.05f) currentPitchOffset = 0.0f;
        }

        // ── Poll mouse movement when freelook is active ──────────────────────────
        if (isActive && mc.currentScreen == null) {
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();

            if (dx != 0 || dy != 0) {
                float sensMult = sensitivity.getValue().floatValue() / 100f;
                float yawDelta   = dx   * 0.15f * sensMult;
                float pitchDelta = dy   * 0.15f * sensMult * -1f; // invert Y

                targetYawOffset   += yawDelta;
                targetPitchOffset += pitchDelta;

                // Clamp pitch to normal limits
                targetPitchOffset = Math.max(-90.0f, Math.min(90.0f, targetPitchOffset));
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isActive) return;

        // Apply camera offsets without changing player body rotation
        mc.thePlayer.rotationYawHead   = mc.thePlayer.rotationYaw   + currentYawOffset;
        mc.thePlayer.rotationPitchHead = mc.thePlayer.rotationPitch + currentPitchOffset;

        // Smooth interpolation current → target
        float lerpFactor = 0.85f;
        currentYawOffset   = lerp(currentYawOffset,   targetYawOffset,   lerpFactor);
        currentPitchOffset = lerp(currentPitchOffset, targetPitchOffset, lerpFactor);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private boolean isKeyPressedThisTick() {
        return getKeybind().isPressed();
    }

    private boolean isKeybindDown() {
        return getKeybind().isKeyDown();
    }

    // HUD suffix
    @Override
    public String[] getSuffix() {
        if (!isActive) return new String[0];
        return new String[]{ mode.getValue() == 
