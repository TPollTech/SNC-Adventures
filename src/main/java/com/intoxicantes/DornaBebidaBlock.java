package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A DORNA DE FERMENTAÇÃO (v1.2.50) — o "sistema geral de fermentação" da
 * spec: uma tina de madeira onde o mosto in natura borbulha até virar mosto
 * fermentado. Reutilizada por TODA cadeia que ferementa antes de destilar
 * (cachaça, rum e futuras bebidas).
 *
 * v1.2.53 TAMANHO REAL: 2 blocos de altura (MaquinaGrandeBlock). A tina de
 * verdade é alta: o corpo baixo guarda o líquido com as pernas, o alto tem a
 * boca + a tampa entreaberta por onde escapa o vapor. O BE mora na parte
 * baixa; cliques na parte alta roteiam pra baixo; a quebra é acoplada.
 *
 * Fluxo: clique-direito com a QUANTIDADE certa de insumo → a dorna carrega;
 * o timer corre (server-side, no BE); quando zera, o resultado fica "servido"
 * — clique-direito de mão vazia recolhe. Clique com outro insumo antes do fim
 * NÃO sobrescreve (o mosto em curso é respeitado).
 */
public class DornaBebidaBlock extends MaquinaGrandeBlock {

    /** Parte BAIXA: pernas + corpo da tina com o líquido (o BE mora aqui). */
    private static final VoxelShape SHAPE_BAIXO = Block.box(0.6, 0.0, 0.6, 15.4, 14.0, 15.4);
    /** Parte ALTA: boca aberta + tampa entreaberta. */
    private static final VoxelShape SHAPE_ALTO = Block.box(0.6, 0.0, 0.6, 15.4, 8.0, 15.4);

    public DornaBebidaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape forma(BlockState state) {
        return state.getValue(METADE) == DoubleBlockHalf.LOWER ? SHAPE_BAIXO : SHAPE_ALTO;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return temBlockEntity(state) ? new DornaBebidaBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof DornaBebidaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                // shift + mão vazia: recolhe o fermentado (a rota de sempre)
                be.interagir((ServerLevel) level, player);
            } else {
                // v1.2.59: botão direito abre a GUI da dorna
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof DornaBebidaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // SHIFT + item: inserção rápida (a BE mesma consome a dose certa)
        if (player.isShiftKeyDown()) {
            boolean aceitou = be.tentarCarregar((ServerLevel) level, player, stack);
            if (aceitou) {
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW,
                        SoundSource.BLOCKS, 0.7F, 0.9F);
            } else {
                be.interagir((ServerLevel) level, player);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        // clique normal: abre a GUI
        player.openMenu(be);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** v1.2.59: quebrou a dorna, devolve o mosto e o fermentado. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (level instanceof ServerLevel servidor
                && level.getBlockEntity(posDoCorpo(state, pos)) instanceof DornaBebidaBlockEntity be) {
            be.soltarConteudo(servidor);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Bolhas de fermentação (client, baratas: 1 a cada 30 ticks quando ativa). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        // só a parte baixa solta bolha (o líquido é aqui)
        if (state.getValue(METADE) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof DornaBebidaBlockEntity be) || !be.fermentando()) {
            return;
        }
        if (random.nextInt(30) != 0) {
            return;
        }
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
        level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.75, z, 0, 0.02, 0);
    }

    /** Ticker do servidor: o relógio da fermentação (barato, 1x/s) — só a parte baixa. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !temBlockEntity(state)) {
            return null;
        }
        return (nivel, pos, estado, be) -> {
            if (be instanceof DornaBebidaBlockEntity dorna) {
                dorna.tick((ServerLevel) nivel);
            }
        };
    }
}
