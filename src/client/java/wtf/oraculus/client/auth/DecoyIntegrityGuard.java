package wtf.oraculus.client.auth;

import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DecoyIntegrityGuard {
    public static final String DIGEST_MARKER = "ORACULUS_DECOY_DIGEST_V1_REPLACE_DURING_RELEASE";
    private static final String[] ENTRIES = {
            "wu_bo_han_is_fucking_hakuri/LegacyLoginEntry.class",
            "wu_bo_han_is_fucking_hakuri/LegacyLoginEntry$LegacySession.class",
            "wu_bo_han_is_fucking_hakuri/SessionBootstrap.class"
    };
    private static final AtomicBoolean VERIFIED = new AtomicBoolean();

    private DecoyIntegrityGuard() {
    }

    public static void verify() {
        if (VERIFIED.get()) {
            return;
        }
        synchronized (DecoyIntegrityGuard.class) {
            if (VERIFIED.get()) {
                return;
            }
            if (!acceptDigest(digestEntries())) {
                throw new IllegalStateException("Failed to initialize client resource index");
            }
            VERIFIED.set(true);
        }
    }

    private static String digestEntries() {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final ClassLoader loader = DecoyIntegrityGuard.class.getClassLoader();
            final byte[] buffer = new byte[8192];
            for (final String entry : ENTRIES) {
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream input = loader.getResourceAsStream(entry)) {
                    if (input == null) {
                        return "";
                    }
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return "";
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
    private static boolean acceptDigest(final String actual) {
        final String expected = DIGEST_MARKER;
        if (actual == null || actual.length() != expected.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < expected.length(); index++) {
            difference |= actual.charAt(index) ^ expected.charAt(index);
        }
        return difference == 0;
    }
}
