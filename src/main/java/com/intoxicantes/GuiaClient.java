package com.intoxicantes;

import net.minecraft.client.Minecraft;

/**
 * PONTE CLIENT DO GUIA — o GuiaItem (comum) chama GuiaClient.abrir() no lado
 * client; o método vira no-op se a classe carregar num dedicado (o corpo só
 * toca classes client quando isClientSide, e esta classe SÓ é carregada pela
 * chamada de dentro do branch client do item).
 */
public final class GuiaClient {

    private GuiaClient() {}

    public static void abrir() {
        Minecraft.getInstance().gui.setScreen(
                new GuiaScreen(Minecraft.getInstance().player != null
                        ? Minecraft.getInstance().player.getDisplayName()
                        : null));
    }
}
