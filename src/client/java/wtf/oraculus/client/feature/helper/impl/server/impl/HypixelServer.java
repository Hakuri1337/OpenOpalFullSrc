package wtf.oraculus.client.feature.helper.impl.server.impl;

import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.KnownServer;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static wtf.oraculus.client.Constants.mc;

public final class HypixelServer extends KnownServer {

    private final Set<Vec3d> armorStands = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bots = ConcurrentHashMap.newKeySet();

    public HypixelServer() {
        super("Hypixel");
    }

    @Override
    public boolean isValidTarget(final LivingEntity livingEntity) {
        final ModAPI.Location location = ModAPI.get().getCurrentLocation();
        if (location != null && (location.serverType() == GameType.REPLAY || location.serverType() == GameType.SMP)) {
            return true;
        }

        final String unstyledName = livingEntity.getName().getString();
        if (unstyledName == null || unstyledName.isEmpty()) {
            return true;
        }

        if (livingEntity instanceof ArmorStandEntity) {
            this.armorStands.add(livingEntity.getEntityPos());
            return false;
        }

        if (livingEntity.getId() == -1234) {
            return false;
        }

        if (livingEntity instanceof OtherClientPlayerEntity player) {
            final PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (playerListEntry == null || playerListEntry.getProfile() == null) {
                return false;
            }

            if (playerListEntry.getLatency() > 1 && player.getHealth() > 14 && player.getHealth() < 20 && player.isInvisible()) {
                return false;
            }

            final boolean inLobby = location != null && location.isLobby();
            final UUID uuid = player.getUuid();

            if (uuid.version() == 2 && (inLobby || (player.getHealth() == 20 && playerListEntry.getScoreboardTeam() == null))) {
                return false;
            }

            if (this.bots.contains(uuid)) {
                if (inLobby || player.getHealth() > 20 || (!player.isInvisible() && (player.isOnGround() || player.age > 170))) {
                    this.bots.remove(uuid);
                } else {
                    return false;
                }
            }
        } else // Villagers should not have UUID v4
            if (livingEntity.getUuid().version() != 4) {
                if (livingEntity.isInvisible()) {
                    return false;
                }

                if (unstyledName.contains(" ")) {
                    final List<Text> siblings = livingEntity.getName().getSiblings();
                    if (!siblings.isEmpty() && siblings.getFirst().getStyle().getColor() != null) {
                        return false;
                    }
                }

                final Vec3d pos = livingEntity.getEntityPos();
                if (this.armorStands.stream().anyMatch(armorStand -> armorStand.distanceTo(pos) < 2)) {
                    return false;
                }
            } else return !(livingEntity instanceof VillagerEntity);

        return true;
    }


    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.armorStands.clear();
        this.bots.clear();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.getNetworkHandler() != null) {
            // Hypixel BungeeCord (2025.2.5.1) <- Hygot 2025.2.4.1
            // Hypixel BungeeCord (2025.2.5.1) <- Paper [...]
            final String serverBrand = mc.getNetworkHandler().getBrand();
            if (serverBrand != null && !SERVER_BRAND_PATTERN.matcher(serverBrand).matches()) {
                // user is on a fake Hypixel server, reset known server
                LocalDataWatch.get().getKnownServerManager().resetServer();
            }
        }
    }

    public static class ModAPI {

        private static final ModAPI INSTANCE = new ModAPI();

        @Nullable
        private Location currentLocation, previousLocation;

        public ModAPI() {
            HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
            HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, this::onLocationReceive);
        }

        public static ModAPI get() {
            return INSTANCE;
        }

        @Nullable
        public Location getCurrentLocation() {
            return currentLocation;
        }

        public void setCurrentLocation(@Nullable Location currentLocation) {
            this.currentLocation = currentLocation;
        }

        @Nullable
        public Location getPreviousLocation() {
            return previousLocation;
        }

        private void onLocationReceive(final ClientboundLocationPacket packet) {
            previousLocation = currentLocation;
            currentLocation = new Location(
                    packet.getServerName(),
                    packet.getServerType().orElse(null),
                    packet.getLobbyName().orElse(null),
                    packet.getMode().orElse(null),
                    packet.getMap().orElse(null)
            );
        }

        public record Location(String serverName, @Nullable ServerType serverType, @Nullable String lobbyName,
                               @Nullable String mode, @Nullable String map) {
            public boolean isLobby() {
                return lobbyName != null;
            }
        }

    }

    public enum BedColor {
        RED(28, 16733525),
        GREEN(19, 5635925),
        BLUE(25, 5592575),
        YELLOW(18, 16777045),
        AQUA(23, 5636095),
        WHITE(8, 16777215),
        PINK(20, 16733695),
        GRAY(21, 5592405);

        public final int mapColorId, teamColor;

        BedColor(final int mapColorId, final int teamColor) {
            this.mapColorId = mapColorId;
            this.teamColor = teamColor;
        }

        @Nullable
        public static HypixelServer.BedColor fromTeamColor(final int teamColor) {
            for (final BedColor color : values()) {
                if (color.teamColor == teamColor) {
                    return color;
                }
            }
            return null;
        }
    }

    public static final Pattern SERVER_BRAND_PATTERN = Pattern.compile("Hypixel BungeeCord \\(.+\\) <- .+");

    public static final Pattern KILL_MESSAGE_PATTERN = Pattern.compile(
            "(?<username>\\w{1,16}) ?.+(by|of|to|for|with|the|from|was|fighting|against|meet) (?<killer>\\w{1,16})",
            Pattern.CASE_INSENSITIVE);

    public static final List<Pattern> KARMA_PATTERNS = List.of(
            Pattern.compile("^ +1st Killer - ?\\[?\\w*\\+*\\]? \\w+ - \\d+(?: Kills?)?$"),
            Pattern.compile("^ *1st (?:Place ?)?(?:-|:)? ?\\[?\\w*\\+*\\]? \\w+(?: : \\d+| - \\d+(?: Points?)?| - \\d+(?: x .)?| \\(\\w+ .{1,6}\\) - \\d+ Kills?|: \\d+:\\d+| - \\d+ (?:Zombie )?(?:Kills?|Blocks? Destroyed)| - \\[LINK\\])?$"),
            Pattern.compile("^ +Winn(?:er #1 \\(\\d+ Kills\\): \\w+ \\(\\w+\\)|er(?::| - )(?:Hiders|Seekers|Defenders|Attackers|PLAYERS?|MURDERERS?|Red|Blue|RED|BLU|\\w+)(?: Team)?|ers?: ?\\[?\\w*\\+*\\]? \\w+(?:, ?\\[?\\w*\\+*\\]? \\w+)?|ing Team ?[\\:-] (?:Animals|Hunters|Red|Green|Blue|Yellow|RED|BLU|Survivors|Vampires))$"),
            Pattern.compile("^ +Alpha Infected: \\w+ \\(\\d+ infections?\\)$"),
            Pattern.compile("^ +Murderer: \\w+ \\(\\d+ Kills?\\)$"),
            Pattern.compile("^ +You survived \\d+ rounds!$"),
            Pattern.compile("^ +(?:UHC|SkyWars|Bridge|Sumo|Classic|OP|MegaWalls|Bow|NoDebuff|Blitz|Combo|Bow Spleef) (?:Duel|Doubles|3v3|4v4|Teams|Deathmatch|2v2v2v2|3v3v3v3)? ?- \\d+:\\d+$"),
            Pattern.compile("^ +They captured all wools!$"),
            Pattern.compile("^ +Game over!$"),
            Pattern.compile("^ +[\\d\\.]+k?/[\\d\\.]+k? \\w+$"),
            Pattern.compile("^ +(?:Criminal|Cop)s won the game!$"),
            Pattern.compile("^ +\\[?\\w*\\+*\\]? \\w+ - \\d+ Final Kills$"),
            Pattern.compile("^ +Zombies - \\d*:?\\d+:\\d+ \\(Round \\d+\\)$"),
            Pattern.compile("^ +. YOUR STATISTICS .$"),
            Pattern.compile("^ {36}Winner(s?)$"),
            Pattern.compile("^ {21}Bridge CTF [a-zA-Z]+ - \\d\\d:\\d\\d$")
    );

}
