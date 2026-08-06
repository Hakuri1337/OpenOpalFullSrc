package wtf.oraculus.client.auth;

public enum RuntimeDomain {
    RUNTIME_START("oraculus-runtime-v3"),
    MODULE_CATALOG("oraculus-module-catalog-v3"),
    MODULE_ENABLE("oraculus-module-enable-v3"),
    MODULE_EXECUTION("oraculus-module-execution-v3"),
    CONFIG_APPLY("oraculus-config-apply-v3"),
    COMMAND_EXECUTE("oraculus-command-execute-v3"),
    NETWORK_START("oraculus-network-start-v3");

    private final String sealDomain;

    RuntimeDomain(final String sealDomain) {
        this.sealDomain = sealDomain;
    }

    String sealDomain() {
        return sealDomain;
    }
}
