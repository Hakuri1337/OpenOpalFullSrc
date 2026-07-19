package wtf.oraculus.client.feature.module.impl.world.blockfly.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

public final class BlockFlyBlockUtil {
    private static final Set<Block> BLACKLIST = new HashSet<>();

    private BlockFlyBlockUtil() {
    }

    public static Set<Block> blacklist() {
        return BLACKLIST;
    }

    public static boolean isPlaceable(final ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1
                || !(stack.getItem() instanceof BlockItem blockItem)
                || stack.getItem() instanceof PlayerHeadItem) {
            return false;
        }

        final String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("click")
                || name.contains("right")
                || name.contains("teleport")
                || name.contains("\u70b9\u51fb")
                || name.contains("\u4f7f\u7528")
                || name.contains("\u4f20\u9001")
                || name.contains("\u518d\u6765")) {
            return false;
        }

        final Block block = blockItem.getBlock();
        return !(block instanceof PlantBlock)
                && !(block instanceof SlabBlock)
                && !(block instanceof CobwebBlock)
                && !BLACKLIST.contains(block);
    }

    public static boolean isSearchSolid(final BlockState state, final BlockPos pos) {
        if (mc.world == null || mc.player == null || state == null || state.isAir()) {
            return false;
        }
        final Block block = state.getBlock();
        return !(block instanceof FurnaceBlock)
                && !(block instanceof CraftingTableBlock)
                && !(block instanceof LadderBlock)
                && !(block instanceof TntBlock)
                && state.getFluidState().isEmpty()
                && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    public static boolean hasSolidTop(final BlockPos pos) {
        return mc.world != null
                && mc.player != null
                && mc.world.getBlockState(pos).hasSolidTopSurface(mc.world, pos, mc.player);
    }

    public static boolean isSupportFace(final BlockPos supportPos, final Direction sourceDirection) {
        if (mc.world == null || mc.player == null || mc.world.isOutOfHeightLimit(supportPos.getY())) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(supportPos);
        return !state.isAir() && state.isSolidSurface(mc.world, supportPos, mc.player, sourceDirection);
    }

    public static boolean isAir(final BlockPos pos) {
        return mc.world != null
                && !mc.world.isOutOfHeightLimit(pos.getY())
                && mc.world.getBlockState(pos).isAir();
    }
}
