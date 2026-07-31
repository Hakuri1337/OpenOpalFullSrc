package wtf.oraculus.client.music.api;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record LanzouShareLink(URI uri, String password) {
    private static final int MAX_PASSWORD_LENGTH = 64;

    public LanzouShareLink {
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new LanzouApiException("Invalid Lanzou share link");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new LanzouApiException("Lanzou share links must use HTTP or HTTPS");
        }
        if (!isLanzouHost(uri.getHost())) {
            throw new LanzouApiException("The link is not a Lanzou share link");
        }
        password = password == null ? "" : password.trim();
        if (password.length() > MAX_PASSWORD_LENGTH || password.chars().anyMatch(Character::isWhitespace)) {
            throw new LanzouApiException("Invalid Lanzou share password");
        }
    }

    public static LanzouShareLink parse(final String input) {
        if (input == null || input.isBlank()) {
            throw new LanzouApiException("Enter a Lanzou share link");
        }

        final String[] parts = input.trim().split("\\s+", 2);
        String rawLink = parts[0];
        if (!rawLink.contains(":")) rawLink = "https://" + rawLink;

        final URI parsed;
        try {
            parsed = URI.create(rawLink);
        } catch (final IllegalArgumentException exception) {
            throw new LanzouApiException("Invalid Lanzou share link", exception);
        }

        String password = parts.length == 2 ? parts[1].trim() : "";
        final String fragment = parsed.getRawFragment();
        if (password.isBlank() && fragment != null && !fragment.isBlank()) {
            try {
                password = decodePasswordFragment(fragment);
            } catch (final IllegalArgumentException exception) {
                throw new LanzouApiException("Invalid Lanzou share password", exception);
            }
        }

        try {
            final URI normalized = new URI(parsed.getScheme(), parsed.getAuthority(), parsed.getPath(), parsed.getQuery(), null);
            return new LanzouShareLink(normalized, password);
        } catch (final Exception exception) {
            throw new LanzouApiException("Invalid Lanzou share link", exception);
        }
    }

    private static String decodePasswordFragment(final String fragment) {
        final String value = fragment.startsWith("pwd=") || fragment.startsWith("password=")
                ? fragment.substring(fragment.indexOf('=') + 1) : fragment;
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isLanzouHost(final String host) {
        final String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.matches("(?:[a-z0-9-]+\\.)*lanzou[a-z]*\\.com");
    }
}
