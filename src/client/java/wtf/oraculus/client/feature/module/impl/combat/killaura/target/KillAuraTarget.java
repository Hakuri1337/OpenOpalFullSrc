package wtf.oraculus.client.feature.module.impl.combat.killaura.target;

import org.jetbrains.annotations.Nullable;
import wtf.oraculus.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class KillAuraTarget {
    private final TargetLivingEntity target;
    private @Nullable LastAttackData lastAttackData;

    public KillAuraTarget(TargetLivingEntity target) {
        this.target = target;
    }

    public TargetLivingEntity getTarget() {
        return target;
    }

    public void onAttack(final boolean reset) {
        final double damage = this.getDamage();
        if (this.lastAttackData == null) {
            this.lastAttackData = new LastAttackData(damage);
        } else {
            this.lastAttackData.reset(reset, damage);
        }
    }

    public boolean isAttackAvailable() {
        if (this.lastAttackData == null) {
            return true;
        }
        final double damage = this.getDamage();
        if (this.target.getFullHealth() <= damage) {
            return true;
        }
        return this.lastAttackData.getTime() >= 470L || damage > this.lastAttackData.getDamage();
    }

    public double getDamage() {
        double damage = PlayerUtility.getStackAttackDamage(mc.player.getMainHandStack());
        if (damage < 0.5D) {
            damage = 0.5D;
        }
        if (PlayerUtility.isCriticalHitAvailable() && mc.player.fallDistance > 0.0F) {
            damage *= 1.5D;
        }
        return damage;
    }

    public @Nullable LastAttackData getLastAttackData() {
        return lastAttackData;
    }
}
