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

        // When activated, store real rotation once
        if (isActive && mc.currentScreen == null) {
            if (!wasPressed) {
                realYaw = mc.thePlayer.rotationYaw;
                realPitch = mc.thePlayer.rotationPitch;

                cameraYaw = realYaw;
                cameraPitch = realPitch;
            }

            // Mouse movement
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();

            float sens = sensitivity.getValue().floatValue() / 100f;

            camera
