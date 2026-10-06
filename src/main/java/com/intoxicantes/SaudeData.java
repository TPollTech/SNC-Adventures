package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v1.2.54 — SAUDE DO FREGUES: a espinha dos 3 sistemas (SEDE, VICIO e
 * PRONTUARIO), num arquivo so do mundo — intoxicantes_saude.json, o mesmo
 * padrao do Embriaguez/PlayerMoney/FidelidadeData (Gson bonito, save no tick
 * de mudanca, load no boot, initTeste pros game tests).
 *
 * Estado por player:
 *  - hidratacao (0..100)      : a barra de sede
 *  - danoFigado/Pulmao/Estomago (0..1000): o historico de vida que o PRONTUARIO
 *    exibe e que escala ressaca/tosses/nausea — cura lenta pelo suco detox
 *  - nivelVicio (0..100)      : a dependencia da droga atual (uma por vez;
 *    trocar de droga persiste a antiga em meia taxa)
 *  - drogaVicio               : qual substancia vicia o fregues agora
 *  - dosesDaSubstancia        : mapa por substancia — a "janela" que os efeitos
 *    por droga usam pra escalar a viagem (dose repetida = trip mais forte)
 *  - dosesVida                : mapa por categoria (alcool/erva/po/pilula) —
 *    o historico do prontuario ("214 bebidas na vida")
 *  - relogioLimpo (ticks)     : quanto tempo sem a droga do vicio (abstinencia)
 *  - curasSeguidas            : dias limpos seguidos — a cura progressiva
 *
 * Server-authoritative: nada disso existe no client sem o SaudeNetworking.
 */
public final class SaudeData {
    private SaudeData() {}

    /** Limites do domínio. */
    public static final int HIDRATACAO_MAX = 100;
    public static final int ORGAO_MAX = 1000;
    public static final int VICIO_MAX = 100;

    /** Estado completo de um fregues. */
    public static final class Estado {
        public int hidratacao = HIDRATACAO_MAX;
        public int danoFigado;
        public int danoPulmao;
        public int danoEstomago;
        public int nivelVicio;
        public String drogaVicio = "";
        public Map<String, Integer> dosesDaSubstancia = new HashMap<>();
        public Map<String, Integer> dosesVida = new HashMap<>();
        public int relogioLimpo;
        public int curasSeguidas;
    }

    private static final Map<UUID, Estado> ESTADOS = new HashMap<>();
    private static boolean sujo; // tem algo pra gravar na parada do servidor

    // ==================================================== PERSISTENCIA

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File saveFile;

    private static class Salvo {
        Map<UUID, Estado> estados = new HashMap<>();
    }

    /** Chamado no boot do servidor (SERVER_STARTING), igual ao Embriaguez. */
    public static void init(File worldDir) {
        saveFile = new File(worldDir, "intoxicantes_saude.json");
        load();
    }

    private static void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (Reader reader = Files.newBufferedReader(saveFile.toPath())) {
            Type tipo = TypeToken.get(Salvo.class).getType();
            Salvo lido = GSON.fromJson(reader, tipo);
            if (lido != null && lido.estados != null) {
                ESTADOS.clear();
                ESTADOS.putAll(lido.estados);
            }
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao carregar saude: {}", e.getMessage());
        }
    }

    /** Package-private: o game test aponta a persistencia pra um arquivo proprio. */
    static void initTeste(File arquivo) {
        saveFile = arquivo;
        ESTADOS.clear();
        sujo = false;
    }

    static void loadTeste() {
        load();
    }

    /** Package-private: o game test faz round-trip sem reiniciar o servidor. */
    static void save() {
        if (saveFile == null) return;
        Salvo salvo = new Salvo();
        salvo.estados = ESTADOS;
        try (Writer writer = Files.newBufferedWriter(saveFile.toPath())) {
            GSON.toJson(salvo, writer);
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao salvar saude: {}", e.getMessage());
        }
        sujo = false;
    }

    /** A parada do servidor grava se algo mudou (chamado pelo SaudeSystem). */
    static void saveSeSujo() {
        if (sujo) save();
    }

    private static Estado de(ServerPlayer player) {
        return ESTADOS.computeIfAbsent(player.getUUID(), id -> new Estado());
    }

    private static void marcar() {
        sujo = true;
    }

    // ==================================================== HIDRATACAO

    public static int hidratacao(ServerPlayer player) {
        return de(player).hidratacao;
    }

    /** Altera a sede (clamp 0..100). Negativo desidrata, positivo hidrata. */
    public static void alterarHidratacao(ServerPlayer player, int delta) {
        Estado e = de(player);
        int novo = Math.max(0, Math.min(HIDRATACAO_MAX, e.hidratacao + delta));
        if (novo != e.hidratacao) {
            e.hidratacao = novo;
            marcar();
        }
    }

    // ==================================================== ORGAOS

    public static int danoFigado(ServerPlayer player) {
        return de(player).danoFigado;
    }

    public static int danoPulmao(ServerPlayer player) {
        return de(player).danoPulmao;
    }

    public static int danoEstomago(ServerPlayer player) {
        return de(player).danoEstomago;
    }

    /** Acumula dano de orgao (clamp 0..1000). Doses sao `acrescimo`. */
    public static void danoDeOrgao(ServerPlayer player, Orgao orgao, int acrescimo) {
        if (acrescimo <= 0) return;
        Estado e = de(player);
        switch (orgao) {
            case FIGADO -> e.danoFigado = Math.min(ORGAO_MAX, e.danoFigado + acrescimo);
            case PULMAO -> e.danoPulmao = Math.min(ORGAO_MAX, e.danoPulmao + acrescimo);
            case ESTOMAGO -> e.danoEstomago = Math.min(ORGAO_MAX, e.danoEstomago + acrescimo);
        }
        marcar();
    }

    /** O suco detox: regenera o orgao (diminishing returns fica no SaudeSystem). */
    public static void curarOrgao(ServerPlayer player, Orgao orgao, int cura) {
        if (cura <= 0) return;
        Estado e = de(player);
        switch (orgao) {
            case FIGADO -> e.danoFigado = Math.max(0, e.danoFigado - cura);
            case PULMAO -> e.danoPulmao = Math.max(0, e.danoPulmao - cura);
            case ESTOMAGO -> e.danoEstomago = Math.max(0, e.danoEstomago - cura);
        }
        marcar();
    }

    /** O pior orgao do fregues (pro veredito e pros multiplicadores). */
    public static Orgao piorOrgao(ServerPlayer player) {
        Estado e = de(player);
        if (e.danoFigado >= e.danoPulmao && e.danoFigado >= e.danoEstomago) return Orgao.FIGADO;
        if (e.danoPulmao >= e.danoEstomago) return Orgao.PULMAO;
        return Orgao.ESTOMAGO;
    }

    public enum Orgao { FIGADO, PULMAO, ESTOMAGO }

    /** Dano do orgao pelo enum (pro prontuario desenhar as 3 barras em laco). */
    public static int danoDe(ServerPlayer player, Orgao orgao) {
        return switch (orgao) {
            case FIGADO -> danoFigado(player);
            case PULMAO -> danoPulmao(player);
            case ESTOMAGO -> danoEstomago(player);
        };
    }

    // ==================================================== VICIO

    public static int nivelVicio(ServerPlayer player) {
        return de(player).nivelVicio;
    }

    public static String drogaVicio(ServerPlayer player) {
        return de(player).drogaVicio;
    }

    /** Testes/sistema: injeta o estado do vicio (nivel + droga) com clamp. */
    static void setVicioTeste(ServerPlayer player, String droga, int nivel) {
        Estado e = de(player);
        e.drogaVicio = droga == null ? "" : droga;
        e.nivelVicio = Math.max(0, Math.min(VICIO_MAX, nivel));
        marcar();
    }

    /**
     * Sobe o vicio da droga atual (clamp 0..100). Chamar nos limites do uso
     * (SaudeSystem.decide), nunca por tick.
     */
    static void subirVicio(ServerPlayer player, String droga, int acrescimo) {
        Estado e = de(player);
        if (!e.drogaVicio.equals(droga)) {
            // trocou de droga: a antiga persiste em MEIA taxa (o corpo não esquece)
            e.nivelVicio = e.nivelVicio / 2;
            e.drogaVicio = droga;
            e.relogioLimpo = 0;
        }
        e.nivelVicio = Math.min(VICIO_MAX, e.nivelVicio + acrescimo);
        marcar();
    }

    static void zerarVicio(ServerPlayer player) {
        Estado e = de(player);
        e.nivelVicio = 0;
        e.drogaVicio = "";
        e.relogioLimpo = 0;
        e.curasSeguidas = 0;
        marcar();
    }

    // ==================================================== DOSES (janela das viagens + historico)

    /** Doses da substancia na janela atual (escala a intensidade da viagem). */
    public static int dosesDaSubstancia(ServerPlayer player, String droga) {
        return de(player).dosesDaSubstancia.getOrDefault(droga, 0);
    }

    /** v1.2.56: define as doses da janela (o decay da viagem usa). */
    static void setDosesTeste(ServerPlayer player, String droga, int doses) {
        de(player).dosesDaSubstancia.put(droga, Math.max(0, doses));
        marcar();
    }

    static void contarDose(ServerPlayer player, String droga) {
        Estado e = de(player);
        e.dosesDaSubstancia.merge(droga, 1, Integer::sum);
        marcar();
    }

    /** Historico da vida, por categoria: alcool/erva/po/pilula. */
    public static int dosesVida(ServerPlayer player, String categoria) {
        return de(player).dosesVida.getOrDefault(categoria, 0);
    }

    static void contarDoseVida(ServerPlayer player, String categoria) {
        de(player).dosesVida.merge(categoria, 1, Integer::sum);
        marcar();
    }

    // ==================================================== RELOGIO LIMPO (abstinencia/cura)

    public static int relogioLimpo(ServerPlayer player) {
        return de(player).relogioLimpo;
    }

    public static int curasSeguidas(ServerPlayer player) {
        return de(player).curasSeguidas;
    }

    /** Ticks limpos acumulados (a cada tick do SaudeSystem, 1x/s). */
    static void somarRelogioLimpo(ServerPlayer player, int ticks) {
        de(player).relogioLimpo += ticks;
        marcar();
    }

    static void registrarRecaida(ServerPlayer player) {
        Estado e = de(player);
        e.relogioLimpo = 0;
        e.curasSeguidas = 0;
        marcar();
    }

    static void registrarDiaLimpo(ServerPlayer player) {
        Estado e = de(player);
        e.curasSeguidas++;
        marcar();
    }
}
