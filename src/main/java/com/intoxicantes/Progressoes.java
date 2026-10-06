package com.intoxicantes;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Progressoes do mod: concede conquistas programaticamente quando o evento
 * acontece no codigo (compra, tier de fidelidade, colheita perfeita). As
 * conquistas com trigger vanilla (matou o Traficante, tradeou) sao 100% JSON.
 *
 * O award por codigo usa criterio "impossible" no JSON + award() daqui — o
 * padrao pra progresso que o vanilla nao tem trigger nativo pra medir.
 */
public final class Progressoes {
    private Progressoes() {}

    public static final Identifier RAIZ = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "esquinao/raiz");
    public static final Identifier FREGUES_DA_ESQUINA = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "esquinao/fregues_da_esquina");
    public static final Identifier DONO_DA_ESQUINA = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "esquinao/dono_da_esquina");
    public static final Identifier COLHEITA_PERFEITA = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "esquinao/colheita_perfeita");

    /** Concede a conquista (criterion "main") se existir e ainda nao tiver sido concedida. */
    public static void conceder(ServerPlayer player, Identifier id) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id);
        if (holder != null) {
            player.getAdvancements().award(holder, "main");
        }
    }

    /** Chamado do FidelidadeData.registrarCompra: avanca o cartao fidelidade. */
    public static void aoComprar(ServerPlayer player, int comprasTotais, int tier) {
        conceder(player, RAIZ);
        if (comprasTotais >= FidelidadeData.META_TIER[1]) {
            conceder(player, FREGUES_DA_ESQUINA);
        }
        if (tier >= 3) {
            conceder(player, DONO_DA_ESQUINA);
        }
    }
}
