package wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;

public final class LinearRotationModel implements IRotationModel {
    private final double speed;

    public LinearRotationModel(double speed) {
        this.speed = speed;
    }

    @Override
    public Vec2f tick(Vec2f from, Vec2f to, float timeDelta) {
        final float deltaYaw = MathHelper.wrapDegrees(to.x - from.x) * timeDelta;
        final float deltaPitch = (to.y - from.y) * timeDelta;

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance == 0.D) {
            return new Vec2f(from.x + deltaYaw, from.y + deltaPitch);
        }
        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = this.speed * distributionYaw;
        final double maxPitch = this.speed * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Vec2f(from.x + moveYaw, from.y + movePitch);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.LINEAR;
    }
}
