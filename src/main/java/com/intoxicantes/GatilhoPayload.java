package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * v1.2.57 — Payload C2S do GATILHO ESQUERDO: o clique esquerdo não é mais
 * "use" do item — o client avisa o servidor, que executa o caminho de tiro
 * REAL (fase do mecanismo, munição, cooldown, dispersão/alcance do ADS —
 * tudo validado de novo server-side; nunca confie no client).
 *
 * O item.use() do botão direito ficou INERTE (PASS): mirar é client-side
 * (isRightPressed), atirar é ESTE payload. Um clique = uma tentativa de
 * disparo, na mão da arma.
 */
public record GatilhoPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GatilhoPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "gatilho_arma"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GatilhoPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (p, buf) -> {},
                    buf -> new GatilhoPayload());

    /** Registra no canal de jogo (chamado do init do mod). */
    public static void registrar() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> disparar(player));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Ponto de entrada dos GAME TESTS: mesmo caminho do servidor. */
    static void dispararTeste(ServerPlayer player) {
        disparar(player);
    }

    /** Encontra a arma de fogo na mão e executa o disparo (validação server). */
    private static void disparar(ServerPlayer player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof EscopetaItem arma) {
                arma.atirarViaGatilho(player, hand, stack);
                return;
            }
            if (stack.getItem() instanceof RevolverItem arma) {
                arma.atirarViaGatilho(player, hand, stack);
                return;
            }
        }
    }
}
