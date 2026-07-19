package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;

public final class NoFOVModule extends Module {
    public NoFOVModule() {
        super("No FOV", "Locks your FOV and disables FOV changes.", ModuleCategory.VISUAL);
    }
}
