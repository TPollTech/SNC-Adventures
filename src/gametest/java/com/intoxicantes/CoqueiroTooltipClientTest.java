package com.intoxicantes;

import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;

/** Verifica a chave e o texto efetivamente apresentado no tooltip do tronco. */
public class CoqueiroTooltipClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                var tronco = IntoxicantesMod.COQUEIRO_TRONCO.asItem();
                String chave = tronco.getDescriptionId();
                String nome = new ItemStack(tronco).getHoverName().getString();
                if (!"block.intoxicantes.coqueiro_tronco".equals(chave)
                        || !Set.of("Tronco de Coqueiro", "Palm Trunk").contains(nome)) {
                    throw new AssertionError("Tooltip do tronco não resolveu: " + chave + " → " + nome);
                }
                IntoxicantesMod.LOGGER.info("[Tooltip] coqueiro_tronco: {} → {}", chave, nome);
            });
        }
    }
}
