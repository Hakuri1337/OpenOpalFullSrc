package wtf.opal.client.feature.module.impl.visual.overlay.impl.modulelist;

import wtf.opal.client.feature.helper.impl.render.ScaleProperty;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

import java.util.stream.Stream;

public final class ToggledSettings {

    private final ScaleProperty scale;
    private final BooleanProperty enabled;
    private final BooleanProperty lowercase;
    private final BooleanProperty showSuffix;
    private final BooleanProperty noRenderModule;
    private final BooleanProperty offsetScoreboard;
    private final MultipleBooleanProperty visibleCategories;
    private final ModeProperty<BarMode> barMode;

    ToggledSettings(OverlayModule module) {
        this.scale = ScaleProperty.newNVGElement();
        this.barMode = new ModeProperty<>("Bar mode", BarMode.LEFT);

        this.enabled = new BooleanProperty("Enabled", true);
        this.lowercase = new BooleanProperty("Lowercase", true);
        this.showSuffix = new BooleanProperty("Show suffix", true);
        this.noRenderModule = new BooleanProperty("NoRenderModule", false);
        this.offsetScoreboard = new BooleanProperty("Offset scoreboard", true);

        this.visibleCategories = new MultipleBooleanProperty("Visible categories",
                Stream.of(ModuleCategory.VALUES)
                        .map(c -> new BooleanProperty(c.getName(), true))
                        .toArray(BooleanProperty[]::new)
        );

        module.addProperties(
                new GroupProperty(
                        "Toggled modules",
                        this.scale.get(), this.barMode, this.enabled, this.lowercase, this.showSuffix, this.noRenderModule, this.offsetScoreboard, this.visibleCategories
                )
        );
    }

    public float getScale() {
        return this.scale.getScale();
    }

    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    public boolean isLowercase() {
        return this.lowercase.getValue();
    }

    public boolean isShowSuffix() {
        return this.showSuffix.getValue();
    }

    public boolean isNoRenderModule() {
        return this.noRenderModule.getValue();
    }

    public boolean isOffsetScoreboard() {
        return this.offsetScoreboard.getValue();
    }

    public MultipleBooleanProperty getVisibleCategories() {
        return this.visibleCategories;
    }

    public ModeProperty<BarMode> getBarMode() {
        return barMode;
    }

    public enum BarMode {
        LEFT("Left"),
        RIGHT("Right"),
        NONE("None");

        private final String name;

        BarMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
