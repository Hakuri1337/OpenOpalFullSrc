package wtf.oraculus;

import net.fabricmc.api.ClientModInitializer;
import wtf.oraculus.client.OraculusClient;

public final class OraculusFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OraculusClient.setInstance();
    }

}
