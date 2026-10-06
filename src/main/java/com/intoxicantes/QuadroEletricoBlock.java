package com.intoxicantes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.intoxicantes.energia.CabosSuspensos;
import com.intoxicantes.energia.CircuitoEletrico;
import com.intoxicantes.energia.EloCabo;
import com.intoxicantes.energia.EnergiaRedes;
import com.intoxicantes.energia.Grandezas;
import com.intoxicantes.energia.SNCEnergiesAdapter;

/**
 * v1.2.70 — O QUADRO ELÉTRICO (o cérebro da instalação): caixa de parede com
 * barramento, 4 circuitos com disjuntor e a ENTRADA do SNC Energies.
 *
 * Módulo 2 (este): a máquina mínima honesta —
 *  - ENTRADA: fonte do SNC Energies adjacente (fogão a lenha, gerador, Energy
 *    Cube, cabo deles) consultada pelo SNCEnergiesAdapter;
 *  - BUFFER de 10.000 E (o "medidor de entrada" — NÃO é geração: só extrai);
 *  - REDE: BFS pelos cabos (nós passivos) até soquetes/interruptores —
 *    reconstruída só quando a topologia muda (fila do EnergiaRedes);
 *  - MEDIÇÃO 1×/s: consome do buffer o consumo real dos soquetes energizados
 *    (W da lâmpada → E/t), verifica sobrecarga (disjuntor desarma com 3 s
 *    acima da capacidade) e acende/apaga os soquetes.
 *
 * A GUI (módulo 3, v1.2.71): o clique abre a tela com entrada/tensão/disponível/
 * consumo e os 4 circuitos — alavanca de disjuntor, rearme, ciclar A/V e nome
 * editável, tudo pelo QuadroEletricoNetworking (servidor revalida).
 */
public class QuadroEletricoBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** A tampa aberta (visual; a GUI é a tela). */
    public static final BooleanProperty ABERTO = BlockStateProperties.OPEN;

    private static final VoxelShape NORTE =
            Block.box(2.0, 0.0, 12.0, 14.0, 16.0, 16.0);

    public QuadroEletricoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ABERTO, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ABERTO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ABERTO, Boolean.TRUE);   // coloca com a tampa aberta
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
        return NORTE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuadroEletricoBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        if (level instanceof ServerLevel servidor) {
            EnergiaRedes.marcarRebuild(servidor, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block blocoVizinho, net.minecraft.world.level.redstone.Orientation orientacao,
            boolean movidoPorPistao) {
        if (level instanceof ServerLevel servidor) {
            EnergiaRedes.marcarRebuild(servidor, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        if (level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro) quadro.desligarSaidas();
        EnergiaRedes.esquecerQuadro(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    /**
     * Clique: abre a GUI do quadro (módulo 3) — o estado de fábrica da tampa
     * (ABERTO no colocar) mostra os disjuntores expostos; fechar é um botão da
     * própria GUI. Sem a tampa de alavanca do módulo 2: o rearme agora tem
     * casa (a alavanca do disjuntor na tela).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer servidor) {
            level.setBlock(pos, state.setValue(ABERTO, Boolean.TRUE), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.5F, 1.0F);
            QuadroEletricoNetworking.abrir(servidor, quadro);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * A BE DO QUADRO: buffer de entrada (SimpleEnergyStorage próprio), 4
     * circuitos e a varredura da rede (BFS passivo pelos cabos).
     */
    public static class QuadroEletricoBlockEntity extends BlockEntity {

        /** O medidor de entrada (NÃO é armazenamento concorrente). */
        public static final long CAPACIDADE_BUFFER = 10_000L;
        /** Raio máximo da instalação a partir do quadro (BFS). */
        public static final int RAIO_REDE = 32;

        private final com.intoxicantes.energia.BufferQuadro buffer =
                new com.intoxicantes.energia.BufferQuadro(CAPACIDADE_BUFFER);
        private final List<CircuitoEletrico> circuitos = new ArrayList<>();
        /** Soquetes conhecidos pela última travessia (posição + circuito). */
        private final List<SoqueteAlcancado> soquetes = new ArrayList<>();
        /** Tomadas conhecidas pela última travessia (posição + circuito). */
        private final List<TomadaAlcancada> tomadas = new ArrayList<>();
        /** Fontes SNC Energies da entrada (posição da BE fonte). */
        private final List<BlockPos> fontes = new ArrayList<>();
        private final Map<BlockPos, Direction> facesFontes = new HashMap<>();

        /** As fontes da entrada (a GUI mostra quantas estão encostadas). */
        public List<BlockPos> fontes() {
            return fontes;
        }

        public QuadroEletricoBlockEntity(BlockPos pos, BlockState state) {
            super(IntoxicantesMod.QUADRO_ELETRICO_ENTITY, pos, state);
            // circuitos de fábrica (módulo 3 deixa renomear/ajustar na GUI)
            circuitos.add(new CircuitoEletrico("iluminacao", 10L,
                    CircuitoEletrico.V_TENSAO_PADRAO));
            circuitos.add(new CircuitoEletrico("tomadas", 20L,
                    CircuitoEletrico.V_TENSAO_PADRAO));
            circuitos.add(new CircuitoEletrico("oficina", 25L, Grandezas.V_220));
            circuitos.add(new CircuitoEletrico("area_externa", 16L,
                    CircuitoEletrico.V_TENSAO_PADRAO));
        }

        public com.intoxicantes.energia.BufferQuadro buffer() {
            return buffer;
        }

        public List<CircuitoEletrico> circuitos() {
            return circuitos;
        }

        /** Energia disponível exibida (buffer + o que as fontes ainda têm). */
        public long disponivelE() {
            long disponivel = buffer.getEnergy();
            for (BlockPos fonte : fontes) {
                if (level != null) {
                    Object s = SNCEnergiesAdapter.storageDe(
                            level.getBlockEntity(fonte), ladoFonte(fonte));
                    disponivel += SNCEnergiesAdapter.energia(s);
                }
            }
            return disponivel;
        }

        private Direction ladoFonte(BlockPos fonte) {
            Direction face = facesFontes.get(fonte);
            if (face != null) return face;
            for (Direction lado : Direction.values()) {
                if (worldPosition.relative(lado).equals(fonte)) {
                    return lado.getOpposite();
                }
            }
            return Direction.UP;
        }

        public long consumoW() {
            long watts = 0L;
            for (CircuitoEletrico c : circuitos) {
                watts += c.consumoW();
            }
            return watts;
        }

        /** Registra o quadro na rede (load/colocação). */
        public void registrar() {
            if (level instanceof ServerLevel servidor) {
                EnergiaRedes.marcarRebuild(servidor, worldPosition);
            }
        }

        /** Rearma todos os desarmados; devolve quantos voltaram. */
        public int rearmarTodos() {
            int n = 0;
            for (CircuitoEletrico c : circuitos) {
                if (c.rearma()) {
                    n++;
                }
            }
            if (n > 0) {
                setChanged();
                reconstruirTopologia();
            }
            return n;
        }

        /**
         * A TRAVESSIA (BFS a partir do quadro, nós = cabos): alcança
         * soquetes através de cabos contínuos; interruptor DESLIGADO é
         * parede elétrica. Só roda quando a topologia muda.
         */
        public void reconstruirTopologia() {
            var cargasAntigas = new HashMap<>(cargas);
            var tomadasAntigas = new ArrayList<>(tomadas);
            circuitoCondutor.clear();
            soquetes.clear();
            tomadas.clear();
            cargas.clear();
            caminhosCargas.clear();
            fontes.clear();
            facesFontes.clear();
            java.util.Arrays.fill(limiteBitolaA, 0L);
            for (var c : circuitos) c.conflitoLigacao = false;
            if (!(level instanceof ServerLevel servidor)) return;
            for (Direction lado : Direction.values()) {
                BlockPos posFonte = worldPosition.relative(lado);
                if (servidor.isLoaded(posFonte)
                        && SNCEnergiesAdapter.storageDe(servidor.getBlockEntity(posFonte), lado.getOpposite()) != null)
                    registrarFonte(posFonte, lado.getOpposite());
            }
            var fila = new java.util.ArrayDeque<NoRede>();
            for (Direction lado : Direction.Plane.HORIZONTAL) {
                BlockPos p = worldPosition.relative(lado);
                if (servidor.isLoaded(p) && componente(servidor.getBlockState(p).getBlock()))
                    fila.add(new NoRede(p.immutable(), indiceCircuitoSaidaOrientado(lado),
                            Long.MAX_VALUE, null, null));
            }
            Map<BlockPos, long[]> melhores = new HashMap<>();
            CabosSuspensos cabos = CabosSuspensos.get(servidor);
            int visitados = 0;
            while (!fila.isEmpty() && visitados < EnergiaRedes.LIMITE_TOPOLOGIA) {
                NoRede no = fila.removeFirst();
                if (!servidor.isLoaded(no.pos())) continue;
                BlockState estado = servidor.getBlockState(no.pos());
                Block bloco = estado.getBlock();
                if (!componente(bloco)) continue;
                long limite = no.limiteA();
                if (bloco instanceof CaboEletricoBlock cabo) limite = Math.min(limite, cabo.amperagemMaxima());
                long[] melhor = melhores.computeIfAbsent(no.pos(), p -> new long[circuitos.size()]);
                if (limite <= melhor[no.circuito()]) continue;
                melhor[no.circuito()] = limite;
                visitados++;
                NoRede atual = new NoRede(no.pos(), no.circuito(), limite, no.anterior(), no.elo());
                Integer dono = circuitoCondutor.putIfAbsent(no.pos(), no.circuito());
                boolean chaveAberta = bloco instanceof InterruptorSimplesBlock
                        && !estado.getValue(InterruptorSimplesBlock.LIGADO);
                if (!chaveAberta && dono != null && dono != no.circuito()) {
                    circuitos.get(dono).conflitoLigacao = true;
                    circuitos.get(no.circuito()).conflitoLigacao = true;
                }
                if (bloco instanceof ConectorEletricoBlock) {
                    Direction suporte = estado.getValue(ConectorEletricoBlock.FACING).getOpposite();
                    BlockPos posFonte = no.pos().relative(suporte);
                    if (servidor.isLoaded(posFonte)
                            && SNCEnergiesAdapter.storageDe(servidor.getBlockEntity(posFonte), suporte.getOpposite()) != null)
                        registrarFonte(posFonte, suporte.getOpposite());
                }
                BlockEntity be = servidor.getBlockEntity(no.pos());
                if (be instanceof SoqueteTetoBlock.SoqueteBlockEntity
                        || be instanceof LedPotenciaBlockEntity) {
                    if (dono == null || dono == no.circuito()) {
                        cargas.put(no.pos(), new CargaAlcancada(no.pos(), be, no.circuito()));
                        caminhosCargas.put(no.pos(), atual);
                    }
                    continue; // consumidor é terminal, nunca um fio de passagem
                }
                if (be instanceof TomadaBlock.TomadaBlockEntity tomada) {
                    if (dono == null) {
                        tomadas.add(new TomadaAlcancada(no.pos(), tomada, no.circuito()));
                        tomada.setCircuito(no.circuito());
                    }
                }
                if (bloco instanceof InterruptorSimplesBlock
                        && !estado.getValue(InterruptorSimplesBlock.LIGADO)) continue;
                for (Direction lado : Direction.values()) {
                    BlockPos proximo = no.pos().relative(lado);
                    if (!servidor.isLoaded(proximo) || proximo.equals(worldPosition)) continue;
                    Block vizinho = servidor.getBlockState(proximo).getBlock();
                    if (vizinho instanceof QuadroEletricoBlock) {
                        circuitos.get(no.circuito()).conflitoLigacao = true;
                        continue;
                    }
                    if (bloco instanceof TomadaBlock && !(vizinho instanceof LedPotenciaBlock)) continue;
                    if (componente(vizinho))
                        fila.add(new NoRede(proximo.immutable(), no.circuito(), limite, atual, null));
                }
                // O CABO SUSPENSO: o elo entre dois conectores é um fio só, e
                // o limite do trecho é a bitola do cabo (o "fio fino derrete").
                for (EloCabo elo : cabos.elosEm(no.pos())) {
                    BlockPos destino = elo.outroExtremo(no.pos()).pos();
                    if (destino.equals(no.pos()) || destino.equals(worldPosition)) continue;
                    if (!servidor.isLoaded(destino)) continue;
                    long limiteElo = Math.min(limite, elo.amperagem());
                    if (limiteElo <= 0) continue;
                    fila.add(new NoRede(destino.immutable(), no.circuito(), limiteElo, atual, elo));
                }
            }
            // Instalação truncada não pode fornecer energia para cargas não verificadas.
            if (!fila.isEmpty()) for (var c : circuitos) c.conflitoLigacao = true;
            for (var carga : cargas.values()) {
                NoRede caminho = caminhosCargas.get(carga.pos());
                long limite = caminho.limiteA() == Long.MAX_VALUE ? 0L : caminho.limiteA();
                int c = carga.circuito();
                if (limite > 0) limiteBitolaA[c] = limiteBitolaA[c] == 0 ? limite : Math.min(limiteBitolaA[c], limite);
                if (carga.be() instanceof SoqueteTetoBlock.SoqueteBlockEntity soquete)
                    soquetes.add(new SoqueteAlcancado(carga.pos(), soquete, c, limite));
            }
            for (var carga : cargasAntigas.values()) {
                if (!cargas.containsKey(carga.pos())) carga.energizar(false);
            }
            for (var t : tomadasAntigas) {
                if (!circuitoCondutor.containsKey(t.pos())) {
                    t.tomada().energizar(false);
                    t.tomada().setCircuito(-1);
                }
            }
            // Abertura e remoção cortam na reconstrução, sem aguardar outra cobrança.
            for (var carga : cargas.values()) {
                if (!circuitos.get(carga.circuito()).energizado()
                        || circuitos.get(carga.circuito()).conflitoLigacao) carga.energizar(false);
            }
            for (var t : tomadas) {
                if (!circuitos.get(t.circuito()).energizado()
                        || circuitos.get(t.circuito()).conflitoLigacao) t.tomada().energizar(false);
            }
        }

        private void registrarFonte(BlockPos pos, Direction face) {
            BlockPos fonte = pos.immutable();
            if (facesFontes.putIfAbsent(fonte, face) == null) fontes.add(fonte);
        }

        private static boolean componente(Block bloco) {
            return bloco instanceof CaboEletricoBlock || bloco instanceof InterruptorSimplesBlock
                    || bloco instanceof SoqueteTetoBlock || bloco instanceof TomadaBlock
                    || bloco instanceof LedPotenciaBlock || bloco instanceof ConectorEletricoBlock;
        }

        /** Índice da saída física relativa à orientação do quadro. */
        private int indiceCircuitoSaidaOrientado(Direction lado) {
            Direction frente = getBlockState().hasProperty(FACING)
                    ? getBlockState().getValue(FACING) : Direction.NORTH;
            if (lado == frente) return 0;
            if (lado == frente.getClockWise()) return 1;
            if (lado == frente.getOpposite()) return 2;
            if (lado == frente.getCounterClockWise()) return 3;
            return 0;
        }

        /**
         * O TICK DA REDE (1×/s, chamado pelo EnergiaRedes): mede consumo dos
         * soquetes, verifica sobrecarga por circuito, extrai energia REAL do
         * SNC Energies e acende/apaga os soquetes.
         */
        public void tickRede(ServerLevel servidor) {
            // Executar após os rebuilds evita comparar com caminhos antigos de outro quadro.
            // Mesmo uma carga terminal não pode unir fontes de dois quadros.
            for (var carga : cargas.values()) {
                if (carga.valida(servidor))
                    EnergiaRedes.validarExclusividade(servidor, this, carga.pos(), carga.circuito());
            }
            for (var tomada : tomadas)
                EnergiaRedes.validarExclusividade(servidor, this, tomada.pos(), tomada.circuito());
            for (var c : circuitos) { c.demandaWAtual = 0L; c.consumoWAtual = 0L; }
            var wattsCabos = new HashMap<BlockPos, Long>();
            // O mesmo mapa vale para o vão: o elo carrega a soma das cargas das
            // duas pontas (a bitola do trecho é o limite real do cabo).
            var wattsElos = new HashMap<EloCabo, Long>();
            for (var carga : cargas.values()) {
                if (!carga.valida(servidor)) continue;
                long watts = carga.consumoW();
                circuitos.get(carga.circuito()).demandaWAtual += watts;
                for (NoRede no = caminhosCargas.get(carga.pos()); no != null; no = no.anterior()) {
                    if (no.elo() != null) wattsElos.merge(no.elo(), watts, Long::sum);
                    if (servidor.isLoaded(no.pos()) && servidor.getBlockState(no.pos()).getBlock() instanceof CaboEletricoBlock)
                        wattsCabos.merge(no.pos(), watts, Long::sum);
                }
            }
            // Storages expostos por mais de uma face entram uma vez na medição.
            var storages = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (BlockPos fonte : fontes) {
                if (!servidor.isLoaded(fonte)) continue;
                Object storage = SNCEnergiesAdapter.storageDe(servidor.getBlockEntity(fonte), ladoFonte(fonte));
                if (storage != null) storages.add(storage);
            }
            long extraido = 0L;
            long demandado = 0L;
            boolean[] fornecido = new boolean[circuitos.size()];
            for (int i = 0; i < circuitos.size(); i++) {
                CircuitoEletrico c = circuitos.get(i);
                if (c.conflitoLigacao && c.energizado()) desarmar(servidor, c);
                if (!c.energizado()) {
                    c.ticksSobrecarregado = c.ticksCaboSobrecarregado = 0L;
                    continue;
                }
                long necessario = Grandezas.energiaDe(c.demandaWAtual, EnergiaRedes.INTERVALO_REDE);
                demandado += necessario;
                long disponivel = buffer.getEnergy();
                for (Object storage : storages)
                    disponivel += SNCEnergiesAdapter.extrairSimulado(storage, Math.max(1L, necessario));
                boolean podeFornecer = disponivel >= necessario && disponivel > 0L;
                c.consumoWAtual = podeFornecer ? c.demandaWAtual : 0L;
                boolean desarmou = c.medir();
                boolean fioSobrecarregado = false;
                if (podeFornecer) {
                    for (var entrada : wattsElos.entrySet()) {
                        if (circuitoDoPonto(entrada.getKey().a()) != i) continue;
                        if (entrada.getValue() > entrada.getKey().amperagem() * c.tensao) {
                            fioSobrecarregado = true;
                            break;
                        }
                    }
                    for (var entrada : wattsCabos.entrySet()) {
                        if (circuitoDoCabo(entrada.getKey()) != i) continue;
                        if (servidor.getBlockState(entrada.getKey()).getBlock() instanceof CaboEletricoBlock cabo
                                && entrada.getValue() > cabo.amperagemMaxima() * c.tensao) {
                            fioSobrecarregado = true;
                            break;
                        }
                    }
                }
                c.ticksCaboSobrecarregado = fioSobrecarregado ? c.ticksCaboSobrecarregado + 1L : 0L;
                if (c.ticksCaboSobrecarregado >= 5L) desarmou = true;
                if (desarmou) {
                    desarmar(servidor, c);
                    c.consumoWAtual = 0L;
                    continue;
                }
                if (!podeFornecer) continue;
                long pago = 0L;
                // Pré-verificação evita gastar energia em um intervalo impossível.
                // Fontes são extraídas antes do buffer, que absorve qualquer sobra de extração parcial.
                long falta = Math.max(0L, necessario - buffer.getEnergy());
                for (Object storage : storages) {
                    long retirado = SNCEnergiesAdapter.extrair(storage, falta);
                    pago += retirado;
                    falta -= retirado;
                    if (falta <= 0L) break;
                }
                if (pago + buffer.getEnergy() >= necessario) {
                    pago += buffer.extract(necessario - pago, false);
                    fornecido[i] = true;
                    c.registrarConsumo(pago);
                    extraido += pago;
                } else {
                    buffer.insert(pago, false);
                    c.consumoWAtual = 0L;
                }
            }
            for (var carga : cargas.values())
                carga.energizar(fornecido[carga.circuito()] && carga.consumoW() > 0L);
            for (var t : tomadas)
                t.tomada().energizar(fornecido[t.circuito()] && circuitos.get(t.circuito()).energizado());
            if (demandaMudou(extraido, demandado) || extraido > 0L) setChanged();
            QuadroEletricoNetworking.atualizarObservadores(this);
        }

        private void desarmar(ServerLevel servidor, CircuitoEletrico c) {
            c.estado = CircuitoEletrico.DESARMADO;
            c.ticksSobrecarregado = c.ticksCaboSobrecarregado = 0L;
            setChanged();
            servidor.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.8F, 0.6F);
        }

        public void desligarSaidas() {
            for (var c : circuitos) c.consumoWAtual = 0L;
            for (var carga : cargas.values()) carga.energizar(false);
            for (var tomada : tomadas) { tomada.tomada().energizar(false); tomada.tomada().setCircuito(-1); }
        }

        public int circuitoDoPonto(BlockPos pos) { return circuitoCondutor.getOrDefault(pos, -1); }

        private final Map<BlockPos, CargaAlcancada> cargas = new HashMap<>();
        private final Map<BlockPos, NoRede> caminhosCargas = new HashMap<>();
        private record CargaAlcancada(BlockPos pos, BlockEntity be, int circuito) {
            boolean valida(ServerLevel level) { return level.isLoaded(pos) && level.getBlockEntity(pos) == be && !be.isRemoved(); }
            long consumoW() {
                if (be instanceof SoqueteTetoBlock.SoqueteBlockEntity soquete) return soquete.consumoW();
                if (be instanceof LedPotenciaBlockEntity led) return led.consumoW();
                return 0L;
            }
            void energizar(boolean ligada) {
                if (be.isRemoved() || be.getLevel() == null || !be.getLevel().isLoaded(pos)
                        || be.getLevel().getBlockEntity(pos) != be) return;
                if (be instanceof SoqueteTetoBlock.SoqueteBlockEntity soquete) {
                    if (ligada) soquete.acender(); else soquete.apagar();
                } else if (be instanceof LedPotenciaBlockEntity led) led.energizar(ligada);
            }
        }

        private long ultimoExtraido = -1L;
        private long ultimoDemandado = -1L;

        private boolean demandaMudou(long extraido, long demandado) {
            boolean mudou = extraido != ultimoExtraido || demandado != ultimoDemandado;
            ultimoExtraido = extraido;
            ultimoDemandado = demandado;
            return mudou;
        }

        private record NoRede(BlockPos pos, int circuito, long limiteA, NoRede anterior,
                EloCabo elo) {}
        private final Map<BlockPos, Integer> circuitoCondutor = new HashMap<>();

        public int circuitoDoCabo(BlockPos posCabo) {
            return circuitoCondutor.getOrDefault(posCabo, -1);
        }

        public int circuitoDoCondutor(BlockPos pos) {
            return circuitoDoCabo(pos);
        }

        /** Um soquete alcançado, circuito e limite do melhor caminho de cabo. */
        public record SoqueteAlcancado(BlockPos pos,
                SoqueteTetoBlock.SoqueteBlockEntity soquete, int circuito,
                long limiteBitolaA) {}

        /** Uma tomada alcançada pela rede e o circuito que a alimenta. */
        public record TomadaAlcancada(BlockPos pos,
                TomadaBlock.TomadaBlockEntity tomada, int circuito) {}

        /** Menor ampacidade nos caminhos com carga de cada circuito. */
        private final long[] limiteBitolaA = new long[]{0L, 0L, 0L, 0L};

        /** O limite de corrente da bitola mínima do circuito (a GUI mostra). */
        public long limiteBitolaA(int circuito) {
            return (circuito >= 0 && circuito < limiteBitolaA.length)
                    ? limiteBitolaA[circuito] : 0L;
        }

        /**
         * O soquete está alcançável pela última travessia? (diagnóstico e
         * gametest — a travessia precisa ter corrido antes)
         */
        public boolean reachTest(BlockPos posSoquete) {
            return circuitoDoSoquete(posSoquete) >= 0;
        }

        /** Circuito que alcançou o soquete (-1 = sem ligação). */
        public int circuitoDoSoquete(BlockPos posSoquete) {
            for (SoqueteAlcancado s : soquetes) {
                if (s.pos().equals(posSoquete)) {
                    return s.circuito();
                }
            }
            return -1;
        }

        // ==================================================== PERSISTÊNCIA

        public void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
            com.intoxicantes.energia.BufferQuadro.save(buffer, output.child("buffer"));
            var cs = output.child("circuitos");
            for (int i = 0; i < circuitos.size(); i++) {
                circuitos.get(i).save(cs.child("c" + i));
            }
        }

        public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
            com.intoxicantes.energia.BufferQuadro.load(buffer, input.childOrEmpty("buffer"));
            var cs = input.childOrEmpty("circuitos");
            for (int i = 0; i < circuitos.size(); i++) {
                circuitos.get(i).load(cs.childOrEmpty("c" + i));
            }
        }

        @Override
        public void setRemoved() {
            desligarSaidas();
            if (level instanceof ServerLevel servidor) {
                // LevelChunk registra a BE nova ANTES de remover a antiga no mesmo ponto.
                if (servidor.isLoaded(worldPosition)
                        && servidor.getChunkAt(worldPosition).getBlockEntities().get(worldPosition)
                                instanceof QuadroEletricoBlockEntity substituto
                        && substituto != this) substituto.registrar();
                else EnergiaRedes.esquecerQuadro(servidor, worldPosition);
                EnergiaRedes.marcarRebuildProximo(servidor, worldPosition, RAIO_REDE);
            }
            super.setRemoved();
        }

        @Override
        public void setLevel(Level nivel) {
            super.setLevel(nivel);
            registrar();
        }

        /** O load do chunk re-registra o quadro na rede (topologia reconstruída). */
        @Override
        public void clearRemoved() {
            super.clearRemoved();
            if (level instanceof ServerLevel servidor) {
                registrar();
            }
        }
    }
}
