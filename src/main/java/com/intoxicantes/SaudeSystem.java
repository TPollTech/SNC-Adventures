package com.intoxicantes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v1.2.54 — O MOTOR DA SAUDE: tick server-authoritative (1x/s por fregues,
 * dentro do END_SERVER_TICK, no padrao do Embriaguez) que decide:
 *
 * - SEDE: hidratacao cai com o tempo (mais rapido correndo, no Nether e
 *   MUITO mais bêbado — a bebedeira seca o fregues). Zerada = fraqueza +
 *   lentidao + nausea — NUNCA dano (regra da casa: sede castiga, nao mata).
 * - VICIO: dose repetida da mesma droga sobe a dependencia (peso do catalogo).
 *   Dependente + tempo limpo = SINDROME DE ABSTINENCIA em estagios (nausea,
 *   tremor, fraqueza, trevas) com COLAPSO que derruba a vida ate 1 CORACAO —
 *   e para ai: o mod NAO mata por abstinencia, deixa na beira.
 * - CURA: dias limpos seguidos derretem o vicio (10/dia); usar de novo zera
 *   o relogio (recaida) e ainda sobe a dependencia — o ciclo cruel.
 * - QUEDA: toda droga do catalogo cobra o aftershock no fim da viagem,
 *   mais pesado quanto maior a intensidade.
 * - EFEITOS POR DROGA: o hook de consumo conta a dose (fim de gole REAL,
 *   mesmo detector do Embriaguez v1.2.12), aplica o efeito assinatura com a
 *   intensidade da janela (1..3) e avisa o client (SaudeNetworking).
 */
public final class SaudeSystem {
    private SaudeSystem() {}

    // ==================================================== ESTADO DE SESSAO
    /** Detector de fim de gole (mesmo esquema do Embriaguez, mapas proprios). */
    private static final Map<UUID, ItemStack> EM_USO = new HashMap<>();
    private static final Map<UUID, Integer> USO_REMAINING = new HashMap<>();
    /** v1.2.56: a sede drena em MILISSEGUNDOS de pontos — multiplicadores
     *  aceleram o relógio SEM arredondar (75s/2x = 1 ponto a cada 37.5s) e
     *  SEM drift de float (inteiros exatos). */
    private static final Map<UUID, Integer> SEDE_MILIS = new HashMap<>();

    /** Segundos restantes ate a QUEDA da viagem atual (0 = sem viagem pendente). */
    private static final Map<UUID, Integer> QUEDA_EM = new HashMap<>();
    /** Qual viagem pendente de queda (CatalogoSubstancias.Viagem.name()). */
    private static final Map<UUID, String> QUEDA_VIAGEM = new HashMap<>();
    /** Estagio atual da abstinencia (0 = fora, 1..4). */
    private static final Map<UUID, Integer> ESTAGIO_ABSTINENCIA = new HashMap<>();
    /** Segundo do proximo colapso (dentro do estagio 4). */
    private static final Map<UUID, Integer> PROXIMO_COLAPSO = new HashMap<>();
    /** Segunda-feira do vicio: ticks limpos acumulados contados por dia. */
    private static final Map<UUID, Integer> RELOGIO_DIA = new HashMap<>();

    // v1.2.56: decay da janela de doses — 5 min limpo = metade (min 1)
    static final long JANELA_SEGUNDOS_DECAY = 300;
    record JanelaKey(UUID jogador, String droga) {}
    private static final Map<JanelaKey, Long> JANELA_DECAY = new HashMap<>();

    /** 
     * v1.2.56: o decay da janela de doses. Roda 1x/s por jogador: quando o
     * prazo vence, metade das doses derrete (piso 1). A janela da droga é
     * recente — a intensidade da viagem desce se você para de usar.
     */
    static void tickDecayJanela(ServerPlayer player) {
        long agora = player.level().getGameTime();
        for (Map.Entry<JanelaKey, Long> e : JANELA_DECAY.entrySet()) {
            if (!e.getKey().jogador().equals(player.getUUID())) continue;
            if (agora < e.getValue()) continue;
            String droga = e.getKey().droga();
            int doses = SaudeData.dosesDaSubstancia(player, droga);
            int novo = Math.max(1, doses / 2);
            SaudeData.setDosesTeste(player, droga, novo);
            e.setValue(agora + JANELA_SEGUNDOS_DECAY * 20L);
        }
    }

    /** Game test: força o prazo do decay da janela pra agora. */
    static void expirarJanelaTeste(ServerPlayer player, String droga) {
        JANELA_DECAY.put(new JanelaKey(player.getUUID(), droga),
                player.level().getGameTime());
    }

    // ==================================================== REGISTRO

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SaudeSystem::tickServidor);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SaudeData.saveSeSujo();
            EM_USO.clear();
            USO_REMAINING.clear();
            SEDE_MILIS.clear();
            QUEDA_EM.clear();
            QUEDA_VIAGEM.clear();
            JANELA_DECAY.clear();
            ESTAGIO_ABSTINENCIA.clear();
            PROXIMO_COLAPSO.clear();
            RELOGIO_DIA.clear();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SaudeData.saveSeSujo());
        // Logout esquece o transitório (o estado pesado persiste no SaudeData)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            EM_USO.remove(id);
            USO_REMAINING.remove(id);
            SEDE_MILIS.remove(id);
            QUEDA_EM.remove(id);
            QUEDA_VIAGEM.remove(id);
            JANELA_DECAY.keySet().removeIf(k -> k.jogador().equals(id));
            ESTAGIO_ABSTINENCIA.remove(id);
            PROXIMO_COLAPSO.remove(id);
            RELOGIO_DIA.remove(id);
        });
    }

    // ==================================================== TICK

    private static void tickServidor(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue; // criativo nao sofre (a casa e' de roleplay, nao de masoquismo)
            }
            tickJogador(player);
        }
        if (server.getTickCount() % 600 == 0) {
            SaudeData.saveSeSujo(); // disco a cada 30s se mudou
        }
    }

    /** Package-private: o game test chama direto e acelera o tempo. */
    static void tickJogador(ServerPlayer player) {
        UUID id = player.getUUID();

        // --- fim de gole: a dose so conta se o consumo COMPLETOU (v1.2.12)
        ItemStack antes = EM_USO.get(id);
        ItemStack agora = player.getUseItem();
        Integer remaining = USO_REMAINING.get(id);
        boolean estavaUsando = antes != null && !antes.isEmpty();
        boolean estaUsando = !agora.isEmpty();
        if (estavaUsando && !estaUsando && remaining != null && remaining <= 1) {
            CatalogoSubstancias.Ficha ficha = CatalogoSubstancias.porItem(antes.getItem());
            if (ficha != null) {
                consumir(player, ficha);
            } else {
                consumirOutros(player, antes);
            }
        }
        EM_USO.put(id, agora);
        if (estaUsando) {
            USO_REMAINING.put(id, player.getUseItemRemainingTicks());
        } else {
            USO_REMAINING.remove(id);
        }

        // --- o resto corre 1x POR SEGUNDO
        if (player.tickCount % 20 != 0) {
            return;
        }
        logicaPorSegundo(player);
    }

    /**
     * A batida de 1x/s: sede, queda e vício. Extraída do gate de tickCount
     * pros game tests chamarem direto (1 chamada = 1 segundo exato).
     */
    static void logicaPorSegundo(ServerPlayer player) {
        ModConfig cfg = ModConfig.get();
        tickSede(player, cfg);
        tickQueda(player);
        tickDecayJanela(player);
        tickVicioEAbstinencia(player, cfg);
        SaudeNetworking.enviarSync(player);
    }

    // ==================================================== SEDE

    private static void tickSede(ServerPlayer player, ModConfig cfg) {
        UUID id = player.getUUID();
        int hidratacao = SaudeData.hidratacao(player);
        if (hidratacao <= 0) {
            aplicarDebulatesDeSede(player);
            return;
        }
        // taxa base: 1 ponto a cada saudeSedeSegundos de vida limpa.
        // multiplicadores: correndo 2x (v1.2.56: 2.5 castigava o jogo normal),
        // mundo quente (Nether) 2x, bêbado (>= fonar) 2x.
        // v1.2.56: dreno FRACIONÁRIO — o multiplicador avança o relógio em
        // frações de ponto (75s/2x = 1 ponto a cada 37.5s), sem o round() que
        // fazia o bêbado drenar na mesma cadência do sóbrio.
        float fator = 1.0F;
        if (player.isSprinting()) fator *= 2.0F;
        // v1.2.54: no 26.3 o "mundo quente" virou EnvironmentAttribute — a
        // checagem direta do Nether é mais legível e imune à mudança de API
        if (player.level().dimension() == net.minecraft.world.level.Level.NETHER) fator *= 2.0F;
        if (Embriaguez.nivel(player) >= cfg.embriaguezLimiarFonar) fator *= 2.0F;
        float avancoMilis = Math.round(fator * 1000.0F); // ms de ponto/segundo
        int limite = cfg.saudeSedeSegundos * 1000;       // ms por ponto inteiro
        int acumulado = SEDE_MILIS.getOrDefault(id, 0) + (int) avancoMilis;
        if (acumulado >= limite) {
            int pontos = acumulado / limite;
            SEDE_MILIS.put(id, acumulado % limite);
            SaudeData.alterarHidratacao(player, -pontos);
            if (SaudeData.hidratacao(player) == 0) {
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.sede.zerou"));
            }
        } else {
            SEDE_MILIS.put(id, acumulado);
        }
        aplicarDebulatesDeSede(player);
    }

    /** Fraqueza/lentidao/nausea conforme a sede cai — dano NUNCA (regra da casa). */
    private static void aplicarDebulatesDeSede(ServerPlayer player) {
        int h = SaudeData.hidratacao(player);
        if (h > 50) return;
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 220, 0, true, false));
        if (h <= 25) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 220, 0, true, false));
        }
        if (h <= 10) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0, true, false));
        }
    }

    // ==================================================== CONSUMO (o coracao dos efeitos por droga)

    /**
     * Uso completo de uma substancia catalogada: conta dose/historico, sobe
     * vicio, aplica o efeito assinatura com a intensidade da janela e agenda
     * a queda. Package-private: o game test chama direto.
     */
    static void consumir(ServerPlayer player, CatalogoSubstancias.Ficha ficha) {
        UUID id = player.getUUID();
        ModConfig cfg = ModConfig.get();

        // 1. doses da janela (escalando a viagem) + historico da vida
        SaudeData.contarDose(player, ficha.id());
        SaudeData.contarDoseVida(player, ficha.categoriaHistorico());
        int doses = SaudeData.dosesDaSubstancia(player, ficha.id());
        int intensidade = Math.min(3, 1 + (doses - 1) / 2); // 1-2:1, 3-4:2, 5+:3

        // 2. o dano de orgao cumulativo (o prontuario exibe, o gameplay cobra)
        SaudeData.danoDeOrgao(player, ficha.orgao(), ficha.danoOrgaoPorDose());

        // 3. o vicio: dose do catalogo; recaida zera o relogio (e a cura)
        if (ficha.vicioPorDose() > 0) {
            SaudeData.subirVicio(player, ficha.id(), ficha.vicioPorDose());
            SaudeData.registrarRecaida(player);
            RELOGIO_DIA.remove(id);
        }

        // 4. a viagem em si: efeito assinatura + intensidade
        int duracaoTicks = ficha.duracaoSegundos() * 20;
        switch (ficha.viagem()) {
            case TRANQUILO -> player.addEffect(new MobEffectInstance(Efeitos.TRANQUILO, duracaoTicks, intensidade - 1, true, true));
            case MORNO -> player.addEffect(new MobEffectInstance(Efeitos.MORNO, duracaoTicks, intensidade - 1, true, true));
            case SONHO -> player.addEffect(new MobEffectInstance(Efeitos.SONHO, duracaoTicks, intensidade - 1, true, true));
            case OVERDRIVE -> player.addEffect(new MobEffectInstance(Efeitos.OVERDRIVE, duracaoTicks, intensidade - 1, true, true));
            case VIAGEM -> player.addEffect(new MobEffectInstance(Efeitos.VIAGEM, duracaoTicks, intensidade - 1, true, true));
        }

        // efeitos vanilla complementares por tier (a base fisica da viagem)
        aplicarBaseVanilla(player, ficha, intensidade);

        // v1.2.57: o PULMÃO cobra — fumante crônico (250+ de dano) tosse seco
        // depois de todo baseado: fraqueza curta + fumaça na cara
        if (ficha.orgao() == SaudeData.Orgao.PULMAO && SaudeData.danoPulmao(player) >= 250) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false));
            player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.4, player.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
        }

        // 5. a QUEDA: agenda o aftershock pro fim da viagem (+15s de gloria)
        QUEDA_EM.put(id, ficha.duracaoSegundos() + 15);
        QUEDA_VIAGEM.put(id, ficha.viagem().name());

        // 6. o client desenha o overlay da viagem (com a intensidade)
        SaudeNetworking.enviarViagem(player, ficha.viagem().name(), intensidade, ficha.duracaoSegundos());

        // 7. sede: droga pesada seca (a boca de algodao e real)
        SaudeData.alterarHidratacao(player, -Math.max(2, ficha.tier() * 3));

        // 8. v1.2.56: a janela DECAY — 5 minutos limpos da droga derretem metade
        // das doses (min 1): a intensidade da viagem é memória RECENTE, não
        // reputação vitalícia. Sem isso, intensidade 3 vira permanente.
        JANELA_DECAY.put(new JanelaKey(id, ficha.id()), player.level().getGameTime() + JANELA_SEGUNDOS_DECAY * 20L);

        // avisinho no chat (sabor; intensidade 2+ muda a fala)
        player.sendSystemMessage(Component.translatable(
                "effect.intoxicantes.viagem." + ficha.id() + (intensidade >= 2 ? ".forte" : ""),
                player.getName()));
    }

    /** A base fisica vanilla de cada viagem (escala com a intensidade). */
    private static void aplicarBaseVanilla(ServerPlayer player, CatalogoSubstancias.Ficha ficha, int intensidade) {
        switch (ficha.viagem()) {
            case TRANQUILO -> {
                // a morgue nao precisa de mais nada: fome e regen vao no tick do efeito
            }
            case MORNO -> { /* anestesia vai no onEffectStarted */ }
            case SONHO -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                    ficha.duracaoSegundos() * 20, 0, true, false));
            case OVERDRIVE -> player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                    ficha.duracaoSegundos() * 20, Math.min(2, intensidade), true, false));
            case VIAGEM -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        Math.max(400, ficha.duracaoSegundos() * 20 + 200), 0, true, false));
            }
        }
    }

    /** O Embriaguez avisa: o fregues bebeu `dose` doses de alcool (desidrata). */
    public static void bebidaDesidratou(ServerPlayer player, int dose) {
        SaudeData.alterarHidratacao(player, -3 * dose);
    }

    /**
     * Fim de gole de consumivel FORA do catalogo (agua, suco, coco, bebidas do
     * mod): a hidratacao das fontes de agua vive aqui — o mesmo detector do
     * gole que conta dose de droga serve pra beber agua kkkk.
     */
    private static void consumirOutros(ServerPlayer player, ItemStack stack) {
        // Garrafa de agua vanilla: +20 de hidratacao (o basico)
        if (stack.is(Items.POTION)
                && stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) != null
                && stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)
                        .is(net.minecraft.world.item.alchemy.Potions.WATER)) {
            SaudeData.alterarHidratacao(player, 20);
        }
        // SUCO DETOX: hidrata e cura os orgaos (v1.2.55: 60 de dano por
        // unidade — reverter uma vida de bebedeira custa ~9 sucos, não 5;
        // a redenção é lenta como tem que ser)
        if (stack.is(IntoxicantesMod.SUCO_DETOX)) {
            SaudeData.alterarHidratacao(player, 35);
            for (SaudeData.Orgao orgao : SaudeData.Orgao.values()) {
                SaudeData.curarOrgao(player, orgao, 60);
            }
            player.sendSystemMessage(Component.translatable("effect.intoxicantes.detox.bebeu"));
        }
        // AGUA DE COCO: o isotopico do sertao (+45 — logo abaixo do suco;
        // o coco na mão é grátis, mas ainda custa achar a praia e subir)
        if (stack.is(IntoxicantesMod.AGUA_DE_COCO)) {
            SaudeData.alterarHidratacao(player, 45);
        }
        // v1.2.58: o COCO in natura — morder o fruto bebe a água dele (+15,
        // logo abaixo da garrafa de água vanilla: o fruto é um lanche de
        // emergência, a garrafa é a infraestrutura)
        if (stack.is(IntoxicantesMod.COCO_FRUTO)) {
            SaudeData.alterarHidratacao(player, 15);
        }
    }

    // ==================================================== QUEDA (o aftershock)

    private static void tickQueda(ServerPlayer player) {
        UUID id = player.getUUID();
        Integer queda = QUEDA_EM.get(id);
        if (queda == null) return;
        if (queda > 1) {
            QUEDA_EM.put(id, queda - 1);
            return;
        }
        QUEDA_EM.remove(id);
        String viagem = QUEDA_VIAGEM.remove(id);
        if (viagem == null) return;
        int doses = 0;
        // a queda escala com a intensidade: o timer foi agendado na dose N,
        // entao reuso a janela atual como proxy (viagens curtas somam doses)
        CatalogoSubstancias.Ficha ficha = fichaDaViagem(viagem);
        if (ficha != null) {
            doses = SaudeData.dosesDaSubstancia(player, ficha.id());
        }
        int intensidade = Math.max(1, Math.min(3, 1 + (doses - 1) / 2));
        switch (viagem) {
            case "MORNO" -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300 * intensidade, 0));
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.queda.morno"));
            }
            case "SONHO" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400 * intensidade, 0));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400 * intensidade, 0));
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.queda.sonho"));
            }
            case "OVERDRIVE" -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300 * intensidade, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 300 * intensidade, 0));
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.queda.overdrive"));
            }
            case "VIAGEM" -> {
                // v1.2.57: o ESTÔMAGO cobra — cada 250 de dano alonga a náusea
                // da queda em 50% (até 2.5x): quem vive de pílula sofre mais
                int fator = 100 + Math.min(3, SaudeData.danoEstomago(player) / 250) * 50;
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                        300 * intensidade * fator / 100, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200 * intensidade, 0));
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.queda.viagem"));
            }
            default -> { /* tranquilo nao cobra: a erva e generosa */ }
        }
    }

    /** Ficha de uma viagem pelo nome do enum (pra reuso na queda). */
    private static CatalogoSubstancias.Ficha fichaDaViagem(String nomeViagem) {
        for (CatalogoSubstancias.Ficha f : CatalogoSubstancias.todas()) {
            if (f.viagem().name().equals(nomeViagem)) return f;
        }
        return null;
    }

    // ==================================================== VICIO + ABSTINENCIA

    private static void tickVicioEAbstinencia(ServerPlayer player, ModConfig cfg) {
        UUID id = player.getUUID();
        int nivel = SaudeData.nivelVicio(player);
        String droga = SaudeData.drogaVicio(player);

        if (nivel <= 0 || droga.isEmpty()) {
            ESTAGIO_ABSTINENCIA.remove(id);
            PROXIMO_COLAPSO.remove(id);
            return;
        }

        // relogio limpo: nada de dose recente — aqui a conta e' simples: o
        // relogioLimpo so cresce; a RECAIDA (consumir) zera (no metodo consumir)
        SaudeData.somarRelogioLimpo(player, 20);
        int limpo = SaudeData.relogioLimpo(player);

        // CURA PROGRESSIVA: 24000 ticks limpos (1 dia) com dependencia = -10 vicio
        int diaContado = RELOGIO_DIA.getOrDefault(id, 0) + 20;
        if (diaContado >= 24000) {
            RELOGIO_DIA.put(id, 0);
            if (limpo >= 7200) { // so conta dia limpo se passou da sindrome aguda
                SaudeData.registrarDiaLimpo(player);
                SaudeData.setVicioTeste(player, droga, Math.max(0, nivel - 10));
                nivel = SaudeData.nivelVicio(player);
                if (nivel <= 0) {
                    SaudeData.zerarVicio(player);
                    ESTAGIO_ABSTINENCIA.remove(id);
                    PROXIMO_COLAPSO.remove(id);
                    player.sendSystemMessage(Component.translatable("effect.intoxicantes.vicio.curado"));
                    return;
                }
                player.sendSystemMessage(Component.translatable("effect.intoxicantes.vicio.dialimpo", nivel));
            }
        } else {
            RELOGIO_DIA.put(id, diaContado);
        }

        // abaixo do limiar de dependencia: sem sindrome (o corpo ainda aguenta)
        if (nivel < cfg.saudeVicioLimiar) {
            ESTAGIO_ABSTINENCIA.remove(id);
            PROXIMO_COLAPSO.remove(id);
            return;
        }

        // dependente + tempo sem a dose = SINDROME DE ABSTINENCIA em estagios
        int estagio = 0;
        if (limpo >= 4L * cfg.saudeAbstinenciaSegundos * 20) estagio = 4;
        else if (limpo >= 2L * cfg.saudeAbstinenciaSegundos * 20) estagio = 3;
        else if (limpo >= cfg.saudeAbstinenciaSegundos * 20) estagio = 2;
        else if (limpo >= (cfg.saudeAbstinenciaSegundos / 2L) * 20) estagio = 1;

        int anterior = ESTAGIO_ABSTINENCIA.getOrDefault(id, 0);
        if (estagio > anterior && estagio == 1) {
            player.sendSystemMessage(Component.translatable(
                    "effect.intoxicantes.abstinencia.inicio",
                    Component.translatable("item.intoxicantes." + droga)));
        }
        ESTAGIO_ABSTINENCIA.put(id, estagio);
        aplicarAbstinencia(player, estagio);

        // O COLAPSO (estagio 4): dano periodico ate 1 CORACAO — e para ai.
        if (estagio == 4) {
            int proximo = PROXIMO_COLAPSO.getOrDefault(id, 30);
            if (--proximo <= 0) {
                proximo = 30; // a cada 30s de colapso
                if (player.getHealth() > 10.0F) {
                    // o colapso é do CORPO: desce a vida direto (o hurt() é
                    // no-op em mock player/invulnerável) — o piso de 1 coração
                    // fica garantido pela própria conta
                    float dano = Math.min(player.getHealth() - 10.0F, 6.0F);
                    player.setHealth(Math.max(10.0F, player.getHealth() - dano));
                    player.hurt(player.damageSources().generic(), dano);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.7F, 0.6F);
                    player.sendSystemMessage(Component.translatable("effect.intoxicantes.abstinencia.colapso"));
                }
            }
            PROXIMO_COLAPSO.put(id, proximo);
        } else {
            PROXIMO_COLAPSO.remove(id);
        }
    }

    /** O combo da sindrome, por estagio (renovado a cada segundo, ambient). */
    private static void aplicarAbstinencia(ServerPlayer player, int estagio) {
        if (estagio <= 0) return;
        player.addEffect(new MobEffectInstance(Efeitos.ABSTINENCIA, 220, estagio - 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 220, 0, true, false));
        if (estagio >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 220, 0, true, false));
        }
        if (estagio >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, true, false));
        }
    }

    // ==================================================== CONSULTAS (testes/client)

    /** Em sindrome de abstinencia agora? (pra HUD/prontuario/teste) */
    public static boolean emAbstinencia(ServerPlayer player) {
        return ESTAGIO_ABSTINENCIA.getOrDefault(player.getUUID(), 0) > 0;
    }

    /** Estagio atual (0..4) — o client usa pro tremor do HUD. */
    public static int estagioAbstinencia(ServerPlayer player) {
        return ESTAGIO_ABSTINENCIA.getOrDefault(player.getUUID(), 0);
    }

    // ==================================================== HELPERS DE TESTE

    static void setQuedaTeste(ServerPlayer player, int segundos, String viagem) {
        if (segundos <= 0) {
            QUEDA_EM.remove(player.getUUID());
            QUEDA_VIAGEM.remove(player.getUUID());
        } else {
            QUEDA_EM.put(player.getUUID(), segundos);
            QUEDA_VIAGEM.put(player.getUUID(), viagem);
        }
    }

    static void limparSessaoTeste(ServerPlayer player) {
        UUID id = player.getUUID();
        EM_USO.remove(id);
        USO_REMAINING.remove(id);
        SEDE_MILIS.remove(id);
        QUEDA_EM.remove(id);
        QUEDA_VIAGEM.remove(id);
        ESTAGIO_ABSTINENCIA.remove(id);
        PROXIMO_COLAPSO.remove(id);
        RELOGIO_DIA.remove(id);
    }

    /**
     * Game test: fim de gole de uma fonte de água (garrafa/detox/coco) sem
     * passar pelo detector de uso do player.
     */
    static void consumirFonteDeAguaTeste(ServerPlayer player, net.minecraft.world.item.ItemStack stack, int deltaEsperado) {
        consumirOutros(player, stack);
    }
}
