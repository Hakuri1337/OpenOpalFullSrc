package wtf.oraculus.client.feature.helper.impl.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

import java.util.ArrayList;
import java.util.List;

public final class FadingBlockHelper implements IHelper {

    private static final Vec3d BLOCK_DIMENSIONS = new Vec3d(1, 1, 1);
    private final List<FadingBlock> fadingBlocks = new ArrayList<>();

    private FadingBlockHelper() {
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        this.fadingBlocks.removeIf(FadingBlock::isRemovable);

        VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        WorldRenderer rc = new WorldRenderer(vcp);

        for (final FadingBlock fadingBlock : this.fadingBlocks) {
            final float progress = (float) fadingBlock.getRemainingLifetime() / fadingBlock.lifetime;
            final float fillAlpha = ((fadingBlock.fillColor >> 24) & 0xFF) / 255.F;
            final float outlineAlpha = ((fadingBlock.outlineColor >> 24) & 0xFF) / 255.F;

            final Vec3d startVec = new Vec3d(fadingBlock.blockPos.getX(), fadingBlock.blockPos.getY(), fadingBlock.blockPos.getZ());
            rc.drawFilledCube(event.matrixStack(), CustomRenderLayers.getPositionColorQuads(true), startVec, BLOCK_DIMENSIONS, ColorUtility.applyOpacity(fadingBlock.fillColor, fillAlpha * progress));
        }

        vcp.draw();

    }

    public void addFadingBlock(final FadingBlock fadingBlock) {
        this.fadingBlocks.add(fadingBlock);
    }

    public static class FadingBlock {

        private final Stopwatch stopwatch = new Stopwatch();

        private final BlockPos blockPos;
        private final int outlineColor, fillColor;
        private final long lifetime;

        public FadingBlock(final BlockPos blockPos, final int outlineColor, final int fillColor, final long lifetime) {
            this.blockPos = blockPos;
            this.outlineColor = outlineColor;
            this.fillColor = fillColor;
            this.lifetime = lifetime;
            this.stopwatch.reset();
        }

        public long getRemainingLifetime() {
            return Math.max(0, this.lifetime - this.stopwatch.getTime());
        }

        public boolean isRemovable() {
            return this.stopwatch.hasTimeElapsed(this.lifetime);
        }

    }

    private static FadingBlockHelper instance;

    public static FadingBlockHelper getInstance() {
        return instance;
    }

    public static void setInstance() {
        instance = new FadingBlockHelper();
        EventDispatcher.subscribe(instance);
    }

}
