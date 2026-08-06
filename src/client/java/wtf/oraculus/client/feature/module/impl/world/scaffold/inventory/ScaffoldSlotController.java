package wtf.oraculus.client.feature.module.impl.world.scaffold.inventory;

import net.minecraft.item.ItemStack;
import wtf.oraculus.client.feature.module.impl.world.scaffold.block.ScaffoldBlockUtil;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldSlotController {
    private int originalSlot = -1;

    public void capture() {
        if (mc.player != null) {
            this.originalSlot = mc.player.getInventory().getSelectedSlot();
        }
    }

    public int findPlaceableSlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (ScaffoldBlockUtil.isPlaceable(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public int selectPlaceableSlot() {
        final int slot = this.findPlaceableSlot();
        if (slot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
        return slot;
    }

    public ItemStack originalStack() {
        if (mc.player == null || this.originalSlot < 0 || this.originalSlot >= 9) {
            return ItemStack.EMPTY;
        }
        return mc.player.getInventory().getStack(this.originalSlot);
    }

    public ItemStack selectedStack() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
    }

    public int countAllBlocks() {
        if (mc.player == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (ScaffoldBlockUtil.isPlaceable(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public int originalSlot() {
        return this.originalSlot;
    }

    public void restore() {
        if (mc.player != null && this.originalSlot >= 0 && this.originalSlot < 9) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
        }
        this.originalSlot = -1;
    }
}
