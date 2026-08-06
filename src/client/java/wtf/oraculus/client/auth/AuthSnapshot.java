package wtf.oraculus.client.auth;

import tech.Oa7hTeam.obfuscate.vm.Virtualize;

public record AuthSnapshot(
        AuthState state,
        String message,
        String requestId,
        String username,
        String tier,
        Long betaExpiresAt,
        String hwidQuality
) {
    public static AuthSnapshot initial() {
        return new AuthSnapshot(AuthState.UNINITIALIZED, "正在初始化认证组件", "", "", "", null, "");
    }

    @Virtualize
    public boolean allowsRuntime() {
        return state == AuthState.AUTHENTICATED
                || state == AuthState.RUNTIME_STARTING
                || state == AuthState.READY
                || state == AuthState.NETWORK_GRACE;
    }
}
