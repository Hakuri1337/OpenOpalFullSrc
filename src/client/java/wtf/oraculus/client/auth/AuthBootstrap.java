package wtf.oraculus.client.auth;

import wtf.oraculus.client.OraculusClient;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

@Oa7hIndy
public final class AuthBootstrap {
    private static AuthService service;

    private AuthBootstrap() {
    }

    public static synchronized AuthService initialize(final OraculusClient client) {
        if (service != null) return service;
        service = new AuthService(client);
        new AuthRuntimeGate(service);
        service.initialize();
        return service;
    }

    public static AuthService getService() {
        return service;
    }
}
