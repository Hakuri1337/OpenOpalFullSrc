package wtf.oraculus.client.feature.module.impl.combat.killaura;

import com.google.common.base.Predicates;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.killaura.target.CurrentTarget;
import wtf.oraculus.client.feature.module.impl.combat.killaura.target.KillAuraTargeting;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.math.MathUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.player.RaycastUtility;
import wtf.oraculus.utility.player.RotationInjector;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

import java.util.function.Predicate;

import static wtf.oraculus.client.Constants.mc;

public final class KillAuraModule extends Module {

    private final KillAuraSettings settings = new KillAuraSettings(this);
    private final KillAuraTargeting targeting = new KillAuraTargeting(this.settings);

    public KillAuraModule() {
        super(
                "KillAura",
                "Finds and attacks the most relevant nearby entities.",
                ModuleCategory.COMBAT
        );
    }

    public KillAuraSettings getSettings() {
        return settings;
    }

    @Override
    public String getSuffix() {
        return this.settings.getMode().toString();
    }

    public KillAuraTargeting getTargeting() {
        return targeting;
    }

    public boolean isTargeting() {
        return this.shouldRun() && this.targeting.getTarget() != null;
    }

    public void setAttackCooldown(final int ticks) {
        if (ticks > this.attackCooldownTicks) {
            this.attackCooldownTicks = ticks;
        }
    }

    public int getAttackCooldown() {
        return this.attackCooldownTicks;
    }

    public boolean isOnCooldown() {
        return this.attackCooldownTicks > 0;
    }

    public boolean isActivelyAttacking() {
        return this.targeting.getTarget() != null && (this.attackCooldownTicks > 0 || this.attacks > 0);
    }

    public boolean isFakeBlocking() {
        return this.settings.isFakeAutoBlock() && this.targeting.getTarget() != null && this.shouldRun();
    }

    public boolean requestVelocityResetAttack(final int clicks, final int windowTicks, final boolean wasSprinting, final Double sprintSlowdown) {
        return false;
    }

    @Subscribe
    public void onHandleInput(final MouseHandleInputEvent event) {
        if (this.isOnCooldown()) {
            return;
        }

        if (isConsumingFoodOrPotion()) {
            return;
        }

        final CurrentTarget target = this.targeting.getTarget();
        if (target != null && this.shouldUseTargetHitResult(target)) {
            if (this.settings.isOverrideRaycast()
                    && this.settings.isTickLookahead()
                    && (this.hitResult == null || this.hitResult.getEntity() != target.getEntity())) {
                return;
            }
            mc.crosshairTarget = target.getRotations().hitResult();
        }

        if (target == null || mc.crosshairTarget == null || mc.crosshairTarget.getType() == HitResult.Type.MISS) {
            final double closestDistance = this.targeting.getClosestDistance();
            if (closestDistance <= this.settings.getSwingRange() && SwingDelay.isSwingAvailable(this.settings.getSwingCpsProperty()) && PlayerUtility.getBlockOver() == null) {
                final MouseButton leftButton = MouseHelper.getLeftButton();
                leftButton.setPressed(true, RandomUtility.getRandomInt(2));
                if (this.settings.isHideFakeSwings() && (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY)) {
                    leftButton.setShowSwings(false);
                }
                this.settings.getSwingCpsProperty().resetClick();
            }
            return;
        }

        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        final boolean allowSwingWhenUsing = animationsModule.isEnabled() && animationsModule.isSwingWhileUsing();
        if (mc.player.isUsingItem() && !allowSwingWhenUsing) {
            return;
        }

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            final VelocityModule velocityModule = OraculusClient.getInstance().getModuleRepository().getModule(VelocityModule.class);
            if (this.settings.isHitSelect() && velocityModule != null && velocityModule.isEnabled()) {
                if (velocityModule.getActiveMode() instanceof VelocityMode velocityMode) {
                    if (velocityMode.getHitSelectSkips() > 0) {
                        velocityMode.consumeHitSelectSkip();
                        return;
                    }
                    if (velocityMode.isAttacking()) {
                        return;
                    }
                }
            }

            if (this.isAttackSwingAvailable(target)) {
                final EntityHitResult hitResult = (EntityHitResult) mc.crosshairTarget;
                if (hitResult.getEntity() == target.getEntity()) {
                    final int smartWeaponSlot = getSmartWeaponSlot(target.getEntity());
                    if (smartWeaponSlot != -1) {
                        SlotHelper.setCurrentItem(smartWeaponSlot).silence(SlotHelper.Silence.NONE);
                    }

                    MouseHelper.getLeftButton().setPressed();
                    this.recordAttack(target);
                    this.settings.getCpsProperty().resetClick();
                    SwingDelay.reset();
                }
            } else {
                this.attacks = 0;
            }
        }
    }

    private boolean shouldUseTargetHitResult(final CurrentTarget target) {
        if (mc.player == null || target == null) {
            return false;
        }
        return this.settings.isOverrideRaycast()
                || this.settings.isThroughWalls()
                || PlayerUtility.getDistanceToEntity(target.getEntity()) > mc.player.getEntityInteractionRange();
    }

    private boolean isAttackSwingAvailable(final CurrentTarget target) {
        if (this.isOnCooldown()) {
            return false;
        }

        final boolean smartWeaponAttack = getSmartWeaponSlot(target.getEntity()) != -1;
        if (this.settings.isAttackCooldown19() && mc.player != null && !smartWeaponAttack) {
            return mc.player.getAttackCooldownProgress(0.5F) >= 1.0F;
        }

        if (settings.isHeypixelBypass()) {
            final long time = System.currentTimeMillis();
            final double baseDelay = 1000.0 / settings.getCpsProperty().getCPS();
            final long delay = (long) (baseDelay + (Math.random() - 0.5) * baseDelay * 0.4);
            return time - lastAttackTime >= delay;
        }

        if (target.getKillAuraTarget().isAttackAvailable() || this.attacks > 0) {
            return true;
        }
        return SwingDelay.isSwingAvailable(this.settings.getCpsProperty(), false);
    }

    private void recordAttack(final CurrentTarget target) {
        target.getKillAuraTarget().onAttack(this.attacks == 0);
        if (this.settings.isHeypixelBypass()) {
            this.lastAttackTime = System.currentTimeMillis();
        }
        if (this.attacks > 0) {
            this.attacks--;
        } else {
            this.attacks = 2;
        }
    }

    private int getSmartWeaponSlot(final LivingEntity target) {
        if (mc.player == null || !this.settings.isSmartWeapon() || target == null) {
            return -1;
        }

        if (!isShielding(target)) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }

        return -1;
    }

    private boolean isShielding(final LivingEntity target) {
        if (!target.isUsingItem()) {
            return false;
        }

        final ItemStack activeItem = target.getActiveItem();
        return !activeItem.isEmpty() && activeItem.getItem() instanceof ShieldItem;
    }

    private int attacks;
    private int attackCooldownTicks;
    public long lastAttackTime;

    private boolean onTickRotationApplied;
    private BufferAllocator worldAllocator;

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!targeting.isTargetSelected() || targeting.getTarget() == null) {
            return;
        }

        final LivingEntity target = targeting.getTarget().getEntity();

        if (worldAllocator == null) {
            worldAllocator = new BufferAllocator(8192);
        }
        VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(worldAllocator);
        WorldRenderer rc = new WorldRenderer(vcp);

        if (settings.getVisuals().getProperty("Box").getValue()) {
            final Vec3d position = MathUtility.interpolate(target, event.tickDelta()).add(mc.gameRenderer.getCamera().getPos()).subtract(0.25, 0, 0.25);
            final Vec3d dimensions = new Vec3d(target.getWidth(), target.getHeight(), target.getWidth());
            rc.drawFilledCube(
                    event.matrixStack(),
                    CustomRenderLayers.getPositionColorQuads(true),
                    position, dimensions,
                    ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.25F)
            );
        }

        if (settings.getVisuals().getProperty("Halo").getValue()) {
            final Vec3d pos = MathUtility.interpolate(target, event.tickDelta()).add(mc.gameRenderer.getCamera().getPos());
            final double now = System.currentTimeMillis() / 1000.0;
            final double t = now * 4.0;
            final double baseY = pos.y + target.getHeight() * 0.5;
            final double s = Math.sin(t);
            final double offset = s * 0.45;
            final double ringY = baseY + offset + 0.22;

            final com.ibm.icu.impl.Pair<Integer, Integer> theme = ColorUtility.getClientTheme();
            final int segCount = 64;
            final double radius = Math.max(0.52, target.getWidth() * 0.85);
            final float baseWidth = 3.8F;
            final int glowLayers = 5;
            for (int layer = 0; layer < glowLayers; layer++) {
                final float lw = baseWidth + layer * 2.2F;
                final float alpha = 0.85F * (float) Math.pow(0.72, layer);
                final float brighten = Math.min(0.4F, layer * 0.08F);
                for (int i = 0; i < segCount; i++) {
                    final double a1 = net.minecraft.util.math.MathHelper.TAU * ((double) i / segCount);
                    final double a2 = net.minecraft.util.math.MathHelper.TAU * ((double) (i + 1) / segCount);
                    final double x1 = pos.x + Math.sin(a1) * radius;
                    final double z1 = pos.z + Math.cos(a1) * radius;
                    final double x2 = pos.x + Math.sin(a2) * radius;
                    final double z2 = pos.z + Math.cos(a2) * radius;
                    final float raw = (float) (((now * 0.75) + ((double) i / segCount)) % 1.0);
                    final float pingpong = raw < 0.5F ? raw * 2F : (1F - (raw - 0.5F) * 2F);
                    final float eased = 0.5F - 0.5F * (float) Math.cos(pingpong * Math.PI);
                    final int grad = ColorUtility.interpolateColorsHSB(theme.first, theme.second, eased);
                    final int brighterGrad = ColorUtility.brighter(grad, brighten);
                    final int color = ColorUtility.applyOpacity(brighterGrad, alpha);
                    rc.drawLine(event.matrixStack(), CustomRenderLayers.getLines(lw, true), new Vec3d(x1, ringY, z1), new Vec3d(x2, ringY, z2), color);
                }
            }
        }

        vcp.draw();
    }

    private EntityHitResult hitResult;

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }

        if (!shouldRun()) {
            this.targeting.reset();
            this.clearOnTickRotation();
            return;
        }

        this.targeting.update();

        final CurrentTarget target = this.targeting.getRotationTarget();
        if (target == null) {
            this.clearOnTickRotation();
            updateAutoblock();
            return;
        }

        if (this.settings.getRotationMode() == RotationInjector.RotationMode.ON_TICK) {
            this.onTickRotationApplied = true;
        } else {
            this.clearOnTickRotation();
        }

        RotationInjector.applyRotation(
                target.getRotations().rotation(),
                settings.getRotationMode(),
                settings.createRotationModel()
        );

        updateAutoblock();
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.settings.isTickLookahead() || this.targeting.getRotationTarget() == null || !shouldRun()) {
            return;
        }

        this.targeting.update();

        final CurrentTarget target = this.targeting.getRotationTarget();
        if (target == null) {
            return;
        }

        event.setYaw(mc.player.getYaw());
        event.setPitch(mc.player.getPitch());
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (!this.settings.isTickLookahead()) {
            return;
        }
        final CurrentTarget target = this.targeting.getTarget();
        Predicate<Entity> entityPredicate = target == null ? Predicates.alwaysTrue() : e -> e == target.getEntity();
        this.hitResult = RaycastUtility.raycastEntity(this.settings.getRange(), 1.0F, mc.player.getYaw(), mc.player.getPitch(), entityPredicate);
    }

    private void updateAutoblock() {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            releaseAutoblock();
            return;
        }

        if (isConsumingFoodOrPotion()) {
            releaseAutoblock();
            return;
        }

        final KillAuraSettings.AutoblockMode autoblockMode = this.settings.getAutoblockMode();
        if (autoblockMode == KillAuraSettings.AutoblockMode.OFF) {
            releaseAutoblock();
            return;
        }

        final CurrentTarget target = this.targeting.getTarget();
        if (target == null) {
            releaseAutoblock();
            return;
        }

        if (autoblockMode == KillAuraSettings.AutoblockMode.FAKE) {
            return;
        }

        if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            releaseAutoblock();
            return;
        }

        final MouseButton rightButton = MouseHelper.getRightButton();
        rightButton.setPressed(true, 2);
        rightButton.setShowSwings(false);
    }

    private void releaseAutoblock() {
        final MouseButton rightButton = MouseHelper.getRightButton();
        rightButton.setPressed(false, 0);
        rightButton.setShowSwings(true);
    }

    private boolean shouldRun() {
        if (mc.player == null) {
            return false;
        }

        if (isConsumingFoodOrPotion()) {
            return false;
        }

        if (settings.isRequireAttackKey() && !mc.options.attackKey.isPressed()) {
            return false;
        }

        final ItemStack heldItem = SlotHelper.getInstance().getMainHandStack(mc.player);
        if (settings.isRequireWeapon() &&
                !(heldItem.isIn(ItemTags.SWORDS) || heldItem.isIn(ItemTags.AXES) || heldItem.isIn(ItemTags.PICKAXES))) {
            return false;
        }

        return true;
    }

    private boolean isConsumingFoodOrPotion() {
        if (mc.player == null || !mc.player.isUsingItem()) {
            return false;
        }

        final ItemStack stack = mc.player.getActiveItem();
        return !stack.isEmpty() && (stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof PotionItem);
    }

    @Override
    protected void onDisable() {
        this.clearOnTickRotation();
        this.targeting.reset();
        this.hitResult = null;
        this.attacks = 0;
        this.attackCooldownTicks = 0;
        this.haloTrailHeights.clear();
        releaseAutoblock();
        super.onDisable();
    }

    private void clearOnTickRotation() {
        if (this.onTickRotationApplied) {
            RotationInjector.clear();
            this.onTickRotationApplied = false;
        }
    }

    private final java.util.ArrayDeque<Double> haloTrailHeights = new java.util.ArrayDeque<>();
}
