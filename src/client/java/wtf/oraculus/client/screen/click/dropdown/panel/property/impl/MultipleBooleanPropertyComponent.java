package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Colors;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;

public final class MultipleBooleanPropertyComponent extends PropertyPanel<MultipleBooleanProperty> {

    public MultipleBooleanPropertyComponent(final MultipleBooleanProperty property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        final MultipleBooleanProperty property = getProperty();
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        font.drawString(property.getName(), x + 5, y + 8.5F, 7, -1);

        float addedHeight = processText(x, y, width, false);

        final float boxX = x + 5;
        final float boxY = y + 13;
        final float boxWidth = width - 10;
        final float boxHeight = 10 + addedHeight;
        final float radius = 2.5F;

        NVGRenderer.roundedRectOutline(boxX, boxY, boxWidth, boxHeight, radius, 1.5F, 0xff505050);
        NVGRenderer.roundedRect(boxX, boxY, boxWidth, boxHeight, radius, 0xff191919);

        setHeight(DEFAULT_HEIGHT + boxHeight);

        processText(x, y, width, true);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        float addedHeight = 0;
        float currentLineLength = 2;

        for (final BooleanProperty booleanProperty : getProperty().getValue()) {
            if (booleanProperty.isHidden()) {
                continue;
            }

            final float elementWidth = font.getStringWidth(booleanProperty.getName(), 6) + 8.75F;

            if (currentLineLength + elementWidth > width - 10) {
                addedHeight += 10;
                currentLineLength = 2;
            }

            if (HoverUtility.isHovering(x + 4.5F + currentLineLength, y + 13 + addedHeight + 7 - 5.5F, elementWidth - 4, 8.5F, mouseX, mouseY)) {
                booleanProperty.toggle();
            }

            currentLineLength += elementWidth - 2.5F;
        }
    }

    private float processText(final float x, final float y, final float width, final boolean render) {
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        float addedHeight = 0;
        float currentLineLength = 2;

        for (final BooleanProperty booleanProperty : getProperty().getValue()) {
            if (booleanProperty.isHidden()) {
                continue;
            }

            final float elementWidth = font.getStringWidth(booleanProperty.getName(), 6) + 8.75F;

            if (currentLineLength + elementWidth > width - 10) {
                addedHeight += 10;
                currentLineLength = 2;
            }

            if (render) {
                NVGRenderer.roundedRect(x + 4.5F + currentLineLength, y + 13 + addedHeight + 7 - 5.5F, elementWidth - 4, 8.5F, 2.5F, booleanProperty.getValue() ? ColorUtility.applyOpacity(ColorUtility.getClientTheme().first, 0.4F) : ColorUtility.darker(ColorUtility.MUTED_COLOR, 0.6F));
                font.drawString(booleanProperty.getName(), x + 7 + currentLineLength, y + 13 + addedHeight + 8F, 6, booleanProperty.getValue() ? ColorUtility.applyOpacity(-1, 0.9F) : Colors.LIGHT_GRAY);
            }

            currentLineLength += elementWidth - 2.5F;
        }

        return addedHeight + 1.5F;
    }

}
