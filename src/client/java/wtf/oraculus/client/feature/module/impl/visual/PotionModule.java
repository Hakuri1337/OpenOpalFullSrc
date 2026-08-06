package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.shader.LiquidGlassV2Renderer;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.render.ColorUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.nanovg.NanoVG.NVG_CCW;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgPathWinding;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static wtf.oraculus.client.Constants.VG;
import static wtf.oraculus.client.Constants.mc;

public final class PotionModule extends Module {

    private static final NVGTextRenderer MODERN_NAME_FONT = FontRepository.getFont("productsans-semibold");
    private static final NVGTextRenderer MODERN_SUB_FONT = FontRepository.getFont("productsans-regular");

    private final Map<RegistryEntry<StatusEffect>, Integer> potionMaxDurations = new HashMap<>();
    private List<StatusEffectInstance> currentEffects = new ArrayList<>();

    private final ModeProperty<Side> side = new ModeProperty<>("Mode", Side.RIGHT).id("mode");
    private final ModeProperty<DisplayMode> displayMode = new ModeProperty<>("Display mode", DisplayMode.BAR).id("display-mode");
    private final NumberProperty offsetX = new NumberProperty("Offset X", 2, 0, 255, 1).id("offset-x");
    private final NumberProperty offsetY = new NumberProperty("Offset Y", 2, 0, 255, 1).id("offset-y");
    private final NumberProperty scale = new NumberProperty("Scale", 1, 0.5, 1.5, 0.01).id("scale");
    private final NumberProperty fontScale = new NumberProperty("Font scale", 1, 0.7, 1.5, 0.01).id("font-scale");
    private final BooleanProperty blur = new BooleanProperty("Blur", false).id("blur");
    private final LiquidGlassV2Settings liquidGlassV2 = new LiquidGlassV2Settings(
            "potion", "potion-liquid-glass-v2",
            () -> this.isEnabled() && this.displayMode.is(DisplayMode.MODERN)
    );

    public PotionModule() {
        super("Potion", "Displays active potion effects.", ModuleCategory.VISUAL);
        this.addProperties(this.liquidGlassV2.after(
                this.side, this.displayMode, this.offsetX, this.offsetY, this.scale, this.fontScale, this.blur
        ));
    }

    private String getPotionName(final StatusEffectInstance effect) {
        final StatusEffect potion = effect.getEffectType().value();
        return I18n.translate(potion.getTranslationKey()) + " " + intToRoman(effect.getAmplifier() + 1);
    }

    private static String intToRoman(int number) {
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            while (values[index] <= number) {
                number -= values[index];
                result.append(symbols[index]);
            }
        }
        return result.toString();
    }

    private void updateMaxDurations() {
        final List<RegistryEntry<StatusEffect>> toRemove = new ArrayList<>();
        for (final Map.Entry<RegistryEntry<StatusEffect>, Integer> entry : this.potionMaxDurations.entrySet()) {
            if (mc.player.getStatusEffect(entry.getKey()) == null) {
                toRemove.add(entry.getKey());
            }
        }
        for (final RegistryEntry<StatusEffect> effect : toRemove) {
            this.potionMaxDurations.remove(effect);
        }
        for (final StatusEffectInstance effect : this.currentEffects) {
            final RegistryEntry<StatusEffect> type = effect.getEffectType();
            if (!this.potionMaxDurations.containsKey(type)
                    || this.potionMaxDurations.get(type) < effect.getDuration()) {
                this.potionMaxDurations.put(type, effect.getDuration());
            }
        }
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        if (mc.player == null || mc.world == null || mc.player.getStatusEffects().isEmpty()) {
            return;
        }

        this.currentEffects = mc.player.getStatusEffects().stream()
                .sorted(Comparator.comparingInt(effect -> -(
                        effect.getDuration()
                                + this.potionMaxDurations.getOrDefault(effect.getEffectType(), 0) / 2
                )))
                .collect(Collectors.toList());
        this.updateMaxDurations();

        final float scale = this.scale.getValue().floatValue();
        final float inverseScale = 1.0F / scale;
        final boolean isRight = this.side.is(Side.RIGHT);
        final boolean doBlur = this.blur.getValue();

        if (this.displayMode.is(DisplayMode.MODERN)) {
            this.renderModern(event.drawContext(), 0, doBlur, isRight, scale);
            return;
        }

        nvgSave(VG);
        nvgScale(VG, scale, scale);
        if (this.displayMode.is(DisplayMode.CIRCLE)) {
            this.renderCircle(event.drawContext(), 0, doBlur, inverseScale, isRight);
        } else {
            this.renderBar(event.drawContext(), 0, doBlur, inverseScale, isRight);
        }
        nvgRestore(VG);
    }

    private void renderModern(
            final DrawContext context,
            int index,
            final boolean doBlur,
            final boolean isRight,
            final float scale
    ) {
        final float screenWidth = mc.getWindow().getScaledWidth();
        final float cardWidth = 144.0F * scale;
        final float cardHeight = 36.0F * scale;
        final float gap = 4.0F * scale;
        final float radius = 7.0F * scale;
        final float textScale = this.fontScale.getValue().floatValue() * scale;
        final float subScale = Math.max(0.72F, this.fontScale.getValue().floatValue() * 0.76F) * scale;
        final float iconBox = 25.0F * scale;
        final float iconSize = 17.0F * scale;
        final float offX = (this.offsetX.getValue().floatValue() + 6.0F) * scale;
        final float offY = (this.offsetY.getValue().floatValue() + 6.0F) * scale;
        final float baseX = isRight ? screenWidth - cardWidth - offX : offX;
        final float baseY = offY;
        final float step = cardHeight + gap;
        final boolean liquidGlass = this.liquidGlassV2.isEnabled();

        for (final StatusEffectInstance effect : this.currentEffects) {
            final RegistryEntry<StatusEffect> type = effect.getEffectType();
            final StatusEffect potion = type.value();
            final int maximumDuration = this.potionMaxDurations.getOrDefault(type, Math.max(effect.getDuration(), 1));
            final float ratio = Math.min((float) effect.getDuration() / maximumDuration, 1.0F);
            final int themeColor = 0xFF000000 | potion.getColor();
            final float textX = 38.0F * scale;
            final float nameMaxWidth = cardWidth - textX - 18.0F * scale;
            final String name = ellipsize(MODERN_NAME_FONT, this.getPotionName(effect), nameMaxWidth, textScale);
            final String duration = ellipsize(MODERN_SUB_FONT, this.getDurationString(effect), nameMaxWidth, subScale);
            final float x = baseX;
            final float y = baseY + index * step;

            final boolean liquidGlassRendered = liquidGlass && LiquidGlassV2Renderer.draw(
                    x, y, cardWidth, cardHeight, radius,
                    this.liquidGlassV2
            );
            final boolean renderNormalBackground = !liquidGlass || !liquidGlassRendered;
            if (doBlur && renderNormalBackground) {
                NVGRenderer.roundedRect(x, y, cardWidth, cardHeight, radius, NVGRenderer.BLUR_PAINT);
            }
            if (renderNormalBackground) {
                NVGRenderer.roundedRect(x + 1.0F * scale, y + 2.0F * scale, cardWidth, cardHeight, radius, 0x23000000);
                NVGRenderer.roundedRect(x, y, cardWidth, cardHeight, radius, 0xB80C0E14);
            }

            final int iconBackground = ColorUtility.applyOpacity(themeColor, 0.19F);
            final int accent = ColorUtility.applyOpacity(themeColor, 0.86F);
            NVGRenderer.roundedRect(x + 6.0F * scale, y + 5.5F * scale, iconBox, iconBox, 6.0F * scale, iconBackground);
            NVGRenderer.roundedRect(x + cardWidth - 7.0F * scale, y + 6.0F * scale, 3.0F * scale, cardHeight - 12.0F * scale, 1.5F * scale, 0x1EFFFFFF);
            NVGRenderer.roundedRect(
                    x + cardWidth - 7.0F * scale,
                    y + cardHeight - 6.0F * scale - (cardHeight - 12.0F * scale) * ratio,
                    3.0F * scale,
                    (cardHeight - 12.0F * scale) * ratio,
                    1.5F * scale,
                    accent
            );

            this.queuePotionIcon(context, type, x + 10.0F * scale, y + 9.5F * scale, iconSize);
            MODERN_NAME_FONT.drawString(name, x + textX, y + 10.0F * scale, 9.0F * textScale, 0xF5F5F7FC);
            MODERN_SUB_FONT.drawString(duration, x + textX, y + 24.0F * scale, 9.0F * subScale, 0xDCBCC4D2);
            index++;
        }
    }

    private void queuePotionIcon(
            final DrawContext context,
            final RegistryEntry<StatusEffect> type,
            final float x,
            final float y,
            final float size
    ) {
        final Identifier texture = InGameHud.getEffectTexture(type);
        MinecraftRenderer.addToQueue(() -> {
            context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, (int) size, (int) size, Colors.WHITE
            );
        });
    }

    private static String ellipsize(
            final NVGTextRenderer font,
            final String text,
            final float width,
            final float size
    ) {
        if (font.getStringWidth(text, size) <= width) {
            return text;
        }
        final String suffix = "...";
        final float available = width - font.getStringWidth(suffix, size);
        return available <= 0 ? suffix : font.trimStringToWidth(text, available, size) + suffix;
    }

    private void renderBar(
            final DrawContext context,
            int index,
            final boolean doBlur,
            final float inverseScale,
            final boolean isRight
    ) {
        final float screenWidth = mc.getWindow().getScaledWidth();
        final float cardWidth = 130.0F;
        final float cardHeight = 28.0F;
        final float gap = 2.0F;
        final float textScale = this.fontScale.getValue().floatValue();
        final float textHeight = mc.textRenderer.fontHeight * textScale;
        final float textY1 = 3.0F;
        final float textY2 = textY1 + textHeight + 1.0F;
        final float iconSize = cardHeight - 4.0F;
        final float iconOffset = iconSize + 4.0F;
        final float offX = this.offsetX.getValue().floatValue() + 4.0F;
        final float offY = this.offsetY.getValue().floatValue() + 4.0F;
        final float baseX = isRight ? (screenWidth - cardWidth - offX) * inverseScale : offX * inverseScale;
        final float baseY = offY * inverseScale;
        final float step = (cardHeight + gap) * inverseScale;

        for (final StatusEffectInstance effect : this.currentEffects) {
            final RegistryEntry<StatusEffect> type = effect.getEffectType();
            final StatusEffect potion = type.value();
            final int maximumDuration = this.potionMaxDurations.getOrDefault(type, Math.max(effect.getDuration(), 1));
            final float ratio = Math.min((float) effect.getDuration() / maximumDuration, 1.0F);
            final int themeColor = 0xFF000000 | potion.getColor();
            final String name = this.getPotionName(effect);
            final String duration = this.getDurationString(effect);

            final float x = baseX;
            final float y = baseY + index * step;

            if (doBlur) {
                NVGRenderer.rect(x, y, cardWidth, cardHeight, NVGRenderer.BLUR_PAINT);
            }
            NVGRenderer.rect(x, y, cardWidth, cardHeight, 0x73000000);
            NVGRenderer.rect(x, y, cardWidth * ratio, cardHeight, ColorUtility.applyOpacity(themeColor, 60));
            NVGRenderer.rectOutline(x, y, cardWidth, cardHeight, 1.0F, ColorUtility.applyOpacity(themeColor, 50));

            this.queuePotionContent(context, type, name, duration, themeColor, x, y, iconSize, iconOffset,
                    textY1, textY2, textScale, this.scale.getValue().floatValue(), 2.0F);
            index++;
        }
    }

    private void renderCircle(
            final DrawContext context,
            int index,
            final boolean doBlur,
            final float inverseScale,
            final boolean isRight
    ) {
        final float cardWidth = 110.0F;
        final float cardHeight = 30.0F;
        final float gap = 3.0F;
        final float radius = 3.5F;
        final float textScale = this.fontScale.getValue().floatValue();
        final float textHeight = mc.textRenderer.fontHeight * textScale;
        final float textY1 = 4.0F;
        final float textY2 = textY1 + textHeight + 1.0F;
        final float iconSize = 18.0F;
        final float iconOffset = iconSize + 4.0F;
        final float offX = this.offsetX.getValue().floatValue() + 4.0F;
        final float offY = this.offsetY.getValue().floatValue() + 4.0F;
        final float screenWidth = mc.getWindow().getScaledWidth();
        final float baseX = isRight ? (screenWidth - cardWidth - offX) * inverseScale : offX * inverseScale;
        final float baseY = offY * inverseScale;
        final float step = (cardHeight + gap) * inverseScale;

        for (final StatusEffectInstance effect : this.currentEffects) {
            final RegistryEntry<StatusEffect> type = effect.getEffectType();
            final StatusEffect potion = type.value();
            final int maximumDuration = this.potionMaxDurations.getOrDefault(type, Math.max(effect.getDuration(), 1));
            final float ratio = Math.min((float) effect.getDuration() / maximumDuration, 1.0F);
            final int themeColor = 0xFF000000 | potion.getColor();
            final String name = this.getPotionName(effect);
            final String duration = this.getDurationString(effect);

            final float x = baseX;
            final float y = baseY + index * step;

            if (doBlur) {
                NVGRenderer.roundedRect(x, y, cardWidth, cardHeight, radius, NVGRenderer.BLUR_PAINT);
            }
            NVGRenderer.roundedRect(x, y, cardWidth, cardHeight, radius, 0x73000000);

            final float centerX = x + iconSize / 2.0F + 2.0F;
            final float centerY = y + cardHeight / 2.0F;
            final float ringRadius = Math.min(iconSize / 2.0F + 1.5F, cardHeight / 2.0F - 1.0F);
            this.drawProgressRing(centerX, centerY, ringRadius, 1.5F, ratio, themeColor);

            this.queuePotionContent(context, type, name, duration, themeColor, x, y, iconSize, iconOffset,
                    textY1, textY2, textScale, this.scale.getValue().floatValue(), (cardHeight - iconSize) / 2.0F);
            index++;
        }
    }

    private String getDurationString(final StatusEffectInstance effect) {
        return StatusEffectUtil.getDurationText(effect, 1.0F, mc.world.getTickManager().getTickRate()).getString();
    }

    public boolean isLiquidGlassV2() {
        return this.liquidGlassV2.isEnabled();
    }

    private void queuePotionContent(
            final DrawContext context,
            final RegistryEntry<StatusEffect> type,
            final String name,
            final String duration,
            final int themeColor,
            final float x,
            final float y,
            final float iconSize,
            final float iconOffset,
            final float textY1,
            final float textY2,
            final float textScale,
            final float scale,
            final float iconY
    ) {
        final Identifier texture = InGameHud.getEffectTexture(type);
        MinecraftRenderer.addToQueue(() -> {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);
            context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    (int) (x + 2.0F),
                    (int) (y + iconY),
                    (int) iconSize,
                    (int) iconSize,
                    Colors.WHITE
            );
            context.getMatrices().popMatrix();

            this.drawScaledText(context, name, x + iconOffset, y + textY1, textScale, scale, Colors.WHITE);
            this.drawScaledText(context, duration, x + iconOffset, y + textY2, textScale, scale, themeColor);
        });
    }

    private void drawScaledText(
            final DrawContext context,
            final String text,
            final float x,
            final float y,
            final float textScale,
            final float scale,
            final int color
    ) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(textScale, textScale);
        context.drawText(mc.textRenderer, Text.literal(text), 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    private void drawProgressRing(
            final float centerX,
            final float centerY,
            final float radius,
            final float thickness,
            final float ratio,
            final int color
    ) {
        final float innerRadius = radius - thickness;
        final int segments = 48;

        NVGRenderer.applyColor(0x40000000, NVGRenderer.NVG_COLOR_1);
        nvgBeginPath(VG);
        for (int index = 0; index <= segments; index++) {
            final double angle = Math.PI * 2 * index / segments - Math.PI / 2;
            final float outerX = centerX + (float) Math.cos(angle) * radius;
            final float outerY = centerY + (float) Math.sin(angle) * radius;
            if (index == 0) {
                nvgMoveTo(VG, outerX, outerY);
            } else {
                nvgLineTo(VG, outerX, outerY);
            }
        }
        for (int index = segments; index >= 0; index--) {
            final double angle = Math.PI * 2 * index / segments - Math.PI / 2;
            nvgLineTo(VG,
                    centerX + (float) Math.cos(angle) * innerRadius,
                    centerY + (float) Math.sin(angle) * innerRadius);
        }
        nvgClosePath(VG);
        nvgFillColor(VG, NVGRenderer.NVG_COLOR_1);
        nvgFill(VG);

        int pieDegrees = (int) (ratio * 360);
        if (pieDegrees < 1) {
            pieDegrees = 1;
        }
        NVGRenderer.applyColor(ColorUtility.applyOpacity(color, 0.7F), NVGRenderer.NVG_COLOR_1);
        nvgBeginPath(VG);
        nvgMoveTo(VG, centerX, centerY);
        for (int index = 0; index <= segments; index++) {
            final double angle = Math.toRadians(index * pieDegrees / (double) segments - 90.0);
            nvgLineTo(VG,
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        nvgLineTo(VG, centerX, centerY);
        nvgClosePath(VG);
        nvgFillColor(VG, NVGRenderer.NVG_COLOR_1);
        nvgFill(VG);
    }

    @SuppressWarnings("unused")
    private void drawRing(
            final float centerX,
            final float centerY,
            final float radius,
            final float thickness,
            final int startDegrees,
            final int endDegrees,
            final int segments
    ) {
        final float innerRadius = radius - thickness;
        nvgBeginPath(VG);
        for (int index = 0; index <= segments; index++) {
            final float angle = (float) Math.toRadians(
                    startDegrees + (endDegrees - startDegrees) * index / (float) segments
            );
            final float cosine = (float) Math.cos(angle);
            final float sine = (float) Math.sin(angle);
            if (index == 0) {
                nvgMoveTo(VG, centerX + cosine * radius, centerY + sine * radius);
            } else {
                nvgLineTo(VG, centerX + cosine * radius, centerY + sine * radius);
            }
        }
        for (int index = segments; index >= 0; index--) {
            final float angle = (float) Math.toRadians(
                    startDegrees + (endDegrees - startDegrees) * index / (float) segments
            );
            nvgLineTo(VG, centerX + (float) Math.cos(angle) * innerRadius,
                    centerY + (float) Math.sin(angle) * innerRadius);
        }
        nvgPathWinding(VG, NVG_CCW);
        nvgClosePath(VG);
        nvgFillColor(VG, NVGRenderer.NVG_COLOR_1);
        nvgFill(VG);
    }

    public enum Side {
        RIGHT,
        LEFT
    }

    public enum DisplayMode {
        BAR("Bar"),
        CIRCLE("Circle"),
        MODERN("Modern");

        private final String name;

        DisplayMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
