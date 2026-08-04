package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

public final class ModuleBootPolicy {
    private ModuleBootPolicy() {
    }

    @Virtualize(integrityCheck = Toggle.ENABLED, vm = @VMOptions(
            structure = VMStructure.GRAPH, encrypt = Toggle.ENABLED,
            shuffle = Toggle.ENABLED, obfuscate = Toggle.ENABLED))
    public static RuntimePermit requireRuntimeStart(final RuntimePermit permit) {
        return require(permit, RuntimeDomain.RUNTIME_START);
    }

    @Virtualize(integrityCheck = Toggle.ENABLED, vm = @VMOptions(
            structure = VMStructure.GRAPH, encrypt = Toggle.ENABLED,
            shuffle = Toggle.ENABLED, obfuscate = Toggle.ENABLED))
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
