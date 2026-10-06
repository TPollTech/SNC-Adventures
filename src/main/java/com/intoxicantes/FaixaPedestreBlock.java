package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * FAIXA DE PEDESTRE (v1.2.24) — a tinta branca da travessia do estacionamento.
 * Bloco PLANO (1px de altura, como tapete de tinta): o freguês anda por cima
 * sem degrau e a vaga continua demarcada. Vai em fileiras sobre o asfalto —
 * faixa + asfalto + faixa = zebra de esquina de verdade.
 *
 * Chão cedeu? a tinta some (updateShape). Quebra num hit e dropa a si mesma
 * (loot padrão) pra o dono da esquina reformar o estacionamento.
 */
public class FaixaPedestreBlock extends Block {

    /** Pintura rasteira: 1px de altura, 16x16 de cobertura. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public FaixaPedestreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /**
     * O asfalto debaixo sumiu? a tinta vai junto (nada de faixa flutuante).
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos posVizinho,
            BlockState estadoVizinho, RandomSource random) {
        if (direction == Direction.DOWN && !estadoVizinho.isFaceSturdy(
                level, posVizinho, Direction.UP)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, posVizinho,
                estadoVizinho, random);
    }
}
