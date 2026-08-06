package wtf.oraculus.client.auth;

import tech.Oa7hTeam.obfuscate.vm.Virtualize;
import tech.Oa7hTeam.obfuscate.vm.VirtualizeExclude;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

@Virtualize
@Oa7hIndy
public final class RuntimeAccessGate {
    private static final GateState DENIED = new GateState(null, 0L, 0L);
    private static volatile GateState state = DENIED;
    private static long generation;

    private RuntimeAccessGate() {
    }

    static synchronized void install(final RuntimePermit permit, final long now) {
        final RuntimePermit accepted = ModuleBootPolicy.requireRuntimeStart(permit);
        state = new GateState(accepted, accepted.accessExpiresAt(), ++generation);
        if (!state.permit().verify(RuntimeDomain.MODULE_EXECUTION, now)) {
            state = DENIED;
            throw new SecurityException("Authenticated runtime gate installation failed");
        }
    }

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

    static synchronized void revoke() {
        generation++;
        state = DENIED;
    }

    public static void require(final RuntimeDomain domain) {
        final GateState current = state;
        final long now = System.currentTimeMillis() / 1000L;
        if (domain == null || current.permit() == null || now >= current.hotExpiresAt()
                || !current.permit().verify(domain, now)) {
            throw new SecurityException("Authenticated runtime resource is unavailable");
        }
    }

    @VirtualizeExclude
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
