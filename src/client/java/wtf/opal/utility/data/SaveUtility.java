package wtf.opal.utility.data;

import com.google.gson.*;
import com.ibm.icu.impl.Pair;
import wtf.opal.client.OpalClient;
import wtf.opal.client.binding.BindingService;
import wtf.opal.client.binding.IBindable;
import wtf.opal.client.binding.type.InputType;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.UnknownModuleException;
import wtf.opal.client.feature.module.DeprecatedModule;
import wtf.opal.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.opal.client.feature.module.property.Property;

import wtf.opal.utility.data.serializer.PairSerializer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static wtf.opal.client.Constants.DIRECTORY;


public final class SaveUtility {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Pair.class, new PairSerializer())
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private static final BindingService BINDING_SERVICE = OpalClient.getInstance().getBindRepository().getBindingService();
    private static final File CONFIG_DIRECTORY = new File(DIRECTORY, "configs");

    private static final AtomicInteger AUTO_SAVE_SUPPRESSION_DEPTH = new AtomicInteger();

    private SaveUtility() {
    }

    private static void ensureDirectories() throws IOException {
        Files.createDirectories(DIRECTORY.toPath());
        Files.createDirectories(CONFIG_DIRECTORY.toPath());
    }

    private static String sanitizeConfigName(final String name) {
        if (name == null) {
            return "";
        }

        final String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        final StringBuilder builder = new StringBuilder(normalizedName.length());
        for (int i = 0; i < normalizedName.length(); i++) {
            final char character = normalizedName.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '_' || character == '-' || character == '.') {
                builder.append(character);
            } else if (Character.isWhitespace(character)) {
                builder.append('_');
            }
        }

        return builder.toString();
    }

    private static Path getConfigPath(final String name) {
        return new File(CONFIG_DIRECTORY, sanitizeConfigName(name) + ".json").toPath();
    }

    public static void saveBindings() {
        try {
            if (!DIRECTORY.exists()) {
                DIRECTORY.mkdir();
            }

            final File file = new File(DIRECTORY, "bindings.json");

            final JsonArray bindingsArray = new JsonArray();
            for (final Pair<Integer, InputType> binding : BINDING_SERVICE.getBindingMap().keySet()) {
                final JsonObject bindingJson = new JsonObject();
                bindingJson.addProperty("keyCode", binding.first);

                JsonArray bindablesArray = new JsonArray();
                for (IBindable bindable : BINDING_SERVICE.getBindingMap().get(binding)) {
                    if (bindable instanceof Module module) {
                        JsonObject moduleJson = new JsonObject();
                        moduleJson.addProperty("module", module.getId());
                        bindablesArray.add(moduleJson);
                    } else if (bindable instanceof Config config) {
                        JsonObject configJson = new JsonObject();
                        configJson.addProperty("config", config.getName());
                        bindablesArray.add(configJson);
                    }
                }
                bindingJson.add("bindables", bindablesArray);

                bindingsArray.add(bindingJson);
            }

            Files.writeString(
                    file.toPath(),
                    GSON.toJson(bindingsArray)
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadBindings() {
        final File file = new File(DIRECTORY, "bindings.json");
        if (!file.exists()) {
            return;
        }

        try (final FileReader reader = new FileReader(file)) {
            final JsonArray bindingsArray = JsonParser.parseReader(reader).getAsJsonArray();

            for (final JsonElement bindingElement : bindingsArray) {
                final JsonObject bindingJson = bindingElement.getAsJsonObject();

                final int keyCode = bindingJson.get("keyCode").getAsInt();
                final InputType inputType = keyCode < 10 ? InputType.MOUSE : InputType.KEYBOARD;

                final JsonArray bindablesArray = bindingJson.getAsJsonArray("bindables");
                for (final JsonElement bindableElement : bindablesArray) {
                    final JsonObject bindableJson = bindableElement.getAsJsonObject();

                    if (bindableJson.has("module")) {
                        final String moduleID = bindableJson.get("module").getAsString();
                        final Module module = OpalClient.getInstance().getModuleRepository().getModule(moduleID);
                        BINDING_SERVICE.register(keyCode, module, inputType);
                    } else if (bindableJson.has("config")) {
                        final String configName = bindableJson.get("config").getAsString();
                        final Config config = new Config(configName);

                        BINDING_SERVICE.register(keyCode, config, inputType);
                    }
                }
            }
        } catch (IOException | UnknownModuleException e) {
            e.printStackTrace();
        }
    }

    public static boolean saveConfig(final String name) {
        final String normalizedName = sanitizeConfigName(name);
        if (normalizedName.isEmpty()) {
            return false;
        }

        try {
            ensureDirectories();

            final Path configPath = getConfigPath(normalizedName);
            final String json = GSON.toJson(OpalClient.getInstance().getModuleRepository().getModules());
            Files.writeString(configPath, json);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean loadConfigFile(final String name) {
        final String normalizedName = sanitizeConfigName(name);
        if (normalizedName.isEmpty()) {
            return false;
        }

        final Path configPath = getConfigPath(normalizedName);
        if (!Files.exists(configPath)) {
            return false;
        }

        try {
            final String jsonString = Files.readString(configPath);
            return applyConfigJson(jsonString);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean deleteConfig(final String name) {
        final String normalizedName = sanitizeConfigName(name);
        if (normalizedName.isEmpty()) {
            return false;
        }

        try {
            return Files.deleteIfExists(getConfigPath(normalizedName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<String> listConfigs() {
        try {
            ensureDirectories();
            try (var stream = Files.list(CONFIG_DIRECTORY.toPath())) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(fileName -> fileName.endsWith(".json"))
                        .map(fileName -> fileName.substring(0, fileName.length() - 5))
                        .sorted()
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void autoSaveDefaultConfig() {
        final OpalClient client = OpalClient.getInstance();
        if (!client.isPostInitialization() || isAutoSaveSuppressed()) {
            return;
        }
        saveConfig("default");
    }

    public static boolean isAutoSaveSuppressed() {
        return AUTO_SAVE_SUPPRESSION_DEPTH.get() > 0;
    }

    public static String captureConfigJson() {
        return GSON.toJson(OpalClient.getInstance().getModuleRepository().getModules());
    }

    public static boolean applyConfigJson(final String jsonString) {
        try (AutoSaveScope ignored = suppressAutoSave()) {
            return applyConfig(jsonString);
        }
    }

    public static AutoSaveScope suppressAutoSave() {
        AUTO_SAVE_SUPPRESSION_DEPTH.incrementAndGet();
        return new AutoSaveScope();
    }

    private static boolean applyConfig(final String jsonString) {
        try {
            final JsonElement rootElement = JsonParser.parseString(jsonString);
            if (!rootElement.isJsonArray()) {
                return false;
            }

            for (final JsonElement jsonModuleElement : rootElement.getAsJsonArray()) {
                if (!jsonModuleElement.isJsonObject()) {
                    continue;
                }

                final JsonObject jsonModule = jsonModuleElement.getAsJsonObject();
                final String jsonModuleID = getString(jsonModule, "name", "id", "module");
                final Module clientModule;
                final Boolean jsonEnabled = getBoolean(jsonModule.get("enabled"));
                final Boolean jsonVisible = getBoolean(jsonModule.get("visible"));

                try {
                    clientModule = OpalClient.getInstance().getModuleRepository().getModule(jsonModuleID);
                } catch (UnknownModuleException ignored) {
                    continue;
                }

                if (jsonEnabled != null && jsonEnabled != clientModule.isEnabled()) {
                    clientModule.setEnabled(jsonEnabled);
                }
                // Deprecated modules: only .deprecated_modules toggle controls ClickGUI visibility, not config
                if (!(clientModule instanceof DeprecatedModule)) {
                    if (jsonVisible != null && jsonVisible != clientModule.isVisible()) {
                        clientModule.setVisible(jsonVisible);
                    }
                }

                final JsonElement jsonPropertiesElement = jsonModule.get("properties");
                if (jsonPropertiesElement == null || !jsonPropertiesElement.isJsonArray()) {
                    continue;
                }

                for (final JsonElement jsonPropertyElement : jsonPropertiesElement.getAsJsonArray()) {
                    if (!jsonPropertyElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject jsonProperty = jsonPropertyElement.getAsJsonObject();
                    final String propertyName = getString(jsonProperty, "name", "id");
                    final Object propertyValue = GSON.fromJson(jsonProperty.get("value"), Object.class);
                    final Property<?> clientProperty = findProperty(clientModule, propertyName);
                    if (clientProperty != null) {
                        applyPropertyValue(clientProperty, propertyValue);
                        continue;
                    }

                    applyLegacyPropertyValue(clientModule, propertyName, propertyValue);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void applyPropertyValue(final Property<?> property, final Object propertyValue) {
        try {
            property.applyValue(propertyValue);
        } catch (RuntimeException exception) {
            exception.printStackTrace();
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

    private static Boolean getBoolean(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            final JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                return Boolean.parseBoolean(primitive.getAsString());
            }
        }
        return null;
    }

    private static Property<?> findProperty(final Module module, final String propertyName) {
        final String normalizedPropertyName = normalize(propertyName);
        for (final Property<?> property : module.getPropertyList()) {
            if (normalize(property.getId()).equals(normalizedPropertyName)
                    || normalize(property.getName()).equals(normalizedPropertyName)) {
                return property;
            }
        }
        return null;
    }

    private static void applyLegacyPropertyValue(final Module module, final String propertyName, final Object propertyValue) {
        if (module instanceof NoSlowModule noSlowModule) {
            if (normalize(propertyName).equals("mode") && noSlowModule.applyLegacyModeValue(propertyValue)) {
                return;
            }

            if (noSlowModule.isLegacyKeepSprintingProperty(propertyName)) {
                noSlowModule.applyLegacyKeepSprintingValue(propertyValue);
            }
        }
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

    public static final class AutoSaveScope implements AutoCloseable {
        private boolean closed;

        private AutoSaveScope() {
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            AUTO_SAVE_SUPPRESSION_DEPTH.updateAndGet(depth -> Math.max(0, depth - 1));
        }
    }

}
