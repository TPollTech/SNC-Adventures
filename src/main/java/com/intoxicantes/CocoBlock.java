package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v1.2.58 — O COCO no pé: nasce pendurado sob a coroa do coqueiro (pela
 * feature), quebra com qualquer tapa e dropa o item coco. Sem suporte
 * (folha quebrada) ele cai junto — o mesmo fruto voxel do item, apoiado na folha acima.
 */
public class CocoBlock extends Block {

    protected static final VoxelShape SHAPE = Block.box(3.8, 7, 3.8, 12.2, 16, 12.2);

    public CocoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** O coco precisa da folha em cima (ou do próprio coqueiro): sem ela, cai. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState acima = level.getBlockState(pos.above());
        return acima.is(IntoxicantesMod.COQUEIRO_FOLHAS)
                || acima.is(IntoxicantesMod.COQUEIRO_TRONCO)
                || acima.is(Blocks.JUNGLE_LEAVES);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block vizinho, net.minecraft.world.level.redstone.Orientation orientacao, boolean movidoPorPistao) {
        if (!canSurvive(state, level, pos)) {
            level.removeBlock(pos, false);
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(
                    IntoxicantesMod.COCO_FRUTO));
        }
    }
}
