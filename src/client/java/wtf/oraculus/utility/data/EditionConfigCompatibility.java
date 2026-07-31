package wtf.oraculus.utility.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import wtf.oraculus.client.edition.EditionBuildInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps Beta-only settings intact while a shared config is opened and saved by
 * the Free distribution. The Free build never instantiates those modules.
 */
public final class EditionConfigCompatibility {
    private static final Map<String, PreservedConfig> PRESERVED_CONFIGS = new HashMap<>();
    private static final Map<Integer, List<JsonObject>> PRESERVED_BINDINGS = new LinkedHashMap<>();

    private EditionConfigCompatibility() {
    }

    public static void beginConfigLoad(final String configName) {
        if (configName != null) {
            PRESERVED_CONFIGS.remove(configName);
        }
    }

    public static void clearConfig(final String configName) {
        if (configName != null) {
            PRESERVED_CONFIGS.remove(configName);
        }
    }

    public static boolean shouldDeferModule(final JsonObject module) {
        if (!EditionBuildInfo.isFree()) {
            return false;
        }

        final String moduleId = normalize(getString(module, "name", "id", "module"));
        final Object mode = getPropertyValue(module, "mode");
        if (moduleId.equals("noslow")) {
            return isUnsupportedNoSlowMode(mode);
        }
        if (moduleId.equals("disabler")) {
            return isUnsupportedDisablerMode(mode);
        }
        if (moduleId.equals("overlay")) {
            return isUnsupportedTheme(getPropertyValue(module, "theme"));
        }
        if (moduleId.equals("antikb")) {
            return isUnsupportedVelocityMode(mode);
        }
        return false;
    }

    public static void deferModule(final String configName, final String moduleId,
                                   final JsonObject original, final JsonObject currentBaseline) {
        if (configName == null || !EditionBuildInfo.isFree()) {
            return;
        }
        final PreservedConfig config = PRESERVED_CONFIGS.computeIfAbsent(configName, ignored -> new PreservedConfig());
        config.deferredModules.put(normalize(moduleId), new DeferredModule(original.deepCopy(), currentBaseline.toString()));
    }

    public static void preserveUnknownModule(final String configName, final JsonObject original) {
        if (configName == null || !EditionBuildInfo.isFree()) {
            return;
        }
        final String id = normalize(getString(original, "name", "id", "module"));
        if (id.isEmpty()) {
            return;
        }
        final PreservedConfig config = PRESERVED_CONFIGS.computeIfAbsent(configName, ignored -> new PreservedConfig());
        config.unknownModules.put(id, original.deepCopy());
    }

    public static JsonArray mergeConfig(final String configName, final JsonArray currentModules) {
        if (configName == null || !EditionBuildInfo.isFree()) {
            return currentModules;
        }

        final PreservedConfig preserved = PRESERVED_CONFIGS.get(configName);
        if (preserved == null) {
            return currentModules;
        }

        final JsonArray merged = new JsonArray();
        final Map<String, Boolean> emitted = new HashMap<>();
        for (final JsonElement element : currentModules) {
            if (!element.isJsonObject()) {
                merged.add(element);
                continue;
            }

            final JsonObject current = element.getAsJsonObject();
            final String id = normalize(getString(current, "name", "id", "module"));
            final DeferredModule deferred = preserved.deferredModules.get(id);
            if (deferred != null && deferred.baselineJson.equals(current.toString())) {
                merged.add(deferred.original.deepCopy());
            } else {
                merged.add(current);
            }
            emitted.put(id, Boolean.TRUE);
        }

        for (final Map.Entry<String, JsonObject> entry : preserved.unknownModules.entrySet()) {
            if (!emitted.containsKey(entry.getKey())) {
                merged.add(entry.getValue().deepCopy());
            }
        }
        return merged;
    }

    public static void beginBindingsLoad() {
        PRESERVED_BINDINGS.clear();
    }

    public static void preserveUnknownBinding(final int keyCode, final JsonObject bindable) {
        if (!EditionBuildInfo.isFree() || !bindable.has("module")) {
            return;
        }
        PRESERVED_BINDINGS.computeIfAbsent(keyCode, ignored -> new ArrayList<>()).add(bindable.deepCopy());
    }

    public static void mergeBindings(final JsonArray bindings) {
        if (!EditionBuildInfo.isFree() || PRESERVED_BINDINGS.isEmpty()) {
            return;
        }

        final Map<Integer, JsonObject> byKey = new HashMap<>();
        for (final JsonElement element : bindings) {
            if (element.isJsonObject() && element.getAsJsonObject().has("keyCode")) {
                byKey.put(element.getAsJsonObject().get("keyCode").getAsInt(), element.getAsJsonObject());
            }
        }

        for (final Map.Entry<Integer, List<JsonObject>> entry : PRESERVED_BINDINGS.entrySet()) {
            JsonObject binding = byKey.get(entry.getKey());
            if (binding == null) {
                binding = new JsonObject();
                binding.addProperty("keyCode", entry.getKey());
                binding.add("bindables", new JsonArray());
                bindings.add(binding);
                byKey.put(entry.getKey(), binding);
            }

            final JsonArray bindables = binding.getAsJsonArray("bindables");
            for (final JsonObject preserved : entry.getValue()) {
                if (!containsJson(bindables, preserved)) {
                    bindables.add(preserved.deepCopy());
                }
            }
        }
    }

    private static boolean isUnsupportedNoSlowMode(final Object mode) {
        if (mode instanceof Number number) {
            // Legacy Beta enum order: VANILLA, WATCHDOG, UNIVERSAL, GRIM_JUMP, NO_C0F.
            return number.intValue() == 1 || number.intValue() == 4;
        }
        final String value = normalize(String.valueOf(mode));
        return value.equals("watchdog") || value.equals("hypixel") || value.equals("noc0f")
                || value.equals("grimfast") || value.equals("grimc0f")
                || value.equals("heypixel") || value.equals("heypixel3");
    }

    private static boolean isUnsupportedDisablerMode(final Object mode) {
        if (mode instanceof Number number) {
            // Legacy Beta enum order: HEYPIXEL, HYPIXEL_INVENTORY, CUBECRAFT, MINIBLOX.
            return number.intValue() == 0 || number.intValue() == 1;
        }
        final String value = normalize(String.valueOf(mode));
        return value.equals("heypixel") || value.equals("hypixelinventory");
    }

    private static boolean isUnsupportedTheme(final Object theme) {
        if (theme instanceof Number number) {
            return number.intValue() == 0;
        }
        final String value = normalize(String.valueOf(theme));
        return value.equals("sigma") || value.equals("sigmastyle");
    }

    private static boolean isUnsupportedVelocityMode(final Object mode) {
        if (mode instanceof Number number) {
            // Current Free modes end at JumpReset (ordinal 4). Older Beta
            // configurations may contain removed or Beta-only mode ordinals.
            return number.intValue() > 4;
        }
        final String value = normalize(String.valueOf(mode));
        return value.equals("attackreduce") || value.equals("noxz")
                || value.equals("hypixelreduce") || value.equals("intave");
    }

    private static Object getPropertyValue(final JsonObject module, final String propertyName) {
        final JsonElement properties = module.get("properties");
        if (properties == null || !properties.isJsonArray()) {
            return null;
        }
        for (final JsonElement element : properties.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject property = element.getAsJsonObject();
            if (!normalize(getString(property, "name", "id")).equals(normalize(propertyName))) {
                continue;
            }
            final JsonElement value = property.get("value");
            if (value == null || value.isJsonNull()) {
                return null;
            }
            if (value.isJsonPrimitive()) {
                final JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    return primitive.getAsNumber();
                }
                if (primitive.isString()) {
                    return primitive.getAsString();
                }
            }
            return value.toString();
        }
        return null;
    }

    private static boolean containsJson(final JsonArray values, final JsonObject expected) {
        final String expectedJson = expected.toString();
        for (final JsonElement value : values) {
            if (value.isJsonObject() && value.getAsJsonObject().toString().equals(expectedJson)) {
                return true;
            }
        }
        return false;
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

    private static final class PreservedConfig {
        private final Map<String, JsonObject> unknownModules = new LinkedHashMap<>();
        private final Map<String, DeferredModule> deferredModules = new LinkedHashMap<>();
    }

    private record DeferredModule(JsonObject original, String baselineJson) {
    }
}
