package wtf.oraculus.client.feature.module.impl.world.scaffold;

import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class ScaffoldSettings {
    public static final String IMPLEMENTATION_MARKER_ID = "__oraculus_scaffold_blockfly_engine_v1";

    private final BooleanProperty implementationMarker = new BooleanProperty("Scaffold Engine", true)
            .id(IMPLEMENTATION_MARKER_ID)
            .hideIf(() -> true);
    private final ModeProperty<ScaffoldMode> mode = new ModeProperty<>("Mode", ScaffoldMode.NORMAL);
    private final BooleanProperty eagle = new BooleanProperty("Eagle", true)
            .hideIf(() -> !this.mode.is(ScaffoldMode.NORMAL));
    private final BooleanProperty sneak = new BooleanProperty("Sneak", true);
    private final BooleanProperty snap = new BooleanProperty("Snap", true)
            .hideIf(() -> !this.mode.is(ScaffoldMode.NORMAL));
    private final BooleanProperty renderItemSpoof = new BooleanProperty("Render Item Spoof", true);
    private final NumberProperty rotationTick = new NumberProperty("Rotation Tick", 3.0D, 1.0D, 6.0D, 1.0D);

    public BooleanProperty implementationMarkerProperty() {
        return this.implementationMarker;
    }

    public ModeProperty<ScaffoldMode> modeProperty() {
        return this.mode;
    }

    public BooleanProperty eagleProperty() {
        return this.eagle;
    }

    public BooleanProperty sneakProperty() {
        return this.sneak;
    }

    public BooleanProperty snapProperty() {
        return this.snap;
    }

    public BooleanProperty renderItemSpoofProperty() {
        return this.renderItemSpoof;
    }

    public NumberProperty rotationTickProperty() {
        return this.rotationTick;
    }

    public ScaffoldMode mode() {
        return this.mode.getValue();
    }

    public boolean eagle() {
        return this.eagle.getValue();
    }

    public boolean sneak() {
        return this.sneak.getValue();
    }

    public boolean snap() {
        return this.snap.getValue();
    }

    public boolean renderItemSpoof() {
        return this.renderItemSpoof.getValue();
    }

    public int rotationTick() {
        return this.rotationTick.getValue().intValue();
    }

}
