package wtf.oraculus.client.feature.module.impl.combat;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class CrystalAuraSettings {
    private final NumberProperty range;
    private final NumberProperty rotationSpeed;

    public CrystalAuraSettings(Module module) {
        this.range = new NumberProperty("Range", 4.0, 1.0, 6.0, 0.1);
        this.rotationSpeed = new NumberProperty("Rotation Speed", 180.0, 1.0, 180.0, 1.0);
        module.addProperties(range, rotationSpeed);
    }

    public NumberProperty getRange() {
        return range;
    }

    public NumberProperty getRotationSpeed() {
        return rotationSpeed;
    }
}
