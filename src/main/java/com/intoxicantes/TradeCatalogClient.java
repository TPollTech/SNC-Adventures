package com.intoxicantes;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * v1.2.44 — Espelho CLIENT dos ICONES que as telas desenham por conta
 * própria (o servidor manda só números; o desenho é daqui).
 *
 * v1.2.66: a aba de venda do PONTO do traficante virou CRU DE DROGA —
 * a ordem tem que ser EXATAMENTE a do TradeCatalog.cruDrogas() do
 * servidor. A colheita de bebida é da tela do Gago (que recebe as 6
 * entradas no próprio payload).
 */
final class TradeCatalogClient {
    private TradeCatalogClient() {}

    static List<ItemStack> cruDrogas() {
        return List.of(
                new ItemStack(IntoxicantesMod.MACONHA_SEDA, 8),
                new ItemStack(IntoxicantesMod.OPIO, 8),
                new ItemStack(IntoxicantesMod.COCAINA, 4));
    }
}
