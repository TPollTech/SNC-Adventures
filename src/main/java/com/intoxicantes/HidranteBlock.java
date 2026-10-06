package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * HIDRANTE DA ESQUINA (v1.2.24) — o vermelhão de ferro fundido na calçada do
 * mercado, decorativo (cuidado pra não estacionar em cima — o Gago reclama).
 *
 * Interatividade de esquina: usar a mão no hidrante dá um JATO de água
 * (partículas + splash) — nada escapa do mod sem uma piada.
 */
public class HidranteBlock extends Block {

    /**
     * v1.2.24: HIDRANTE DE VERDADE — saiu do nanico (10px): pedestal no chão,
     * corpo gordo, boné e domo — 15px de altura (quase um bloco), 10px de
     * largura (dá pra ver de longe e chutar bola nele kkkk).
     */
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 15.0, 13.0);

    public HidranteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /**
     * O JATO: bola de água no rosto de quem mexe onde não deve (client vê as
     * partículas pelo sendParticles do server). Som de splash + pop da tampa.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.7F, 1.2F);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    18, 0.25, 0.15, 0.25, 0.4);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    8, 0.15, 0.1, 0.15, 0.05);
        }
        return InteractionResult.PASS;
    }
}
