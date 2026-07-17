package wtf.opal.client.feature.module.impl.visual;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import wtf.opal.client.feature.helper.impl.render.FrustumHelper;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.render.RenderScreenEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.render.ESPUtility;

import java.util.ArrayList;
import java.util.List;

import static wtf.opal.client.Constants.mc;

/** Renders Naven-style vertical bed-defense labels. */
public final class BedPlatesModule extends Module {

    private final NumberProperty range = new NumberProperty("Range", 10.0D, 5.0D, 30.0D, 1.0D);
    private final NumberProperty layers = new NumberProperty("Layers", 1.0D, 1.0D, 5.0D, 1.0D);
    private final List<BlockInfo> obstructingBlocks = new ArrayList<>();

    public BedPlatesModule() {
        super("BedPlates", "Renders the defense above nearby beds.", ModuleCategory.VISUAL);
        this.addProperties(this.range, this.layers);
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        if (mc.player == null || mc.world == null) {
            this.obstructingBlocks.clear();
            return;
        }

        this.collectBlocks();
        this.renderLabels(event);
    }

    private void collectBlocks() {
        this.obstructingBlocks.clear();
        final BlockPos playerPos = mc.player.getBlockPos();
        final int radius = this.range.getValue().intValue();
        final int maxLayers = this.layers.getValue().intValue();

        for (final BlockPos pos : BlockPos.iterate(
                playerPos.add(-radius, -radius, -radius),
                playerPos.add(radius, radius, radius))) {
            final BlockState state = mc.world.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock) || state.get(BedBlock.PART) != BedPart.FOOT) {
                continue;
            }

            for (int layer = 0; layer < maxLayers; layer++) {
                final BlockPos protectedPos = pos.up(layer + 1);
                final BlockState protection = mc.world.getBlockState(protectedPos);
                if (protection.isAir() || protection.getBlock() instanceof BedBlock) {
                    continue;
                }
                this.obstructingBlocks.add(new BlockInfo(
                        protection.getBlock().getName().getString(), protectedPos.toImmutable(), layer));
            }
        }
    }

    private void renderLabels(final RenderScreenEvent event) {
        final Frustum frustum = FrustumHelper.get();
        if (frustum == null) {
            return;
        }

        final MatrixStack projectionStack = ESPUtility.createMatrixStack(event.tickDelta());
        final Matrix4f projection = projectionStack.peek().getPositionMatrix();
        final int[] viewport = {0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight()};
        final float scale = (float) mc.getWindow().getScaleFactor();
        final Vec3d camera = mc.gameRenderer.getCamera().getPos();

        for (final BlockInfo info : this.obstructingBlocks) {
            final Box box = new Box(info.pos);
            if (!frustum.isVisible(box)) {
                continue;
            }

            final Vec3d center = Vec3d.ofCenter(info.pos);
            final Vector4f screen = new Vector4f();
            projection.project(new Vector3f(
                    (float) (center.x - camera.x),
                    (float) (center.y - camera.y),
                    (float) (center.z - camera.z)), viewport, screen);
            if (screen.z < 0.0F || screen.z > 1.0F) {
                continue;
            }

            final String label = info.layer == 0 ? info.name : "L" + (info.layer + 1) + " " + info.name;
            final int x = (int) (screen.x / scale) - mc.textRenderer.getWidth(label) / 2;
            final int y = (int) ((viewport[3] - screen.y) / scale) - mc.textRenderer.fontHeight / 2;
            event.drawContext().drawText(mc.textRenderer, Text.literal(label), x, y, -1, true);
        }
    }

    private record BlockInfo(String name, BlockPos pos, int layer) {
    }
}
