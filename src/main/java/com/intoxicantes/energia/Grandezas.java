package com.intoxicantes.energia;

/**
 * v1.2.70 — AS GRANDEZAS DA INSTALAÇÃO (a única conversão do mod).
 *
 * A unidade interna do SNC Energies é o E (SNC energy unit), com taxas em
 * E/tick. A instalação elétrica fala a língua do eletricista:
 *
 *   WATTS  = E/t × 10   (fogão a lenha 80 E/t ≈ 800 W; LED 15 W ≈ 1,5 E/t)
 *   AMPÈRE = W ÷ V      (127 V ou 220 V, definido no circuito)
 *
 * Tudo aqui é EXIBIÇÃO/limiar de gameplay; a energia real trafega em E
 * dentro do SimpleEnergyStorage do quadro — nunca uma segunda unidade.
 */
public final class Grandezas {

    /** Fator de conversão: 1 E/t = 10 W (o fogão de 80 E/t vira 800 W). */
    public static final long W_POR_E_TICK = 10L;

    /** Tensões de circuito suportadas pela arquitetura. */
    public static final long V_127 = 127L;
    public static final long V_220 = 220L;

    private Grandezas() {}

    /** E/t → Watts (arredondado; a exibição não quebra com fração). */
    public static long wattsDe(long ePorTick) {
        return ePorTick * W_POR_E_TICK;
    }

    /** Watts → E/t (arredondando pra cima: a rede cobra o tick cheio). */
    public static long eTickDe(long watts) {
        return (watts + W_POR_E_TICK - 1) / W_POR_E_TICK;
    }

    /** Taxa fracionária para o multímetro, sem arredondar 5 W para 10 W. */
    public static String formatarETick(long watts) {
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f",
                (double) watts / W_POR_E_TICK);
    }

    /** Energia do intervalo: evita cobrar 10 W de uma lâmpada de apenas 5 W. */
    public static long energiaDe(long watts, long ticks) {
        if (watts <= 0 || ticks <= 0) return 0L;
        return Math.ceilDiv(Math.multiplyExact(watts, ticks), W_POR_E_TICK);
    }

    /** Corrente aparente do circuito: A = W ÷ V (mínimo 0; exibição). */
    public static long amperes(long watts, long volts) {
        if (volts <= 0) {
            return 0L;
        }
        return (watts + volts - 1) / volts;   // ceil: não subestimar corrente
    }

    /** Capacidade do disjuntor (A) em watts, pela tensão do circuito. */
    public static long wattsDoDisjuntor(long amperes, long volts) {
        return amperes * volts;
    }

    /** "1.540 W" com separador de milhar pt-BR. */
    public static String formatarW(long watts) {
        return String.format("%,d W", watts).replace(',', '.');
    }

    /** "6,8 A" (uma casa decimal). */
    public static String formatarA(long watts, long volts) {
        if (volts <= 0) {
            return "0,0 A";
        }
        double a = (double) watts / volts;
        return String.format(java.util.Locale.forLanguageTag("pt-BR"),
                "%.1f A", a);
    }

    /** Energia acumulada (E) em unidades legíveis ("10,0 kE", "2,4 ME"). */
    public static String formatarE(long e) {
        if (e >= 1_000_000L) {
            return String.format(java.util.Locale.forLanguageTag("pt-BR"),
                    "%.2f ME", e / 1_000_000.0);
        }
        if (e >= 1_000L) {
            return String.format(java.util.Locale.forLanguageTag("pt-BR"),
                    "%.1f kE", e / 1_000.0);
        }
        return e + " E";
    }
}
