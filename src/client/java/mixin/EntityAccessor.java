package mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("POSE")
    static TrackedData<EntityPose> getTrackedPose() {
        throw new AssertionError();
    }

    @Accessor("FLAGS")
    static TrackedData<Byte> getFlags() {
        throw new AssertionError();
    }

    @Accessor
    void setPos(final Vec3d pos);

    @Invoker
    Box callCalculateDefaultBoundingBox(final Vec3d pos);
}
