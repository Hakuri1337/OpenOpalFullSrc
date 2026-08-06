package wtf.oraculus.client.feature.module.impl.combat.killaura;

import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.swing.CPSProperty;
import wtf.oraculus.client.feature.helper.impl.target.TargetProperty;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.utility.player.RotationInjector;

public final class KillAuraSettings {

    private final RotationProperty rotationProperty;
    private final ModeProperty<Mode> mode;
    private final TargetProperty targetProperty;
    private final CPSProperty cpsProperty, swingCpsProperty;

    private final NumberProperty range, rotationRange, swingRange;
    private final BooleanProperty hideFakeSwings;

    private final BooleanProperty requireAttackKey, requireWeapon;
    private final BooleanProperty overrideRaycast, tickLookahead, throughWalls;
    private final BooleanProperty smartWeapon;
    private final BooleanProperty attackCooldown19;
    private final BooleanProperty blockAnimationWhenTargeting;
    private final NumberProperty fov;
    private final ModeProperty<RotationInjector.RotationMode> rotationMode;

    private final MultipleBooleanProperty visuals;

    public KillAuraSettings(final KillAuraModule module) {
        this.rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE);
        this.targetProperty = new TargetProperty(true, false, false, false, false, true);
        this.cpsProperty = new CPSProperty(module, "Attack CPS", true);
        this.swingCpsProperty = new CPSProperty(module, "Swing CPS", false).hideIf(this.cpsProperty::isModernDelay);

        this.range = new NumberProperty("Range", 3.D, 3.D, 6.D, 0.1D);
        this.rotationRange = new NumberProperty("Rotation range", 5.D, 3.D, 8.D, 0.1D);
        this.swingRange = new NumberProperty("Swing range", 5.D, 3.D, 8.D, 0.1D).hideIf(this.cpsProperty::isModernDelay);
        this.hideFakeSwings = new BooleanProperty("Hide fake swings", true).hideIf(this.cpsProperty::isModernDelay);

        this.requireAttackKey = new BooleanProperty("Require attack key", false);
        this.requireWeapon = new BooleanProperty("Require weapon", false);
        this.overrideRaycast = new BooleanProperty("Override raycast", true);
        this.tickLookahead = new BooleanProperty("Tick lookahead", false).hideIf(() -> !this.isOverrideRaycast());
        this.throughWalls = new BooleanProperty("Through Walls", false);
        this.smartWeapon = new BooleanProperty("SmartWeapon", false);
        this.attackCooldown19 = new BooleanProperty("1.9+ Attack Cooldown", false);
        this.blockAnimationWhenTargeting = new BooleanProperty("FakeBlock", true);
        this.mode = new ModeProperty<>("Mode", Mode.SWITCH);
        this.fov = new NumberProperty("FOV", 180, 1, 180, 1);
        this.rotationMode = new ModeProperty<>("Rotation Mode", module, RotationInjector.RotationMode.NORMAL);

        this.visuals = new MultipleBooleanProperty("Visuals",
                new BooleanProperty("Box", false),
                new BooleanProperty("Halo", true)
        );

        module.addProperties(
                rotationProperty.get(), new GroupProperty("Requirements", requireWeapon, requireAttackKey),
                mode, range, rotationRange, swingRange, hideFakeSwings, targetProperty.get(),
                fov, overrideRaycast, tickLookahead, visuals, blockAnimationWhenTargeting,
                throughWalls, smartWeapon, attackCooldown19, rotationMode
        );
    }

    public double getRange() {
        return this.range.getValue();
    }

    public double getSwingRange() {
        return this.swingRange.getValue();
    }


    public boolean isThroughWalls() {
        return this.throughWalls.getValue();
    }

    public boolean isHideFakeSwings() {
        return this.hideFakeSwings.getValue();
    }

    public boolean isOverrideRaycast() {
        return this.overrideRaycast.getValue();
    }

    public boolean isTickLookahead() {
        return this.tickLookahead.getValue();
    }

    public double getRotationRange() {
        return this.rotationRange.getValue();
    }

    public MultipleBooleanProperty getVisuals() {
        return visuals;
    }

    public TargetProperty getTargetProperty() {
        return targetProperty;
    }

    public CPSProperty getCpsProperty() {
        return cpsProperty;
    }

    public CPSProperty getSwingCpsProperty() {
        return swingCpsProperty;
    }

    public boolean isRequireAttackKey() {
        return requireAttackKey.getValue();
    }

    public boolean isRequireWeapon() {
        return requireWeapon.getValue();
    }

    public IRotationModel createRotationModel() {
        return rotationProperty.createModel();
    }

    public RotationInjector.RotationMode getRotationMode() {
        return this.rotationMode.getValue();
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public float getFov() {
        return this.fov.getValue().floatValue();
    }

    public boolean isAttackCooldown19() {
        return attackCooldown19.getValue();
    }

    public boolean isSmartWeapon() {
        return smartWeapon.getValue();
    }

    public boolean isBlockAnimationWhenTargeting() {
        return blockAnimationWhenTargeting.getValue();
    }

    public enum Mode {
        SINGLE("Single"),
        SWITCH("Switch");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
