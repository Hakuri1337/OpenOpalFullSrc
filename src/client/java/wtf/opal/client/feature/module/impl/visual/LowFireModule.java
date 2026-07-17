package wtf.opal.client.feature.module.impl.visual;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;

/** The rendering hook lives in LowFireMixin. */
public final class LowFireModule extends Module {
    public LowFireModule() {
        super("LowFire", "Renders the fire overlay lower on screen.", ModuleCategory.VISUAL);
    }
}
