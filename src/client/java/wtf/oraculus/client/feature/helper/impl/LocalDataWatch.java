package wtf.oraculus.client.feature.helper.impl;

import net.hypixel.data.type.GameType;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.client.feature.helper.impl.server.KnownServerManager;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.helper.impl.target.TargetList;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.PlayerCreateEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.step.StepSuccessEvent;
import wtf.oraculus.event.impl.game.server.ServerConnectEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.ClientCommonNetworkHandlerAccessor;
import wtf.oraculus.mixin.PlayerInteractEntityC2SPacketAccessor;

import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.misc.time.Scheduler;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.utility.player.PlayerUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;

import static wtf.oraculus.client.Constants.mc;

public final class LocalDataWatch implements IHelper {

    private LocalDataWatch() {
    }

    private final KnownServerManager knownServerManager = new KnownServerManager();

    private TargetList targetList;
    private final List<String> friendList = new CopyOnWriteArrayList<>();
    private final List<String> strengthedPlayerList = new ArrayList<>();

    public int airTicks, groundTicks, ticksSinceStepped, ticksSinceTeleport;
    public final Stopwatch velocityStopwatch = new Stopwatch(0);

    // Expires after 10 ticks
    public final Pair<Integer, LivingEntity> lastEntityAttack = new Pair<>(0, null);

    @Subscribe
    public void onAttack(final AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity livingEntity) {
            lastEntityAttack.setLeft(0);
            lastEntityAttack.setRight(livingEntity);
        }
    }

    @Subscribe
    
    public void onServerConnect(ServerConnectEvent event) {
        RandomUtility.resetJoinRandom();

        this.knownServerManager.identifyServer(event.getServerAddress());

//        if (this.knownServerManager.getCurrentServer() instanceof HypixelServer) {
//            final ProtocolVersion selectedVersion = ViaFabricPlus.getImpl().getTargetVersion();
//            final ProtocolVersion optimalVersion = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion());
//
//            if (selectedVersion != optimalVersion) {
//                mc.setScreen(
//                        new DisconnectedScreen(
//                                new MultiplayerScreen(null),
//                                Text.literal(Formatting.GRAY + "Failed to connect to Hypixel"),
//                                Text.literal(
//                                        "Oraculus's Hypixel bypasses are not made for the Minecraft version \nyou selected in ViaFabricPlus. " +
//                                                "Please select " + Formatting.GREEN + Formatting.BOLD + optimalVersion.getName() + Formatting.RESET
//                                                + " in \nyour ViaFabricPlus settings."
//                                )
//                        )
//                );
//
//                event.setCancelled();
//            }
//        }
    }

    @Subscribe
    
    public void onServerDisconnect(ServerDisconnectEvent event) {
        HypixelServer.ModAPI.get().setCurrentLocation(null);

        this.knownServerManager.resetServer();
        this.targetList = null;

        if (mc.getNetworkHandler() != null) {
            final ClientCommonNetworkHandlerAccessor accessor = (ClientCommonNetworkHandlerAccessor) mc.getNetworkHandler();
            accessor.getServerCookies().clear();
        }
    }

    @Subscribe
    public void onPlayerCreate(PlayerCreateEvent event) {
        this.targetList = new TargetList();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (this.targetList != null) {
            this.targetList.tick();
        }

        ticksSinceStepped++;
        ticksSinceTeleport++;

        if (mc.currentScreen == null && mc.getOverlay() == null && PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_PERIOD)) {
            mc.setScreen(new ChatScreen(".", false));
        }

        lastEntityAttack.setLeft(lastEntityAttack.getLeft() + 1);
        if (lastEntityAttack.getLeft() > 10) {
            lastEntityAttack.setRight(null);
        }
    }

    @Subscribe
    public void onStepSuccess(final StepSuccessEvent event) {
        ticksSinceStepped = 0;
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
       if (event.getPacket() instanceof GameMessageS2CPacket gameMessage) {
            final String message = gameMessage.content().getString();
            final Matcher matcher = HypixelServer.KILL_MESSAGE_PATTERN.matcher(message);

            if (matcher.find()) {
                final String killer = matcher.group("killer");

                if (this.getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
                    final HypixelServer.ModAPI.Location currentLocation = HypixelServer.ModAPI.get().getCurrentLocation();
                    if (currentLocation != null
                            && currentLocation.serverType() == GameType.SKYWARS
                            && currentLocation.mode() != null
                            && !currentLocation.mode().startsWith("mini")) {
                        final int strengthTicks = 20 * (currentLocation.mode().startsWith("solo") ? 5 : 2);

                        strengthedPlayerList.add(killer);
                        Scheduler.addTask(() -> strengthedPlayerList.remove(killer), strengthTicks);
                    }
                }
            }
        } else if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            ticksSinceTeleport = 0;
        } else if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (mc.player != null && packet.getEntityId() == mc.player.getId()) {
                this.velocityStopwatch.reset();
            }
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        boolean noSlowDebug = false;
        if (noSlowDebug) {
            switch (event.getPacket()) {
                case PlayerInteractEntityC2SPacket interact -> {
                    PlayerInteractEntityC2SPacketAccessor accessor = (PlayerInteractEntityC2SPacketAccessor) interact;
                    ChatUtility.error("ENT_" + accessor.getType().getType() + mc.player.age);
                }
                case PlayerInteractItemC2SPacket interact -> {
                    ChatUtility.error("ITEM_INTERACT" + mc.player.age + " " + interact.getHand() + " " + interact.getSequence());
                }
                case PlayerInteractBlockC2SPacket interact -> {
                    ChatUtility.error("BLOCK_INTERACT" + mc.player.age + " " + interact.getHand() + " " + interact.getSequence());
                }
                case UpdateSelectedSlotC2SPacket slot -> {
                    ChatUtility.error("SLOT" + mc.player.age + " " + slot.getSelectedSlot());
                }
                case ClientCommandC2SPacket command -> {
                    ChatUtility.error("COMMAND" + mc.player.age + " " + command.getMode().name());
                }
                case PlayerActionC2SPacket action -> {
                    ChatUtility.error("ACTION" + mc.player.age + " " + action.getAction());
                }
                default -> {
                }
            }
        }
    }

    private Vec3d prevVelocity;

    @Subscribe(priority = -10)
    public void onPostMoveLow(final PostMoveEvent event) {
        this.prevVelocity = mc.player.getVelocity();
    }

    public Vec3d getPrevVelocity() {
        return prevVelocity;
    }

    public static TargetList getTargetList() {
        return instance.targetList;
    }

    public static List<String> getFriendList() {
        return instance.friendList;
    }

    public List<String> getStrengthedPlayerList() {
        return strengthedPlayerList;
    }

    public KnownServerManager getKnownServerManager() {
        return knownServerManager;
    }

    private static LocalDataWatch instance;

    public static LocalDataWatch get() {
        return instance;
    }

    public static void setInstance() {
        instance = new LocalDataWatch();
        EventDispatcher.subscribe(instance);
    }

}
