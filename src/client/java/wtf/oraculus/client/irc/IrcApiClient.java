package wtf.oraculus.client.irc;

import com.google.gson.JsonObject;
import wtf.oraculus.client.ReleaseInfo;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class IrcApiClient {
    private static final URI BASE_URI = URI.create("https://auth.hakuri.tech/api/v1/irc/");
    private static final URI MINECRAFT_JOIN_URI =
            URI.create("https://sessionserver.mojang.com/session/minecraft/join");

    private final HttpClient client;

    IrcApiClient(final Executor executor) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build();
    }

    HttpResponse<InputStream> openStream(final String token) throws Exception {
        final HttpRequest request = request("stream", token)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        return this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    CompletableFuture<HttpResponse<String>> post(final String path, final JsonObject payload, final String token) {
        final HttpRequest request = request(path, token)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    CompletableFuture<Boolean> joinMinecraftSession(final String accessToken, final String profileId,
                                                    final String serverId) {
        final JsonObject payload = new JsonObject();
        payload.addProperty("accessToken", accessToken);
        payload.addProperty("selectedProfile", profileId);
        payload.addProperty("serverId", serverId);
        final HttpRequest request = HttpRequest.newBuilder(MINECRAFT_JOIN_URI)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Oraculus/" + ReleaseInfo.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() == 204);
    }

    private static HttpRequest.Builder request(final String path, final String token) {
        return HttpRequest.newBuilder(BASE_URI.resolve(path))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "Oraculus/" + ReleaseInfo.VERSION);
    }
}
