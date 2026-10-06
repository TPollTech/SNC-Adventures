package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mobília com frente -Z; o blockstate gira o modelo e seus UVs como conjunto. */
public class BlocoOrientadoBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final VoxelShape[] formas;

    public BlocoOrientadoBlock(Properties properties, VoxelShape norte, VoxelShape leste,
            VoxelShape sul, VoxelShape oeste) {
        super(properties);
        formas = new VoxelShape[] {norte, leste, sul, oeste};
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext contexto) {
        return defaultBlockState().setValue(FACING, contexto.getHorizontalDirection().getOpposite());
    }

    private VoxelShape forma(BlockState state) {
        // get2DDataValue NÃO tem ordem norte/leste/sul/oeste.
        return formas[switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter mundo, BlockPos pos,
            CollisionContext contexto) {
        return forma(state);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter mundo, BlockPos pos,
            CollisionContext contexto) {
        return forma(state);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
