package wtf.oraculus.event.impl.game.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;

public final class BlockShapeEvent {
    private final BlockPos blockPos;
    private final BlockState blockState;
    private VoxelShape voxelShape;
    private final List<VoxelShape> extraVoxelShapes;

    public BlockShapeEvent(BlockPos blockPos, BlockState blockState, VoxelShape voxelShape, List<VoxelShape> extraVoxelShapes) {
        this.blockPos = blockPos;
        this.blockState = blockState;
        this.voxelShape = voxelShape;
        this.extraVoxelShapes = extraVoxelShapes;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public VoxelShape getVoxelShape() {
        return voxelShape;
    }

    public List<VoxelShape> getExtraVoxelShapes() {
        return extraVoxelShapes;
    }

    public void setVoxelShape(VoxelShape voxelShape) {
        this.voxelShape = voxelShape;
    }
}
