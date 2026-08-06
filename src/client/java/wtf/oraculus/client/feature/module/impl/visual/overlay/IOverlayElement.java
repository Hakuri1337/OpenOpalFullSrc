package wtf.oraculus.client.feature.module.impl.visual.overlay;

import net.minecraft.client.gui.DrawContext;

public interface IOverlayElement {

    void render(DrawContext context, float delta, boolean isBloom);

    default void renderBlur(DrawContext context, float delta) {
    }

    default void onResize() {
    }

    default void tick() {
    }

    default void onDisable() {
    }

    default boolean isActive() {
        return true;
    }

    boolean isBloom();
}
