package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import wtf.oraculus.client.Constants;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.PlayerCreateEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;

import static wtf.oraculus.client.Constants.mc;

public final class KillSayModule extends Module {

    private static final Path FILE_PATH = Constants.DIRECTORY.toPath().resolve("KillSay.txt");
    private static final String[] DEATH_KEYWORDS = new String[]{
            "killed", "slain", "void", "fell", "died", "dead", "eliminated", "final killed",
            "击杀", "击败", "淘汰", "死亡", "摔死", "虚空"
    };

    private final Map<String, String> attackedPlayers = new LinkedHashMap<>();
    private final Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();
    private int nextMessageIndex;

    public KillSayModule() {
        super("Kill Say", "Sends rotating kill messages from KillSay.txt.", ModuleCategory.UTILITY);
    }

    @Override
    protected void onEnable() {
        this.ensureFileExists();
        this.attackedPlayers.clear();
        this.pendingMessages.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.attackedPlayers.clear();
        this.pendingMessages.clear();
        super.onDisable();
    }

    @Subscribe
    public void onPlayerCreate(final PlayerCreateEvent event) {
        this.attackedPlayers.clear();
        this.pendingMessages.clear();
    }

    @Subscribe
    public void onAttack(final AttackEvent event) {
        if (event.getTarget() instanceof PlayerEntity player) {
            final String name = player.getName().getString();
            this.attackedPlayers.put(normalize(name), name);
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        if (mc.player.age < 10) {
            this.pendingMessages.clear();
            this.attackedPlayers.clear();
            return;
        }

        final String message = this.pendingMessages.poll();
        if (message != null) {
            ChatUtility.send(message);
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) {
            return;
        }

        if (event.getPacket() instanceof PlayerRemoveS2CPacket packet) {
            if (mc.getNetworkHandler() == null) {
                return;
            }
            for (final UUID uuid : packet.profileIds()) {
                final var entry = mc.getNetworkHandler().getPlayerListEntry(uuid);
                if (entry == null) {
                    continue;
                }
                final String name = entry.getProfile().name();
                final String stored = this.attackedPlayers.remove(normalize(name));
                if (stored != null) {
                    this.queueMessage(stored);
                }
            }
            return;
        }

        if (event.getPacket() instanceof GameMessageS2CPacket gameMessage) {
            final String message = gameMessage.content().getString();
            final Matcher matcher = HypixelServer.KILL_MESSAGE_PATTERN.matcher(message);
            String victim = null;

            if (matcher.find()) {
                final String killer = matcher.group("killer");
                if (normalize(mc.player.getName().getString()).equals(normalize(killer))) {
                    victim = matcher.group("username");
                    this.attackedPlayers.remove(normalize(victim));
                }
            }

            if (victim == null) {
                final String normalizedMessage = normalize(message);
                if (!looksLikeKillMessage(normalizedMessage)) {
                    return;
                }
                victim = this.findMentionedAttackedPlayer(normalizedMessage);
                if (victim == null) {
                    return;
                }
                this.attackedPlayers.remove(normalize(victim));
            }

            this.queueMessage(victim);
        }
    }

    private String nextMessage(final String victimName) {
        final List<String> lines = this.readLines();
        if (lines.isEmpty()) {
            return null;
        }

        final int index = Math.floorMod(this.nextMessageIndex, lines.size());
        this.nextMessageIndex = (index + 1) % lines.size();
        return lines.get(index).replace("{name}", victimName);
    }

    private List<String> readLines() {
        this.ensureFileExists();
        try {
            return Files.readAllLines(FILE_PATH, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (final IOException ignored) {
            return List.of();
        }
    }

    private void ensureFileExists() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            if (!Files.exists(FILE_PATH)) {
                Files.writeString(FILE_PATH, "gg {name}" + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        } catch (final IOException ignored) {
        }
    }

    private String findMentionedAttackedPlayer(final String normalizedMessage) {
        for (final Map.Entry<String, String> entry : this.attackedPlayers.entrySet()) {
            if (normalizedMessage.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean looksLikeKillMessage(final String normalizedMessage) {
        for (final String keyword : DEATH_KEYWORDS) {
            if (normalizedMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(final String text) {
        return text == null ? "" : text.replaceAll("§.", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private void queueMessage(final String victim) {
        final String nextMessage = this.nextMessage(victim);
        if (nextMessage != null && !nextMessage.isBlank()) {
            this.pendingMessages.offer(nextMessage);
        }
    }
}
