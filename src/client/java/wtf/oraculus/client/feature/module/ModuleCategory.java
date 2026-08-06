package wtf.oraculus.client.feature.module;

import wtf.oraculus.utility.misc.INameable;

public enum ModuleCategory implements INameable {
    COMBAT("Combat", "\ue9e0"),
    MOVEMENT("Movement", "\ue566"),
    VISUAL("Visual", "\ue8f4"),
    WORLD("World", "\ue80b"),
    UTILITY("Utility", "\uea3c");

    private final String name, icon;
    private int moduleIndex;

    ModuleCategory(final String name, final String icon) {
        this.name = name;
        this.icon = icon;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public void setModuleIndex(final int moduleIndex) {
        this.moduleIndex = moduleIndex;
    }

    public int getModuleIndex() {
        return moduleIndex;
    }

    public static final ModuleCategory[] VALUES = ModuleCategory.values();
}
