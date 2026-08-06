package wtf.oraculus.utility.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4d;
import org.joml.Vector4f;
import mixin.GameRendererAccessor;
import wtf.oraculus.utility.misc.math.MathUtility;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.enchantment.Enchantments.*;
import static wtf.oraculus.client.Constants.mc;

public class ESPUtility {

    private ESPUtility() {
    }

    public static Vector4d getEntityPositionsOn2D(LivingEntity target, float tickDelta) {
        final int[] viewport = new int[]{0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight()};
        final MatrixStack matrixStack = ESPUtility.createMatrixStack(tickDelta);

        final Matrix4f projectionMatrix = matrixStack.peek().getPositionMatrix();

        final Vec3d position = MathUtility.interpolate(target, tickDelta);

        final float width = target.getWidth() / 2f;
        final float height = target.getHeight() + (target.isInSneakingPose() ? 0.1f : 0.2f);

        final Box boundingBox = new Box(
                position.x - width,
                position.y,
                position.z - width,
                position.x + width,
                position.y + height,
                position.z + width
        );

        final Vector4d projection = ESPUtility.projectEntity(viewport, projectionMatrix, boundingBox);

        projection.div(mc.getWindow().getScaleFactor());

        projection.z -= projection.x;
        projection.w -= projection.y;

        return projection;
    }

    public static Vector4d projectEntity(final int[] viewport, final Matrix4f matrix, final Box boundingBox) {
        final Vector4f windowCoords = new Vector4f();

        final List<Vec3d> list = getBoxBounds(boundingBox);
        Vector4d projected = null;

        for (final Vec3d pos : list) {
            matrix.project(pos.toVector3f(), viewport, windowCoords);
            windowCoords.y = viewport[3] - windowCoords.y;

            if (windowCoords.w != 1) {
                break;
            }

            if (projected == null) {
                projected = new Vector4d(windowCoords.x, windowCoords.y, 0, 0);
            } else {
                final double windowX = windowCoords.x;
                final double windowY = windowCoords.y;

                projected.x = Math.min(windowX, projected.x);
                projected.y = Math.min(windowY, projected.y);
                projected.z = Math.max(windowX, projected.z);
                projected.w = Math.max(windowY, projected.w);
            }
        }

        return projected;
    }

    public static MatrixStack createMatrixStack(final float tickDelta) {
        GameRendererAccessor gameRendererAccessor = (GameRendererAccessor) mc.gameRenderer;
        MatrixStack matrixStack = new MatrixStack();
        final Camera camera = mc.gameRenderer.getCamera();

        float fov = gameRendererAccessor.callGetFov(camera, tickDelta, true);

        matrixStack.multiplyPositionMatrix(mc.gameRenderer.getBasicProjectionMatrix(fov));

        gameRendererAccessor.callTiltViewWhenHurt(matrixStack, camera.getLastTickProgress());

        if (mc.options.getBobView().getValue())
            gameRendererAccessor.callBobView(matrixStack, camera.getLastTickProgress());

        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        return matrixStack;
    }

    public static List<Vec3d> getBoxBounds(final Box boundingBox) {
        return Arrays.asList(
                new Vec3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ),
                new Vec3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ),
                new Vec3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ),
                new Vec3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ),
                new Vec3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ),
                new Vec3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ),
                new Vec3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ),
                new Vec3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ)
        );
    }

    public static final Map<RegistryKey<Enchantment>, String> ENCHANTMENT_NAMES = new HashMap<>() {{
        put(PROTECTION, "Pr");
        put(FIRE_PROTECTION, "Fp");
        put(FEATHER_FALLING, "Ff");
        put(BLAST_PROTECTION, "Bp");
        put(PROJECTILE_PROTECTION, "Pp");
        put(RESPIRATION, "Re");
        put(AQUA_AFFINITY, "Aa");
        put(THORNS, "Th");
        put(DEPTH_STRIDER, "Ds");
        put(FROST_WALKER, "Fw");
        put(BINDING_CURSE, "Bc");
        put(SOUL_SPEED, "Ss");
        put(SWIFT_SNEAK, "Sn");
        put(SHARPNESS, "Sh");
        put(SMITE, "Sm");
        put(BANE_OF_ARTHROPODS, "BoA");
        put(KNOCKBACK, "Kb");
        put(FIRE_ASPECT, "Fa");
        put(LOOTING, "Lo");
        put(SWEEPING_EDGE, "Sw");
        put(EFFICIENCY, "Ef");
        put(SILK_TOUCH, "St");
        put(UNBREAKING, "Un");
        put(FORTUNE, "Fo");
        put(POWER, "Po");
        put(PUNCH, "Pu");
        put(FLAME, "Fl");
        put(INFINITY, "In");
        put(LUCK_OF_THE_SEA, "Lu");
        put(LURE, "Lr");
        put(LOYALTY, "Ly");
        put(IMPALING, "Ip");
        put(RIPTIDE, "Ri");
        put(CHANNELING, "Ch");
        put(MULTISHOT, "Mu");
        put(QUICK_CHARGE, "Qc");
        put(PIERCING, "Pi");
        put(MENDING, "Me");
        put(VANISHING_CURSE, "Vc");
    }};

}
