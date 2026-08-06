package wtf.oraculus.client.feature.helper.impl.server;

import net.minecraft.client.network.ServerAddress;

public interface KnownServerIdentifier {
    KnownServer identifyServer(ServerAddress address);
}
