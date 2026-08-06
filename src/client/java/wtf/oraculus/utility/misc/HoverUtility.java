package wtf.oraculus.utility.misc;

public final class HoverUtility {

    private HoverUtility() {
    }

    public static boolean isHovering(final float x, final float y, final float width, final float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static boolean isHovering(final float x, final float y, final float width, final float height, double mouseX, double mouseY, final float scaleFactor) {
        final float scaledWidth = width * scaleFactor;
        final float scaledHeight = height * scaleFactor;
        final float offsetX = x + (width - scaledWidth) / 2f;
        final float offsetY = y + (height - scaledHeight) / 2f;

        return mouseX >= offsetX && mouseY >= offsetY && mouseX < offsetX + scaledWidth && mouseY < offsetY + scaledHeight;
    }

}
