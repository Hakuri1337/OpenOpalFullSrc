package wtf.oraculus.client.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.client.edition.EditionBuildInfo;
import tech.Oa7hTeam.obfuscate.mark.Oa7hIndy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Oa7hIndy
public final class AuthApiClient {
    private static final URI BASE_URI = URI.create("https://auth.hakuri.tech/api/v1/");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final Executor executor;
    private final HttpClient client;

    public AuthApiClient(final Executor executor) {
        this.executor = executor;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build();
    }

    public CompletableFuture<ApiResult> login(final String username, final String password,
                                               final DeviceFingerprintProvider.Fingerprint fingerprint) {
        final JsonObject body = commonDeviceRequest(fingerprint);
        body.addProperty("username", username);
        body.addProperty("password", password);
        return send("auth/login", body, null);
    }

    public CompletableFuture<ApiResult> launcherLogin(
            final String username,
            final String accessToken,
            final DeviceFingerprintProvider.Fingerprint fingerprint
    ) {
        final JsonObject body = commonDeviceRequest(fingerprint);
        body.addProperty("username", username);
        return send("auth/launcher-login", body, accessToken);
    }

    public CompletableFuture<ApiResult> register(final String username, final String password,
                                                  final DeviceFingerprintProvider.Fingerprint fingerprint) {
        final JsonObject body = commonDeviceRequest(fingerprint);
        body.addProperty("username", username);
        body.addProperty("password", password);
        return send("auth/register", body, null);
    }

    public CompletableFuture<ApiResult> refresh(final String refreshToken,
                                                 final DeviceFingerprintProvider.Fingerprint fingerprint) {
        final JsonObject body = commonDeviceRequest(fingerprint);
        body.addProperty("refreshToken", refreshToken);
        return send("auth/refresh", body, null);
    }

    public CompletableFuture<ApiResult> heartbeat(final String accessToken) {
        return send("auth/heartbeat", new JsonObject(), accessToken);
    }

    public CompletableFuture<LegalityResult> legality(final String accessToken) {
        final HttpRequest request = request("auth/legality", new JsonObject(), accessToken);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(this::parseLegality, executor);
    }

    public CompletableFuture<ApiResult> logout(final String accessToken) {
        return send("auth/logout", new JsonObject(), accessToken);
    }

    private JsonObject commonDeviceRequest(final DeviceFingerprintProvider.Fingerprint fingerprint) {
        final JsonObject body = new JsonObject();
        body.addProperty("deviceFingerprint", fingerprint.value());
        body.addProperty("hwidVersion", fingerprint.version());
        body.addProperty("hwidQuality", fingerprint.quality());
        body.addProperty("edition", EditionBuildInfo.getApiName());
        body.addProperty("clientVersion", ReleaseInfo.VERSION);
        body.addProperty("buildId", ReleaseInfo.getBuildId());
        return body;
    }

    private CompletableFuture<ApiResult> send(final String relativePath, final JsonObject body,
                                               final String accessToken) {
        return client.sendAsync(request(relativePath, body, accessToken), HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(this::parse, executor);
    }

    private HttpRequest request(final String relativePath, final JsonObject body, final String accessToken) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(BASE_URI.resolve(relativePath))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Oraculus/" + ReleaseInfo.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (accessToken != null && !accessToken.isBlank())
            builder.header("Authorization", "Bearer " + accessToken);
        return builder.build();
    }

    private LegalityResult parseLegality(final HttpResponse<InputStream> response) {
        try (InputStream input = response.body()) {
            final byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES)
                throw new IOException("Authentication response exceeds the size limit");
            final JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Authentication response is not a JSON object");
            final JsonObject json = parsed.getAsJsonObject();
            return new LegalityResult(
                    bool(json, "Ok"),
                    bool(json, "Exists"),
                    bool(json, "Active"),
                    bool(json, "BetaEligible"),
                    string(json, "Username"),
                    string(json, "Tier"),
                    number(json, "ServerTimeUtc"),
                    string(json, "Error"),
                    string(json, "Message"),
                    string(json, "requestId"),
                    response.statusCode()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Authentication server returned an invalid legality response", exception);
        }
    }

    private ApiResult parse(final HttpResponse<InputStream> response) {
        try (InputStream input = response.body()) {
            final byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES)
                throw new IOException("Authentication response exceeds the size limit");
            final JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Authentication response is not a JSON object");
            final JsonObject json = parsed.getAsJsonObject();
            final JsonObject account = object(json, "Account");
            return new ApiResult(
                    bool(json, "Ok"),
                    string(json, "Error"),
                    string(json, "Message"),
                    string(json, "AccessToken"),
                    string(json, "RefreshToken"),
                    number(json, "AccessExpiresAt"),
                    number(json, "RefreshExpiresAt"),
                    string(json, "requestId"),
                    account == null ? "" : string(account, "id"),
                    account == null ? "" : string(account, "username"),
                    account == null ? "" : string(account, "tier"),
                    account == null ? null : nullableNumber(account, "betaExpiresAt"),
                    account == null ? "" : string(account, "hwidQuality"),
                    string(json, "EntitlementProof"),
                    response.statusCode()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("认证服务器返回了无效响应", exception);
        }
    }

    private static JsonObject object(final JsonObject json, final String name) {
        final JsonElement value = find(json, name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static boolean bool(final JsonObject json, final String name) {
        final JsonElement value = find(json, name);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static String string(final JsonObject json, final String name) {
        final JsonElement value = find(json, name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static long number(final JsonObject json, final String name) {
        final Long value = nullableNumber(json, name);
        return value == null ? 0L : value;
    }

    private static Long nullableNumber(final JsonObject json, final String name) {
        final JsonElement value = find(json, name);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }

    private static JsonElement find(final JsonObject json, final String name) {
        if (json.has(name)) return json.get(name);
        final String camel = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return json.has(camel) ? json.get(camel) : null;
    }

    public record ApiResult(
            boolean ok,
            String error,
            String message,
            String accessToken,
            String refreshToken,
            long accessExpiresAt,
            long refreshExpiresAt,
            String requestId,
            String accountId,
            String username,
            String tier,
            Long betaExpiresAt,
            String hwidQuality,
            String entitlementProof,
            int statusCode
    ) {
    }

    public record LegalityResult(
            boolean ok,
            boolean exists,
            boolean active,
            boolean betaEligible,
            String username,
            String tier,
            long serverTimeUtc,
            String error,
            String message,
            String requestId,
            int statusCode
    ) {
    }
}
