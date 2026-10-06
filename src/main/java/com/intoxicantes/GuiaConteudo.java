package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * O CONTEÚDO DO GUIA DO SNC ADVENTURES — o registro central das categorias,
 * entradas e páginas, no mesmo espírito do ProcessosBebida: montado UMA vez,
 * lido pela tela (client). Regra 32 do AGENTS.md: estrutura expansível —
 * feature nova adiciona UMA entrada aqui, sem reescrever a GUI.
 *
 * DATA-DRIVEN (regra 18): as cadeias de bebida/máquina buscam quantidades e
 * tempos DIRETO de ProcessosBebida + ModConfig na montagem. As receitas são
 * descritas por ITENS (ReceitaGrid) e o GuiaGameTest valida cada grade contra
 * o JSON real de data/intoxicantes/recipe/ — se alguém editar a receita e
 * esquecer o guia, o TESTE quebra (o contrário de valores em dois lugares).
 *
 * Textos: 100% lang (regra 29) — chaves guia.intoxicantes.*.
 */
public final class GuiaConteudo {

    private GuiaConteudo() {}

    // ==================================================== PÁGINAS

    /** Parágrafo de texto corrido (chave de lang). */
    public record PagTexto(String chave) {}

    /** Título de seção dentro da entrada (chave de lang). */
    public record PagTitulo(String chave) {}

    /** Dica em destaque (carimbo de tinta). */
    public record PagDica(String chave) {}

    /** Linha de ficha: ícone + texto (chave de lang). */
    public record PagItem(Supplier<ItemStack> icone, String chave) {}

    /** Linha de ficha com valores AO VIVO (tempos do config/formatadores). */
    public record PagItemDinamica(Supplier<ItemStack> icone, String chave,
            java.util.function.Supplier<String> valor1,
            java.util.function.Supplier<String> valor2) {}

    /** Linha de tabela (texto puro — efeitos, preços). */
    public record PagLinha(String chave) {}

    /**
     * CADEIA de processo: passos com ícone + quantidade + tempo opcional.
     * Os stacks são SUPPLIERS porque a montagem acontece no client já depois
     * do registro dos itens — cada abertura resolve do vivo.
     */
    public record PagCadeia(List<Passo> passos) {
        public record Passo(Supplier<ItemStack> icone, String tempo, boolean setaAntes,
                String chaveLegenda) {}
    }

    /**
     * Receita 3x3 descrita por ITENS (a receita shapeless de 3 insumos entra
     * como lista simples). O GuiaGameTest compara com o JSON real.
     */
    public record ReceitaGrid(List<Ingrediente> ingredientes, ItemStack resultado,
            boolean shaped) {
        /** Ingrediente: item + quantidade (a grade mostra o contador). */
        public record Ingrediente(Item item, int qtd) {}
    }

    // ==================================================== ENTRADA / CATEGORIA

    /** Uma entrada do guia: id de lang, ícone e páginas. */
    public static final class GuiaEntrada {
        public final String id;
        public final Supplier<ItemStack> icone;
        public final List<Object> paginas = new ArrayList<>();

        public GuiaEntrada(String id, Supplier<ItemStack> icone) {
            this.id = id;
            this.icone = icone;
        }

        public GuiaEntrada add(Object pagina) {
            paginas.add(pagina);
            return this;
        }

        public Component titulo() {
            return Component.translatable("guia.intoxicantes." + id + ".titulo");
        }
    }

    /** Categoria do sumário. */
    public static final class GuiaCategoria {
        public final String id;
        public final Supplier<ItemStack> icone;
        public final List<GuiaEntrada> entradas = new ArrayList<>();

        public GuiaCategoria(String id, Supplier<ItemStack> icone) {
            this.id = id;
            this.icone = icone;
        }

        public GuiaEntrada entrada(String idEntrada, Supplier<ItemStack> icone) {
            GuiaEntrada e = new GuiaEntrada(id + "." + idEntrada, icone);
            entradas.add(e);
            return e;
        }

        public Component titulo() {
            return Component.translatable("guia.intoxicantes.cat." + id);
        }

        public Component subtitulo() {
            return Component.translatable("guia.intoxicantes.cat." + id + ".sub");
        }
    }

    // ==================================================== O REGISTRO

    private static final List<GuiaCategoria> CATEGORIAS = new ArrayList<>();
    private static boolean montado;

    /** Monta todo o guia uma vez (primeira abertura da tela). */
    public static synchronized List<GuiaCategoria> categorias() {
        if (!montado) {
            montar();
            montado = true;
        }
        return CATEGORIAS;
    }

    /** Para o gametest: varre receitas declaradas pelo guia. */
    public record ReceitaDeclarada(String id, ReceitaGrid grade) {}

    private static final List<ReceitaDeclarada> RECEITAS_DECLARADAS = new ArrayList<>();

    public static List<ReceitaDeclarada> receitasDeclaradas() {
        categorias();
        return RECEITAS_DECLARADAS;
    }

    private static ItemStack st(Item item) {
        return new ItemStack(item);
    }

    private static ItemStack st(Item item, int qtd) {
        return new ItemStack(item, qtd);
    }

    private static Supplier<ItemStack> ic(Item item) {
        return () -> st(item);
    }

    private static Supplier<ItemStack> ic(Item item, int qtd) {
        return () -> st(item, qtd);
    }

    private static GuiaCategoria cat(String id, Item icone) {
        GuiaCategoria c = new GuiaCategoria(id, ic(icone));
        CATEGORIAS.add(c);
        return c;
    }

    /** Tempo legível: segundos base × config (fonte única: ModConfig). */
    public static String tempo(int segundosBase) {
        int seg = Math.max(1, Math.round(segundosBase * ModConfig.get().velocidadeEfetiva()));
        if (seg < 60) {
            return seg + "s";
        }
        int min = seg / 60;
        int resto = seg % 60;
        return resto == 0 ? min + "min" : min + "min" + resto + "s";
    }

    /** Passo de item (com seta vinda do passo anterior por padrão). */
    private static PagCadeia.Passo passo(Item item, int qtd, boolean seta) {
        return new PagCadeia.Passo(ic(item, qtd), null, seta, null);
    }

    /** Passo de máquina com tempo. */
    private static PagCadeia.Passo passoMaquina(Item maquina, String tempo) {
        return new PagCadeia.Passo(ic(maquina), tempo, true, null);
    }

    /** Passo de máquina "fase" (legenda própria, sem tempo — ex. fornalha). */
    private static PagCadeia.Passo passoFase(Item icone, String chaveLegenda) {
        return new PagCadeia.Passo(ic(icone), null, true, chaveLegenda);
    }

    /** Registra a grade e devolve a página (o gametest valida depois). */
    private static ReceitaGrid receita(String id, ItemStack resultado,
            ReceitaGrid.Ingrediente... ingredientes) {
        ReceitaGrid g = new ReceitaGrid(List.of(ingredientes), resultado, false);
        RECEITAS_DECLARADAS.add(new ReceitaDeclarada(id, g));
        return g;
    }

    private static ReceitaGrid.Ingrediente ing(Item item, int qtd) {
        return new ReceitaGrid.Ingrediente(item, qtd);
    }

    // ==================================================== TEMPOS (espelham as BEs;
    //       GuiaGameTest compara com MaquinaPrimaBlockEntity/ProcessosBebida)
    /** Mostura do caldeirão (1ª dose) — MaquinaPrimaBlockEntity usa 40F. */
    public static final int SEG_MOSTURA = 40;
    /** Fervura com lúpulo (2ª dose) — MaquinaPrimaBlockEntity usa 30F. */
    public static final int SEG_FERVURA = 30;

    // ==================================================== MONTAGEM

    private static void montar() {
        ProcessosBebida.registrar();

        // ================================================== 1. COMEÇANDO
        GuiaCategoria comeco = cat("comecando", Items.BOOK);
        comeco.entrada("sobre", ic(Items.WRITABLE_BOOK))
                .add(new PagTitulo("comecando.sobre.boas_vindas"))
                .add(new PagTexto("comecando.sobre.1"))
                .add(new PagTexto("comecando.sobre.2"))
                .add(new PagTitulo("comecando.sobre.o_mundo"))
                .add(new PagItem(ic(IntoxicantesMod.OVO_GAGO), "comecando.sobre.gago"))
                .add(new PagItem(ic(IntoxicantesMod.OVO_TRAFICANTE), "comecando.sobre.traficante"))
                .add(new PagItem(ic(IntoxicantesMod.OVO_JUCA), "comecando.sobre.juca"))
                .add(new PagTitulo("comecando.sobre.regra_de_ouro"))
                .add(new PagLinha("comecando.souro.1"))
                .add(new PagLinha("comecando.souro.2"))
                .add(new PagLinha("comecando.souro.3"))
                .add(new PagDica("comecando.sobre.esquina"));

        comeco.entrada("primeiros_passos", ic(IntoxicantesMod.REAL))
                .add(new PagTitulo("comecando.primeiros.titulo"))
                .add(new PagTexto("comecando.primeiros.1"))
                .add(receita("emerald_to_real", st(IntoxicantesMod.MOEDA_1, 9),
                        ing(Items.EMERALD_BLOCK, 1)))
                .add(new PagTexto("comecando.primeiros.2"))
                .add(new PagDica("comecando.primeiros.dica"));

        // ================================================== 2. BEBIDAS
        GuiaCategoria bebidas = cat("bebidas", IntoxicantesMod.CERVEJA);

        bebidas.entrada("garrafas", ic(IntoxicantesMod.VINHO))
                .add(new PagTitulo("bebidas.garrafas.hero"))
                .add(new PagTexto("bebidas.garrafas.colocar"))
                .add(new PagTexto("bebidas.garrafas.recolher"))
                .add(new PagTexto("bebidas.garrafas.consumir"))
                .add(new PagDica("bebidas.garrafas.apoio"));

        // ---- VINHO: uva → prensa → barril (fermenta+matura) → garrafas
        ProcessosBebida.Barril bVinho = ProcessosBebida.barrilDe("vinho").orElseThrow();
        ProcessosBebida.Prima pPrensa = ProcessosBebida.prensaDe(IntoxicantesMod.UVA).orElseThrow();
        bebidas.entrada("vinho", ic(IntoxicantesMod.VINHO))
                .add(new PagTitulo("bebidas.vinho.hero"))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.UVA, pPrensa.qtdIn(), false),
                        passoMaquina(IntoxicantesMod.PRENSA_UVAS.asItem(), tempo(SEG_MOSTURA)),
                        passo(IntoxicantesMod.MOSTO_DE_UVA, pPrensa.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.BARRIL_VINHO.asItem(),
                                tempo(bVinho.tempoFermentacaoSeg()) + " + "
                                        + tempo(bVinho.tempoMaturacaoSeg())),
                        passoFase(Items.GLASS_BOTTLE, "cadeia.engarrafar"),
                        passo(IntoxicantesMod.VINHO,
                                ProcessosBebida.garrafasPorLote(bVinho), true))))
                .add(new PagTitulo("bebidas.vinho.copo"))
                .add(new PagItem(ic(IntoxicantesMod.VINHO), "bebidas.vinho.efeitos"))
                .add(new PagTexto("bebidas.vinho.garrafa"))
                .add(new PagTitulo("bebidas.vinho.uvas"))
                .add(new PagItem(ic(IntoxicantesMod.SEMENTE_UVA), "bebidas.vinho.uvas1"))
                .add(new PagItem(ic(IntoxicantesMod.LAMPADA_UV.asItem()), "bebidas.vinho.uvas2"))
                .add(new PagTitulo("bebidas.vinho.barril_titulo"))
                .add(new PagItem(ic(IntoxicantesMod.BARRIL_VINHO.asItem()), "bebidas.vinho.barril"))
                .add(new PagTitulo("bebidas.vinho.dois_estagios_titulo"))
                .add(new PagItem(ic(IntoxicantesMod.BARRIL_VINHO.asItem()), "bebidas.vinho.dois_estagios"))
                .add(new PagTitulo("bebidas.vinho.total_titulo"))
                // v1.2.53: total COMPUTADO (regra do AGENTS.md)
                .add(new PagItemDinamica(ic(IntoxicantesMod.VINHO), "bebidas.vinho.total",
                        () -> tempo(SEG_MOSTURA + bVinho.tempoFermentacaoSeg()
                                + bVinho.tempoMaturacaoSeg()),
                        () -> tempo(bVinho.tempoFermentacaoSeg())))
                .add(new PagTitulo("bebidas.vinho.adega_titulo"))
                .add(new PagItem(ic(IntoxicantesMod.PRENSA_UVAS.asItem()), "bebidas.vinho.adega"));

        // ---- CACHAÇA: cana → moenda → dorna → alambique → barril
        ProcessosBebida.Barril bCachaca = ProcessosBebida.barrilDe("cachaca").orElseThrow();
        ProcessosBebida.Prima pMoenda = ProcessosBebida.moendaDe(IntoxicantesMod.CANA_DE_ACUCAR).orElseThrow();
        ProcessosBebida.Dorna dCana = ProcessosBebida.dornaDe(IntoxicantesMod.CALDO_DE_CANA).orElseThrow();
        ProcessosBebida.Alambique aCana = ProcessosBebida.alambiqueDe(IntoxicantesMod.MOSTO_CANA_FERMENTADO).orElseThrow();
        bebidas.entrada("cachaca", ic(IntoxicantesMod.CACHACA))
                .add(new PagTitulo("bebidas.cachaca.hero"))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.CANA_DE_ACUCAR, pMoenda.qtdIn(), false),
                        passoMaquina(IntoxicantesMod.MOENDA_CANA.asItem(), tempo(SEG_MOSTURA)),
                        passo(IntoxicantesMod.CALDO_DE_CANA, pMoenda.qtdOut(), true),
                        passoFase(IntoxicantesMod.BAGACO_DE_CANA, "cadeia.bagaco"),
                        passoMaquina(IntoxicantesMod.DORNA_BEBIDA.asItem(), tempo(ProcessosBebida.SEG_DORNA_BASE)),
                        passo(IntoxicantesMod.MOSTO_CANA_FERMENTADO, dCana.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.ALAMBIQUE.asItem(), tempo(ProcessosBebida.SEG_ALAMBIQUE_BASE)),
                        passo(IntoxicantesMod.CACHACA_JOVEM, aCana.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.BARRIL_CACHACA.asItem(), tempo(bCachaca.tempoMaturacaoSeg())),
                        passo(IntoxicantesMod.CACHACA,
                                ProcessosBebida.garrafasPorLote(bCachaca), true))))
                .add(new PagTitulo("bebidas.cachaca.fogo_titulo"))
                .add(new PagItem(ic(IntoxicantesMod.ALAMBIQUE.asItem()), "bebidas.cachaca.fogo"))
                .add(new PagTitulo("bebidas.cachaca.total_titulo"))
                // v1.2.53: tempo total do ciclo COMPUTADO das constantes (regra do
                // AGENTS.md: número no guia que existe no código é proibido)
                .add(new PagItemDinamica(ic(IntoxicantesMod.CACHACA), "bebidas.cachaca.total",
                        () -> tempo(SEG_MOSTURA + ProcessosBebida.SEG_DORNA_BASE
                                + ProcessosBebida.SEG_ALAMBIQUE_BASE
                                + bCachaca.tempoMaturacaoSeg()),
                        () -> tempo(bCachaca.tempoMaturacaoSeg())))
                .add(new PagTitulo("bebidas.cachaca.copo"))
                .add(new PagItem(ic(IntoxicantesMod.CACHACA), "bebidas.cachaca.efeitos"))
                .add(new PagTitulo("bebidas.cachaca.dica_titulo"))
                .add(new PagDica("bebidas.cachaca.dica"));

        // ---- RUM: cana → esmagadora → caldo → fornalha (melaço) → dorna → alambique → barril
        // v1.2.53 CORREÇÃO: a cadeia antiga mostrava cana indo pra fornalha
        // direto — mas smelting_melaco.json recebe CALDO (a cana precisa da
        // esmagadora antes). A spec do usuário confirmou o fluxo real.
        ProcessosBebida.Barril bRum = ProcessosBebida.barrilDe("rum").orElseThrow();
        ProcessosBebida.Dorna dRum = ProcessosBebida.dornaDe(IntoxicantesMod.MELACO).orElseThrow();
        ProcessosBebida.Alambique aRum = ProcessosBebida.alambiqueDe(IntoxicantesMod.MOSTO_RUM_FERMENTADO).orElseThrow();
        bebidas.entrada("rum", ic(IntoxicantesMod.RUM))
                .add(new PagTitulo("bebidas.rum.hero"))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.CANA_DE_ACUCAR, pMoenda.qtdIn(), false),
                        passoMaquina(IntoxicantesMod.MOENDA_CANA.asItem(), tempo(SEG_MOSTURA)),
                        passo(IntoxicantesMod.CALDO_DE_CANA, pMoenda.qtdOut(), true),
                        passoFase(Items.FURNACE, "cadeia.fornalha"),
                        passo(IntoxicantesMod.MELACO, 1, true),
                        passoMaquina(IntoxicantesMod.DORNA_BEBIDA.asItem(), tempo(ProcessosBebida.SEG_DORNA_BASE)),
                        passo(IntoxicantesMod.MOSTO_RUM_FERMENTADO, dRum.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.ALAMBIQUE.asItem(), tempo(ProcessosBebida.SEG_ALAMBIQUE_BASE)),
                        passo(IntoxicantesMod.RUM_JOVEM, aRum.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.BARRIL_RUM.asItem(), tempo(bRum.tempoMaturacaoSeg())),
                        passo(IntoxicantesMod.RUM,
                                ProcessosBebida.garrafasPorLote(bRum), true))))
                .add(new PagTitulo("bebidas.rum.mesmo_alambique_titulo"))
                .add(new PagItem(ic(IntoxicantesMod.ALAMBIQUE.asItem()), "bebidas.rum.mesmo_alambique"))
                .add(new PagTitulo("bebidas.rum.total_titulo"))
                // v1.2.53: total COMPUTADO (regra do AGENTS.md). O rum é o caminho
                // mais longo: esmagar + cozinhar + fermentar + destilar + maturar.
                .add(new PagItemDinamica(ic(IntoxicantesMod.RUM), "bebidas.rum.total",
                        () -> tempo(SEG_MOSTURA + ProcessosBebida.SEG_DORNA_BASE
                                + ProcessosBebida.SEG_ALAMBIQUE_BASE
                                + bRum.tempoMaturacaoSeg()),
                        () -> tempo(bRum.tempoMaturacaoSeg())))
                .add(new PagTitulo("bebidas.rum.copo"))
                .add(new PagItem(ic(IntoxicantesMod.RUM), "bebidas.rum.efeitos"));

        // ---- CERVEJA: cevada → malte (forna) → caldeirão 2 doses → barril
        ProcessosBebida.Barril bCerveja = ProcessosBebida.barrilDe("cerveja").orElseThrow();
        ProcessosBebida.Prima pCaldeirao = ProcessosBebida.caldeiraoDe(IntoxicantesMod.MALTE).orElseThrow();
        bebidas.entrada("cerveja", ic(IntoxicantesMod.CERVEJA))
                .add(new PagTitulo("bebidas.cerveja.hero"))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.CEVADA, 1, false),
                        passoFase(Items.FURNACE, "cadeia.torrefacao"),
                        passo(IntoxicantesMod.MALTE, 1, true),
                        passoMaquina(IntoxicantesMod.CALDEIRAO_MOSTURA.asItem(),
                                tempo(SEG_MOSTURA) + " + " + tempo(SEG_FERVURA)),
                        passo(IntoxicantesMod.MOSTO_CERVEJA_LUPULADO, pCaldeirao.qtdOut(), true),
                        passoMaquina(IntoxicantesMod.BARRIL_CERVEJA.asItem(),
                                tempo(bCerveja.tempoFermentacaoSeg()) + " + "
                                        + tempo(bCerveja.tempoMaturacaoSeg())),
                        passoFase(Items.GLASS_BOTTLE, "cadeia.engarrafar"),
                        passo(IntoxicantesMod.CERVEJA,
                                ProcessosBebida.garrafasPorLote(bCerveja), true))))
                .add(new PagTitulo("bebidas.cerveja.duas_doses"))
                .add(new PagItem(ic(IntoxicantesMod.MALTE, pCaldeirao.qtdIn()), "bebidas.cerveja.dose1"))
                .add(new PagItem(ic(IntoxicantesMod.LOUPULO_FRESCO, pCaldeirao.secQtd()), "bebidas.cerveja.dose2"))
                .add(new PagTitulo("bebidas.cerveja.agua_titulo"))
                .add(new PagItem(ic(Items.WATER_BUCKET), "bebidas.cerveja.agua"))
                .add(new PagTitulo("bebidas.cerveja.total_titulo"))
                // v1.2.53: total COMPUTADO das constantes (regra do AGENTS.md)
                .add(new PagItemDinamica(ic(IntoxicantesMod.CERVEJA), "bebidas.cerveja.total",
                        () -> tempo(SEG_MOSTURA + SEG_FERVURA
                                + bCerveja.tempoFermentacaoSeg()
                                + bCerveja.tempoMaturacaoSeg()),
                        () -> tempo(bCerveja.tempoFermentacaoSeg())))
                .add(new PagTitulo("bebidas.cerveja.copo"))
                .add(new PagItem(ic(IntoxicantesMod.CERVEJA), "bebidas.cerveja.efeitos"));

        // ---- HIDROMEL: crafting direto
        bebidas.entrada("hidromel", ic(IntoxicantesMod.HIDROMEL))
                .add(new PagTitulo("bebidas.hidromel.hero"))
                .add(new PagTexto("bebidas.hidromel.1"))
                .add(new PagItem(ic(Items.HONEY_BOTTLE), "bebidas.hidromel.receita"))
                .add(new PagItem(ic(Items.GLASS_BOTTLE), "bebidas.hidromel.receita2"))
                .add(new PagTitulo("bebidas.hidromel.copo"))
                .add(new PagItem(ic(IntoxicantesMod.HIDROMEL), "bebidas.hidromel.efeitos"));

        // ================================================== 3. CULTIVOS
        GuiaCategoria cultivos = cat("cultivos", IntoxicantesMod.UVA);
        parreira(cultivos);
        cultivo(cultivos, "lupulo", IntoxicantesMod.SEMENTE_LOUPULO, IntoxicantesMod.LOUPULO_FRESCO);
        cultivo(cultivos, "cafe", IntoxicantesMod.SEMENTE_CAFE, IntoxicantesMod.CAFE_VERDE);
        cultivo(cultivos, "maconha", IntoxicantesMod.SEMENTE_MACONHA, IntoxicantesMod.MACONHA_SEDA);
        cultivo(cultivos, "papoula", IntoxicantesMod.SEMENTE_PAPOULA, IntoxicantesMod.OPIO);
        cultivo(cultivos, "cevada", IntoxicantesMod.SEMENTE_CEVADA, IntoxicantesMod.CEVADA);

        cultivos.entrada("uv", ic(IntoxicantesMod.LAMPADA_UV.asItem()))
                .add(new PagTitulo("cultivos.uv.hero"))
                .add(new PagTexto("cultivos.uv.1"))
                .add(receita("lampada_uv", st(IntoxicantesMod.LAMPADA_UV.asItem()),
                        ing(Items.IRON_INGOT, 2), ing(Items.STAINED_GLASS.white(), 1),
                        ing(Items.GLASS, 5), ing(Items.GLOWSTONE_DUST, 1)))
                .add(new PagTexto("cultivos.uv.2"))
                .add(new PagDica("cultivos.uv.dica"));

        // ================================================== 4. FERMENTAÇÃO / DESTILAÇÃO / BARRIS
        GuiaCategoria fermento = cat("fermentacao", IntoxicantesMod.DORNA_BEBIDA.asItem());
        fermento.entrada("dorna", ic(IntoxicantesMod.DORNA_BEBIDA.asItem()))
                .add(new PagTitulo("fermentacao.dorna.hero"))
                .add(new PagTexto("fermentacao.dorna.1"))
                .add(receita("dorna_bebida", st(IntoxicantesMod.DORNA_BEBIDA.asItem()),
                        ing(Items.STRIPPED_SPRUCE_LOG, 7), ing(Items.WATER_BUCKET, 1)))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.CALDO_DE_CANA, dCana.qtdIn(), false),
                        passoMaquina(IntoxicantesMod.DORNA_BEBIDA.asItem(), tempo(ProcessosBebida.SEG_DORNA_BASE)),
                        passo(IntoxicantesMod.MOSTO_CANA_FERMENTADO, dCana.qtdOut(), true))))
                .add(new PagTexto("fermentacao.dorna.2"));

        fermento.entrada("alambique", ic(IntoxicantesMod.ALAMBIQUE.asItem()))
                .add(new PagTitulo("fermentacao.alambique.hero"))
                .add(new PagTexto("fermentacao.alambique.1"))
                .add(receita("alambique", st(IntoxicantesMod.ALAMBIQUE.asItem()),
                        ing(Items.COPPER_INGOT, 4), ing(Items.GLASS_BOTTLE, 1),
                        ing(Items.BARREL, 1), ing(Items.FURNACE, 1)))
                .add(new PagCadeia(List.of(
                        passo(IntoxicantesMod.MOSTO_CANA_FERMENTADO, aCana.qtdIn(), false),
                        passoMaquina(IntoxicantesMod.ALAMBIQUE.asItem(), tempo(ProcessosBebida.SEG_ALAMBIQUE_BASE)),
                        passo(IntoxicantesMod.CACHACA_JOVEM, aCana.qtdOut(), true))))
                .add(new PagTexto("fermentacao.alambique.2"));

        GuiaEntrada barris = fermento.entrada("barris", ic(IntoxicantesMod.BARRIL_VINHO.asItem()));
        barris.add(new PagTitulo("fermentacao.barris.hero"))
                .add(new PagTexto("fermentacao.barris.1"))
                .add(new PagTitulo("fermentacao.barris.fases"))
                .add(new PagLinha("fermentacao.barris.fase1"))
                .add(new PagLinha("fermentacao.barris.fase2"))
                .add(new PagTitulo("fermentacao.barris.engarrafar"))
                .add(new PagTexto("fermentacao.barris.2"))
                .add(new PagItem(ic(Items.GLASS_BOTTLE), "fermentacao.barris.garrafa"))
                .add(new PagTitulo("fermentacao.barris.lista"));
        for (ProcessosBebida.Barril b : ProcessosBebida.barris()) {
            Item blocoBarril = switch (b.bebida()) {
                case "cachaca" -> IntoxicantesMod.BARRIL_CACHACA.asItem();
                case "cerveja" -> IntoxicantesMod.BARRIL_CERVEJA.asItem();
                case "rum" -> IntoxicantesMod.BARRIL_RUM.asItem();
                default -> IntoxicantesMod.BARRIL_VINHO.asItem();
            };
            barris.add(new PagItemDinamica(ic(blocoBarril), "fermentacao.barris.linha",
                    () -> tempo(b.tempoFermentacaoSeg()),
                    () -> tempo(b.tempoMaturacaoSeg())));
        }

        // ================================================== 5. MÁQUINAS
        GuiaCategoria maquinas = cat("maquinas", IntoxicantesMod.MOENDA_CANA.asItem());
        maquina(maquinas, "moenda", IntoxicantesMod.MOENDA_CANA.asItem(), "moenda_cana", pMoenda,
                ing(Items.STRIPPED_SPRUCE_LOG, 7), ing(Items.IRON_INGOT, 1), ing(Items.SPRUCE_SLAB, 1));
        maquina(maquinas, "prensa", IntoxicantesMod.PRENSA_UVAS.asItem(), "prensa_uvas", pPrensa,
                ing(Items.STRIPPED_BIRCH_LOG, 7), ing(Items.IRON_INGOT, 1), ing(Items.BIRCH_SLAB, 1));
        maquina(maquinas, "caldeirao", IntoxicantesMod.CALDEIRAO_MOSTURA.asItem(), "caldeirao_mostura",
                pCaldeirao, ing(Items.IRON_INGOT, 5), ing(Items.SMOOTH_STONE_SLAB, 3));

        // v1.2.61: a NOVA INTERAÇÃO das máquinas — GUI, shift+item, shift+mão vazia
        maquinas.entrada("interacao", ic(IntoxicantesMod.PRENSA_UVAS.asItem()))
                .add(new PagTitulo("maquinas.interacao.hero"))
                .add(new PagTexto("maquinas.interacao.1"))
                .add(new PagItem(ic(IntoxicantesMod.CALDEIRAO_MOSTURA.asItem()),
                        "maquinas.interacao.gui"))
                .add(new PagItem(ic(IntoxicantesMod.UVA), "maquinas.interacao.inserir"))
                .add(new PagTexto("maquinas.interacao.recolher"))
                .add(new PagDica("maquinas.interacao.dica"));

        // ================================================== 6. ARMAS
        GuiaCategoria armas = cat("armas", IntoxicantesMod.ESCOPETA);
        armas.entrada("escopeta", ic(IntoxicantesMod.ESCOPETA))
                .add(new PagTitulo("armas.escopeta.hero"))
                .add(new PagTexto("armas.escopeta.1"))
                .add(receita("escopeta", st(IntoxicantesMod.ESCOPETA),
                        ing(Items.IRON_INGOT, 2), ing(Items.IRON_TRAPDOOR, 1),
                        ing(Items.STICK, 2), ing(Items.LEATHER, 1)))
                .add(new PagItem(ic(IntoxicantesMod.CARTUCHO), "armas.escopeta.municao"))
                .add(new PagItem(ic(IntoxicantesMod.CARTUCHO), "armas.escopeta.reparo"))
                .add(new PagDica("armas.escopeta.dica"));
        armas.entrada("revolver", ic(IntoxicantesMod.REVOLVER))
                .add(new PagTitulo("armas.revolver.hero"))
                .add(new PagTexto("armas.revolver.1"))
                .add(receita("revolver", st(IntoxicantesMod.REVOLVER),
                        ing(Items.IRON_INGOT, 2), ing(Items.IRON_NUGGET, 2),
                        ing(Items.LEATHER, 2)))
                .add(new PagItem(ic(IntoxicantesMod.CARTUCHO_38), "armas.revolver.municao"))
                .add(new PagItem(ic(IntoxicantesMod.CARTUCHO_38), "armas.revolver.reparo"))
                .add(new PagDica("armas.revolver.dica"));

        // ================================================== 7. MUNIÇÕES
        GuiaCategoria municoes = cat("municoes", IntoxicantesMod.CARTUCHO);
        municoes.entrada("cartucho", ic(IntoxicantesMod.CARTUCHO))
                .add(new PagTitulo("municoes.cartucho.hero"))
                .add(receita("cartucho", st(IntoxicantesMod.CARTUCHO, 4),
                        ing(Items.GUNPOWDER, 1), ing(Items.PAPER, 1), ing(Items.IRON_NUGGET, 1)))
                .add(new PagItem(ic(IntoxicantesMod.ESCOPETA), "municoes.cartucho.uso"))
                .add(new PagItem(ic(Items.SKELETON_SKULL), "municoes.cartucho.loot"));
        municoes.entrada("cartucho_38", ic(IntoxicantesMod.CARTUCHO_38))
                .add(new PagTitulo("municoes.cartucho38.hero"))
                .add(receita("cartucho_38", st(IntoxicantesMod.CARTUCHO_38, 4),
                        ing(Items.GUNPOWDER, 1), ing(Items.IRON_NUGGET, 2)))
                .add(new PagItem(ic(IntoxicantesMod.REVOLVER), "municoes.cartucho38.uso"))
                .add(new PagItem(ic(Items.SKELETON_SKULL), "municoes.cartucho38.loot"));

        // ================================================== 8. MOBS
        GuiaCategoria mobs = cat("mobs", IntoxicantesMod.OVO_GAGO);
        mobs.entrada("gago", ic(IntoxicantesMod.OVO_GAGO))
                .add(new PagTitulo("mobs.gago.hero"))
                .add(new PagTexto("mobs.gago.1"))
                .add(new PagItem(ic(IntoxicantesMod.ESCOPETA), "mobs.gago.arma"))
                .add(new PagDica("mobs.gago.dica"));
        mobs.entrada("traficante", ic(IntoxicantesMod.OVO_TRAFICANTE))
                .add(new PagTitulo("mobs.traficante.hero"))
                .add(new PagTexto("mobs.traficante.1"))
                .add(new PagDica("mobs.traficante.dica"));
        mobs.entrada("juca", ic(IntoxicantesMod.OVO_JUCA))
                .add(new PagTitulo("mobs.juca.hero"))
                .add(new PagTexto("mobs.juca.1"))
                .add(new PagItem(ic(IntoxicantesMod.CIGARRO_CAMEL), "mobs.juca.camel"))
                .add(new PagItem(ic(IntoxicantesMod.CAMISA_MATANZA), "mobs.juca.camisa"));

        // ================================================== 9. ITENS ESPECIAIS
        GuiaCategoria especiais = cat("especiais", IntoxicantesMod.BASEADO);
        especial(especiais, "baseado", IntoxicantesMod.BASEADO, IntoxicantesMod.MACONHA_SEDA);
        especial(especiais, "cigarro_camel", IntoxicantesMod.CIGARRO_CAMEL, null);
        especial(especiais, "opio", IntoxicantesMod.OPIO, null);
        especial(especiais, "lsd", IntoxicantesMod.LSD, null);
        especial(especiais, "camisa_matanza", IntoxicantesMod.CAMISA_MATANZA, null);

        // ================================================== 10. BLOCOS
        GuiaCategoria blocos = cat("blocos", IntoxicantesMod.POSTE_LUZ.asItem());
        bloco(blocos, "poste_luz", IntoxicantesMod.POSTE_LUZ.asItem(), null);
        bloco(blocos, "lampada_led", IntoxicantesMod.LAMPADA_LED.asItem(), null);
        bloco(blocos, "lampada_potencia", IntoxicantesMod.LAMPADA_LED_50W.asItem(),
                receita("lampada_led_50w", st(IntoxicantesMod.LAMPADA_LED_50W.asItem()),
                        ing(Items.GLASS, 1), ing(IntoxicantesMod.LAMPADA_LED_30W.asItem(), 1)));
        for (Block lampada : IntoxicantesMod.LAMPADAS_POTENCIA) {
            if (lampada != IntoxicantesMod.LAMPADA_LED_50W) {
                bloco(blocos, "lampada_" + BuiltInRegistries.BLOCK.getKey(lampada).getPath()
                                .substring("lampada_led_".length()),
                        lampada.asItem(), null);
            }
        }
        bloco(blocos, "prateleira_mercado", IntoxicantesMod.PRATELEIRA_MERCADO.asItem(), null);
        bloco(blocos, "caixa_mercado", IntoxicantesMod.CAIXA_MERCADO.asItem(), null);
        bloco(blocos, "asfalto", IntoxicantesMod.ASFALTO.asItem(), null);
        bloco(blocos, "faixa_pedestre", IntoxicantesMod.FAIXA_PEDESTRE.asItem(), null);
        bloco(blocos, "hidrante", IntoxicantesMod.HIDRANTE.asItem(), null);
        bloco(blocos, "placa_esquinao", IntoxicantesMod.PLACA_ESQUINAO.asItem(), null);
        bloco(blocos, "painel_led", IntoxicantesMod.PAINEL_LED.asItem(),
                receita("painel_led", st(IntoxicantesMod.PAINEL_LED.asItem()),
                        ing(Items.IRON_INGOT, 3), ing(Items.GLASS, 5), ing(Items.REDSTONE_BLOCK, 1)));

        // ================================================== 11. ELETRICIDADE
        GuiaCategoria eletricidade = cat("eletricidade", IntoxicantesMod.QUADRO_ELETRICO.asItem());
        eletricidade.entrada("rede", ic(IntoxicantesMod.QUADRO_ELETRICO.asItem()))
                .add(new PagTitulo("eletricidade.rede.hero"))
                .add(new PagTexto("eletricidade.rede.1"))
                .add(new PagItem(ic(IntoxicantesMod.QUADRO_ELETRICO.asItem()),
                        "eletricidade.rede.quadro"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_2_5MM.asItem()),
                        "eletricidade.rede.saidas"))
                .add(new PagItem(ic(IntoxicantesMod.INTERRUPTOR_SIMPLES.asItem()),
                        "eletricidade.rede.interruptor"))
                .add(new PagItem(ic(IntoxicantesMod.SOQUETE_TETO.asItem()),
                        "eletricidade.rede.soquete"))
                .add(new PagItem(ic(IntoxicantesMod.TOMADA.asItem()),
                        "eletricidade.rede.tomada"))
                .add(new PagDica("eletricidade.rede.dica"));

        eletricidade.entrada("quadro_gui", ic(IntoxicantesMod.QUADRO_ELETRICO.asItem()))
                .add(new PagTitulo("eletricidade.quadro_gui.hero"))
                .add(new PagTexto("eletricidade.quadro_gui.1"))
                .add(new PagItem(ic(IntoxicantesMod.QUADRO_ELETRICO.asItem()),
                        "eletricidade.quadro_gui.ajustes"))
                .add(new PagDica("eletricidade.quadro_gui.dica"));

        eletricidade.entrada("bitolas", ic(IntoxicantesMod.CABO_COBRE_1_5MM.asItem()))
                .add(new PagTitulo("eletricidade.bitolas.hero"))
                .add(new PagTexto("eletricidade.bitolas.1"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_1_5MM.asItem()),
                        "eletricidade.bitolas.1_5"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_2_5MM.asItem()),
                        "eletricidade.bitolas.2_5"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_4MM.asItem()),
                        "eletricidade.bitolas.4"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_6MM.asItem()),
                        "eletricidade.bitolas.6"))
                .add(new PagItem(ic(IntoxicantesMod.CABO_COBRE_10MM.asItem()),
                        "eletricidade.bitolas.10"))
                .add(new PagDica("eletricidade.bitolas.dica"));

        eletricidade.entrada("multimetro", ic(IntoxicantesMod.MULTIMETRO))
                .add(new PagTitulo("eletricidade.multimetro.hero"))
                .add(new PagTexto("eletricidade.multimetro.1"))
                .add(receita("multimetro", st(IntoxicantesMod.MULTIMETRO),
                        ing(Items.GLASS, 1), ing(Items.REDSTONE, 1), ing(Items.IRON_INGOT, 1)))
                .add(new PagItem(ic(IntoxicantesMod.MULTIMETRO),
                        "eletricidade.multimetro.leitura"))
                .add(new PagDica("eletricidade.multimetro.dica"));

        eletricidade.entrada("tomada", ic(IntoxicantesMod.TOMADA.asItem()))
                .add(new PagTitulo("eletricidade.tomada.hero"))
                .add(new PagTexto("eletricidade.tomada.1"))
                .add(receita("tomada", st(IntoxicantesMod.TOMADA.asItem(), 2),
                        ing(Items.COPPER_INGOT, 1), ing(Items.IRON_NUGGET, 1)))
                .add(new PagItem(ic(IntoxicantesMod.TOMADA.asItem()),
                        "eletricidade.tomada.leitura"))
                .add(new PagDica("eletricidade.tomada.dica"));

        // v1.2.80: o cabo suspenso (ligação ponto a ponto, sem preencher blocos)
        eletricidade.entrada("cabo_suspenso", ic(IntoxicantesMod.BOBINA_COBRE_2_5MM))
                .add(new PagTitulo("eletricidade.cabo_suspenso.hero"))
                .add(new PagTexto("eletricidade.cabo_suspenso.1"))
                .add(receita("conector_eletrico", st(IntoxicantesMod.CONECTOR_ELETRICO.asItem(), 4),
                        ing(Items.COPPER_INGOT, 4), ing(Items.IRON_NUGGET, 1)))
                .add(new PagItem(ic(IntoxicantesMod.CONECTOR_ELETRICO.asItem()),
                        "eletricidade.cabo_suspenso.conector"))
                .add(new PagItem(ic(IntoxicantesMod.BOBINA_COBRE_2_5MM),
                        "eletricidade.cabo_suspenso.bobina"))
                .add(receita("bobina_cobre_1_5mm", st(IntoxicantesMod.BOBINA_COBRE_1_5MM, 2),
                        ing(Items.COPPER_INGOT, 2), ing(Items.IRON_NUGGET, 2)))
                .add(receita("bobina_cobre_2_5mm", st(IntoxicantesMod.BOBINA_COBRE_2_5MM, 2),
                        ing(Items.COPPER_INGOT, 2), ing(IntoxicantesMod.CABO_COBRE_1_5MM.asItem(), 2)))
                .add(receita("bobina_cobre_4mm", st(IntoxicantesMod.BOBINA_COBRE_4MM, 2),
                        ing(Items.COPPER_INGOT, 2), ing(IntoxicantesMod.CABO_COBRE_2_5MM.asItem(), 2)))
                .add(receita("bobina_cobre_6mm", st(IntoxicantesMod.BOBINA_COBRE_6MM, 2),
                        ing(Items.COPPER_INGOT, 2), ing(IntoxicantesMod.CABO_COBRE_4MM.asItem(), 2)))
                .add(receita("bobina_cobre_10mm", st(IntoxicantesMod.BOBINA_COBRE_10MM, 2),
                        ing(Items.COPPER_INGOT, 2), ing(IntoxicantesMod.CABO_COBRE_6MM.asItem(), 2)))
                .add(new PagDica("eletricidade.cabo_suspenso.dica"));

        // ================================================== 12. ECONOMIA R$
        GuiaCategoria economia = cat("economia", IntoxicantesMod.REAL);
        economia.entrada("real", ic(IntoxicantesMod.MOEDA_1))
                .add(new PagTitulo("economia.real.hero"))
                .add(new PagTexto("economia.real.1"))
                .add(receita("emerald_to_real_single", st(IntoxicantesMod.MOEDA_1, 1),
                        ing(Items.EMERALD, 1)))
                .add(new PagTexto("economia.real.2"))
                .add(new PagDica("economia.real.dica"));
        // v1.2.60: a FAMÍLIA de notas — moeda de 1 até a de 200, cada uma vale
        // o que diz, e a escada de câmbio junta/quebra na bancada
        economia.entrada("notas", ic(IntoxicantesMod.NOTA_100))
                .add(new PagTitulo("economia.notas.hero"))
                .add(new PagTexto("economia.notas.1"))
                // v1.2.62: a ESCADA COMPLETA de câmbio — cada degrau é uma
                // receita real de bancada (junta a nota de cima, quebra a
                // de baixo); a 500 não tem degrau: é troféu do Wither
                .add(receita("cambio_1_para_2", st(IntoxicantesMod.REAL, 1),
                        ing(IntoxicantesMod.MOEDA_1, 2)))
                .add(receita("cambio_5_montar", st(IntoxicantesMod.NOTA_5, 1),
                        ing(IntoxicantesMod.MOEDA_1, 1), ing(IntoxicantesMod.REAL, 2)))
                .add(receita("cambio_10_montar", st(IntoxicantesMod.NOTA_10, 1),
                        ing(IntoxicantesMod.NOTA_5, 2)))
                .add(receita("cambio_20_montar", st(IntoxicantesMod.NOTA_20, 1),
                        ing(IntoxicantesMod.NOTA_10, 2)))
                .add(receita("cambio_50_montar", st(IntoxicantesMod.NOTA_50, 1),
                        ing(IntoxicantesMod.NOTA_20, 2), ing(IntoxicantesMod.NOTA_10, 1)))
                .add(receita("cambio_100_montar", st(IntoxicantesMod.NOTA_100, 1),
                        ing(IntoxicantesMod.NOTA_50, 2)))
                .add(receita("cambio_200_montar", st(IntoxicantesMod.NOTA_200, 1),
                        ing(IntoxicantesMod.NOTA_100, 2)))
                .add(new PagTexto("economia.notas.2"))
                .add(new PagDica("economia.notas.dica"));
        // v1.2.62: a 500 ganhou página própria — troféu de boss, sem receita
        economia.entrada("nota500", ic(IntoxicantesMod.NOTA_500))
                .add(new PagTitulo("economia.nota500.hero"))
                .add(new PagTexto("economia.nota500.1"))
                .add(new PagDica("economia.nota500.dica"));
        economia.entrada("fiado", ic(IntoxicantesMod.REAL))
                .add(new PagTitulo("economia.fiado.hero"))
                .add(new PagTexto("economia.fiado.1"))
                .add(new PagLinha("economia.fiado.juros"))
                .add(new PagLinha("economia.fiado.quitacao"))
                .add(new PagDica("economia.fiado.dica"));
        economia.entrada("peru", ic(IntoxicantesMod.CIGARRO_CAMEL))
                .add(new PagTitulo("economia.peru.hero"))
                .add(new PagTexto("economia.peru.1"))
                .add(new PagTexto("economia.peru.2"))
                .add(new PagTexto("economia.peru.3"))
                .add(new PagDica("economia.peru.dica"));
        economia.entrada("cotacao", ic(Items.EMERALD))
                .add(new PagTitulo("economia.cotacao.hero"))
                .add(new PagTexto("economia.cotacao.1"))
                .add(new PagLinha("economia.cotacao.faixa"))
                .add(new PagLinha("economia.cotacao.markup"));

        // ================================================== 12. EFEITOS
        GuiaCategoria efeitos = cat("efeitos", Items.POTION);
        efeitos.entrada("tabela", ic(Items.POTION))
                .add(new PagTitulo("efeitos.tabela.hero"))
                .add(new PagLinha("efeitos.tabela.cerveja"))
                .add(new PagLinha("efeitos.tabela.vinho"))
                .add(new PagLinha("efeitos.tabela.cachaca"))
                .add(new PagLinha("efeitos.tabela.hidromel"))
                .add(new PagLinha("efeitos.tabela.rum"))
                .add(new PagLinha("efeitos.tabela.cafeina"))
                .add(new PagLinha("efeitos.tabela.ressaca"))
                .add(new PagDica("efeitos.tabela.dica"));
    }

    // ==================================================== HELPERS DE MONTAGEM

    /** Página de máquina de prima: receita + cadeia real + texto. */
    private static void maquina(GuiaCategoria c, String id, Item item, String receitaId,
            ProcessosBebida.Prima p, ReceitaGrid.Ingrediente... materiais) {
        List<PagCadeia.Passo> passos = new ArrayList<>();
        passos.add(passo(p.input(), p.qtdIn(), false));
        passos.add(passoMaquina(item, tempo(SEG_MOSTURA)));
        passos.add(passo(p.output(), p.qtdOut(), true));
        if (p.secIn() != null) {
            passos.add(passoFase(p.secIn(), "cadeia.segunda_dose"));
            passos.add(passo(p.output(), p.qtdOut(), true));
        }
        if (p.extraQtd() > 0) {
            passos.add(passoFase(p.extraOut(), "cadeia.extra"));
        }
        GuiaEntrada e = c.entrada(id, ic(item));
        e.add(new PagTitulo("maquinas." + id + ".hero"))
                .add(new PagTexto("maquinas." + id + ".1"))
                .add(receita(receitaId, st(item), materiais))
                .add(new PagCadeia(passos))
                .add(new PagTexto("maquinas." + id + ".2"));
    }

    /** A uva usa a armação construída pelo jogador; valores vêm do cultivo real. */
    private static void parreira(GuiaCategoria c) {
        c.entrada("uva", ic(IntoxicantesMod.SEMENTE_UVA))
                .add(new PagTitulo("cultivos.uva.hero"))
                .add(new PagTexto("cultivos.uva.1"))
                .add(new PagItem(ic(IntoxicantesMod.SEMENTE_UVA), "cultivos.uva.semente"))
                .add(new PagItemDinamica(ic(IntoxicantesMod.UVA), "cultivos.uva.produto",
                        () -> Integer.toString(ParreiraBlockEntity.COLHEITA_NORMAL),
                        () -> Integer.toString(ParreiraEstrutura.MAX_APOIOS_COBERTURA)))
                .add(new PagItem(ic(IntoxicantesMod.LAMPADA_UV.asItem()), "cultivos.uva.uv"))
                .add(new PagTitulo("cultivos.uva.armacao"))
                .add(new PagItemDinamica(ic(Items.OAK_FENCE), "cultivos.uva.apoios",
                        () -> Integer.toString(ParreiraEstrutura.ALTURA_MAXIMA),
                        () -> ParreiraEstrutura.LARGURA_COBERTURA + " × " + ParreiraEstrutura.LARGURA_COBERTURA))
                .add(new PagTexto("cultivos.uva.2"))
                .add(new PagTitulo("cultivos.uva.colher"))
                .add(new PagTexto("cultivos.uva.interagir"))
                .add(new PagItemDinamica(ic(IntoxicantesMod.UVA), "cultivos.uva.rebrota",
                        () -> Integer.toString(ParreiraBlockEntity.TEMPO_REBROTA / 20), () -> ""))
                .add(new PagTexto("cultivos.uva.poda"))
                .add(new PagDica("cultivos.uva.legado"));
    }

    /** Página de cultivo: semente → produto, UV, estágios (texto em lang). */
    private static void cultivo(GuiaCategoria c, String id, Item semente, Item produto) {
        c.entrada(id, ic(semente))
                .add(new PagTitulo("cultivos." + id + ".hero"))
                .add(new PagTexto("cultivos." + id + ".1"))
                .add(new PagItem(ic(semente), "cultivos." + id + ".semente"))
                .add(new PagItem(ic(produto), "cultivos." + id + ".produto"))
                .add(new PagItem(ic(IntoxicantesMod.LAMPADA_UV.asItem()), "cultivos." + id + ".uv"))
                .add(new PagTexto("cultivos." + id + ".2"));
    }

    /** Página de item especial (efeitos ficcionais; obtenção opcional). */
    private static void especial(GuiaCategoria c, String id, Item item, Item de) {
        GuiaEntrada e = c.entrada(id, ic(item));
        e.add(new PagTitulo("especiais." + id + ".hero"))
                .add(new PagItem(ic(item), "especiais." + id + ".efeito"));
        if (de != null) {
            e.add(new PagItem(ic(de), "especiais." + id + ".de"));
        }
        e.add(new PagTexto("especiais." + id + ".1"));
    }

    /** Página de bloco simples (receita opcional + texto). */
    private static void bloco(GuiaCategoria c, String id, Item item, ReceitaGrid receita) {
        GuiaEntrada e = c.entrada(id, ic(item));
        e.add(new PagTitulo("blocos." + id + ".hero"));
        if (receita != null) {
            e.add(receita);
        }
        e.add(new PagTexto("blocos." + id + ".1"));
    }
}
