package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;

/** The rendering hook lives in LowFireMixin. */
public final class LowFireModule extends Module {
    public LowFireModule() {
        super("LowFire", "Renders the fire overlay lower on screen.", ModuleCategory.VISUAL);
    }
}
