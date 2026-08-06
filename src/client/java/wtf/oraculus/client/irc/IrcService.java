package wtf.oraculus.client.irc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import wtf.oraculus.client.auth.AuthService;
import wtf.oraculus.client.auth.RuntimeAccessGate;
import wtf.oraculus.client.auth.RuntimeDomain;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;
import tech.Oa7hTeam.obfuscate.mark.Oa7hNoParameter;
import tech.Oa7hTeam.obfuscate.vm.Virtualize;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static wtf.oraculus.client.Constants.mc;

@Oa7hIndy
public final class IrcService implements AutoCloseable, IEventSubscriber {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RECONNECT_DELAY_MILLIS = 2500L;

    private final AuthService auth;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        final Thread thread = new Thread(runnable, "Oraculus-IRC");
        thread.setDaemon(true);
        return thread;
    });
    private final IrcApiClient api = new IrcApiClient(this.executor);
    private final Map<String, IrcUser> usersByAccount = new ConcurrentHashMap<>();
    private final Map<String, IrcUser> usersByProfile = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean identityProofInFlight = new AtomicBoolean();

    private volatile InputStream activeStream;
    private volatile String status = "未启动";
    private volatile int presenceTick;
    private volatile String provenProfileId = "";

    public IrcService(final AuthService auth) {
        this.auth = auth;
        EventDispatcher.subscribe(this);
    }

    public void start() {
        RuntimeAccessGate.require(RuntimeDomain.NETWORK_START);
        if (!this.running.compareAndSet(false, true)) return;
        this.status = "正在连接";
        this.executor.execute(this::streamLoop);
    }

    public void stop() {
        this.running.set(false);
        this.connected.set(false);
        this.status = "已停止";
        this.usersByAccount.clear();
        this.usersByProfile.clear();
        this.provenProfileId = "";
        this.identityProofInFlight.set(false);
        final InputStream stream = this.activeStream;
        this.activeStream = null;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void reconnect() {
        if (!RuntimeAccessGate.allowsHotPath()) {
            this.stop();
            return;
        }
        if (!this.running.get()) {
            this.start();
            return;
        }
        this.connected.set(false);
        this.status = "正在重连";
        final InputStream stream = this.activeStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    public CompletableFuture<Boolean> sendMessage(final String content) {
        if (!RuntimeAccessGate.allowsHotPath()) return CompletableFuture.completedFuture(false);
        final String token = this.auth.ircAccessToken();
        if (!this.connected.get() || token.isBlank()) return CompletableFuture.completedFuture(false);
        final JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        return this.api.post("messages", payload, token)
                .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300)
                .exceptionally(throwable -> false);
    }

    public boolean isConnected() {
        return this.connected.get();
    }

    public String status() {
        return this.status;
    }

    public List<IrcUser> users() {
        return this.usersByAccount.values().stream()
                .sorted((left, right) -> left.username().compareToIgnoreCase(right.username()))
                .toList();
    }

    @Virtualize
    public boolean isProtectedProfile(final UUID uuid) {
        if (uuid == null || !this.connected.get()) return false;
        final IrcUser user = this.usersByProfile.get(IrcUser.normalizeUuid(uuid.toString()));
        return user != null && user.protectsProfile();
    }

    @Subscribe
    @Oa7hNoParameter
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!RuntimeAccessGate.allowsHotPath() || !this.running.get() || !this.connected.get()) return;
        if (++this.presenceTick >= 100) {
            this.presenceTick = 0;
            this.publishPresence();
        }
    }

    @Subscribe
    @Oa7hNoParameter
    public void onJoinWorld(final JoinWorldEvent event) {
        this.presenceTick = 99;
    }

    @Subscribe
    @Oa7hNoParameter
    public void onServerDisconnect(final ServerDisconnectEvent event) {
        this.presenceTick = 99;
    }

    private void streamLoop() {
        while (this.running.get() && RuntimeAccessGate.allowsHotPath()) {
            final String token = this.auth.ircAccessToken();
            if (token.isBlank()) {
                this.waitBeforeReconnect();
                continue;
            }
            try {
                this.status = "正在连接";
                final HttpResponse<InputStream> response = this.api.openStream(token);
                if (response.statusCode() != 200) {
                    response.body().close();
                    this.status = "连接被拒绝（HTTP " + response.statusCode() + "）";
                    this.waitBeforeReconnect();
                    continue;
                }
                this.activeStream = response.body();
                this.connected.set(true);
                this.status = "在线";
                mc.execute(this::publishPresence);
                this.readStream(response.body());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                if (this.running.get()) LOGGER.debug("Oraculus IRC stream disconnected", exception);
            } finally {
                this.activeStream = null;
                this.connected.set(false);
                this.usersByAccount.clear();
                this.usersByProfile.clear();
                this.provenProfileId = "";
                this.identityProofInFlight.set(false);
                if (this.running.get()) this.status = "连接中断，正在重试";
            }
            this.waitBeforeReconnect();
        }
    }

    private void readStream(final InputStream input) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            final StringBuilder data = new StringBuilder();
            String line;
            while (this.running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        this.handleEvent(data.toString());
                        data.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                }
            }
        }
    }

    private void handleEvent(final String raw) {
        try {
            final JsonObject envelope = JsonParser.parseString(raw).getAsJsonObject();
            final String type = string(envelope, "type");
            final JsonObject data = envelope.has("data") && envelope.get("data").isJsonObject()
                    ? envelope.getAsJsonObject("data") : new JsonObject();
            switch (type) {
                case "roster.snapshot" -> this.replaceRoster(data.getAsJsonArray("users"));
                case "chat.message" -> this.displayMessage(data);
                case "session.expired" -> this.reconnect();
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Ignoring malformed IRC event", exception);
        }
    }

    private void replaceRoster(final JsonArray array) {
        final Map<String, IrcUser> accounts = new ConcurrentHashMap<>();
        final Map<String, IrcUser> profiles = new ConcurrentHashMap<>();
        if (array != null) {
            for (final JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                final JsonObject user = element.getAsJsonObject();
                final IrcUser parsed = new IrcUser(
                        string(user, "username"),
                        string(user, "tier"),
                        string(user, "role"),
                        string(user, "minecraftProfileId"),
                        string(user, "minecraftProfileName"),
                        string(user, "presence"),
                        bool(user, "profileVerified"),
                        number(user, "connectedAtUtc")
                );
                accounts.put(parsed.username().toUpperCase(java.util.Locale.ROOT), parsed);
                if (!parsed.minecraftProfileId().isBlank()) profiles.put(parsed.minecraftProfileId(), parsed);
            }
        }
        this.usersByAccount.clear();
        this.usersByAccount.putAll(accounts);
        this.usersByProfile.clear();
        this.usersByProfile.putAll(profiles);
    }

    private void displayMessage(final JsonObject message) {
        final JsonObject sender = message.has("sender") && message.get("sender").isJsonObject()
                ? message.getAsJsonObject("sender") : new JsonObject();
        final String tier = string(sender, "tier");
        final String username = string(sender, "username");
        final String content = string(message, "content");
        if (!username.isBlank() && !content.isBlank())
            ChatUtility.print("[IRC][" + tier + "] " + username + ": " + content);
    }

    private void publishPresence() {
        if (!RuntimeAccessGate.allowsHotPath() || !this.running.get() || !this.connected.get()) return;
        final UUID uuid = mc.getSession().getUuidOrNull();
        if (uuid == null) return;
        final JsonObject payload = new JsonObject();
        payload.addProperty("state", mc.world == null ? "MENU" : "IN_GAME");
        payload.addProperty("minecraftProfileId", IrcUser.normalizeUuid(uuid.toString()));
        payload.addProperty("minecraftProfileName", mc.getSession().getUsername());
        final String token = this.auth.ircAccessToken();
        if (token.isBlank()) return;
        final String profileId = IrcUser.normalizeUuid(uuid.toString());
        this.api.post("presence", payload, token).thenAccept(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && mc.world != null && !profileId.equals(this.provenProfileId)) {
                this.proveMinecraftIdentity(token, profileId, mc.getSession().getUsername());
            }
        }).exceptionally(throwable -> null);
    }

    private void proveMinecraftIdentity(final String token, final String profileId, final String profileName) {
        if (!this.identityProofInFlight.compareAndSet(false, true)) return;
        final JsonObject challenge = new JsonObject();
        challenge.addProperty("minecraftProfileId", profileId);
        challenge.addProperty("minecraftProfileName", profileName);
        this.api.post("identity/challenge", challenge, token).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                return CompletableFuture.completedFuture(false);
            try {
                final JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                final String serverId = string(json, "ServerId");
                if (serverId.isBlank()) return CompletableFuture.completedFuture(false);
                return this.api.joinMinecraftSession(mc.getSession().getAccessToken(), profileId, serverId)
                        .thenCompose(joined -> {
                            if (!joined) return CompletableFuture.completedFuture(false);
                            return this.api.post("identity/verify", new JsonObject(), token)
                                    .thenApply(verify -> verify.statusCode() >= 200 && verify.statusCode() < 300);
                        });
            } catch (RuntimeException exception) {
                return CompletableFuture.completedFuture(false);
            }
        }).whenComplete((verified, throwable) -> {
            if (Boolean.TRUE.equals(verified)) this.provenProfileId = profileId;
            this.identityProofInFlight.set(false);
        });
    }

    private void waitBeforeReconnect() {
        if (!this.running.get()) return;
        try {
            Thread.sleep(RECONNECT_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String string(final JsonObject object, final String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private static boolean bool(final JsonObject object, final String name) {
        return object.has(name) && !object.get(name).isJsonNull() && object.get(name).getAsBoolean();
    }

    private static long number(final JsonObject object, final String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsLong() : 0L;
    }

    @Override
    public void close() {
        this.stop();
        this.executor.shutdownNow();
    }
}
