package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.client;

import com.google.common.util.concurrent.AtomicDouble;
import com.ibm.icu.impl.Pair;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.player.MoveUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.ClientTheme;

import java.util.Locale;

import static wtf.oraculus.client.Constants.mc;

public final class ClientElements implements IOverlayElement {

    private static final NVGTextRenderer BOLD_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer REGULAR_FONT = FontRepository.getFont("productsans-regular");

    private static final float FONT_SIZE = 8.F;
    private static final float FONT_HEIGHT = REGULAR_FONT.getStringHeight("A", FONT_SIZE);

    private final ClientElementSettings settings;

    public ClientElements(final OverlayModule module) {
        this.settings = new ClientElementSettings(module);
    }

    @Override
    public void render(final DrawContext context, final float delta, boolean isBloom) {
        if (mc.player == null) {
            return;
        }

        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();
        final MultipleBooleanProperty options = this.settings.getOptions();
        final float scale = this.settings.getScale();

        final Window window = mc.getWindow();
        final float scaledWidth = window.getScaledWidth();
        final float scaledHeight = window.getScaledHeight();

        if (ColorUtility.getClientTheme().first.equals(ClientTheme.SIGMA.getColors().first)) {
            BOLD_FONT.drawStringWithShadow("SIGMA", 8, 14, 11, colors.first);
            REGULAR_FONT.drawStringWithShadow("Jello Style", 8, 25, 7, 0xff718096);
        }

        // Bottom left
        {
            final float x = 2;

            NVGRenderer.scale(scale, x, scaledHeight - 3, 0, 0, () -> {
                float y = scaledHeight - 3;

                if (options.getProperty("XYZ").getValue()) {
                    final String prefix = convertCase("XYZ ");
                    final float prefixWidth = BOLD_FONT.getStringWidth(prefix, FONT_SIZE);

                    BOLD_FONT.drawGradientStringWithShadow(prefix, x, y, FONT_SIZE, colors.first, colors.second);

                    final Vec3d pos = mc.player.getEntityPos();
                    REGULAR_FONT.drawStringWithShadow(String.format("%.0f %.0f %.0f", pos.x, pos.y, pos.z), prefixWidth + 2, y, FONT_SIZE, -1);

                    y -= FONT_HEIGHT;
                }

                if (options.getProperty("BPS").getValue()) {
                    final String prefix = convertCase("BPS ");
                    final float prefixWidth = BOLD_FONT.getStringWidth(prefix, FONT_SIZE);

                    BOLD_FONT.drawGradientStringWithShadow(prefix, x, y, FONT_SIZE, colors.first, colors.second);
                    REGULAR_FONT.drawStringWithShadow(String.valueOf(MoveUtility.getBlocksPerSecond()), prefixWidth + 2, y, FONT_SIZE, -1);

                    y -= FONT_HEIGHT;
                }

                if (options.getProperty("FPS").getValue()) {
                    final String prefix = convertCase("FPS ");
                    final float prefixWidth = BOLD_FONT.getStringWidth(prefix, FONT_SIZE);

                    BOLD_FONT.drawGradientStringWithShadow(prefix, x, y, FONT_SIZE, colors.first, colors.second);
                    REGULAR_FONT.drawStringWithShadow(String.valueOf(mc.getCurrentFps()), prefixWidth + 2, y, FONT_SIZE, -1);
                }
            });
        }

        // Bottom right
        {
            final float x = scaledWidth - 2;
            final AtomicDouble y = new AtomicDouble(scaledHeight - 3);

            if (options.getProperty("Status effects").getValue()) {
                final int kx = ColorHelper.getWhite(1);

                mc.player.getActiveStatusEffects()
                        .entrySet()
                        .stream()
                        .sorted((a, b) -> Float.compare(
                                -REGULAR_FONT.getStringWidth(getStatusEffectString(a.getValue()), FONT_SIZE),
                                -REGULAR_FONT.getStringWidth(getStatusEffectString(b.getValue()), FONT_SIZE)
                        ))
                        .forEach((entry) -> {
                            final RegistryEntry<StatusEffect> registryEntry = entry.getKey();
                            final StatusEffect effect = registryEntry.value();
                            final StatusEffectInstance instance = entry.getValue();

                            final String text = getStatusEffectString(instance);
                            final int textWidth = (int) REGULAR_FONT.getStringWidth(text, FONT_SIZE);

                            final int effectColor = ColorUtility.applyOpacity(effect.getColor(), 255);
                            final float effectY = (float) y.getAndAdd(-(FONT_HEIGHT + 0.5F));

                            REGULAR_FONT.drawStringWithShadow(text, x - textWidth - 1, effectY, FONT_SIZE, effectColor);

                            MinecraftRenderer.addToQueue(() -> {
                                final Identifier identifier = InGameHud.getEffectTexture(registryEntry);

                                GlStateManager._enableBlend();
                                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, identifier, (int) (x - textWidth - 12), (int) effectY - 7, 9, 9, kx);
                                GlStateManager._disableBlend();
                            });
                        });
            }
        }
    }

    private String getStatusEffectString(final StatusEffectInstance instance) {
        final String duration = instance.isInfinite()
                ? "**:**"
                : formatTicks(instance.getDuration());

        return convertCase(I18n.translate(instance.getTranslationKey()))
                + (instance.getAmplifier() > 0 ? " " + (instance.getAmplifier() + 1) : "")
                + " §7" + duration;
    }

    private String formatTicks(int ticks) {
        int i = MathHelper.floor((float) ticks / 20);
        int j = i / 60;
        i %= 60;
        int k = j / 60;
        j %= 60;
        return k > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", k, j, i)
                : String.format(Locale.ROOT, "%d:%02d", j, i);
    }

    private String convertCase(final String text) {
        return this.settings.isLowercase() ? text.toLowerCase() : text;
    }

    @Override
    public boolean isActive() {
        return !mc.getDebugHud().shouldShowDebugHud();
    }

    @Override
    public boolean isBloom() {
        return false;
    }
}
