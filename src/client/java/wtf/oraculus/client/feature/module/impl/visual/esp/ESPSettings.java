package wtf.oraculus.client.feature.module.impl.visual.esp;

import wtf.oraculus.client.feature.helper.impl.target.TargetProperty;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;

public final class ESPSettings {

    private final TargetProperty targetProperty;

    private final BooleanProperty box, boxStroke;
    private final BooleanProperty healthBar, healthBarStroke;

    private final BooleanProperty nameTags, hideTeammates;
    private final MultipleBooleanProperty nameTagElements;
    private final MultipleBooleanProperty nameTagIndicators;

    private final BooleanProperty bloom;

    public ESPSettings(final ESPModule module) {
        this.targetProperty = new TargetProperty(true, true, true, false, false, true);

        this.box = new BooleanProperty("Enabled", true);
        this.boxStroke = new BooleanProperty("Stroke", true).hideIf(() -> !this.box.getValue());

        this.healthBar = new BooleanProperty("Enabled", true);
        this.healthBarStroke = new BooleanProperty("Stroke", true).hideIf(() -> !this.healthBar.getValue());

        this.nameTags = new BooleanProperty("Enabled", true);
        this.hideTeammates = new BooleanProperty("Hide Teammates", false)
                .hideIf(() -> !this.nameTags.getValue());

        this.nameTagElements = new MultipleBooleanProperty("Elements",
                new BooleanProperty("Name", true),
                new BooleanProperty("Health", true),
                new BooleanProperty("Distance", true),
                new BooleanProperty("Equipment", false)
        ).hideIf(() -> !this.nameTags.getValue());

        this.nameTagIndicators = new MultipleBooleanProperty("Indicators",
                new BooleanProperty("Sneaking", true),
                new BooleanProperty("Strength", true),
                new BooleanProperty("Invisible", true),
                new BooleanProperty("Blocking", true)
        ).hideIf(() -> !this.nameTags.getValue());

        this.bloom = new BooleanProperty("Bloom", true);

        module.addProperties(
                new GroupProperty("Box", this.box, this.boxStroke),
                new GroupProperty("Health Bar", this.healthBar, this.healthBarStroke),
                new GroupProperty("Name Tags", this.nameTags, this.hideTeammates, this.nameTagElements, this.nameTagIndicators),
                this.targetProperty.get(),
                this.bloom
        );
    }

    public TargetProperty getTargetProperty() {
        return targetProperty;
    }

    public boolean areNameTagsEnabled() {
        return nameTags.getValue();
    }

    public MultipleBooleanProperty getNameTagElements() {
        return nameTagElements;
    }

    public MultipleBooleanProperty getNameTagIndicators() {
        return nameTagIndicators;
    }

    public boolean shouldHideTeammates() {
        return this.hideTeammates.getValue();
    }

    public boolean getHealthBarStroke() {
        return healthBarStroke.getValue() && getHealthBar();
    }

    public boolean getHealthBar() {
        return healthBar.getValue();
    }

    public boolean getBoxStroke() {
        return boxStroke.getValue() && getBox();
    }

    public boolean getBox() {
        return box.getValue();
    }

    public boolean getBloom() {
        return bloom.getValue();
    }
}
