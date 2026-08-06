package wtf.oraculus.client.auth;

import tech.Oa7hTeam.obfuscate.vm.Virtualize;

@Virtualize
final class AccountLegalityPolicy {
    static final int DENY = 0;
    static final int ALLOW = 1;
    static final int TEMPORARY_FAILURE = 2;

    private AccountLegalityPolicy() {
    }

    static boolean requiresProbe(final String moduleName) {
        return "Overlay".equals(moduleName) || "AntiKB".equals(moduleName) || "Velocity".equals(moduleName);
    }

    static boolean shouldLimitFps(final AuthApiClient.LegalityResult result) {
        return result != null && result.ok() && !result.exists();
    }

    static int betaConnectionDecision(final AuthApiClient.LegalityResult result) {
        if (result == null || result.statusCode() == 429 || result.statusCode() >= 500) {
            return TEMPORARY_FAILURE;
        }
        if (!result.ok()) {
            return DENY;
        }
        return result.exists() && result.active() && result.betaEligible() ? ALLOW : DENY;
    }
}
