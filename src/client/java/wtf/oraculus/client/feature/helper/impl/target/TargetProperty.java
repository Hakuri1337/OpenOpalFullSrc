package wtf.oraculus.client.feature.helper.impl.target;

import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;

public final class TargetProperty {

    private final MultipleBooleanProperty property;
    private final boolean allowLocalPlayer;

    public TargetProperty(final boolean players, final boolean allowLocalPlayer, final boolean localPlayer, final boolean hostile, final boolean passive, final boolean friendly) {
        this.allowLocalPlayer = allowLocalPlayer;

        final BooleanProperty playersProperty = new BooleanProperty("Players", players);
        final BooleanProperty localPlayerProperty = new BooleanProperty("Local player", localPlayer).hideIf(() -> !playersProperty.getValue() || !allowLocalPlayer);

        this.property = new MultipleBooleanProperty("Targets",
                playersProperty,
                new BooleanProperty("Hostile", hostile),
                new BooleanProperty("Passive", passive),
                new BooleanProperty("Friendly", friendly),
                localPlayerProperty
        );
    }

    public MultipleBooleanProperty get() {
        return this.property;
    }

    public int getTargetFlags() {
        return TargetFlags.get(
                this.property.getProperty("Players").getValue(),
                this.property.getProperty("Hostile").getValue(),
                this.property.getProperty("Passive").getValue(),
                this.property.getProperty("Friendly").getValue()
        );
    }

    public boolean isLocalPlayer() {
        return this.allowLocalPlayer && this.property.getProperty("Local player").getValue();
    }

}
