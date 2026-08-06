package wtf.oraculus.client.feature.module.impl.utility;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static wtf.oraculus.client.Constants.mc;

/**
 * Oraculus AntiBots adapted to Yarn packet names.
 */
public final class AntiBotsModule extends Module {

    private static final Map<UUID, String> SUSPECT_NAMES = new ConcurrentHashMap<>();
    private static final Map<Integer, String> CONFIRMED_BOT_NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUSPECT_JOIN_TIMES = new ConcurrentHashMap<>();
    private static final Set<Integer> CONFIRMED_BOT_IDS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> PLAYER_ADD_TIMES = new ConcurrentHashMap<>();

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.NONE);
    private final NumberProperty newPlayerTimeout =
            new NumberProperty("Respawn Time", 2500.0D, 0.0D, 10000.0D, 100.0D);
    private final BooleanProperty debug = new BooleanProperty("Debug", true);

    public AntiBotsModule() {
        super("AntiBots", "Prevents bots from being targeted.", ModuleCategory.COMBAT);
        this.addProperties(this.mode, this.newPlayerTimeout, this.debug);
    }

    public static boolean shouldFilter(final Entity entity) {
        final AntiBotsModule module = getModule();
        if (module == null || !module.isEnabled() || entity == null) {
            return false;
        }
        return module.mode.getValue() == Mode.HEYPIXEL
                ? isOraculusBot(entity)
                : isBot(entity) || isBedWarsBot(entity);
    }

    private static boolean isOraculusBot(final Entity entity) {
        return isBot(entity) || isBedWarsBot(entity);
    }

    public static boolean isBot(final Entity entity) {
        return entity != null && CONFIRMED_BOT_IDS.contains(entity.getId());
    }

    public static boolean isBedWarsBot(final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity.getId() >= 1_000_000_000 || entity.getId() <= -1) {
            return true;
        }
        if (entity.getName() == null || entity.getNameForScoreboard().isEmpty()) {
            return true;
        }

        final AntiBotsModule module = getModule();
        if (module == null || module.newPlayerTimeout.getValue().floatValue() < 1.0F) {
            return false;
        }

        final Long addTime = PLAYER_ADD_TIMES.get(entity.getUuid());
        return addTime != null
                && (float) (System.currentTimeMillis() - addTime) < module.newPlayerTimeout.getValue().floatValue();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerListS2CPacket infoPacket
                && infoPacket.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
            for (final PlayerListS2CPacket.Entry entry : infoPacket.getEntries()) {
                final GameProfile profile = entry.profile();
                if (profile == null) {
                    continue;
                }

                final UUID uuid = entry.profileId();
                PLAYER_ADD_TIMES.put(uuid, System.currentTimeMillis());

                if (entry.displayName() == null
                        || !entry.displayName().getSiblings().isEmpty()
                        || entry.gameMode() != GameMode.SURVIVAL) {
                    continue;
                }

                SUSPECT_JOIN_TIMES.put(uuid, System.currentTimeMillis());
                SUSPECT_NAMES.put(uuid, entry.displayName().getString());
            }
            return;
        }

        if (packet instanceof EntityAnimationS2CPacket animationPacket) {
            final Entity entity = mc.world.getEntityById(animationPacket.getEntityId());
            if (entity != null && animationPacket.getAnimationId() == 0) {
                PLAYER_ADD_TIMES.remove(entity.getUuid());
            }
            return;
        }

        if (packet instanceof EntitySpawnS2CPacket spawnPacket
                && SUSPECT_JOIN_TIMES.containsKey(spawnPacket.getUuid())) {
            final String botName = SUSPECT_NAMES.get(spawnPacket.getUuid());
            if (this.debug.getValue()) {
                ChatUtility.print("Bot Detected! (" + botName + ")");
            }
            CONFIRMED_BOT_NAMES.put(spawnPacket.getEntityId(), botName);
            SUSPECT_JOIN_TIMES.remove(spawnPacket.getUuid());
            CONFIRMED_BOT_IDS.add(spawnPacket.getEntityId());
            return;
        }

        if (packet instanceof EntitiesDestroyS2CPacket removePacket) {
            for (final int entityId : removePacket.getEntityIds()) {
                if (!CONFIRMED_BOT_IDS.contains(entityId)) {
                    continue;
                }
                if (this.debug.getValue()) {
                    ChatUtility.print("Bot Removed! (" + CONFIRMED_BOT_NAMES.get(entityId) + ")");
                }
                CONFIRMED_BOT_IDS.remove(entityId);
            }
        }
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        for (final Map.Entry<UUID, Long> entry : SUSPECT_JOIN_TIMES.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() <= 500L) {
                continue;
            }
            if (this.debug.getValue()) {
                ChatUtility.print("Fake Staff Detected! (" + SUSPECT_NAMES.get(entry.getKey()) + ")");
            }
            SUSPECT_JOIN_TIMES.remove(entry.getKey());
        }
    }

    @Subscribe
    public void onWorldChange(final JoinWorldEvent event) {
        SUSPECT_NAMES.clear();
        CONFIRMED_BOT_NAMES.clear();
        CONFIRMED_BOT_IDS.clear();
        SUSPECT_JOIN_TIMES.clear();
    }

    private static AntiBotsModule getModule() {
        final OraculusClient client = OraculusClient.getInstance();
        if (client.getModuleRepository() == null) {
            return null;
        }
        return client.getModuleRepository().getModule(AntiBotsModule.class);
    }

    public enum Mode {
        NONE("None"),
        HEYPIXEL("Heypixel");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
