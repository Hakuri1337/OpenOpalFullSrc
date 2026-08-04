package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.SuperInstructionOptions;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.SuperInstructionMode;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;
import wtf.oraculus.client.edition.EditionBuildInfo;

final class AuthDecisionEvaluator {
    private AuthDecisionEvaluator() {
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.REGISTER_BASED,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            ),
            superInstructions = @SuperInstructionOptions(
                    enabled = Toggle.ENABLED, mode = SuperInstructionMode.HYBRID,
                    combineMin = 2, combineMax = 4, minFrequency = 2
            )
    )
    static SessionDecision evaluateSession(final AuthApiClient.ApiResult result,
                                           final DeviceFingerprintProvider.Fingerprint fingerprint,
                                           final long now) {
        if (result == null) return SessionDecision.MALFORMED_RESPONSE;
        if (!result.ok()) {
            return isTemporaryFailure(result)
                    ? SessionDecision.TEMPORARY_FAILURE
                    : SessionDecision.SERVER_REJECTED;
        }
        if (result.accessToken() == null || result.accessToken().isBlank()
                || result.refreshToken() == null || result.refreshToken().isBlank()) {
            return SessionDecision.MALFORMED_RESPONSE;
        }
        if (result.accessExpiresAt() <= now || result.refreshExpiresAt() <= now) {
            return SessionDecision.INVALID_ENTITLEMENT;
        }
        if (!EditionBuildInfo.isFree()
                && (!"BETA".equals(result.tier())
                || result.betaExpiresAt() == null
                || result.betaExpiresAt() <= now)) {
            return SessionDecision.INVALID_ENTITLEMENT;
        }
        if (!ServerEntitlementVerifier.verify(result, fingerprint, now)) {
            return SessionDecision.INVALID_SERVER_PROOF;
        }
        return SessionDecision.ACCEPT;
    }

    @Virtualize(
            integrityCheck = Toggle.ENABLED,
            vm = @VMOptions(
                    structure = VMStructure.REGISTER_BASED,
                    encrypt = Toggle.ENABLED,
                    shuffle = Toggle.ENABLED,
                    obfuscate = Toggle.ENABLED
            ),
            superInstructions = @SuperInstructionOptions(
                    enabled = Toggle.ENABLED, mode = SuperInstructionMode.HYBRID,
                    combineMin = 2, combineMax = 4, minFrequency = 2
            )
    )
    static boolean isTemporaryFailure(final AuthApiClient.ApiResult result) {
        return result != null
                && ("TEMPORARY_UNAVAILABLE".equals(result.error()) || result.statusCode() >= 500);
    }

    enum SessionDecision {
        ACCEPT,
        TEMPORARY_FAILURE,
        SERVER_REJECTED,
        MALFORMED_RESPONSE,
        INVALID_ENTITLEMENT,
        INVALID_SERVER_PROOF
    }
}
