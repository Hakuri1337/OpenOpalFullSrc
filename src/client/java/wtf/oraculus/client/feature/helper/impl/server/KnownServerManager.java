package wtf.oraculus.client.feature.helper.impl.server;

import net.minecraft.client.network.ServerAddress;
import wtf.oraculus.client.feature.helper.impl.server.impl.CubecraftServer;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.helper.impl.server.impl.proxy.LiquidProxyServer;
import wtf.oraculus.client.feature.helper.impl.server.impl.proxy.NyaProxyServer;
import wtf.oraculus.event.EventDispatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KnownServerManager {

    private KnownServer currentServer;

    public void identifyServer(final ServerAddress address) {
        for (final KnownServerIdentifier identifier : SERVER_IDENTIFIERS) {
            final KnownServer knownServer = identifier.identifyServer(address);
            if (knownServer != null) {
                this.currentServer = knownServer;
                EventDispatcher.subscribe(knownServer);
                return;
            }
        }
        this.currentServer = null;
    }

    public KnownServer getCurrentServer() {
        return currentServer;
    }

    public void resetServer() {
        this.currentServer = null;
    }

    public void setServer(final KnownServer currentServer) {
        if (this.currentServer != currentServer) {
            this.currentServer = currentServer;
            EventDispatcher.subscribe(currentServer);
        }
    }

    private static final KnownServerIdentifier[] SERVER_IDENTIFIERS = {
            // Hypixel
            address -> {
                if (address.getPort() == 25565) {
                    if (isAddressOfDomain(address, "hypixel.net", true)
                            || isAddressOfDomain(address, "hypixel.io", true)
                            || isAddressOfDomain(address, "technoblade.club", true)) {
                        return new HypixelServer();
                    }
                }
                return null;
            },
            // Cubecraft
            address -> {
                if (address.getPort() == 25565) {
                    if (isAddressOfDomain(address, "cubecraft.net", false)
                            || isAddressOfDomain(address, "play.cubecraft.net", false)) {
                        return new CubecraftServer();
                    }
                }
                return null;
            },
            address -> {
                if (address.getPort() == 25565 && isAddressOfDomain(address, "liquidproxy.net", true)) {
                    return new LiquidProxyServer();
                }
                return null;
            },
            address -> {
                if (address.getPort() == 25565 && isAddressOfDomain(address, "nyap.buzz", true)) {
                    return new NyaProxyServer();
                }
                return null;
            }
    };

    private static boolean isAddressOfDomain(ServerAddress address, String domain, boolean allowSubdomains) {
        final String addressStr = address.getAddress().toLowerCase();

        String regex = Pattern.quote(domain) + "(\\.*)$";
        if (allowSubdomains) {
            regex = "^(?:[a-zA-Z0-9-]+\\.)*" + regex;
        }

        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(addressStr);

        return matcher.matches();
    }

}
