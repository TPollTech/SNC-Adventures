package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.Map;

/**
 * A PRATELEIRA DE MERCADO (v1.2.75) — a gôndola do Esquinão: metal cinza,
 * 3 prateleiras brancas e etiqueta amarela. A ILHA TEM MEIO BLOCO de
 * profundidade com produtos na FRENTE e ATRÁS (o corredor dos dois lados
 * aproveita). O MODELO 3D é a estanteria; os ITENS são desenhados pelo
 * {@link PrateleiraMercadoRenderer} em cima de cada fileira.
 *
 * A GÔNDOLA OLHA PRO CORREDOR: FACING do placement (convenção da fornalha —
 * o jogador de frente pra gôndola planta e a etiqueta fica pra ele).
 *
 * Interação:
 * - TAPA (botão ESQUERDO): COMPRA — o tapa soma o item mirado no carrinho, o
 *   visor da registradora soma na hora e o CALÇO da registradora toca com o
 *   pitch subindo conforme a gôndola enche (v1.2.77, o som mora no
 *   PrateleiraNetworking.tocarCalco); SHIFT+tapa anota a fileira inteira;
 * - clique DIREITO com item: repõe o slot mirado;
 * - SHIFT+clique DIREITO com item: modo GERENTE — recolhe a pilha do slot;
 * - o clique direito VAZIO não faz nada (compra é no esquerdo, estilo MSC:
 *   dá um tapa no produto e vai somando); o caixa (caixa_mercado) abre a
 *   tela do carrinho e cobra.
 */
public class PrateleiraMercadoBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * v1.2.75 — A ILHA DE MEIO BLOCO: face aberta na FRENTE (-Z local, com
     * etiqueta) e ABERTA ATRÁS (+Z local, o segundo corredor compra de lá).
     * As tampas de prateleira e o dorso central atravessam a ilha inteira.
     */
    private static final Map<Direction, VoxelShape> SELECAO = formas(false);
    private static final Map<Direction, VoxelShape> COLISAO = formas(true);

    private static Map<Direction, VoxelShape> formas(boolean colisao) {
        var formas = new java.util.EnumMap<Direction, VoxelShape>(Direction.class);
        double[][] caixas = colisao ? new double[][] {
            {0,0,0,1,16,16}, {15,0,0,16,16,16},
            {1,1,7.5,15,16,8.5},                     // dorso central, fundo aberto
            {1,1.5,0,15,2,8.5}, {1,6.5,0,15,7,8.5},   // frente: prateleiras 1 e 2
            {1,11.5,0,15,12,8.5},                     // frente: prateleira 3
            {1,1.5,8,15,2,15.5}, {1,6.5,8,15,7,15.5}, // fundo: prateleiras 1 e 2
            {1,11.5,8,15,12,15.5}                     // fundo: prateleira 3
        } : new double[][] {{0,0,0,16,16,16}};
        for (Direction frente : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] c : caixas) {
                double x0=c[0], z0=c[2], x1=c[3], z1=c[5];
                double[] r = switch (frente) {
                    case EAST -> new double[] {16-z1,x0,16-z0,x1};
                    case SOUTH -> new double[] {16-x1,16-z1,16-x0,16-z0};
                    case WEST -> new double[] {z0,16-x1,z1,16-x0};
                    default -> new double[] {x0,z0,x1,z1};
                };
                shape = Shapes.or(shape, Block.box(r[0],c[1],r[1],r[2],c[4],r[3]));
            }
            formas.put(frente, shape.optimize());
        }
        return Map.copyOf(formas);
    }

    public PrateleiraMercadoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // o dono planta olhando a gôndola: ela devolve o olhar (fornalha)
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SELECAO.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return COLISAO.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrateleiraMercadoBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // v1.2.75: a COMPRA é o TAPA (botão esquerdo, AttackBlockCallback ->
        // anotarTapado). O clique direito vazio não faz nada na gôndola.
        return InteractionResult.PASS;
    }

    /**
     * O TAPA (botão esquerdo) — o gesto de compra estilo My Summer Car:
     * cada tapa no produto soma uma dose no carrinho e o visor da registradora
     * atualiza. O callback de ataque só traz bloco+face, então a mira EXATA é
     * refeita no servidor (clip do olhar — a mesma geometria que o client viu).
     */
    public static void anotarTapado(ServerLevel level, ServerPlayer player, BlockPos pos,
            Direction face) {
        if (!(level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be)) {
            return;
        }
        var olho = player.getEyePosition();
        var clip = level.clip(new net.minecraft.world.level.ClipContext(olho,
                olho.add(player.getViewVector(1.0F).scale(6)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        BlockHitResult hit = clip.getBlockPos().equals(pos) ? clip
                : new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).relative(face, .5),
                        face, pos, false);
        int slot = slotDaMira(hit, be.getBlockState(), player);
        boolean fundo = hit.getDirection() == be.getBlockState().getValue(FACING).getOpposite();
        PrateleiraNetworking.solicitarCompra(level, player, pos, slot, fundo, player.isShiftKeyDown());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // v1.2.75: mão vazia NÃO compra pela direita — o gesto de compra é o
        // TAPA com o botão ESQUERDO (AttackBlockCallback -> anotarTapado).
        // PASS deixa a interação passar sem consumir nada.
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        ServerLevel servidor = (ServerLevel) level;
        int slot = slotDaMira(hit, state, player);
        boolean fundo = hit.getDirection() == state.getValue(FACING).getOpposite();
        if (player.isShiftKeyDown()) {
            // SHIFT+item: modo GERENTE — recolhe a pilha do slot pra mão
            be.recolherParaMao(servidor, player, slot, fundo);
            return InteractionResult.SUCCESS_SERVER;
        }
        // clique com item: repõe a gôndola (qualquer item — do garrafão à semente)
        if (!be.reabastecerDaMao(servidor, player, stack, slot, fundo)
                && player instanceof ServerPlayer jogador) {
            // slot incompatível/cheio: diz o porquê (a etiqueta fala)
            jogador.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "block.intoxicantes.prateleira_slot_cheio"), true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * O slot pela MIRA: a coluna do hit escolhe 1 dos 3 expositores da
     * fileira; a ALTURA do hit escolhe a fileira (embaixo/meio/cima) — e a
     * FACING gira a coluna junto (a etiqueta olha o freguês).
     */
    static int slotDaMira(BlockHitResult hit, BlockState state) {
        return slotDaMira(hit, state, null);
    }

    static int slotDaMira(BlockHitResult hit, BlockState state, Player jogador) {
        double x = hit.getLocation().x - hit.getBlockPos().getX();
        double z = hit.getLocation().z - hit.getBlockPos().getZ();
        double y = hit.getLocation().y - hit.getBlockPos().getY();
        Direction frente = state.getValue(FACING);
        // Direita de quem está diante da gôndola: os produtos continuam no
        // mesmo slot quando a fileira oposta é vista pelo outro lado.
        Direction direita = frente.getClockWise();
        double across = .5 + (x - .5) * direita.getStepX()
                + (z - .5) * direita.getStepZ();
        double meio01 = (PrateleiraMercadoBlockEntity.COLUNAS[0]
                + PrateleiraMercadoBlockEntity.COLUNAS[1]) / 32.0;
        double meio12 = (PrateleiraMercadoBlockEntity.COLUNAS[1]
                + PrateleiraMercadoBlockEntity.COLUNAS[2]) / 32.0;
        int coluna = across < meio01 ? 0 : (across < meio12 ? 1 : 2);
        double corte01 = (PrateleiraMercadoBlockEntity.NIVEIS[1] - 1) / 16.0;
        double corte12 = (PrateleiraMercadoBlockEntity.NIVEIS[2] - 1) / 16.0;
        int fileira = y < corte01 ? 0 : (y < corte12 ? 1 : 2);
        // v1.2.75: a FACE do hit escolhe o lado — frente (-Z local) ou o
        // FUNDO aberto (+Z local). O slot existe nos dois corredores.
        Direction face = hit.getDirection();
        return fileira * 3 + coluna + (face == frente.getOpposite()
                ? PrateleiraMercadoBlockEntity.SLOTS : 0);
    }

    /** Ticker do servidor: o relógio da entrega (1 checagem/s). */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (nivel, pos, estado, be) -> {
            if (be instanceof PrateleiraMercadoBlockEntity prateleira) {
                prateleira.tick((ServerLevel) nivel);
            }
        };
    }
}
