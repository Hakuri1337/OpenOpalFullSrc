package wtf.oraculus.client.feature.helper.impl.server;

import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public class ProxyServer extends KnownServer {

    public ProxyServer(final String name) {
        super(name);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.getNetworkHandler() == null) {
            return;
        }

        final String serverBrand = mc.getNetworkHandler().getBrand();
        if (serverBrand != null && HypixelServer.SERVER_BRAND_PATTERN.matcher(serverBrand).matches()) {
            final KnownServer realServer = new HypixelServer();
            realServer.setProxyServer(this);

            LocalDataWatch.get().getKnownServerManager().setServer(realServer);
        }
    }

}
