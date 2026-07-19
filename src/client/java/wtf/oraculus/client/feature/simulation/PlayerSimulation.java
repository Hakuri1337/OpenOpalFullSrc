package wtf.oraculus.client.feature.simulation;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.module.impl.movement.MovementFixModule;
import wtf.oraculus.mixin.LivingEntityAccessor;

import java.util.UUID;

import static wtf.oraculus.client.Constants.mc;

// TODO: make this use move flying calculations so we can predict other players, not just the local player
public final class PlayerSimulation {

    private OtherClientPlayerEntity simulatedEntity;
    private final PlayerEntity player;

    public PlayerSimulation(final PlayerEntity player) {
        if (mc.world == null) {
            this.player = null;
            return;
        }

        final GameProfile profile = new GameProfile(UUID.randomUUID(), "Simulated Player");

        this.simulatedEntity = new OtherClientPlayerEntity(mc.world, profile) {
            @Override
            public void pushAwayFrom(Entity entity) {

            }
            @Override
            protected void pushAway(Entity entity) {
            }
        };

        this.player = player;
        cloneStates();
    }

    private void cloneStates() {
        this.simulatedEntity.noClip = player.noClip;

        this.simulatedEntity.lastX = player.lastX;
        this.simulatedEntity.lastY = player.lastY;
        this.simulatedEntity.lastZ = player.lastZ;
        this.simulatedEntity.lastYaw = player.lastYaw;
        this.simulatedEntity.lastPitch = player.lastPitch;
        this.simulatedEntity.setPosition(player.getEntityPos());
        this.simulatedEntity.setBoundingBox(player.getBoundingBox());
        this.simulatedEntity.setVelocity(player.getVelocity());
        final float yaw = OraculusClient.getInstance().getModuleRepository().getModule(MovementFixModule.class).isFixMovement() || player != mc.player
                ? player.getYaw()
                : RotationHelper.getClientHandler().getYawOr(player.getYaw());
        this.simulatedEntity.setYaw(yaw);
        this.simulatedEntity.setPitch(RotationHelper.getClientHandler().getPitchOr(player.getPitch()));
        this.simulatedEntity.setSneaking(player.isSneaking());
        this.simulatedEntity.setOnGround(player.isOnGround());
        this.simulatedEntity.setSprinting(player.isSprinting());
        for (final StatusEffectInstance statusEffect : player.getStatusEffects()) {
            this.simulatedEntity.addStatusEffect(statusEffect);
        }
        this.simulatedEntity.setMovementSpeed(player.getMovementSpeed());

        this.simulatedEntity.fallDistance = player.fallDistance;
    }

    public void simulateTicks(final int tickCount) {
        this.simulateTicks(tickCount, player.forwardSpeed, player.sidewaysSpeed, player.upwardSpeed);
    }

    public void simulateTicks(final int tickCount, final float forward, final float sideways, final float upward) {
        final LivingEntityAccessor accessor = (LivingEntityAccessor) simulatedEntity;
        for (int i = 0; i < tickCount; i++) {
            accessor.callTravelMidAir(new Vec3d(sideways, upward, forward));
        }
    }

    public void simulateTick() {
        simulateTicks(1);
    }

    public void simulateTick(final float forward, final float sideways, final float upward) {
        this.simulateTicks(1, forward, sideways, upward);
    }

    public OtherClientPlayerEntity getSimulatedEntity() {
        return simulatedEntity;
    }
}
