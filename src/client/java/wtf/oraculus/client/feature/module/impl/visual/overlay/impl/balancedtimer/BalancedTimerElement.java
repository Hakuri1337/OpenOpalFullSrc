package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.balancedtimer;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.utility.BalancedTimerModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static org.lwjgl.nanovg.NanoVG.NVG_CW;
import static org.lwjgl.nanovg.NanoVG.NVG_ROUND;
import static org.lwjgl.nanovg.NanoVG.nvgArc;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgLineCap;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static wtf.oraculus.client.Constants.VG;
import static wtf.oraculus.client.Constants.mc;

public final class BalancedTimerElement implements IOverlayElement {

    private static final NVGTextRenderer FONT = FontRepository.getFont("productsans-medium");
    private static final float TEXT_SIZE = 6.0F;
    private static final float TEXT_LINE_HEIGHT = 9.0F;
    private static final float RING_RADIUS = 28.0F;
    private static final float RING_THICKNESS = 1.0F;
    private static final float RING_GLOW = 5.0F;
    private static final float ANIMATION_MAX = 116.0F;

    private final Animation animation = new Animation(Easing.EASE_OUT_QUAD, 400L);
    private BalancedTimerModule module;

    public BalancedTimerElement() {
        this.animation.setValue(0.0F);
    }

    @Override
    public void render(final DrawContext context, final float delta, final boolean isBloom) {
        final BalancedTimerModule balancedTimer = this.getModule();
        if (balancedTimer == null || mc.player == null || mc.world == null) {
            return;
        }

        int displayBalance = Math.min(balancedTimer.getBalance(), 20);
        if (displayBalance >= 19) {
            displayBalance = 20;
        }

        final String text = "Balanced Timer: " + displayBalance * 5 + "%";
        final float textWidth = FONT.getStringWidth(text, TEXT_SIZE);
        final float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        final float adjustedY = mc.getWindow().getScaledHeight() + (float) balancedTimer.getVerticalPosition();

        this.animation.run(displayBalance * 5.8F);
        final float animationValue = this.animation.getValue();
        final float ringCenterY = adjustedY + TEXT_LINE_HEIGHT + 8.0F + RING_RADIUS
                + RING_GLOW * 2.5F + RING_THICKNESS * 0.5F;

        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();
        this.drawGlowArc(centerX, ringCenterY, 0.0F, 360.0F, 0xA0323232);

        final float arcLength = animationValue * 360.0F / ANIMATION_MAX;
        if (arcLength > 0.0F) {
            final int segments = Math.min(24, Math.max(1, (int) (arcLength / 6.0F)));
            final float segmentArc = arcLength / segments;
            for (int index = 0; index < segments; index++) {
                final float progress = segments > 1 ? (float) index / (segments - 1) : 0.0F;
                final int color = ColorUtility.interpolateColors(theme.first, theme.second, progress);
                this.drawGlowArc(centerX, ringCenterY, 270.0F + index * segmentArc, segmentArc, color);
            }
        }

        FONT.drawString(text, centerX - textWidth / 2.0F, adjustedY + 7.0F, TEXT_SIZE, 0xFFC8C8C8);
    }

    private void drawGlowArc(final float centerX, final float centerY, final float startDegrees,
                             final float arcDegrees, final int color) {
        for (int layer = (int) RING_GLOW; layer >= 1; layer--) {
            final int alpha = Math.round(24.0F * (1.0F - layer / (RING_GLOW + 1.0F)));
            this.strokeArc(centerX, centerY, startDegrees, arcDegrees,
                    RING_THICKNESS + layer * 2.0F, ColorUtility.applyOpacity(color, alpha));
        }
        this.strokeArc(centerX, centerY, startDegrees, arcDegrees, RING_THICKNESS, color);
    }

    private void strokeArc(final float centerX, final float centerY, final float startDegrees,
                           final float arcDegrees, final float thickness, final int color) {
        NVGRenderer.applyColor(color, NVGRenderer.NVG_COLOR_1);
        nvgBeginPath(VG);
        nvgLineCap(VG, NVG_ROUND);
        nvgStrokeColor(VG, NVGRenderer.NVG_COLOR_1);
        nvgStrokeWidth(VG, thickness);
        if (arcDegrees >= 359.99F) {
            nvgCircle(VG, centerX, centerY, RING_RADIUS);
        } else {
            final float startRadians = (float) Math.toRadians(startDegrees);
            final float endRadians = (float) Math.toRadians(startDegrees + arcDegrees);
            nvgArc(VG, centerX, centerY, RING_RADIUS, startRadians, endRadians, NVG_CW);
        }
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    private BalancedTimerModule getModule() {
        if (this.module != null) {
            return this.module;
        }
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository != null) {
            this.module = repository.getModule(BalancedTimerModule.class);
        }
        return this.module;
    }

    @Override
    public boolean isActive() {
        final BalancedTimerModule balancedTimer = this.getModule();
        return balancedTimer != null && balancedTimer.isEnabled();
    }

    @Override
    public boolean isBloom() {
        return false;
    }
}
