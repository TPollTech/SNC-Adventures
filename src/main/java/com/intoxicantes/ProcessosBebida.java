package com.intoxicantes;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * O LIVRO-DE-RECEITAS DAS BEBIDAS (v1.2.50) — o registro central de todos os
 * processos: fermentação de dorna, destilação de alambique, fermentação/
 * maturação de barril e engarrafamento. Tudo aqui, NADA espalhado pelas
 * máquinas: o Alambique não sabe o que é rum, ele apenas consulta este
 * registro com o item que recebeu.
 *
 * ARQUITETURA EXTENSÍVEL: uma bebida futura (uísque, vodka, saquê...) é só
 * registrar as etapas dela aqui (e os itens intermediários) — as MÁQUINAS
 * (dorna, alambique, barril) são genéricas e não mudam. O hidromel segue
 * como receita de crafting simples (não tem cadeia pedida).
 *
 * Tempos: segundos de JOGO convertidos em ticks (20/s) — configuráveis no
 * ModConfig (nada de relógio de parede do computador). O progresso mora nas
 * BlockEntities (sobrevive a save/restart/chunk descarregado) e toda a lógica
 * roda no servidor.
 *
 * v1.2.59 (GUI das máquinas): cada receita carrega o PRÓPRIO tempo em
 * segundos ({@code tempoSeg}) — acabaram os 40s/30s do caldeirão espalhados
 * fora do registro. As dornas/alambiques continuam com as bases globais
 * (SEG_DORNA_BASE/SEG_ALAMBIQUE_BASE, que o Guia trava em teste). A regra de
 * consumo é UNIVERSAL: aceita pilha MAIOR que a dose e consome EXATAMENTE a
 * dose ({@code >=} na largada, {@code qtdIn} consumidos) — sobra fica no
 * buffer pra o próximo lote. O par (input, qtdIn) → (output, qtdOut) é a
 * UNIDADE DE PRODUÇÃO da máquina: nenhum número mágico fora daqui.
 */
public final class ProcessosBebida {

    private ProcessosBebida() {}

    // ==================================================== TIPOS DE RECEITA

    /**
     * FERMENTAÇÃO DE DORNA: mosto in natura → mosto fermentado.
     * (caldo de cana, mosto de rum; futuras frutas caem aqui de graça)
     */
    public record Dorna(Item input, int qtdIn, Item output, int qtdOut) {}

    /**
     * DESTILAÇÃO DE ALAMBIQUE: mosto fermentado → destilado jovem.
     * Reutilizada por cachaça, rum e futuros destilados (uísque, tequila...).
     */
    public record Alambique(Item input, int qtdIn, Item output, int qtdOut) {}

    /**
     * BARRIL: enche o barril com o insumo; ele FERMENTA (fase 1, opcional se
     * tempoFermentacao == 0) e/ou MADIURA (fase 2) e depois rende
     * {@code garrafas} garrafas da bebida final (retiradas 1 a 1 com garrafa
     * de vidro).
     *
     * Fase 1 = FERMENTANDO (mosto cru; bolhas/partículas);
     * Fase 2 = MATURANDO (destilado jovem ou pós-fermentação; silencioso).
     * Cerveja/vinho: fase 1 + fase 2 (condicionamento dentro do mesmo barril,
     * como a spec pede). Cachaça/rum: só fase 2 (o alambique já entregou o
     * jovem destilado).
     */
    public record Barril(String bebida, Item input, int qtdIn,
            int tempoFermentacaoSeg, int tempoMaturacaoSeg,
            Item bebidaFinal, int garrafas) {}

    /**
     * Uma máquina de PRIMA (moenda/prensa/caldeirão): entrada → saída direta.
     * O caldeirão usa {@code secIn}/{@code secQtd}: o SEGUNDO ingrediente da
     * mostura (o lúpulo da fervura), carregado DEPOIS do principal — duas
     * doses no mesmo caldeirão, como na cervejaria real. secIn null = máquina
     * de ingrediente único (moenda, prensa).
     *
     * v1.2.59: {@code tempoSeg} = duração da PRIMEIRA dose (mostura) em
     * segundos de design — a fervura continua fixa em 30s (as duas metades do
     * ciclo de cerveja). Moenda/prensa usam só o tempoSeg.
     */
    public record Prima(Item input, int qtdIn, Item output, int qtdOut,
            Item secIn, int secQtd, Item extraOut, int extraQtd, int tempoSeg) {}

    // ==================================================== O REGISTRO
    // v1.2.60 — DATAPACK: as listas moram no CatalogoBebidas (JSON de
    // data/intoxicantes/processo_bebida/<maquina>/ com serializer próprio).
    // Esta classe continua a FACHADA única de leitura (máquinas, GUIs e Guia
    // não sabem de onde os números vêm — fonte única preservada).

    private static List<Dorna> DORNAS() {
        return CatalogoBebidas.dornas();
    }

    private static List<Alambique> ALAMBIQUES() {
        return CatalogoBebidas.alambiques();
    }

    private static List<Barril> BARRIS() {
        return CatalogoBebidas.barris();
    }

    private static List<Prima> listaDe(MaquinaPrimaBlock.Tipo tipo) {
        return switch (tipo) {
            case MOENDA -> CatalogoBebidas.moendas();
            case PRENSA -> CatalogoBebidas.prensas();
            case CALDEIRAO -> CatalogoBebidas.caldeiroes();
        };
    }

    /**
     * Legado do registro em código: hoje só AQUECE o catálogo (os defaults de
     * fábrica entram em memória antes do reload do datapack — o Guia e os
     * tooltips de item construídos no init já têm receita pra ler). Mantido
     * porque GuiaConteudo e o boot de gametests o chamam.
     */
    static void registrar() {
        CatalogoBebidas.dornas();
        CatalogoBebidas.alambiques();
        CatalogoBebidas.barris();
        CatalogoBebidas.moendas();
        CatalogoBebidas.prensas();
        CatalogoBebidas.caldeiroes();
    }

    // ==================================================== CONSULTAS

    /** A fermentação de dorna que consome {@code qtd} deste item (a pilha da mão
     *  pode ser MAIOR: consome só o qtdIn — v1.2.51, fim da exigência de quantidade exata). */
    public static Optional<Dorna> dornaQueAceita(Item item, int qtd) {
        for (Dorna r : DORNAS()) {
            if (r.input() == item && r.qtdIn() <= qtd) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Alguma receita de dorna usa este item? (tooltip da máquina) */
    public static Optional<Dorna> dornaDe(Item item) {
        for (Dorna r : DORNAS()) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static Optional<Alambique> alambiqueQueAceita(Item item, int qtd) {
        for (Alambique r : ALAMBIQUES()) {
            if (r.input() == item && r.qtdIn() <= qtd) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static Optional<Alambique> alambiqueDe(Item item) {
        for (Alambique r : ALAMBIQUES()) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** A receita de barril de uma bebida (id "cachaca", "cerveja"...). */
    public static Optional<Barril> barrilDe(String bebida) {
        for (Barril r : BARRIS()) {
            if (r.bebida().equals(bebida)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Qual barril aceita este insumo? (tooltip/instinto do jogador) */
    public static Optional<Barril> barrilQueAceita(Item item) {
        for (Barril r : BARRIS()) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** v1.2.59: aceita pilha MAIOR que a dose (a exigência de quantidade exata
     *  que sobrava aqui era fonte do bug "exatamente 6"). */
    public static Optional<Prima> moendaQueAceita(Item item, int qtd) {
        for (Prima r : listaDe(MaquinaPrimaBlock.Tipo.MOENDA)) {
            if (r.input() == item && r.qtdIn() <= qtd) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** v1.2.59: aceita pilha MAIOR que a dose (idem moenda). */
    public static Optional<Prima> prensaQueAceita(Item item, int qtd) {
        for (Prima r : listaDe(MaquinaPrimaBlock.Tipo.PRENSA)) {
            if (r.input() == item && r.qtdIn() <= qtd) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** A receita de máquina de prima por TIPO (moenda/prensa/caldeirão). */
    public static Optional<Prima> primaDe(MaquinaPrimaBlock.Tipo tipo, Item item, int qtd) {
        for (Prima r : listaDe(tipo)) {
            // v1.2.51: aceita pilha MAIOR que a receita (consome só qtdIn);
            // antes exigia contagem EXATA (6 uvas = 6, nem 7, nem uma pilha)
            if (r.input() == item && r.qtdIn() <= qtd) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Alguma receita deste tipo de máquina usa o insumo como PRINCIPAL? */
    public static Optional<Prima> primaPrincipal(MaquinaPrimaBlock.Tipo tipo, Item item) {
        for (Prima r : listaDe(tipo)) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static List<Barril> barris() {
        return List.copyOf(BARRIS());
    }

    // ==================================================== CATÁLOGO PRA GUI (v1.2.59)
    // A tela da máquina lista "o que ela produz" lendo o registro daqui —
    // mesmo jeito do Guia. Nada de receita escrita duas vezes.

    /** Receitas de prima por tipo de máquina (moenda/prensa/caldeirão). */
    public static List<Prima> receitasPrima(MaquinaPrimaBlock.Tipo tipo) {
        return List.copyOf(listaDe(tipo));
    }

    /** Receitas de dorna (fermentação fora do barril). */
    public static List<Dorna> receitasDorna() {
        return List.copyOf(DORNAS());
    }

    /** Receitas de alambique (destilação). */
    public static List<Alambique> receitasAlambique() {
        return List.copyOf(ALAMBIQUES());
    }

    /** Receitas de barril (todas as bebidas engarrafáveis). */
    public static List<Barril> receitasBarril() {
        return List.copyOf(BARRIS());
    }

    // ==================================================== TEMPO NAS RECEITAS (v1.2.59)

    /** Segundos de design da dose PRINCIPAL da prima (mostura, no caldeirão). */
    public static int segPrima(Prima r) {
        return r.tempoSeg();
    }

    /** Ticks da dose principal da prima, com a escala do config. */
    public static int tempoPrima(Prima r) {
        return ModConfig.ticksDeSegundos(r.tempoSeg());
    }

    // ==================================================== LOTE POR DOSE (v1.2.59)
    // Regra universal das máquinas: pilha >= dose inicia UM lote; consome
    // EXATAMENTE a dose; a sobra fica no buffer e o lote seguinte começa
    // sozinho (produção contínua lote a lote — nunca tudo de uma vez).

    /** A receita de dorna pra UM lote deste item (dose ignorando a pilha). */
    public static Optional<Dorna> dornaQueAceitaLote(Item item) {
        for (Dorna r : DORNAS()) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** A receita de alambique pra UM lote deste item. */
    public static Optional<Alambique> alambiqueQueAceitaLote(Item item) {
        for (Alambique r : ALAMBIQUES()) {
            if (r.input() == item) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** A receita de prima pra UM lote deste item neste tipo de máquina. */
    public static Optional<Prima> primaQueAceitaLote(MaquinaPrimaBlock.Tipo tipo, Item item) {
        return primaPrincipal(tipo, item);
    }

    /** A receita de prima em curso num lote (item + dose carregada). */
    private static Optional<Prima> lote(List<Prima> lista, Item item, int qtdCarregada) {
        for (Prima r : lista) {
            if (r.input() == item && r.qtdIn() <= qtdCarregada) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** A receita de prima do lote carregado por tipo (leitura do motor). */
    public static Optional<Prima> primaDoLote(MaquinaPrimaBlock.Tipo tipo, Item item, int qtdCarregada) {
        return lote(listaDe(tipo), item, qtdCarregada);
    }

    // ==================================================== CONSULTAS PRA GUIA
    // (o Guia do SNC Adventures lê o registro direto — tempos/quantidades
    // nunca são escritos em dois lugares, regra data-driven do AGENTS.md)

    /** Base de tempo da DORNA em segundos (a BE escala pelo config). */
    public static final int SEG_DORNA_BASE = 420;
    /** Base de tempo do ALAMBIQUE em segundos (a BE escala pelo config). */
    public static final int SEG_ALAMBIQUE_BASE = 90;

    /** A receita da PRENSA que usa este item como principal (guia). */
    public static Optional<Prima> prensaDe(Item item) {
        return primaPrincipal(MaquinaPrimaBlock.Tipo.PRENSA, item);
    }

    /** A receita da MOENDA que usa este item como principal (guia). */
    public static Optional<Prima> moendaDe(Item item) {
        return primaPrincipal(MaquinaPrimaBlock.Tipo.MOENDA, item);
    }

    /** A receita do CALDEIRÃO que usa este item como principal (guia). */
    public static Optional<Prima> caldeiraoDe(Item item) {
        return primaPrincipal(MaquinaPrimaBlock.Tipo.CALDEIRAO, item);
    }

    // ==================================================== HELPERS DE LOTE

    /**
     * Segundos de cada fase com o balanceamento do ModConfig (as receitas
     * declaram "ritmo base" e o config escala — teste rápido fica fácil).
     */
    public static int tempoFermentacao(Barril r) {
        int base = r.tempoFermentacaoSeg();
        // v1.2.51: base está em SEGUNDOS de design — converte pra ticks (antes
        // o número entrava como ticks: tudo 20× mais rápido que o planejado)
        return base == 0 ? 0 : ModConfig.ticksDeSegundos(base);
    }

    public static int tempoMaturacao(Barril r) {
        int base = r.tempoMaturacaoSeg();
        return base == 0 ? 0 : ModConfig.ticksDeSegundos(base);
    }

    public static int tempoDorna() {
        return ModConfig.ticksDeSegundos(420F);
    }

    public static int tempoAlambique() {
        return ModConfig.ticksDeSegundos(90F);
    }

    public static int garrafasPorLote(Barril r) {
        return Math.max(1, ModConfig.get().bebidaGarrafasPorLote);
    }

    /**
     * Ícone de lote: 1 item "jarra" invisível no barril? NÃO — o lote inteiro
     * mora como contagem no BlockEntity (sem item fantasma).
     */

    /** Tooltip do item intermediário: "etapa X da cadeia de Y" (lore automática). */
    public static Component etapaTooltip(String chave) {
        return Component.translatable(chave).withStyle(s -> s.withColor(0xB8B0A0)
                .withItalic(false));
    }

    /** Stack de um intermediário com a lore da etapa (usado pelos registros do mod). */
    public static ItemStack comEtapa(Item item, String chave) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(etapaTooltip(chave))));
        return stack;
    }

    /** A garrafa de vidro do vanilla (o item de engarrafamento). */
    public static boolean eGarrafa(Item item) {
        return item == Items.GLASS_BOTTLE;
    }

    // ==================================================== GARRAFAS VAZIAS (v1.2.66)

    /**
     * A GARRAFA VAZIA da bebida (o recipiente que volta ao beber). Se a
     * bebida não tiver vazia registrada, cai na garrafa de vidro vanilla.
     */
    public static Item garrafaVaziaDe(Item bebida) {
        var chave = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bebida);
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID,
                        chave.getPath() + "_vazia")).orElse(Items.GLASS_BOTTLE);
    }

    /** O item é a VAZIA da bebida exata da receita? (o barril só engarrafa na dela) */
    public static boolean eVaziaDoBarril(Item item, Optional<Barril> rec) {
        if (rec.isEmpty() || !(item instanceof GarrafaVaziaItem)) {
            return false;
        }
        return garrafaVaziaDe(rec.get().bebidaFinal()) == item;
    }

    /**
     * O barril engarrafa com a garrafa de vidro vanilla OU com a VAZIA DA
     * PRÓPRIA bebida (v1.2.66: o fregues devolve o vidro ao beber — o barril
     * aceita o vidro de volta). A vazia de OUTRA bebida não serve.
     */
    public static boolean eGarrafaDoBarril(Item item, Optional<Barril> rec) {
        return item == Items.GLASS_BOTTLE || eVaziaDoBarril(item, rec);
    }
}
