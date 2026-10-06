package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * O LETREIRO DO MERCADO ESQUINÃO (v1.2.23; DISPLAY DE FACHADA na v1.2.31) —
 * uma FAIXA de display estilo Satisfactory MONTADA NA FACHADA do mercado:
 * LARGURA_FACHADA blocos (a largura do prédio) × 1 de altura, colada sob o
 * beiral do alpendre, ACIMA da porta — nada de torres/pólos na calçada e
 * nada na frente da entrada. Fundo preto contínuo (1 tela só) e o texto em
 * FONTE DE LED PRÓPRIA (LedFont, bitmap 5×7 desenhado em Java — nada de
 * fonte do Minecraft), desenhado por código no PlacaEsquinaoRenderer com
 * brilho full-bright, status ABERTO/FECHADO piscando e o nome ESTICADO até
 * preencher a largura da fachada.
 *
 * PARTE define o papel do bloco na faixa:
 *   - PAINEL   : o centro — o ÚNICO com block entity (o texto renderiza 1×,
 *                atravessando a faixa toda)
 *   - EXTENSAO : os demais blocos da faixa (só a caixa, sem texto próprio)
 *   - ESQUERDA / DIREITA + RODAPE / COLUNA / TOPO : anatomia VELHA (torres
 *                de posto, 1.2.24–1.2.30) — mantida SÓ para saves antigos
 *                carregarem até o zelador migrar para a fachada.
 *
 * Quebrar qualquer bloco da faixa derruba a faixa INTEIRA — o display é UM
 * objeto na fachada.
 */
public class PlacaEsquinaoBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** O papel do bloco na montagem (torres nas pontas, painel no centro). */
    public static final EnumProperty<Parte> PARTE = EnumProperty.create("parte", Parte.class);
    /** v1.2.23: a camada da torre/painel na vertical (roda, coluna, topo). */
    public static final EnumProperty<Nivel> NIVEL = EnumProperty.create("nivel", Nivel.class);
    /** v1.2.23: o letreiro acende de noite (a placa "liga" como LED de verdade). */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /** Nomes serializados batem com o blockstate e com o NBT do template. */
    public enum Parte implements net.minecraft.util.StringRepresentable {
        PAINEL("painel"),
        EXTENSAO("extensao"),
        ESQUERDA("esquerda"),
        DIREITA("direita");

        private final String nome;

        Parte(String nome) {
            this.nome = nome;
        }

        @Override
        public String getSerializedName() {
            return nome;
        }
    }

    /** Camada vertical: RODAPE (chão), COLUNA (meio), TOPO (alto). */
    public enum Nivel implements net.minecraft.util.StringRepresentable {
        RODAPE("rodape"), COLUNA("coluna"), TOPO("topo");

        private final String nome;

        Nivel(String nome) {
            this.nome = nome;
        }

        @Override
        public String getSerializedName() {
            return nome;
        }
    }

    // ---- shapes: quadrado no perfil, o modelo cuida do detalhe 3D ----
    /** Anatomia VELHA: pedestal mais largo (6/16). */
    private static final VoxelShape SHAPE_RODAPE = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
    /** Anatomia VELHA: metal fino no centro. */
    private static final VoxelShape SHAPE_COLUNA = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
    /** Anatomia VELHA: coroa no alto da torre. */
    private static final VoxelShape SHAPE_TOPO = Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0);
    /** Painel/extensão da fachada: bloco CHEIO — o display É a faixa da
     * parede (o letreiro é EMBUTIDO, cara de letreiro de posto; o texto
     * lê dos DOIS lados pela renderização dupla-face). */
    private static final VoxelShape SHAPE_PAINEL = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public PlacaEsquinaoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(PARTE, Parte.PAINEL)
                .setValue(NIVEL, Nivel.COLUNA)
                .setValue(LIT, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PARTE, NIVEL, LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // SÓ o painel (centro da faixa) tem block entity — as extensões
        // deixam de renderizar o texto N× (o texto cruza a faixa toda, 1×)
        if (state.getValue(PARTE) != Parte.PAINEL) return null;
        return new PlacaEsquinaoBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * v1.2.27 — A AUTOCURA DA PLACA: o painel tem ticker de server. A cada
     * 30s ele chama o zelador do block entity: placa de MUNDO VELHO (sem a
     * marca "nova") adota o texto novo; e placa de anatomia VELHA
     * (versao<3, torres na calçada) é MIGRADA para o display de fachada
     * (v1.2.31) — sem rebuild de mundo, sem placa na frente da porta.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (state.getValue(PARTE) != Parte.PAINEL) return null;
        return createTickerHelper(type, IntoxicantesMod.PLACA_ESQUINAO_ENTITY,
                (nivel, pos, estado, be) -> PlacaEsquinaoBlockEntity.zeladorDaAutocura(nivel, pos, be));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        Parte parte = state.getValue(PARTE);
        if (parte == Parte.PAINEL || parte == Parte.EXTENSAO) {
            // o display é a própria faixa da parede: bloco cheio
            return SHAPE_PAINEL;
        }
        return switch (state.getValue(NIVEL)) {
            case RODAPE -> SHAPE_RODAPE;
            case COLUNA -> SHAPE_COLUNA;
            case TOPO -> SHAPE_TOPO;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // facing aponta PRO OBSERVADOR (igual à placa na fachada, voltada pra rua)
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PARTE, Parte.PAINEL)
                .setValue(NIVEL, Nivel.COLUNA)
                .setValue(LIT, context.getLevel().getRawBrightness(
                        context.getClickedPos(), 0) < 9);
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
     * v1.2.31 — A MIGRAÇÃO DA FACHADA (chamada pelo zelador do block entity,
     * 1× por placa): placas da anatomia VELHA (versao<3 — o letreiro de
     * torres na calçada) são reformadas: as torres caem SEM drop, a faixa
     * nova nasce MONTADA NA FACHADA (1 bloco acima e 1 pra dentro da posição
     * velha — exatamente a faixa da parede sob o beiral) com a LARGURA DO
     * PRÉDIO, e o painel velho (que ficava na frente da porta) sai. Texto,
     * vínculo com o mercado e estado ABERTO/FECHADO são levados juntos.
     */
    static void migrarParaFachada(Level level, BlockPos pos, PlacaEsquinaoBlockEntity be) {
        if (level.isClientSide()) return;
        BlockState velha = be.getBlockState();
        Direction facing = velha.getValue(FACING);
        // A faixa nova MONTADA NA FACHADA: 1 acima e 1 pra dentro da posição
        // velha — exatamente a faixa da parede sob o beiral (o display é
        // embutido: o bloco da faixa É o da parede, sem "TV na frente"). Se
        // alguma posição ficar FORA do prédio (ar), a faixa ENCOSTA: 1 a
        // menos por lado enquanto cair no ar (min 5 blocos).
        int larguraDesejada = PlacaEsquinaoBlockEntity.LARGURA_FACHADA;
        int larguraReal = larguraDesejada;
        Direction eixoLarg = facing.getClockWise();
        BlockPos teste = pos.relative(facing.getOpposite());
        int meia = larguraDesejada / 2;
        for (int lado = -1; lado <= 1; lado += 2) {
            while (larguraReal > 5) {
                BlockPos p = teste.relative(eixoLarg, lado * (larguraReal / 2));
                if (!level.getBlockState(p).isSolidRender()) {
                    larguraReal -= 1;
                } else {
                    break;
                }
            }
        }
        larguraDesejada = larguraReal;

        // 1) as duas torres velhas caem (reforma, não demolição de jogador)
        for (Direction lado : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos p = pos.relative(lado, 2).offset(0, dy, 0);
                if (level.getBlockState(p).getBlock() instanceof PlacaEsquinaoBlock) {
                    level.setBlockAndUpdate(p,
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }

        // 2) a faixa nova na fachada: 1 acima + 1 pra dentro da posição velha
        BlockPos centro = pos.above().relative(facing.getOpposite());
        Direction eixo = facing.getClockWise();
        java.util.List<String> linhas = java.util.List.copyOf(be.getLinhas());
        BlockState caixa = IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(NIVEL, Nivel.COLUNA)
                .setValue(LIT, velha.getValue(LIT));
        int meia2 = larguraDesejada / 2;
        for (int dx = -meia2; dx <= meia2; dx++) {
            BlockPos p = centro.relative(eixo, dx);
            level.setBlockAndUpdate(p, dx == 0 ? caixa : caixa.setValue(PARTE, Parte.EXTENSAO));
        }
        if (level.getBlockEntity(centro) instanceof PlacaEsquinaoBlockEntity novo) {
            novo.definirConteudo(linhas, be.isLinkMercado(), be.isAberto(), larguraDesejada);
        }

        // 3) o painel velho (flutuando na frente da porta) sai
        level.setBlockAndUpdate(pos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    /**
     * Quebrou um bloco da faixa? A FAIXA INTEIRA cai (é um objeto): o
     * removido derruba os vizinhos no eixo da fachada e a queda cascata até
     * as pontas. Estados VELHOS (torres) derrubam a coluna da torre irmã.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movidoPorPistao) {
        super.affectNeighborsAfterRemoval(state, level, pos, movidoPorPistao);
        if (movidoPorPistao) return;
        Parte parte = state.getValue(PARTE);
        Direction facing = state.getValue(FACING);
        if (parte == Parte.PAINEL || parte == Parte.EXTENSAO) {
            for (Direction lado : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos vizinho = pos.relative(lado);
                if (level.getBlockState(vizinho).getBlock() instanceof PlacaEsquinaoBlock) {
                    level.destroyBlock(vizinho, false);
                }
            }
            return;
        }
        // anatomia velha: a torre derruba o painel e a torre irmã
        Direction proPainel = parte == Parte.ESQUERDA
                ? facing.getCounterClockWise() : facing.getClockWise();
        BlockPos painel = pos.relative(proPainel, 2);
        for (int dy = -2; dy <= 3; dy++) {
            BlockPos p = painel.offset(0, dy, 0);
            if (level.getBlockState(p).getBlock() instanceof PlacaEsquinaoBlock) {
                level.destroyBlock(p, false);
            }
        }
        derrubarTorreVelha(level, painel, parte == Parte.ESQUERDA
                ? facing.getClockWise() : facing.getCounterClockWise());
    }

    /** Derruba a coluna inteira de uma torre VELHA (offset de 2 painéis). */
    private static void derrubarTorreVelha(ServerLevel level, BlockPos painel, Direction lado) {
        for (int dy = -1; dy <= 2; dy++) {
            BlockPos p = painel.relative(lado, 2).offset(0, dy, 0);
            if (level.getBlockState(p).getBlock() instanceof PlacaEsquinaoBlock) {
                level.destroyBlock(p, false);
            }
        }
    }

    /**
     * v1.2.36 — A CENTRAL DE COMANDO: mano na placa (clique com a mão vazia
     * ou com qualquer item NÃO-chave) abre a Central de Comando — editar
     * texto, cor, brilho, modo. Com a CHAVE (comparador) na mão: trava/destrava
     * o letreiro do mercado (a Central pede a chave pra abrir). O letreiro
     * continua tocando o jingle de leitura quando abre.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            PlacaEsquinaoBlockEntity be = null;
            if (level.getBlockEntity(pos) instanceof PlacaEsquinaoBlockEntity p) {
                be = p;
            } else {
                // extensão/velho: acha o PAINEL da faixa (o dono do texto)
                for (int d = -24; d <= 24; d++) {
                    BlockPos p = pos.relative(state.getValue(FACING).getClockWise(), d);
                    if (level.getBlockEntity(p) instanceof PlacaEsquinaoBlockEntity pe) {
                        be = pe;
                        break;
                    }
                }
            }
            if (be == null) return InteractionResult.PASS;
            boolean comChave = sp.getMainHandItem().is(net.minecraft.world.item.Items.COMPARATOR);
            if (be.isLinkMercado() && be.isTrancada() && !comChave) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "block.intoxicantes.placa_esquinao.trancada"));
                tocarBlipsDeLeitura(level, pos);
                return InteractionResult.SUCCESS_SERVER;
            }
            tocarBlipsDeLeitura(level, pos);
            CentralComandoNetworking.abrir(sp, be);
            var mercado = MarketSystem.getMarketPos();
            if (mercado == null || pos.closerThan(mercado, 24.0)) {
                for (GagoEntity g : level.getEntitiesOfClass(GagoEntity.class,
                        sp.getBoundingBox().inflate(32.0))) {
                    g.cumprimentarLeitor((ServerLevel) level, sp);
                    break;
                }
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** v1.2.36: a CHAVE (comparador) tranca/destranca o letreiro do mercado. */
    @Override
    protected InteractionResult useItemOn(ItemStack item, BlockState state, Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand mao, BlockHitResult hit) {
        if (!level.isClientSide() && item.is(net.minecraft.world.item.Items.COMPARATOR)
                && level.getBlockEntity(pos) instanceof PlacaEsquinaoBlockEntity be
                && be.isLinkMercado()) {
            be.setTrancada(!be.isTrancada());
            level.playSound(null, pos,
                    be.isTrancada() ? net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE
                            : net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    be.isTrancada() ? "block.intoxicantes.placa_esquinao.trancou"
                            : "block.intoxicantes.placa_esquinao.destrancou"));
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.useItemOn(item, state, level, pos, player, mao, hit);
    }

    /** v1.2.23: faísca verde no LED ocasional de noite (só no painel). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PARTE) != Parte.PAINEL) return;
        if (!state.getValue(LIT)) return; // LED desligado de dia não faísca
        if (random.nextInt(40) != 0) return;
        if (level.getRawBrightness(pos, 0) >= 9) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.62;
        double y = pos.getY() + 0.4 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.62;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW,
                x, y, z, 0.0, 0.0, 0.0);
    }

    /**
     * v1.2.19: a caixa de som do letreiro — clicou pra LER, o letreiro toca o
     * "boop boop boop" de linhas (um blip por linha, subindo) + a voz do Gago
     * cumprimentando. Server-side (todos perto escutam, igual NPC de RPG).
     */
    private static void tocarBlipsDeLeitura(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        for (int i = 0; i < 4; i++) {
            MarketSystem.agendarNota(server, pos, NOTE_BLOCK_BIT_HAT(),
                    0.55F, 1.3F + i * 0.12F, i * 5);
        }
    }

    /** O hat do note block (blip curto e seco — o "boop" do letreiro). */
    public static net.minecraft.sounds.SoundEvent NOTE_BLOCK_BIT_HAT() {
        return net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value();
    }
}
