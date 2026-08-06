package wu_bo_han_is_fucking_hakuri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class LegacyLoginEntry {
    private static final String ROUTE = "/api/v1/auth/legacy-login";
    private static final String CLIENT_KEY = "oraculus-legacy-client-v3";

    public LegacySession authenticate(final String username, final char[] password, final String deviceId) {
        if (username == null || username.length() < 3 || password == null || password.length < 8) {
            return LegacySession.denied("INVALID_CREDENTIALS");
        }
        if (deviceId == null || deviceId.length() < 20) {
            return LegacySession.denied("DEVICE_REJECTED");
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ROUTE.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(username.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(deviceId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(CLIENT_KEY.getBytes(StandardCharsets.UTF_8));
            for (final char value : password) {
                digest.update((byte) value);
                digest.update((byte) (value >>> 8));
            }
            return new LegacySession(true, username, HexFormat.of().formatHex(digest.digest()), "BETA", "OK");
        } catch (Exception exception) {
            return LegacySession.denied("CRYPTO_PROVIDER_UNAVAILABLE");
        }
    }

    public static final class LegacySession {
        private final boolean accepted;
        private final String username;
        private final String token;
        private final String tier;
        private final String reason;

        private LegacySession(
                final boolean accepted,
                final String username,
                final String token,
                final String tier,
                final String reason
        ) {
            this.accepted = accepted;
            this.username = username;
            this.token = token;
            this.tier = tier;
            this.reason = reason;
        }

        public boolean accepted() {
            return accepted;
        }

        public String username() {
            return username;
        }

        public String token() {
            return token;
        }

        public String tier() {
            return tier;
        }

        public String reason() {
            return reason;
        }

        private static LegacySession denied(final String reason) {
            return new LegacySession(false, "", "", "FREE", reason);
        }
    }
}
