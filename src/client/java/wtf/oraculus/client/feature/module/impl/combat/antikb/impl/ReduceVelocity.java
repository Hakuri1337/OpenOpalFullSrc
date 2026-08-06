package wtf.oraculus.client.feature.module.impl.combat.antikb.impl;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.holder.BlockHolder;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.impl.utility.AntiBotsModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBMode;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class ReduceVelocity extends AntiKBMode {

    private final BooleanProperty delayUntilGround = new BooleanProperty("Delay until ground", true).hideIf(() -> this.module.getActiveMode() != this);

    private final NumberProperty attackCountProp = new NumberProperty("AttackCount", 3, 1, 8, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty risingAttacks = new NumberProperty("Rising Attacks", 3, 1, 8, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty fallingAttacks = new NumberProperty("Falling Attacks", 4, 1, 8, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty attackCooldown = new NumberProperty("AttackCooldown", 2, 0, 20, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty alinkTime = new NumberProperty("Alink", 5000, 50, 10000, 50).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty sprintReset = new BooleanProperty("SprintReset", true).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty enableTimerBoost = new BooleanProperty("TimerBoost", false);
    private final NumberProperty timerSpeed = new NumberProperty("Timer speed", "x", 0.5F, 0.1F, 1.0F, 0.05F).hideIf(() -> !enableTimerBoost.getValue());

    private static final double MIN_HORIZONTAL_SPEED = 0.1;

    private int limitUntilJump = 0;
    private boolean isFallDamage = false;
    private final Stopwatch hitStopwatch = new Stopwatch();

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Stopwatch blockStopwatch = new Stopwatch();
    private final Stopwatch alinkStopwatch = new Stopwatch();
    private boolean lag = false;
    private boolean canAttack = false;
    private boolean hasPendingVelocity = false;
    private int attackCount = 0;
    private boolean cooldownSet = false;
    private boolean wasSprinting = false;
    private boolean waitingForSprint = false;

    private int releaseDelay = 0;
    private boolean pendingRelease = false;

    private int tempTimerTicks = 0;
    private float originalTimerValue = 1.0F;

    private double lastVelocityY = 0;
    private double lastHorizontalSpeed = 0;
    private boolean velocityTooWeak = false;

    public ReduceVelocity(AntiKBModule module) {
        super(module);

        module.addProperties(
                this.delayUntilGround,
                this.attackCountProp, this.risingAttacks, this.fallingAttacks,
                this.attackCooldown, this.alinkTime, this.sprintReset,
                this.enableTimerBoost, this.timerSpeed, this.debug
        );
    }

    @Override
    public String getSuffix() {
        return "Reduce";
    }

    private boolean hasEnemyInRange(double range) {
        if (mc.player == null || mc.world == null) return false;
        Box box = mc.player.getBoundingBox().expand(range);
        for (Entity entity : mc.world.getEntitiesByClass(PlayerEntity.class, box, e -> e != mc.player && !AntiBotsModule.isBot(e))) {
            return true;
        }
        return false;
    }

    private boolean hasFireResistance() {
        if (mc.player == null) return false;
        return mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE);
    }

    private boolean isOnFire() {
        if (mc.player == null) return false;
        return mc.player.isOnFire();
    }

    private boolean shouldDisableAlink() {
        if (isOnFire() && !hasFireResistance()) {
            return true;
        }
        if (this.velocityTooWeak) {
            return true;
        }
        return false;
    }

    private int getDynamicAttackCount() {
        int baseAttacks = this.attackCountProp.getValue().intValue();

        if (mc.player != null && mc.player.isOnGround()) {
            return 2;
        }

        if (this.lastVelocityY == 0) {
            return baseAttacks;
        }

        return this.lastVelocityY > 0
                ? this.risingAttacks.getValue().intValue()
                : this.fallingAttacks.getValue().intValue();
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
        if (mc.player == null || this.module.isInvalid()) return;

        if (mc.player.hurtTime == 9 && mc.player.isOnGround() && mc.player.isSprinting() && !this.isFallDamage) {
            if (isCooldownReady()) {
                ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
                event.setJump(true);
                this.limitUntilJump = 0;
                if (debug.getValue()) ChatUtility.debug("Jump Reset");
            }
        }
    }

    private boolean isCooldownReady() {
        return true;
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (velocity.getEntityId() != mc.player.getId()) return;

            if (this.lag) {
                this.lag = false;
                return;
            }

            double velX = velocity.getVelocity().x;
            double velZ = velocity.getVelocity().z;
            this.lastHorizontalSpeed = Math.sqrt(velX * velX + velZ * velZ);
            this.lastVelocityY = velocity.getVelocity().y;
            this.velocityTooWeak = this.lastHorizontalSpeed < MIN_HORIZONTAL_SPEED;

            this.hasPendingVelocity = true;
            this.attackCount = 0;
            this.cooldownSet = false;
            this.waitingForSprint = false;
            this.canAttack = false;

            if (this.delayUntilGround.getValue() && !shouldDisableAlink()) {
                if (mc.player.isOnGround()) {
                    if (hasEnemyInRange(3.0)) {
                        this.canAttack = true;
                    } else {
                        this.blockHolder.block();
                        this.blockStopwatch.reset();
                        this.alinkStopwatch.reset();
                    }
                } else {
                    this.blockHolder.block();
                    this.blockStopwatch.reset();
                    this.alinkStopwatch.reset();
                }
            } else {
                if (hasEnemyInRange(3.0)) {
                    this.canAttack = true;
                    if (debug.getValue()) {
                        if (isOnFire() && !hasFireResistance()) {
                            ChatUtility.debug("Alink disabled - fire");
                        } else if (this.velocityTooWeak) {
                            ChatUtility.debug("Alink disabled - weak KB");
                        } else {
                            ChatUtility.debug("Instant attack");
                        }
                    }
                } else {
                    this.blockHolder.block();
                    this.blockStopwatch.reset();
                    this.alinkStopwatch.reset();
                }
            }
        }
    }

    @Subscribe
    public void onReceivePacketLag(final ReceivePacketEvent event) {
        if (mc.player == null) return;

        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) {
            if (this.blockHolder.isBlocking()) this.blockHolder.release();
            this.canAttack = false;
            this.hasPendingVelocity = false;
            return;
        }

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.lag = true;
            if (this.blockHolder.isBlocking()) {
                this.blockHolder.release();
                this.canAttack = false;
            }
        }
    }

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (this.module.isInvalid()) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) return;

        final double horizontalValue = 1.0;
        final double verticalValue = 1.0;

        event.setCancelled();

        if (!event.isExplosion() && (horizontalValue == 0 && verticalValue == 0)) return;

        final double velocityX = event.getVelocityX() * horizontalValue;
        final double velocityY = event.getVelocityY() * verticalValue;
        final double velocityZ = event.getVelocityZ() * horizontalValue;

        if (this.blockHolder.isBlocking()) return;

        if (horizontalValue != 0) {
            mc.player.setVelocity(velocityX, mc.player.getVelocity().getY(), velocityZ);
        }
        if (verticalValue != 0) {
            mc.player.setVelocity(mc.player.getVelocity().getX(), velocityY, mc.player.getVelocity().getZ());
        }
    }

    @Subscribe
    public void onScheduledExecutables(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (tempTimerTicks > 0) {
            tempTimerTicks--;
            if (tempTimerTicks == 0) {
                TimerHelper.getInstance().timer = originalTimerValue;
                if (debug.getValue()) ChatUtility.debug("Timer reset");
            }
        }

        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) {
            if (this.blockHolder.isBlocking()) this.blockHolder.release();
            this.canAttack = false;
            this.hasPendingVelocity = false;
            return;
        }

        final KillAuraModule aura = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        boolean hasKillAuraTarget = aura != null && aura.isEnabled() &&
                aura.getTargeting().getTarget() != null &&
                aura.getTargeting().getTarget().getEntity() != null;

        if (this.delayUntilGround.getValue() && this.blockHolder.isBlocking() && !shouldDisableAlink()) {
            boolean shouldRelease = false;
            boolean hasTarget = hasEnemyInRange(3.0);

            if (mc.player.isOnGround() && hasTarget) {
                shouldRelease = true;
            } else if (this.alinkStopwatch.hasTimeElapsed(this.alinkTime.getValue().longValue())) {
                shouldRelease = true;
            } else if (this.blockStopwatch.hasTimeElapsed(1000L)) {
                shouldRelease = true;
            }

            if (shouldRelease && !pendingRelease) {
                if (!hasKillAuraTarget) {
                    if (enableTimerBoost.getValue() && tempTimerTicks > 0) {
                        TimerHelper.getInstance().timer = originalTimerValue;
                        tempTimerTicks = 0;
                        if (debug.getValue()) ChatUtility.debug("TimerBoost cancelled");
                    }
                    executeRelease();
                    return;
                }

                pendingRelease = true;

                if (enableTimerBoost.getValue()) {
                    originalTimerValue = TimerHelper.getInstance().timer;
                    TimerHelper.getInstance().timer = timerSpeed.getValue().floatValue();
                    tempTimerTicks = 6;
                    if (debug.getValue()) ChatUtility.debug("TimerBoost: " + timerSpeed.getValue().floatValue());
                }

                releaseDelay = 2;
                return;
            }
        }

        if (this.blockHolder.isBlocking() && shouldDisableAlink()) {
            executeRelease();
        }

        if (releaseDelay > 0) {
            if (!hasKillAuraTarget) {
                releaseDelay = 0;
                pendingRelease = false;
                if (enableTimerBoost.getValue() && tempTimerTicks > 0) {
                    TimerHelper.getInstance().timer = originalTimerValue;
                    tempTimerTicks = 0;
                    if (debug.getValue()) ChatUtility.debug("TimerBoost cancelled");
                }
                this.canAttack = false;
                return;
            }

            releaseDelay--;
            if (releaseDelay == 0) {
                executeRelease();
            }
            onAttackPacket();
            return;
        }

        onAttackPacket();

        int neededAttacks = getDynamicAttackCount();
        if (this.hasPendingVelocity && this.attackCount >= neededAttacks) {
            this.hasPendingVelocity = false;
            this.canAttack = false;
            if (debug.getValue()) ChatUtility.debug("Sync " + neededAttacks + " attacks");
        }
    }

    private void executeRelease() {
        this.blockHolder.release();
        this.canAttack = true;
        pendingRelease = false;
    }

    private void onAttackPacket() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) return;
        if (!this.canAttack) return;

        final KillAuraModule aura = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);

        if (aura != null && aura.isEnabled() && aura.getTargeting().getTarget() != null &&
                aura.getTargeting().getTarget().getEntity() != null &&
                mc.player.hurtTime > 0) {

            if (mc.player.hurtTime == mc.player.maxHurtTime) {
                this.attackCount = 0;
                this.cooldownSet = false;
                this.waitingForSprint = false;
            }

            if (!mc.player.isSprinting()) {
                this.waitingForSprint = true;
                return;
            }

            if (this.waitingForSprint) {
                this.waitingForSprint = false;
            }

            int neededAttacks = getDynamicAttackCount();

            if (this.attackCount < neededAttacks) {
                if (this.attackCount == 0 && this.sprintReset.getValue()) {
                    this.wasSprinting = mc.player.isSprinting();
                }

                mc.player.setSprinting(false);

                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(
                        aura.getTargeting().getTarget().getEntity(), mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                double reducePercent = 0.6;
                mc.player.setVelocity(mc.player.getVelocity().multiply(reducePercent, 1.0, reducePercent));

                this.attackCount++;

                if (this.attackCount >= neededAttacks && this.sprintReset.getValue() && this.wasSprinting) {
                    mc.player.setSprinting(true);
                }

                if (this.attackCount >= neededAttacks &&
                        !this.cooldownSet && this.attackCooldown.getValue().intValue() > 0) {
                    aura.setAttackCooldown(this.attackCooldown.getValue().intValue());
                    this.cooldownSet = true;
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.limitUntilJump = 0;
        this.isFallDamage = false;

        this.blockHolder.release();
        this.lag = false;
        this.canAttack = false;
        this.hasPendingVelocity = false;
        this.attackCount = 0;
        this.cooldownSet = false;
        this.waitingForSprint = false;
        this.releaseDelay = 0;
        this.pendingRelease = false;

        this.lastVelocityY = 0;
        this.lastHorizontalSpeed = 0;
        this.velocityTooWeak = false;

        if (tempTimerTicks > 0) {
            TimerHelper.getInstance().timer = originalTimerValue;
            tempTimerTicks = 0;
        }

        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return AntiKBModule.Mode.REDUCE;
    }
}
