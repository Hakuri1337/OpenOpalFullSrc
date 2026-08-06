package wtf.oraculus.client.feature.helper.impl.target.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import wtf.oraculus.client.feature.helper.impl.target.TargetFlags;

public class TargetLivingEntity extends Target<LivingEntity> {
    public TargetLivingEntity(LivingEntity entity) {
        super(entity);
    }

    @Override
    public boolean isMatchingFlags(int flags) {
        if (this.entity instanceof HostileEntity) {
            return (flags & TargetFlags.HOSTILE) != 0;
        }
        return (flags & TargetFlags.PASSIVE) != 0;
    }

    public float getFullHealth() {
        return this.entity.getHealth() + this.entity.getAbsorptionAmount();
    }
}
