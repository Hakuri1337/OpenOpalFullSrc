package wtf.oraculus.client.feature.module.impl.combat.killaura.target;

import net.minecraft.entity.LivingEntity;
import wtf.oraculus.utility.player.RaytracedRotation;

public final class CurrentTarget {
    private final KillAuraTarget target;
    private final RaytracedRotation rotations;

    public CurrentTarget(KillAuraTarget target, RaytracedRotation rotations) {
        this.target = target;
        this.rotations = rotations;
    }

    public KillAuraTarget getKillAuraTarget() {
        return target;
    }

    public LivingEntity getEntity() {
        return getKillAuraTarget().getTarget().getEntity();
    }

    public RaytracedRotation getRotations() {
        return rotations;
    }
}
