package wtf.oraculus.utility.render;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

public final class OrderedTextVisitor implements CharacterVisitor {

    private final StringBuilder builder = new StringBuilder();
    private Formatting lastFormatting = null;

    @Override
    public boolean accept(int index, Style style, int codePoint) {
        if (style.isBold()) {
            builder.append(Formatting.BOLD);
            lastFormatting = Formatting.BOLD;
        }

        if (style.getColor() != null) {
            for (final Formatting formatting : Formatting.values()) {
                if (formatting.isColor() && formatting.getColorValue() == style.getColor().getRgb() && formatting != lastFormatting) {
                    builder.append(formatting);
                    lastFormatting = formatting;
                    break;
                }
            }
        }

        builder.append(new String(Character.toChars(codePoint)));
        return true;
    }

    public String getFormattedString() {
        return builder.toString();
    }

}

