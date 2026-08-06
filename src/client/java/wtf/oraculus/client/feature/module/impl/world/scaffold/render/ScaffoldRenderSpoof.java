package wtf.oraculus.client.feature.module.impl.world.scaffold.render;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

public final class ScaffoldRenderSpoof {
    private static boolean active;
    private static int originalSlot = -1;

    private ScaffoldRenderSpoof() {
    }

    public static void update(final boolean enabled, final int slot) {
        active = enabled;
        originalSlot = enabled ? slot : -1;
    }

    public static ItemStack mainHandStackOr(
            final ClientPlayerEntity player,
            final ItemStack fallback
    ) {
        if (!active || originalSlot < 0 || originalSlot >= 9) {
            return fallback;
        }
        return player.getInventory().getStack(originalSlot);
    }

    public static void clear() {
        active = false;
        originalSlot = -1;
    }
}
