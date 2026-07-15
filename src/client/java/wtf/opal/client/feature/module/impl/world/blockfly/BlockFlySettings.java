package wtf.opal.client.feature.module.impl.world.blockfly;

import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class BlockFlySettings {
    private final ModeProperty<BlockFlyMode> mode = new ModeProperty<>("Mode", BlockFlyMode.NORMAL);
    private final BooleanProperty eagle = new BooleanProperty("Eagle", true)
            .hideIf(() -> !this.mode.is(BlockFlyMode.NORMAL));
    private final BooleanProperty sneak = new BooleanProperty("Sneak", true);
    private final BooleanProperty snap = new BooleanProperty("Snap", true)
            .hideIf(() -> !this.mode.is(BlockFlyMode.NORMAL));
    private final BooleanProperty renderItemSpoof = new BooleanProperty("Render Item Spoof", true);
    private final NumberProperty rotationTick = new NumberProperty("Rotation Tick", 3.0D, 1.0D, 6.0D, 1.0D);
    private final BooleanProperty clutch = new BooleanProperty("Clutch", true);

    public ModeProperty<BlockFlyMode> modeProperty() {
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

    public BooleanProperty clutchProperty() {
        return this.clutch;
    }

    public BlockFlyMode mode() {
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

    public boolean clutch() {
        return this.clutch.getValue();
    }
}
