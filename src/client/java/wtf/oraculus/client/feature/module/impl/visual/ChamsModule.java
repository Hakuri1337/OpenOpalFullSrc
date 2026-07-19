package wtf.oraculus.client.feature.module.impl.visual;

import com.ibm.icu.impl.Pair;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.utility.render.ColorUtility;

public final class ChamsModule extends Module {

    private final BooleanProperty keepTextures = new BooleanProperty("Keep textures", false),
            colorOverlay = new BooleanProperty("Color overlay", true);

    public ChamsModule() {
        super("Chams", "A form of wall-hack that allows you see players through walls.", ModuleCategory.VISUAL);
        addProperties(
                keepTextures,
                colorOverlay
        );
    }

    public int getRGBAColor() {
        if (!colorOverlay.getValue())
            return -1;

        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();
        return ColorUtility.interpolateColorsBackAndForth(10, 1, colors.first, colors.second);
    }

    public boolean isColorOverlay() {
        return colorOverlay.getValue();
    }

    public boolean shouldKeepTextures() {
        return keepTextures.getValue();
    }

}
