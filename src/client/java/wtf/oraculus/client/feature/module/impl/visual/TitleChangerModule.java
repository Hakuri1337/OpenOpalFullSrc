package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.StringProperty;
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class TitleChangerModule extends Module {
    private static final String LEGACY_DEFAULT_TITLE = "OpenOpal";
    private final StringProperty title = new StringProperty("Title", ReleaseInfo.NAME);

    public TitleChangerModule() {
        super("TitleChanger", "Changes the Minecraft window title.", ModuleCategory.VISUAL);
        this.addProperties(title);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.applyTitle();
    }

    @Override
    protected void onEnable() {
        this.applyTitle();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.updateWindowTitle();
        super.onDisable();
    }

    private void applyTitle() {
        final String value = title.getValue() == null ? "" : title.getValue().trim();
        mc.getWindow().setTitle(value.isEmpty() || value.equalsIgnoreCase(LEGACY_DEFAULT_TITLE)
                ? ReleaseInfo.NAME
                : value);
    }
}
