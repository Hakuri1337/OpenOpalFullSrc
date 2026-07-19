package wtf.oraculus.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import wtf.oraculus.client.binding.repository.BindRepository;
import wtf.oraculus.client.edition.EditionModuleCatalog;
import wtf.oraculus.client.command.impl.config.ConfigCommand;
import wtf.oraculus.client.command.impl.misc.DashboardCommand;
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
import wtf.oraculus.client.feature.module.impl.world.blockfly.BlockFlyModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.client.notification.NotificationManager;
import wtf.oraculus.client.music.MusicPlayerModule;
import wtf.oraculus.client.music.MusicService;
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

    private boolean postInitialization;

    private OraculusClient() {
        this.notificationManager = new NotificationManager();
        this.bindRepository = new BindRepository();
    }

    public void runPostInitializations() {
        this.runHelperInitializations();
//        this.registerFabricEvents();

        if (this.musicService == null) {
            this.musicService = new MusicService();
        }

        if (this.moduleRepository == null) {
            this.moduleRepository = ModuleRepository.fromModules(EditionModuleCatalog.createModules());
        }

        SaveUtility.loadBindings();
        SaveUtility.loadConfigFile("default");
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
                            new FriendCommand(),
                            new ScriptCommand()
                    ).build();
        }

        if (this.scriptRepository == null) {
            this.scriptRepository = new ScriptRepository();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown));

        this.postInitialization = true;
        EventDispatcher.dispatch(new PostClientInitializationEvent());

        PayloadTypeRegistry.playS2C().register(PhysicsModule.ResyncPhysicsPayload.ID, PhysicsModule.ResyncPhysicsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MinibloxDisabler.MovePayload.ID, MinibloxDisabler.MovePayload.CODEC);
    }


    private void runHelperInitializations() {
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
        MiniBloxHelperService.getInstance().close();
        this.moduleRepository.getModule(ClickGUIModule.class).setEnabled(false);
        this.moduleRepository.getModule(MusicPlayerModule.class).setEnabled(false);

        if (this.musicService != null) {
            this.musicService.close();
        }

        SaveUtility.saveConfig("default");
        SaveUtility.saveBindings();
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
