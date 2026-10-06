package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Canonical prices and daily quantities for the market and street dealer. */
final class TradeCatalog {
    private TradeCatalog() {}

    public record Entry(String id, Item item, int count, int price, int stock, int tier, boolean harvest) {
        public ItemStack stack() { return new ItemStack(item, count); }
    }

    /**
     * Mercado do Esquinão: oito bebidas prontas, seus recipientes, insumos
     * agrícolas e comidas de mercearia. Os índices históricos 0..20 e IDs
     * de estoque permanecem; os alimentos entram antes da fidelidade.
     * Bebidas em 0..7; vinho continua no índice 1. Drogas ficam no Ponto.
     */
    static List<Entry> gago(int tier) {
        List<Entry> menu = new ArrayList<>();
        menu.add(new Entry("cerveja", IntoxicantesMod.CERVEJA, 1, 15, 8, 0, false));
        menu.add(new Entry("vinho", IntoxicantesMod.VINHO, 1, 25, 8, 0, false));
        menu.add(new Entry("hidromel", IntoxicantesMod.HIDROMEL, 1, 20, 8, 0, false));
        menu.add(new Entry("cachaca", IntoxicantesMod.CACHACA, 1, 35, 8, 0, false));
        menu.add(new Entry("rum", IntoxicantesMod.RUM, 1, 40, 8, 0, false));
        menu.add(new Entry("suco_detox", IntoxicantesMod.SUCO_DETOX, 1, 12, 6, 0, false));
        menu.add(new Entry("agua_de_coco", IntoxicantesMod.AGUA_DE_COCO, 1, 8, 6, 0, false));
        menu.add(new Entry("cha_lupulo", IntoxicantesMod.CHA_LUPULO, 1, 10, 6, 0, false));
        // as GARRAFAS VAZIAS da v1.2.65: o recipiente de cada bebida
        menu.add(new Entry("cerveja_vazia", IntoxicantesMod.CERVEJA_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("vinho_vazia", IntoxicantesMod.VINHO_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("cachaca_vazia", IntoxicantesMod.CACHACA_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("hidromel_vazia", IntoxicantesMod.HIDROMEL_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("rum_vazia", IntoxicantesMod.RUM_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("suco_detox_vazia", IntoxicantesMod.SUCO_DETOX_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("agua_de_coco_vazia", IntoxicantesMod.AGUA_DE_COCO_VAZIA, 1, 3, 8, 0, false));
        menu.add(new Entry("cha_lupulo_vazia", IntoxicantesMod.CHA_LUPULO_VAZIA, 1, 3, 8, 0, false));
        // insumos de GROW DE BEBIDA (o pomar/orfanato do freguês)
        menu.add(new Entry("semente_loupulo", IntoxicantesMod.SEMENTE_LOUPULO, 4, 4, 8, 0, false));
        menu.add(new Entry("semente_uva", IntoxicantesMod.SEMENTE_UVA, 4, 4, 8, 0, false));
        menu.add(new Entry("semente_cevada", IntoxicantesMod.SEMENTE_CEVADA, 4, 4, 8, 0, false));
        // v1.2.17 (balance): R$ 36 e 2/dia — a lâmpada virou INVESTIMENTO de
        // growshop (era R$ 24 com 4/dia: dava pra montar fazenda no dia 1)
        menu.add(new Entry("lampada_uv", IntoxicantesMod.LAMPADA_UV.asItem(), 1, 36, 2, 0, false));
        menu.add(new Entry("glass_bottle", Items.GLASS_BOTTLE, 3, 5, 16, 0, false));
        menu.addAll(alimentos());
        // ---- EXCLUSIVOS DE FIDELIDADE (v1.2.66: tema BEBIDA, nada de arma)
        if (tier >= 1) menu.add(new Entry("cha_lupulo_fidelidade", IntoxicantesMod.CHA_LUPULO, 4, 30, 4, 1, false));
        if (tier >= 2) menu.add(new Entry("hidromel_fidelidade", IntoxicantesMod.HIDROMEL, 4, 120, 2, 2, false));
        if (tier >= 2) menu.add(new Entry("cerveja_fidelidade", IntoxicantesMod.CERVEJA, 6, 150, 2, 2, false));
        if (tier >= 3) menu.add(new Entry("rum_fidelidade", IntoxicantesMod.RUM, 4, 260, 1, 3, false));
        if (tier >= 3) menu.add(new Entry("garrafas_fidelidade", Items.GLASS_BOTTLE, 16, 200, 1, 3, false));
        return List.copyOf(menu);
    }

    /**
     * Quantidades são por compra; estoque conta compras disponíveis por dia.
     * Sopas, bolo e leite saem em unidades, respeitando suas pilhas máximas.
     * Ingredientes custam mais no varejo que a recompra em harvests().
     */
    private static List<Entry> alimentos() {
        return List.of(
                // Comidas do SNC e colheitas para a cozinha/produção de bebidas.
                new Entry("pao_cevada", IntoxicantesMod.PAO_CEVADA, 1, 6, 16, 0, false),
                new Entry("coco", IntoxicantesMod.COCO_FRUTO, 1, 4, 16, 0, false),
                new Entry("uva", IntoxicantesMod.UVA, 8, 10, 8, 0, false),
                new Entry("lupulo", IntoxicantesMod.LOUPULO_FRESCO, 8, 10, 8, 0, false),
                new Entry("cana_de_acucar", IntoxicantesMod.CANA_DE_ACUCAR, 8, 8, 8, 0, false),
                new Entry("cevada", IntoxicantesMod.CEVADA, 8, 9, 8, 0, false),
                new Entry("malte", IntoxicantesMod.MALTE, 8, 11, 6, 0, false),
                // Padaria, frutas e verduras comuns; nenhuma comida nociva.
                new Entry("bread", Items.BREAD, 1, 5, 16, 0, false),
                new Entry("apple", Items.APPLE, 4, 6, 8, 0, false),
                new Entry("melon_slice", Items.MELON_SLICE, 8, 4, 8, 0, false),
                new Entry("sweet_berries", Items.SWEET_BERRIES, 8, 5, 8, 0, false),
                new Entry("carrot", Items.CARROT, 8, 6, 8, 0, false),
                new Entry("potato", Items.POTATO, 8, 6, 8, 0, false),
                new Entry("beetroot", Items.BEETROOT, 8, 6, 8, 0, false),
                new Entry("baked_potato", Items.BAKED_POTATO, 2, 6, 8, 0, false),
                new Entry("dried_kelp", Items.DRIED_KELP, 8, 3, 8, 0, false),
                // Carnes e peixes prontos; estoque menor que o pão de todo dia.
                new Entry("cooked_beef", Items.COOKED_BEEF, 2, 16, 6, 0, false),
                new Entry("cooked_porkchop", Items.COOKED_PORKCHOP, 2, 14, 6, 0, false),
                new Entry("cooked_chicken", Items.COOKED_CHICKEN, 2, 10, 8, 0, false),
                new Entry("cooked_mutton", Items.COOKED_MUTTON, 2, 12, 6, 0, false),
                new Entry("cooked_rabbit", Items.COOKED_RABBIT, 2, 10, 6, 0, false),
                new Entry("cooked_cod", Items.COOKED_COD, 2, 8, 8, 0, false),
                new Entry("cooked_salmon", Items.COOKED_SALMON, 2, 10, 8, 0, false),
                // Cada sopa inclui sua tigela; bolo mantém a colocação vanilla.
                new Entry("mushroom_stew", Items.MUSHROOM_STEW, 1, 7, 6, 0, false),
                new Entry("beetroot_soup", Items.BEETROOT_SOUP, 1, 7, 6, 0, false),
                new Entry("rabbit_stew", Items.RABBIT_STEW, 1, 14, 4, 0, false),
                new Entry("cookie", Items.COOKIE, 8, 6, 8, 0, false),
                new Entry("pumpkin_pie", Items.PUMPKIN_PIE, 1, 8, 8, 0, false),
                new Entry("cake", Items.CAKE, 1, 20, 3, 0, false),
                // Não há farinha registrada: trigo, cevada e malte já são os grãos.
                new Entry("wheat", Items.WHEAT, 8, 8, 8, 0, false),
                new Entry("sugar", Items.SUGAR, 8, 5, 8, 0, false),
                new Entry("egg", Items.EGG, 4, 3, 8, 0, false),
                new Entry("pumpkin", Items.PUMPKIN, 2, 4, 6, 0, false),
                new Entry("red_mushroom", Items.RED_MUSHROOM, 8, 4, 6, 0, false),
                new Entry("brown_mushroom", Items.BROWN_MUSHROOM, 8, 4, 6, 0, false),
                new Entry("cocoa_beans", Items.COCOA_BEANS, 8, 5, 8, 0, false),
                // Consumo vanilla devolve vidro/balde; não há recipiente duplicado.
                new Entry("honey_bottle", Items.HONEY_BOTTLE, 1, 5, 8, 0, false),
                new Entry("milk_bucket", Items.MILK_BUCKET, 1, 18, 4, 0, false),
                // Cozinha completa: crus para preparar, frutas e ovos das variantes.
                // Frango cru e fruta do coro preservam seus efeitos vanilla.
                new Entry("glow_berries", Items.GLOW_BERRIES, 8, 6, 6, 0, false),
                new Entry("chorus_fruit", Items.CHORUS_FRUIT, 4, 12, 4, 0, false),
                new Entry("beef", Items.BEEF, 2, 10, 6, 0, false),
                new Entry("chicken", Items.CHICKEN, 2, 6, 8, 0, false),
                new Entry("porkchop", Items.PORKCHOP, 2, 9, 6, 0, false),
                new Entry("mutton", Items.MUTTON, 2, 8, 6, 0, false),
                new Entry("rabbit", Items.RABBIT, 2, 6, 6, 0, false),
                new Entry("cod", Items.COD, 2, 5, 8, 0, false),
                new Entry("salmon", Items.SALMON, 2, 6, 8, 0, false),
                new Entry("melon", Items.MELON, 1, 5, 6, 0, false),
                new Entry("blue_egg", Items.BLUE_EGG, 4, 3, 8, 0, false),
                new Entry("brown_egg", Items.BROWN_EGG, 4, 3, 8, 0, false));
    }

    /** Agenda de produtos expostos: mesma ordem e preços do varejo básico. */
    private static List<Entry> produtosPrateleira() {
        return gago(0).stream().filter(e -> !e.id().endsWith("_vazia")
                && !e.id().startsWith("semente_")
                && !e.id().equals("lampada_uv") && !e.id().equals("glass_bottle")).toList();
    }

    /** Quantas seções de nove expositores cobrem toda a agenda de comidas/bebidas. */
    static int secoesPrateleira() {
        return (produtosPrateleira().size() + 8) / 9;
    }

    /**
     * Cada seção mantém nove slots. A seção gira de forma determinística,
     * e o fim da última é preenchido com os primeiros alimentos da mercearia.
     * Entradas reutilizam os IDs/preços/doses/estoques do catálogo canônico.
     */
    static List<Entry> prateleira(int secao) {
        List<Entry> produtos = produtosPrateleira();
        int inicio = Math.floorMod(secao, (produtos.size() + 8) / 9) * 9;
        List<Entry> complemento = alimentos();
        List<Entry> agenda = new ArrayList<>(9);
        int preenchidos = 0;
        for (int slot = 0; slot < 9; slot++) {
            int indice = inicio + slot;
            agenda.add(indice < produtos.size() ? produtos.get(indice)
                    : complemento.get(preenchidos++ % complemento.size()));
        }
        return List.copyOf(agenda);
    }

    /**
     * v1.2.44 — PAUTA FIXA DO PONTO: o catalogo COMPLETO do traficante (7
     * produtos, preco de referencia do dia, estoque diario). A ordem E o
     * indice da tela (0..6) e do array EXCLUSIVOS abaixo.
     */
    static List<Entry> traficanteFixo() {
        return List.of(
                new Entry("maconha_seda", IntoxicantesMod.MACONHA_SEDA, 1, 12, 6, 0, false),
                new Entry("cocaina", IntoxicantesMod.COCAINA, 1, 42, 4, 0, false),
                new Entry("heroina", IntoxicantesMod.HEROINA, 1, 44, 4, 0, false),
                new Entry("lsd", IntoxicantesMod.LSD, 1, 48, 4, 0, false),
                new Entry("baseado", IntoxicantesMod.BASEADO, 1, 34, 6, 0, false),
                new Entry("opio", IntoxicantesMod.OPIO, 1, 14, 6, 0, false),
                new Entry("extrato_cafeina", IntoxicantesMod.EXTRATO_CAFEINA, 1, 20, 6, 0, false));
    }

    /** Indices (do traficanteFixo) que podem virar o LANCAMENTO DO DIA. */
    static final int[] EXCLUSIVOS = {0, 1, 2, 3, 4, 5};

    /**
     * v1.2.66 — A COMPRA DO TRAFICANTE: cru de DROGA que ele recolhe na porta
     * do ponto (mesmo papel que as colheitas fazem pro Gago). Paga ~90% do
     * preço do dia; a folha da maconha (a matéria-prima do baseado) entra
     * junto. Índices 0..2 — é a aba 2 da tela do Ponto.
     */
    static List<Entry> cruDrogas() {
        return List.of(
                new Entry("cru_maconha_seda", IntoxicantesMod.MACONHA_SEDA, 8, 10, 12, 0, true),
                new Entry("cru_opio", IntoxicantesMod.OPIO, 8, 12, 12, 0, true),
                new Entry("cru_cocaina", IntoxicantesMod.COCAINA, 4, 32, 12, 0, true));
    }

    static List<Entry> harvests() {
        return List.of(
                new Entry("colheita_lupulo", IntoxicantesMod.LOUPULO_FRESCO, 8, 6, 12, 0, true),
                new Entry("colheita_uva", IntoxicantesMod.UVA, 8, 6, 12, 0, true),
                new Entry("ingrediente_trigo", Items.WHEAT, 8, 4, 12, 0, true),
                new Entry("ingrediente_cana", IntoxicantesMod.CANA_DE_ACUCAR, 8, 4, 12, 0, true),
                new Entry("ingrediente_mel", Items.HONEY_BOTTLE, 4, 8, 12, 0, true),
                new Entry("ingrediente_garrafas", Items.GLASS_BOTTLE, 8, 4, 12, 0, true));
    }

    static List<Entry> all() {
        List<Entry> all = new ArrayList<>(gago(3));
        all.addAll(harvests());
        all.addAll(cruDrogas());
        return List.copyOf(all);
    }

    /** Preço FIXO de varejo no PONTO do traficante (Integer.MAX_VALUE se ele não vende). */
    private static int precoTraficante(Item item) {
        for (Entry e : traficanteFixo()) {
            if (e.item() == item) {
                return e.price();
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Cotacao da RUA: oscila por DIA (seed = dia comercial), variando -30%..+40%
     * em volta da referencia. A mesma oferta custa o mesmo o dia inteiro (toda a
     * "rede" precifica junto, viu kkkk), muda a cada reposicao das 07h.
     * v1.2.66: teto = preço do PONTO do traficante (o concorrente da rua é ele,
     * não o Gago — o balcão do Esquinão não vende droga).
     */
    static MerchantOffers traficante(RandomSource random, long seedDia) {
        List<StreetOffer> pool = new ArrayList<>(List.of(
                new StreetOffer(IntoxicantesMod.BASEADO, 34),
                new StreetOffer(IntoxicantesMod.COCAINA, 42),
                new StreetOffer(IntoxicantesMod.HEROINA, 44),
                new StreetOffer(IntoxicantesMod.LSD, 48),
                new StreetOffer(IntoxicantesMod.MACONHA_SEDA, 12),
                new StreetOffer(IntoxicantesMod.OPIO, 14),
                new StreetOffer(IntoxicantesMod.EXTRATO_CAFEINA, 20)));
        MerchantOffers offers = new MerchantOffers();
        for (int i = 0; i < 3; i++) {
            StreetOffer chosen = pool.remove(random.nextInt(pool.size()));
            // a rua oscila por dia, mas NUNCA passa do preco fixo do Esquinao:
            // a escolha do fregues e "rua barata e escassa" vs "Gago caro e
            // abastecido" — nunca " rua mais cara por azar do dado"
            int teto = precoTraficante(chosen.item()) - 1;
            int preco = Math.min(precoDoDia(seedDia, chosen.item().getDescriptionId(), chosen.price()), teto);
            sell(offers, chosen.item(), 1, Math.max(1, preco), 4);
        }
        return offers;
    }



    /**
     * Preco flutuante do dia pra um produto da rua (mesma seed pra qualquer NPC).
     * Cache por dia comercial: o refresh de 1s dos NPCs le isso toda hora — o
     * java.util.Random so roda UMA vez por produto/dia (pauta fixa do dia kkkk).
     */
    private static long pautaDia = Long.MIN_VALUE;
    private static final java.util.Map<String, Float> FATORES_DIA = new java.util.HashMap<>();

    static int precoDoDia(long seedDia, String produto, int base) {
        if (seedDia != pautaDia) {
            pautaDia = seedDia;
            FATORES_DIA.clear();
        }
        Float fator = FATORES_DIA.get(produto);
        if (fator == null) {
            fator = ModConfig.get().fatorCotacao(seedDia, produto);
            FATORES_DIA.put(produto, fator);
        }
        return Math.max(1, Math.round(base * fator));
    }



    private static void sell(MerchantOffers offers, Item item, int count, int price, int stock) {
        offers.add(new MerchantOffer(new ItemCost(IntoxicantesMod.REAL, price),
                new ItemStack(item, count), stock, 0, 0.0F));
    }

    private record StreetOffer(Item item, int price) {}
}
