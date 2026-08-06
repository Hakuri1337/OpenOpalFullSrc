package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.utility.player.BlockUtility;

public final class AnimationsModule extends Module {

    // Sword blocking
    private final BooleanProperty swordBlocking = new BooleanProperty("Enabled", true);
    private final BooleanProperty fakeAB = new BooleanProperty("FakeAB", false).hideIf(() -> !swordBlocking.getValue());
    private final ModeProperty<BlockMode> blockAnimationMode = new ModeProperty<>("Block animation", BlockMode.V1_7).hideIf(() -> !swordBlocking.getValue());

    // Shields
    private final BooleanProperty alwaysHideShield = new BooleanProperty("Always hidden", true);
    private final BooleanProperty hideShieldSlotInHotbar = new BooleanProperty("Hide offhand slot", true);

    // Player
    private final BooleanProperty oldBackwardsWalking = new BooleanProperty("Old backwards walking", true);
    private final BooleanProperty oldArmorDamageTint = new BooleanProperty("Old armor damage tint", true);
    private final BooleanProperty oldSneaking = new BooleanProperty("Old sneaking", false);
    private final BooleanProperty fixPoseRepeat = new BooleanProperty("Fix pose repeat", true);

    // Item
    private final NumberProperty mainHandScale = new NumberProperty("Scale", 0f, -2f, 2f, 0.1f);
    private final NumberProperty mainHandX = new NumberProperty("Offset X", 0.f, -2.f, 2.f, 0.1f);
    private final NumberProperty mainHandY = new NumberProperty("Offset Y", 0.f, -2.f, 2.f, 0.1f);
    private final NumberProperty swingSlowdown = new NumberProperty("Swing slowdown", 0.F, 0.F, 5.F, 0.25F);
    private final BooleanProperty oldCooldownAnimation = new BooleanProperty("Old cooldown animation", true);
    private final BooleanProperty swingWhileUsing = new BooleanProperty("Visual swing on use", true);
    private final BooleanProperty hideDropSwing = new BooleanProperty("Hide drop swing", false);
    private final BooleanProperty equipOffset = new BooleanProperty("Equip offset", false);

    public AnimationsModule() {
        super("Animations", "Modifies animations within the game.", ModuleCategory.VISUAL);
        setEnabled(true);
        addProperties(
                new GroupProperty("Sword blocking", swordBlocking, fakeAB, blockAnimationMode),
                new GroupProperty("Shields", alwaysHideShield, hideShieldSlotInHotbar),
                new GroupProperty("Player", oldBackwardsWalking, oldArmorDamageTint, oldSneaking, fixPoseRepeat),
                new GroupProperty(
                        "Item",
                        mainHandScale, mainHandX, mainHandY, swingSlowdown,
                        oldCooldownAnimation, swingWhileUsing, hideDropSwing, equipOffset
                )
        );
    }

    public boolean isHideDropSwing() {
        return this.hideDropSwing.getValue();
    }

    public boolean isOldSneaking() {
        return this.oldSneaking.getValue();
    }

    public boolean isFixPoseRepeat() {
        return this.fixPoseRepeat.getValue();
    }

    public float getSwingSlowdown() {
        return swingSlowdown.getValue().floatValue() + 1.F;
    }

    public boolean isSwordBlocking() {
        return swordBlocking.getValue();
    }

    public boolean isFakeAB() {
        return fakeAB.getValue();
    }

    public boolean isEquipOffset() {
        return equipOffset.getValue();
    }

    public boolean isOldCooldownAnimation() {
        return oldCooldownAnimation.getValue();
    }

    public boolean isOldBackwardsWalking() {
        return oldBackwardsWalking.getValue();
    }

    public boolean isOldArmorDamageTint() {
        return oldArmorDamageTint.getValue();
    }

    public boolean isHideShield() {
        return alwaysHideShield.getValue();
    }

    public boolean isHideShieldSlotInHotbar() {
        return hideShieldSlotInHotbar.getValue();
    }

    public float getMainHandScale() {
        return mainHandScale.getValue().floatValue();
    }

    public float getMainHandX() {
        return mainHandX.getValue().floatValue();
    }

    public float getMainHandY() {
        return mainHandY.getValue().floatValue();
    }

    public boolean isSwingWhileUsing() {
        return this.swingWhileUsing.getValue();
    }

    public void applyTransformations(final MatrixStack matrices, final float swingProgress) {
        final float convertedProgress = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        final float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

        switch (blockAnimationMode.getValue()) {
            case V1_7 -> {
                BlockUtility.applySwingTransformation(matrices, swingProgress, convertedProgress);
                BlockUtility.applyBlockTransformation(matrices);
            }
            case V1_8 -> {
                BlockUtility.applyBlockTransformation(matrices);
            }
            case RUB -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -30.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(convertedProgress * -30.0F));
            }
            case STELLA -> {
                BlockUtility.applySwingTransformation(matrices, swingProgress, convertedProgress);
                matrices.translate(-0.15F, 0.16F, 0.15F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-24.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(75.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            }
            case BOUNCE -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(0.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(convertedProgress * 42.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-convertedProgress * 22.0F));
            }
            case DIAGONAL -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(5.0F - (convertedProgress * 32.0F)));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(0.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(0.0F));
            }
            case SWANK -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F + f * -5.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(convertedProgress * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(convertedProgress * -40.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F));
                BlockUtility.applyBlockTransformation(matrices);
            }
        }
    }

    public enum BlockMode {
        V1_7("1.7"),
        V1_8("1.8"),
        RUB("Rub"),
        STELLA("Stella"),
        BOUNCE("Bounce"),
        DIAGONAL("Diagonal"),
        SWANK("Swank");

        private final String name;

        BlockMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
