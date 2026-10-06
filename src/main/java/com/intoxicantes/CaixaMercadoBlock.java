package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Balcão e registradora: uma peça de duas metades, com o atendente atrás. */
public final class CaixaMercadoBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public CaixaMercadoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxY()
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            LivingEntity colocador, ItemStack item) {
        super.setPlacedBy(level, pos, state, colocador, item);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (level instanceof ServerLevel server) vincularAtendente(server, pos);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState baixo = level.getBlockState(pos.below());
            return baixo.is(this) && baixo.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos vizinho,
            BlockState outro, net.minecraft.util.RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if ((half == DoubleBlockHalf.LOWER && direction == Direction.UP)
                || (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN)) {
            if (!outro.is(this) || outro.getValue(HALF) == half
                    || outro.getValue(FACING) != state.getValue(FACING)) return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, vizinho, outro, random);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? new CaixaMercadoBlockEntity(pos, state) : null;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        double[][] caixas = state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new double[][] {{0,0,.45,16,16,16}}
                : new double[][] {
                    {6.05,0,4.6,14.85,2.08,15.58},       // gaveta e puxador
                    {6.42,2.08,5.05,14.42,6.58,14.07},  // registradora/teclado
                    {7.12,7.43,4.235,13.88,9.31,6.22},  // visor do cliente
                    {10.17,5.05,5.5,10.83,7.92,6.1},    // coluna do visor
                    {1.35,0,1.94,4.77,3.55,7.39}        // terminal de cartão
                };
        VoxelShape shape = Shapes.empty();
        for (double[] c : caixas) {
            double[] r = switch (state.getValue(FACING)) {
                case EAST -> new double[] {16-c[5],c[0],16-c[2],c[3]};
                case SOUTH -> new double[] {16-c[3],16-c[5],16-c[0],16-c[2]};
                case WEST -> new double[] {c[2],16-c[3],c[5],16-c[0]};
                default -> new double[] {c[0],c[2],c[3],c[5]};
            };
            shape = Shapes.or(shape, Block.box(r[0],c[1],r[1],r[2],c[4],r[3]));
        }
        return shape.optimize();
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            BlockPos raiz = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            GagoEntity gago = atendente(server, raiz);
            if (gago != null) PrateleiraNetworking.abrirCaixa(sp, gago);
            else sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "caixa.intoxicantes.sem_atendente"), true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public static BlockPos posAtendente(BlockPos caixa, Direction facing) {
        return caixa.relative(facing.getOpposite());
    }

    /** Procura somente caixas completos em chunks carregados, sem forçar carregamento. */
    public static BlockPos encontrar(ServerLevel level, BlockPos origem, int raio) {
        BlockPos melhor = null;
        double distancia = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origem.offset(-raio,-2,-raio), origem.offset(raio,2,raio))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof CaixaMercadoBlock) || state.getValue(HALF) != DoubleBlockHalf.LOWER) continue;
            BlockState cima = level.getBlockState(p.above());
            if (!cima.is(state.getBlock()) || cima.getValue(HALF) != DoubleBlockHalf.UPPER
                    || cima.getValue(FACING) != state.getValue(FACING)) continue;
            double d = p.distSqr(origem);
            if (d < distancia) { distancia = d; melhor = p.immutable(); }
        }
        return melhor;
    }

    public static GagoEntity atendente(ServerLevel level, BlockPos caixa) {
        BlockState state = level.getBlockState(caixa);
        if (!(state.getBlock() instanceof CaixaMercadoBlock)) return null;
        BlockPos posto = posAtendente(caixa, state.getValue(FACING));
        return level.getEntitiesOfClass(GagoEntity.class, new AABB(posto).inflate(.8),
                g -> g.isAlive() && !g.isPuto() && g.blockPosition().distSqr(posto) <= 1).stream()
                .min(java.util.Comparator.comparingDouble(g -> g.distanceToSqr(posto.getX()+.5, posto.getY(), posto.getZ()+.5)))
                .orElse(null);
    }

    /** Um Gago próximo passa a atender; nunca nasce um NPC adicional por colocar um caixa. */
    public static void vincularAtendente(ServerLevel level, BlockPos caixa) {
        BlockState state = level.getBlockState(caixa);
        if (!(state.getBlock() instanceof CaixaMercadoBlock) || state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        BlockPos posto = posAtendente(caixa, state.getValue(FACING));
        if (!level.getBlockState(posto.below()).isFaceSturdy(level, posto.below(), Direction.UP)) return;
        GagoEntity gago = level.getEntitiesOfClass(GagoEntity.class, new AABB(posto).inflate(3),
                g -> g.isAlive() && !g.isPuto() && !g.isTrading()).stream()
                .min(java.util.Comparator.comparingDouble(g -> g.blockPosition().distSqr(posto))).orElse(null);
        if (gago == null) return;
        BlockPos outroCaixa = encontrar(level, gago.blockPosition(), 2);
        if (outroCaixa != null && !outroCaixa.equals(caixa)
                && atendente(level, outroCaixa) == gago) return;
        var destino = new net.minecraft.world.phys.Vec3(posto.getX()+.5, posto.getY(), posto.getZ()+.5);
        if (!level.noCollision(gago, gago.getBoundingBox().move(destino.subtract(gago.position())))) return;
        if (gago.blockPosition().distSqr(posto) > 0) gago.absSnapTo(destino.x, destino.y, destino.z, state.getValue(FACING).toYRot(), 0);
        gago.setupPostoMercado(posto);
        gago.getNavigation().stop();
        gago.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != IntoxicantesMod.CAIXA_MERCADO_ENTITY) return null;
        return (nivel,pos,estado,be) -> {
            if (nivel.getGameTime() % 40 == 0) vincularAtendente((ServerLevel)nivel, pos.below());
        };
    }
}
