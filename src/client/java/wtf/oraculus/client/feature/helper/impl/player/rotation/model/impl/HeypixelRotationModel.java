package wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.oraculus.utility.player.RotationUtility;

public final class HeypixelRotationModel implements IRotationModel {

    private final float speed;

    public HeypixelRotationModel(final float speed) {
        this.speed = speed;
    }

    @Override
    public Vec2f tick(final Vec2f from, final Vec2f to, final float timeDelta) {
        final float deltaYaw = MathHelper.wrapDegrees(to.x - from.x);
        final float deltaPitch = to.y - from.y;

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance <= 1.0E-6D) {
            return from;
        }

        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = this.speed * distributionYaw;
        final double maxPitch = this.speed * distributionPitch;

        final float moveYaw = (float) MathHelper.clamp(deltaYaw, -maxYaw, maxYaw);
        final float movePitch = (float) MathHelper.clamp(deltaPitch, -maxPitch, maxPitch);
        final Vec2f rotation = new Vec2f(from.x + moveYaw, MathHelper.clamp(from.y + movePitch, -90.0F, 90.0F));
        return RotationUtility.patchConstantRotation(rotation, from);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.HEYPIXEL;
    }
}
