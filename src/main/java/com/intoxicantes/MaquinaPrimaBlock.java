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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * MÁQUINA DE PRIMA (v1.2.50) — a base reutilizável do processamento de
 * matéria-prima: UMA classe, TRÊS blocos com identidade própria:
 *
 * <ul>
 *   <li><b>ESMAGADORA DE CANA</b> — os rolos esmagam a cana: 4 canas → 4 caldo
 *       + 1 bagaço (queima na fornalha);</li>
 *   <li><b>PRENSA DE UVAS</b> — o fuso esmaga e coa: 6 uvas → 4 caldo de uva;</li>
 *   <li><b>CALDEIRÃO DE CERVEJA</b> — mostura + fervura: 4 malte + 1 lúpulo →
 *       4 cerveja crua (precisa de ÁGUA embaixo).</li>
 * </ul>
 *
 * v1.2.53 TAMANHO REAL: 2 blocos de altura (MaquinaGrandeBlock) — as versões
 * grandes de verdade têm altura de gente. O BE mora na parte baixa; cliques
 * na parte alta roteiam pra baixo; a quebra é acoplada.
 *
 * Uma futura máquina de prima (torrador de café, etc.) é só registrar outro
 * bloco desta classe com a receita no {@link ProcessosBebida}.
 */
public class MaquinaPrimaBlock extends MaquinaGrandeBlock {

    /** Identidade da máquina (qual livro-de-receitas consultar). */
    public enum Tipo {
        MOENDA("moenda_cana"),
        PRENSA("prensa_uvas"),
        CALDEIRAO("caldeirao_mostura");

        public final String id;

        Tipo(String id) {
            this.id = id;
        }
    }

    private final Tipo tipo;

    public MaquinaPrimaBlock(Properties properties, Tipo tipo) {
        super(properties);
        this.tipo = tipo;
    }

    public Tipo tipo() {
        return tipo;
    }

    @Override
    protected VoxelShape forma(BlockState state) {
        // silhueta cheia nos dois andares (o modelo 3D esculpe o detalhe)
        return state.getValue(METADE) == DoubleBlockHalf.LOWER
                ? Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0)
                : Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return temBlockEntity(state) ? new MaquinaPrimaBlockEntity(pos, state, tipo) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof MaquinaPrimaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                // shift + mão vazia: recolhe o pronto (a rota de sempre)
                be.interagir((ServerLevel) level, player);
            } else {
                // v1.2.59: botão direito abre a GUI da máquina
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(posDoCorpo(state, pos)) instanceof MaquinaPrimaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // SHIFT + item: inserção rápida (a BE mesma consome a dose certa)
        if (player.isShiftKeyDown()) {
            boolean aceitou = be.tentarCarregar((ServerLevel) level, player, stack);
            if (aceitou) {
                level.playSound(null, pos, SoundEvents.WOOD_BREAK,
                        SoundSource.BLOCKS, 0.7F, 0.9F);
            } else {
                be.interagir((ServerLevel) level, player);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        // clique normal: abre a GUI (insere pelos slots, com feedback visual)
        player.openMenu(be);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** v1.2.59: quebrou a máquina, devolve TUDO que estava dentro. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (level instanceof ServerLevel servidor
                && level.getBlockEntity(posDoCorpo(state, pos)) instanceof MaquinaPrimaBlockEntity be) {
            be.soltarConteudo(servidor);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Poeira do processamento (client, só com lote em curso) — parte baixa. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (state.getValue(METADE) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MaquinaPrimaBlockEntity be)
                || !be.processando()) {
            return;
        }
        if (random.nextInt(15) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.POOF,
                pos.getX() + 0.4 + random.nextDouble() * 0.2,
                pos.getY() + 0.9,
                pos.getZ() + 0.4 + random.nextDouble() * 0.2,
                0, 0.01, 0);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !temBlockEntity(state)) {
            return null;
        }
        return (nivel, pos, estado, be) -> {
            if (be instanceof MaquinaPrimaBlockEntity maquina) {
                maquina.tick((ServerLevel) nivel);
            }
        };
    }
}
