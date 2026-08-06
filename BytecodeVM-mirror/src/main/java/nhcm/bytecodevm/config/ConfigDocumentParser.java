package nhcm.bytecodevm.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Safely parses YAML configuration documents. */
final class ConfigDocumentParser
{
    private ConfigDocumentParser()
    {
    }

    static Map<String, Object> parse(String document)
    {
        rejectJsonSyntax(document);
        return parseYaml(document);
    }

    static Map<String, Object> parse(String document, Path source)
    {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".yaml") && !name.endsWith(".yml"))
        {
            throw new IllegalArgumentException(
                    "Unsupported config format for " + source.getFileName() +
                            "; use a .yml or .yaml file");
        }
        return parse(document);
    }

    private static Map<String, Object> parseYaml(String document)
    {
        Object root;
        try
        {
            root = newYaml().load(document);
        }
        catch (RuntimeException exception)
        {
            throw new IllegalArgumentException("Invalid YAML configuration", exception);
        }
        if (!(root instanceof Map<?, ?> rootMap))
        {
            throw new IllegalArgumentException("YAML configuration root must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rootMap.entrySet())
        {
            if (!(entry.getKey() instanceof String key))
            {
                throw new IllegalArgumentException("YAML configuration keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Yaml newYaml()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(32);
        options.setNestingDepthLimit(64);
        return new Yaml(new SafeConstructor(options));
    }

    private static void rejectJsonSyntax(String document)
    {
        for (String line : document.split("\\R"))
        {
            String content = line.stripLeading();
            if (content.isEmpty() || content.startsWith("#") || content.equals("---"))
            {
                continue;
            }
            char value = content.charAt(0);
            if (value == '{' || value == '[')
            {
                throw new IllegalArgumentException("JSON configuration is not supported; use YAML");
            }
            return;
        }
        throw new IllegalArgumentException("Configuration document is empty");
    }
}
