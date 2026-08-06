package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.shape.VoxelShape;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class SafeWalkModule extends Module {

    private static final double EDGE_OFFSET_Y = -0.5D;
    private static final float EDGE_INSET = 0.3F;

    public SafeWalkModule() {
        super("Safe Walk", "Prevents walking off ledges by forcing sneak on block edges.", ModuleCategory.MOVEMENT);
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        mc.options.sneakKey.setPressed(mc.player.isOnGround() && this.isOnBlockEdge(EDGE_INSET));
    }

    @Override
    protected void onDisable() {
        this.restoreSneakKeyState();
        super.onDisable();
    }

    private boolean isOnBlockEdge(final float inset) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        final Iterable<VoxelShape> collisions = mc.world.getBlockCollisions(
                mc.player,
                mc.player.getBoundingBox()
                        .offset(0.0D, EDGE_OFFSET_Y, 0.0D)
                        .expand(-inset, 0.0D, -inset)
        );

        return !collisions.iterator().hasNext();
    }

    private void restoreSneakKeyState() {
        if (mc.options == null || mc.getWindow() == null) {
            return;
        }

        final KeyBinding sneakKey = mc.options.sneakKey;
        final boolean pressed = PlayerUtility.isKeyPressed(sneakKey);

        sneakKey.setPressed(pressed);
    }
}
