package wtf.oraculus.client.feature.module.impl.movement.clipper;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.preset.DefaultIsland;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;

public final class ClipperIsland extends DefaultIsland {

    private final ClipperModule clipperModule;

    public ClipperIsland(ClipperModule clipperModule) {
        this.clipperModule = clipperModule;
    }

    private static final NVGTextRenderer FONT = FontRepository.getFont("productsans-medium");

    private String text;
    private float width;

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        super.renderIsland(context, posX, posY, width, height, progress);
        FONT.drawString(this.text, posX + ((width - this.width) * 0.5F), posY + 28 + 2, 7, ColorUtility.applyOpacity(ColorUtility.MUTED_COLOR, progress * 2.0F));
    }

    @Override
    public float getIslandWidth() { // island width is calculated before render, so we can just cache the text
        String text = "You can press ";
        if (this.clipperModule.getUpPos() != null) {
            text += Formatting.WHITE + "UP" + Formatting.RESET;
            if (this.clipperModule.getDownPos() != null) {
                text += " or " + Formatting.WHITE + "DOWN" + Formatting.RESET;
            }
        } else {
            text += Formatting.WHITE + "DOWN" + Formatting.RESET;
        }
        text += " to clip";
        this.text = text;
        return Math.max(super.getIslandWidth(), (this.width = FONT.getStringWidth(text, 7)) + 13 + 4);
    }

    @Override
    public float getIslandHeight() {
        return super.getIslandHeight() + 8;
    }

    @Override
    public int getIslandPriority() {
        return -1;
    }
}
