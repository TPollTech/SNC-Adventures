package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A registradora é a metade superior; os carrinhos pertencem ao servidor e ao jogador. */
public final class CaixaMercadoBlockEntity extends BlockEntity {
    public CaixaMercadoBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.CAIXA_MERCADO_ENTITY, pos, state);
    }
}
