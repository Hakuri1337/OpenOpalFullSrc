package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.renderer.component.ToggleSwitchComponent;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.render.ColorUtility;

public final class BooleanPropertyComponent extends PropertyPanel<BooleanProperty> {

    private final ToggleSwitchComponent toggleSwitch;

    public BooleanPropertyComponent(BooleanProperty property) {
        super(property);

        toggleSwitch = new ToggleSwitchComponent(property::toggle, property::getValue);
    }

    @Override
    public void init() {
        toggleSwitch.reset();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        FontRepository.getFont("productsans-medium").drawString(getProperty().getName(), x + 5, y + 10.5F, 7, -1);

        toggleSwitch.setBoxColors(Pair.of(ColorUtility.getClientTheme().first, 0xff3c3c3c));
        toggleSwitch.render(x + 88, y + 3.8F, 0.85F);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            toggleSwitch.mouseClicked(x, y, width, height, mouseX, mouseY);
        }
    }

}
