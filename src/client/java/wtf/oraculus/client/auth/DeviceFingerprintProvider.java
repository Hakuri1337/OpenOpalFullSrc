package wtf.oraculus.client.auth;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import wtf.oraculus.client.Constants;
import tech.Oa7hTeam.obfuscate.vm.Virtualize;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Virtualize
@Oa7hIndy
public final class DeviceFingerprintProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERSION = "v1";

    public Fingerprint collect() {
        final Map<String, String> fields = new TreeMap<>();
        try {
            final HardwareAbstractionLayer hardware = new SystemInfo().getHardware();
            final ComputerSystem system = hardware.getComputerSystem();
            put(fields, "BASEBOARD_SERIAL", system.getBaseboard().getSerialNumber());
            put(fields, "HARDWARE_UUID", system.getHardwareUUID());
            put(fields, "SYSTEM_SERIAL", system.getSerialNumber());
            put(fields, "PROCESSOR_ID",
                    hardware.getProcessor().getProcessorIdentifier().getProcessorID());
        } catch (Throwable exception) {
            LOGGER.warn("Unable to collect one or more hardware identity fields; using a safe fallback when needed");
        }

        final String quality;
        if (fields.size() >= 2) {
            quality = "STRONG";
        } else if (fields.size() == 1) {
            quality = "DEGRADED";
        } else {
            quality = "FALLBACK";
            fields.put("INSTALLATION_ID", installationId());
        }

        final StringBuilder canonical = new StringBuilder("oraculus-hwid-v1\0");
        fields.forEach((key, value) -> canonical.append(key).append('=').append(value).append('\n'));
        return new Fingerprint(sha256(canonical.toString()), VERSION, quality,
                quality + " · " + fields.size() + " 个稳定字段");
    }

    private static void put(final Map<String, String> fields, final String name, final String raw) {
        final String value = normalize(raw);
        if (!value.isEmpty() && !isPlaceholder(value)) fields.put(name, value);
    }

    private static String normalize(final String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toUpperCase()
                .replaceAll("\\s+", "")
                .trim();
    }

    private static boolean isPlaceholder(final String value) {
        return value.isEmpty()
                || value.equals("UNKNOWN")
                || value.equals("NONE")
                || value.equals("NOTSPECIFIED")
                || value.equals("TOBEFILLEDBYO.E.M.")
                || value.matches("0+")
                || value.equals("00000000-0000-0000-0000-000000000000");
    }

    private static String installationId() {
        final Path directory = Constants.DIRECTORY.toPath().resolve("auth");
        final Path path = directory.resolve("device.id");
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(path)) {
                final String existing = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) return existing;
            }
            final String generated = UUID.randomUUID().toString();
            Files.writeString(path, generated, StandardCharsets.UTF_8);
            return generated;
        } catch (IOException exception) {
            LOGGER.warn("Unable to persist the fallback device identity");
            return UUID.randomUUID().toString();
        }
    }

    private static String sha256(final String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Fingerprint(String value, String version, String quality, String detail) {
    }
}
