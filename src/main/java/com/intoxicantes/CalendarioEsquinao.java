package com.intoxicantes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

/**
 * v1.2.69 — O CALENDÁRIO DO ESQUINÃO: dias da semana do mundo, igual à vida
 * real. O dia comercial segue o {@link DailyTradeStock} (muda às 07:00, com
 * a entrega — 06:00 é a virada vanilla), então a semana também: cada "dia"
 * começa às 07:00 e a QUARTA-FEIRA é o dia de entrega do mercadinho
 * (estilo My Summer Car: o caminhão chega, a prateleira enche).
 *
 * A contagem é DERIVADA — convenção da casa: o DIA 0 do mundo é um SÁBADO
 * (a semana do Esquinão começa no sábado e a entrega chega na quarta;
 * mundos novos nascem com as gôndolas do template abastecidas e a 1ª
 * entrega cai na 1ª quarta do mundo). Sem NBT nenhum: nunca quebra save,
 * nunca desincroniza e o dia da semana é o MESMO pra qualquer sistema
 * (HUD, prateleira, Gago). Duas APIs: a do DIA COMERCIAL (07:00, a semana
 * do comércio) e a do amanhecer vanilla (06:00) pro HUD nomear a data em
 * que o jogador acorda.
 */
public final class CalendarioEsquinao {

    /** A semana começa no SÁBADO (convenção da casa — o dia 0 do mundo). */
    public static final int SATURDAY = 0;
    public static final int SUNDAY = 1;
    public static final int MONDAY = 2;
    public static final int TUESDAY = 3;
    public static final int WEDNESDAY = 4;
    public static final int THURSDAY = 5;
    public static final int FRIDAY = 6;

    /** O dia da entrega do mercadinho (o caminhão do My Summer Car). */
    public static final int DIA_ENTREGA = WEDNESDAY;

    private static final String[] NOMES = {
            "sabado", "domingo", "segunda", "terca", "quarta", "quinta", "sexta"};
    /** Sufixo ordinal pt-BR ("em plena terça-feira, dia 21"). */
    private static final String[] SUFIXOS = {"o", "o", "a", "a", "a", "a", "a"};

    private CalendarioEsquinao() {}

    /** A semana segue o DIA COMERCIAL (07:00 — a hora da entrega). */
    public static int diaDaSemana(long dayTime) {
        long diaComercial = DailyTradeStock.tradingDay(dayTime);
        return Math.floorMod((int) diaComercial, 7);
    }

    /** A semana do AMANHECER vanilla (06:00) — pro HUD nomear a data. */
    public static int diaDaSemanaAmanhecer(long dayTime) {
        long diaVanilla = Math.floorDiv(dayTime, 24000L);
        return Math.floorMod((int) diaVanilla, 7);
    }

    /** Ticks totais do relógio do overworld (mesma fonte do MarketSystem). */
    public static long tempoTotal(ServerLevel level) {
        Holder<WorldClock> clock = level.registryAccess()
                .lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        return level.clockManager().getInstance(clock).totalTicks();
    }

    public static int diaDaSemana(ServerLevel level) {
        return diaDaSemana(tempoTotal(level));
    }

    public static boolean hojeEDiaDeEntrega(ServerLevel level) {
        return diaDaSemana(level) == DIA_ENTREGA;
    }

    /**
     * O PRÓXIMO dia de entrega (contando o de hoje): 0 = é hoje. O carimbo
     * da prateleira usa ("entrega em X dia(s)"); o check de virada usa o
     * total de ticks (04:59 do dia errado não abre a entrega).
     */
    public static int diasAteEntrega(int diaSemana) {
        return Math.floorMod(DIA_ENTREGA - diaSemana, 7);
    }

    public static long proximoDiaDeEntregaTicks(long dayTime) {
        int dia = diaDaSemana(dayTime);
        long diaComercial = DailyTradeStock.tradingDay(dayTime);
        return (diaComercial + diasAteEntrega(dia)) * 24000L + 1000L;
    }

    public static String nome(int diaSemana) {
        return NOMES[Math.floorMod(diaSemana, 7)];
    }

    /** Sufixo do ordinal ("21o", "4a") — o placar usa em pt. */
    public static String sufixo(int diaSemana) {
        return SUFIXOS[Math.floorMod(diaSemana, 7)];
    }

    /** true se ESTE tick já pertence ao outro dia (a semana virou à meia-noite
     *  comercial de 07:00 — o check de virada do estoque usa). */
    public static boolean virouDeDia(long dayTimeAnterior, long dayTimeNovo) {
        return DailyTradeStock.tradingDay(dayTimeAnterior)
                < DailyTradeStock.tradingDay(dayTimeNovo);
    }
}
