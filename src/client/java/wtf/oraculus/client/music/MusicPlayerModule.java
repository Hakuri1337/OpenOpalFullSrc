package wtf.oraculus.client.music;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.music.screen.MusicScreen;

import static wtf.oraculus.client.Constants.mc;

public final class MusicPlayerModule extends Module {
    private MusicScreen screen;
    private final BooleanProperty islandLyrics = new BooleanProperty("Island Lyrics", true);

    public MusicPlayerModule() {
        super("Music Player", "Opens the built-in NetEase music player.", ModuleCategory.UTILITY);
        this.addProperties(islandLyrics);
    }

    @Override
    protected void onEnable() {
        // Screen widgets belong to one GUI lifetime. Reusing an instance after
        // it has been closed leaves stale child/input state behind on some
        // Minecraft screen transitions.
        this.screen = new MusicScreen(OraculusClient.getInstance().getMusicService());
        mc.setScreen(screen);
    }

    @Override
    protected void onDisable() {
        if (mc.currentScreen == screen) mc.setScreen(null);
        this.screen = null;
    }

    public boolean isIslandLyricsEnabled() {
        return islandLyrics.getValue();
    }
}
