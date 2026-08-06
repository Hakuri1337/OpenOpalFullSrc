package nhcm.bytecodevm.generator;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.utils.RandomUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GeneratedMemberNamer
{
    public static final GeneratedMemberNamer DISABLED = new GeneratedMemberNamer(false);

    private static final char[] NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final boolean enabled;
    private final Set<String> classNames = new HashSet<>();
    private final Map<String, Set<String>> fieldNamesByOwner = new HashMap<>();
    private final Map<String, Set<String>> methodKeysByOwner = new HashMap<>();

    public GeneratedMemberNamer(BytecodeVMConfig config)
    {
        this(config.renameMode == BytecodeVMConfig.RenameMode.ENABLE);
    }

    private GeneratedMemberNamer(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean enabled()
    {
        return enabled;
    }

    public void reserveClassNames(Iterable<String> names)
    {
        if (!enabled)
        {
            return;
        }
        for (String name : names)
        {
            reserveClassName(name);
        }
    }

    public String className(String location, String originalName)
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(originalName, "originalName");
        String normalizedLocation = normalizePath(location);
        String normalizedName = normalizePath(originalName);
        String packageName = packageName(normalizedLocation, normalizedName);
        if (!enabled)
        {
            return join(packageName, simpleName(normalizedName));
        }

        String name;
        do
        {
            name = join(packageName, randomName());
        } while (classNames.contains(name));
        classNames.add(name);
        return name;
    }

    public String field(String owner, String originalName)
    {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(originalName, "originalName");
        Set<String> used = fieldNamesByOwner.computeIfAbsent(owner, ignored -> new HashSet<>());
        if (!enabled)
        {
            return originalName;
        }
        return randomUnusedName(used);
    }

    public String method(String owner, String originalName, String descriptor)
    {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(descriptor, "descriptor");
        if (isSpecialMethod(originalName))
        {
            if (enabled)
            {
                reserveMethod(owner, originalName, descriptor);
            }
            return originalName;
        }
        if (!enabled)
        {
            return originalName;
        }

        Set<String> used = methodKeysByOwner.computeIfAbsent(owner, ignored -> new HashSet<>());
        String name;
        do
        {
            name = randomName();
        } while (used.contains(methodKey(name, descriptor)));
        used.add(methodKey(name, descriptor));
        return name;
    }

    public String reserveMethodName(String owner, String name, String descriptor)
    {
        if (enabled)
        {
            reserveMethod(owner, name, descriptor);
        }
        return name;
    }

    private void reserveMethod(String owner, String name, String descriptor)
    {
        Set<String> used = methodKeysByOwner.computeIfAbsent(owner, ignored -> new HashSet<>());
        reserve(used, methodKey(name, descriptor), "method", owner);
    }

    private static void reserve(Set<String> used, String key, String type, String owner)
    {
        if (!used.add(key))
        {
            throw new IllegalStateException("Generated " + type + " name is already occupied in " + owner + ": " + key);
        }
    }

    private static String randomUnusedName(Set<String> used)
    {
        String name;
        do
        {
            name = randomName();
        } while (used.contains(name));
        used.add(name);
        return name;
    }

    private static String randomName()
    {
        int length = 3 + RandomUtils.randomInt(5);
        StringBuilder name = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            name.append(NAME_CHARS[RandomUtils.randomInt(NAME_CHARS.length)]);
        }
        return name.toString();
    }

    private void reserveClassName(String name)
    {
        reserve(classNames, normalizePath(name), "class", "<jar>");
    }

    private static String packageName(String location, String name)
    {
        String namePackage = "";
        int slash = name.lastIndexOf('/');
        if (slash >= 0)
        {
            namePackage = name.substring(0, slash);
        }
        if (!namePackage.isEmpty())
        {
            return namePackage;
        }
        return location;
    }

    private static String simpleName(String name)
    {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private static String join(String packageName, String simpleName)
    {
        return packageName == null || packageName.isEmpty()
                ? simpleName
                : packageName + '/' + simpleName;
    }

    private static String normalizePath(String value)
    {
        String normalized = value.replace('.', '/');
        while (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isSpecialMethod(String name)
    {
        return "<init>".equals(name) || "<clinit>".equals(name);
    }

    private static String methodKey(String name, String descriptor)
    {
        return name + descriptor;
    }
}
