package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import wtf.oraculus.utility.player.InventoryUtility;

import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

/** SSNG block validation and slot-selection order. */
public final class SsngInventoryUtil {
    private static final Set<Block> INVALID = Set.of(
            Blocks.ENCHANTING_TABLE, Blocks.OAK_SIGN, Blocks.CHEST, Blocks.ENDER_CHEST,
            Blocks.TRAPPED_CHEST, Blocks.ANVIL, Blocks.SAND, Blocks.COBWEB, Blocks.TORCH,
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.WATER_CAULDRON, Blocks.DISPENSER,
            Blocks.STONE_PRESSURE_PLATE, Blocks.BAMBOO_PRESSURE_PLATE, Blocks.NOTE_BLOCK,
            Blocks.DROPPER, Blocks.TNT, Blocks.REDSTONE_TORCH, Blocks.DAYLIGHT_DETECTOR,
            Blocks.BIRCH_SIGN, Blocks.SPRUCE_SIGN, Blocks.JUNGLE_SIGN, Blocks.ACACIA_SIGN,
            Blocks.DARK_OAK_SIGN, Blocks.MANGROVE_SIGN, Blocks.CHERRY_SIGN, Blocks.BAMBOO_SIGN,
            Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN,
            Blocks.SPRUCE_HANGING_SIGN, Blocks.JUNGLE_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN,
            Blocks.DARK_OAK_HANGING_SIGN, Blocks.MANGROVE_HANGING_SIGN, Blocks.CHERRY_HANGING_SIGN,
            Blocks.BAMBOO_HANGING_SIGN, Blocks.CRIMSON_HANGING_SIGN, Blocks.WARPED_HANGING_SIGN
    );

    private SsngInventoryUtil() { }

    public static boolean isValid(final ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && !INVALID.contains(item.getBlock()) && InventoryUtility.isGoodBlock(item.getBlock());
    }

    public static SlotData choose(final BlockSlotMode mode) {
        if (mc.player == null) return null;
        if (isValid(mc.player.getOffHandStack())) return new SlotData(-1, Hand.OFF_HAND);
        final int selected = mc.player.getInventory().getSelectedSlot();
        if (mode == BlockSlotMode.FARTHEST && isValid(mc.player.getMainHandStack())) return new SlotData(selected, Hand.MAIN_HAND);
        int best = -1, bestCount = -1;
        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isValid(stack)) continue;
            if (mode == BlockSlotMode.FARTHEST) best = i;
            else if (stack.getCount() > bestCount) { best = i; bestCount = stack.getCount(); }
        }
        return best == -1 ? null : new SlotData(best, Hand.MAIN_HAND);
    }

    public static int countHotbar() {
        if (mc.player == null) return 0;
        int count = isValid(mc.player.getOffHandStack()) ? mc.player.getOffHandStack().getCount() : 0;
        for (int i = 0; i < 9; i++) if (isValid(mc.player.getInventory().getStack(i))) count += mc.player.getInventory().getStack(i).getCount();
        return count;
    }

    public enum BlockSlotMode { FARTHEST("Farthest"), MOST_BLOCKS("Most Blocks");
        private final String text; BlockSlotMode(String text) { this.text = text; } public String toString() { return text; } }
    public record SlotData(int slot, Hand hand) {
        public boolean invalid() {
            if (mc.player == null) return true;
            return hand == Hand.OFF_HAND ? !isValid(mc.player.getOffHandStack()) : slot < 0 || slot > 8 || !isValid(mc.player.getInventory().getStack(slot));
        }
    }
}
