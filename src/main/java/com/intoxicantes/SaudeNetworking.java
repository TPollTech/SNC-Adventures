package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * v1.2.54 — REDE DA SAUDE. Payloads S2C (o servidor manda o estado) + C2S
 * (o client pede pra abrir o PRONTUARIO pela tecla H):
 *
 * - SaudeSyncPayload (S2C, periodico + instantaneo no uso): hidratacao,
 *   vicio (nivel + droga), estagio da abstinencia e dano dos 3 orgaos.
 *   O HUD de sede/vicio e o overlay das viagens leem daqui — o client
 *   nunca adivinha estado de saude.
 * - ViagemPayload (S2C): ligou uma viagem (efeito assinatura + intensidade).
 *   O client guarda e desenha overlay/alucinacoes enquanto o efeito vive.
 * - AbrirProntuarioPayload (C2S): a tecla H. O servidor responde com o
 *   SaudeSyncPayload marcado abrirProntuario=true (uma tela, um dado).
 */
public final class SaudeNetworking {
    private SaudeNetworking() {}

    // ==================================================== S2C: SYNC PERIODICO

    public record SaudeSyncPayload(int hidratacao, int vicio, String drogaVicio,
                                   int estagioAbstinencia, int figado, int pulmao,
                                   int estomago, boolean abrirProntuario,
                                   int alcool, int erva, int po, int pilula,
                                   int curasSeguidas)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SaudeSyncPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        IntoxicantesMod.MOD_ID, "saude_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SaudeSyncPayload> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> {
                            buf.writeVarInt(p.hidratacao());
                            buf.writeVarInt(p.vicio());
                            buf.writeUtf(p.drogaVicio(), 32);
                            buf.writeVarInt(p.estagioAbstinencia());
                            buf.writeVarInt(p.figado());
                            buf.writeVarInt(p.pulmao());
                            buf.writeVarInt(p.estomago());
                            buf.writeBoolean(p.abrirProntuario());
                            buf.writeVarInt(p.alcool());
                            buf.writeVarInt(p.erva());
                            buf.writeVarInt(p.po());
                            buf.writeVarInt(p.pilula());
                            buf.writeVarInt(p.curasSeguidas());
                        },
                        buf -> new SaudeSyncPayload(
                                buf.readVarInt(), buf.readVarInt(), buf.readUtf(32),
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt(), buf.readBoolean(),
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ==================================================== S2C: VIAGEM LIGADA

    public record ViagemPayload(String viagem, int intensidade, int segundos)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ViagemPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        IntoxicantesMod.MOD_ID, "saude_viagem"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ViagemPayload> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> {
                            ByteBufCodecs.STRING_UTF8.encode(buf, p.viagem());
                            buf.writeVarInt(p.intensidade());
                            buf.writeVarInt(p.segundos());
                        },
                        buf -> new ViagemPayload(
                                ByteBufCodecs.STRING_UTF8.decode(buf),
                                buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ==================================================== C2S: ABRIR O PRONTUARIO (tecla H)

    public record AbrirProntuarioPayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AbrirProntuarioPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        IntoxicantesMod.MOD_ID, "abrir_prontuario"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirProntuarioPayload> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> {},
                        buf -> new AbrirProntuarioPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ==================================================== REGISTRO + ENVIO

    public static void registrar() {
        PayloadTypeRegistry.clientboundPlay().register(SaudeSyncPayload.TYPE, SaudeSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ViagemPayload.TYPE, ViagemPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbrirProntuarioPayload.TYPE, AbrirProntuarioPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AbrirProntuarioPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> enviarSync(player, true));
        });
    }

    /** Mock player de game test não tem conexão: não há pra quem enviar. */
    private static boolean semConexao(ServerPlayer player) {
        return player.connection == null;
    }

    /** Sync periodico (fim do tick de saude): estado sem abrir tela. */
    static void enviarSync(ServerPlayer player) {
        enviarSync(player, false);
    }

    /** Sync com ou sem o pedido de abrir o prontuario. */
    static void enviarSync(ServerPlayer player, boolean abrirProntuario) {
        if (semConexao(player)) {
            return; // game test (mock player sem rede)
        }
        ServerPlayNetworking.send(player, new SaudeSyncPayload(
                SaudeData.hidratacao(player),
                SaudeData.nivelVicio(player),
                SaudeData.drogaVicio(player),
                SaudeSystem.estagioAbstinencia(player),
                SaudeData.danoFigado(player),
                SaudeData.danoPulmao(player),
                SaudeData.danoEstomago(player),
                abrirProntuario,
                SaudeData.dosesVida(player, "alcool"),
                SaudeData.dosesVida(player, "erva"),
                SaudeData.dosesVida(player, "po"),
                SaudeData.dosesVida(player, "pilula"),
                SaudeData.curasSeguidas(player)));
    }

    /** A viagem ligou: client desenha o overlay enquanto durar. */
    static void enviarViagem(ServerPlayer player, String viagem, int intensidade, int segundos) {
        if (semConexao(player)) {
            return; // game test (mock player sem rede)
        }
        ServerPlayNetworking.send(player, new ViagemPayload(viagem, intensidade, segundos));
    }
}
