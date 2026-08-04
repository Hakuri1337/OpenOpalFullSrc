package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

public final class RuntimeAccessGate {
    private static final GateState DENIED = new GateState(null, 0L, 0L);
    private static volatile GateState state = DENIED;
    private static long generation;

    private RuntimeAccessGate() {
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.GRAPH,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            )
    )
    static synchronized void install(final RuntimePermit permit, final long now) {
        final RuntimePermit accepted = ModuleBootPolicy.requireRuntimeStart(permit);
        state = new GateState(accepted, accepted.accessExpiresAt(), ++generation);
        if (!state.permit().verify(RuntimeDomain.MODULE_EXECUTION, now)) {
            state = DENIED;
            throw new SecurityException("Authenticated runtime gate installation failed");
        }
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.GRAPH,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            )
    )
    static synchronized void enterNetworkGrace(final RuntimePermit permit, final long graceExpiresAt,
                                                final long now) {
        final GateState current = state;
        if (permit == null || current.permit() != permit
                || !permit.verify(RuntimeDomain.MODULE_EXECUTION, now)) {
            state = DENIED;
            throw new SecurityException("Authenticated runtime grace transition failed");
        }
        final long deadline = Math.min(permit.refreshExpiresAt(), graceExpiresAt);
        if (deadline <= now) {
            state = DENIED;
            throw new SecurityException("Authenticated runtime grace has expired");
        }
        state = new GateState(permit, deadline, ++generation);
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.GRAPH,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            )
    )
    static synchronized void revoke() {
        generation++;
        state = DENIED;
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.GRAPH,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            )
    )
    public static void require(final RuntimeDomain domain) {
        final GateState current = state;
        final long now = System.currentTimeMillis() / 1000L;
        if (domain == null || current.permit() == null || now >= current.hotExpiresAt()
                || !current.permit().verify(domain, now)) {
            throw new SecurityException("Authenticated runtime resource is unavailable");
        }
    }

    public static boolean allowsHotPath() {
        final GateState current = state;
        return current.permit() != null && System.currentTimeMillis() / 1000L < current.hotExpiresAt();
    }

    static long generation() {
        return state.generation();
    }

    private record GateState(RuntimePermit permit, long hotExpiresAt, long generation) {
    }
}
