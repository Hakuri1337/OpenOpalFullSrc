package wtf.opal.client.feature.module.impl.visual;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.utility.player.BlockUtility;

import static wtf.opal.client.Constants.mc;

public final class SilenceItemRotationModule extends Module {

    private final ModeProperty<Axis> axis = new ModeProperty<>("Axis", Axis.Y);
    private final NumberProperty speed = new NumberProperty("Speed", "degrees/tick", 15.0D, 0.0D, 60.0D, 1.0D);
    private final BooleanProperty allowRotationWhileMoving = new BooleanProperty("Allow rotation while moving", false);
    private final BooleanProperty mainHand = new BooleanProperty("Main hand", true);
    private final BooleanProperty offHand = new BooleanProperty("Off hand", true);
    private final BooleanProperty onlySword = new BooleanProperty("Only sword", false);
    private final BooleanProperty stopNearPlayers = new BooleanProperty("Stop near players", false);
    private final BooleanProperty stopNearMobs = new BooleanProperty("Stop near mobs", false);
    private final NumberProperty stopRange = new NumberProperty("Stop range", 3.0D, 1.0D, 8.0D, 0.5D)
            .hideIf(() -> !this.stopNearPlayers.getValue() && !this.stopNearMobs.getValue());

    public SilenceItemRotationModule() {
        super("SilenceItemRotation", "Rotates idle first-person held items.", ModuleCategory.VISUAL);
        this.addProperties(
                this.axis,
                this.speed,
                this.allowRotationWhileMoving,
                this.mainHand,
                this.offHand,
                this.onlySword,
                this.stopNearPlayers,
                this.stopNearMobs,
                this.stopRange
        );
    }

    public boolean shouldRotate(final ClientPlayerEntity player, final ItemStack stack, final Hand hand) {
        if (!this.isEnabled() || stack.isEmpty()) {
            return false;
        }

        if ((hand == Hand.MAIN_HAND && !this.mainHand.getValue())
                || (hand == Hand.OFF_HAND && !this.offHand.getValue())) {
            return false;
        }

        final boolean sword = stack.isIn(ItemTags.SWORDS);
        if (this.onlySword.getValue() && !sword) {
            return false;
        }

        if (this.hasNearbyStopTarget(player)) {
            return false;
        }

        if (player.isUsingItem() || player.hurtTime > 0) {
            return false;
        }

        if (!this.allowRotationWhileMoving.getValue()
                && (Math.abs(player.forwardSpeed) > 0.001F || Math.abs(player.sidewaysSpeed) > 0.001F)) {
            return false;
        }

        if (sword && hand == Hand.MAIN_HAND && (player.handSwinging || mc.options.jumpKey.isPressed())) {
            return false;
        }

        return !BlockUtility.isForceBlockUseState(player)
                && !BlockUtility.isBlockUseState(player)
                && !BlockUtility.isNoSlowBlockingState()
                && !BlockUtility.isFakeABBlockingState(player);
    }

    public void applyRotation(final MatrixStack matrices, final float animationTicks) {
        final float angle = (animationTicks * this.speed.getValue().floatValue()) % 360.0F;
        matrices.multiply(switch (this.axis.getValue()) {
            case X -> RotationAxis.POSITIVE_X.rotationDegrees(angle);
            case Y -> RotationAxis.POSITIVE_Y.rotationDegrees(angle);
            case Z -> RotationAxis.POSITIVE_Z.rotationDegrees(angle);
        });
    }

    private boolean hasNearbyStopTarget(final ClientPlayerEntity player) {
        if ((!this.stopNearPlayers.getValue() && !this.stopNearMobs.getValue()) || mc.world == null) {
            return false;
        }

        final double range = this.stopRange.getValue().doubleValue();
        for (LivingEntity entity : mc.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(range),
                entity -> entity != player && entity.isAlive()
        )) {
            if (entity instanceof PlayerEntity) {
                if (this.stopNearPlayers.getValue()) {
                    return true;
                }
            } else if (this.stopNearMobs.getValue()) {
                return true;
            }
        }

        return false;
    }

    public enum Axis {
        X,
        Y,
        Z
    }
}
