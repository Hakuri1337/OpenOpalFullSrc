package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import wtf.oraculus.client.feature.helper.impl.render.FrustumHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.duck.ClientPlayerInteractionManagerAccess;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.render.ESPUtility;

import static wtf.oraculus.client.Constants.mc;

public final class BreakProgressModule extends Module {

    public BreakProgressModule() {
        super("Break Progress", "Shows the progress of the block you're breaking.", ModuleCategory.VISUAL);
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        if (mc.interactionManager == null || !mc.interactionManager.isBreakingBlock()) {
            return;
        }

        final ClientPlayerInteractionManagerAccess access = (ClientPlayerInteractionManagerAccess) mc.interactionManager;
        final String breakProgress = (int) Math.ceil(access.oraculus$currentBreakingProgress() * 100) + "%";
        final BlockPos blockPos = access.oraculus$getCurrentBreakingPos();

        final float tickDelta = event.tickDelta();
        final Frustum frustum = FrustumHelper.get();

        if (frustum == null) {
            return;
        }

        final Box blockBox = PlayerUtility.getBlockBox(blockPos);
        if (!frustum.isVisible(blockBox)) {
            return;
        }

        final Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        final float relX = (float) (blockPos.getX() - cameraPos.x + 0.5);
        final float relY = (float) (blockPos.getY() - cameraPos.y + 0.5);
        final float relZ = (float) (blockPos.getZ() - cameraPos.z + 0.5);

        final Vector3f relativePoint = new Vector3f(relX, relY, relZ);

        final int[] viewport = new int[]{0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight()};

        final MatrixStack projectionStack = ESPUtility.createMatrixStack(tickDelta);
        final Matrix4f projectionMatrix = projectionStack.peek().getPositionMatrix();

        final Vector4f windowCoords = new Vector4f();
        projectionMatrix.project(relativePoint, viewport, windowCoords);

        windowCoords.y = viewport[3] - windowCoords.y;

        final float scaleFactor = (float) mc.getWindow().getScaleFactor();
        windowCoords.x /= scaleFactor;
        windowCoords.y /= scaleFactor;

        MinecraftRenderer.addToQueue(() -> {
            event.drawContext().drawText(
                    mc.textRenderer,
                    Text.literal(breakProgress).asOrderedText(),
                    (int) windowCoords.x - mc.textRenderer.getWidth(breakProgress) / 2,
                    (int) windowCoords.y - mc.textRenderer.fontHeight / 2,
                    -1,
                    true
            );
        });
    }

}
