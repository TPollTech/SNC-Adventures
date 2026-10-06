package com.intoxicantes.energia;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * v1.2.70 — UM CIRCUITO DO QUADRO ELÉTRICO: nome (editável na GUI),
 * disjuntor (10/16/20/25/32/40 A), tensão (127/220 V) e estado
 * (LIGADO/DESLIGADO/DEARMADO). O consumo é lido da rede a cada tick de
 * medição (não persiste: é reconstruído da topologia).
 *
 * Sobrecarga: consumo > capacidade do disjuntor em 3 medições de 1 s →
 * DESARMADO (proteção antes de punição). Rearme é manual pela GUI do quadro.
 */
public class CircuitoEletrico {

    /** Amperagens de disjuntor disponíveis (valores reais NBR). */
    public static final long[] AMPERAGENS = {10L, 16L, 20L, 25L, 32L, 40L};

    public static final int LIGADO = 0;
    public static final int DESARMADO = 1;
    public static final int DESLIGADO = 2;

    public String nome = "Circuito";
    public long disjuntorAmperes = 16L;
    public long tensao = V_TENSAO_PADRAO;
    public int estado = LIGADO;

    /** Tensão padrão de fábrica (127 V — a comum das residências). */
    public static final long V_TENSAO_PADRAO = Grandezas.V_127;

    /** Medições consecutivas (1×/s) acima da capacidade — 3 s desarmam. */
    public long ticksSobrecarregado;
    /**
     * Medidas seguidas com fio subdimensionado no circuito (bitola mínima do
     * trecho menor que a corrente) — 5 medidas (5 s) desarmam. Não persiste.
     */
    public long ticksCaboSobrecarregado;
    /** Consumo atual do circuito em W (lido da rede; não persiste). */
    public long consumoWAtual;
    /** Carga solicitada, separada da potência realmente fornecida. */
    public long demandaWAtual;
    /** Defeito de ligação reconstruído do mundo; não persiste. */
    public boolean conflitoLigacao;
    /** Energia total consumida desde a instalação (persiste; estatística). */
    public long totalConsumidoE;

    public CircuitoEletrico() {}

    public CircuitoEletrico(String nome, long disjuntorAmperes, long tensao) {
        this.nome = nome;
        this.disjuntorAmperes = disjuntorAmperes;
        this.tensao = tensao;
    }

    /** Capacidade do circuito em W (A × V). */
    public long capacidadeW() {
        return Grandezas.wattsDoDisjuntor(disjuntorAmperes, tensao);
    }

    /** Consumo atual em W. */
    public long consumoW() {
        return consumoWAtual;
    }

    /** O circuito entrega energia? (LIGADO e não-desarmado) */
    public boolean energizado() {
        return estado == LIGADO;
    }

    /**
     * Medição do tick de rede (1×/s): 3 medições acima da capacidade desarmam.
     * Devolve true se desarmou neste tick.
     */
    public boolean medir() {
        if (estado != LIGADO) {
            ticksSobrecarregado = 0;
            return false;
        }
        if (capacidadeW() > 0 && consumoW() > capacidadeW()) {
            ticksSobrecarregado++;
            if (ticksSobrecarregado >= 3L) {
                estado = DESARMADO;
                ticksSobrecarregado = 0;
                return true;
            }
        } else {
            ticksSobrecarregado = 0;
        }
        return false;
    }

    /** Acumula energia (E) somente depois de a rede confirmar o fornecimento. */
    public void registrarConsumo(long energiaFornecidaE) {
        if (energiaFornecidaE > 0 && energizado()) {
            totalConsumidoE += energiaFornecidaE;
        }
    }

    /** Rearme manual (GUI/clique): DESARMADO → LIGADO. */
    public boolean rearma() {
        if (estado != DESARMADO) {
            return false;
        }
        estado = LIGADO;
        ticksSobrecarregado = 0;
        ticksCaboSobrecarregado = 0;
        return true;
    }

    // ==================================================== PERSISTÊNCIA

    public void save(ValueOutput output) {
        output.putString("nome", nome);
        output.putLong("disjuntor", disjuntorAmperes);
        output.putLong("tensao", tensao);
        output.putInt("estado", estado);
        output.putLong("total", totalConsumidoE);
    }

    public void load(ValueInput input) {
        nome = input.getStringOr("nome", "Circuito");
        long salvoA = input.getLongOr("disjuntor", disjuntorAmperes);
        disjuntorAmperes = java.util.Arrays.stream(AMPERAGENS).anyMatch(a -> a == salvoA)
                ? salvoA : disjuntorAmperes;
        long salvoV = input.getLongOr("tensao", tensao);
        tensao = salvoV == Grandezas.V_220 ? Grandezas.V_220 : Grandezas.V_127;
        estado = Math.clamp(input.getIntOr("estado", LIGADO), LIGADO, DESLIGADO);
        totalConsumidoE = Math.max(0L, input.getLongOr("total", 0L));
        ticksSobrecarregado = ticksCaboSobrecarregado = 0L;
        consumoWAtual = demandaWAtual = 0L;
    }
}
