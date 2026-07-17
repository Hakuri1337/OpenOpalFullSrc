package wtf.opal.client.feature.module.impl.visual;

import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;

/** Naven's AntiBlindness and AntiNausea combined into one visual module. */
public final class NoRenderModule extends Module {

    private final MultipleBooleanProperty effects = new MultipleBooleanProperty("Effects",
            new BooleanProperty("Blindness", true),
            new BooleanProperty("Nausea", true));

    public NoRenderModule() {
        super("NoRender", "Suppresses selected visual status effects.", ModuleCategory.VISUAL);
        this.addProperties(this.effects);
    }

    public static boolean shouldSuppressBlindness() {
        final NoRenderModule module = getModule();
        return module != null && module.isEnabled() && module.effects.getProperty("Blindness").getValue();
    }

    public static boolean shouldSuppressNausea() {
        final NoRenderModule module = getModule();
        return module != null && module.isEnabled() && module.effects.getProperty("Nausea").getValue();
    }

    private static NoRenderModule getModule() {
        final OpalClient client = OpalClient.getInstance();
        return client.getModuleRepository() == null ? null : client.getModuleRepository().getModule(NoRenderModule.class);
    }
}
