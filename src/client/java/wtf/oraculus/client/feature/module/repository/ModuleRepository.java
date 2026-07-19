package wtf.oraculus.client.feature.module.repository;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.UnknownModuleException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ModuleRepository {

    private final ClassToInstanceMap<Module> classToInstanceMap;
    private final ImmutableMap<String, Module> idToInstanceMap;
    private final ImmutableMap<String, Module> aliasToInstanceMap;

    private ModuleRepository(
            final ClassToInstanceMap<Module> classToInstanceMap,
            final ImmutableMap<String, Module> idToInstanceMap,
            final ImmutableMap<String, Module> aliasToInstanceMap
    ) {
        this.classToInstanceMap = classToInstanceMap;
        this.idToInstanceMap = idToInstanceMap;
        this.aliasToInstanceMap = aliasToInstanceMap;
    }

    public void findModule(final String id, final Consumer<Module> moduleConsumer, final Consumer<UnknownModuleException> exceptionHandler) {
        try {
            moduleConsumer.accept(getModule(id));
        } catch (UnknownModuleException e) {
            exceptionHandler.accept(e);
        }
    }

    public <T extends Module> T getModule(final Class<T> moduleClass) {
        return classToInstanceMap.getInstance(moduleClass);
    }

    public Module getModule(final String id) throws UnknownModuleException {
        Module module = idToInstanceMap.get(id);
        if (module == null) {
            module = aliasToInstanceMap.get(id.toLowerCase(Locale.ROOT));
        }
        if (module == null) {
            module = aliasToInstanceMap.get(normalize(id));
        }
        if (module == null)
            throw new UnknownModuleException(id);
        return module;
    }

    public Module getOptionalModule(final String id) {
        try {
            return getModule(id);
        } catch (UnknownModuleException ignored) {
            return null;
        }
    }

    public Collection<Module> getModules() {
        return classToInstanceMap.values();
    }

    public Collection<Module> getModulesInCategory(final ModuleCategory category) {
        return classToInstanceMap.values().stream()
                .filter(module -> category.equals(module.getCategory()))
                .collect(Collectors.toList());
    }

    public static ModuleRepository fromModules(final Module... modules) {
        final Builder builder = builder();
        for (final Module module : modules) {
            builder.register(module);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImmutableClassToInstanceMap.Builder<Module> classToInstanceMapBuilder = ImmutableClassToInstanceMap.builder();
        private final ImmutableMap.Builder<String, Module> idToInstanceMapBuilder = ImmutableMap.builder();
        private final Map<String, Module> aliasToInstanceMap = new LinkedHashMap<>();

        private Builder() {

        }

        @SuppressWarnings("unchecked")
        public Builder register(final Module module) {
            classToInstanceMapBuilder.put((Class<Module>) module.getClass(), module);
            idToInstanceMapBuilder.put(module.getId(), module);
            registerAlias(module.getId(), module);
            registerAlias(module.getName(), module);
            return this;
        }

        public ModuleRepository build() {
            return new ModuleRepository(
                    classToInstanceMapBuilder.build(),
                    idToInstanceMapBuilder.build(),
                    ImmutableMap.copyOf(aliasToInstanceMap)
            );
        }

        private void registerAlias(final String alias, final Module module) {
            if (alias == null || alias.isBlank()) {
                return;
            }

            aliasToInstanceMap.putIfAbsent(alias.toLowerCase(Locale.ROOT), module);

            final String normalizedAlias = normalize(alias);
            if (!normalizedAlias.isEmpty()) {
                aliasToInstanceMap.putIfAbsent(normalizedAlias, module);
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
}
