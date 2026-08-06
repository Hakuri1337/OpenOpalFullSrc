package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.targetinfo;

import com.ibm.icu.impl.Pair;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import wtf.oraculus.client.Constants;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.impl.combat.TeamsModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.target.CurrentTarget;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.image.NVGImageRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.repository.ImageRepository;
import wtf.oraculus.client.renderer.shader.LiquidGlassV2Renderer;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.ESPUtility;
import wtf.oraculus.utility.render.OrderedTextVisitor;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_IMAGE_NODELETE;
import static org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle;
import static wtf.oraculus.client.Constants.VG;
import static wtf.oraculus.client.Constants.mc;

public final class TargetInfoElement implements IOverlayElement, IslandTrigger {

    private static final NVGTextRenderer BOLD_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer MEDIUM_FONT = FontRepository.getFont("productsans-medium");
    private static final NVGTextRenderer ICON_FONT = FontRepository.getFont("materialicons-regular");
    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");
    private static final float RVN_VANILLA_TEXT_SCALE = 1.44F;
    public static final Map<String, AtomicInteger> playerHealthMap = new HashMap<>();

    private final Animation targetAnimation, healthAnimation;
    private final TargetInfoSettings settings;
    private Target currentTarget, lastTarget;
    private Target islandTarget;
    private boolean islandTriggerActive;
    private NVGImageRenderer gayBackground;

    public TargetInfoElement(final OverlayModule module) {
        this.settings = new TargetInfoSettings(module);

        this.targetAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
        this.targetAnimation.setValue(1);

        this.healthAnimation = new Animation(Easing.EASE_OUT_EXPO, 1000);
    }

    public TargetInfoSettings getSettings() {
        return this.settings;
    }

    public void initialize() {
    }

    public void onReceivePacket(final ReceivePacketEvent event) {
        if (!(event.getPacket() instanceof ScoreboardScoreUpdateS2CPacket packet)
                || mc.world == null || mc.player == null) {
            return;
        }

        if (("belowHealth".equals(packet.objectiveName()) || "health".equals(packet.objectiveName()))
                && !packet.scoreHolderName().equals(mc.player.getGameProfile().name())) {
            playerHealthMap.computeIfAbsent(packet.scoreHolderName(), ignored -> new AtomicInteger())
                    .set(packet.score());
        }
    }

    public void applyScoreboardHealth() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !playerHealthMap.containsKey(player.getName().getString())) {
                continue;
            }

            player.setHealth(Math.max(1.0F, playerHealthMap.get(player.getName().getString()).get()));
        }
    }

    @Override
    public void render(DrawContext context, float delta, boolean isBloom) {
        final Target target = this.getTarget();
        this.updateIslandTrigger(target);

        if (this.settings.isDynamicIsland()) {
            if (target != null) {
                final float absorption = target.entity.getAbsorptionAmount();
                final float healthPercent = MathHelper.clamp(
                        (target.entity.getHealth() + absorption) / (target.entity.getMaxHealth() + absorption),
                        0, 1
                );
                this.healthAnimation.run(healthPercent);
            }
            return;
        }

        if (target == null) {
            return;
        }

        if (this.settings.isCompact()) {
            this.renderCompact(context, target, isBloom);
            return;
        }

        if (this.settings.isGay()) {
            this.renderGay(context, target, isBloom);
            return;
        }

        if (this.settings.isRvn()) {
            this.renderRvn(context, target, isBloom);
            return;
        }

        if (this.settings.isLiquidGlassMode()) {
            this.renderLiquidGlass(target, delta, isBloom);
            return;
        }

        final float scale = this.settings.getScale();

        final float targetNameSize = 6;
        final float hpSize = 5;

        final String targetName = Formatting.WHITE + target.getFormattedName();
        final int targetNameColor = -1;

        final int skinTextureGlId = isBloom ? -1 : this.getSkinTextureGlId(target.entity);

        final float padding = 3;
        final float headOffset = 22.5F;
        final float equipmentWidth = 55;

        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();

        final float width = (padding * 2) + Math.max(50, Math.max(equipmentWidth, BOLD_FONT.getStringWidth(targetName, targetNameSize))) + headOffset + 1;
        final float height = (padding * 2) + 25.5F;

        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();

        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);

        final float targetAnimationProgress = this.targetAnimation.getValue();
        final float healthAnimationProgress = this.healthAnimation.getValue();

        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();

        final float trueHealthPercent = MathHelper.clamp(
                (target.entity.getHealth() + target.entity.getAbsorptionAmount()) / (target.entity.getMaxHealth() + target.entity.getAbsorptionAmount()),
                0, 1
        );

        this.healthAnimation.run(trueHealthPercent);


        final String finalTargetName = targetName;
        final boolean liquidGlass = this.settings.isLiquidGlassV2();
        final boolean liquidGlassRendered = liquidGlass
                && !isBloom
                && LiquidGlassV2Renderer.draw(
                        x, y, width * scale, height * scale, 4 * scale,
                        this.settings.getLiquidGlassV2Settings(), targetAnimationProgress
                );
        final boolean renderNormalBackground = !liquidGlass || (!isBloom && !liquidGlassRendered);
        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);

            // background
            if (renderNormalBackground) {
                NVGRenderer.roundedRect(x, y, width, height, 4, NVGRenderer.BLUR_PAINT);
                NVGRenderer.roundedRect(x, y, width, height, 4, 0x80090909);
            }

            // name
            BOLD_FONT.drawString(finalTargetName, x + padding + headOffset, y + 9, targetNameSize, targetNameColor);

            // health
            final float absorption = target.entity.getAbsorptionAmount();
            final float heartWidth = ICON_FONT.getStringWidth("\uE87D", hpSize);
            final String hp = HEALTH_DF.format(target.entity.getHealth() + absorption);

            ICON_FONT.drawString((absorption > 0 ? "" : Formatting.RED) + "\uE87D", x + width - (padding * 2.5F), y + 29, hpSize, 0xFFFFC247);
            MEDIUM_FONT.drawString(hp, x + width - padding - MEDIUM_FONT.getStringWidth(hp, hpSize) - heartWidth - 0.25F, y + 28.5F, hpSize, -1);

            // health bar
            {
                final float healthBarWidth = width - (padding * 2.75F) - MEDIUM_FONT.getStringWidth(hp.length() > 2 ? hp : "88.", hpSize) - heartWidth;

                // full width bg
                NVGRenderer.roundedRect(
                        x + padding - 0.125F, y + 24.75F,
                        healthBarWidth, 4, 5 / 3F,
                        ColorUtility.applyOpacity(ColorUtility.darker(theme.second, 0.8F), 0.6F)
                );

                // animated health bg
                if (healthAnimationProgress > 0.01) {
                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            healthAnimationProgress * healthBarWidth, 4, 5 / 3F,
                            ColorUtility.darker(theme.first, 0.6F), ColorUtility.darker(theme.second, 0.6F), 0
                    );
                }

                // true health
                if (trueHealthPercent > 0.01) {
                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            trueHealthPercent * healthBarWidth, 4, 5 / 3F,
                            theme.first, theme.second, 0
                    );

                    NVGRenderer.roundedRectGradient(
                            x + padding - 0.125F, y + 24.75F,
                            trueHealthPercent * healthBarWidth, 4, 5 / 3F,
                         Color.TRANSLUCENT, ColorUtility.applyOpacity(0xFF000000, 0.6F), 90
                    );
                }
            }

            // head
            renderHead:
            {
                if (skinTextureGlId == -1) {
                    break renderHead;
                }

                nvgBeginPath(VG);

                final float headX = x + padding + 0.25F;
                final float headY = y + padding;
                final float headScale = 8 / 3F;
                final float size = 19.5F;

                final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
                nvgImagePattern(VG, headX - ((64 - 4.8F) / headScale), headY - ((64 - 3) / headScale),
                        64 * headScale, 64 * headScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT);
//                    nvgShapeAntiAlias(VG, false);

                if (target.entity.hurtTime > 0) {
                    final float damageFactor = target.entity.hurtTime / (float) target.entity.maxHurtTime;
                    final float reductionFactor = 0.6F;
                    final float r = Math.min(1, 1 + ((1 - reductionFactor) * damageFactor));
                    final float g = 1 - (damageFactor * reductionFactor);
                    final float b = 1 - (damageFactor * reductionFactor);
                    NVGRenderer.applyColor(new Color(r, g, b).getRGB(), NVGRenderer.NVG_COLOR_1);
                    NVGRenderer.NVG_PAINT.innerColor(NVGRenderer.NVG_COLOR_1);
                }

                nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
                nvgRoundedRect(VG, headX, headY, size, size, 2);
                nvgFill(VG);
                nvgClosePath(VG);
//                    nvgShapeAntiAlias(VG, true);
            }

            // render equipment
            {
                final List<ItemStack> equipment = new ArrayList<>();

                for (final EquipmentSlot equipmentSlot : AttributeModifierSlot.ARMOR) {
                    if (equipmentSlot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                        continue;
                    }
                    equipment.add(target.entity.getEquippedStack(equipmentSlot));
                }

                equipment.add(target.entity.getMainHandStack());
                Collections.reverse(equipment);

                final float stackScale = 0.625F * scale;
                final float stackTextScale = 0.6F;

                final int equipmentCount = equipment.size();

                // slot backgrounds
                for (int i = 0; i < equipmentCount; i++) {
                    final float boxX = x + (i * 11.5F) + padding + headOffset - 0.5F;
                    final float boxY = y + padding + 8.5F;
                    NVGRenderer.roundedRect(boxX, boxY, 10.5F, 10.5F, 1, ColorUtility.applyOpacity(Colors.BLACK, 0.2F));
                }

                // reset alpha
                NVGRenderer.globalAlpha(1);

                MinecraftRenderer.addToQueue(() -> {
                    // draw now so alpha doesn't affect previously queued items
                    context.createNewRootLayer();

                    GlStateManager._enableBlend();
//                    RenderSystem.setShaderColor(1, 1, 1, targetAnimationProgress);
//                    DiffuseLighting.disableGuiDepthLighting();

                    for (int i = 0; i < equipmentCount; i++) {
                        final float offsetX = (i * 11.6F) + padding + headOffset - 0.5F / scale;
                        final float offsetY = padding + 8.5F;
                        final float stackX = x + offsetX * scale;
                        final float stackY = y + offsetY * scale;

                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(stackX, stackY);
                        context.getMatrices().scale(stackScale, stackScale);

                        context.getMatrices().scale(stackTextScale, stackTextScale);
//                        context.getMatrices().translate(6, 8);

                        final ItemStack stack = equipment.get(i);
                        context.getMatrices().pushMatrix();

                        context.getMatrices().transform(new Vector3f(-6, -12, -200));
                        context.getMatrices().scale(1 / stackTextScale, 1 / stackTextScale);

                        if (stack.getItem() instanceof BlockItem) {
                            if (targetAnimationProgress >= 0.5F) {
                                context.drawItem(stack, 0, 0, -200);
                            }
                        } else {
                            context.drawItem(stack, 0, 0, -200);
                        }
                        context.getMatrices().popMatrix();

                        EnchantmentHelper
                                .getEnchantments(stack)
                                .getEnchantmentEntries()
                                .forEach((entry) -> entry.getKey().getKey().ifPresent(key -> {
                                    final String shortName = ESPUtility.ENCHANTMENT_NAMES.get(key);
                                    if (shortName == null) {
                                        return;
                                    }
                                    context.drawText(
                                            mc.textRenderer,
                                            Text.of(shortName + entry.getIntValue()).asOrderedText(), 2, 7, -1, true);
                                }));

                        context.getMatrices().popMatrix();
                    }
                    GlStateManager._disableBlend();
                });
            }
        });

        if (currentTarget != null) {
            lastTarget = currentTarget;
        }
    }

    private void renderCompact(final DrawContext context, final Target target, final boolean isBloom) {
        final float scale = this.settings.getScale();
        final float padding = 4;
        final float avatarSize = 30;
        final float contentX = padding + avatarSize + 5;
        final float textSize = 7;
        final float nameSize = 8;
        final float height = 47;
        final float barHeight = 3.28125F;
        final float barBottomPadding = 3.5F;
        final LivingEntity entity = target.entity;
        final String name = target.getFormattedName();
        final String healthText = this.getCompactHealth(entity);
        final String distance = this.getCompactDistance(entity);
        final String heldItem = entity.getMainHandStack().isEmpty()
                ? "Empty"
                : entity.getMainHandStack().getName().getString();

        final float textWidth = Math.max(
                BOLD_FONT.getStringWidth(name, nameSize),
                Math.max(
                        MEDIUM_FONT.getStringWidth(healthText, textSize),
                        Math.max(
                                MEDIUM_FONT.getStringWidth(distance, textSize),
                                MEDIUM_FONT.getStringWidth(heldItem, textSize)
                        )
                )
        );
        final float width = Math.max(contentX + textWidth + padding, avatarSize + padding * 2);
        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();
        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();
        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);

        final int backgroundColor = ColorUtility.applyOpacity(0xFF090909, this.settings.getBackgroundOpacity());
        final int shadowColor = ColorUtility.applyOpacity(Colors.BLACK, 0.5F);
        final int fixedHealthColor = 0xFFDE2910;
        final float targetAnimationProgress = this.targetAnimation.getValue();
        final float currentHealth = Math.max(0, entity.getHealth() + entity.getAbsorptionAmount());
        final float maximumHealth = Math.max(1, entity.getMaxHealth() + entity.getAbsorptionAmount());
        final float healthPercent = MathHelper.clamp(currentHealth / maximumHealth, 0, 1);
        final float displayHealthPercent = MathHelper.clamp(this.healthAnimation.getValue(), 0, 1);
        this.healthAnimation.run(healthPercent);

        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);
            if (this.settings.isShadow()) {
                NVGRenderer.roundedRect(x + 1.5F, y + 2, width, height, 4, shadowColor);
            }
            if (this.settings.isBlur()) {
                NVGRenderer.roundedRect(x, y, width, height, 4, NVGRenderer.BLUR_PAINT);
            }
            NVGRenderer.roundedRect(x, y, width, height, 4, backgroundColor);

            final int skinTextureGlId = isBloom ? -1 : this.getSkinTextureGlId(entity);
            if (skinTextureGlId != -1) {
                final float avatarY = y + 6;
                this.renderCompactHead(target, x + padding, avatarY, avatarSize, skinTextureGlId);
            }

            final boolean textShadow = this.settings.isShadow();
            this.drawCompactText(name, x + contentX, y + 10, nameSize, -1, textShadow);
            this.drawCompactText(healthText, x + contentX, y + 19, textSize, 0xFFAAAAAA, textShadow);
            this.drawCompactText(distance, x + contentX, y + 27, textSize, 0xFFAAAAAA, textShadow);
            this.drawCompactText(heldItem, x + contentX, y + 35, textSize, 0xFFAAAAAA, textShadow);

            final float barX = x + padding;
            final float barY = y + height - barBottomPadding - barHeight;
            final float barWidth = width - padding * 2;
            NVGRenderer.roundedRect(barX, barY, barWidth, barHeight, 1.25F, 0x66222222);
            if (displayHealthPercent > 0.01F) {
                NVGRenderer.roundedRect(barX, barY, barWidth * displayHealthPercent, barHeight, 1.25F, fixedHealthColor);
            }
            if (healthPercent > 0.01F && displayHealthPercent < healthPercent) {
                NVGRenderer.roundedRect(barX, barY, barWidth * healthPercent, barHeight, 1.25F,
                        ColorUtility.applyOpacity(fixedHealthColor, 130));
            }
            NVGRenderer.globalAlpha(1);
        });

    }

    private void renderGay(final DrawContext context, final Target target, final boolean isBloom) {
        final float scale = this.settings.getScale();
        final float width = 158;
        final float height = 64;
        final float radius = 5;
        final float padding = 4;
        final float headSize = 56;
        final float contentXOffset = padding + headSize + 5;
        final float contentWidth = width - contentXOffset - 5;
        final float slotSize = 12;
        final float slotGap = 4.5F;
        final float slotsY = 30;
        final float healthBarY = 48;
        final float healthBarHeight = 12;
        final LivingEntity entity = target.entity;
        final String name = this.ellipsize(BOLD_FONT, target.getFormattedName(), contentWidth, 10);
        final String distance = String.format(Locale.ROOT, "Distance: %.1f", (double) mc.player.distanceTo(entity));
        final List<ItemStack> equipment = List.of(
                entity.getEquippedStack(EquipmentSlot.HEAD),
                entity.getEquippedStack(EquipmentSlot.CHEST),
                entity.getEquippedStack(EquipmentSlot.LEGS),
                entity.getEquippedStack(EquipmentSlot.FEET),
                entity.getMainHandStack()
        );

        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();
        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();
        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);

        final float currentHealth = Math.max(0, entity.getHealth() + entity.getAbsorptionAmount());
        final float maximumHealth = Math.max(1, entity.getMaxHealth() + entity.getAbsorptionAmount());
        final float healthPercent = MathHelper.clamp(currentHealth / maximumHealth, 0, 1);
        this.healthAnimation.run(healthPercent);
        final float displayHealthPercent = MathHelper.clamp(this.healthAnimation.getValue(), 0, 1);
        final float targetAnimationProgress = this.targetAnimation.getValue();
        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();

        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);

            if (!isBloom) {
                if (this.gayBackground == null) {
                    this.gayBackground = ImageRepository.getImage("images/targethud_gay.png");
                }
                if (this.gayBackground != null) {
                    this.gayBackground.drawRoundedImageCover(x, y, width, height, radius, 2048, 1024);
                } else {
                    NVGRenderer.roundedRect(x, y, width, height, radius, 0xFF555555);
                }
            }

            if (this.settings.isBlackOverlay()) {
                NVGRenderer.roundedRect(x, y, width, height, radius, ColorUtility.applyOpacity(Colors.BLACK, 100));
            }

            final int skinTextureGlId = isBloom ? -1 : this.getSkinTextureGlId(entity);
            if (skinTextureGlId != -1) {
                this.renderGayHead(target, x + padding, y + padding, headSize, skinTextureGlId);
            } else if (!isBloom) {
                NVGRenderer.roundedRect(x + padding, y + padding, headSize, headSize, 4, 0xFFCCCCCC);
            }

            BOLD_FONT.drawString(name, x + contentXOffset, y + 16, 10, Colors.WHITE);
            MEDIUM_FONT.drawString(distance, x + contentXOffset, y + 26, 7, Colors.WHITE);

            for (int index = 0; index < equipment.size(); index++) {
                if (equipment.get(index).isEmpty()) {
                    final float slotX = x + contentXOffset + index * (slotSize + slotGap);
                    NVGRenderer.roundedRect(slotX, y + slotsY, slotSize, slotSize, 6, 0x995F5F5F);
                }
            }

            final float barX = x + contentXOffset;
            final float barY = y + healthBarY;
            NVGRenderer.roundedRect(barX, barY, contentWidth, healthBarHeight, radius, 0x66000000);
            if (displayHealthPercent > 0.01F) {
                NVGRenderer.roundedRectGradient(
                        barX, barY, contentWidth * displayHealthPercent, healthBarHeight, radius,
                        theme.first, theme.second, 0
                );
            }

            NVGRenderer.globalAlpha(1);
        });

        if (!isBloom && targetAnimationProgress >= 0.5F) {
            MinecraftRenderer.addToQueue(() -> {
                context.createNewRootLayer();
                GlStateManager._enableBlend();
                for (int index = 0; index < equipment.size(); index++) {
                    final ItemStack stack = equipment.get(index);
                    if (stack.isEmpty()) {
                        continue;
                    }

                    final float slotX = contentXOffset + index * (slotSize + slotGap);
                    final float itemX = x + slotX * scale;
                    final float itemY = y + slotsY * scale;
                    final float itemScale = slotSize / 16.F * scale;
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(itemX, itemY);
                    context.getMatrices().scale(itemScale, itemScale);
                    context.drawItem(stack, 0, 0);
                    context.getMatrices().popMatrix();
                }
                GlStateManager._disableBlend();
            });
        }
    }

    private void renderRvn(final DrawContext context, final Target target, final boolean isBloom) {
        final LivingEntity entity = target.entity;
        final float scale = this.settings.getScale();
        final float padding = 4;
        final float textSize = 12.8F;
        final float height = 38;
        final boolean vanillaFont = this.settings.isRvnFontVanilla();
        final float textGap = vanillaFont ? 5 : 3;

        final String teamLetter = this.getRvnTeamLetter(entity);
        final String targetName = entity.getName().getString();
        final String healthText = String.format(
                Locale.ROOT,
                entity.getAbsorptionAmount() > 0.05F ? "%.1f+%.1f" : "%.1f",
                (double) entity.getHealth(), (double) entity.getAbsorptionAmount()
        );
        final String direction = this.getRvnDirection(entity);
        final float teamWidth = this.getRvnTextWidth(teamLetter, textSize, vanillaFont, true);
        final float nameWidth = this.getRvnTextWidth(targetName, textSize, vanillaFont, true);
        final float healthWidth = this.getRvnTextWidth(healthText, textSize, vanillaFont, false);
        final float directionWidth = this.getRvnTextWidth(direction, textSize, vanillaFont, false);
        final float width = padding * 2 + teamWidth + nameWidth + healthWidth + directionWidth + textGap * 3;

        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();
        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);
        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();

        final float absorption = entity.getAbsorptionAmount();
        final float health = Math.max(0, entity.getHealth() + absorption);
        final float maximumHealth = Math.max(1, entity.getMaxHealth() + absorption);
        final float healthPercent = MathHelper.clamp(health / maximumHealth, 0, 1);
        this.healthAnimation.run(healthPercent);
        final float animatedHealthPercent = MathHelper.clamp(this.healthAnimation.getValue(), 0, 1);
        final float targetAnimationProgress = this.targetAnimation.getValue();
        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();
        final int teamColor = this.getRvnTeamColor(entity);
        final int healthColor = this.settings.isRvnHealthBarTheme()
                ? theme.first
                : this.getRvnHealthColor(health);
        final int oldHealthColor = this.settings.isRvnHealthBarTheme()
                ? ColorUtility.darker(theme.first, 0.55F)
                : ColorUtility.darker(this.getRvnHealthColor(animatedHealthPercent * maximumHealth), 0.45F);
        final float radius = this.settings.isRvnLiquidGlassV2()
                ? this.settings.getRvnLiquidGlassCornerRadius()
                : this.settings.getRvnBackgroundCornerRadius();

        final boolean liquidGlass = this.settings.isRvnLiquidGlassV2();
        final boolean liquidGlassRendered = liquidGlass
                && !isBloom
                && LiquidGlassV2Renderer.draw(
                        x, y, width * scale, height * scale, radius * scale,
                        this.settings.getRvnLiquidGlassV2Settings(), targetAnimationProgress
                );
        final boolean renderNormalBackground = !liquidGlass || (!isBloom && !liquidGlassRendered);

        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);

            if (this.settings.isRvnOutline()) {
                NVGRenderer.roundedRectOutlineGradient(
                        x, y, width, height, radius, 1.25F,
                        theme.first, theme.second, 0
                );
            }

            if (renderNormalBackground) {
                NVGRenderer.roundedRect(x, y, width, height, radius, NVGRenderer.BLUR_PAINT);
                NVGRenderer.roundedRect(x, y, width, height, radius, ColorUtility.applyOpacity(Colors.BLACK, 0.1F));
            }

            final float teamX = x + padding;
            final float nameX = teamX + teamWidth + textGap;
            final float healthX = nameX + nameWidth + textGap;
            final float directionX = healthX + healthWidth + textGap;
            if (!vanillaFont) {
                final float textY = y + 16;
                BOLD_FONT.drawString(teamLetter, teamX, textY, textSize, teamColor);
                BOLD_FONT.drawString(targetName, nameX, textY, textSize, teamColor);
                MEDIUM_FONT.drawString(healthText, healthX, textY, textSize, this.getRvnHealthColor(health));
                MEDIUM_FONT.drawString(direction, directionX, textY, textSize, 0xFFFFFFFF);
            }

            final float barX = x + padding;
            final float barY = y + height - 13;
            final float barWidth = width - padding * 2;
            final float barHeight = 5;
            final float barRadius = Math.min(2.5F, radius);
            NVGRenderer.roundedRect(barX, barY, barWidth, barHeight, barRadius, 0x66333333);

            if (animatedHealthPercent > healthPercent + 0.001F) {
                if (this.settings.isRvnHealthBarTheme()) {
                    NVGRenderer.roundedRectGradient(
                            barX, barY, barWidth * animatedHealthPercent, barHeight, barRadius,
                            oldHealthColor, ColorUtility.darker(theme.second, 0.55F), 0
                    );
                } else {
                    NVGRenderer.roundedRect(
                            barX, barY, barWidth * animatedHealthPercent, barHeight, barRadius, oldHealthColor
                    );
                }
            }

            if (healthPercent > 0.001F) {
                if (this.settings.isRvnHealthBarTheme()) {
                    NVGRenderer.roundedRectGradient(
                            barX, barY, barWidth * healthPercent, barHeight, barRadius,
                            theme.first, theme.second, 0
                    );
                } else {
                    NVGRenderer.roundedRect(
                            barX, barY, barWidth * healthPercent, barHeight, barRadius, healthColor
                    );
                }
            }

            NVGRenderer.globalAlpha(1);
        });

        if (vanillaFont && !isBloom && targetAnimationProgress >= 0.01F) {
            final float finalTeamX = x + padding;
            final float finalNameX = finalTeamX + teamWidth + textGap;
            final float finalHealthX = finalNameX + nameWidth + textGap;
            final float finalDirectionX = finalHealthX + healthWidth + textGap;
            final int animatedTeamColor = ColorUtility.applyOpacity(teamColor, targetAnimationProgress);
            final int animatedHealthColor = ColorUtility.applyOpacity(this.getRvnHealthColor(health), targetAnimationProgress);
            final int animatedWhite = ColorUtility.applyOpacity(Colors.WHITE, targetAnimationProgress);
            MinecraftRenderer.addToQueue(() -> {
                context.getMatrices().pushMatrix();
                context.getMatrices().scale(scale * RVN_VANILLA_TEXT_SCALE, scale * RVN_VANILLA_TEXT_SCALE);
                final int textY = (int) ((y + 7) / (scale * RVN_VANILLA_TEXT_SCALE));
                context.drawText(mc.textRenderer, Text.literal(teamLetter), (int) (finalTeamX / (scale * RVN_VANILLA_TEXT_SCALE)), textY, animatedTeamColor, false);
                context.drawText(mc.textRenderer, Text.literal(targetName), (int) (finalNameX / (scale * RVN_VANILLA_TEXT_SCALE)), textY, animatedTeamColor, false);
                context.drawText(mc.textRenderer, Text.literal(healthText), (int) (finalHealthX / (scale * RVN_VANILLA_TEXT_SCALE)), textY, animatedHealthColor, false);
                context.drawText(mc.textRenderer, Text.literal(direction), (int) (finalDirectionX / (scale * RVN_VANILLA_TEXT_SCALE)), textY, animatedWhite, false);
                context.getMatrices().popMatrix();
            });
        }
    }

    private void renderLiquidGlass(final Target target, final float delta, final boolean isBloom) {
        final LivingEntity entity = target.entity;
        final float scale = this.settings.getScale();
        final float padding = 4;
        final float headSize = 26;
        final float contentGap = 5;
        final float nameSize = 7;
        final float height = 34;
        final float radius = 10;
        final String name = Formatting.WHITE + target.getFormattedName();
        final float nameWidth = BOLD_FONT.getStringWidth(name, nameSize);
        final float contentWidth = Math.max(48, nameWidth + 2);
        final float width = padding + headSize + contentGap + contentWidth + padding + 1;

        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();
        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();
        screenPosition.setWidth(width * scale);
        screenPosition.setHeight(height * scale);

        final float absorption = entity.getAbsorptionAmount();
        final float health = Math.max(0, entity.getHealth() + absorption);
        final float maximumHealth = Math.max(1, entity.getMaxHealth() + absorption);
        final float healthPercent = MathHelper.clamp(health / maximumHealth, 0, 1);
        this.healthAnimation.run(healthPercent);
        final float animatedHealthPercent = MathHelper.clamp(this.healthAnimation.getValue(), 0, 1);
        final float targetAnimationProgress = this.targetAnimation.getValue();
        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();

        final float contentX = x + padding + headSize + contentGap;
        final float barY = y + 22.5F;
        final float barHeight = 3;
        final float rawDamageProgress = entity.maxHurtTime <= 0
                ? 0
                : MathHelper.clamp((entity.hurtTime - delta) / entity.maxHurtTime, 0, 1);
        final float damageProgress = rawDamageProgress * rawDamageProgress;
        final boolean liquidGlassV2 = this.settings.isLiquidGlassModeV2();
        final boolean liquidGlassV2Rendered = liquidGlassV2
                && !isBloom
                && LiquidGlassV2Renderer.draw(
                        x, y, width * scale, height * scale, radius * scale,
                        this.settings.getLiquidGlassModeV2Settings(), targetAnimationProgress
                );
        final boolean renderNormalBackground = !liquidGlassV2 || (!isBloom && !liquidGlassV2Rendered);

        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            NVGRenderer.globalAlpha(targetAnimationProgress);

            if (renderNormalBackground) {
                NVGRenderer.roundedRect(x, y, width, height, radius, NVGRenderer.BLUR_PAINT);
                NVGRenderer.roundedRect(x, y, width, height, radius, 0x2EFFFFFF);
                NVGRenderer.roundedRectOutline(
                        x + 0.35F, y + 0.35F, width - 0.7F, height - 0.7F,
                        radius - 0.35F, 0.65F, 0xA6FFFFFF
                );
            }

            if (!isBloom) {
                final int skinTextureGlId = this.getSkinTextureGlId(entity);
                if (skinTextureGlId != -1) {
                    this.renderLiquidGlassHead(
                            target, x + padding, y + padding, headSize, 5, skinTextureGlId
                    );
                } else {
                    NVGRenderer.roundedRect(x + padding, y + padding, headSize, headSize, 5, 0x66FFFFFF);
                }

                if (damageProgress > 0.001F) {
                    NVGRenderer.roundedRect(
                            x + padding, y + padding, headSize, headSize, 5,
                            ColorUtility.applyOpacity(0xFFFF3434, damageProgress * 0.48F)
                    );
                    NVGRenderer.roundedRectOutline(
                            x + padding, y + padding, headSize, headSize, 5,
                            0.75F + damageProgress * 2.25F,
                            ColorUtility.applyOpacity(0xFFFF3C3C, damageProgress * 0.95F)
                    );
                }
            }

            BOLD_FONT.drawString(name, contentX, y + 13, nameSize, Colors.WHITE);
            NVGRenderer.rect(contentX, barY, contentWidth, barHeight, 0x66565A5D);
            if (animatedHealthPercent > 0.001F) {
                NVGRenderer.rectGradient(
                        contentX, barY, contentWidth * animatedHealthPercent, barHeight,
                        theme.first, theme.second, 0
                );
            }

            NVGRenderer.globalAlpha(1);
        });

        if (this.currentTarget != null) {
            this.lastTarget = this.currentTarget;
        }
    }

    private void renderLiquidGlassHead(
            final Target target,
            final float x,
            final float y,
            final float size,
            final float radius,
            final int skinTextureGlId
    ) {
        final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
        this.renderLiquidGlassSkinLayer(skinTextureHandle, x, y, size, radius, 8, 8);
        this.renderLiquidGlassSkinLayer(skinTextureHandle, x, y, size, radius, 40, 8);
    }

    private void renderLiquidGlassSkinLayer(
            final int skinTextureHandle,
            final float x,
            final float y,
            final float size,
            final float radius,
            final int sourceX,
            final int sourceY
    ) {
        final float skinScale = size / 8.F;
        nvgBeginPath(VG);
        nvgImagePattern(
                VG, x - sourceX * skinScale, y - sourceY * skinScale,
                64 * skinScale, 64 * skinScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT
        );
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgRoundedRect(VG, x, y, size, size, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    private float getRvnTextWidth(final String text, final float size, final boolean vanillaFont, final boolean bold) {
        if (!vanillaFont) {
            return (bold ? BOLD_FONT : MEDIUM_FONT).getStringWidth(text, size);
        }

        // TextRenderer.getWidth can bake glyphs and upload textures. Rvn is also
        // rendered during the bloom pass, where that upload is illegal. Keep the
        // layout deterministic without touching the Minecraft font renderer here.
        float width = 0;
        for (int index = 0; index < text.length(); index++) {
            final char character = text.charAt(index);
            width += character > 0xFF
                    ? 8.5F
                    : switch (character) {
                case ' ', '.', ':', 'i', 'l', 'I', '!', '|' -> 3.0F;
                case 'W', 'M', '@', '#' -> 7.0F;
                default -> 6.0F;
            };
        }
        return width * RVN_VANILLA_TEXT_SCALE;
    }

    private String getRvnTeamLetter(final LivingEntity entity) {
        String team = TeamsModule.getTeam(entity);
        if ((team == null || team.isBlank()) && entity.getScoreboardTeam() != null) {
            team = entity.getScoreboardTeam().getName();
        }
        return team == null || team.isBlank()
                ? "?"
                : team.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private int getRvnTeamColor(final LivingEntity entity) {
        return 0xFF000000 | (entity.getTeamColorValue() & 0x00FFFFFF);
    }

    private String getRvnDirection(final LivingEntity entity) {
        final int direction = Math.floorMod(Math.round(MathHelper.wrapDegrees(entity.getYaw()) / 90F), 4);
        return switch (direction) {
            case 0 -> "S";
            case 1 -> "W";
            case 2 -> "N";
            default -> "E";
        };
    }

    private int getRvnHealthColor(final float health) {
        if (health <= 8) {
            return 0xFFE53232;
        }
        if (health <= 16) {
            return 0xFFFFD447;
        }
        return 0xFF35DD5B;
    }

    private void renderGayHead(
            final Target target,
            final float x,
            final float y,
            final float size,
            final int skinTextureGlId
    ) {
        final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
        this.renderGaySkinLayer(skinTextureHandle, x, y, size, 8, 8);
        this.renderGaySkinLayer(skinTextureHandle, x, y, size, 40, 8);
    }

    private void renderGaySkinLayer(
            final int skinTextureHandle,
            final float x,
            final float y,
            final float size,
            final int sourceX,
            final int sourceY
    ) {
        final float skinScale = size / 8.F;
        nvgBeginPath(VG);
        nvgImagePattern(VG, x - sourceX * skinScale, y - sourceY * skinScale,
                64 * skinScale, 64 * skinScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgRoundedRect(VG, x, y, size, size, 4);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    private void drawCompactText(final String text, final float x, final float y, final float size, final int color, final boolean shadow) {
        final NVGTextRenderer font = size == 8 ? BOLD_FONT : MEDIUM_FONT;
        if (shadow) {
            font.drawStringWithShadow(text, x, y, size, color);
        } else {
            font.drawString(text, x, y, size, color);
        }
    }

    private String getCompactHealth(final LivingEntity entity) {
        final float absorption = entity.getAbsorptionAmount();
        return absorption > 0.05F
                ? String.format(Locale.ROOT, "Health: %.1f+%.1f", (double) entity.getHealth(), (double) absorption)
                : String.format(Locale.ROOT, "Health: %.1f", (double) entity.getHealth());
    }

    private String getCompactDistance(final LivingEntity entity) {
        return String.format(Locale.ROOT, "Distance: %.1f", (double) mc.player.distanceTo(entity));
    }

    private void renderCompactHead(final Target target, final float x, final float y, final float size, final int skinTextureGlId) {
        final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
        final float skinScale = size / 8.0F;
        nvgBeginPath(VG);
        nvgImagePattern(VG, x - 8 * skinScale, y - 8 * skinScale,
                64 * skinScale, 64 * skinScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgRoundedRect(VG, x, y, size, size, 2.5F);
        nvgFill(VG);
        nvgClosePath(VG);
    }


    @Override
    public boolean isActive() {
        return this.settings.isEnabled();
    }

    @Override
    public void onDisable() {
        this.updateIslandTrigger(null);
    }

    public void refreshIslandTrigger() {
        if (!this.settings.isEnabled() || !this.settings.isDynamicIsland()) {
            this.updateIslandTrigger(null);
        }
    }

    private void updateIslandTrigger(final Target target) {
        final boolean shouldShow = this.settings.isEnabled() && this.settings.isDynamicIsland() && target != null;
        this.islandTarget = shouldShow ? target : null;

        if (shouldShow && !this.islandTriggerActive) {
            DynamicIslandElement.addTrigger(this);
            this.islandTriggerActive = true;
        } else if (!shouldShow && this.islandTriggerActive) {
            DynamicIslandElement.removeTrigger(this);
            this.islandTriggerActive = false;
        }
    }

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        final Target target = this.islandTarget;
        if (target == null) {
            return;
        }

        final LivingEntity entity = target.entity;
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-semibold");
        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();

        final float headSize = 17;
        final float headX = posX + 6;
        final float headY = posY + (height - headSize) / 2.0F;
        this.renderIslandHead(target, headX, headY, headSize, theme);

        final float contentX = headX + headSize + 6;
        final float contentWidth = Math.max(30, width - (contentX - posX) - 8);
        final String name = this.ellipsize(titleFont, target.getFormattedName(), contentWidth, 7.5F);
        titleFont.drawString(name, contentX, posY + 11.5F, 7.5F, 0xFFFFFFFF);

        final float absorption = entity.getAbsorptionAmount();
        final float health = Math.max(0, entity.getHealth() + absorption);
        final float maximumHealth = Math.max(1, entity.getMaxHealth() + absorption);
        final float healthPercent = MathHelper.clamp(health / maximumHealth, 0, 1);
        final float displayHealthPercent = MathHelper.clamp(this.healthAnimation.getValue(), 0, 1);
        final float barY = posY + height - 8.5F;

        NVGRenderer.roundedRect(contentX, barY, contentWidth, 2.5F, 1.25F, 0x334F4F4F);
        if (displayHealthPercent > 0.01F) {
            NVGRenderer.roundedRectGradient(contentX, barY, contentWidth * displayHealthPercent, 2.5F, 1.25F, theme.first, theme.second, 0);
        }

        if (healthPercent > 0.01F && displayHealthPercent < healthPercent) {
            NVGRenderer.roundedRectGradient(contentX, barY, contentWidth * healthPercent, 2.5F, 1.25F,
                    ColorUtility.applyOpacity(theme.first, 100), ColorUtility.applyOpacity(theme.second, 100), 0);
        }
    }

    @Override
    public float getIslandWidth() {
        final Target target = this.islandTarget;
        if (target == null) {
            return 112;
        }

        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-semibold");
        return Math.clamp(45 + titleFont.getStringWidth(target.getFormattedName(), 7.5F), 112, 180);
    }

    @Override
    public float getIslandHeight() {
        return 29;
    }

    @Override
    public int getIslandPriority() {
        return 10;
    }

    private void renderIslandHead(final Target target, final float x, final float y, final float size, final Pair<Integer, Integer> theme) {
        final int skinTextureGlId = this.getSkinTextureGlId(target.entity);
        if (skinTextureGlId == -1) {
            NVGRenderer.roundedRectGradient(x, y, size, size, size / 2.0F, theme.first, theme.second, 45);
            return;
        }

        final int skinTextureHandle = target.getSkinTextureHandle(skinTextureGlId);
        this.renderIslandSkinLayer(skinTextureHandle, x, y, size, 8, 8);
        this.renderIslandSkinLayer(skinTextureHandle, x, y, size, 40, 8);
    }

    private void renderIslandSkinLayer(
            final int skinTextureHandle,
            final float x,
            final float y,
            final float size,
            final int sourceX,
            final int sourceY
    ) {
        final float skinScale = size / 8.0F;
        nvgBeginPath(VG);
        nvgImagePattern(VG, x - sourceX * skinScale, y - sourceY * skinScale,
                64 * skinScale, 64 * skinScale, 0, skinTextureHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgRoundedRect(VG, x, y, size, size, 3);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    private String ellipsize(final NVGTextRenderer font, final String text, final float width, final float size) {
        if (font.getStringWidth(text, size) <= width) {
            return text;
        }

        final String suffix = "...";
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth(suffix, size)), size) + suffix;
    }

    private Target getTarget() {
        LivingEntity target = LocalDataWatch.get().lastEntityAttack.getRight();
        if (target != null && !LocalDataWatch.getTargetList().hasTarget(target.getId())) {
            target = null;
        }

        if (target == null) {
            final KillAuraModule killAuraModule = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
            if (killAuraModule.isEnabled()) {
                final CurrentTarget killAuraTarget = killAuraModule.getTargeting().getTarget();
                if (killAuraTarget != null) {
                    target = killAuraTarget.getEntity();
                }
            }
        }

        final Target preCurrentTarget = this.currentTarget;
        final Target preLastTarget = this.lastTarget;

        if (target != null) {
            if (this.currentTarget == null || this.currentTarget.entity.getId() != target.getId()) {
                this.currentTarget = new Target(target);
            }
        } else {
            if (mc.currentScreen instanceof ChatScreen) {
                if (this.currentTarget == null || this.currentTarget.entity.getId() != mc.player.getId()) {
                    this.currentTarget = new Target(mc.player);
                }
            } else {
                this.currentTarget = null;
            }
        }

        Target activeTarget = this.currentTarget;
        if (activeTarget == null) {
            if (this.targetAnimation.isFinished()) {
                this.lastTarget = null;
            } else {
                activeTarget = this.lastTarget;
                this.targetAnimation.run(0);
            }
        } else {
            this.targetAnimation.setValue(1);
            this.targetAnimation.reset();
        }

        if (activeTarget != null) {
            activeTarget.updateFormattedName();
        }

        if (preCurrentTarget != null && preCurrentTarget.skinTextureHandle != -1 && this.lastTarget == preCurrentTarget && preCurrentTarget != this.currentTarget && preCurrentTarget != activeTarget) {
            // target switched (no animation)
            nvgDeleteImage(VG, preCurrentTarget.skinTextureHandle);
        } else if (this.currentTarget == null && this.lastTarget != null && this.lastTarget.skinTextureHandle != -1 && this.targetAnimation.getValue() == 0) {
            // target animated out
            nvgDeleteImage(VG, preLastTarget.skinTextureHandle);
            this.lastTarget = null;
        }

        return activeTarget;
    }

    private int getSkinTextureGlId(final LivingEntity entity) {
        final Identifier identifier = switch (entity) {
            case AbstractClientPlayerEntity player -> player.getSkin().body().texturePath();
            case SkeletonEntity ignored -> Identifier.ofVanilla("textures/entity/skeleton/skeleton.png");
            case ZombieEntity ignored -> Identifier.ofVanilla("textures/entity/zombie/zombie.png");
            case CreeperEntity ignored -> Identifier.ofVanilla("textures/entity/creeper/creeper.png");
            case PiglinEntity ignored -> Identifier.ofVanilla("textures/entity/piglin/piglin.png");
            default -> null;
        };
        if (identifier == null) {
            return -1;
        }
        try {
            return Integer.parseInt(mc.getTextureManager().getTexture(identifier).getGlTexture().getLabel());
        } catch (IllegalStateException ignored) {
            // Texture upload can be deferred until the next normal HUD pass.
            return -1;
        }
    }

    private static final class Target {

        private final LivingEntity entity;
        private String formattedName;

        private int skinTextureHandle = -1;

        private Target(final LivingEntity entity) {
            this.entity = entity;
        }

        private String getFormattedName() {
            if (this.formattedName != null) {
                return this.formattedName;
            }
            return this.entity.getName().getString();
        }

        private void updateFormattedName() {
            if (this.entity.getDisplayName() == null) {
                return;
            }

            if (this.formattedName != null
                    && LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                    && this.entity.getDisplayName().getStyle().getColor() == TextColor.fromFormatting(Formatting.GRAY)) {
                return;
            }

            final OrderedTextVisitor visitor = new OrderedTextVisitor();
            this.entity.getDisplayName().asOrderedText().accept(visitor);
            this.formattedName = visitor.getFormattedString();
        }

        private int getSkinTextureHandle(final int skinTextureGlId) {
            if (this.skinTextureHandle != -1) {
                return this.skinTextureHandle;
            }
            return this.skinTextureHandle = nvglCreateImageFromHandle(VG, skinTextureGlId, 64, 64, NVG_IMAGE_NODELETE);
        }

    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
