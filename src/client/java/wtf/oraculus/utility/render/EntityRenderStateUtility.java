package wtf.oraculus.utility.render;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.jetbrains.annotations.Nullable;

public final class EntityRenderStateUtility {

    private static final ThreadLocal<BipedEntityRenderState> HUMAN_RENDER_STATE = ThreadLocal.withInitial(() -> null);

    private EntityRenderStateUtility() {
    }

    @Nullable
    public static BipedEntityRenderState getHumanRenderState() {
        return HUMAN_RENDER_STATE.get();
    }

    public static void setHumanRenderState(final BipedEntityRenderState state) {
        HUMAN_RENDER_STATE.set(state);
    }

    public static void clearHumanRenderState() {
        HUMAN_RENDER_STATE.remove();
    }

}
