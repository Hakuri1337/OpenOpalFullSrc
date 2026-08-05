package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.SuperInstructionOptions;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.SuperInstructionMode;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.client.edition.EditionBuildInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RuntimePermit {
    private final String edition;
    private final String buildId;
    private final String username;
    private final String fingerprintDigest;
    private final String sessionDigest;
    private final long issuedAt;
    private final long accessExpiresAt;
    private final long refreshExpiresAt;
    private final String[] domainSeals;

    private RuntimePermit(final String edition, final String buildId, final String username,
                          final String fingerprintDigest, final String sessionDigest,
                          final long issuedAt, final long accessExpiresAt, final long refreshExpiresAt,
                          final String[] domainSeals) {
        this.edition = edition;
        this.buildId = buildId;
        this.username = username;
        this.fingerprintDigest = fingerprintDigest;
        this.sessionDigest = sessionDigest;
        this.issuedAt = issuedAt;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.domainSeals = domainSeals.clone();
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
                    enabled = Toggle.ENABLED,
                    mode = SuperInstructionMode.HYBRID,
                    combineMin = 2,
                    combineMax = 4,
                    minFrequency = 2
            )
    )
    static RuntimePermit issue(final AuthApiClient.ApiResult result,
                               final DeviceFingerprintProvider.Fingerprint fingerprint,
                               final long now) {
        if (AuthDecisionEvaluator.evaluateSession(result, fingerprint, now)
                != AuthDecisionEvaluator.SessionDecision.ACCEPT || fingerprint == null) {
            throw new SecurityException("Cannot issue an unauthenticated runtime permit");
        }

        final String edition = EditionBuildInfo.getApiName();
        final String buildId = ReleaseInfo.getBuildId();
        final String username = safe(result.username());
        final String fingerprintDigest = digest(safe(fingerprint.value()));
        final String sessionDigest = digest(result.accessToken() + '\0' + result.refreshToken()
                + '\0' + result.entitlementProof());
        final String common = canonical(edition, buildId, username, fingerprintDigest, sessionDigest,
                now, result.accessExpiresAt(), result.refreshExpiresAt());
        final RuntimeDomain[] domains = RuntimeDomain.values();
        final String[] seals = new String[domains.length];
        for (int index = 0; index < domains.length; index++) {
            seals[index] = seal(domains[index].sealDomain(), common);
        }
        return new RuntimePermit(edition, buildId, username, fingerprintDigest, sessionDigest,
                now, result.accessExpiresAt(), result.refreshExpiresAt(), seals);
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
                    enabled = Toggle.ENABLED,
                    mode = SuperInstructionMode.HYBRID,
                    combineMin = 2,
                    combineMax = 4,
                    minFrequency = 2
            )
    )
    boolean verify(final RuntimeDomain domain, final long now) {
        if (now < issuedAt - 30L || now >= accessExpiresAt || now >= refreshExpiresAt) return false;
        if (!EditionBuildInfo.getApiName().equals(edition) || !ReleaseInfo.getBuildId().equals(buildId)) return false;
        if (domain == null || username.isBlank() || fingerprintDigest.isBlank() || sessionDigest.isBlank()) return false;
        if (domainSeals.length != RuntimeDomain.values().length) return false;

        final String common = canonical(edition, buildId, username, fingerprintDigest, sessionDigest,
                issuedAt, accessExpiresAt, refreshExpiresAt);
        final String expected = seal(domain.sealDomain(), common);
        final String actual = domainSeals[domain.ordinal()];
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    long accessExpiresAt() {
        return accessExpiresAt;
    }

    long refreshExpiresAt() {
        return refreshExpiresAt;
    }

    private static String canonical(final String edition, final String buildId, final String username,
                                    final String fingerprintDigest, final String sessionDigest,
                                    final long issuedAt, final long accessExpiresAt,
                                    final long refreshExpiresAt) {
        return edition + '\0' + buildId + '\0' + username + '\0' + fingerprintDigest + '\0'
                + sessionDigest + '\0' + issuedAt + '\0' + accessExpiresAt + '\0' + refreshExpiresAt;
    }

    private static String seal(final String domain, final String value) {
        return digest("0r4c" + domain + '\0' + value + "u1us-b8");
    }

    private static String digest(final String value) {
        try {
            final byte[] result = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(result);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
