package com.intoxicantes;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.intoxicantes.energia.SNCEnergiesAdapter;

/**
 * v1.2.71 — O CABO ELÉTRICO DE COBRE (módulo 5: AS 5 BITOLAS REAIS, não
 * "tiers"). O disjuntor protege o FIO: cada bitola aguenta uma corrente
 * máxima (tabela NBR, arredondada pros valores do jogo) e a rede usa a
 * MENOR bitola do trecho como limite — fio fino num circuito grosso é o
 * clássico "o disjuntor segura, o fio derrete".
 *
 * NÓ PASSIVO (mantido): sem ticker, sem buffer — a topologia do quadro que
 * o percorre (BFS). Visual multipart (núcleo + 6 braços). Conecta em cabo,
 * quadro, soquete, interruptor, tomada e fontes do SNC Energies (adapter).
 */
public class CaboEletricoBlock extends Block {

    /**
     * A amperagem máxima por bitola (NBR 5410 arredondada pros disjuntores do
     * jogo): 1,5 mm² → 10 A (iluminação), 2,5 → 20 A (tomadas), 4 → 32 A,
     * 6 → 40 A, 10 → 63 A (alimentação principal).
     */
    public static final Map<Double, Long> AMPERAGEM_BITOLA = Map.of(
            1.5, 10L,
            2.5, 20L,
            4.0, 32L,
            6.0, 40L,
            10.0, 63L);

    public static final Map<Direction, BooleanProperty> CONEXOES = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN);

    /** A bitola do cabo (mm² — do ID: cabo_cobre_2_5mm → 2.5). */
    private final double bitola;

    public CaboEletricoBlock(Properties properties, double bitola) {
        super(properties);
        this.bitola = bitola;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BlockStateProperties.NORTH, Boolean.FALSE)
                .setValue(BlockStateProperties.SOUTH, Boolean.FALSE)
                .setValue(BlockStateProperties.EAST, Boolean.FALSE)
                .setValue(BlockStateProperties.WEST, Boolean.FALSE)
                .setValue(BlockStateProperties.UP, Boolean.FALSE)
                .setValue(BlockStateProperties.DOWN, Boolean.FALSE));
    }

    public double bitola() {
        return bitola;
    }

    /** A corrente máxima da bitola (0 se desconhecida). */
    public long amperagemMaxima() {
        return AMPERAGEM_BITOLA.getOrDefault(bitola, 0L);
    }

    /** Amperagem máxima de uma bitola arbitrária (o multímetro usa). */
    public static long amperagemDaBitola(double bitola) {
        return AMPERAGEM_BITOLA.getOrDefault(bitola, 0L);
    }

    /** Bitola a partir do ID (cabo_cobre_2_5mm → 2.5; cabo_cobre_4mm → 4.0). */
    public static double bitolaDoBloco(Block bloco) {
        String nome = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(bloco).getPath();
        if (!nome.startsWith("cabo_cobre_") || !nome.endsWith("mm")) {
            return 0;
        }
        String meio = nome.substring("cabo_cobre_".length(),
                nome.length() - 2);
        return Double.parseDouble(meio.replace('_', '.'));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
                BlockStateProperties.EAST, BlockStateProperties.WEST,
                BlockStateProperties.UP, BlockStateProperties.DOWN);
    }

    /**
     * O vizinho aceita conexão? Cabos entre si, equipamentos da instalação
     * (quadro/soquete/interruptor/tomada) e fontes do SNC Energies (a BE de
     * entrada). Água/terra/parede não conectam.
     */
    public static boolean conectaCom(BlockState vizinho, Level level, BlockPos posVizinho) {
        Block b = vizinho.getBlock();
        if (b instanceof CaboEletricoBlock
                || b instanceof QuadroEletricoBlock
                || b instanceof SoqueteTetoBlock
                || b instanceof InterruptorSimplesBlock
                || b instanceof TomadaBlock
                || b instanceof LedPotenciaBlock) {
            return true;
        }
        // fonte do SNC Energies (gerador/fogão/cubo/cabo deles) — via adapter
        return vizinho.hasBlockEntity()
                && SNCEnergiesAdapter.eFonteValida(level.getBlockEntity(posVizinho));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> e : CONEXOES.entrySet()) {
            BlockPos vizinho = pos.relative(e.getKey());
            state = state.setValue(e.getValue(),
                    conectaCom(level.getBlockState(vizinho), level, vizinho));
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        if (level instanceof ServerLevel servidor) {
            com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(
                    servidor, pos, QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(
                level, pos, QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess tick, BlockPos pos, Direction direcao,
            BlockPos posVizinho, BlockState estadoVizinho,
            net.minecraft.util.RandomSource random) {
        BooleanProperty prop = CONEXOES.get(direcao);
        if (prop != null) {
            boolean conecta = level instanceof Level lvl
                    ? conectaCom(estadoVizinho, lvl, posVizinho)
                    : estadoVizinho.getBlock() instanceof CaboEletricoBlock
                            || estadoVizinho.getBlock() instanceof QuadroEletricoBlock
                            || estadoVizinho.getBlock() instanceof SoqueteTetoBlock
                            || estadoVizinho.getBlock() instanceof InterruptorSimplesBlock
                            || estadoVizinho.getBlock() instanceof TomadaBlock
                            || estadoVizinho.getBlock() instanceof LedPotenciaBlock;
            state = state.setValue(prop, conecta);
            if (level instanceof ServerLevel servidor) {
                com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(
                        servidor, pos, QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
            }
        }
        return state;
    }

    /** Fare fino no chão (anda por cima); 4px de diâmetro. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        VoxelShape forma = Block.box(7, 7, 7, 9, 9, 9);
        for (var conexao : CONEXOES.entrySet()) {
            if (!state.getValue(conexao.getValue())) continue;
            VoxelShape braco = switch (conexao.getKey()) {
                case NORTH -> Block.box(7, 7, 0, 9, 9, 7);
                case SOUTH -> Block.box(7, 7, 9, 9, 9, 16);
                case EAST -> Block.box(9, 7, 7, 16, 9, 9);
                case WEST -> Block.box(0, 7, 7, 7, 9, 9);
                case UP -> Block.box(7, 9, 7, 9, 16, 9);
                case DOWN -> Block.box(7, 0, 7, 9, 7, 9);
            };
            forma = net.minecraft.world.phys.shapes.Shapes.or(forma, braco);
        }
        return forma;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }
}
