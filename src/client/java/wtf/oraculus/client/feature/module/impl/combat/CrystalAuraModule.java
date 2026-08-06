package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.RaycastUtility;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class CrystalAuraModule extends Module {
    private final CrystalAuraSettings settings = new CrystalAuraSettings(this);
    private EndCrystalEntity target;
    private int lastScanAge = -1;

    public CrystalAuraModule() {
        super("CrystalAura", "Automatically attacks nearby end crystals.", ModuleCategory.COMBAT);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) return;

        final double range = settings.getRange().getValue().doubleValue();
        final double rangeSq = range * range;

        if (this.target != null) {
            final double distanceSq = mc.player.squaredDistanceTo(this.target);
            if (!this.target.isAlive() || distanceSq > rangeSq) {
                this.target = null;
            }
        }

        if (this.target == null || mc.player.age - this.lastScanAge >= 2) {
            this.target = findTarget(range, rangeSq);
            this.lastScanAge = mc.player.age;
        }

        if (this.target != null) {
            Vec2f rotations = RotationUtility.getRotationFromPosition(this.target.getEyePos());
            RotationHelper.getHandler().rotate(rotations, InstantRotationModel.INSTANCE);
            
            // Auto attack logic
            HitResult hitResult = RaycastUtility.raycastEntity(range, 1.0F, rotations.x, rotations.y, e -> e == this.target);
            if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() == this.target) {
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(this.target, mc.player.isSneaking()));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private EndCrystalEntity findTarget(final double range, final double rangeSq) {
        final Box box = mc.player.getBoundingBox().expand(range);
        EndCrystalEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        final List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, Entity::isAlive);
        for (final EndCrystalEntity crystal : crystals) {
            final double distanceSq = mc.player.squaredDistanceTo(crystal);
            if (distanceSq > rangeSq || distanceSq >= closestDistanceSq) {
                continue;
            }
            closest = crystal;
            closestDistanceSq = distanceSq;
        }

        return closest;
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (target != null) {
            event.setYaw(mc.player.getYaw());
            event.setPitch(mc.player.getPitch());
        }
    }

    @Override
    protected void onDisable() {
        target = null;
        lastScanAge = -1;
        super.onDisable();
    }
}
