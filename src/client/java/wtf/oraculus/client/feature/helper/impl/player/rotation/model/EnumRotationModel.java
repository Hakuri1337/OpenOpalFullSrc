package wtf.oraculus.client.feature.helper.impl.player.rotation.model;

import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.HeypixelRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.LinearRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.OrganicRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.SidewaysRotationModel;

import java.util.function.Function;

public enum EnumRotationModel {
    INSTANT("Instant", r -> InstantRotationModel.INSTANCE),
    HEYPIXEL("Heypixel", r -> new HeypixelRotationModel(r.getMaxAngle())),
    LINEAR("Linear", r -> new LinearRotationModel(r.getMaxAngle())),
    ORGANIC("Organic", r -> new OrganicRotationModel(r.getMaxAngle(), r.getDriftIntensity(), r.getJitterIntensity())),
    SIDEWAYS("Sideways", r -> new SidewaysRotationModel(r.getMaxAngle()));

    private final String name;
    private final Function<RotationProperty, IRotationModel> modelSupplier;

    EnumRotationModel(String name, Function<RotationProperty, IRotationModel> modelSupplier) {
        this.name = name;
        this.modelSupplier = modelSupplier;
    }

    @Override
    public String toString() {
        return name;
    }

    public IRotationModel supply(final RotationProperty property) {
        return modelSupplier.apply(property);
    }
}
