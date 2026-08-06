package wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;


public class HypixelRotationModel implements IRotationModel {


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

        final double maxYaw = this.getSpeed() * distributionYaw;
        final double maxPitch = this.getSpeed() * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Vec2f(from.x + moveYaw, from.y + movePitch);
    }

    private float getSpeed() {

        return isYawDiagonal() ? (LocalDataWatch.get().airTicks == 1 ? 65f : 36f)  : 35f;
    }

    private boolean isYawDiagonal() {
        final float direction = Math.abs(MoveUtility.getDirectionDegrees() % 90);
        final int range = 30;
        return direction > 45 - range && direction < 45 + range;
    }

    @Override
    public EnumRotationModel getEnum() {
        return null;
    }
}
