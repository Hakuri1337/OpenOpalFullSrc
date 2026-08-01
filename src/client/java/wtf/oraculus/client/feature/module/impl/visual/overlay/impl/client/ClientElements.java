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
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.client.auth.AuthBootstrap;
import wtf.oraculus.client.auth.AuthService;
import wtf.oraculus.client.edition.EditionBuildInfo;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.image.NVGImageRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.repository.ImageRepository;
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
    private static final float FOOTER_ROW_HEIGHT = FONT_HEIGHT + 0.5F;
    private static final int BETA_COLOR = 0xFF4AA3FF;
    private static final int FREE_COLOR = 0xFF55D98A;
    private static final int MUTED_COLOR = 0xFFAAAAAA;

    private final ClientElementSettings settings;
    private NVGImageRenderer footerLogo;

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
            final float footerY = scaledHeight - 3;
            final AtomicDouble y = new AtomicDouble(footerY - FOOTER_ROW_HEIGHT);

            this.renderFooter(x, footerY, scale);

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

    private void renderFooter(final float rightX, final float y, final float scale) {
        final boolean freeEdition = EditionBuildInfo.isFree();
        final String edition = EditionBuildInfo.getDisplayName();
        final String separator = " - ";
        final String version = ReleaseInfo.VERSION;
        final String userPrefix = " | User - ";
        final String username = this.getClientUsername();
        final int editionColor = freeEdition ? FREE_COLOR : BETA_COLOR;
        final int usernameColor = freeEdition ? -1 : BETA_COLOR;
        final float logoSize = FONT_SIZE;
        final float logoGap = 2;

        final float editionWidth = REGULAR_FONT.getStringWidth(edition, FONT_SIZE);
        final float separatorWidth = REGULAR_FONT.getStringWidth(separator, FONT_SIZE);
        final float versionWidth = REGULAR_FONT.getStringWidth(version, FONT_SIZE);
        final float userPrefixWidth = REGULAR_FONT.getStringWidth(userPrefix, FONT_SIZE);
        final float usernameWidth = REGULAR_FONT.getStringWidth(username, FONT_SIZE);
        final float totalWidth = logoSize + logoGap + editionWidth + separatorWidth
                + versionWidth + userPrefixWidth + usernameWidth;

        if (this.footerLogo == null) {
            this.footerLogo = ImageRepository.getImage("images/logo_hd.png");
        }

        NVGRenderer.scale(scale, rightX, y, 0, 0, () -> {
            float cursorX = rightX - totalWidth;
            if (this.footerLogo != null) {
                this.footerLogo.drawImage(cursorX, y - logoSize + 1, logoSize, logoSize);
            }
            cursorX += logoSize + logoGap;

            REGULAR_FONT.drawStringWithShadow(edition, cursorX, y, FONT_SIZE, editionColor);
            cursorX += editionWidth;
            REGULAR_FONT.drawStringWithShadow(separator, cursorX, y, FONT_SIZE, MUTED_COLOR);
            cursorX += separatorWidth;
            REGULAR_FONT.drawStringWithShadow(version, cursorX, y, FONT_SIZE, -1);
            cursorX += versionWidth;
            REGULAR_FONT.drawStringWithShadow(userPrefix, cursorX, y, FONT_SIZE, MUTED_COLOR);
            cursorX += userPrefixWidth;
            REGULAR_FONT.drawStringWithShadow(username, cursorX, y, FONT_SIZE, usernameColor);
        });
    }

    private String getClientUsername() {
        final AuthService authService = AuthBootstrap.getService();
        if (authService != null) {
            final String authenticatedUsername = authService.snapshot().username();
            if (authenticatedUsername != null && !authenticatedUsername.isBlank()) {
                return authenticatedUsername;
            }

            final String savedUsername = authService.savedUsername();
            if (savedUsername != null && !savedUsername.isBlank()) {
                return savedUsername;
            }
        }
        return "Unknown";
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
