package com.intoxicantes;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import com.intoxicantes.energia.CabosSuspensos;
import com.intoxicantes.energia.EnergiaRedes;

/**
 * v1.2.80 — O CONECTOR (o terminal do cabo suspenso): uma peça pequena que se
 * prende NA FACE de um bloco da instalação — quadro, soquete, tomada,
 * interruptor, cabo de cobre ou uma fonte do SNC Energies (a mesma regra do
 * cabo de bloco: CaboEletricoBlock.conectaCom).
 *
 * Ele não carrega energia sozinho: é o ponto onde o elo pendurado
 * (energia/EloCabo) entra e sai. Quebrar o conector corta o cabo; tirar o
 * bloco de suporte derruba o conector (o fio perde o ponto).
 *
 * NÓ PASSIVO da rede: sem ticker, sem BE. A topologia é a do quadro que o
 * percorre (BFS), que agora atravessa os elos.
 */
public class ConectorEletricoBlock extends Block {

    /** A face para onde o conector aponta (o suporte fica atrás dele). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * As quatro PARTES do conector no FACING = NORTE, em coordenadas 0..16
     * (a convenção do Block.box): base contra o suporte (z=16), colar, corpo e
     * ponta de cobre (z=1). O SUPORTE fica ao sul e o cabo sai pelo norte —
     * a ponta do cabo no mundo é centro + face × 0,5.
     *
     * O corpo é CENTRADO no bloco de propósito: o meio do conector é
     * clicável em qualquer orientação, e é por ali que a bobina age.
     */
    private static final double[][] PARTES = {
            {3.0, 3.0, 13.5, 13.0, 13.0, 16.0},
            {4.5, 4.5, 11.0, 11.5, 11.5, 13.5},
            {5.5, 5.5, 5.0, 10.5, 10.5, 11.0},
            {6.5, 6.5, 1.0, 9.5, 9.5, 5.0}};

    /** As seis formas prontas (mesma rotação do blockstate, sem recalcular). */
    private static final Map<Direction, VoxelShape> FORMAS = new EnumMap<>(Direction.class);

    static {
        for (Direction face : Direction.values()) {
            FORMAS.put(face, formaPara(face));
        }
    }

    /**
     * A forma do conector na face pedida. A aritmética acontece nas
     * coordenadas 0..16 ANTES de virarem VoxelShape — o Block.box normaliza
     * para 0..1, então transformar AABBs depois daria conta errada.
     */
    private static VoxelShape formaPara(Direction face) {
        VoxelShape saida = Shapes.empty();
        for (double[] p : PARTES) {
            double x1 = p[0], y1 = p[1], z1 = p[2];
            double x2 = p[3], y2 = p[4], z2 = p[5];
            switch (face) {
                case EAST -> {        // norte vira leste (y: 90)
                    double nx1 = 16.0 - z2;
                    double nx2 = 16.0 - z1;
                    z1 = p[0]; z2 = p[3];
                    x1 = nx1; x2 = nx2;
                }
                case SOUTH -> {       // meia volta
                    double nx1 = 16.0 - p[3];
                    double nx2 = 16.0 - p[0];
                    double nz1 = 16.0 - p[5];
                    double nz2 = 16.0 - p[2];
                    x1 = nx1; x2 = nx2; z1 = nz1; z2 = nz2;
                }
                case WEST -> {        // (x, z) -> (z, 16 - x)
                    double nx1 = p[2];
                    double nx2 = p[5];
                    double nz1 = 16.0 - p[3];
                    double nz2 = 16.0 - p[0];
                    x1 = nx1; x2 = nx2; z1 = nz1; z2 = nz2;
                }
                case UP -> {          // suporte embaixo: a base vira o piso
                    double ny1 = 16.0 - p[5];
                    double ny2 = 16.0 - p[2];
                    double nz1 = p[1];
                    double nz2 = p[4];
                    y1 = ny1; y2 = ny2; z1 = nz1; z2 = nz2;
                }
                case DOWN -> {        // suporte em cima: a base vira o teto
                    double ny1 = p[2];
                    double ny2 = p[5];
                    double nz1 = 16.0 - p[4];
                    double nz2 = 16.0 - p[1];
                    y1 = ny1; y2 = ny2; z1 = nz1; z2 = nz2;
                }
                default -> { }        // NORTH: as coordenadas já são as do modelo
            }
            saida = Shapes.or(saida, Block.box(x1, y1, z1, x2, y2, z2));
        }
        return saida;
    }

    public ConectorEletricoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** O bloco atrás (o suporte) aceita conector? */
    public static boolean suportaConector(BlockState estado, Level level, BlockPos pos) {
        return CaboEletricoBlock.conectaCom(estado, level, pos);
    }

    /**
     * Só instala contra um suporte compatível e numa célula livre — o terminal
     * é preso na face, nunca flutua.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos suporte = pos.relative(face.getOpposite());
        if (!level.getBlockState(pos).canBeReplaced()) return null;
        if (!suportaConector(level.getBlockState(suporte), level, suporte)) return null;
        return this.defaultBlockState().setValue(FACING, face);
    }

    /** A forma gira com o FACING (a base fica sempre colada no suporte). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return FORMAS.getOrDefault(state.getValue(FACING), FORMAS.get(Direction.NORTH));
    }

    // O conector TEM colisão (a do próprio contorno): a bobina age por clique,
    // então o terminal precisa ser mirável — diferente do cabo, que é atravessável.

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        if (level instanceof ServerLevel servidor) {
            EnergiaRedes.marcarRebuildProximo(servidor, pos,
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    /** Tirou o bloco de trás: o conector perde o ponto e cai com o cabo. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block blocoVizinho,
            net.minecraft.world.level.redstone.Orientation orientacao, boolean movidoPorPistao) {
        super.neighborChanged(state, level, pos, blocoVizinho, orientacao, movidoPorPistao);
        if (!(level instanceof ServerLevel servidor)) return;
        BlockPos suporte = pos.relative(state.getValue(FACING).getOpposite());
        if (suportaConector(level.getBlockState(suporte), level, suporte)) return;
        cortarCabo(servidor, pos);
        level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.5F, 0.8F);
        // drop manual: o próprio item do conector volta para quem quebrou o suporte
        Block.popResource(servidor, pos,
                new net.minecraft.world.item.ItemStack(IntoxicantesMod.CONECTOR_ELETRICO));
        servidor.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        cortarCabo(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    /** O conector sumiu: o elo pendurado nele perde a ponta. */
    private static void cortarCabo(ServerLevel level, BlockPos pos) {
        CabosSuspensos dados = CabosSuspensos.get(level);
        for (var elo : dados.removerTodosEm(pos)) {
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(BobinaCaboItem.da(elo.bitola())));
        }
        EnergiaRedes.marcarRebuildProximo(level, pos,
                QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        // O vão some junto com o terminal, sem esperar a cadência de 1s.
        CabosNetworking.sincronizarAgora(level);
    }

    /** O terminal não tem GUI: a bobina age em cima dele. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}