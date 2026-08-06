package wtf.oraculus.client.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

@Oa7hIndy
final class LauncherAccountStore {
    private static final int MAX_FILE_BYTES = 16 * 1024;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("[A-Za-z0-9_-]{32,512}");

    LoadResult load() {
        final Path file = accountFile();
        if (file == null || !Files.isRegularFile(file)) {
            return LoadResult.absent();
        }
        try {
            final byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
                return LoadResult.invalid();
            }
            final JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return LoadResult.invalid();
            }
            final JsonObject json = parsed.getAsJsonObject();
            final String username = string(json, "accname").trim();
            final String accessToken = string(json, "pswd");
            if (!USERNAME.matcher(username).matches() || !ACCESS_TOKEN.matcher(accessToken).matches()) {
                return LoadResult.invalid();
            }
            return LoadResult.ready(new LauncherAccount(username, accessToken));
        } catch (Throwable ignored) {
            return LoadResult.invalid();
        }
    }

    private static Path accountFile() {
        try {
            String temporaryDirectory = System.getenv("TEMP");
            if (temporaryDirectory == null || temporaryDirectory.isBlank()) {
                temporaryDirectory = System.getProperty("java.io.tmpdir", "");
            }
            return temporaryDirectory.isBlank()
                    ? null
                    : Path.of(temporaryDirectory).resolve("oraaculus").resolve("acc.json");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String string(final JsonObject json, final String name) {
        if (!json.has(name)) {
            return "";
        }
        final JsonElement value = json.get(name);
        return value != null && !value.isJsonNull() && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    record LauncherAccount(String username, String accessToken) {
    }

    record LoadResult(Status status, LauncherAccount account) {
        static LoadResult absent() {
            return new LoadResult(Status.ABSENT, null);
        }

        static LoadResult invalid() {
            return new LoadResult(Status.INVALID, null);
        }

        static LoadResult ready(final LauncherAccount account) {
            return new LoadResult(Status.READY, account);
        }
    }

    enum Status {
        ABSENT,
        INVALID,
        READY
    }
}
