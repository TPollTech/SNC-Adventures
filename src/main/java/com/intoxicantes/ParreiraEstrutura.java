package com.intoxicantes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Descobre a armação existente, sem colocar blocos nem alterar cercas. */
public final class ParreiraEstrutura {
    public static final int ALTURA_MAXIMA = 3;
    public static final int LARGURA_COBERTURA = 5;
    /** O poste pode ficar num canto: distância máxima até a outra ponta do teto. */
    public static final int RAIO_COBERTURA = LARGURA_COBERTURA - 1;
    public static final int MAX_APOIOS_COBERTURA = 25;
    public static final int RAIO_BUSCA_RAIZ = LARGURA_COBERTURA;
    private static final Direction[] HORIZONTAIS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private ParreiraEstrutura() {}

    /** Todas as posições são absolutas; as listas são imutáveis e nunca nulas. */
    public record Estrutura(BlockPos raiz, Direction direcao, List<BlockPos> coluna,
            List<BlockPos> cobertura, List<BlockPos> apoios, int altura) {
        public Estrutura {
            raiz = raiz.immutable();
            coluna = List.copyOf(coluna);
            cobertura = List.copyOf(cobertura);
            apoios = List.copyOf(apoios);
        }

        public boolean produtiva() {
            return altura >= 2 && cobertura.size() >= 2;
        }

        public boolean contem(BlockPos pos) {
            return apoios.contains(pos);
        }

        public BlockPos topo() {
            return coluna.isEmpty() ? raiz : coluna.getLast();
        }
    }

    public static Estrutura vazia(BlockPos raiz) {
        return new Estrutura(raiz, Direction.NORTH, List.of(), List.of(), List.of(), 0);
    }

    /**
     * Coluna cardeal adjacente, até três cercas contínuas, e somente conexões
     * horizontais reais no topo. Nunca pede um chunk que ainda não está carregado.
     * Havendo várias armações, prefere a produtiva, depois a maior; empate segue
     * a ordem norte/leste/sul/oeste, igual nos dois lados da rede.
     */
    public static Estrutura buscar(LevelReader level, BlockPos raiz) {
        if (level == null || !level.hasChunkAt(raiz)) {
            return vazia(raiz);
        }
        Estrutura melhor = vazia(raiz);
        for (Direction lado : HORIZONTAIS) {
            BlockPos inicio = raiz.relative(lado);
            List<BlockPos> coluna = new ArrayList<>();
            for (int i = 0; i < ALTURA_MAXIMA; i++) {
                BlockPos pos = inicio.above(i);
                if (!cercaCarregada(level, pos)) {
                    break;
                }
                coluna.add(pos.immutable());
            }
            if (coluna.isEmpty()) {
                continue;
            }
            BlockPos topo = coluna.getLast();
            List<BlockPos> cobertura = buscarCobertura(level, topo);
            Set<BlockPos> todos = new LinkedHashSet<>(coluna);
            todos.addAll(cobertura);
            Estrutura atual = new Estrutura(raiz, lado, coluna, cobertura,
                    new ArrayList<>(todos), coluna.size());
            if (pontuacao(atual) > pontuacao(melhor)) {
                melhor = atual;
            }
        }
        return melhor;
    }

    private static int pontuacao(Estrutura e) {
        return (e.produtiva() ? 1000 : 0) + e.altura() * 30 + e.cobertura().size();
    }

    private static List<BlockPos> buscarCobertura(LevelReader level, BlockPos topo) {
        Set<BlockPos> vistos = new LinkedHashSet<>();
        ArrayDeque<BlockPos> fila = new ArrayDeque<>();
        vistos.add(topo);
        fila.add(topo);
        int minX = topo.getX(), maxX = topo.getX();
        int minZ = topo.getZ(), maxZ = topo.getZ();
        while (!fila.isEmpty() && vistos.size() < MAX_APOIOS_COBERTURA) {
            BlockPos atual = fila.removeFirst();
            BlockState estado = level.getBlockState(atual);
            for (Direction lado : HORIZONTAIS) {
                BlockPos proximo = atual.relative(lado);
                if (Math.abs(proximo.getX() - topo.getX()) > RAIO_COBERTURA
                        || Math.abs(proximo.getZ() - topo.getZ()) > RAIO_COBERTURA
                        || vistos.contains(proximo) || !cercaCarregada(level, proximo)) {
                    continue;
                }
                int novoMinX = Math.min(minX, proximo.getX()), novoMaxX = Math.max(maxX, proximo.getX());
                int novoMinZ = Math.min(minZ, proximo.getZ()), novoMaxZ = Math.max(maxZ, proximo.getZ());
                if (novoMaxX - novoMinX >= LARGURA_COBERTURA || novoMaxZ - novoMinZ >= LARGURA_COBERTURA) {
                    continue;
                }
                // Madeira e cerca de tijolos do Nether, por exemplo, não se unem.
                if (!conecta(estado, lado)
                        || !conecta(level.getBlockState(proximo), lado.getOpposite())) {
                    continue;
                }
                vistos.add(proximo.immutable());
                fila.addLast(proximo.immutable());
                minX = novoMinX;
                maxX = novoMaxX;
                minZ = novoMinZ;
                maxZ = novoMaxZ;
                if (vistos.size() == MAX_APOIOS_COBERTURA) {
                    break;
                }
            }
        }
        return List.copyOf(vistos);
    }

    public static boolean cercaCarregada(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof FenceBlock;
    }

    /**
     * O renderer solicita os donos de toda sua estrutura em uma única coleta.
     * Cada apoio tem uma dona: raiz mais próxima; empate usa o menor asLong.
     * Só disputa o apoio que seus ramos já alcançaram; colheita e recuperação
     * preservam etapaVisual e não trocam a dona para outra planta.
     */
    public static Map<BlockPos, BlockPos> donosDosApoios(LevelReader level, Estrutura referencia) {
        if (level == null || referencia.apoios().isEmpty()) {
            return Map.of();
        }
        List<Candidata> candidatas = coletarEstruturas(level, referencia.apoios(), referencia);
        Map<BlockPos, BlockPos> donos = new LinkedHashMap<>();
        for (BlockPos apoio : referencia.apoios()) {
            BlockPos dona = escolherDona(candidatas, apoio);
            if (dona != null) {
                donos.put(apoio, dona);
            }
        }
        return Map.copyOf(donos);
    }

    /** Mesma regra do renderer, aplicada ao único apoio clicado no servidor. */
    public static BlockPos donoDoApoio(LevelReader level, BlockPos apoio) {
        if (level == null || !cercaCarregada(level, apoio)) {
            return null;
        }
        return escolherDona(coletarEstruturas(level, List.of(apoio), null), apoio);
    }

    private record Candidata(Estrutura estrutura, int etapa) {
        boolean alcanca(BlockPos apoio) {
            if (etapa < 2) {
                return false;
            }
            int coluna = estrutura.coluna().indexOf(apoio);
            if (coluna >= 0 && (etapa >= 3 || coluna < etapa)) {
                return true;
            }
            if (etapa < 3) {
                return false;
            }
            int teto = estrutura.cobertura().indexOf(apoio);
            int limite = etapa == 3 ? (estrutura.cobertura().size() + 1) / 2 : estrutura.cobertura().size();
            return teto >= 0 && teto < limite;
        }
    }

    private static BlockPos escolherDona(List<Candidata> candidatas, BlockPos apoio) {
        BlockPos dona = null;
        double distancia = Double.MAX_VALUE;
        for (Candidata candidata : candidatas) {
            if (!candidata.alcanca(apoio)) {
                continue;
            }
            BlockPos raiz = candidata.estrutura().raiz();
            double atual = raiz.distSqr(apoio);
            if (atual < distancia || (atual == distancia && dona != null
                    && raiz.asLong() < dona.asLong())) {
                dona = raiz;
                distancia = atual;
            }
        }
        return dona;
    }

    /** Lê somente raízes em chunks existentes e descobre cada estrutura uma vez. */
    private static List<Candidata> coletarEstruturas(LevelReader level, List<BlockPos> apoios,
            Estrutura referencia) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : apoios) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        List<Candidata> resultado = new ArrayList<>();
        int raio = RAIO_BUSCA_RAIZ;
        if (level instanceof Level mundo) {
            // Os chunks do servidor migram raízes antigas antes do envio; toda
            // raiz real tem BE. Consultar esses mapas custa poucas entradas,
            // em vez de varrer mais de mil estados por quadro do renderer.
            for (int cx = (minX - raio) >> 4; cx <= (maxX + raio) >> 4; cx++) {
                for (int cz = (minZ - raio) >> 4; cz <= (maxZ + raio) >> 4; cz++) {
                    if (!mundo.hasChunk(cx, cz)) {
                        continue;
                    }
                    for (var entidade : mundo.getChunk(cx, cz).getBlockEntities().values()) {
                        if (!(entidade instanceof ParreiraBlockEntity be) || be.isRemoved()) {
                            continue;
                        }
                        BlockPos raiz = be.getBlockPos();
                        if (raiz.getX() < minX - raio || raiz.getX() > maxX + raio
                                || raiz.getZ() < minZ - raio || raiz.getZ() > maxZ + raio
                                || raiz.getY() < minY - ALTURA_MAXIMA + 1 || raiz.getY() > maxY
                                || !(mundo.getBlockState(raiz).getBlock() instanceof UvaParreiraBlock)) {
                            continue;
                        }
                        Estrutura estrutura = referencia != null && referencia.raiz().equals(raiz)
                                ? referencia : buscar(level, raiz);
                        if (!estrutura.apoios().isEmpty()) {
                            resultado.add(new Candidata(estrutura, be.etapaVisual()));
                        }
                    }
                }
            }
            return resultado;
        }
        for (int x = minX - raio; x <= maxX + raio; x++) {
            for (int z = minZ - raio; z <= maxZ + raio; z++) {
                if (!level.hasChunkAt(new BlockPos(x, minY, z))) {
                    continue;
                }
                for (int y = minY - ALTURA_MAXIMA + 1; y <= maxY; y++) {
                    BlockPos raiz = new BlockPos(x, y, z);
                    BlockState estado = level.getBlockState(raiz);
                    if (!(estado.getBlock() instanceof UvaParreiraBlock)) {
                        continue;
                    }
                    Estrutura estrutura = referencia != null && referencia.raiz().equals(raiz)
                            ? referencia : buscar(level, raiz);
                    if (!estrutura.apoios().isEmpty()) {
                        resultado.add(new Candidata(estrutura, estado.getValue(UvCropBlock.AGE)));
                    }
                }
            }
        }
        return resultado;
    }

    private static boolean conecta(BlockState state, Direction direcao) {
        BooleanProperty propriedade = switch (direcao) {
            case NORTH -> FenceBlock.NORTH;
            case EAST -> FenceBlock.EAST;
            case SOUTH -> FenceBlock.SOUTH;
            case WEST -> FenceBlock.WEST;
            default -> throw new IllegalArgumentException("A ligação precisa ser horizontal");
        };
        return state.getValue(propriedade);
    }
}
