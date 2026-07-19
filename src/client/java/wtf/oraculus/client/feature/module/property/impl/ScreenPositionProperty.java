package wtf.oraculus.client.feature.module.property.impl;

import com.google.gson.internal.LinkedTreeMap;
import com.ibm.icu.impl.Pair;
import net.minecraft.client.util.Window;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;

import static wtf.oraculus.client.Constants.mc;

public final class ScreenPositionProperty extends Property<Pair<Float, Float>> {

    private float startX, startY, width, height;
    private boolean dragging;

    public ScreenPositionProperty(final String name, final float relativeX, final float relativeY) {
        super(name);
        this.setValue(Pair.of(relativeX, relativeY));
    }

    public ScreenPositionProperty(final String name, final ModuleMode<?> parent, final float relativeX, final float relativeY) {
        super(name, parent);
        this.setValue(Pair.of(relativeX, relativeY));
    }

    @Override
    public void applyValue(Object propertyValue) {
        if (propertyValue instanceof LinkedTreeMap<?, ?> propertyObj) {
            if (propertyObj.isEmpty()) {
                return;
            }

            final Double relativeX = (Double) propertyObj.get("x");
            final Double relativeY = (Double) propertyObj.get("y");

            if (relativeX == null || relativeY == null) {
                return;
            }

            this.setValue(Pair.of(relativeX.floatValue(), relativeY.floatValue()));
        }
    }

    public float getRelativeX() {
        return this.getValue().first;
    }

    public float getRelativeY() {
        return this.getValue().second;
    }

    public float getScaledX() {
        final float relativeX = this.getRelativeX();
        final float actualX = relativeX * mc.getWindow().getScaledWidth();

        // right-align
        if (relativeX > 0.5F) {
            return actualX - this.width;
        }

        // left-align
        return actualX;
    }

    public float getScaledY() {
        return this.getRelativeY() * mc.getWindow().getScaledHeight();
    }

    public void _setRelativeX(final float relativeX) {
        this.setValue(Pair.of(relativeX, this.getRelativeY()));
    }

    public void _setRelativeY(final float relativeY) {
        this.setValue(Pair.of(this.getRelativeX(), relativeY));
    }

    public void setRelativeX(final float scaledX) {
        final int scaledWidth = mc.getWindow().getScaledWidth();

        float relativeX = scaledX / scaledWidth;
        if (relativeX > 0.5F) {
            relativeX += width / scaledWidth;
        }

        this._setRelativeX(relativeX);
    }

    public void setRelativeY(final float scaledY) {
        final float relativeY = scaledY / mc.getWindow().getScaledHeight();
        this._setRelativeY(relativeY);
    }

    public void snapToGrid() {
        final Window window = mc.getWindow();
        final int scaledWidth = window.getScaledWidth();
        final int scaledHeight = window.getScaledHeight();

        final float relativeX = this.getRelativeX();
        final float relativeY = this.getRelativeY();

        final float relativeWidth = this.width / scaledWidth;
        final float relativeHeight = this.height / scaledHeight;

        final float halfWidth = relativeWidth / 2;
        final float halfHeight = relativeHeight / 2;

        // x
        if (relativeX < 0.01F) {
            this._setRelativeX(0);
        } else if (relativeX > 0.99F) {
            this._setRelativeX(1);
        } else if (relativeX + halfWidth > 0.49F && relativeX + halfWidth < 0.51F) {
            this._setRelativeX(0.5F - halfWidth);
        }

        // y
        if (relativeY < 0.01F) {
            this._setRelativeY(0);
        } else if (relativeY + relativeHeight > 0.99F) {
            this._setRelativeY(1 - relativeHeight);
        } else if (relativeY + halfHeight > 0.49F && relativeY + halfHeight < 0.51F) {
            this._setRelativeY(0.5F - halfHeight);
        }
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public void setWidth(final float width) {
        this.width = width;
    }

    public void setHeight(final float height) {
        this.height = height;
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public void setDragging(final boolean dragging) {
        this.dragging = dragging;
    }

    public float getStartX() {
        return this.startX > mc.getWindow().getScaledWidth() / 2F
                ? this.startX - this.width
                : this.startX;
    }

    public float getStartY() {
        return this.startY;
    }

    public void setStartX(final float startX) {
        this.startX = startX;
    }

    public void setStartY(final float startY) {
        this.startY = startY;
    }

}
