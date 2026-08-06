package wtf.oraculus.client.irc;

import tech.Oa7hTeam.obfuscate.vm.Virtualize;

import java.util.Locale;

@Virtualize
public record IrcUser(
        String username,
        String tier,
        String role,
        String minecraftProfileId,
        String minecraftProfileName,
        String presence,
        boolean profileVerified,
        long connectedAtUtc
) {
    public IrcUser {
        username = safe(username);
        tier = safe(tier).toUpperCase(Locale.ROOT);
        role = safe(role).toUpperCase(Locale.ROOT);
        minecraftProfileId = normalizeUuid(minecraftProfileId);
        minecraftProfileName = safe(minecraftProfileName);
        presence = safe(presence).toUpperCase(Locale.ROOT);
    }

    public static String normalizeUuid(final String value) {
        return safe(value).replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
