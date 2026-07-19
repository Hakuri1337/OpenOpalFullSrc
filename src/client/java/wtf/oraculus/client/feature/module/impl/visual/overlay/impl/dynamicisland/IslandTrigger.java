package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland;

import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;

public interface IslandTrigger extends Comparable<IslandTrigger> {
    void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress);

    float getIslandWidth();

    float getIslandHeight();

    default int getIslandPriority() {
        return 0;
    }

    @Override
    default int compareTo(@NotNull IslandTrigger o) {
        return Integer.compare(o.getIslandPriority(), getIslandPriority());
    }
}
