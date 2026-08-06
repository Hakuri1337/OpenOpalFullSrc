package wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;

public final class InstantRotationModel implements IRotationModel {
    public static final InstantRotationModel INSTANCE = new InstantRotationModel();

    private InstantRotationModel() {
    }

    @Override
    public Vec2f tick(Vec2f from, Vec2f to, float timeDelta) { // Interpolates between ticks, finishes rotation instantly tick-wise
        final float deltaYaw = MathHelper.wrapDegrees(to.x - from.x) * timeDelta;
        final float deltaPitch = (to.y - from.y) * timeDelta;
        return new Vec2f(from.x + deltaYaw, from.y + deltaPitch);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.INSTANT;
    }
}
