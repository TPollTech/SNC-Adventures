package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * O PAINEL DE LED CRAFTÁVEL (v1.2.36) — a TV de tela plana do Esquinão:
 * um painel FINO (3px, shape colado na face de suporte, igual quadro) que
 * o jogador crafta, monta na parede e programa pela CENTRAL DE COMANDO
 * (clique direito): texto de 2 linhas, cor do LED, brilho e modo
 * (FIXO / SCROLL). Suporta até 3 painéis lado a lado no EIXO da parede —
 * o painel ESQUERDO (menor X/Z no eixo) manda o texto pra TODA a linha,
 * como o letreiro do mercado.
 *
 * Regras de vizinhança:
 *  - quebrou um painel → a LINHA INTEIRA cai (dropa 1 item por painel);
 *  - face de suporte quebrou → cai também (scheduledTick checa o suporte);
 *  - o texto/cor vive no block entity do painel-cabeça.
 */
public class PainelLedBlock extends BaseEntityBlock {

    /** A face do bloco de suporte que o painel encara (como o quadro). */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 1 = painel solto; 2/3 = segunda/terceira tela da linha (conta pro texto esticar). */
    public static final IntegerProperty TELAS = IntegerProperty.create("telas", 1, 3);
    /** LED ligado (de dia apaga como o letreiro, economizando energia kkk). */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** O painel fino (v1.2.36 — a TV de tela plana): 3px de espessura, UM
     * shape POR FACING (shape não gira com o blockstate). CONVENÇÃO DA
     * FORNALHA: o facing é a direção de onde a tela ENCARA — north = painel
     * colado na parede ao SUL (corpo z13..16), etc. */
    private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
            Direction.NORTH, Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0),
            Direction.SOUTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0),
            Direction.WEST, Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Direction.EAST, Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0));

    public PainelLedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TELAS, 1)
                .setValue(LIT, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TELAS, LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PainelLedBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    /** Nascimento: clicou numa parede → painel colado NA PAREDE (a tela
     * encara quem colocou); chão/teto → encara quem colocou. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isVertical()
                ? context.getHorizontalDirection().getOpposite()
                : face;
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(TELAS, 1)
                .setValue(LIT, context.getLevel().getRawBrightness(
                        context.getClickedPos(), 0) < 9);
    }

    /**
     * MONTAGEM EM LINHA: colocou um painel ao lado de outro (mesma altura,
     * mesma facing, eixo da parede)? A linha cresce: o painel-CABEÇA (menor
     * coordenada no eixo) passa a mandar o texto pra todos; cada painel
     * grava TELAS = total da linha. (O modelo é o mesmo; a Central abre pela
     * cabeça.)
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            LivingEntity colocador, ItemStack stack) {
        super.setPlacedBy(level, pos, state, colocador, stack);
        if (level.isClientSide()) return;
        reavaliarLinha(level, pos);
    }

    /**
     * Recalcula TELAS (1..3) da linha que contém {@code pos} — varre as duas
     * direções do eixo da parede por painéis irmãos com a mesma facing.
     */
    static void reavaliarLinha(Level level, BlockPos pos) {
        BlockState centro = level.getBlockState(pos);
        if (!(centro.getBlock() instanceof PainelLedBlock)) return;
        Direction facing = centro.getValue(FACING);
        Direction eixoA = facing.getClockWise();
        Direction eixoB = facing.getCounterClockWise();

        // ache a CABEÇA: caminha pro lado A até o fim da linha
        BlockPos cabeca = pos;
        for (int i = 0; i < 2; i++) {
            BlockPos p = cabeca.relative(eixoA);
            if (level.getBlockState(p).getBlock() instanceof PainelLedBlock
                    && level.getBlockState(p).getValue(FACING) == facing) {
                cabeca = p;
            } else {
                break;
            }
        }
        // conta a linha inteira partindo da cabeça, no sentido B
        int total = 1;
        BlockPos fim = cabeca;
        for (int i = 0; i < 2; i++) {
            BlockPos p = fim.relative(eixoB);
            if (level.getBlockState(p).getBlock() instanceof PainelLedBlock
                    && level.getBlockState(p).getValue(FACING) == facing) {
                fim = p;
                total++;
            } else {
                break;
            }
        }
        // grava TELAS em todos (o valor só muda o drop; o texto vem da cabeça)
        BlockPos cursor = cabeca;
        for (int i = 0; i < total; i++) {
            BlockState s = level.getBlockState(cursor);
            if (s.getBlock() instanceof PainelLedBlock && s.getValue(TELAS) != total) {
                level.setBlock(cursor, s.setValue(TELAS, total), 3);
            }
            cursor = cursor.relative(eixoB);
        }
    }

    /**
     * Quebrou um painel: a LINHA INTEIRA cai (é uma TV só, segmentada).
     * O drop é 1 item por painel que havia na linha (loot table × TELAS).
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movidoPorPistao) {
        super.affectNeighborsAfterRemoval(state, level, pos, movidoPorPistao);
        if (movidoPorPistao) return;
        Direction facing = state.getValue(FACING);
        for (Direction lado : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos vizinho = pos.relative(lado);
            if (level.getBlockState(vizinho).getBlock() instanceof PainelLedBlock) {
                // SEM drop: a linha inteira é UMA TV só — o loot table já devolve
                // 1 item por TELA no painel destruído (sem cascata multiplicando)
                level.destroyBlock(vizinho, false);
            }
        }
    }

    /**
     * O suporte sumiu (alguém quebrou a parede)? O painel cai no tick
     * seguinte — igual quadro sem parede.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockPos suporte = pos.relative(facing.getOpposite());
        BlockState s = level.getBlockState(suporte);
        // cai só sem NADA atrás (ar ou substituível — grama/água). Vidro,
        // pane e cercas seguram o painel (TV pendurada em vitrine vale).
        if (s.isAir() || s.canBeReplaced()) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Clique direito = CENTRAL DE COMANDO (texto, cor, brilho, modo).
     * Clique com comparador não tranca nada aqui (painel do jogador é livre);
     * o comparador é chave só no letreiro do mercado.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            BlockPos cabeca = acharCabeca(level, pos, state);
            if (level.getBlockEntity(cabeca) instanceof PainelLedBlockEntity be) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(),
                        SoundSource.BLOCKS, 0.5F, 1.4F);
                CentralComandoNetworking.abrir(sp, be);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** O painel-cabeça da linha (menor coordenada no eixo da parede). */
    static BlockPos acharCabeca(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction eixoA = facing.getClockWise();
        BlockPos cabeca = pos;
        for (int i = 0; i < 2; i++) {
            BlockPos p = cabeca.relative(eixoA);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof PainelLedBlock && s.getValue(FACING) == facing) {
                cabeca = p;
            } else {
                break;
            }
        }
        return cabeca;
    }

    /** Faísca do LED à noite (o painel vivo, igual ao letreiro). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(60) != 0) return;
        if (level.getRawBrightness(pos, 0) >= 9) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.62;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.62;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW, x, y, z, 0, 0, 0);
    }

    /** Ticker de server: LIT segue a luz do dia (LED dorme de dia). */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, IntoxicantesMod.PAINEL_LED_ENTITY,
                (nivel, pos, estado, be) -> {
                    if (nivel.getGameTime() % 100L != 0L) return;
                    boolean luz = nivel.getRawBrightness(pos, 0) >= 9;
                    if (estado.getValue(LIT) == luz) {
                        nivel.setBlock(pos, estado.setValue(LIT, !luz), 3);
                    }
                });
    }
}
