package wtf.oraculus.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.impl.utility.AntiBotsModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

/**
 * Amadeus Velocity Reduce port, exposed as Oraculus AntiKB AttackReduce.
 *
 * <p>The original implementation deliberately holds inbound packets while
 * waiting for a grounded combat window, releases the held timeline, then
 * performs one sprint-reset attack per game tick. Keep these phases separate:
 * collapsing them into an immediate velocity cancellation changes both the
 * local movement result and the packet ordering this mode relies on.</p>
 */
public final class AttackReduceVelocity extends VelocityMode {

    private static final double MIN_HORIZONTAL_SPEED = 0.1D;

    private final BooleanProperty delayUntilGround =
            new BooleanProperty("Delay until ground", true)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty attackCountProperty =
            new NumberProperty("AttackCount", 3.0D, 1.0D, 8.0D, 1.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty risingAttacks =
            new NumberProperty("Rising Attacks", 3.0D, 1.0D, 8.0D, 1.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty fallingAttacks =
            new NumberProperty("Falling Attacks", 4.0D, 1.0D, 8.0D, 1.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty attackCooldown =
            new NumberProperty("AttackCooldown", 2.0D, 0.0D, 20.0D, 1.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty alinkTime =
            new NumberProperty("Alink", 5000.0D, 50.0D, 10000.0D, 50.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty sprintReset =
            new BooleanProperty("SprintReset", true)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty timerBoost =
            new BooleanProperty("TimerBoost", false)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty timerSpeed =
            new NumberProperty("Timer speed", "x", 0.5D, 0.1D, 1.0D, 0.05D)
                    .hideIf(() -> this.module.getActiveMode() != this || !this.timerBoost.getValue());
    private final BooleanProperty debug =
            new BooleanProperty("Debug", false)
                    .hideIf(() -> this.module.getActiveMode() != this);

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Stopwatch hitStopwatch = new Stopwatch();
    private final Stopwatch blockStopwatch = new Stopwatch();
    private final Stopwatch alinkStopwatch = new Stopwatch();

    private int limitUntilJump;
    private boolean fallDamage;
    private boolean lag;
    private boolean canAttack;
    private boolean hasPendingVelocity;
    private int attackCount;
    private boolean cooldownSet;
    private boolean wasSprinting;
    private boolean waitingForSprint;
    private int releaseDelay;
    private boolean pendingRelease;
    private int temporaryTimerTicks;
    private float originalTimerValue = 1.0F;
    private double lastVelocityY;
    private double lastHorizontalSpeed;
    private boolean velocityTooWeak;

    public AttackReduceVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(
                this.delayUntilGround,
                this.attackCountProperty,
                this.risingAttacks,
                this.fallingAttacks,
                this.attackCooldown,
                this.alinkTime,
                this.sprintReset,
                this.timerBoost,
                this.timerSpeed,
                this.debug
        );
    }

    @Override
    public String getSuffix() {
        return "AttackReduce";
    }

    private boolean hasEnemyInRange(final double range) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        final Box box = mc.player.getBoundingBox().expand(range);
        return !mc.world.getEntitiesByClass(
                PlayerEntity.class,
                box,
                entity -> entity != mc.player && !AntiBotsModule.isBot(entity)
        ).isEmpty();
    }

    private boolean hasFireResistance() {
        return mc.player != null && mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    private boolean isOnFire() {
        return mc.player != null && mc.player.isOnFire();
    }

    private boolean shouldDisableAlink() {
        return this.isOnFire() && !this.hasFireResistance() || this.velocityTooWeak;
    }

    private int getDynamicAttackCount() {
        final int baseAttacks = this.attackCountProperty.getValue().intValue();
        if (mc.player != null && mc.player.isOnGround()) {
            return 2;
        }
        if (this.lastVelocityY == 0.0D) {
            return baseAttacks;
        }
        return this.lastVelocityY > 0.0D
                ? this.risingAttacks.getValue().intValue()
                : this.fallingAttacks.getValue().intValue();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (!(event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity)
                || mc.player == null
                || velocity.getEntityId() != mc.player.getId()) {
            return;
        }

        final Vec3d motion = velocity.getVelocity();
        if (motion.x == 0.0D && motion.z == 0.0D && motion.y < 0.0D) {
            this.fallDamage = true;
            return;
        }

        this.fallDamage = false;
        if (mc.player.hurtTime == 9) {
            this.limitUntilJump++;
            this.hitStopwatch.reset();
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || this.module.isInvalid()) {
            return;
        }

        if (mc.player.hurtTime == 9
                && mc.player.isOnGround()
                && mc.player.isSprinting()
                && !this.fallDamage
                && this.isCooldownReady()) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.limitUntilJump = 0;
            this.debugLog("Jump Reset");
        }
    }

    private boolean isCooldownReady() {
        return true;
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (mc.player == null
                || mc.world == null
                || mc.player.isDead()
                || mc.player.getHealth() <= 0.0F
                || !(event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity)
                || velocity.getEntityId() != mc.player.getId()) {
            return;
        }

        if (this.lag) {
            this.lag = false;
            return;
        }

        final Vec3d motion = velocity.getVelocity();
        this.lastHorizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        this.lastVelocityY = motion.y;
        this.velocityTooWeak = this.lastHorizontalSpeed < MIN_HORIZONTAL_SPEED;
        this.hasPendingVelocity = true;
        this.attackCount = 0;
        this.cooldownSet = false;
        this.waitingForSprint = false;
        this.canAttack = false;

        if (this.delayUntilGround.getValue() && !this.shouldDisableAlink()) {
            if (mc.player.isOnGround()) {
                if (this.hasEnemyInRange(3.0D)) {
                    this.canAttack = true;
                } else {
                    this.startBlocking();
                }
            } else {
                this.startBlocking();
            }
            return;
        }

        if (this.hasEnemyInRange(3.0D)) {
            this.canAttack = true;
            if (this.isOnFire() && !this.hasFireResistance()) {
                this.debugLog("Alink disabled - fire");
            } else if (this.velocityTooWeak) {
                this.debugLog("Alink disabled - weak KB");
            } else {
                this.debugLog("Instant attack");
            }
        } else {
            this.startBlocking();
        }
    }

    private void startBlocking() {
        this.blockHolder.block();
        this.blockStopwatch.reset();
        this.alinkStopwatch.reset();
    }

    @Subscribe
    public void onReceivePacketLag(final ReceivePacketEvent event) {
        if (mc.player == null) {
            return;
        }

        if (mc.player.isDead() || mc.player.getHealth() <= 0.0F) {
            this.blockHolder.release();
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
        if (this.module.isInvalid()
                || mc.player == null
                || mc.world == null
                || mc.player.isDead()
                || mc.player.getHealth() <= 0.0F) {
            return;
        }

        event.setCancelled();
        if (this.blockHolder.isBlocking()) {
            return;
        }

        mc.player.setVelocity(
                event.getVelocityX(),
                mc.player.getVelocity().getY(),
                event.getVelocityZ()
        );
        mc.player.setVelocity(
                mc.player.getVelocity().getX(),
                event.getVelocityY(),
                mc.player.getVelocity().getZ()
        );
    }

    @Subscribe
    public void onScheduledExecutables(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        this.tickTemporaryTimer();

        if (mc.player.isDead() || mc.player.getHealth() <= 0.0F) {
            this.blockHolder.release();
            this.canAttack = false;
            this.hasPendingVelocity = false;
            return;
        }

        final KillAuraModule aura = this.getKillAura();
        final boolean hasKillAuraTarget = this.hasKillAuraTarget(aura);

        if (this.delayUntilGround.getValue()
                && this.blockHolder.isBlocking()
                && !this.shouldDisableAlink()) {
            final boolean shouldRelease =
                    mc.player.isOnGround() && this.hasEnemyInRange(3.0D)
                            || this.alinkStopwatch.hasTimeElapsed(this.alinkTime.getValue().longValue())
                            || this.blockStopwatch.hasTimeElapsed(1000L);

            if (shouldRelease && !this.pendingRelease) {
                if (!hasKillAuraTarget) {
                    this.cancelTimerBoost();
                    this.executeRelease();
                    return;
                }

                this.pendingRelease = true;
                if (this.timerBoost.getValue()) {
                    this.originalTimerValue = TimerHelper.getInstance().timer;
                    TimerHelper.getInstance().timer = this.timerSpeed.getValue().floatValue();
                    this.temporaryTimerTicks = 6;
                    this.debugLog("TimerBoost: " + this.timerSpeed.getValue().floatValue());
                }
                this.releaseDelay = 2;
                return;
            }
        }

        if (this.blockHolder.isBlocking() && this.shouldDisableAlink()) {
            this.executeRelease();
        }

        if (this.releaseDelay > 0) {
            if (!hasKillAuraTarget) {
                this.releaseDelay = 0;
                this.pendingRelease = false;
                this.cancelTimerBoost();
                this.canAttack = false;
                return;
            }

            this.releaseDelay--;
            if (this.releaseDelay == 0) {
                this.executeRelease();
            }
            this.onAttackPacket(aura);
            return;
        }

        this.onAttackPacket(aura);
        final int neededAttacks = this.getDynamicAttackCount();
        if (this.hasPendingVelocity && this.attackCount >= neededAttacks) {
            this.hasPendingVelocity = false;
            this.canAttack = false;
            this.debugLog("Sync " + neededAttacks + " attacks");
        }
    }

    private void tickTemporaryTimer() {
        if (this.temporaryTimerTicks <= 0) {
            return;
        }

        this.temporaryTimerTicks--;
        if (this.temporaryTimerTicks == 0) {
            TimerHelper.getInstance().timer = this.originalTimerValue;
            this.debugLog("Timer reset");
        }
    }

    private void cancelTimerBoost() {
        if (!this.timerBoost.getValue() || this.temporaryTimerTicks <= 0) {
            return;
        }

        TimerHelper.getInstance().timer = this.originalTimerValue;
        this.temporaryTimerTicks = 0;
        this.debugLog("TimerBoost cancelled");
    }

    private KillAuraModule getKillAura() {
        return OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
    }

    private boolean hasKillAuraTarget(final KillAuraModule aura) {
        return aura != null
                && aura.isEnabled()
                && aura.getTargeting().getTarget() != null
                && aura.getTargeting().getTarget().getEntity() != null;
    }

    private void executeRelease() {
        this.blockHolder.release();
        this.canAttack = true;
        this.pendingRelease = false;
    }

    private void onAttackPacket(final KillAuraModule aura) {
        if (mc.player == null
                || mc.world == null
                || mc.player.isDead()
                || mc.player.getHealth() <= 0.0F
                || !this.canAttack
                || !this.hasKillAuraTarget(aura)
                || mc.player.hurtTime <= 0) {
            return;
        }

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

        final int neededAttacks = this.getDynamicAttackCount();
        if (this.attackCount >= neededAttacks) {
            return;
        }

        if (this.attackCount == 0 && this.sprintReset.getValue()) {
            this.wasSprinting = mc.player.isSprinting();
        }

        mc.player.setSprinting(false);
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(
                aura.getTargeting().getTarget().getEntity(),
                mc.player.isSneaking()
        ));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.setVelocity(mc.player.getVelocity().multiply(0.6D, 1.0D, 0.6D));
        this.attackCount++;

        if (this.attackCount >= neededAttacks && this.sprintReset.getValue() && this.wasSprinting) {
            mc.player.setSprinting(true);
        }

        if (this.attackCount >= neededAttacks
                && !this.cooldownSet
                && this.attackCooldown.getValue().intValue() > 0) {
            aura.setAttackCooldown(this.attackCooldown.getValue().intValue());
            this.cooldownSet = true;
        }
    }

    private void debugLog(final String message) {
        if (this.debug.getValue()) {
            ChatUtility.print("AntiKB AttackReduce | " + message);
        }
    }

    private void resetState() {
        this.limitUntilJump = 0;
        this.fallDamage = false;
        this.blockHolder.release();
        this.lag = false;
        this.canAttack = false;
        this.hasPendingVelocity = false;
        this.attackCount = 0;
        this.cooldownSet = false;
        this.wasSprinting = false;
        this.waitingForSprint = false;
        this.releaseDelay = 0;
        this.pendingRelease = false;
        this.lastVelocityY = 0.0D;
        this.lastHorizontalSpeed = 0.0D;
        this.velocityTooWeak = false;

        if (this.temporaryTimerTicks > 0) {
            TimerHelper.getInstance().timer = this.originalTimerValue;
            this.temporaryTimerTicks = 0;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.resetState();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.ATTACK_REDUCE;
    }
}
