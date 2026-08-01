package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.modulelist;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static wtf.oraculus.client.Constants.mc;

public final class ModuleElement implements Comparable<ModuleElement> {

    private Animation xAnimation, yAnimation, heightAnimation;

    private final ToggledSettings settings;
    private final Module module;

    public ModuleElement(ToggledSettings settings, Module module) {
        this.settings = settings;
        this.module = module;
    }

    public void render(
            final DrawContext context,
            final int index,
            final float nextWidth,
            final boolean isBloom
    ) {
        this.xAnimation.run(this.posX);
        this.yAnimation.run(this.posY);
        this.heightAnimation.run(this.module.isEnabled() ? 1 : 0);

        final float scale = this.settings.getScale();
        final int scaledWidth = mc.getWindow().getScaledWidth();
        final float posX = this.xAnimation.getValue() + scaledWidth;
        final float posY = this.yAnimation.getValue();


        final float radius = 1.F;
        final boolean roundList = this.settings.isRoundList();
        final float backgroundX = posX - 6.5F;
        final float backgroundY = posY;
        final float backgroundWidth = this.width + 6.5F;
        final float backgroundHeight = OFFSET;
        final float backgroundRadius = roundList ? 3.F : 0;
        final float radiusBottomLeft = !roundList
                ? 0
                : Float.isNaN(nextWidth)
                ? backgroundRadius
                : Math.min(backgroundRadius, Math.max(0, this.width - nextWidth));

        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();
        final int color = ColorUtility.interpolateColorsBackAndForth(
                6,
                index * 20,
                colors.first, colors.second
        );

        // blur has to stay outside so it renders the right blurred area
        final float scaledBackgroundX = (backgroundX - scaledWidth) * scale + scaledWidth;
        final float scaledBackgroundY = backgroundY * scale;
        final float scaledBackgroundWidth = backgroundWidth * scale;
        final float scaledBackgroundHeight = backgroundHeight * scale;
        if (roundList) {
            NVGRenderer.roundedRectVarying(
                    scaledBackgroundX, scaledBackgroundY, scaledBackgroundWidth, scaledBackgroundHeight,
                    0, 0, 0, radiusBottomLeft * scale,
                    NVGRenderer.BLUR_PAINT
            );
        } else {
            NVGRenderer.rect(
                    scaledBackgroundX, scaledBackgroundY,
                    scaledBackgroundWidth, scaledBackgroundHeight, NVGRenderer.BLUR_PAINT
            );
        }

        NVGRenderer.scale(
                scale,
                scaledWidth,
                0,
                0,
                0,
                () -> {
                    if (this.settings.isBackgroundFade()) {
                        final int backgroundFirstColor = ColorUtility.applyOpacity(this.settings.getBackgroundFirstColor(), 0.5F);
                        final int backgroundSecondColor = ColorUtility.applyOpacity(this.settings.getBackgroundSecondColor(), 0.5F);
                        if (roundList) {
                            NVGRenderer.roundedRectVaryingGradient(
                                    backgroundX, backgroundY, backgroundWidth, backgroundHeight,
                                    0, 0, 0, radiusBottomLeft,
                                    backgroundFirstColor, backgroundSecondColor, 0
                            );
                        } else {
                            NVGRenderer.rectGradient(
                                    backgroundX, backgroundY, backgroundWidth, backgroundHeight,
                                    backgroundFirstColor, backgroundSecondColor, 0
                            );
                        }
                    } else if (roundList) {
                        NVGRenderer.roundedRectVarying(
                                backgroundX, backgroundY, backgroundWidth, backgroundHeight,
                                0, 0, 0, radiusBottomLeft,
                                0x80090909
                        );
                    } else {
                        NVGRenderer.rect(backgroundX, backgroundY, backgroundWidth, backgroundHeight, 0x80090909);
                    }

                    final ToggledSettings.BarMode barMode = settings.getBarMode().getValue();
                    if (barMode != ToggledSettings.BarMode.NONE) {
                        final float xOffset = barMode == ToggledSettings.BarMode.LEFT ? -4.5F : width - 2.5F;

                        NVGRenderer.roundedRect(posX + xOffset + 0.5F, posY + 2.5F, 1.F, 8.F, radius, ColorUtility.getShadowColor(color));
                        NVGRenderer.roundedRect(posX + xOffset, posY + 2.F, 1.F, 8.F, radius, color);
                    }

                    final float textOffset = barMode == ToggledSettings.BarMode.LEFT ? 2 : barMode == ToggledSettings.BarMode.NONE ? 3.5F : barMode == ToggledSettings.BarMode.RIGHT ? 4.25F : 0;
                    if (!this.settings.isVanillaFont()) {
                        FONT.drawStringWithShadow(this.text, posX - textOffset, posY + 9.F, 8.F, color);
                    }
                }
        );

        if (this.settings.isVanillaFont() && !isBloom) {
            final String renderText = this.text;
            final float textOffset = this.getTextOffset();
            final float textX = posX - textOffset;
            final float textY = posY + (OFFSET - mc.textRenderer.fontHeight) / 2.F;
            MinecraftRenderer.addToQueue(() -> {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(scaledWidth, 0);
                context.getMatrices().scale(scale, scale);
                context.getMatrices().translate(-scaledWidth + textX, textY);
                context.drawTextWithShadow(mc.textRenderer, renderText, 0, 0, color);
                context.getMatrices().popMatrix();
            });
        }
    }

    private static final NVGTextRenderer FONT = FontRepository.getFont("productsans-medium");
    public static final float OFFSET = 12.F;

    private String text;
    private float width;
    private float posX, posY;
    private boolean visible, disabled;

    public void tick(int index, boolean visible) {
        this.updateText();
        this.updateVisibility();
        this.updatePosition(index, visible);
    }

    private void updateText() {
        final String name = this.module.getName();
        final String suffix = this.module.getSuffix();
        if (suffix == null || !this.settings.isShowSuffix()) {
            this.text = name;
        } else {
            this.text = name + " " + Formatting.GRAY + suffix; // TODO color suffix gray
        }
        if (this.settings.isLowercase()) {
            this.text = this.text.toLowerCase();
        }
        this.width = this.settings.isVanillaFont()
                ? mc.textRenderer.getWidth(this.text)
                : FONT.getStringWidth(this.text, 8.F);
    }

    private float getTextOffset() {
        return switch (this.settings.getBarMode().getValue()) {
            case LEFT -> 2.F;
            case NONE -> 3.5F;
            case RIGHT -> 4.25F;
        };
    }

    private void updateVisibility() {
        if (!this.isModuleVisible()) {
            if (this.visible) {
                if (this.xAnimation != null && this.xAnimation.isFinished() && this.disabled) {
                    this.xAnimation = null;
                    this.yAnimation = null;
                    this.heightAnimation = null;
                    this.visible = false;
                    return;
                }
                this.disabled = true;
            }
        } else {
            this.disabled = false;
        }
    }

    private void updatePosition(int index, boolean visible) {
        if (this.disabled) {
            this.posX = 8.F;
        } else {
            this.posX = -this.width;
        }

        if (visible) {
            this.visible = true;

            this.posY = index * OFFSET;

            if (this.xAnimation == null) {
                this.xAnimation = new Animation(Easing.EASE_OUT_EXPO, 400);
                this.xAnimation.setValue(8.F);
            }
            if (this.yAnimation == null) {
                this.yAnimation = new Animation(Easing.EASE_OUT_EXPO, 600);
                this.yAnimation.setValue(this.posY);
            }
            if (this.heightAnimation == null) {
                this.heightAnimation = new Animation(Easing.EASE_IN_OUT_CUBIC, 200);
            }
        }
    }

    public boolean isModuleVisible() {
        return this.module.isVisible()
                && this.module.isEnabled()
                && (!this.settings.isNoRenderModule() || this.module.getCategory() != ModuleCategory.VISUAL)
                && this.settings.getVisibleCategories().getProperty(this.module.getCategory().getName()).getValue();
    }

    public boolean isVisible() {
        return this.visible;
    }

    public Animation getHeightAnimation() {
        return heightAnimation;
    }

    public float getWidth() {
        return this.width;
    }

    @Override
    public int compareTo(@NotNull ModuleElement o) {
        return Float.compare(o.width, this.width);
    }

    public Module getModule() {
        return this.module;
    }

}
