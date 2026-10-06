package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cartao Fidelidade do Mercado Esquinao: conta as compras de cada fregues (por
 * UUID) e define o nivel. Mais compras = mais desconto no precinho + itens
 * exclusivos no cardapio (a 12 so sai pro Dono da Esquina, claro).
 * Persistido em JSON no diretorio do mundo, igual ao PlayerMoney.
 */
public final class FidelidadeData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Integer> compras = new HashMap<>();
    private static File saveFile;

    /** Compras minimas de cada nivel (indice = tier 0..3). */
    public static final int[] META_TIER = {0, 5, 15, 30};
    /** Indice do tier máximo (Dono da Esquina) — saudação VIP do Gago. */
    public static final int TIER_DONO = META_TIER.length - 1;
    /** Desconto de cada nivel, em % (aplicado em cima do preco cheio). */
    public static final int[] DESCONTO_TIER = {0, 5, 10, 15};
    /** Fronteira calculada pelo catálogo, incluindo todos os alimentos comuns. */
    public static int primeiroExclusivo() {
        return TradeCatalog.gago(0).size();
    }

    private FidelidadeData() {}

    /** Inicializa o cartao fidelidade com o diretorio do mundo. */
    public static void init(File worldDir) {
        saveFile = new File(worldDir, "intoxicantes_fidelidade.json");
        load();
    }

    public static int getCompras(ServerPlayer player) {
        return compras.getOrDefault(player.getUUID(), 0);
    }

    /** Nivel do fregues: 0 Fregues, 1 Fregues da Esquina, 2 Cabare VIP, 3 Dono da Esquina. */
    public static int getTier(ServerPlayer player) {
        int c = getCompras(player);
        int tier = 0;
        for (int i = 0; i < META_TIER.length; i++) {
            if (c >= META_TIER[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /** Desconto atual do fregues, em %. */
    public static int descontoDe(ServerPlayer player) {
        return DESCONTO_TIER[getTier(player)];
    }

    /** Conta uma compra no historico (chamar depois de vender, nao antes kkkk). */
    public static void registrarCompra(ServerPlayer player) {
        compras.merge(player.getUUID(), 1, Integer::sum);
        save();
        // progressoes do cartao: primeira compra, Fregues da Esquina, Dono
        Progressoes.aoComprar(player, compras.get(player.getUUID()), getTier(player));
    }

    /**
     * Preco final do cardapio: desconto do fidelidade MENOS a vantagem do Gago
     * (v1.2.13): fregues visivelmente bêbado paga markup por nivel acima do
     * limiar da fala (teto 30% do config) — o dono da esquina nao perde a piada
     * kkkk. Fregues alegre (abaixo do limiar) paga normal. Minimo R$ 1.
     * Ponto UNICO de preco: a tela e a cobranca usam este metodo, entao o
     * markup aparece pro fregues NA TELA antes de pagar.
     */
    public static int precoComDesconto(ServerPlayer player, int precoCheio) {
        int preco = Math.round(precoCheio * (100 - descontoDe(player)) / 100f);
        int nivel = Embriaguez.nivel(player);
        int limiarFonar = ModConfig.get().embriaguezLimiarFonar;
        int markupPct = ModConfig.get().embriaguezMarkup;
        // v1.2.54 — DOIS MARKUPS, VALE O MAIOR:
        //  - bêbado visível: embriaguezMarkup % por nível acima do limiar (teto 30)
        //  - desesperado: viciado em síndrome de abstinência paga o preço que
        //    o Gago mandar (saudeMarkupDesesperado %) — o fregues aceita kkkk
        int markup = 0;
        if (markupPct > 0 && nivel >= limiarFonar) {
            markup = Math.min(markupPct * (nivel - limiarFonar + 1), 30);
        }
        int desespero = ModConfig.get().saudeMarkupDesesperado;
        if (desespero > 0 && SaudeSystem.emAbstinencia(player)) {
            markup = Math.max(markup, desespero);
        }
        if (markup > 0) {
            preco = Math.round(preco * (100 + markup) / 100f);
        }
        return Math.max(1, preco);
    }

    // ==================================================== CARDAPIO

    /** Entrada do cardapio: preco cheio em R$ e o que o fregues leva. */
    public record Entrada(int preco, ItemStack item) {}

    /**
     * Cardapio completo pro fregues: bebidas + substancias + conveniencia,
     * e os EXCLUSIVOS do nivel de fidelidade (a partir de primeiroExclusivo()).
     * ORDEM FIXA: o indice da compra na tela referencia exatamente esta lista.
     */
    public static List<Entrada> cardapioPara(ServerPlayer player) {
        return TradeCatalog.gago(getTier(player)).stream()
                .map(entry -> new Entrada(entry.price(), entry.stack())).toList();
    }

    // ==================================================== SAVE / LOAD (padrao PlayerMoney)

    private static void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (Reader reader = Files.newBufferedReader(saveFile.toPath())) {
            Type mapType = new TypeToken<HashMap<UUID, Integer>>() {}.getType();
            HashMap<UUID, Integer> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                compras.clear();
                compras.putAll(loaded);
            }
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao carregar fidelidade: {}", e.getMessage());
        }
    }

    private static void save() {
        if (saveFile == null) return;
        try (Writer writer = Files.newBufferedWriter(saveFile.toPath())) {
            GSON.toJson(compras, writer);
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao salvar fidelidade: {}", e.getMessage());
        }
    }
}
