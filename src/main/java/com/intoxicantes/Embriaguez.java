package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Embriaguez do fregues (v1.2.9, refinada na v1.2.10): cada dose de bebida/droga
 * do mod sobe o nivel; o nivel cai 1 a cada `embriaguezDecaySegundos` de vida limpa.
 *
 *   0 limpo | 1..limiarFonar-1 alegre | limiarFonar..limiarHic-1 bêbado (fala fonar)
 *           | limiarHic+ caído (fala pior + *hic!* intercalado + HIC audível)
 *
 * - A fala sai embaralhada via ServerMessageDecoratorEvent (CONTENT_PHASE): o
 *   servidor reescreve a mensagem ANTES de distribuir — todo mundo le o fonar,
 *   e o autor também (pior: ele nem sabe o que escreveu).
 * - v1.2.10: limiares, decay e teto vem do ModConfig (config/intoxicantes.json);
 *   no teto com tranca ligada, o corpo DEVOLVE o gole (refluxo comico).
 * - v1.2.10: embriaguez ATRAVESSA relog/restart (intoxicantes_embriaguez.json,
 *   mesmo padrao do PlayerMoney/FidelidadeData). O logout so esquece o
 *   consumivel em uso — o fregues continua bêbado quando volta.
 * - v1.2.10: bêbado de verdade CAMBIA de verdade — náusea constante e fraqueza
 *   gradual aparecem no HUD vanilla, e a action bar mostra o estado.
 * - v1.2.13 (o refinamento supimpa):
 *   * RESSACA de verdade — voltar a zero depois de ter cruzado o limiar da fala
 *     derruba o fregues por `embriaguezRessacaSegundos` (nausea + lentidao +
 *     fraqueza); qualquer dose durante a ressaca e' o classico "cabelo do
 *     cachorro" e remove ela na hora. Persiste no arquivo do mundo.
 *   * CAMBALEIO mecânico — no nivel do soluco, empurrao aleatorio (config).
 *   * Markup do Gago — fregues visivelmente bêbado paga ate 30% a mais
 *     (FidelidadeData.precoComDesconto, o ponto unico de preco).
 *   * Action bar GRÁFICA (▮▮▮▯) e rotulos traduzidos (en_us de verdade).
 */
public final class Embriaguez {
    private Embriaguez() {}

    /**
     * Doses de ÁLCOOL por item (v1.2.14): SÓ bebida emborracha. Erva, pó e
     * comprimido têm efeitos próprios dos itens — misturar maconha no medidor
     * de cachaça não faz sentido nem no roleplay kkkk. Cafeína é a CURA.
     */
    private static final Map<Item, Integer> DOSE = new HashMap<>();

    static {
        DOSE.put(IntoxicantesMod.CERVEJA, 2);
        DOSE.put(IntoxicantesMod.VINHO, 2);
        DOSE.put(IntoxicantesMod.HIDROMEL, 2);
        DOSE.put(IntoxicantesMod.CACHACA, 3);
        DOSE.put(IntoxicantesMod.RUM, 3);
    }

    private static final Map<UUID, Integer> NIVEL = new HashMap<>();
    /** Segundos limpos acumulados (0..decay-1). */
    private static final Map<UUID, Integer> SOBRIEDADE = new HashMap<>();
    /** Para o HIC: ticks até a próxima soluçada. */
    private static final Map<UUID, Integer> PROXIMO_HIC = new HashMap<>();
    /** Consumível em uso na passada de tick anterior (pra detectar o fim do gole). */
    private static final Map<UUID, ItemStack> EM_USO = new HashMap<>();
    /**
     * v1.2.12: ticks restantes de uso na passada ANTERIOR — a dose só conta se o
     * gole COMPLETOU. Soltar o botão, trocar de item ou ter o baseado apagado
     * pelo Gago também "zera" o uso, mas o remaining nunca chegou ao fim.
     */
    private static final Map<UUID, Integer> USO_REMAINING = new HashMap<>();
    /** Itens que SOBRIAM (cura da bebedeira): item → níveis removidos. */
    private static final Map<Item, Integer> CURA = new HashMap<>();

    static {
        CURA.put(IntoxicantesMod.EXTRATO_CAFEINA, 2);
    }
    /** Nivel da action bar na última exibição: só re-envia quando muda. */
    private static final Map<UUID, Integer> BARRA_ENVIADA = new HashMap<>();
    /**
     * v1.2.13: SEGUNDOS restantes de RESSACA (duração configurável; 0 = limpo).
     * v1.2.46: era TICKS com decremento por segundo — ressaca de 90s durava 30
     * minutos kkkk. Entrada quando o nivel chega a ZERO vindo de um estado
     * bêbado (>= limiar da fala). O "cabelo do cachorro" (qualquer dose durante
     * a ressaca) remove; a CAFEINA agora também (cura da ressaca).
     */
    private static final Map<UUID, Integer> RESSACA = new HashMap<>();
    /**
     * v1.2.13: passou pelo estado bêbado (>= limiar da fala) desde o último
     * zero? O decay derruba 1 NIVEL por passada — quando chega a zero, o
     * "nível anterior" já é 1 (alegre), então a travessia pra ressaca precisa
     * de MEMÓRIA do estado, não do nível imediatamente anterior.
     * Transitório (logout limpa): a ressaca é decidida em sessão viva.
     */
    private static final java.util.Set<UUID> ESTEVE_BEBADO = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ==================================================== PERSISTENCIA (padrao FidelidadeData)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File saveFile;

    /** Estado salvo: nivel + relógio de sobriedade + ressaca de cada fregues. */
    private static class Salvo {
        Map<UUID, Integer> nivel = new HashMap<>();
        Map<UUID, Integer> sobriedade = new HashMap<>();
        Map<UUID, Integer> ressaca = new HashMap<>(); // v1.2.13: relog não cura ressaca
    }

    /** Inicializa a persistência com o diretorio do mundo (chamado no boot). */
    public static void init(File worldDir) {
        saveFile = new File(worldDir, "intoxicantes_embriaguez.json");
        load();
    }

    private static void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (Reader reader = Files.newBufferedReader(saveFile.toPath())) {
            Type tipo = TypeToken.get(Salvo.class).getType();
            Salvo lido = GSON.fromJson(reader, tipo);
            if (lido != null) {
                NIVEL.clear();
                NIVEL.putAll(lido.nivel);
                SOBRIEDADE.clear();
                SOBRIEDADE.putAll(lido.sobriedade);
                RESSACA.clear();
                if (lido.ressaca != null) {
                    // v1.2.46 — MIGRAÇÃO: saves 1.2.13~1.2.45 gravavam TICKS.
                    // Valor acima do teto do config (600s) = legado em ticks.
                    for (Map.Entry<UUID, Integer> entrada : lido.ressaca.entrySet()) {
                        RESSACA.put(entrada.getKey(), entrada.getValue() > 600
                                ? entrada.getValue() / 20 : entrada.getValue());
                    }
                }
            }
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao carregar embriaguez: {}", e.getMessage());
        }
    }

    /** Package-private: o game test aponta a persistencia pra um arquivo proprio. */
    static void initTeste(File arquivo) {
        saveFile = arquivo;
        NIVEL.clear();
        SOBRIEDADE.clear();
        RESSACA.clear();
    }

    /** Package-private: o game test faz round-trip sem reiniciar o servidor. */
    static void loadTeste() {
        load();
    }

    /** Package-private: o game test faz round-trip sem reiniciar o servidor. */
    static void save() {
        if (saveFile == null) return;
        Salvo salvo = new Salvo();
        salvo.nivel = new HashMap<>(NIVEL);
        salvo.sobriedade = new HashMap<>(SOBRIEDADE);
        salvo.ressaca = new HashMap<>(RESSACA);
        try (Writer writer = Files.newBufferedWriter(saveFile.toPath())) {
            GSON.toJson(salvo, writer);
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao salvar embriaguez: {}", e.getMessage());
        }
    }

    // ==================================================== REGISTRO

    public static void register() {
        // dose: fim de consumo de QUALQUER consumível (bebida/erva do mod)
        ServerTickEvents.END_SERVER_TICK.register(Embriaguez::tickServidor);

        // fala embaralhada: decorator de conteúdo do chat (server-authoritative)
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE,
                (sender, message) -> {
                    if (sender == null) return message;
                    int nivel = nivel(sender);
                    if (nivel < ModConfig.get().embriaguezLimiarFonar) return message;
                    String puro = message.getString();
                    String fonado = fonar(puro, nivel);
                    if (fonado.equals(puro)) return message;
                    return Component.literal(fonado);
                });

        // v1.2.10: logout NÃO derruba a embriaguez (persiste no arquivo do mundo);
        // só esquece o consumível em uso e a action bar — o resto volta no login.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            EM_USO.remove(id);
            USO_REMAINING.remove(id);
            BARRA_ENVIADA.remove(id);
            ESTEVE_BEBADO.remove(id); // transitória: a ressaca é decidida em sessão viva
        });

        // desliga o servidor: garante o estado em disco
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
    }

    // ==================================================== CONSUMO

    /**
     * Fim de uso de consumível: se o item é dose do mod, conta. Roda no tick
     * do servidor (1x/s) comparando o useAction atual com o do tick passado —
     * quem terminou de beber fica 0 tick sem uso.
     */
    private static void tickServidor(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickJogador(player);
        }
    }

    static void tickJogador(ServerPlayer player) { // package-private: game test acelera o decay
        UUID id = player.getUUID();

        // --- dose/cura: só quando o gole TERMINA de verdade (v1.2.12)
        ItemStack antes = EM_USO.get(id);
        ItemStack agora = player.getUseItem();
        Integer remaining = USO_REMAINING.get(id);
        boolean estavaUsando = antes != null && !antes.isEmpty();
        boolean estaUsando = !agora.isEmpty();
        if (estavaUsando && !estaUsando) {
            // o uso acabou: COMPLETOU (remaining chegou ao fim) ou INTERROMPEU?
            if (remaining != null && remaining <= 1) {
                int dose = DOSE.getOrDefault(antes.getItem(), 0);
                if (dose > 0) {
                    beber(player, dose);
                }
                int cura = CURA.getOrDefault(antes.getItem(), 0);
                if (cura > 0) {
                    curar(player, cura);
                }
            }
            // interrompido no meio: NADA conta (o bug do "clique sem beber")
        }
        EM_USO.put(id, agora);
        if (estaUsando) {
            USO_REMAINING.put(id, player.getUseItemRemainingTicks());
        } else {
            USO_REMAINING.remove(id);
        }

        // decay, efeitos e HIC correm 1x POR SEGUNDO (20 ticks)
        if (player.tickCount % 20 != 0) {
            return;
        }

        ModConfig cfg = ModConfig.get();
        int nivel = NIVEL.getOrDefault(id, 0);

        // --- v1.2.13: RESSACA corre MESMO com nivel zero — a bebedeira acabou,
        // mas o dia seguinte continua. Por isso ela vem ANTES do gate de baixo.
        int ressaca = RESSACA.getOrDefault(id, 0);
        if (ressaca > 0) {
            ressaca--;
            if (ressaca == 0) {
                RESSACA.remove(id);
                // v1.2.14: mensagens de estado vão pro CHAT (action bar de 2s
                // não dá pra ler — report do beta tester)
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.ressaca.fim"));
            } else {
                BARRA_ENVIADA.remove(id); // proxima passada reenvia a barra "limpo"
                RESSACA.put(id, ressaca);
                // o combo da ressaca: nausea + lentidao + fraqueza (sem particulas)
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, true, false));
            }
            // (a barra usa o sentinela -2 no gate de nivel zero: sem reenvio por segundo)
        }

        if (nivel <= 0) {
            SOBRIEDADE.remove(id);
            PROXIMO_HIC.remove(id);
            if (ressaca > 0) {
                // barra "0/7 — de ressaca" (sentinela -2: so reenvia quando muda)
                if (BARRA_ENVIADA.getOrDefault(id, -1) != -2) {
                    player.sendSystemMessage(barraDeEstado(player, 0, cfg));
                    BARRA_ENVIADA.put(id, -2);
                }
                save(); // o timer da ressaca corre mesmo sem bebedeira
            }
            return;
        }

        // --- decay: 1 nivel a cada DECAY_SEGUNDOS (config) sem dose nova
        int limpos = SOBRIEDADE.getOrDefault(id, 0) + 1;
        if (limpos >= Math.max(1, cfg.embriaguezDecaySegundos)) {
            SOBRIEDADE.put(id, 0);
            NIVEL.put(id, nivel - 1);
            BARRA_ENVIADA.remove(id); // nivel mudou: barra atualiza
            nivel--;
            // v1.2.13: voltou a ZERO tendo passado pelo estado bêbado? RESSACA
            // (curada só por outra dose — o classico "cabelo do cachorro")
            if (nivel <= 0 && ESTEVE_BEBADO.remove(id)) {
                iniciarRessaca(player);
            }
        } else {
            SOBRIEDADE.put(id, limpos);
        }

        // --- v1.2.10: bêbado de verdade sente na CARNE (não só no chat)
        aplicarEfeitosGraduais(player, nivel, cfg);

        // --- v1.2.13: CAMBALEIO — no nivel do soluco, o corpo nao obedece
        if (cfg.embriaguezCambaleio && nivel >= cfg.embriaguezLimiarHic
                && player.getRandom().nextInt(4) == 0) {
            cambaleia(player);
        }

        // estado persistiu (timer da ressaca corre enquanto bebe): grava (arquivo pequeno)
        if (ressaca > 0) save();

        // --- barra de estado GRÁFICA (v1.2.13): ▮▮▮▯ com cor por faixa — no
        // CHAT desde a v1.2.14 (action bar de 2s não dá pra ler)
        if (BARRA_ENVIADA.getOrDefault(id, -1) != nivel) {
            player.sendSystemMessage(barraDeEstado(player, nivel, cfg));
            BARRA_ENVIADA.put(id, nivel);
        }

        // --- HIC: limiar do config soluça com som e fumaça
        if (nivel >= cfg.embriaguezLimiarHic) {
            int proximo = PROXIMO_HIC.getOrDefault(id, 160 + player.getRandom().nextInt(100));
            if (--proximo <= 0) {
                darHic(player, nivel);
                proximo = 160 + player.getRandom().nextInt(100);
            }
            PROXIMO_HIC.put(id, proximo);
        }
    }

    /** Conta a dose, toca o GLUP, aplica teto/refluxo e acorda o Gago zoeiro. */
    static void beber(ServerPlayer player, int dose) {
        UUID id = player.getUUID();
        ModConfig cfg = ModConfig.get();
        int cap = Math.max(1, cfg.embriaguezCap);
        int atual = NIVEL.getOrDefault(id, 0);
        int novo = atual + dose;

        // v1.2.10: no teto com a tranca ligada, o corpo DEVOLVE o gole (refluxo)
        if (novo > cap) {
            if (cfg.embriaguezTrancaTeto && atual >= cap) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        IntoxicantesMod.GLUP, SoundSource.PLAYERS, 1.0F, 0.7F);
                player.sendSystemMessage(Component.translatable(
                        "effect.intoxicantes.refluxo", player.getName()));
                player.level().sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.4, player.getZ(),
                        6, 0.2, 0.2, 0.2, 0.02);
            }
            novo = cap;
        }

        // o gole tem som proprio (glup.ogg), pitch variando com a dose
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                IntoxicantesMod.GLUP, SoundSource.PLAYERS, 0.9F,
                0.95F + player.getRandom().nextFloat() * 0.1F);

        // v1.2.13: o "CABELO DO CACHORRO" — dose durante a ressaca remove ela
        // (a ressaca persiste pro tick de decay; o estado do nivel acabou de
        // ser reescrito aqui embaixo, entao a limpeza vem antes)
        if (RESSACA.remove(id) != null) {
            player.sendSystemMessage(Component.translatable(
                    "effect.intoxicantes.ressaca.cabelo", player.getName()));
            BARRA_ENVIADA.remove(id);
        }
        // a dose nova marca o estado bêbado de novo (se cruzar o limiar),
        // realimentando a próxima ressaca
        if (novo >= cfg.embriaguezLimiarFonar) {
            ESTEVE_BEBADO.add(id);
        }

        NIVEL.put(id, novo);
        SOBRIEDADE.put(id, 0);
        BARRA_ENVIADA.remove(id);
        if (novo >= cfg.embriaguezLimiarHic && !PROXIMO_HIC.containsKey(id)) {
            PROXIMO_HIC.put(id, 60 + player.getRandom().nextInt(60));
        }
        // v1.2.54: a bebedeira desidrata (a cabeça de manhã não é só da ressaca)
        SaudeSystem.bebidaDesidratou(player, dose);
        save();

        // v1.2.10: cruzou o limiar da fala bêbada? o Gago do balcão COMENTA
        if (atual < cfg.embriaguezLimiarFonar && novo >= cfg.embriaguezLimiarFonar
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (GagoEntity gago : level.getEntitiesOfClass(GagoEntity.class,
                    player.getBoundingBox().inflate(8.0))) {
                gago.zoFreguesBebado(player);
                break;
            }
        }
    }

    /**
     * v1.2.12: A CURA DA BEBEDEIRA — o Extrato de Cafeína derruba 2 níveis na
     * hora (piso 0). O café não é mágico: nada de reset total de uma vez, e o
     * fregues continua bêbado o suficiente pra lembrar da vergonha.
     * v1.2.46: na RESSACA o extrato encerra ela na hora (antes não fazia NADA
     * — o nível já estava em 0 e o método saía cedo); e a cura em si NUNCA
     * PROVOCA ressaca (antes, sair da bebedeira pelo café cobrava a dose de
     * manhã kkkk — só a BEBIDA que zera marca o estado bêbado).
     */
    static void curar(ServerPlayer player, int niveis) {
        UUID id = player.getUUID();
        int atual = NIVEL.getOrDefault(id, 0);
        int novo = Math.max(0, atual - niveis);

        // v1.2.46 — A CURA MATA A RESSACA: o café líquido do dia seguinte.
        if (RESSACA.remove(id) != null) {
            player.sendSystemMessage(Component.translatable(
                    "effect.intoxicantes.ressaca.cura"));
            BARRA_ENVIADA.remove(id);
            save();
        }

        if (novo == atual) {
            return; // já estava limpo: sem spam (a cura da ressaca já agiu acima)
        }
        NIVEL.put(id, novo);
        SOBRIEDADE.put(id, 0);
        BARRA_ENVIADA.remove(id);
        if (novo < ModConfig.get().embriaguezLimiarHic) {
            PROXIMO_HIC.remove(id); // saiu da zona do soluço
        }
        // v1.2.46: saiu da bebedeira PELO CAFÉ — a memória de travessia vai
        // embora junto, senão o decay até zero ainda cobrava a ressaca depois
        // do cafezinho (só a BEBIDA que rebaixa a zero deixa a conta).
        if (novo < ModConfig.get().embriaguezLimiarFonar) {
            ESTEVE_BEBADO.remove(id);
        }
        player.sendSystemMessage(Component.translatable(
                "effect.intoxicantes.cura", player.getName()));
        // v1.2.58: SEM fumaça — o extrato é um cafezinho, não um incenso.
    }

    /**
     * v1.2.10: efeitos mecânicos graduais — bêbado cambaleia (náusea renovada)
     * e muito bêbado tem os braços de geleia (fraqueza que escala com o nível).
     * Renovados a cada segundo com ambient=true (sem partículas piscando).
     */
    private static void aplicarEfeitosGraduais(ServerPlayer player, int nivel, ModConfig cfg) {
        if (nivel >= cfg.embriaguezLimiarFonar) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, true, false));
        }
        if (nivel >= Math.max(2, cfg.embriaguezLimiarHic - 1)) {
            int amplitude = Math.min(1, (nivel - cfg.embriaguezLimiarFonar) / 3);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 220, amplitude, true, false));
        }
    }

    // ==================================================== HIC

    /** O soluço audível: som + partícula + asterisco no chat do bêbado. */
    private static void darHic(ServerPlayer player, int nivel) {
        int limiar = ModConfig.get().embriaguezLimiarHic;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                IntoxicantesMod.HIC, SoundSource.PLAYERS, 0.8F,
                1.0F + (nivel - limiar) * 0.1F);
        player.level().sendParticles(ParticleTypes.NOTE,
                player.getX(), player.getY() + 1.6, player.getZ(),
                1, 0.2, 0.1, 0.2, 1.0);
        // o HIC é SABOR (som + nota já comunicam) e repete a cada ~10s: no chat
        // viraria spam — fica na action bar, onde perder a linha não perde nada
        player.sendSystemMessage(Component.translatable(
                "effect.intoxicantes.hic", player.getName()), true);
    }

    // ==================================================== FALA BÊBADA

    /**
     * "Fonar": embaralha a fala conforme o nivel (limiares do config).
     * bêbado: troca letras vizinhas de vez em quando + alonga vogais.
     * caído: quase todo par troca, vogais dobram e vem *hic!* intercalado.
     * Determinístico (seed = hash da mensagem) pra teste.
     */
    static String fonar(String texto, int nivel) {
        ModConfig cfg = ModConfig.get();
        int limiarFonar = cfg.embriaguezLimiarFonar;
        if (nivel < limiarFonar || texto.isEmpty()) return texto;
        int limiarHic = cfg.embriaguezLimiarHic;
        boolean caido = nivel >= limiarHic;
        boolean bêbadoForte = nivel >= limiarFonar + 1;
        StringBuilder sb = new StringBuilder(texto.length());
        char anterior = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            char prox = i + 1 < texto.length() ? texto.charAt(i + 1) : 0;
            boolean trocaPar = caido ? ((i * 31 + texto.hashCode()) & 3) != 0
                    : ((i * 31 + texto.hashCode()) & 7) == 0;
            if (Character.isLetter(c) && Character.isLetter(prox) && trocaPar) {
                sb.append(prox).append(c); // troca o par
                i++; // pula o par
                anterior = prox;
                continue;
            }
            // alonga vogais quando a fala já tá pesada
            if (bêbadoForte && isVogal(c) && anterior != c
                    && ((i * 13 + texto.hashCode()) & 3) == 0) {
                sb.append(c);
            }
            sb.append(c);
            anterior = c;
        }
        // v1.2.10: no nível do soluço, o *hic!* entra NA FALA do fregues
        return caido ? intercalarHic(sb.toString(), texto.hashCode()) : sb.toString();
    }

    /**
     * Solução determinística: um *hic!* GARANTIDO em posição que varia por hash
     * (máscara pura de 1/4 por espaço deixava frases curtas sem soluço nenhum),
     * + extras quando a máscara bate.
     */
    private static String intercalarHic(String fala, int seed) {
        int espacos = 0;
        for (int i = 0; i < fala.length(); i++) {
            if (fala.charAt(i) == ' ') espacos++;
        }
        if (espacos == 0) return fala;
        int alvo = (seed & Integer.MAX_VALUE) % espacos;
        StringBuilder sb = new StringBuilder(fala.length() + 16);
        int vistos = 0;
        for (int i = 0; i < fala.length(); i++) {
            sb.append(fala.charAt(i));
            if (fala.charAt(i) == ' ') {
                if (vistos == alvo || ((i * 7 + seed) & 3) == 0) {
                    sb.append("*hic!* ");
                }
                vistos++;
            }
        }
        return sb.toString();
    }

    private static boolean isVogal(char c) {
        return "aeiouAEIOUáéíóúÁÉÍÓÚãõÃÕâêôÂÊÔ".indexOf(c) >= 0;
    }

    // ==================================================== v1.2.13 — RESSACA / CAMBALEIO / BARRA

    /** Entra na ressaca (config; 0 = desligada), com aviso e save. */
    private static void iniciarRessaca(ServerPlayer player) {
        int segundos = ModConfig.get().embriaguezRessacaSegundos;
        RESSACA.remove(player.getUUID()); // reinicia do zero
        if (segundos <= 0) return;
        // v1.2.57: o FÍGADO cobra — cada 250 de dano cumulativo (de 1000)
        // multiplica a ressaca: 0-249 = 1x, 250-499 = 1.5x, 500-749 = 2x,
        // 750+ = 2.5x. O prontuário promete, a ressaca cumpre.
        int figado = SaudeData.danoFigado(player);
        float multiplicador = 1.0F + Math.min(3, figado / 250) * 0.5F;
        segundos = Math.round(segundos * multiplicador);
        // v1.2.46: em SEGUNDOS (era *20 em ticks, decrementado 1 por segundo:
        // a ressaca de 90s durava 30 minutos — o "não cura com o tempo" do playtest)
        RESSACA.put(player.getUUID(), segundos);
        player.sendSystemMessage(Component.translatable("effect.intoxicantes.ressaca.inicio"));
        save();
    }

    /**
     * O CAMBALEIO mecânico: empurrão horizontal aleatório (sem componente
     * vertical — não joga ninguém de penhasco) + fumaça. Package-private:
     * o game test chama direto e verifica o delta.
     */
    static void cambaleia(ServerPlayer player) {
        player.push((player.getRandom().nextDouble() - 0.5) * 0.36, 0.0,
                (player.getRandom().nextDouble() - 0.5) * 0.36);
        player.level().sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
    }

    /** Em ressaca? (consulta pra testes e pra futura UI) */
    static boolean emRessaca(ServerPlayer player) {
        return RESSACA.getOrDefault(player.getUUID(), 0) > 0;
    }

    /** Testes: injeção do timer de ressaca (v1.2.46: em SEGUNDOS). */
    static void setRessacaTeste(ServerPlayer player, int segundos) {
        if (segundos <= 0) RESSACA.remove(player.getUUID());
        else RESSACA.put(player.getUUID(), segundos);
    }

    // ==================================================== CONSULTAS (testes/UI)

    public static int nivel(ServerPlayer player) {
        return NIVEL.getOrDefault(player.getUUID(), 0);
    }

    /**
     * v1.2.13: rótulo como CHAVE DE TRADUÇÃO (era texto pt hardcoded — o en_us
     * não tinha como traduzir). Chaves: rotulo.limpo/alegre/bebado/caido/ressaca.
     */
    public static String chaveRotulo(int nivel) {
        ModConfig cfg = ModConfig.get();
        if (nivel >= cfg.embriaguezLimiarHic) return "effect.intoxicantes.rotulo.caido";
        if (nivel >= cfg.embriaguezLimiarFonar) return "effect.intoxicantes.rotulo.bebado";
        if (nivel >= 1) return "effect.intoxicantes.rotulo.alegre";
        return "effect.intoxicantes.rotulo.limpo";
    }

    /**
     * A BARRA DE ESTADO GRÁFICA: ▮▮▮▮▯▯▯ 4/7 — bêbado, com cor por faixa
     * (verde até o limiar da fala, âmbar até o do soluço, vermelho acima).
     * Enviada NO CHAT (v1.2.14 — action bar de 2s não dá pra ler). O rótulo é
     * chave i18n (Component.translatable) — traduzida no cliente; a RESSACA
     * sobrepõe o rótulo quando existe.
     */
    static Component barraDeEstado(ServerPlayer player, int nivel, ModConfig cfg) {
        StringBuilder barra = new StringBuilder();
        for (int i = 1; i <= cfg.embriaguezCap; i++) {
            barra.append(i <= nivel ? '▮' : '▯');
        }
        String cor = nivel >= cfg.embriaguezLimiarHic ? "\u00a7c"    // vermelho: caído
                : nivel >= cfg.embriaguezLimiarFonar ? "\u00a76"       // âmbar: bêbado
                : "\u00a7a";                                            // verde: alegre
        Component rotulo = Component.translatable(nivel <= 0
                ? (emRessaca(player) ? "effect.intoxicantes.rotulo.ressaca"
                        : "effect.intoxicantes.rotulo.limpo")
                : chaveRotulo(nivel));
        return Component.literal("").append(cor + "\u00a7l" + barra + " \u00a7f" + nivel
                + "/" + cfg.embriaguezCap + " \u00a77— ").append(rotulo);
    }

    /** Testes: injeção direta do estado. */
    static void setNivelTeste(ServerPlayer player, int nivel) {
        if (nivel <= 0) {
            NIVEL.remove(player.getUUID());
        } else {
            NIVEL.put(player.getUUID(), nivel);
        }
        // a injeção realimenta a memória de travessia igual a uma dose real
        if (nivel >= ModConfig.get().embriaguezLimiarFonar) {
            ESTEVE_BEBADO.add(player.getUUID());
        } else {
            ESTEVE_BEBADO.remove(player.getUUID());
        }
        SOBRIEDADE.remove(player.getUUID());
        PROXIMO_HIC.remove(player.getUUID());
        BARRA_ENVIADA.remove(player.getUUID());
        USO_REMAINING.remove(player.getUUID());
        EM_USO.remove(player.getUUID());
    }
}
