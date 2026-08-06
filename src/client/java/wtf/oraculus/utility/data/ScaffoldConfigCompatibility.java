package wtf.oraculus.utility.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;

/**
 * Separates the removed legacy Scaffold configuration from the new Scaffold
 * implementation that was previously exposed as BlockFly.
 */
public final class ScaffoldConfigCompatibility {
    private static final String LEGACY_BLOCK_FLY_ID = "blockfly";
    private static final String SCAFFOLD_ID = "scaffold";

    private ScaffoldConfigCompatibility() {
    }

    public static JsonArray normalizeModules(final JsonArray modules) {
        final boolean hasMarkedScaffold = containsMarkedScaffold(modules);
        final JsonArray normalizedModules = new JsonArray();
        boolean emittedScaffold = false;

        for (final JsonElement element : modules) {
            if (!element.isJsonObject()) {
                normalizedModules.add(element);
                continue;
            }

            final JsonObject module = element.getAsJsonObject();
            final String moduleId = normalize(getString(module, "name", "id", "module"));
            if (moduleId.equals(SCAFFOLD_ID)) {
                if (!emittedScaffold && hasImplementationMarker(module)) {
                    normalizedModules.add(module.deepCopy());
                    emittedScaffold = true;
                }
                continue;
            }

            if (moduleId.equals(LEGACY_BLOCK_FLY_ID)) {
                if (!hasMarkedScaffold && !emittedScaffold) {
                    final JsonObject migrated = module.deepCopy();
                    setModuleId(migrated, SCAFFOLD_ID);
                    ensureImplementationMarker(migrated);
                    normalizedModules.add(migrated);
                    emittedScaffold = true;
                }
                continue;
            }

            normalizedModules.add(element);
        }

        return normalizedModules;
    }

    public static String normalizeBindingModuleId(final String moduleId) {
        return normalize(moduleId).equals(LEGACY_BLOCK_FLY_ID) ? SCAFFOLD_ID : moduleId;
    }

    private static boolean containsMarkedScaffold(final JsonArray modules) {
        for (final JsonElement element : modules) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject module = element.getAsJsonObject();
            if (normalize(getString(module, "name", "id", "module")).equals(SCAFFOLD_ID)
                    && hasImplementationMarker(module)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasImplementationMarker(final JsonObject module) {
        final JsonElement properties = module.get("properties");
        if (properties == null || !properties.isJsonArray()) {
            return false;
        }

        final String expected = normalize(ScaffoldSettings.IMPLEMENTATION_MARKER_ID);
        for (final JsonElement element : properties.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject property = element.getAsJsonObject();
            if (normalize(getString(property, "name", "id")).equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureImplementationMarker(final JsonObject module) {
        JsonArray properties;
        final JsonElement existingProperties = module.get("properties");
        if (existingProperties != null && existingProperties.isJsonArray()) {
            properties = existingProperties.getAsJsonArray();
        } else {
            properties = new JsonArray();
            module.add("properties", properties);
        }

        if (hasImplementationMarker(module)) {
            return;
        }

        final JsonObject marker = new JsonObject();
        marker.addProperty("name", ScaffoldSettings.IMPLEMENTATION_MARKER_ID);
        marker.addProperty("value", true);
        properties.add(marker);
    }

    private static void setModuleId(final JsonObject module, final String moduleId) {
        if (module.has("name")) {
            module.addProperty("name", moduleId);
        } else if (module.has("id")) {
            module.addProperty("id", moduleId);
        } else if (module.has("module")) {
            module.addProperty("module", moduleId);
        } else {
            module.addProperty("name", moduleId);
        }
    }

    private static String getString(final JsonObject object, final String... names) {
        for (final String name : names) {
            final JsonElement value = object.get(name);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return "";
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }
}
