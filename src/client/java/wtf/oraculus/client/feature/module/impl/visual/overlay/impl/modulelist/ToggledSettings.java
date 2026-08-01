package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.modulelist;

import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.ColorProperty;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

import java.util.stream.Stream;

public final class ToggledSettings {

    private final ScaleProperty scale;
    private final BooleanProperty enabled;
    private final BooleanProperty lowercase;
    private final BooleanProperty showSuffix;
    private final BooleanProperty noRenderModule;
    private final BooleanProperty offsetScoreboard;
    private final BooleanProperty backgroundFade;
    private final ColorProperty backgroundFirstColor;
    private final ColorProperty backgroundSecondColor;
    private final BooleanProperty roundList;
    private final BooleanProperty vanillaFont;
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
        this.backgroundFade = new BooleanProperty("BackGround Fade", false);
        this.backgroundFirstColor = new ColorProperty("Background first color", 0xFF090909);
        this.backgroundSecondColor = new ColorProperty("Background second color", 0xFF090909);
        this.roundList = new BooleanProperty("Round List", false);
        this.vanillaFont = new BooleanProperty("Vanilla Font", false);

        this.backgroundFirstColor.hideIf(() -> !this.backgroundFade.getValue());
        this.backgroundSecondColor.hideIf(() -> !this.backgroundFade.getValue());

        this.visibleCategories = new MultipleBooleanProperty("Visible categories",
                Stream.of(ModuleCategory.VALUES)
                        .map(c -> new BooleanProperty(c.getName(), true))
                        .toArray(BooleanProperty[]::new)
        );

        module.addProperties(
                new GroupProperty(
                        "Toggled modules",
                        this.scale.get(), this.barMode, this.enabled, this.lowercase, this.showSuffix,
                        this.backgroundFade, this.backgroundFirstColor, this.backgroundSecondColor,
                        this.roundList, this.vanillaFont,
                        this.noRenderModule, this.offsetScoreboard, this.visibleCategories
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

    public boolean isBackgroundFade() {
        return this.backgroundFade.getValue();
    }

    public int getBackgroundFirstColor() {
        return this.backgroundFirstColor.getValue();
    }

    public int getBackgroundSecondColor() {
        return this.backgroundSecondColor.getValue();
    }

    public boolean isRoundList() {
        return this.roundList.getValue();
    }

    public boolean isVanillaFont() {
        return this.vanillaFont.getValue();
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
