package wtf.oraculus.client.feature.module.impl.combat.antikb.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBMode;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class JumpResetVelocity extends AntiKBMode {

    private final NumberProperty chance = new NumberProperty("Chance", "%", 100, 0, 100, 1).hideIf(() -> this.module.getActiveMode() != this);

    private final BooleanProperty jumpByReceivedHits = new BooleanProperty("Jump by received hits", false).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty hitsUntilJump = new NumberProperty("Hits until jump", 2, 0, 10, 1).hideIf(() -> !this.jumpByReceivedHits.getValue() || this.module.getActiveMode() != this);

    private final BooleanProperty jumpByDelay = new BooleanProperty("Jump by delay", true).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty ticksUntilJump = new NumberProperty("Ticks until jump", 2, 0, 20, 1).hideIf(() -> !this.jumpByDelay.getValue() || this.module.getActiveMode() != this);

    public JumpResetVelocity(AntiKBModule module) {
        super(module);
        module.addProperties(this.chance, this.jumpByReceivedHits, this.hitsUntilJump, this.jumpByDelay, this.ticksUntilJump);
    }

    private int limitUntilJump = 0;
    private boolean isFallDamage = false;
    private final Stopwatch hitStopwatch = new Stopwatch();

    @Override
    public String getSuffix() {
        return "JumpReset";
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (mc.player != null && packet.getEntityId() == mc.player.getId()) {
                double velocityX = packet.getVelocity().x;
                double velocityY = packet.getVelocity().y;
                double velocityZ = packet.getVelocity().z;

                if (velocityX == 0 && velocityZ == 0 && velocityY < 0) {
                    this.isFallDamage = true;
                } else {
                    this.isFallDamage = false;
                    if (mc.player.hurtTime == 9) {
                        this.limitUntilJump++;
                        this.hitStopwatch.reset();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || this.module.isInvalid()) {
            return;
        }

        if (mc.player.hurtTime != 9 || !mc.player.isOnGround() || !mc.player.isSprinting() || this.isFallDamage) {
            this.updateLimit();
            return;
        }

        if (this.chance.getValue().intValue() < 100 && Math.random() * 100 > this.chance.getValue().intValue()) {
            this.updateLimit();
            return;
        }

        if (!this.iscooldo()) {
            this.updateLimit();
            return;
        }

        ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
        event.setJump(true);
        this.limitUntilJump = 0;
    }

    private boolean iscooldo() {
        if (this.jumpByReceivedHits.getValue() && this.limitUntilJump >= this.hitsUntilJump.getValue().intValue()) {
            return true;
        }

        if (this.jumpByDelay.getValue() && this.hitStopwatch.hasTimeElapsed(this.ticksUntilJump.getValue().intValue() * 50L)) {
            return true;
        }

        return !this.jumpByReceivedHits.getValue() && !this.jumpByDelay.getValue();
    }

    private void updateLimit() {
        if (this.jumpByReceivedHits.getValue()) {
            return;
        }
        this.limitUntilJump++;
    }

    @Override
    public void onDisable() {
        this.limitUntilJump = 0;
        this.isFallDamage = false;
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return AntiKBModule.Mode.JUMPRESET;
    }
}