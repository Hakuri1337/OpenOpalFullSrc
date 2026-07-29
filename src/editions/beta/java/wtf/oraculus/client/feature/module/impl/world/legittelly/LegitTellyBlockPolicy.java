package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

import static wtf.oraculus.client.Constants.mc;

final class LegitTellyBlockPolicy {
    private LegitTellyBlockPolicy() {
    }

    static boolean isPlaceable(final ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() < 1
                || !(stack.getItem() instanceof BlockItem blockItem)
                || stack.getItem() instanceof PlayerHeadItem) {
            return false;
        }

        final String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("click") || name.contains("right") || name.contains("teleport")
                || name.contains("点击") || name.contains("使用")
                || name.contains("传送") || name.contains("再来")) {
            return false;
        }

        final Block block = blockItem.getBlock();
        final String id = Registries.BLOCK.getId(block).getPath();
        if (id.contains("stairs") || id.contains("slab") || id.contains("fence")
                || id.contains("pane") || id.contains("rail") || id.contains("door")
                || id.contains("torch") || id.contains("pumpkin") || id.contains("flower")
                || id.contains("sapling") || id.contains("banner") || id.contains("button")
                || id.contains("skull") || id.contains("head") || id.contains("carpet")
                || id.contains("cactus") || id.contains("sign") || id.contains("mushroom")
                || id.contains("sand") || id.contains("gravel") || id.contains("clay")
                || id.contains("chest") || id.contains("furnace") || id.contains("hopper")
                || id.contains("dispenser") || id.contains("dropper") || id.contains("anvil")
                || id.contains("jukebox") || id.contains("crafting_table")
                || id.contains("enchanting_table") || id.contains("brewing_stand")
                || id.contains("spawner") || id.contains("bed") || id.contains("beacon")
                || id.contains("portal") || id.contains("ladder") || id.contains("snow")
                || id.contains("tripwire") || id.contains("pressure_plate")) {
            return false;
        }
        return !(block instanceof PlantBlock)
                && !(block instanceof SlabBlock)
                && !(block instanceof CobwebBlock)
                && !(block instanceof TntBlock);
    }

    static boolean isReplaceable(final BlockPos pos) {
        if (mc.world == null || mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        return mc.world.getBlockState(pos).isReplaceable();
    }

    static boolean isSafeSupport(final BlockPos pos) {
        if (mc.world == null || mc.player == null || mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        final Block block = state.getBlock();
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.getCollisionShape(mc.world, pos).isEmpty()
                && !(block instanceof BlockWithEntity)
                && !(block instanceof FurnaceBlock)
                && !(block instanceof CraftingTableBlock)
                && !(block instanceof LadderBlock)
                && !(block instanceof DoorBlock)
                && !(block instanceof TrapdoorBlock)
                && !(block instanceof FenceGateBlock)
                && !(block instanceof TntBlock);
    }
}
