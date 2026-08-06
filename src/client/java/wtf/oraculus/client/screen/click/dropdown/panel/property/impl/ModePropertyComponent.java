package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ClientTheme;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;

public final class ModePropertyComponent extends PropertyPanel<ModeProperty<?>> {

    private final Animation expandAnimation = new Animation(Easing.DECELERATE, 125);
    private boolean expanded;

    public ModePropertyComponent(ModeProperty<?> property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        expandAnimation.run(expanded ? 1 : 0);

        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");
        final NVGTextRenderer fontBold = FontRepository.getFont("productsans-bold");

        font.drawString(getProperty().getName(), x + 5, y + 9.5F, 7, -1);

        final float padding = 2;

        final float rectX = x + 3;
        final float rectY = y + padding + 11.5F;
        final float rectWidth = width - 5 - padding;
        NVGRenderer.roundedRect(rectX, rectY, rectWidth, height - padding - (32 - DEFAULT_HEIGHT), 4, ColorUtility.applyOpacity(0xff000000, 0.25F));
        fontBold.drawString(getProperty().getValue().toString(), rectX + 4, rectY + 10, 7, -1);

        if (getProperty().isTheme()) {
            final ClientTheme selectedTheme = ClientTheme.valueOf(getProperty().getValue().name());

            final Pair<Integer, Integer> colors = selectedTheme.getColors();
            final float valueWidth = fontBold.getStringWidth(getProperty().getValue().toString(), 7);

            NVGRenderer.roundedRect(rectX + valueWidth + 7, rectY + 4F, 7, 7, 2, colors.first);
            NVGRenderer.roundedRect(rectX + valueWidth + 16, rectY + 4F, 7, 7, 2, colors.second);
        }

        final String expandIcon = "\ue5cf";
        final NVGTextRenderer iconFont = FontRepository.getFont("materialicons-regular");
        final float iconSize = 9;
        final float iconWidth = iconFont.getStringWidth(expandIcon, iconSize);
        NVGRenderer.rotate(
                expandAnimation.getValue() * 180,
                rectX + rectWidth - 12,
                rectY + 2.5F,
                iconWidth,
                iconSize,
                () -> iconFont.drawString("\ue5cf", 0, 0, iconSize, -1, false, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE)
        );

        NVGRenderer.scissor(rectX, rectY, rectWidth, height - padding - (32 - DEFAULT_HEIGHT), () -> {
            int addedHeight = 0;
            if (expandAnimation.getValue() > 0) {
                for (final Enum<?> mode : getProperty().getValues()) {
                    if (mode == null || mode == getProperty().getValue()) continue;

                    font.drawString(mode.toString(), rectX + 4, rectY + 9.5F + 13 + addedHeight, 7, -1);

                    if (getProperty().isTheme()) {
                        final ClientTheme selectedTheme = ClientTheme.valueOf(mode.name());

                        final Pair<Integer, Integer> colors = selectedTheme.getColors();

                        NVGRenderer.roundedRect(rectX + width - 5 - padding - 20.5F, rectY + 3.5F + 13 + addedHeight, 7, 7, 2.5F, colors.first);
                        NVGRenderer.roundedRect(rectX + width - 5 - padding - 12, rectY + 3.5F + 13 + addedHeight, 7, 7, 2.5F, colors.second);
                    }

                    addedHeight += 13;
                }
            }

            setHeight(32 + addedHeight * expandAnimation.getValue());
        });
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtility.isHovering(x, y, width, 32, mouseX, mouseY) && button == 1) {
            expanded = !expanded;
            return;
        }

        if (expanded) {
            final float padding = 2;

            final float rectX = x + 3;
            final float rectY = y + padding + 11.5F;
            final float rectWidth = width - 8 - padding - (6 / 2F);

            int addedHeight = 0;
            for (final Enum<?> mode : getProperty().getValues()) {
                if (mode == null || mode.ordinal() == getProperty().getValue().ordinal()) continue;

                if (HoverUtility.isHovering(rectX, rectY + 13 + addedHeight, rectWidth, 13, mouseX, mouseY)) {
                    getProperty().setValueOrdinal(mode.ordinal());
                    expanded = false;
                }

                addedHeight += 13;
            }
        }
    }
}
