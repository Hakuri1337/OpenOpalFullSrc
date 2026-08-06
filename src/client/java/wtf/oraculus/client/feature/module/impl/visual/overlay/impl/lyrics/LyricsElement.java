package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.lyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Colors;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.music.MusicService;
import wtf.oraculus.client.music.model.LyricTimeline;
import wtf.oraculus.client.music.playback.PlaybackSnapshot;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.image.NVGImageRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.repository.ImageRepository;
import wtf.oraculus.client.renderer.shader.LiquidGlassV2Renderer;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;

import java.nio.file.Path;
import java.util.Locale;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static wtf.oraculus.client.Constants.mc;

public final class LyricsElement implements IOverlayElement {

    private static final NVGTextRenderer TITLE_FONT = FontRepository.getFont("productsans-semibold");
    private static final NVGTextRenderer TEXT_FONT = FontRepository.getFont("productsans-regular");

    private static final float WIDTH = 320;
    private static final float HEIGHT = 112;
    private static final float CORNER_RADIUS = 5;
    private static final float PADDING = 7;
    private static final float COVER_SIZE = 18;
    private static final float COVER_RADIUS = 4;
    private static final float METADATA_GAP = 5;
    private static final float TITLE_SIZE = 8.5F;
    private static final float TIME_SIZE = 6.2F;
    private static final float CONTEXT_SIZE = 9;
    private static final float CURRENT_SIZE = 13;
    private static final int MUTED_TEXT = 0xFF999999;

    private final LyricsSettings settings;

    public LyricsElement(final OverlayModule module) {
        this.settings = new LyricsSettings(module);
    }

    public LyricsSettings getSettings() {
        return this.settings;
    }

    @Override
    public void render(final DrawContext context, final float delta, final boolean isBloom) {
        final MusicService service = OraculusClient.getInstance().getMusicService();
        final PlaybackSnapshot snapshot = service.getSnapshot();
        if (snapshot.song() == null) {
            return;
        }

        final float scale = this.settings.getScale();
        final ScreenPositionProperty screenPosition = this.settings.getScreenPosition();
        screenPosition.setWidth(WIDTH * scale);
        screenPosition.setHeight(HEIGHT * scale);
        final float x = screenPosition.getScaledX();
        final float y = screenPosition.getScaledY();

        final boolean liquidGlass = this.settings.isLiquidGlassV2();
        if (isBloom) {
            if (!liquidGlass) {
                NVGRenderer.scale(scale, x, y, 0, 0, () -> NVGRenderer.roundedRect(
                        x, y, WIDTH, HEIGHT, CORNER_RADIUS,
                        ColorUtility.applyOpacity(Colors.BLACK, 0.75F)
                ));
            }
            return;
        }

        final boolean liquidGlassRendered = liquidGlass && LiquidGlassV2Renderer.draw(
                x, y, WIDTH * scale, HEIGHT * scale, CORNER_RADIUS * scale,
                this.settings.getLiquidGlassV2Settings()
        );
        final LyricTimeline.Context lyrics = service.getLyricContext(snapshot.positionMillis());
        final NVGImageRenderer artwork = this.getArtwork(service.getCurrentArtworkPath());

        NVGRenderer.scale(scale, x, y, 0, 0, () -> {
            if (!liquidGlass || !liquidGlassRendered) {
                NVGRenderer.roundedRect(x, y, WIDTH, HEIGHT, CORNER_RADIUS, NVGRenderer.BLUR_PAINT);
                NVGRenderer.roundedRect(
                        x, y, WIDTH, HEIGHT, CORNER_RADIUS,
                        ColorUtility.applyOpacity(0xFF090909, this.settings.getBackgroundOpacity())
                );
            }

            final float metadataX;
            if (artwork != null) {
                artwork.drawRoundedImage(x + PADDING, y + PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS);
                metadataX = x + PADDING + COVER_SIZE + METADATA_GAP;
            } else {
                metadataX = x + PADDING;
            }

            final float metadataWidth = WIDTH - (metadataX - x) - PADDING;
            TITLE_FONT.drawString(
                    ellipsize(TITLE_FONT, snapshot.song().name(), metadataWidth, TITLE_SIZE),
                    metadataX, y + 13, TITLE_SIZE, Colors.WHITE
            );
            TEXT_FONT.drawString(
                    formatTime(snapshot.positionMillis()) + "/" + formatTime(snapshot.durationMillis()),
                    metadataX, y + 22, TIME_SIZE, 0xFFD0D0D0
            );

            final float lyricWidth = WIDTH - PADDING * 3;
            drawCentered(lyricText(lyrics.previous()), x, y + 39, lyricWidth, CONTEXT_SIZE, MUTED_TEXT);
            drawCentered(
                    lyrics.current() == null ? "..." : lyricText(lyrics.current()),
                    x, y + 66, lyricWidth, CURRENT_SIZE, Colors.WHITE
            );
            drawCentered(lyricText(lyrics.next()), x, y + 92, lyricWidth, CONTEXT_SIZE, MUTED_TEXT);
        });
    }

    private static NVGImageRenderer getArtwork(final Path artworkPath) {
        return artworkPath == null ? null : ImageRepository.getImage(artworkPath, 0);
    }

    private static String lyricText(final LyricTimeline.Line line) {
        return line == null || line.text().isBlank() ? "" : line.text();
    }

    private static void drawCentered(
            final String text,
            final float x,
            final float y,
            final float maximumWidth,
            final float size,
            final int color
    ) {
        if (text.isEmpty()) {
            return;
        }
        TEXT_FONT.drawString(
                ellipsize(TEXT_FONT, text, maximumWidth, size),
                x + WIDTH / 2, y, size, color, false,
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE
        );
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
        return font.trimStringToWidth(
                text, Math.max(0, width - font.getStringWidth(suffix, size)), size
        ) + suffix;
    }

    private static String formatTime(final long millis) {
        final long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean isActive() {
        return this.settings.isEnabled()
                && !mc.getDebugHud().shouldShowDebugHud()
                && OraculusClient.getInstance().getMusicService().getSnapshot().song() != null;
    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
