package wtf.oraculus.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.player.movement.KeepSprintEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static wtf.oraculus.client.Constants.mc;

/**
 * Reproduces the legacy Intave hurt-time reduction stages without duplicate
 * attack packets or repeated multiplication inside the same hurt-time stage.
 */
public final class IntaveVelocity extends VelocityMode {

    private final BooleanProperty jumpReset = new BooleanProperty("Jump Reset", true)
            .id("intaveJumpReset")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .id("intaveDebug")
            .hideIf(() -> this.module.getActiveMode() != this);

    private int previousHurtTime;
    private int appliedStages;
    private boolean jumpQueued;
    private boolean suppressVanillaSlowdown;

    public IntaveVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.jumpReset, this.debug);
    }

    @Subscribe(priority = 2)
    public void onAttack(final AttackEvent event) {
        if (!this.canOperate() || event.getTarget() == null || !event.getTarget().isAlive()) {
            return;
        }

        final int hurtTime = mc.player.hurtTime;
        final int stageBit;
        final double multiplier;
        switch (hurtTime) {
            case 9 -> {
                stageBit = 1;
                multiplier = 0.60D;
            }
            case 8 -> {
                stageBit = 2;
                multiplier = 0.36D;
            }
            case 7 -> {
                stageBit = 4;
                multiplier = 0.60D;
            }
            default -> {
                return;
            }
        }

        if ((this.appliedStages & stageBit) != 0) {
            return;
        }

        final Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
        this.appliedStages |= stageBit;
        this.suppressVanillaSlowdown = true;
        this.debugLog("hurtTime " + hurtTime + " -> " + Math.round(multiplier * 100.0D) + "%");
    }

    @Subscribe(priority = 100)
    public void onKeepSprint(final KeepSprintEvent event) {
        if (this.suppressVanillaSlowdown) {
            event.setCancelled();
            this.suppressVanillaSlowdown = false;
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!this.canOperate()) {
            this.reset();
            return;
        }

        final int hurtTime = mc.player.hurtTime;
        if (hurtTime == 0 || hurtTime > this.previousHurtTime) {
            this.appliedStages = 0;
        }
        if (this.jumpReset.getValue() && hurtTime == 9 && this.previousHurtTime != 9
                && mc.player.isOnGround() && mc.player.isSprinting()) {
            this.jumpQueued = true;
        }

        this.previousHurtTime = hurtTime;
        this.suppressVanillaSlowdown = false;
    }

    @Subscribe(priority = 2)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player != null && this.jumpQueued) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.jumpQueued = false;
            this.debugLog("jump reset");
        }
    }

    private boolean canOperate() {
        return mc.player != null && mc.world != null && !this.module.isInvalid()
                && !mc.player.isSpectator() && !mc.player.isInFluid()
                && !mc.player.isClimbing() && !mc.player.hasVehicle();
    }

    private void reset() {
        this.previousHurtTime = 0;
        this.appliedStages = 0;
        this.jumpQueued = false;
        this.suppressVanillaSlowdown = false;
    }

    private void debugLog(final String message) {
        if (this.debug.getValue()) {
            ChatUtility.print("AntiKB Intave | " + message);
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.INTAVE;
    }
}
