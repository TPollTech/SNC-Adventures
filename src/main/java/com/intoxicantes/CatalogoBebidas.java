package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * O CATÁLOGO DE BEBIDAS AGORA É DATAPACK (v1.2.60): as receitas de dorna,
 * alambique, barril e máquinas de prima moram em JSON sob
 * {@code data/intoxicantes/processo_bebida/<máquina>/} — um arquivo por
 * receita, com codec declarativo próprio (um datapack pode adicionar/mudar
 * receitas SEM tocar em código).
 *
 * FONTE ÚNICA preservada: as máquinas, as GUIs e o Guia continuam lendo
 * pelo {@link ProcessosBebida} (que delega aqui) — nenhum número escrito
 * em dois lugares, regra data-driven do AGENTS.md.
 *
 * RESILIÊNCIA: arquivo malformado é pulado com WARNING (vanilla RecipeManager
 * faz igual); pasta vazia ou datapack sem os arquivos → os DEFAULTS de fábrica
 * voltam a valer no primeiro acesso (o jogo nunca fica sem receita).
 *
 * Formato (todos os campos obrigatórios salvo os marcados):
 * <pre>
 * { "input": "intoxicantes:cana_de_acucar", "qtdIn": 4,
 *   "output": "intoxicantes:caldo_de_cana", "qtdOut": 4,
 *   "secIn": "...", "secQtd": 1,        (opcional, caldeirão)
 *   "extraOut": "...", "extraQtd": 1,   (opcional, moenda)
 *   "tempoSeg": 40 }                    (só máquinas de prima)
 * </pre>
 */
public final class CatalogoBebidas {

    private CatalogoBebidas() {}

    // ==================================================== CODECS
    // Item por ID do registro (intoxicantes:cana_de_acucar → Item).

    private static Codec<Item> item() {
        return Identifier.CODEC.xmap(
                id -> BuiltInRegistries.ITEM.getValue(id),
                item -> BuiltInRegistries.ITEM.getKey(item));
    }

    private static final Codec<ProcessosBebida.Dorna> DORNA_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    item().fieldOf("input").forGetter(ProcessosBebida.Dorna::input),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdIn").forGetter(ProcessosBebida.Dorna::qtdIn),
                    item().fieldOf("output").forGetter(ProcessosBebida.Dorna::output),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdOut").forGetter(ProcessosBebida.Dorna::qtdOut))
                    .apply(i, ProcessosBebida.Dorna::new));

    private static final Codec<ProcessosBebida.Alambique> ALAMBIQUE_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    item().fieldOf("input").forGetter(ProcessosBebida.Alambique::input),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdIn").forGetter(ProcessosBebida.Alambique::qtdIn),
                    item().fieldOf("output").forGetter(ProcessosBebida.Alambique::output),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdOut").forGetter(ProcessosBebida.Alambique::qtdOut))
                    .apply(i, ProcessosBebida.Alambique::new));

    private static final Codec<ProcessosBebida.Barril> BARRIL_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("bebida").forGetter(ProcessosBebida.Barril::bebida),
                    item().fieldOf("input").forGetter(ProcessosBebida.Barril::input),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdIn").forGetter(ProcessosBebida.Barril::qtdIn),
                    Codec.intRange(0, 86400).fieldOf("tempoFermentacaoSeg")
                            .forGetter(ProcessosBebida.Barril::tempoFermentacaoSeg),
                    Codec.intRange(0, 86400).fieldOf("tempoMaturacaoSeg")
                            .forGetter(ProcessosBebida.Barril::tempoMaturacaoSeg),
                    item().fieldOf("bebidaFinal").forGetter(ProcessosBebida.Barril::bebidaFinal),
                    ExtraCodecs.POSITIVE_INT.fieldOf("garrafas").forGetter(ProcessosBebida.Barril::garrafas))
                    .apply(i, ProcessosBebida.Barril::new));

    private static final Codec<ProcessosBebida.Prima> PRIMA_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    item().fieldOf("input").forGetter(ProcessosBebida.Prima::input),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdIn").forGetter(ProcessosBebida.Prima::qtdIn),
                    item().fieldOf("output").forGetter(ProcessosBebida.Prima::output),
                    ExtraCodecs.POSITIVE_INT.fieldOf("qtdOut").forGetter(ProcessosBebida.Prima::qtdOut),
                    item().optionalFieldOf("secIn").forGetter(
                            (ProcessosBebida.Prima r) -> Optional.ofNullable(r.secIn())),
                    Codec.intRange(0, 64).optionalFieldOf("secQtd", 0).forGetter(ProcessosBebida.Prima::secQtd),
                    item().optionalFieldOf("extraOut").forGetter(
                            (ProcessosBebida.Prima r) -> Optional.ofNullable(r.extraOut())),
                    Codec.intRange(0, 64).optionalFieldOf("extraQtd", 0).forGetter(ProcessosBebida.Prima::extraQtd),
                    Codec.intRange(1, 86400).fieldOf("tempoSeg").forGetter(ProcessosBebida.Prima::tempoSeg))
                    .apply(i, (Item in, Integer qIn, Item out, Integer qOut,
                            Optional<Item> sec, Integer secQ,
                            Optional<Item> extra, Integer extraQ, Integer tempo) ->
                            new ProcessosBebida.Prima(in, qIn, out, qOut,
                                    sec.orElse(null), secQ, extra.orElse(null), extraQ, tempo)));

    // ==================================================== O ESTADO (substituído a cada reload)

    /** True quando PELO MENOS um reload aplicou (o JSON — não o fallback — alimentou o catálogo). */
    private static volatile boolean recarregado = false;

    /** O reload do datapack rodou? (gametest prova o caminho JSON fim a fim) */
    static boolean recarregado() {
        return recarregado;
    }

    private static volatile List<ProcessosBebida.Dorna> dornas = new ArrayList<>();
    private static volatile List<ProcessosBebida.Alambique> alambiques = new ArrayList<>();
    private static volatile List<ProcessosBebida.Barril> barris = new ArrayList<>();
    private static volatile List<ProcessosBebida.Prima> moendas = new ArrayList<>();
    private static volatile List<ProcessosBebida.Prima> prensas = new ArrayList<>();
    private static volatile List<ProcessosBebida.Prima> caldeiroes = new ArrayList<>();

    static void setDornas(List<ProcessosBebida.Dorna> l) { dornas = l; }
    static void setAlambiques(List<ProcessosBebida.Alambique> l) { alambiques = l; }
    static void setBarris(List<ProcessosBebida.Barril> l) { barris = l; }
    static void setMoendas(List<ProcessosBebida.Prima> l) { moendas = l; }
    static void setPrensas(List<ProcessosBebida.Prima> l) { prensas = l; }
    static void setCaldeiroes(List<ProcessosBebida.Prima> l) { caldeiroes = l; }

    // ==================================================== LEITURA (com fallback de fábrica)

    /** Garante defaults se o datapack não trouxe nada (primeiro acesso/reload vazio). */
    private static void garantir() {
        if (dornas.isEmpty()) {
            dornas = List.of(d(CALDO, MOSTO_CANA), d(MELACO, MOSTO_RUM));
        }
        if (alambiques.isEmpty()) {
            alambiques = List.of(a(MOSTO_CANA, CACHACA_J), a(MOSTO_RUM, RUM_J));
        }
        if (barris.isEmpty()) {
            barris = List.of(
                    new ProcessosBebida.Barril("cachaca", CACHACA_J, 2, 0, 600, CACHACA, 4),
                    new ProcessosBebida.Barril("cerveja", MOSTO_LUP, 4, 480, 120, CERVEJA, 4),
                    new ProcessosBebida.Barril("rum", RUM_J, 2, 0, 600, RUM, 4),
                    new ProcessosBebida.Barril("vinho", MOSTO_UVA, 4, 300, 300, VINHO, 4));
        }
        if (moendas.isEmpty()) {
            moendas = List.of(new ProcessosBebida.Prima(CANA, 4, CALDO, 4,
                    null, 0, BAGACO, 1, 40));
        }
        if (prensas.isEmpty()) {
            prensas = List.of(new ProcessosBebida.Prima(UVA, 6, MOSTO_UVA, 4,
                    null, 0, null, 0, 40));
        }
        if (caldeiroes.isEmpty()) {
            caldeiroes = List.of(new ProcessosBebida.Prima(MALTE, 4, MOSTO_LUP, 4,
                    LUPULO, 1, null, 0, 40));
        }
    }

    // Apelidos locais (o javadoc das receitas de fábrica mora no ProcessosBebida).
    private static final Item CANA = IntoxicantesMod.CANA_DE_ACUCAR;
    private static final Item CALDO = IntoxicantesMod.CALDO_DE_CANA;
    private static final Item BAGACO = IntoxicantesMod.BAGACO_DE_CANA;
    private static final Item UVA = IntoxicantesMod.UVA;
    private static final Item MOSTO_UVA = IntoxicantesMod.MOSTO_DE_UVA;
    private static final Item MALTE = IntoxicantesMod.MALTE;
    private static final Item LUPULO = IntoxicantesMod.LOUPULO_FRESCO;
    private static final Item MOSTO_LUP = IntoxicantesMod.MOSTO_CERVEJA_LUPULADO;
    private static final Item MELACO = IntoxicantesMod.MELACO;
    private static final Item MOSTO_CANA = IntoxicantesMod.MOSTO_CANA_FERMENTADO;
    private static final Item MOSTO_RUM = IntoxicantesMod.MOSTO_RUM_FERMENTADO;
    private static final Item CACHACA_J = IntoxicantesMod.CACHACA_JOVEM;
    private static final Item RUM_J = IntoxicantesMod.RUM_JOVEM;
    private static final Item CACHACA = IntoxicantesMod.CACHACA;
    private static final Item CERVEJA = IntoxicantesMod.CERVEJA;
    private static final Item RUM = IntoxicantesMod.RUM;
    private static final Item VINHO = IntoxicantesMod.VINHO;

    private static ProcessosBebida.Dorna d(Item in, Item out) {
        return new ProcessosBebida.Dorna(in, 4, out, 4);
    }

    private static ProcessosBebida.Alambique a(Item in, Item out) {
        return new ProcessosBebida.Alambique(in, 4, out, 2);
    }

    // ==================================================== LISTAS PRA O ProcessosBebida

    static List<ProcessosBebida.Dorna> dornas() {
        garantir();
        return dornas;
    }

    static List<ProcessosBebida.Alambique> alambiques() {
        garantir();
        return alambiques;
    }

    static List<ProcessosBebida.Barril> barris() {
        garantir();
        return barris;
    }

    static List<ProcessosBebida.Prima> moendas() {
        garantir();
        return moendas;
    }

    static List<ProcessosBebida.Prima> prensas() {
        garantir();
        return prensas;
    }

    static List<ProcessosBebida.Prima> caldeiroes() {
        garantir();
        return caldeiroes;
    }

    // ==================================================== O LISTENER (1 por pasta)

    /**
     * Lê os JSON de UMA pasta do datapack e substitui a lista destino.
     * Pasta vazia = lista vazia (o {@code garantir()} devolve os defaults).
     * JSON malformado: o próprio listener da vanilla loga o erro e PULA o
     * arquivo (o resto da pasta carrega — resiliência do RecipeManager).
     */
    private static final class Pasta<T> extends
            SimpleJsonResourceReloadListener<T>
            implements net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener {

        private final Identifier id;
        private final Consumer<List<T>> destino;

        Pasta(Identifier id, Codec<T> codec, String pasta, Consumer<List<T>> destino) {
            super(codec, FileToIdConverter.json("processo_bebida/" + pasta));
            this.id = id;
            this.destino = destino;
        }

        @Override
        protected void apply(Map<Identifier, T> mapa, ResourceManager rm,
                ProfilerFiller profiler) {
            // substituição INTEIRA (reload = nova verdade; nunca duplica)
            destino.accept(new ArrayList<>(mapa.values()));
            recarregado = true;
        }

        @Override
        public Identifier getFabricId() {
            return id;
        }
    }

    // ==================================================== REGISTRO

    /**
     * Registra os 6 listeners (um por pasta de máquina) no reload de dados
     * do servidor. Chamado UMA vez no boot do mod.
     */
    static void registrar() {
        var helper = net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(
                net.minecraft.server.packs.PackType.SERVER_DATA);
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_dorna"),
                DORNA_CODEC, "dorna", CatalogoBebidas::setDornas));
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_alambique"),
                ALAMBIQUE_CODEC, "alambique", CatalogoBebidas::setAlambiques));
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_barril"),
                BARRIL_CODEC, "barril", CatalogoBebidas::setBarris));
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_moenda"),
                PRIMA_CODEC, "moenda", CatalogoBebidas::setMoendas));
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_prensa"),
                PRIMA_CODEC, "prensa", CatalogoBebidas::setPrensas));
        helper.addReloadListener(new Pasta<>(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "processo_bebida_caldeirao"),
                PRIMA_CODEC, "caldeirao", CatalogoBebidas::setCaldeiroes));
    }
}
