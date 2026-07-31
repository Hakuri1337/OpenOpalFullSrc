package wtf.oraculus.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import wtf.oraculus.client.auth.AuthBootstrap;
import wtf.oraculus.client.auth.AuthService;
import wtf.oraculus.client.binding.repository.BindRepository;
import wtf.oraculus.client.edition.EditionModuleCatalog;
import wtf.oraculus.client.edition.EditionHooks;
import wtf.oraculus.client.command.impl.config.ConfigCommand;
import wtf.oraculus.client.command.impl.misc.DashboardCommand;
import wtf.oraculus.client.command.impl.misc.IrcCommand;
import wtf.oraculus.client.command.impl.misc.ScriptCommand;
import wtf.oraculus.client.command.impl.module.BindCommand;
import wtf.oraculus.client.command.impl.module.DeprecatedModulesCommand;
import wtf.oraculus.client.command.impl.module.ToggleCommand;
import wtf.oraculus.client.command.impl.player.FriendCommand;
import wtf.oraculus.client.command.impl.player.UsernameCommand;
import wtf.oraculus.client.command.impl.player.movement.HClipCommand;
import wtf.oraculus.client.command.impl.player.movement.VClipCommand;
import wtf.oraculus.client.command.repository.CommandRepository;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.miniblox.MiniBloxHelperService;
import wtf.oraculus.client.feature.helper.impl.player.hypixel.TransactionStreamValidator;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.helper.impl.render.FadingBlockHelper;
import wtf.oraculus.client.feature.helper.impl.render.ClientUiDefaults;
import wtf.oraculus.client.feature.helper.impl.render.ScreenPositionManager;
import wtf.oraculus.client.feature.module.impl.combat.*;
import wtf.oraculus.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.impl.movement.*;
import wtf.oraculus.client.feature.module.impl.movement.clipper.ClipperModule;
import wtf.oraculus.client.feature.module.impl.movement.flight.FlightModule;
import wtf.oraculus.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.impl.utility.*;
import wtf.oraculus.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.oraculus.client.feature.module.impl.utility.disabler.impl.MinibloxDisabler;
import wtf.oraculus.client.feature.module.impl.utility.inventory.AutoArmorModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.oraculus.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.oraculus.client.feature.module.impl.visual.*;
import wtf.oraculus.client.feature.module.impl.visual.esp.ESPModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.world.FastBreakModule;
import wtf.oraculus.client.feature.module.impl.world.ChestAuraModule;
import wtf.oraculus.client.feature.module.impl.world.TimerModule;
import wtf.oraculus.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.client.notification.NotificationManager;
import wtf.oraculus.client.music.MusicPlayerModule;
import wtf.oraculus.client.music.MusicService;
import wtf.oraculus.client.irc.IrcService;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.client.PostClientInitializationEvent;

import wtf.oraculus.scripting.repository.ScriptRepository;
import wtf.oraculus.utility.data.SaveUtility;

import java.util.ServiceLoader;

public final class OraculusClient {

    private final NotificationManager notificationManager;
    private final BindRepository bindRepository;

    private CommandRepository commandRepository;
    private ModuleRepository moduleRepository;
    private ScriptRepository scriptRepository;
    private MusicService musicService;
    private IrcService ircService;

    private boolean bootstrapInitialization;
    private boolean postInitialization;
    private boolean runtimeInitialized;
    private boolean shutdownHookRegistered;

    private OraculusClient() {
        this.notificationManager = new NotificationManager();
        this.bindRepository = new BindRepository();
    }

    public synchronized void runBootstrapInitializations() {
        if (this.bootstrapInitialization) {
            return;
        }
        this.runHelperInitializations();
        this.bootstrapInitialization = true;
        if (!this.shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown, "Oraculus-Shutdown"));
            this.shutdownHookRegistered = true;
        }
        AuthBootstrap.initialize(this);
    }

    public synchronized void runAuthenticatedInitializations() {
        if (!this.bootstrapInitialization) {
            this.runBootstrapInitializations();
        }
        if (this.runtimeInitialized) {
            SaveUtility.loadConfigFile("default");
            if (this.ircService != null) {
                this.ircService.start();
            }
            this.postInitialization = true;
            return;
        }

        if (this.musicService == null) {
            this.musicService = new MusicService();
        }

        if (this.moduleRepository == null) {
            this.moduleRepository = ModuleRepository.fromModules(EditionModuleCatalog.createModules());
        }

        SaveUtility.loadBindings();
        SaveUtility.loadConfigFile("default");
        EditionHooks.enforceEditionDefaults(this.moduleRepository);
        MiniBloxHelperService.getInstance().recoverInterruptedSession();

        if (this.commandRepository == null) {
            this.commandRepository = CommandRepository.builder()
                    .putAll(
                            new ToggleCommand(),
                            new BindCommand(),
                            new DeprecatedModulesCommand(),
                            new ConfigCommand(),
                            new VClipCommand(),
                            new HClipCommand(),
                            new UsernameCommand(),
                            new DashboardCommand(),
                            new IrcCommand(),
                            new FriendCommand(),
                            new ScriptCommand()
                    ).build();
        }

        if (this.scriptRepository == null) {
            this.scriptRepository = new ScriptRepository();
        }

        this.runtimeInitialized = true;
        this.postInitialization = true;
        if (this.ircService == null) {
            this.ircService = new IrcService(AuthBootstrap.getService());
        }
        this.ircService.start();
        EventDispatcher.dispatch(new PostClientInitializationEvent());

        PayloadTypeRegistry.playS2C().register(PhysicsModule.ResyncPhysicsPayload.ID, PhysicsModule.ResyncPhysicsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MinibloxDisabler.MovePayload.ID, MinibloxDisabler.MovePayload.CODEC);
    }

    @Deprecated
    public void runPostInitializations() {
        this.runAuthenticatedInitializations();
    }

    public synchronized void stopAuthenticatedRuntime() {
        if (this.ircService != null) {
            this.ircService.stop();
        }
        if (this.moduleRepository == null) {
            this.postInitialization = false;
            return;
        }
        try (SaveUtility.AutoSaveScope ignored = SaveUtility.suppressAutoSave()) {
            for (final var module : this.moduleRepository.getModules()) {
                if (module.isEnabled()) {
                    try {
                        module.setEnabled(false);
                    } catch (RuntimeException ignoredFailure) {
                    }
                }
            }
        }
        this.postInitialization = false;
    }


    private void runHelperInitializations() {
        ClientUiDefaults.initialize();
        LocalDataWatch.setInstance();
        MouseHelper.setInstance();
        SwingDelay.setInstance();
        SlotHelper.setInstance();
        TimerHelper.setInstance();
        FadingBlockHelper.setInstance();
        ScreenPositionManager.setInstance();
        TransactionStreamValidator.setInstance();
        MiniBloxHelperService.setInstance();
    }


    private void registerFabricEvents() {
//        ScreenEvents.BEFORE_INIT.register(((client, screen, scaledWidth, scaledHeight) -> {
//            if (screen instanceof TitleScreen) {
//                mc.setScreen(new OraculusTitleScreen());
//            }
//        }));
    }


    private void onShutdown() {
        if (this.bootstrapInitialization) {
            MiniBloxHelperService.getInstance().close();
        }
        final AuthService authService = AuthBootstrap.getService();
        if (authService != null) {
            authService.close();
        }
        if (this.moduleRepository != null) {
            this.moduleRepository.getModule(ClickGUIModule.class).setEnabled(false);
            this.moduleRepository.getModule(MusicPlayerModule.class).setEnabled(false);
        }

        if (this.musicService != null) {
            this.musicService.close();
        }
        if (this.ircService != null) {
            this.ircService.close();
        }

        if (this.runtimeInitialized && this.moduleRepository != null) {
            SaveUtility.saveConfig("default");
            SaveUtility.saveBindings();
        }
    }


    public boolean isPostInitialization() {
        return postInitialization;
    }

    public ModuleRepository getModuleRepository() {
        return moduleRepository;
    }

    public BindRepository getBindRepository() {
        return bindRepository;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ScriptRepository getScriptRepository() {
        return scriptRepository;
    }

    public MusicService getMusicService() {
        return musicService;
    }

    public IrcService getIrcService() {
        return ircService;
    }

    private static OraculusClient instance;

    public static OraculusClient getInstance() {
        if (instance == null) {
            instance = new OraculusClient();
        }
        return instance;
    }

    public static void setInstance() {
        instance = new OraculusClient();
    }

}
