package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * O ALAMBIQUE DE COBRE (v1.2.50) — a destilação de verdade: caldeira de
 * cobre + serpentina. Regra da spec: cachaça e rum são DESTILADAS, e as
 * duas passam por ESTE bloco (a receita determina a saída — nunca um
 * "alambique de rum" duplicado).
 *
 * Como funciona: coloca FOGO/campfire embaixo (a chama aquece), carrega
 * com o mosto fermentado (clique-direito), o cobre trabalha (vapor +
 * borbulha) e, quando termina, o destilado jovem fica pronto na bica —
 * mão vazia recolhe. Sem fogo embaixo, o lote simplesmente pausa (a
 * destilação precisa de calor constante).
 */
public class AlambiqueBlock extends MaquinaGrandeBlock {

    /** Parte BAIXA: caldeira gorda + balde condensador na lateral. */
    private static final VoxelShape SHAPE_BAIXO = Block.box(1.4, 0.0, 1.4, 14.6, 16.0, 14.6);
    /** Parte ALTA: domo + pescoço + braço da serpentina. */
    private static final VoxelShape SHAPE_ALTO = Block.box(4.0, 0.0, 4.0, 15.5, 10.0, 12.0);

    public AlambiqueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape forma(BlockState state) {
        return state.getValue(METADE) == DoubleBlockHalf.LOWER ? SHAPE_BAIXO : SHAPE_ALTO;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return temBlockEntity(state) ? new AlambiqueBlockEntity(pos, state) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof AlambiqueBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                // shift + mão vazia: recolhe o destilado (a rota de sempre)
                be.interagir((ServerLevel) level, player);
            } else {
                // v1.2.59: botão direito abre a GUI do alambique
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof AlambiqueBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // SHIFT + item: inserção rápida (a BE mesma consome a dose certa)
        if (player.isShiftKeyDown()) {
            boolean aceitou = be.tentarCarregar((ServerLevel) level, player, stack);
            if (aceitou) {
                level.playSound(null, pos, SoundEvents.BLAZE_SHOOT,
                        SoundSource.BLOCKS, 0.7F, 1.1F);
            } else {
                be.interagir((ServerLevel) level, player);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        // clique normal: abre a GUI
        player.openMenu(be);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** v1.2.59: quebrou o alambique, devolve o mosto e o destilado. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (level instanceof ServerLevel servidor
                && level.getBlockEntity(posDoCorpo(state, pos)) instanceof AlambiqueBlockEntity be) {
            be.soltarConteudo(servidor);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Vapor e brilho do cobre quente (client, só quando destilando). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        // o vapor sai do pescoço na parte ALTA (o BE mora na baixa)
        boolean alto = state.getValue(METADE) == DoubleBlockHalf.UPPER;
        var bePos = alto ? pos.below() : pos;
        if (!(level.getBlockEntity(bePos) instanceof AlambiqueBlockEntity be) || !be.destilando()) {
            return;
        }
        if (random.nextInt(20) != 0) {
            return;
        }
        // vapor sai do pescoço (parte alta) — cobre quente também brilha embaixo
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        double y = alto ? pos.getY() + 0.6 : pos.getY() + 1.0;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, 0.015, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.05, z, 0, 0.01, 0);
        }
    }

    /** Ticker do servidor: o relógio da destilação (pausa sem fogo) — só a parte baixa. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !temBlockEntity(state)) {
            return null;
        }
        return (nivel, pos, estado, be) -> {
            if (be instanceof AlambiqueBlockEntity alambique) {
                alambique.tick((ServerLevel) nivel);
            }
        };
    }
}
