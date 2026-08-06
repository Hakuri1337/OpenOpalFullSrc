package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.screen.serverpack.ServerPackSpoofScreen;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intercepts server-pack requests before Minecraft's downloader can start. Required packs
 * wait for an explicit dialog action; optional packs are declined immediately.
 */
public final class ServerPackSpoofModule extends Module {
    private final List<PendingPack> pendingPacks = new ArrayList<>();

    private ClientConnection pendingConnection;
    private Screen pendingParent;
    private ServerPackSpoofScreen activeScreen;

    public ServerPackSpoofModule() {
        super("ServerPackSpoof", "Blocks server resource-pack downloads.", ModuleCategory.UTILITY);
    }

    public void handleRequest(final MinecraftClient client, final ClientConnection connection,
                              final ResourcePackSendS2CPacket packet) {
        if (client == null || connection == null || packet == null || !connection.isOpen()) {
            return;
        }

        if (!packet.required()) {
            sendStatus(connection, packet.id(), ResourcePackStatusC2SPacket.Status.DECLINED);
            return;
        }

        if (this.pendingConnection != connection) {
            this.discardStaleBatch();
            this.pendingConnection = connection;
            this.pendingParent = client.currentScreen;
        }

        this.pendingPacks.add(new PendingPack(packet.id(), packet.url(), packet.hash(), packet.prompt().orElse(null)));

        if (client.currentScreen instanceof ServerPackSpoofScreen screen && screen.isFor(this, connection)) {
            this.activeScreen = screen;
            return;
        }

        final ServerPackSpoofScreen screen = new ServerPackSpoofScreen(this, connection, this.pendingParent);
        this.activeScreen = screen;
        client.setScreen(screen);
    }

    public void loadPending(final ServerPackSpoofScreen source) {
        final PendingBatch batch = this.takeBatch(source);
        if (batch == null) {
            return;
        }

        final List<LoadablePack> loadablePacks = new ArrayList<>();
        for (final PendingPack pack : batch.packs()) {
            final URL url = parseHttpUrl(pack.url());
            if (url == null) {
                sendStatus(batch.connection(), pack.id(), ResourcePackStatusC2SPacket.Status.INVALID_URL);
            } else {
                loadablePacks.add(new LoadablePack(pack, url));
            }
        }

        this.restoreParent(batch);
        if (loadablePacks.isEmpty() || !batch.connection().isOpen()) {
            return;
        }

        final ServerResourcePackLoader loader = batch.client().getServerResourcePackProvider();
        loader.acceptAll();
        for (final LoadablePack loadable : loadablePacks) {
            loader.addResourcePack(loadable.pack().id(), loadable.url(), loadable.pack().hash());
        }
    }

    public void spoofPending(final ServerPackSpoofScreen source) {
        final PendingBatch batch = this.takeBatch(source);
        if (batch == null) {
            return;
        }

        for (final PendingPack pack : batch.packs()) {
            sendStatus(batch.connection(), pack.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED);
            sendStatus(batch.connection(), pack.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED);
            sendStatus(batch.connection(), pack.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
        }
        this.restoreParent(batch);
    }

    public void declinePending(final ServerPackSpoofScreen source) {
        final PendingBatch batch = this.takeBatch(source);
        if (batch == null) {
            return;
        }

        for (final PendingPack pack : batch.packs()) {
            sendStatus(batch.connection(), pack.id(), ResourcePackStatusC2SPacket.Status.DECLINED);
        }
        this.restoreParent(batch);
    }

    public int getPendingCount(final ClientConnection connection) {
        return this.pendingConnection == connection ? this.pendingPacks.size() : 0;
    }

    public Text getPrompt(final ClientConnection connection) {
        if (this.pendingConnection != connection) {
            return Text.empty();
        }

        for (int index = this.pendingPacks.size() - 1; index >= 0; index--) {
            final Text prompt = this.pendingPacks.get(index).prompt();
            if (prompt != null) {
                return prompt;
            }
        }
        return Text.translatable("multiplayer.requiredTexturePrompt.line1");
    }

    public String getSourceHost(final ClientConnection connection) {
        if (this.pendingConnection != connection || this.pendingPacks.isEmpty()) {
            return "Unknown source";
        }

        final URL url = parseHttpUrl(this.pendingPacks.getFirst().url());
        return url == null || url.getHost().isBlank() ? "Unknown source" : url.getHost();
    }

    @Override
    protected void onDisable() {
        this.declinePending(this.activeScreen);
        super.onDisable();
    }

    private PendingBatch takeBatch(final ServerPackSpoofScreen source) {
        if (this.pendingConnection == null || this.pendingPacks.isEmpty()) {
            return null;
        }
        if (source != null && source != this.activeScreen) {
            return null;
        }

        if (this.activeScreen != null) {
            this.activeScreen.markResolved();
        }

        final PendingBatch batch = new PendingBatch(
                MinecraftClient.getInstance(),
                this.pendingConnection,
                this.pendingParent,
                this.activeScreen,
                List.copyOf(this.pendingPacks)
        );
        this.pendingPacks.clear();
        this.pendingConnection = null;
        this.pendingParent = null;
        this.activeScreen = null;
        return batch;
    }

    private void discardStaleBatch() {
        if (this.activeScreen != null) {
            this.activeScreen.markResolved();
        }
        this.pendingPacks.clear();
        this.pendingConnection = null;
        this.pendingParent = null;
        this.activeScreen = null;
    }

    private void restoreParent(final PendingBatch batch) {
        if (batch.client().currentScreen == batch.screen()) {
            batch.client().setScreen(batch.parent());
        }
    }

    private static URL parseHttpUrl(final String value) {
        try {
            final URL url = new URL(value);
            return "http".equals(url.getProtocol()) || "https".equals(url.getProtocol()) ? url : null;
        } catch (MalformedURLException exception) {
            return null;
        }
    }

    private static void sendStatus(final ClientConnection connection, final UUID id,
                                   final ResourcePackStatusC2SPacket.Status status) {
        if (connection == null || !connection.isOpen()) {
            return;
        }

        try {
            connection.send(new ResourcePackStatusC2SPacket(id, status), null, true);
        } catch (RuntimeException ignored) {
            // The connection can close between the open check and Netty's write.
        }
    }

    private record PendingPack(UUID id, String url, String hash, Text prompt) {
    }

    private record LoadablePack(PendingPack pack, URL url) {
    }

    private record PendingBatch(MinecraftClient client, ClientConnection connection, Screen parent,
                                ServerPackSpoofScreen screen, List<PendingPack> packs) {
    }
}
