package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v1.2.53 — MÁQUINAS DE TAMANHO REAL (regra do AGENTS.md): as máquinas de
 * produção deixam de ser blocos-toy de 1m³ e ocupam 2 blocos de altura, como
 * as versões grandes de verdade.
 *
 * Padrão DoubleBlockHalf (igual porta/cama vanilla e o PortaGradeBlock):
 * - LOWER (baixo): carrega o BlockEntity, a lógica e as interações;
 * - UPPER (alto): estrutura — sem BE; qualquer clique cai no bloco de baixo;
 * - setPlacedBy sobe a metade de cima; updateShape derruba a máquina inteira
 *   se a irmã sumir (igual porta vanilla);
 * - as formas por parte são declaradas pela subclasse.
 *
 * Barris de bebida continuam 1 bloco DE PROPÓSITO: barril real tem ~1m —
 * já é tamanho real.
 */
public abstract class MaquinaGrandeBlock extends Block implements EntityBlock {

    public static final EnumProperty<DoubleBlockHalf> METADE =
            EnumProperty.create("metade", DoubleBlockHalf.class);

    protected MaquinaGrandeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(METADE, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(METADE);
    }

    /** A parte de baixo tem BE (as subclasses chamam newBlockEntity só aqui). */
    protected boolean temBlockEntity(BlockState state) {
        return state.getValue(METADE) == DoubleBlockHalf.LOWER;
    }

    /** Nasce sempre na parte de baixo (a de cima sobe no setPlacedBy). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(METADE, DoubleBlockHalf.LOWER);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** A dupla: colocou a de baixo, sobe a de cima (igual porta vanilla). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity colocador, ItemStack stack) {
        super.setPlacedBy(level, pos, state, colocador, stack);
        erguerParteAlta(level, pos, state);
    }

    /** v1.2.53: setBlockAndUpdate (estruturas, testes, comandos) NÃO passa pelo
     *  setPlacedBy — o onPlace cobre esses caminhos e idempotentemente sobe a
     *  parte alta se ela não existe ainda. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        erguerParteAlta(level, pos, state);
    }

    private void erguerParteAlta(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()
                || state.getValue(METADE) != DoubleBlockHalf.LOWER) {
            return;
        }
        BlockPos posAlta = pos.above();
        if (!(level.getBlockState(posAlta).getBlock() instanceof MaquinaGrandeBlock)) {
            level.setBlock(posAlta, state.setValue(METADE, DoubleBlockHalf.UPPER), 3);
        }
    }

    /** A irmã sumiu (quebrou/pistão)? a máquina inteira cai como item. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
            net.minecraft.core.Direction direction, BlockPos posVizinho,
            BlockState estadoVizinho, net.minecraft.util.RandomSource random) {
        DoubleBlockHalf half = state.getValue(METADE);
        if (direction.getAxis() == net.minecraft.core.Direction.Axis.Y) {
            if (half == DoubleBlockHalf.LOWER == (direction == net.minecraft.core.Direction.UP)) {
                return estadoVizinho.is(this) && estadoVizinho.getValue(METADE) != half
                        ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            if (half == DoubleBlockHalf.LOWER && direction == net.minecraft.core.Direction.DOWN
                    && !state.canSurvive(level, pos)) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, posVizinho,
                estadoVizinho, random);
    }

    /**
     * v1.2.71 — MÁQUINA SOBRE ÁGUA (bug do playtest: "impossível colocar o
     * caldeirão de cerveja sobre uma fonte de água ou sobre um caldeirão de
     * água"): o canSurvive antigo exigia face sólida embaixo, mas o
     * CALDEIRÃO DE CERVEJA PRECISA de água embaixo pra rodar (a mostura
     * checa temAgua) — o bloco nunca podia ser colocado onde ele funciona.
     * A família toda (moenda, prensa, dorna, alambique, caldeirão) agora
     * aceita: face sólida OU água OU caldeirão com água. Quebrou o apoio
     * (o suporte de verdade sumiu)? a máquina cai, como já era — água não
     * "quebra", então o pote na fonte fica de pé.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(METADE) == DoubleBlockHalf.LOWER) {
            BlockState base = level.getBlockState(pos.below());
            if (base.isFaceSturdy(level, pos.below(),
                    net.minecraft.core.Direction.UP)) {
                return true;
            }
            return base.is(net.minecraft.world.level.block.Blocks.WATER)
                    || base.hasProperty(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON)
                            && base.getValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON) > 0;
        }
        return level.getBlockState(pos.below()).is(this);
    }

    /** A parte de cima não é interativa — o clique roteia pro corpo (baixo). */
    protected BlockPos posDoCorpo(BlockState state, BlockPos pos) {
        return state.getValue(METADE) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** A forma final (a subclasse declara por parte). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return forma(state);
    }

    /** Formas por parte (o modelo manda: 2 blocos de altura divididos). */
    protected abstract VoxelShape forma(BlockState state);
}
