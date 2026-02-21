package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public class Eagle extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  edgeOffset  = new SliderSetting("Edge Offset", 0.1, 0.0, 1.0, 0.05);
    public final BooleanSetting blocksOnly  = new BooleanSetting("Blocks Only", true);
    public final BooleanSetting pitchBypass = new BooleanSetting("Pitch Bypass", true);

    public Eagle() {
        super("Eagle", false);
        register(edgeOffset);
        register(blocksOnly);
        register(pitchBypass);
    }

    private boolean isNearEdge() {
        if (!mc.thePlayer.onGround)                              return false;
        if (blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) return false;
        if (pitchBypass.getValue() && mc.thePlayer.rotationPitch > 60.0f) return false;

        float  offset = (float) edgeOffset.getValue();
        double px     = mc.thePlayer.posX;
        double py     = mc.thePlayer.posY;
        double pz     = mc.thePlayer.posZ;

        // Predict next position based on movement input
        double mx  = mc.thePlayer.motionX;
        double mz  = mc.thePlayer.motionZ;
        float  fwd = mc.thePlayer.movementInput.moveForward;
        float  str = mc.thePlayer.movementInput.moveStrafe;
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        mx += (-Math.sin(yaw) * fwd + Math.cos(yaw) * str) * 0.02;
        mz += ( Math.cos(yaw) * fwd + Math.sin(yaw) * str) * 0.02;

        double simX   = px + mx;
        double simZ   = pz + mz;
        int    floorY = (int) Math.floor(py) - 1;

        double w = 0.3 + offset;
        return cornerIsAir(simX + w, floorY, simZ + w)
            || cornerIsAir(simX + w, floorY, simZ - w)
            || cornerIsAir(simX - w, floorY, simZ + w)
            || cornerIsAir(simX - w, floorY, simZ - w);
    }

    private boolean cornerIsAir(double x, int y, double z) {
        try {
            BlockPos pos   = new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
            Block    block = mc.theWorld.getChunkFromBlockCoords(pos).getBlock(pos);
            return block == null || block instanceof BlockAir;
        } catch (Exception e) {
            return false;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;
        // Continuously hold sneak while near an edge — same pattern as Scaffold's safe-walk
        if (isNearEdge()) {
            mc.thePlayer.movementInput.sneak = true;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ String.format("%.2fb", edgeOffset.getValue()) };
    }
}