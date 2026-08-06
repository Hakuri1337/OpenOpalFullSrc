package wtf.oraculus.client.feature.helper.impl.server;

import net.minecraft.entity.LivingEntity;
import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;

public abstract class KnownServer implements IHelper {

    private ProxyServer proxyServer;
    private final String name;

    public KnownServer(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(final ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public boolean isValidTarget(final LivingEntity livingEntity) {
        return true;
    }

    @Override
    public boolean isHandlingEvents() {
        return this == LocalDataWatch.get().getKnownServerManager().getCurrentServer();
    }

}
