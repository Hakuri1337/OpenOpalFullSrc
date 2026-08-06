package wtf.oraculus.client.auth;

import wtf.oraculus.client.edition.EditionBuildInfo;
import tech.Oa7hTeam.obfuscate.vm.Virtualize;

@Virtualize
final class AuthDecisionEvaluator {
    private AuthDecisionEvaluator() {
    }

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
