package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * v1.2.53 — Payload C2S do ESTADO DE MIRA (ADS) das armas de fogo: com a
 * troca dos botões (esquerdo atira, direito mira), o SHIFT deixou de ser o
 * sinal de mira — e o vanilla só sincroniza agachamento. O client manda
 * "estou mirando / parei de mirar" na BORDA do estado (não por tick), e o
 * servidor guarda o flag pra dispersão/alcance do tiro. O servidor valida
 * que a arma continua na mão na hora do disparo — mentir aqui só vale se a
 * arma estiver na mão mesmo, e o custo de mentir é o mesmo de mirar de fora.
 */
public record MiraPayload(boolean mirando) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MiraPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "mira_arma"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiraPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (p, buf) -> buf.writeBoolean(p.mirando()),
                    buf -> new MiraPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Estado de mira por jogador (o servidor consulta no disparo). */
    private static final java.util.Map<java.util.UUID, Boolean> MIRANDO = new java.util.HashMap<>();

    public static void registrar() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                boolean armaNaMao = player.getMainHandItem().is(IntoxicantesMod.ESCOPETA)
                        || player.getMainHandItem().is(IntoxicantesMod.REVOLVER)
                        || player.getOffhandItem().is(IntoxicantesMod.ESCOPETA)
                        || player.getOffhandItem().is(IntoxicantesMod.REVOLVER);
                if (armaNaMao) {
                    MIRANDO.put(player.getUUID(), payload.mirando());
                } else {
                    MIRANDO.remove(player.getUUID());
                }
            });
        });
    }

    /** O servidor pergunta: este atirador está no ADS? (limpa ao sair do mundo) */
    public static boolean estaMirando(ServerPlayer player) {
        return MIRANDO.getOrDefault(player.getUUID(), false);
    }

    /** Logout/dimensão: limpa o estado (o client re-manda na borda se voltar). */
    public static void limpar(ServerPlayer player) {
        MIRANDO.remove(player.getUUID());
    }
}
