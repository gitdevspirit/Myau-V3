package myau.module;

import net.minecraft.client.Minecraft;

public abstract class Module {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private boolean enabled = false;  // toggled state

    // Animation fields
    private float animationProgress = 0.0f;  // 0.0 hidden → 1.0 visible

    public Module(String name) {
        this.name = name;
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Animation update (smooth slide/fade) - call every tick
    public void updateAnimation() {
        float target = isEnabled() ? 1.0f : 0.0f;
        // Manual lerp (no MathHelper.lerp in 1.8.9)
        animationProgress += (target - animationProgress) * 0.14f;
        // Clamp manually
        if (animationProgress < 0.0f) animationProgress = 0.0f;
        if (animationProgress > 1.0f) animationProgress = 1.0f;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onUpdate() {}     // can call updateAnimation() here as fallback
    public void onRender2D() {}

    public String getName() {
        return name;
    }

    // If needed later: add keybind, hidden, etc.
}
