package wtf.oraculus.client.music.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LanzouApiClient {
    public static final URI DEFAULT_API_URI = URI.create("https://api.bugpk.com/api/lanzou");

    private final HttpClient httpClient;
    private final URI apiUri;

    public LanzouApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public LanzouApiClient(final HttpClient httpClient) {
        this(httpClient, DEFAULT_API_URI);
    }

    public LanzouApiClient(final HttpClient httpClient, final URI apiUri) {
        this.httpClient = httpClient;
        this.apiUri = apiUri;
    }

    public CompletableFuture<LanzouFile> resolve(final LanzouShareLink shareLink) {
        final StringBuilder query = new StringBuilder("url=")
                .append(URLEncoder.encode(shareLink.uri().toASCIIString(), StandardCharsets.UTF_8));
        if (!shareLink.password().isBlank()) {
            query.append("&pwd=").append(URLEncoder.encode(shareLink.password(), StandardCharsets.UTF_8));
        }

        final URI requestUri = URI.create(apiUri + "?" + query);
        final HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "Oraculus/1.0")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parseResponse);
    }

    private LanzouFile parseResponse(final HttpResponse<String> response) {
        if (response.statusCode() == 429) {
            throw new LanzouApiException("Lanzou API rate limited the request");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LanzouApiException("Lanzou API returned HTTP " + response.statusCode());
        }

        try {
            final JsonElement rootElement = JsonParser.parseString(response.body());
            if (!rootElement.isJsonObject()) throw new LanzouApiException("Lanzou API returned invalid JSON");
            final JsonObject root = rootElement.getAsJsonObject();
            final int code = number(root, "code");
            if (code != 200) {
                throw new LanzouApiException("Lanzou API returned code " + code + ": " + string(root, "msg"));
            }
            final JsonObject data = object(root.get("data"));
            final String url = string(data, "url");
            if (url.isBlank()) throw new LanzouApiException("Lanzou API returned no download URL");
            return new LanzouFile(string(data, "name"), string(data, "size"), string(data, "time"), URI.create(url));
        } catch (final LanzouApiException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            throw new LanzouApiException("Invalid JSON from Lanzou API", exception);
        }
    }

    private static JsonObject object(final JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(final JsonObject object, final String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        } catch (final RuntimeException ignored) {
            return "";
        }
    }

    private static int number(final JsonObject object, final String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }
}
