package wtf.oraculus.client.screen.click.dropdown.panel.property;

import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.screen.click.OraculusPanelComponent;
import wtf.oraculus.utility.render.ColorUtility;

public abstract class PropertyPanel<T extends Property<?>> extends OraculusPanelComponent {

    private final T property;

    protected final static int DEFAULT_HEIGHT = 17;

    protected boolean lastProperty;

    public PropertyPanel(final T property) {
        this.property = property;
        setHeight(DEFAULT_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (lastProperty)
            NVGRenderer.roundedRectVarying(x, y, width, height, 0, 0, 5, 5, ColorUtility.applyOpacity(0xff000000, 0.25F));
        else
            NVGRenderer.rect(x, y, width, height, ColorUtility.applyOpacity(0xff000000, 0.25F));
    }

    public T getProperty() {
        return property;
    }

    public boolean isHidden() {
        return property.isHidden();
    }

}
