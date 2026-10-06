package com.intoxicantes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Sistema do Mercado Esquinão do Gago.
 * - Gago atende 24h, nunca some
 * - v1.2.76: o plantão é SEMPRE o balcão (o guichê da porta às 00:00 foi
 *   extinto — o dono da esquina não muda de posto, e a porta-grade ficou
 *   24h aberta como o letreiro "ABERTO · 24H" promete)
 * - Clock na action bar perto do mercado
 * - Posição do mercado é PERSISTENTE (NBT no diretório do mundo) e, se o admin
 *   nunca setou, o sistema acha a estrutura mercado_gago sozinho (igual /locate)
 */
public final class MarketSystem {
    private static final int MARKET_RANGE = 32;
    /** v1.2.45: o relógio roda no overworld TODO (o HUD mantém a última mensagem). */
    // v1.2.58: CLOCK_INTERVAL removido junto com o actionbar do relógio
    private static final int MANAGE_INTERVAL = 100;
    private static final int DISCOVER_INTERVAL = 600; // 30s entre tentativas de achar a estrutura
    private static final int SCAN_BUDGET_POR_TENTATIVA = 600; // chunks NOVOS por tentativa (o cursor nunca revista)
    private static final int DISCOVER_BACKOFF = 12000; // rodada completa sem achar: re-tenta daqui a 10 min
    private static final int DISCOVER_RADIUS_CHUNKS = 24;
    private static final int CLOCK_COOLDOWN = 60;     // action bar no max 1x a cada 3s por player
    private static final String DATA_FILE = "intoxicantes_market.dat";

    /**
     * Offsets locais (rotacao NONE) a partir do CENTRO do template (x7,z5):
     * - porta: v1.2.51 — o posto da madrugada é ATRÁS DO GUICHÊ (x7, z8):
     *   1 bloco DENTRO da loja, encostado na porta-grade; de madrugada o
     *   guichê fecha (embaixo sólido, em cima grade) e ele atende POR TRÁS,
     *   visível através da grade de ferro — igual bodega de esquina kkkk.
     *   (Antes era o alpendre z10 — na FRENTE da porta: com o guichê fechado
     *   o dono ficaria trancado FORA da própria loja.)
     * - balcao: atras do balcão (x=4, z=3) — onde o Gago nasce pelo template
     * O jigsaw gira os DOIS junto com a rotacao real da estrutura.
     */
    private static final net.minecraft.core.Vec3i PORTA_LOCAL = new net.minecraft.core.Vec3i(0, 0, 3);
    private static final net.minecraft.core.Vec3i BALCAO_LOCAL = new net.minecraft.core.Vec3i(-3, 0, -2);

    @Nullable
    private static BlockPos marketPos = null;
    /**
     * v1.2.25 — REGIÃO DO MERCADO: qual pele o template nasceu (classico /
     * sertao / serra). O descobridor preenche pela chave real da estrutura;
     * o /gagomarket rebuild e o gen_mercado.py gravam na tag. "" = clássico
     * (mundo velho: o prédio branco original do SUL DISTRIBUIDORA).
     */
    private static String regiaoMercado = "";
    /** Offsets JA girados (padrao: sem rotacao). */
    private static net.minecraft.core.Vec3i portaOffset = PORTA_LOCAL;
    private static net.minecraft.core.Vec3i balcaoOffset = BALCAO_LOCAL;
    private static java.util.UUID gagoAtivoId = null; // evita spawn duplicado quando o Gago sai puto
    private static boolean discovered = false; // achou a estrutura? (nao tenta de novo)
    /** Cursor da espiral: anel atual e posicao dentro dele (nunca revista chunk). */
    private static int varreduraRaio = 0;
    private static int varreduraIndice = 0;
    private static boolean avisouVarreduraCompleta = false;
    /** Centro da espiral fixado na sessao: o cursor so faz sentido com centro fixo. */
    @Nullable
    private static ChunkPos centroVarredura = null;
    /** v1.2.51: estado do GUICHÊ conhecido (a porta-grade) — detecção da virada. */
    private static boolean viradaNaPortaAnterior = false;
    /** v1.2.19: a 1a avaliação pós-boot só sincroniza (sem tocar o arpejo). */
    private static boolean viradaIniciada = false;
    private static int manageCooldown = 0;
    private static int discoverCooldown = 0;

    /**
     * v1.2.19 — FILA DE NOTAS: o tell()/TickTask do server saiu no 1.26.3,
     * então os jingles do mod (arpejo da virada do letreiro, blips de leitura)
     * entram aqui com o tick-alvo e são drenados no fim de cada tick.
     */
    private record NotaPendente(long tick, ServerLevel level, BlockPos pos,
                                SoundEvent som, float volume, float pitch) {}

    private static final List<NotaPendente> notasPendentes = new ArrayList<>();

    /** O ZELADOR passa a cada 30s (600 ticks) — árvore invasora não espera. */
    private static final int ZELADOR_INTERVALO = 600;
    /**
     * v1.2.27: a PRIMEIRA passada depois de achar o mercado é IMEDIATA (e as
     * duas seguintes em 5s) — o mundo de verdade chegou com placa de 1 bloco
     * e poste mangrado; não tem por que esperar 30s pra começar a autocura.
     */
    private static final int[] ZELADOR_TURBO = {100, 100};
    private static int zeladorTurboIdx = 0;



    private static int zeladorCooldown = 0;

    private MarketSystem() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel overworld = server.overworld();
            if (overworld == null) return;

            // v1.2.19: toca as notas da fila que chegaram à hora (e só elas)
            if (!notasPendentes.isEmpty()) {
                long agora = overworld.getGameTime();
                Iterator<NotaPendente> it = notasPendentes.iterator();
                while (it.hasNext()) {
                    NotaPendente nota = it.next();
                    if (agora >= nota.tick()) {
                        it.remove();
                        nota.level().playSound(null, nota.pos(),
                                nota.som(), SoundSource.BLOCKS, nota.volume(), nota.pitch());
                    }
                }
            }

            // 1) Se ainda nao tem posicao, tenta achar a estrutura (espiral de chunks)
            if (marketPos == null) {
                if (!discovered) {
                    if (--discoverCooldown <= 0) {
                        discoverCooldown = DISCOVER_INTERVAL;
                        tryDescobrirEstrutura(overworld);
                    }
                }
                if (marketPos == null) return; // sem posicao ainda: nada a fazer
            }

            BlockPos pos = marketPos;

            // 2) v1.2.19: a virada ABERTO/FECHADO do letreiro (barata: 1 bloco
            // getBlockEntity por tick)
            avaliarViradaDoMercado(overworld, pos);

        // 2.2) v1.2.20: o ZELADOR DA PROPRIEDADE (árvore invasora) — 1x/30s
        if (--zeladorCooldown <= 0) {
            // v1.2.27: as duas primeiras repetições são rápidas (5s);
            // depois cai no ritmo normal de 30s
            zeladorCooldown = zeladorTurboIdx < ZELADOR_TURBO.length
                    ? ZELADOR_TURBO[zeladorTurboIdx++] : ZELADOR_INTERVALO;
            zeladorDaPropriedade(overworld, pos);
        }

        // 2.3) v1.2.69: O DIA DA ENTREGA (quarta) — o anúncio do caminhão do
        // mercadinho (1x/semana, no amanhecer do dia de quarta)
        anunciarDiaDaEntrega(overworld, pos);

            // 2.1) v1.2.58: o RELÓGIO DO MERCADO no actionbar MORREU — o HUD do
            // canto (RelogioHud) já informa a hora do mundo direto, sem spam
            // periódico em cima da hotbar.

            // 3) Gerenciador do Gago (throttle de 5s)
            if (--manageCooldown > 0) return;
            manageCooldown = MANAGE_INTERVAL;
            gerenciarGago(overworld, pos);
            gerenciarJuca(overworld, pos);
        });
    }

    /**
     * v1.2.19: agenda uma nota (jingle da virada, blip de leitura) pra tocar
     * dentro de alguns ticks — substituto do server.tell() que saiu no 1.26.3.
     */
    public static void agendarNota(ServerLevel level, BlockPos pos, SoundEvent som,
                                   float volume, float pitch, int atrasoTicks) {
        notasPendentes.add(new NotaPendente(
                level.getGameTime() + Math.max(0, atrasoTicks), level, pos, som, volume, pitch));
    }

    /**
     * v1.2.20 — O ZELADOR DO ESQUINÃO: a decoração do bioma roda DEPOIS da
     * estrutura na geração, então árvore nascida no chunk vizinho enfia copa
     * e tronco pra dentro da loja (o ar limpo do template só cobre o próprio
     * box — o tester flagrou FOLHA DENTRO do prédio). A cada 30s, varre a
     * propriedade (caixa do template: ±10 x, 8 y, ±10 z do centro) e EXPULSA
     * material de árvore: folhas, troncos, mudas e bambu — sem drop (nada de
     * 40 folhas nascendo no chão do freguês). Só age em chunk carregado;
     * custo desprezível (uma varredura de 3.5k blocos por 30s).
     *
     * v1.2.24 — e TAMBÉM é o concerto de mundos velhos: a placa do letreiro
     * mudou de anatomia (9 blocos: 2 torres de 4 + painel suspenso) e a velha
     * (1.2.18–1.2.23) não tem a marca "nova" no NBT. Qualquer passada desmonta
     * SÓ as velhas — chunk longe é limpo assim que carrega.
     */
    /**
     * v1.2.53: a parede do mercado na PELE da região (o zelador usa pra
     * devolver as paredes que nasceram porta no bug do "G" duplicado).
     * Na 26.3 os blocos coloridos são ColorCollection — pick(DyeColor).
     */
    private static net.minecraft.world.level.block.Block paredeDaRegiao() {
        return switch (regiaoMercado) {
            case "sertao" -> net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA
                    .pick(net.minecraft.world.item.DyeColor.ORANGE);
            case "serra" -> net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS;
            default -> net.minecraft.world.level.block.Blocks.CONCRETE
                    .pick(net.minecraft.world.item.DyeColor.GREEN);
        };
    }

    static void zeladorDaPropriedade(ServerLevel level, BlockPos pos) {
        BlockPos base = pos.offset(-10, 0, -10);
        // v1.2.55: a posição REAL da porta (metade-superior dela) — fora
        // dali, porta-grade é parede que nasceu porta (bug do "G" duplicado)
        BlockPos portaReal = posPortaReal(pos).above();
        for (int dx = 0; dx < 21; dx++) {
            for (int dy = 0; dy < 8; dy++) {
                for (int dz = 0; dz < 21; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) continue; // chunk fora: próxima passada
                    BlockState estado = level.getBlockState(p);
                    if (estado.is(net.minecraft.tags.BlockTags.LEAVES)
                            || estado.is(net.minecraft.tags.BlockTags.LOGS)
                            || estado.is(net.minecraft.tags.BlockTags.SAPLINGS)
                            || estado.is(net.minecraft.world.level.block.Blocks.BAMBOO)) {
                        level.destroyBlock(p, false);
                    }
                    // v1.2.24: placa VELHA (sem a marca "nova" no NBT) cai na
                    // mesma passada — as irmãs velhas caem junto com ela
                    else if (estado.getBlock() instanceof PlacaEsquinaoBlock
                            && level.getBlockEntity(p) instanceof PlacaEsquinaoBlockEntity be
                            && !be.isNova()) {
                        level.destroyBlock(p, false);
                    }
                    // v1.2.53/1.2.55: conserto do mercado "porta" — o gerador
                    // 1.2.51 definia o char "G" (parede) DUAS vezes na paleta e
                    // a metade-superior da porta venceu: as PAREDES nasceram
                    // porta. Como as falsas empilham (colunas de upper), a
                    // checagem "de baixo não é porta" não pega nada — a regra
                    // certa é POSICIONAL: só é porta de verdade a metade-superior
                    // que está EXATAMENTE sobre a porta real (portaOffset, girada
                    // igual à estrutura). Toda outra porta-grade vira parede da
                    // região — sem tocar na porta genuína.
                    else if (estado.getBlock() instanceof PortaGradeBlock
                            && estado.getValue(PortaGradeBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                            && !p.equals(portaReal)) {
                        level.setBlockAndUpdate(p, paredeDaRegiao().defaultBlockState());
                    }
                }
            }
        }
        // v1.2.25: e REFORMA o pátio velho (faixa + hidrantes) quando o chão
        // já é o asfalto do mercado — mundos 1.2.23/1.2.24 ganham a travessia
        if (level.isLoaded(pos)) {
            reformarPatio(level, pos);
            // v1.2.51: reforma a ENTRADA (porta vanilla → porta-grade + mini
            // display) nos mundos 1.2.50 e anteriores
            reformarEntrada(level, pos);
        }
    }

    /**
     * v1.2.55: a posição da porta REAL (metade LOWER) — template (x7, z9),
     * ou seja local (0, 0, 4), girado pela rotação da estrutura. Fonte única
     * usada pelo reformarEntrada (que coloca a porta) e pelo zelador
     * (que agora protege EXATAMENTE ela e derruba as falsas).
     */
    private static BlockPos posPortaReal(BlockPos centro) {
        net.minecraft.core.Vec3i g = girar(new net.minecraft.core.Vec3i(0, 0, 4),
                rotacaoDaPorta(portaOffset));
        return new BlockPos(centro.getX() + g.getX(), centro.getY(),
                centro.getZ() + g.getZ());
    }

    /**
     * v1.2.69 — O DIA DA ENTREGA: na virada pra QUARTA-FEIRA (amanhecer do
     * dia comercial), quem está perto do mercado lê no chat que o caminhão
     * do mercadinho chegou — a prateleira repõe o estoque de partida (as
     * BEs das gôndolas detectam a mesma virada no tick delas, cada uma na
     * sua hora, sem NBT global nenhum). 1x por semana, disparo único.
     */
    private static void anunciarDiaDaEntrega(ServerLevel level, BlockPos pos) {
        long dayTime = CalendarioEsquinao.tempoTotal(level);
        int dia = CalendarioEsquinao.diaDaSemana(dayTime);
        long semana = Math.floorDiv(DailyTradeStock.tradingDay(dayTime), 7L);
        if (dia != CalendarioEsquinao.DIA_ENTREGA || semana == ultimaSemanaAnunciada) {
            return;
        }
        ultimaSemanaAnunciada = semana;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) < 32 * 32) {
                player.sendSystemMessage(Component.translatable(
                        "prateleira.intoxicantes.entrega_hoje"));
            }
        }
    }

    /** A semana comercial do último anúncio de entrega (disparo único/semana). */
    private static long ultimaSemanaAnunciada = Long.MIN_VALUE;

    /**
     * v1.2.19 — O ZELADOR DO HORÁRIO (refeito na v1.2.51): o mercado é 24h,
     * então NÃO existe mais "virada ABERTO/FECHADO" (era código morto: o
     * abertoAgora=true cravado nunca disparava nada). v1.2.76: o guichê da
     * madrugada também foi — a porta-grade NÃO fecha sozinha mais (o dono
     * fecha na mão, no clique direito). O que fica vivo aqui, a cada passada:
     * 1) reforça os POSTES DE LUZ do pátio (anti-fuso: o scheduleTick do
     *    poste pode atrasar com o chunk longe);
     * 2) religa o mini display "ABERTO · 24H" e conserta o pé do poste.
     */
    private static void avaliarViradaDoMercado(ServerLevel overworld, BlockPos pos) {
        boolean virada = !viradaIniciada || estaNaPorta(overworld) != viradaNaPortaAnterior;
        if (virada) {
            viradaIniciada = true;
            viradaNaPortaAnterior = estaNaPorta(overworld);
        } else if (overworld.getGameTime() % 1000L != 0L) {
            // sem virada: a varredura (3.5k leituras) só roda na virada do
            // dia e como reforço 1×/hora do jogo — NUNCA por tick
            return;
        }

        // a estrutura GIRA na geração, então a caixa é SIMÉTRICA em torno do
        // centro do prédio (meia-extensão máx do template 15×19 = ±10). Uma
        // varredura por passada (a cada tick é barata demais pra valer o
        // custo; o Zelador de 30s cobre o resto): acha a porta-grade, o
        // mini display e os postes.
        BlockPos base = pos.offset(-10, 0, -10);
        for (int dx = 0; dx < 21; dx++) {
            for (int dy = 0; dy < 8; dy++) {
                for (int dz = 0; dz < 21; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState estado = overworld.getBlockState(p);
                    if (estado.getBlock() instanceof PosteLuzBlock
                            && estado.getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.BASE) {
                        boolean alvo = PosteLuzBlock.estaNoHorarioDeLuz(overworld);
                        if (estado.getValue(PosteLuzBlock.LIT) != alvo) {
                            overworld.setBlockAndUpdate(p,
                                    estado.setValue(PosteLuzBlock.LIT, alvo));
                        }
                        PosteLuzBlock.agendaProximaVirada(overworld, p);
                        // v1.2.27: autocura da coluna — reconstrói corpo/topo
                        // faltantes no eixo (só a partir da BASE: chamar pra
                        // cada peça cresceria coluna infinita). Roda 1x/30s,
                        // custo zero (mesma varredura da virada).
                        PosteLuzBlock.selfHealAPartirDaBase(overworld, p);
                    } else if (estado.getBlock() instanceof PosteLuzBlock
                            && PosteLuzBlock.eDesgarrada(overworld, p)) {
                        // v1.2.27: peça FORA DO EIXO (o erro do template da
                        // 1.2.24 deixou um corpo 1 bloco pro lado, o "negócio
                        // preto flutuando" do print) — recolhida sem drop
                        overworld.destroyBlock(p, false);
                    } else if (estado.getBlock() == IntoxicantesMod.PAINEL_LED
                            && overworld.getBlockEntity(p) instanceof PainelLedBlockEntity mini
                            && mini.getLinhas().equals(java.util.List.of("ABERTO", "· 24H ·"))) {
                        // v1.2.51: o MINI DISPLAY “ABERTO · 24H” nunca dorme
                        // (defesa: se o freguês apagou o LED pela Central, o
                        // dono da esquina religa — a esquina NUNCA “fecha”)
                        if (!estado.getValue(PainelLedBlock.LIT)) {
                            overworld.setBlockAndUpdate(p,
                                    estado.setValue(PainelLedBlock.LIT, Boolean.TRUE));
                        }
                    }
                }
            }
        }
    }

    // ==================================================== DESCOBERTA DA ESTRUTURA

    /**
     * Procura o mercado numa espiral de chunks (estilo /locate), com CURSOR
     * PROGRESSIVO: cada tentativa gasta no maximo SCAN_BUDGET_POR_TENTATIVA
     * chunks NOVOS (nunca revista o que ja descartou) e o anel/indice ficam
     * guardados pra continuar de onde parou. Uma rodada completa (raio 24 ~
     * 2401 chunks, ~4 tentativas) sem achar entra em backoff de 10 min antes
     * de recomecar.
     *
     * v1.2.25: as TRES peles do mercado (classico/sertao/serra) compartilham
     * a mesma distribuição de structure_set — cada chunk da espiral é testado
     * contra as 3 estruturas e a que responde define a região do prédio.
     */
    private static void tryDescobrirEstrutura(ServerLevel level) {
        // Mundos com geração de estruturas desligada não têm mercado natural.
        // Não forçar centenas de chunks nesses mundos (inclusive superplanos).
        if (!level.getServer().getWorldGenSettings().options().generateStructures()) {
            discovered = true;
            return;
        }
        var registro = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        // caminho da chave -> estrutura (só entram as que têm JSON no datapack)
        Map<String, Structure> estruturas = new LinkedHashMap<>();
        for (String caminho : List.of("mercado_gago", "mercado_gago_sertao", "mercado_gago_serra")) {
            ResourceKey<Structure> chave = ResourceKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, caminho));
            registro.get(chave).ifPresent(h -> estruturas.put(caminho, h.value()));
        }
        if (estruturas.isEmpty()) {
            // JSON de worldgen ausente: desiste de tentar (log uma vez por boot)
            discovered = true;
            return;
        }
        // centro fixo por sessao: se o spawn mudar no meio, a espiral NAO escorrega
        if (centroVarredura == null) {
            centroVarredura = ChunkPos.containing(level.getRespawnData().pos());
        }
        ChunkPos centro = centroVarredura;
        var manager = level.structureManager();
        int checados = 0;
        while (varreduraRaio <= DISCOVER_RADIUS_CHUNKS && checados < SCAN_BUDGET_POR_TENTATIVA) {
            int[] d = chunkDoAnel(varreduraRaio, varreduraIndice);
            int cx = centro.x() + d[0];
            int cz = centro.z() + d[1];
            for (var entrada : estruturas.entrySet()) {
                List<StructureStart> starts = manager.startsForStructure(cx, cz, entrada.getValue());
                for (StructureStart start : starts) {
                    if (start != null && start.isValid()) {
                        var bb = start.getBoundingBox();
                        // centro no PISO da estrutura (minY), nao no meio do telhado
                        setMarketPos(new BlockPos(
                                bb.minX() + bb.getXSpan() / 2,
                                bb.minY(),
                                bb.minZ() + bb.getZSpan() / 2));
                        descobrirOffsets(start);
                        // v1.2.25: a PELE do prédio vem da chave que respondeu
                        String caminho = entrada.getKey();
                        regiaoMercado = caminho.startsWith("mercado_gago_")
                                ? caminho.substring("mercado_gago_".length())
                                : "classico";
                        discovered = true;
                        IntoxicantesMod.LOGGER.info("[Market] Mercado Esquinao descoberto em {} — regiao '{}' (varredura: {} chunks)",
                                marketPos.toShortString(), regiaoMercado,
                                chunksVarridos(varreduraRaio, varreduraIndice));
                        anunciarDescoberta(level);
                        return;
                    }
                }
            }
            checados++;
            avancarCursor();
        }
        if (varreduraRaio > DISCOVER_RADIUS_CHUNKS) {
            // rodada completa sem achar (setado errado? removeram o worldgen?):
            // reinicia a espiral e espera o backoff — a estrutura pode aparecer
            // num /gagomarket set ou num datapack carregado depois
            if (!avisouVarreduraCompleta) {
                avisouVarreduraCompleta = true;
                IntoxicantesMod.LOGGER.warn("[Market] espiral completa (raio {}) sem achar mercado_gago; recomecando em {}s",
                        DISCOVER_RADIUS_CHUNKS, DISCOVER_BACKOFF / 20);
            }
            varreduraRaio = 0;
            varreduraIndice = 0;
            discoverCooldown = DISCOVER_BACKOFF;
        }
    }

    /** Chunk `indice` do anel `raio` em espiral (raio 0 = so o centro). Ordem estavel. */
    private static int[] chunkDoAnel(int raio, int indice) {
        if (raio == 0) return new int[]{0, 0};
        int lado = 2 * raio;
        int perimetro = 4 * lado;
        int i = indice % perimetro; // defesa: indice nunca passa do perimetro
        // topo (esq->dir), direita (cima->baixo), base (dir->esq), esquerda (baixo->cima)
        if (i < lado) return new int[]{-raio + i, -raio};
        i -= lado;
        if (i < lado) return new int[]{raio, -raio + i};
        i -= lado;
        if (i < lado) return new int[]{raio - i, raio};
        i -= lado;
        return new int[]{-raio, raio - i};
    }

    /** Avanca o cursor da espiral (indice dentro do anel, depois o anel). */
    private static void avancarCursor() {
        varreduraIndice++;
        if (varreduraIndice >= tamanhoAnel(varreduraRaio)) {
            varreduraIndice = 0;
            varreduraRaio++;
        }
    }

    /** Numero de chunks no anel `raio` (raio 0 = 1 chunk). */
    private static int tamanhoAnel(int raio) {
        return raio == 0 ? 1 : 8 * raio;
    }

    /** Chunks varridos ate a posicao atual do cursor (so pra log). */
    private static int chunksVarridos(int raio, int indice) {
        int total = indice;
        for (int r = 0; r < raio; r++) total += tamanhoAnel(r);
        return total;
    }

    private static void anunciarDescoberta(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            // v1.2.14: pro lang (era pt cravado)
            player.sendSystemMessage(Component.translatable("market.intoxicantes.descoberta"));
        }
    }

    // ==================================================== GAGERO (gerente do Gago)

    /** Package-private: o game test do ciclo de vida chama direto (determinístico). */
    static void gerenciarGago(ServerLevel level, BlockPos pos) {
        // v1.2.76 — SEM VIRADA DE PLANTÃO: o dono da esquina serve 24h ATRÁS
        // DO BALCÃO. Antes ele descia pro guichê da porta às 00:00 (a grade
        // fechava e ele atendia por trás dela); com o balcão virando o único
        // posto de atendimento, o destino não muda mais com o relógio.
        // v1.2.46 — A VARREDURA DO FANTASMA: o raio 48 deixava Gagos de saves
        // antigos FORA da gestão (entalados: a "voz fantasma" que falava no
        // chat e o "imbatível" que não tinha corpo onde a voz saía — o playtest
        // não conseguia bater). Varredura LARGA (96): acha o fugitivo; a escolha
        // abaixo prefere quem está de fato no posto.
        var gagos = level.getEntitiesOfClass(
                GagoEntity.class, new AABB(pos).inflate(96));

        // A GESTÃO é do raio de 48 (perseguição do Gago puto, posto, vizinhança);
        // a varredura larga de cima existe SÓ pra achar fantasma entalado.
        double raioGestao = 48L * 48L;
        GagoEntity gago = null;
        for (GagoEntity g : gagos) {
            if ((g.isPuto() || (gagoAtivoId != null && g.getUUID().equals(gagoAtivoId)))
                    && g.blockPosition().distSqr(pos) <= raioGestao) {
                gago = g;
                break;
            }
        }
        if (gago == null) {
            // o dono do posto real é o mais PERTO DENTRO do raio de gestão —
            // um Gago livre a 75 blocos (outro teste/outra vida) nunca é escolhido
            double melhor = Double.MAX_VALUE;
            for (GagoEntity g : gagos) {
                double d = g.blockPosition().distSqr(pos);
                if (d <= raioGestao && d < melhor) {
                    melhor = d;
                    gago = g;
                }
            }
            if (gago == null) {
                // v1.2.46 — ADOÇÃO DO FANTASMA: sem dono local, um Gago ENTALADO
                // (a voz fantasma/imbatível de save antigo) na varredura larga é
                // adotado — o bloco de resgate lá embaixo o teleporta pro posto
                // real (garantirPostoLivre + snap + setupPostoMercado).
                double melhorFantasma = Double.MAX_VALUE;
                for (GagoEntity g : gagos) {
                    if (g.isPuto() || g.getTradingPlayer() != null
                            || !g.estaPresoEmBloco()) continue;
                    double d = g.blockPosition().distSqr(pos);
                    if (d < melhorFantasma) {
                        melhorFantasma = d;
                        gago = g;
                    }
                }
            }
        }
        if (gago == null) {
            spawnarGago(level, getPosBalcao(pos));
            return;
        }
        // v1.2.44 — DOU DE BAIXA nos Gagos EXCEDENTES: ovo duplicado, corrida
        // de spawn, double-spawn de save — o gerenciador só mantém UM. Os
        // excedentes ficam entalados (invisíveis!) mas continuam soltando
        // frase no chat: a "fala fantasma" que o print mostrou.
        // v1.2.46 — O CRITÉRIO: só sai da cena quem está ENTALADO (o fantasma
        // de save antigo — invisível e "imbatível") ou FORA do raio de gestão
        // (não é de ninguém). O dono no posto e o freguês em atendimento ficam.
        for (GagoEntity excedente : gagos) {
            if (excedente != gago && !excedente.isPuto()
                    && excedente.getTradingPlayer() == null
                    && (excedente.blockPosition().distSqr(pos) <= raioGestao
                        || excedente.estaPresoEmBloco())) {
                excedente.discard();
            }
        }
        // v1.2.36 — O CADÁVER NO POSTO: se o candidato está morrendo OU com a
        // cabeça presa em bloco (sufocado no posto), NÃO o "conserta" no
        // lugar — antes o teleporte de plantão o realojava DENTRO do bloco a
        // cada passada: loop de morte + loja cheia de cachaça/cerveja/R$.
        if (!gago.isPuto() && (gago.isDeadOrDying() || gago.estaPresoEmBloco())) {
            gagoAtivoId = null;
            BlockPos posto = getPosBalcao(pos);
            garantirPostoLivre(level, posto);
            if (gago.isDeadOrDying()) return; // morrendo: nada a salvar
            // v1.2.36 — O RESGATE: presos de saves antigos (o barril que a
            // limpeza antiga pulava) ficavam ENTALADOS pra sempre — vivos,
            // falando no chat e INVISÍVEIS dentro do bloco. O posto já está
            // livre agora: devolve o resgatado a ele em vez de esperar outro
            // ciclo (que antes só limpava e voltava a ignorá-lo).
            gago.anunciarRetornoAoBalcao(level);
            gago.absSnapTo(posto.getX() + 0.5, posto.getY(), posto.getZ() + 0.5, 180F, 0F);
            gago.setupPostoMercado(posto);
            gagoAtivoId = gago.getUUID();
            return;
        }
        gagoAtivoId = gago.getUUID();

        // EM ATENDIMENTO: ninguem toca no vendedor — sem teleporte de plantao,
        // sem re-organizacao de posto. O tick dele congela a IA; quando a tela
        // fechar (stopTrading) ele volta pro posto sozinho.
        if (gago.isTrading()) {
            return;
        }

        BlockPos destino = getPosBalcao(pos);
        // Gago PUTO ninguem mexe: deixa ele perseguir em paz; quando a raiva passar,
        // o tick dele o devolve ao posto e o gerenciador reassume
        if (gago.isPuto()) return;

        // v1.2.76 — A ÂNCORA é o BALCÃO (o guichê da madrugada saiu): se ele
        // NÃO está no posto certo: garante o destino livre (a pauta do
        // 1.2.30 — nunca dentro de bloco), teleporta e trava o plantão lá.
        boolean noLugar = gago.blockPosition().distSqr(destino) < 4.0;
        if (noLugar && gago.isNoAi() && gago.estaDePlantao()) {
            return; // já ancorado no posto certo: caminho feliz, custo zero
        }
        if (noLugar) {
            // no lugar certo mas "solto" (save velho, empurrão): trava aí
            gago.consolidarAqui(level);
            return;
        }
        // TROCA DE PLANTÃO: destino limpo, snap e NoAI — o mesmo caminho
        // seguro da v1.2.30 (garantirPostoLivre derruba com drop SÓ bloco do
        // próprio mercado/natureza: nunca baú, porta ou cama de jogador)
        MarketSystem.garantirPostoLivre(level, destino);
        gago.absSnapTo(destino.getX() + 0.5, destino.getY(), destino.getZ() + 0.5, 180F, 0F);
        gago.setupPostoMercado(destino);
    }

    /**
     * v1.2.39 — O PARÇA NO PÁTIO: o Juça fica rondando a frente do mercado
     * (perto da porta, do lado de FORA — o balcão é do Gago). Nasce uma vez,
     * persiste, e se morrer nasce outro (a balada continua). Package-private:
     * o game test chama direto.
     *
     * v1.2.71 — O PONTO CERTO (bug do playtest: "Juça não spawna"): o
     * ponto de nascimento usava offset FIXO sul+leste do centro — o jigsaw
     * GIRA a estrutura, então esse ponto cedia DENTRO da fachada ou na LAJE
     * do prédio (o heightmap o plantava no telhado, fora da vista do
     * freguês). Agora o ponto é a FRENTE DA PORTA (o offset girado do
     * plantão da madrugada + 2 blocos pra rua) — calçada, do lado de fora,
     * onde o freguês chega. Juça entalado em bloco ou na laje de save antigo
     * é RESGATADO pro mesmo ponto.
     */
    static void gerenciarJuca(ServerLevel level, BlockPos pos) {
        BlockPos posteJuca = frenteDaPorta(pos);
        var jucas = level.getEntitiesOfClass(JucelinoEntity.class,
                new AABB(pos).inflate(48));
        if (!jucas.isEmpty()) {
            // RESGATE: nasceu no lugar velho (offset fixo) e ficou entalado na
            // parede ou plantado na laje? devolve à frente da porta.
            for (JucelinoEntity juca : jucas) {
                boolean entalado = level.getBlockState(juca.blockPosition())
                        .isSolidRender();
                boolean naLaje = juca.getY() > pos.getY() + 6;
                if (entalado || naLaje) {
                    BlockPos chao = level.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            posteJuca);
                    juca.absSnapTo(chao.getX() + 0.5, chao.getY(), chao.getZ() + 0.5, 0F, 0F);
                    juca.getNavigation().stop();
                }
            }
            return; // o parça já tá na área
        }
        // nasce na frente da porta (fora, do lado da calçada)
        BlockPos chao = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                posteJuca);
        JucelinoEntity juca = IntoxicantesMod.JUCA.create(level, EntitySpawnReason.EVENT);
        if (juca == null) return;
        juca.absSnapTo(chao.getX() + 0.5, chao.getY(), chao.getZ() + 0.5, 0F, 0F);
        juca.finalizeSpawn(level, level.getCurrentDifficultyAt(chao), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(juca);
    }

    /** A calçada EM FRENTE DA PORTA: o plantão da madrugada (girado pela
     *  rotação real da estrutura) + 2 blocos pra rua. */
    private static BlockPos frenteDaPorta(BlockPos pos) {
        BlockPos porta = getPosPorta(pos);
        return new BlockPos(
                porta.getX() + portaOffset.getX() * 2,
                porta.getY(),
                porta.getZ() + portaOffset.getZ() * 2);
    }

    /**
     * v1.2.30 — O POSTO TEM QUE ESTAR LIVRE: o bug do "Gago sumindo" era o
     * teleporte de plantão NUNCA checar o destino — bloco novo no posto
     * (reforma de pele 1.2.26, plantio velho, offset de estrutura girada)
     * sufocava o Gago, ele morria dropando cachaça/cerveja/R$/cartucho e o
     * gerenciador nasce outro no MESMO lugar: loop de morte + loja cheia de
     * item. Antes de teleportar: espaço livre garantido (limpa SÓ bloco do
     * próprio mercado/paisagem natural — nunca baú, porta ou bloco raro).
     */
    static void garantirPostoLivre(ServerLevel level, BlockPos pos) {
        // corpo inteiro (2 de altura) + 1 acima de folga
        for (BlockPos p : new BlockPos[]{pos, pos.above(), pos.above(2)}) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            // v1.2.36 — A PAUTA DA REFORMA: baú/porta/cama continuam INTACTOS
            // (conteúdo de jogador nunca some), mas TODO o resto cai COMO ITEM
            // — inclusive barril e contêiner do PRÓPRIO template. O barril
            // decorativo era o caminho do bug: posto sobre ele = Gago
            // sufocado = morre dropando estoque; e como barril é EntityBlock,
            // a limpeza antiga o pulava e o loop nunca acabava.
            if (s.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    || s.is(net.minecraft.tags.BlockTags.DOORS)
                    || s.is(net.minecraft.tags.BlockTags.BEDS)) {
                continue;
            }
            level.destroyBlock(p, true);
        }
    }

    private static void spawnarGago(ServerLevel level, BlockPos pos) {
        // v1.2.30: o recém-nascido NUNCA nasce dentro de bloco (mesmo bug do
        // sufocamento — nascia, morria, dropava, e nascia outro em cima)
        garantirPostoLivre(level, pos);
        GagoEntity gago = IntoxicantesMod.GAGO.create(level, EntitySpawnReason.EVENT);
        if (gago == null) return;
        gago.absSnapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180F, 0F);
        gago.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        gago.setPersistenceRequired();
        gago.setupPostoMercado(pos);
        level.addFreshEntity(gago);
        // se apresente pro fregues mais proximo (a frase de chegada finalmente roda)
        Player maisPerto = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0, false);
        if (maisPerto != null) {
            gago.anunciarChegada(level, maisPerto);
        }
    }

    /** Atras do balcao (posto do plantao diurno), JA girado pela rotacao real. */
    public static BlockPos getPosBalcao(BlockPos pos) {
        return pos.above().offset(balcaoOffset.getX(), balcaoOffset.getY(), balcaoOffset.getZ());
    }

    /** Alpendre na frente da porta (plantao da madrugada), JA girado. */
    public static BlockPos getPosPorta(BlockPos pos) {
        return pos.above().offset(portaOffset.getX(), portaOffset.getY(), portaOffset.getZ());
    }

    /** Offset da porta ja girado (usado pelo /gagomarket rebuild pra derivar a rotacao). */
    public static net.minecraft.core.Vec3i getPortaOffset() {
        return portaOffset;
    }

    /**
     * A estrutura gira aleatoriamente na geracao (jigsaw). Le a rotacao do piece e
     * gira os offsets locais da porta e do balcao junto — senao o Gago fica de
     * frente pro muro ou atras do balcao do vizinho kkkk.
     */
    private static void descobrirOffsets(StructureStart start) {
        net.minecraft.core.Vec3i porta = PORTA_LOCAL;
        net.minecraft.core.Vec3i balcao = BALCAO_LOCAL;
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
            if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece p) {
                var rot = p.getRotation();
                porta = girar(porta, rot);
                balcao = girar(balcao, rot);
                break;
            }
        }
        portaOffset = porta;
        balcaoOffset = balcao;
    }

    private static net.minecraft.core.Vec3i girar(net.minecraft.core.Vec3i local,
            net.minecraft.world.level.block.Rotation rot) {
        return switch (rot) {
            case NONE -> local;
            case CLOCKWISE_90 -> new net.minecraft.core.Vec3i(-local.getZ(), local.getY(), local.getX());
            case CLOCKWISE_180 -> new net.minecraft.core.Vec3i(-local.getX(), local.getY(), -local.getZ());
            case COUNTERCLOCKWISE_90 -> new net.minecraft.core.Vec3i(local.getZ(), local.getY(), -local.getX());
        };
    }

    // ==================================================== EXPULSAO DO MERCADO

    /**
     * v1.2.20 — UMA CASA PRO EXPULSO: varre anéis de raio 5..9 em volta do
     * mercado e devolve o melhor ponto com PÉ NO CHÃO (piso sólido), CORPO EM AR
     * (2 blocos), sem água/lava e longe do Gago — o mais perto do mercado nos
     * empates. O teleport com offset fixo do mundo (+5,+1,+5) ignorava a
     * rotação da estrutura e o terreno: o freguês nascia DENTRO da fundação
     * nova (v1.2.18) ou do prédio e ficava trancado nas pedras.
     * Retorna null se não achar nada viável (fica onde está: melhor duvidoso
     * do que soterrado).
     */
    @Nullable
    public static BlockPos procurarCasaProExpulso(ServerLevel level, BlockPos mercado,
            Player expulso, LivingEntity gago) {
        BlockPos melhor = null;
        double melhorNota = -1;
        for (int raio = 5; raio <= 9; raio++) {
            for (int dx = -raio; dx <= raio; dx++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != raio) continue; // só a borda
                    BlockPos pe = casaViavel(level, mercado.offset(dx, 0, dz), expulso);
                    if (pe == null) continue;
                    double doGago = gago == null ? 24.0
                            : Math.min(24.0, Math.sqrt(pe.distSqr(gago.blockPosition())));
                    double doMercado = Math.sqrt(pe.distSqr(mercado));
                    // longe do Gago primeiro (teto 24: não jogar pro horizonte),
                    // depois o mais perto do mercado (não virar pára-quedista)
                    double nota = doGago * 10 - doMercado;
                    if (nota > melhorNota) {
                        melhorNota = nota;
                        melhor = pe;
                    }
                }
            }
        }
        return melhor;
    }

    /** Resolve o pé no chão do candidato: procura o ponto habitável mais perto (±4 vertical). */
    @Nullable
    private static BlockPos casaViavel(ServerLevel level, BlockPos candidato, Player expulso) {
        BlockPos melhor = null;
        double melhorAltura = Double.MAX_VALUE;
        for (int dy = -4; dy <= 4; dy++) {
            BlockPos pe = candidato.offset(0, dy, 0);
            if (chaoHabitavel(level, pe, expulso)) {
                double altura = Math.abs(dy);
                if (altura < melhorAltura) {
                    melhorAltura = altura;
                    melhor = pe;
                }
            }
        }
        return melhor;
    }

    /** Piso sólido embaixo, corpo e cabeça em AR, sem água/lava — e não em cima do próprio freguês. */
    private static boolean chaoHabitavel(ServerLevel level, BlockPos pe, Player expulso) {
        BlockPos piso = pe.below();
        BlockState estadoPiso = level.getBlockState(piso);
        if (estadoPiso.isAir() || !estadoPiso.isSolidRender()) return false;
        if (!level.getBlockState(pe).isAir() || !level.getBlockState(pe.above()).isAir()) return false;
        if (level.getBlockState(pe).is(Blocks.WATER) || level.getBlockState(pe).is(Blocks.LAVA)) return false;
        return !expulso.getBoundingBox().intersects(new AABB(pe));
    }

    // ==================================================== TEMPO

    /** true = 00:00 ~ 07:00 (Gago fica na porta) */
    public static boolean estaNaPorta(ServerLevel level) {
        return horaDoDia(level) < 7;
    }

    /** Ticks totais do clock do overworld (equivalente ao getDayTime das versoes antigas). */
    public static long tempoTotal(ServerLevel level) {
        Holder<WorldClock> clock = level.registryAccess()
                .lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        return level.clockManager().getInstance(clock).totalTicks();
    }

    public static int horaDoDia(ServerLevel level) {
        return (int) ((tempoTotal(level) / 1000L + 6) % 24);
    }

    public static String formatarHora(ServerLevel level) {
        long ticks = tempoTotal(level) % 24000L;
        int totalMinutos = (int) ((ticks * 24 * 60) / 24000);
        int hora = (totalMinutos / 60 + 6) % 24;
        int minuto = totalMinutos % 60;
        return String.format("%02d:%02d", hora, minuto);
    }

    // ==================================================== ESTADO / PERSISTENCIA

    @Nullable
    public static BlockPos getMarketPos() {
        return marketPos;
    }

    /** A região do prédio (""/classico/sertao/serra) — UI e log consultam. */
    public static String getRegiaoMercado() { return regiaoMercado; }

    public static void setRegiaoMercado(String regiao) {
        regiaoMercado = (regiao == null || regiao.isBlank()) ? "" : regiao;
    }

    /**
     * Chão do LOTE do mercado (asfalto do mod ou a faixa de vaga/meio-fio
     * de concreto do template) — o zelador só recolhe entulho plantado
     * sobre o próprio lote, nunca sobre construção de jogador.
     */
    private static boolean pisoDoLote(ServerLevel level, BlockPos chao) {
        var b = level.getBlockState(chao).getBlock();
        return b == IntoxicantesMod.ASFALTO
                || b == net.minecraft.world.level.block.Blocks.CONCRETE
                        .pick(net.minecraft.world.item.DyeColor.GRAY)
                || b == net.minecraft.world.level.block.Blocks.CONCRETE
                        .pick(net.minecraft.world.item.DyeColor.BLUE);
    }

    /**
     * v1.2.68 — REFORMA DO PÁTIO, AGORA MÃO CONTRÁRIA: mundos 1.2.61–67
     * nasceram com o pátio "sem sentido nenhum" (relato do playtest) —
     * HIDRANTE no meio do asfalto (x10,z20), QUATRO postes DENTRO das vagas
     * demarcadas (x3/x23, z20/z23) e a TRAVESSIA cruzando o estacionamento
     * inteiro (z19..z25). O template novo já nasce certo (hidrante na
     * calçada x11,z19, dois postes nos cantos do lote x0/x26, travessia só
     * na calçada) — aqui o zelador RECOLHE o legado sobre o asfalto do
     * mercado. Nunca mais PLANTA nada no pátio (a reforma 1.2.25–29 pintava
     * faixa e plantava hidrante SOBRE o asfalto: desfeita). Seguro por
     * construção: só age em bloco do próprio mod cujo chão é piso do lote
     * (pisoDoLote) — nunca em construção de jogador.
     */
    private static void reformarPatio(ServerLevel level, BlockPos centro) {
        net.minecraft.core.Vec3i porta = portaOffset;
        net.minecraft.core.Vec3i balcao = balcaoOffset;
        // offsets NO TEMPLATE → mundo: a mesma conta do descobrirOffsets
        java.util.function.BiFunction<Integer, Integer, BlockPos> noMundo = (lx, lz) -> {
            net.minecraft.core.Vec3i g = girar(new net.minecraft.core.Vec3i(lx - 7, 0, lz - 5), rotacaoDaPorta(porta));
            return new BlockPos(centro.getX() + g.getX(), centro.getY() + 1, centro.getZ() + g.getZ());
        };
        // HIDRANTE em VAGA é o oposto da vida real (perto de hidrante é onde
        // se PROÍBE estacionar). Recolhe TODOS os perdidos sobre o asfalto:
        // o em-vaga do v2 (10,20), os das vagas 1.2.24~28 (2,14)/(12,14), o
        // "da calçada" da reforma antiga (4,12 — na prática era asfalto) e
        // os never-were (8,22)/(18,22).
        for (int[] velho : new int[][]{{10, 20}, {8, 22}, {18, 22},
                {2, 14}, {12, 14}, {4, 12}}) {
            BlockPos p = noMundo.apply(velho[0], velho[1]);
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).getBlock() == IntoxicantesMod.HIDRANTE
                    && pisoDoLote(level, p.below())) {
                level.destroyBlock(p, false);
            }
        }
        // TRAVESSIA no MEIO do estacionamento não existe na rua real: a
        // tinta do pátio (z20..z25 do v2) sai; a da CALÇADA (z19) fica.
        for (int lz = 20; lz <= 25; lz++) {
            for (int lx = 11; lx <= 15; lx++) {
                BlockPos p = noMundo.apply(lx, lz);
                if (!level.isLoaded(p)) continue;
                if (level.getBlockState(p).getBlock() == IntoxicantesMod.FAIXA_PEDESTRE
                        && pisoDoLote(level, p.below())) {
                    level.destroyBlock(p, false);
                }
            }
        }
        // POSTE DENTRO DE VAGA demarcada é vaga morta: recolhe os 4 do v2
        // (x3/x23, z20/z23) pela BASE — quebrar a base derruba o poste
        // inteiro (affectNeighborsAfterRemoval), sem drop dobrado.
        for (int[] vaga : new int[][]{{3, 20}, {23, 20}, {3, 23}, {23, 23}}) {
            BlockPos base = noMundo.apply(vaga[0], vaga[1]);
            if (!level.isLoaded(base)) continue;
            if (level.getBlockState(base).getBlock() == IntoxicantesMod.POSTE_LUZ
                    && level.getBlockState(base).getValue(PosteLuzBlock.PARTE)
                            == PosteLuzBlock.Parte.BASE
                    && pisoDoLote(level, base.below())) {
                level.destroyBlock(base, false);
            }
        }

        // ====================================================== v1.2.40 — REFORMA DO DISPLAY
        // Mundos 1.2.36–39 nasceram com dois defeitos que o zelador ainda
        // não concertava:
        //  (1) CROSSWALK DUPLA — tinta da travessia no chão E em cima (o
        //      Skyu diagnosticou: "em baixo tem que ser concreto"). A de
        //      BAIXO vira concreto branco;
        //  (2) PAINEL NA PORTA — a fileira do painel de LED (x4..8, y2)
        //      atravessava a PORTA (x7) e TODOS os 5 blocos tinham NBT
        //      próprio (5 textos sobrepostos = o display bugado do print).
        //      Remonta a linha à ESQUERDA da porta (x2..4), NBT só no
        //      cabeça, preservando texto/cor/brilho/modo.
        // Idempotente e seguro: só mexe em bloco do próprio template
        // (faixa sobre faixa; painel demolido só onde é painel; remonte
        // só onde é ar — nunca em construção do jogador).
        for (int lx = 5; lx <= 9; lx++) {
            BlockPos p = noMundo.apply(lx, 11);
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).getBlock() == IntoxicantesMod.FAIXA_PEDESTRE
                    && level.getBlockState(p.below()).getBlock()
                            == IntoxicantesMod.FAIXA_PEDESTRE) {
                level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks
                        .CONCRETE.white().defaultBlockState());
            }
        }
        java.util.function.BiFunction<Integer, Integer, BlockPos> noMundo2 = (lx, lz) -> {
            net.minecraft.core.Vec3i g = girar(new net.minecraft.core.Vec3i(lx - 7, 0, lz - 5),
                    rotacaoDaPorta(porta));
            return new BlockPos(centro.getX() + g.getX(), centro.getY() + 2,
                    centro.getZ() + g.getZ());
        };
        if (level.isLoaded(noMundo2.apply(6, 10))
                && level.getBlockState(noMundo2.apply(6, 10)).getBlock()
                        == IntoxicantesMod.PAINEL_LED) {
            // herda o texto do cabeça VELHO (x4 — ponta oeste da linha antiga)
            java.util.List<String> texto = java.util.List.of();
            int cor = 0x39FF6E, brilho = 15, modo = 0;
            BlockPos cabecaVelha = noMundo2.apply(4, 10);
            if (level.isLoaded(cabecaVelha) && level.getBlockEntity(cabecaVelha)
                    instanceof PainelLedBlockEntity be) {
                texto = java.util.List.copyOf(be.getLinhas());
                cor = be.getCor();
                brilho = be.getBrilho();
                modo = be.getModo();
            }
            if (texto.isEmpty()) texto = java.util.List.of("OFERTAS DO DIA", "PROMOCOES!");
            // facing dos novos painéis: SUL do template girado pela porta —
            // a reforma não passa pelo StructureTemplate, gira na mão
            net.minecraft.core.Direction facingMundo =
                    rotacaoDaPorta(porta).rotate(net.minecraft.core.Direction.SOUTH);
            // demolição: a fileira velha INTEIRA (só onde é painel do template)
            for (int lx = 3; lx <= 9; lx++) {
                BlockPos p = noMundo2.apply(lx, 10);
                if (level.isLoaded(p)
                        && level.getBlockState(p).getBlock() == IntoxicantesMod.PAINEL_LED) {
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks
                            .AIR.defaultBlockState());
                }
            }
            // remonte à esquerda da porta (x2..4); o texto entra só no x2
            for (int lx = 2; lx <= 4; lx++) {
                BlockPos p = noMundo2.apply(lx, 10);
                if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
                level.setBlockAndUpdate(p, IntoxicantesMod.PAINEL_LED.defaultBlockState()
                        .setValue(PainelLedBlock.FACING, facingMundo)
                        .setValue(PainelLedBlock.TELAS, 1)
                        .setValue(PainelLedBlock.LIT, Boolean.TRUE));
            }
            if (level.getBlockEntity(noMundo2.apply(2, 10)) instanceof PainelLedBlockEntity novo) {
                novo.aplicar(texto, cor, brilho, modo);
            }
            PainelLedBlock.reavaliarLinha(level, noMundo2.apply(2, 10));
        }
    }

    /** Rotação inferida dos offsets JA girados (mesma tabela do MoneyCommands). */
    private static net.minecraft.world.level.block.Rotation rotacaoDaPorta(net.minecraft.core.Vec3i porta) {
        int px = porta.getX();
        int pz = porta.getZ();
        return (px == 0 && pz > 0) ? net.minecraft.world.level.block.Rotation.NONE
                : (px < 0 && pz == 0) ? net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                : (px == 0 && pz < 0) ? net.minecraft.world.level.block.Rotation.CLOCKWISE_180
                : net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
    }

    /**
     * v1.2.51 — A REFORMA DA ENTRADA (mundos 1.2.50 e anteriores): troca a
     * porta de spruce vanilla do template pela PORTA-GRADE do mod (o guichê
     * de madrugada) e planta o MINI DISPLAY "ABERTO · 24H" ao lado direito
     * da porta — os mesmos offsets do template novo, girados pela rotação
     * real. Idempotente e seguro: só age em bloco DO PRÓPRIO template
     * (porta de spruce no vão, ar nas posições novas; nunca em construção
     * de jogador).
     */
    private static void reformarEntrada(ServerLevel level, BlockPos centro) {
        java.util.function.BiFunction<Integer, Integer, BlockPos> noMundo = (lx, lz) -> {
            net.minecraft.core.Vec3i g = girar(new net.minecraft.core.Vec3i(lx - 7, 0, lz - 5),
                    rotacaoDaPorta(portaOffset));
            return new BlockPos(centro.getX() + g.getX(), centro.getY(), centro.getZ() + g.getZ());
        };
        // 1) a PORTA-GRADE no vão da porta (template x7, z9) — a mesma conta
        // do posPortaReal (fonte única com o zelador): lower + upper
        BlockPos raiz = posPortaReal(centro);
        if (level.isLoaded(raiz)
                && level.getBlockState(raiz).getBlock() == net.minecraft.world.level.block.Blocks.SPRUCE_DOOR
                && level.getBlockState(raiz.above()).getBlock() == net.minecraft.world.level.block.Blocks.SPRUCE_DOOR) {
            net.minecraft.world.level.block.Rotation rot = rotacaoDaPorta(portaOffset);
            net.minecraft.core.Direction facingMundo = rot.rotate(net.minecraft.core.Direction.SOUTH);
            BlockState portaNova = IntoxicantesMod.PORTA_GRADE.defaultBlockState()
                    .setValue(PortaGradeBlock.FACING, facingMundo)
                    // v1.2.76: mercado 24h — a grade nasce ABERTA (fechar a
                    // porta-grade é na mão do dono, no clique direito)
                    .setValue(PortaGradeBlock.FECHADA, false);
            level.setBlockAndUpdate(raiz, portaNova.setValue(PortaGradeBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            level.setBlockAndUpdate(raiz.above(), portaNova.setValue(PortaGradeBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        // 2) o MINI DISPLAY "ABERTO · 24H" (template x12, y2, z10) — só onde
        // é ar (a fachada da 1.2.31+ é parede/placa; jogador nunca perde bloco)
        BlockPos display = noMundo.apply(12, 10).above();
        if (level.isLoaded(display) && level.getBlockState(display).isAir()) {
            net.minecraft.world.level.block.Rotation rot = rotacaoDaPorta(portaOffset);
            net.minecraft.core.Direction facingMundo = rot.rotate(net.minecraft.core.Direction.SOUTH);
            level.setBlockAndUpdate(display, IntoxicantesMod.PAINEL_LED.defaultBlockState()
                    .setValue(PainelLedBlock.FACING, facingMundo)
                    .setValue(PainelLedBlock.TELAS, 1)
                    .setValue(PainelLedBlock.LIT, Boolean.TRUE));
            if (level.getBlockEntity(display) instanceof PainelLedBlockEntity mini) {
                mini.aplicar(java.util.List.of("ABERTO", "· 24H ·"),
                        PlacaEsquinaoBlockEntity.COR_LED, 15, PainelLedBlockEntity.MODO_FIXO);
            }
        }
    }

    /** Seta a posição do mercado (null limpa — usado pelos game tests pra manter o run hermético). */
    public static void setMarketPos(@Nullable BlockPos pos) {
        marketPos = pos;
        portaOffset = PORTA_LOCAL;   // default; a descoberta sobrescreve com a rotacao real
        balcaoOffset = BALCAO_LOCAL;
        discovered = true;
    }

    /** Chamado no SERVER_STARTING: carrega o NBT salvo no diretorio do mundo. */
    public static void load(File worldDir) {
        File file = new File(worldDir, DATA_FILE);
        if (!file.exists()) return;
        try (InputStream in = new FileInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
                marketPos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
                portaOffset = new BlockPos(
                        tag.getIntOr("portax", PORTA_LOCAL.getX()),
                        tag.getIntOr("portay", PORTA_LOCAL.getY()),
                        tag.getIntOr("portaz", PORTA_LOCAL.getZ()));
                balcaoOffset = new BlockPos(
                        tag.getIntOr("balcaox", BALCAO_LOCAL.getX()),
                        tag.getIntOr("balcaoy", BALCAO_LOCAL.getY()),
                        tag.getIntOr("balcaoz", BALCAO_LOCAL.getZ()));
                regiaoMercado = tag.getStringOr("regiao", "");
                discovered = true;
                IntoxicantesMod.LOGGER.info("[Market] Posicao do Mercado carregada: {}",
                        marketPos.toShortString());
            }
        } catch (IOException e) {
            IntoxicantesMod.LOGGER.warn("[Market] Erro ao carregar posicao do mercado: {}", e.getMessage());
        }
    }

    /** Chamado no SERVER_STOPPING: salva o NBT. */
    public static void save(File worldDir) {
        if (marketPos == null) return;
        File file = new File(worldDir, DATA_FILE);
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", marketPos.getX());
        tag.putInt("y", marketPos.getY());
        tag.putInt("z", marketPos.getZ());
        tag.putInt("portax", portaOffset.getX());
        tag.putInt("portay", portaOffset.getY());
        tag.putInt("portaz", portaOffset.getZ());
        tag.putInt("balcaox", balcaoOffset.getX());
        tag.putInt("balcaoy", balcaoOffset.getY());
        tag.putInt("balcaoz", balcaoOffset.getZ());
        tag.putString("regiao", regiaoMercado);
        try (FileOutputStream out = new FileOutputStream(file)) {
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            IntoxicantesMod.LOGGER.warn("[Market] Erro ao salvar posicao do mercado: {}", e.getMessage());
        }
    }

    // ==================================================== HELPER PRA OUTROS SISTEMAS

    /** O player ta dentro da area do mercado? */
    public static boolean estaNoMercado(Player player) {
        if (marketPos == null) return false;
        if (!player.level().dimension().equals(Level.OVERWORLD)) return false;
        return player.blockPosition().distSqr(marketPos) < 16 * 16;
    }

    /** Usado pelo /gagomarket (comando). Salva na hora pro server nao perder se der crash. */
    public static void setMarketPosComando(MinecraftServer server, BlockPos pos) {
        setMarketPos(pos);
        File worldDir = server.getWorldPath(LevelResource.ROOT).toFile();
        save(worldDir);
    }
}
