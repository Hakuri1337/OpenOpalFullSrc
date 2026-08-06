package wtf.oraculus.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.sun.jna.platform.win32.Crypt32Util;
import org.slf4j.Logger;
import wtf.oraculus.client.Constants;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

@Oa7hIndy
public final class AuthSessionStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path directory = Constants.DIRECTORY.toPath().resolve("auth");
    private final Path preferences = directory.resolve("client.json");
    private final Path session = directory.resolve("session.dat");

    public Optional<SavedSession> loadSession() {
        if (!isWindows() || !Files.isRegularFile(session)) return Optional.empty();
        try {
            final byte[] clear = Crypt32Util.cryptUnprotectData(Files.readAllBytes(session));
            final JsonObject json = JsonParser.parseString(new String(clear, StandardCharsets.UTF_8)).getAsJsonObject();
            final SavedSession saved = new SavedSession(
                    string(json, "username"),
                    string(json, "refreshToken"),
                    number(json, "refreshExpiresAt")
            );
            if (saved.refreshToken().length() < 32 || saved.refreshExpiresAt() <= System.currentTimeMillis() / 1000L)
                return Optional.empty();
            return Optional.of(saved);
        } catch (Throwable exception) {
            LOGGER.warn("Unable to read the protected Oraculus session; a password login is required");
            quarantineCorruptSession();
            return Optional.empty();
        }
    }

    public String loadUsername() {
        try {
            if (!Files.isRegularFile(preferences)) return "";
            return string(JsonParser.parseString(Files.readString(preferences)).getAsJsonObject(), "username");
        } catch (Exception ignored) {
            return "";
        }
    }

    public void save(final String username, final String refreshToken, final long refreshExpiresAt,
                     final boolean remember) {
        saveUsername(username);
        if (!remember || !isWindows()) {
            clearSession();
            return;
        }
        try {
            Files.createDirectories(directory);
            final JsonObject json = new JsonObject();
            json.addProperty("username", username);
            json.addProperty("refreshToken", refreshToken);
            json.addProperty("refreshExpiresAt", refreshExpiresAt);
            final byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    json.toString().getBytes(StandardCharsets.UTF_8));
            atomicWrite(session, protectedBytes);
        } catch (Throwable exception) {
            LOGGER.warn("Unable to save the protected Oraculus session; remember-login is disabled");
            clearSession();
        }
    }

    public void saveUsername(final String username) {
        try {
            Files.createDirectories(directory);
            final JsonObject json = new JsonObject();
            json.addProperty("username", username == null ? "" : username);
            atomicWrite(preferences, json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            LOGGER.warn("Unable to save Oraculus authentication preferences");
        }
    }

    public void clearSession() {
        try {
            Files.deleteIfExists(session);
        } catch (IOException exception) {
            LOGGER.warn("Unable to remove the local Oraculus session");
        }
    }

    public boolean supportsRememberLogin() {
        return isWindows();
    }

    private void quarantineCorruptSession() {
        try {
            if (Files.exists(session)) {
                Files.move(session, directory.resolve("session.corrupt-" + System.currentTimeMillis() + ".dat"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            clearSession();
        }
    }

    private static void atomicWrite(final Path target, final byte[] bytes) throws IOException {
        final Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String string(final JsonObject json, final String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : "";
    }

    private static long number(final JsonObject json, final String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsLong() : 0L;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public record SavedSession(String username, String refreshToken, long refreshExpiresAt) {
    }
}
