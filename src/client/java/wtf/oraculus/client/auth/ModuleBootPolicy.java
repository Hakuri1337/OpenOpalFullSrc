package wtf.oraculus.client.auth;

import tech.Oa7hTeam.obfuscate.vm.Virtualize;

@Virtualize
public final class ModuleBootPolicy {
    private ModuleBootPolicy() {
    }

    public static RuntimePermit requireRuntimeStart(final RuntimePermit permit) {
        return require(permit, RuntimeDomain.RUNTIME_START);
    }

    public static RuntimePermit requireModuleCatalog(final RuntimePermit permit) {
        return require(permit, RuntimeDomain.MODULE_CATALOG);
    }

    private static RuntimePermit require(final RuntimePermit permit, final RuntimeDomain domain) {
        final long now = System.currentTimeMillis() / 1000L;
        if (permit == null || !permit.verify(domain, now)) {
            throw new SecurityException("Authenticated runtime permit validation failed");
        }
        return permit;
    }
}
