package wtf.oraculus.client.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.client.edition.EditionBuildInfo;
import tech.Oa7hTeam.obfuscate.vm.Virtualize;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Virtualize
@Oa7hIndy
final class ServerEntitlementVerifier {
    private static final String KEY_ID = "Bf3yBGht-SsN9Hvi";
    private static final String PUBLIC_KEY_DER =
            "MCowBQYDK2VwAyEAy/fEZHAN9u1e/iPCZMjwJ8Ra3TPS7449CESmOKgrueE=";
    private static final PublicKey PUBLIC_KEY = loadPublicKey();
    private static final int MAX_PROOF_LENGTH = 16 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    private ServerEntitlementVerifier() {
    }

    static boolean verify(final AuthApiClient.ApiResult result,
                          final DeviceFingerprintProvider.Fingerprint fingerprint,
                          final long now) {
        if (result == null || fingerprint == null) return false;
        final String proof = safe(result.entitlementProof());
        if (proof.isBlank() || proof.length() > MAX_PROOF_LENGTH) return false;

        try {
            final String[] parts = proof.split("\\.", -1);
            if (parts.length != 2 || !base64Url(parts[0]) || !base64Url(parts[1])) return false;
            final byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            final byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
            if (payloadBytes.length == 0 || payloadBytes.length > MAX_PAYLOAD_BYTES
                    || signatureBytes.length != 64) return false;

            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(PUBLIC_KEY);
            verifier.update(parts[0].getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signatureBytes)) return false;

            final JsonElement parsed = JsonParser.parseString(new String(payloadBytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return false;
            final JsonObject claims = parsed.getAsJsonObject();
            final long issuedAt = integer(claims, "issuedAt");
            final long accessExpiresAt = integer(claims, "accessExpiresAt");
            final long refreshExpiresAt = integer(claims, "refreshExpiresAt");
            final Long betaExpiresAt = nullableInteger(claims, "betaExpiresAt");

            return integer(claims, "version") == 1L
                    && KEY_ID.equals(string(claims, "keyId"))
                    && safe(result.accountId()).equals(string(claims, "subject"))
                    && safe(result.username()).equals(string(claims, "username"))
                    && EditionBuildInfo.getApiName().equals(string(claims, "edition"))
                    && safe(result.tier()).equals(string(claims, "tier"))
                    && ReleaseInfo.VERSION.equals(string(claims, "clientVersion"))
                    && ReleaseInfo.getBuildId().equals(string(claims, "buildId"))
                    && constantEquals(sha256(fingerprint.value()), string(claims, "deviceFingerprintHash"))
                    && constantEquals(sha256(result.accessToken()), string(claims, "accessTokenHash"))
                    && issuedAt >= now - 300L && issuedAt <= now + 60L
                    && issuedAt < accessExpiresAt
                    && accessExpiresAt == result.accessExpiresAt()
                    && refreshExpiresAt == result.refreshExpiresAt()
                    && sameNullable(betaExpiresAt, result.betaExpiresAt());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static PublicKey loadPublicKey() {
        try {
            final byte[] encoded = Base64.getDecoder().decode(PUBLIC_KEY_DER);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String sha256(final String value) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(safe(value).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static boolean constantEquals(final String expected, final String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                safe(actual).getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean base64Url(final String value) {
        return !value.isEmpty() && value.length() <= MAX_PROOF_LENGTH
                && value.chars().allMatch(character -> character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '-' || character == '_');
    }

    private static String string(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid entitlement string claim: " + name);
        }
        return value.getAsString();
    }

    private static long integer(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?[0-9]+")) {
            throw new IllegalArgumentException("Invalid entitlement integer claim: " + name);
        }
        return value.getAsLong();
    }

    private static Long nullableInteger(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : integer(object, name);
    }

    private static boolean sameNullable(final Long left, final Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
