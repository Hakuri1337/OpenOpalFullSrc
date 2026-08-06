package wtf.oraculus.client.feature.module.impl.world.legittelly.guidance;

import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;

final class LegitTellyGuidanceIsland implements IslandTrigger {
    private static final int MAX_DETAIL_CODEPOINTS = 42;

    private String stage = "READY";
    private String detail = "等待 Legit Telly 引导";
    private int accent = 0xFF4A90E2;

    void update(final String stage, final String detail, final int accent) {
        this.stage = stage == null || stage.isBlank() ? "GUIDE" : stage;
        this.detail = abbreviate(detail);
        this.accent = accent;
    }

    @Override
    public void renderIsland(
            final DrawContext context,
            final float posX,
            final float posY,
            final float width,
            final float height,
            final float progress
    ) {
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer detailFont = FontRepository.getFont("productsans-medium");

        NVGRenderer.roundedRect(
                posX + 6.0F,
                posY + 5.0F,
                14.0F,
                14.0F,
                7.0F,
                ColorUtility.applyOpacity(this.accent, 145)
        );
        NVGRenderer.roundedRect(
                posX + 10.75F,
                posY + 9.75F,
                4.5F,
                4.5F,
                2.25F,
                this.accent
        );
        titleFont.drawString(
                "TELLY · " + this.stage,
                posX + 27.0F,
                posY + 11.5F,
                7.5F,
                this.accent
        );
        detailFont.drawString(
                this.detail,
                posX + 27.0F,
                posY + 19.0F,
                6.0F,
                ColorUtility.MUTED_COLOR
        );
    }

    @Override
    public float getIslandWidth() {
        return 220.0F;
    }

    @Override
    public float getIslandHeight() {
        return 25.0F;
    }

    @Override
    public int getIslandPriority() {
        // Higher than normal progress islands, lower than urgent warnings.
        return 11;
    }

    private static String abbreviate(final String value) {
        if (value == null || value.isBlank()) {
            return "等待下一步";
        }
        final String normalized = value
                .replace("[Legit Telly]", "")
                .replaceAll("\\s+", " ")
                .trim();
        final int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= MAX_DETAIL_CODEPOINTS) {
            return normalized;
        }
        final int end = normalized.offsetByCodePoints(0, MAX_DETAIL_CODEPOINTS - 1);
        return normalized.substring(0, end) + "…";
    }
}
