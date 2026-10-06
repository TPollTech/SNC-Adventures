package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A Lampada UV: bloco de luz 15 que amadurece as plantacoes — e, desde a
 * v1.2.17, com DISJUNTOR: redstone corta a energia e a maturacao PARA. A
 * plantacao indoor agora tem o risco do growshop de verdade: alguem virou a
 * alavanca, a planta para no tempo e o dono so descobre pelo SILENCIO — o
 * zumbido so roda na lampada ligada kkkk.
 *
 * Regra (previsivel, sem surpresa):
 *   - sinal de redstone chegando (alavanca ligada, torch, fio): LIGADA
 *   - sem sinal MAS com controle de redstone adjacente (alavanca/botao/torch):
 *     DESLIGADA — o circuito esta aberto, a energia foi cortada
 *   - sem circuito nenhum: LIGADA (plugada na tomada — saves antigos e a
 *     plantacao de quem nunca mexeu com redstone continuam funcionando)
 *
 * A historia do circuito: o dono poe alavanca ao lado = chave da plantacao.
 * O vizinho esperto vira a chave, o zumbido para, a colheita para no tempo.
 */
public class LampadaUvBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Carcaça, dois tubos e apoios do modelo nativo; sem cubo invisível abaixo. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 9, 3, 15, 10.6, 13),
            Block.box(1, 7.8, 3, 15, 9, 3.7),
            Block.box(1, 7.8, 12.3, 15, 9, 13),
            Block.box(1, 7.8, 3, 1.7, 10.6, 13),
            Block.box(14.3, 7.8, 3, 15, 10.6, 13),
            Block.box(2.1, 6.65, 4.6, 13.9, 8.6, 6.4),
            Block.box(2.1, 6.65, 9.6, 13.9, 8.6, 11.4),
            Block.box(3, 10.6, 7, 4, 16, 9),
            Block.box(12, 10.6, 7, 13, 16, 9));

    public LampadaUvBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.TRUE));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) { return SHAPE; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(LIT, deveEstarLigada(ctx.getLevel(), ctx.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block blocoVizinho, net.minecraft.world.level.redstone.Orientation orientacao,
            boolean movidoPorPistao) {
        if (level.isClientSide()) return;
        // energia chegando: liga NA HORA (sem esperar o tick — lampada responde
        // ao disjuntor instantaneo); o resto avalia debounced no scheduleTick
        if (level.hasNeighborSignal(pos)) {
            if (!state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, Boolean.TRUE), 3);
                ligarComCue((ServerLevel) level, pos);
            }
        }
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean alvo = deveEstarLigada(level, pos);
        if (state.getValue(LIT) == alvo) return;
        level.setBlock(pos, state.setValue(LIT, alvo), 3);
        if (alvo) {
            ligarComCue(level, pos);
        } else {
            // a luz morre com "click" grave e fumaca (a energia cortou)
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.7F, 0.55F);
            level.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    4, 0.2, 0.2, 0.2, 0.005);
        }
    }

    /**
     * A regra do disjuntor (comentario de topo explica os 3 casos).
     * Package-private: o game test verifica cada caso isolado.
     */
    static boolean deveEstarLigada(Level level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) return true;      // energia chegando
        return !haControleDeEnergia(level, pos);            // sem circuito: plugada
    }

    /** Ha alavanca/botao/torch adjacente? (o "disjuntor" existe fisicamente) */
    static boolean haControleDeEnergia(Level level, BlockPos pos) {
        for (Direction direcao : Direction.values()) {
            var vizinho = level.getBlockState(pos.relative(direcao));
            if (vizinho.is(Blocks.LEVER)
                    || vizinho.is(Blocks.STONE_BUTTON)
                    || vizinho.is(Blocks.POLISHED_BLACKSTONE_BUTTON)
                    || vizinho.is(Blocks.REDSTONE_TORCH)
                    || vizinho.is(Blocks.REDSTONE_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    /** A luz volta com fagulhas (a energia chegou). */
    private static void ligarComCue(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                5, 0.2, 0.2, 0.2, 0.01);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // o zumbido e da LAMPADA LIGADA (desligada = silencio — e o vizinho
        // esperto ESCUTA o silencio de longe e sabe que a plantacao parou kkkk)
        if (!state.getValue(LIT)) return;
        // ~1 hum a cada 50s por lampada visivel: ambiente, nao karaoke kkkk
        if (random.nextFloat() < 0.02F) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    IntoxicantesMod.ZUMBIDO_UV, SoundSource.BLOCKS, 0.35F, 1.0F, false);
        }
    }
}
