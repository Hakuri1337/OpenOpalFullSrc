package wtf.oraculus.utility.render;

import com.ibm.icu.impl.Pair;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;

import java.awt.*;

public enum ClientTheme {

    SIGMA("Sigma Style", new Color(77, 167, 255), new Color(42, 104, 214)),
    ORACULUS("Oraculus", new Color(45, 191, 254), new Color(36, 153, 203)),
    SPEARMINT("Spearmint", new Color(97, 194, 162), new Color(65, 130, 108)),
    JADE_GREEN("Jade Green", new Color(0, 168, 107), new Color(0, 105, 66)),
    GREEN_SPIRIT("Green Spirit", new Color(159, 226, 191), new Color(0, 135, 62)),
    ROSY_PINK("Rosy Pink", new Color(255, 102, 204), new Color(191, 77, 153)),
    MAGENTA("Magenta", new Color(213, 63, 119), new Color(157, 68, 110)),
    HOT_PINK("Hot Pink", new Color(231, 84, 128), new Color(172, 79, 198)),
    LAVENDER("Lavender", new Color(219, 166, 247), new Color(152, 115, 172)),
    AMETHYST("Amethyst", new Color(144, 99, 205), new Color(98, 67, 140)),
    PURPLE_FIRE("Purple Fire", new Color(177, 162, 202), new Color(104, 71, 141)),
    SUNSET_PINK("Sunset Pink", new Color(255, 145, 20), new Color(245, 105, 231)),
    BLAZE_ORANGE("Blaze Orange", new Color(255, 169, 77), new Color(255, 130, 0)),
    PINK_BLOOD("Pink Blood", new Color(255, 166, 201), new Color(228, 0, 70)),
    PASTEL("Pastel", new Color(255, 109, 106), new Color(191, 82, 80)),
    NEON_RED("Neon Red", new Color(210, 39, 48), new Color(184, 25, 42)),
    RED_COFFEE("Red Coffee", new Color(225, 34, 59), new Color(75, 19, 19)),
    DEEP_OCEAN("Deep Ocean", new Color(60, 82, 145), new Color(0, 20, 64)),
    CHAMBRAY_BLUE("Chambray Blue", new Color(60, 82, 145), new Color(33, 46, 182)),
    MINT_BLUE("Mint Blue", new Color(66, 158, 157), new Color(40, 94, 93)),
    PACIFIC_BLUE("Pacific Blue", new Color(5, 169, 199), new Color(4, 115, 135)),
    TROPICAL_ICE("Tropical Ice", new Color(102, 255, 209), new Color(6, 149, 255)),
    BLUE_PURPLE("Blue Purple", new Color(104, 77, 178), new Color(4, 60, 174)),
    RAINBOW("Rainbow", Color.BLACK, Color.BLACK),
    CUSTOM("Custom", Color.BLACK, Color.BLACK);

    private final String name;
    private final Pair<Integer, Integer> colors;

    ClientTheme(final String name, final Color color, final Color alternateColor) {
        this.name = name;
        this.colors = Pair.of(color.getRGB(), alternateColor.getRGB());
    }

    @Override
    public String toString() {
        return name;
    }

    public Pair<Integer, Integer> getColors() {
        if (this == CUSTOM) {
            return Pair.of(OverlayModule.primaryColorProperty.getValue(), OverlayModule.secondaryColorProperty.getValue());
        } else if (this == RAINBOW) {
            return Pair.of(ColorUtility.rainbow(20, 1, 1, 1), ColorUtility.rainbow(20, 40, 1, 1));
        }
        return colors;
    }
}
