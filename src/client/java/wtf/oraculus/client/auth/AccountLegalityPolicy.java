package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

final class AccountLegalityPolicy {
    static final int DENY = 0;
    static final int ALLOW = 1;
    static final int TEMPORARY_FAILURE = 2;

    private AccountLegalityPolicy() {
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(structure = VMStructure.GRAPH, encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED, obfuscate = Toggle.ENABLED)
    )
    static boolean requiresProbe(final String moduleName) {
        return "Overlay".equals(moduleName) || "AntiKB".equals(moduleName) || "Velocity".equals(moduleName);
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(structure = VMStructure.REGISTER_BASED, encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED, obfuscate = Toggle.ENABLED)
    )
    static boolean shouldLimitFps(final AuthApiClient.LegalityResult result) {
        return result != null && result.ok() && !result.exists();
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(structure = VMStructure.GRAPH, encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED, obfuscate = Toggle.ENABLED)
    )
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
