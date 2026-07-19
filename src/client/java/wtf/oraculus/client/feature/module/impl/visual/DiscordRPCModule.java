package wtf.oraculus.client.feature.module.impl.visual;//package wtf.oraculus.client.feature.module.impl.visual;
//
//import com.mojang.logging.LogUtils;
//import de.jcm.discordgamesdk.Core;
//import de.jcm.discordgamesdk.CreateParams;
//import de.jcm.discordgamesdk.LogLevel;
//import de.jcm.discordgamesdk.activity.Activity;
//import net.minecraft.client.network.ServerInfo;
//import org.slf4j.Logger;
//import wtf.oraculus.client.OraculusClient;
//import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
//import wtf.oraculus.client.feature.helper.impl.server.KnownServer;
//import wtf.oraculus.client.feature.module.Module;
//import wtf.oraculus.client.feature.module.ModuleCategory;
//import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
//import wtf.oraculus.client.notification.NotificationType;
//
//import java.time.Instant;
//
//import static wtf.oraculus.client.Constants.mc;
//
//public final class DiscordRPCModule extends Module {
//
//    private final BooleanProperty hideServer = new BooleanProperty("Hide server", false);
//
//    private static final long DISCORD_CLIENT_ID = 1224889209261920316L;
//    private static final Logger LOGGER = LogUtils.getLogger();
//
//    private volatile boolean running;
//
//    public DiscordRPCModule() {
//        super("Discord RPC", "Displays rich presence information in Discord.", ModuleCategory.VISUAL);
//        setEnabled(true);
//        addProperties(this.hideServer);
//    }
//
//    private void setupActivity() {
//        if (this.running) {
//            return;
//        }
//
//        try {
//            Core.initDownload();
//
//            this.running = true;
//
//            final CreateParams params = new CreateParams();
//            params.setClientID(DISCORD_CLIENT_ID);
//            params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD);
//
//            final Core core = new Core(params);
//            core.setLogHook(
//                    LogLevel.INFO,
//                    (level, message) -> {
//                        switch (level) {
//                            case ERROR -> LOGGER.error(message);
//                            case INFO -> LOGGER.info(message);
//                            case WARN -> LOGGER.warn(message);
//                        }
//                    }
//            );
//
//            try (final Activity activity = new Activity()) {
//                // Setting a start time causes an "elapsed" field to appear
//                activity.timestamps().setStart(Instant.now());
//
//                // Set image stuff
//                activity.assets().setLargeImage("oraculus-v2");
//                activity.assets().setLargeText("Oraculus");
//
//                // Update current activity
//                try {
//                    core.activityManager().updateActivity(activity);
//                } catch (Exception e) {
//                    forceDisable(e);
//                    return;
//                }
//
//                // Run callbacks forever
//                new Thread("Discord RPC") {
//                    @Override
//                    @SuppressWarnings("BusyWait")
//                    public void run() {
//                        while (running) {
//                            try {
//                                if (hideServer.getValue()) {
//                                    activity.setState("Activity hidden");
//                                } else {
//                                    final ServerInfo serverInfo = mc.getCurrentServerEntry();
//
//                                    if (serverInfo != null) {
//                                        final KnownServer currentKnownServer = LocalDataWatch.get().getKnownServerManager().getCurrentServer();
//
//                                        final String serverAddress = currentKnownServer != null && currentKnownServer.getProxyServer() != null
//                                                ? currentKnownServer.getProxyServer().getName()
//                                                : serverInfo.address.toLowerCase();
//
//                                        activity.setState("Playing on " + serverAddress);
//                                    } else if (mc.isInSingleplayer()) {
//                                        activity.setState("In singleplayer");
//                                    } else {
//                                        activity.setState("Currently idle");
//                                    }
//                                }
//
//                                core.activityManager().updateActivity(activity);
//                                core.runCallbacks();
//
//                                sleep(100L);
//                            } catch (Exception e) {
//                                forceDisable(e);
//                                break;
//                            }
//                        }
//
//                        core.close();
//                    }
//                }.start();
//            }
//        } catch (Exception e) {
//            this.forceDisable(e);
//        }
//    }
//
//    private void forceDisable(final Exception e) {
//        LOGGER.error("Discord RPC error", e);
//        this.setEnabled(false);
//
//        OraculusClient.getInstance().getNotificationManager()
//                .builder(NotificationType.ERROR)
//                .title(getName())
//                .description("Disabled due to an unexpected error.")
//                .buildAndPublish();
//    }
//
//    @Override
//    protected void onEnable() {
//        super.onEnable();
//        this.setupActivity();
//    }
//
//    @Override
//    protected void onDisable() {
//        this.running = false;
//        super.onDisable();
//    }
//
//}
