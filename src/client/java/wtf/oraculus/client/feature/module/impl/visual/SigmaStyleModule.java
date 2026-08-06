package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.utility.render.ClientTheme;

/** Applies the Sigma-inspired visual theme without importing the legacy client UI. */
public final class SigmaStyleModule extends Module {
    private ClientTheme previousTheme;

    public SigmaStyleModule() {
        super("Sigma Style", "Applies the Sigma-inspired ClickGUI and overlay theme.", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        final OverlayModule overlay = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        this.previousTheme = overlay.getThemeMode().getValue();
        overlay.getThemeMode().setValueOrdinal(ClientTheme.SIGMA.ordinal());
    }

    @Override
    protected void onDisable() {
        if (this.previousTheme == null) {
            return;
        }
        final OverlayModule overlay = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        overlay.getThemeMode().setValueOrdinal(this.previousTheme.ordinal());
        this.previousTheme = null;
    }
}
