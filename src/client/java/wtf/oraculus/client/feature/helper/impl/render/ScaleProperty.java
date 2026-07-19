package wtf.oraculus.client.feature.helper.impl.render;

import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

import static wtf.oraculus.client.Constants.mc;

public final class ScaleProperty {

    private static final ScaleMode[] MINECRAFT_VALUES = new ScaleMode[]{ScaleMode.AUTO, ScaleMode.SMALL, ScaleMode.NORMAL, null, ScaleMode.LARGE};
    private final ModeProperty<ScaleMode> modeProperty;

    private ScaleProperty(final ScaleMode[] values) {
        this.modeProperty = new ModeProperty<>("Scale", ScaleMode.AUTO, values);
    }

    public static ScaleProperty newMinecraftElement() {
        return new ScaleProperty(MINECRAFT_VALUES);
    }

    public static ScaleProperty newNVGElement() {
        return new ScaleProperty(ScaleMode.values());
    }

    public ModeProperty<ScaleMode> get() {
        return modeProperty;
    }

    public float getScale() {
        final int guiScale = mc.options.getGuiScale().getValue();

        return switch (modeProperty.getValue()) {
            // 1x
            case SMALL -> switch (guiScale) {
                case 2 -> 0.5F;
                case 3 -> 1 / 3F;
                default -> 1;
            };
            // 2x
            case NORMAL -> switch (guiScale) {
                case 1 -> 2;
                case 3 -> 2 / 3F;
                default -> 1;
            };
            // ~2.67x
            case MEDIUM -> switch (guiScale) {
                case 1 -> 2.25F;
                case 2 -> 1.125F;
                case 3 -> 0.75F;
                default -> 1;
            };
            // 3x
            case LARGE -> switch (guiScale) {
                case 1 -> 3;
                case 2 -> 1.5F;
                default -> 1;
            };
            default -> 1;
        };
    }

    public enum ScaleMode {
        AUTO("Auto"),
        SMALL("Small (1x)"),
        NORMAL("Normal (2x)"),
        MEDIUM("Medium (2.67x)"),
        LARGE("Large (3x)");

        private final String name;

        ScaleMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
