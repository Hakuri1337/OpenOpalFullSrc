package wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.EnumRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.Random;

public final class OrganicRotationModel implements IRotationModel {

    private final double speed;
    private final double driftIntensity;
    private final double jitterIntensity;

    private final double freqYaw1, freqYaw2, freqPitch1, freqPitch2;
    private final double phaseYaw1, phaseYaw2, phasePitch1, phasePitch2;
    private double timeAccumulator;

    private final Random random;

    public OrganicRotationModel(final double speed, final double driftIntensity, final double jitterIntensity) {
        this.speed = speed;
        this.driftIntensity = driftIntensity;
        this.jitterIntensity = jitterIntensity;

        random = new Random(System.nanoTime());
        this.freqYaw1 = random.nextDouble() * 0.3 + 0.1;
        this.freqYaw2 = random.nextDouble() * 0.5 + 0.5;
        this.freqPitch1 = random.nextDouble() * 0.3 + 0.1;
        this.freqPitch2 = random.nextDouble() * 0.5 + 0.5;
        this.phaseYaw1 = random.nextDouble() * Math.PI * 2;
        this.phaseYaw2 = random.nextDouble() * Math.PI * 2;
        this.phasePitch1 = random.nextDouble() * Math.PI * 2;
        this.phasePitch2 = random.nextDouble() * Math.PI * 2;
        this.timeAccumulator = 0.0;
    }

    @Override
    public Vec2f tick(Vec2f from, Vec2f to, float timeDelta) {
        final float rawYaw = MathHelper.wrapDegrees(to.x - from.x);
        final float rawPitch = to.y - from.y;
        float deltaYaw = rawYaw * timeDelta;
        float deltaPitch = rawPitch * timeDelta;

        final double distance = Math.hypot(deltaYaw, deltaPitch);
        if (distance < driftIntensity) {
            return new Vec2f(from.x + deltaYaw, from.y + deltaPitch);
        }

        if (distance > 0) {
            final double ratioYaw = Math.abs(deltaYaw) / distance;
            final double ratioPitch = Math.abs(deltaPitch) / distance;
            final double maxYaw = speed * ratioYaw * timeDelta;
            final double maxPitch = speed * ratioPitch * timeDelta;
            deltaYaw = MathHelper.clamp(deltaYaw, (float)-maxYaw, (float)maxYaw);
            deltaPitch = MathHelper.clamp(deltaPitch, (float)-maxPitch, (float)maxPitch);
        }

        timeAccumulator += timeDelta;

        final double sinYaw = Math.sin(timeAccumulator * freqYaw1 + phaseYaw1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqYaw2 + phaseYaw2);
        final double sinPitch = Math.sin(timeAccumulator * freqPitch1 + phasePitch1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqPitch2 + phasePitch2);
        final double driftYaw = sinYaw * driftIntensity * timeDelta;
        final double driftPitch = sinPitch * driftIntensity * timeDelta;

        final double jitterYaw = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;
        final double jitterPitch = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;

        final float moveYaw = deltaYaw + (float) driftYaw + (float) jitterYaw;
        final float movePitch = deltaPitch + (float) driftPitch + (float) jitterPitch;

        final Vec2f rotation = new Vec2f(from.x + moveYaw, from.y + movePitch);

        return RotationUtility.patchConstantRotation(rotation, from);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.ORGANIC;
    }
}
