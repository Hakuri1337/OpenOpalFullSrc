package wtf.oraculus.event.impl.game.server;

import net.minecraft.client.network.ServerAddress;
import wtf.oraculus.event.EventCancellable;

public final class ServerConnectEvent extends EventCancellable {

    private final ServerAddress serverAddress;

    public ServerConnectEvent(final ServerAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public ServerAddress getServerAddress() {
        return serverAddress;
    }

}
