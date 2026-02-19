package myau.module;

import net.minecraft.client.Minecraft;

public abstract class Module {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private boolean enabled = false;

    public Module(String name) {
        this.name = name;
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public void setEnabled(boolean enabled) {
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

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onUpdate() {
    }

    public void onRender2D() {
    }

    public String getName() {
        return name;
    }

    // If your project later needs these (from config/clickgui errors), you can add dummies:
    // public java.util.List getSettings() { return new java.util.ArrayList(); }
    // public int getKey() { return 0; }
    // public void setKey(int key) {}
    // public boolean isHidden() { return false; }
    // public void setHidden(boolean hidden) {}
}
