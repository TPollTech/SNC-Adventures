package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Payload C2S da RECARGA POR TECLA (v1.2.41): o jogador aperta R com a 12 ou
 * o .38 na mão e o client avisa o servidor. O servidor decide o que a tecla
 * faz (nunca confie no client):
 *
 * <ul>
 *   <li><b>aperto (pressionar=true):</b> com mecanismo PRONTO, tubo/tambor com
 *       espaço e reserva: entra na recarga shell-by-shell (o mesmo caminho do
 *       botão direito, incluindo a pose de operação manual);</li>
 *   <li><b>soltar (pressionar=false):</b> se estava RECARREGANDO via tecla,
 *       interrompe — o que entrou, ficou (igual soltar o botão direito).</li>
 * </ul>
 *
 * Segurança: o servidor valida tudo de novo (fase, espaço, reserva, item na
 * mão). Client malicioso só consegue o mesmo que o uso normal permite.
 */
public record RecargaPayload(boolean pressionar) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecargaPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "recarga_arma"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecargaPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (p, buf) -> buf.writeBoolean(p.pressionar()),
                    buf -> new RecargaPayload(buf.readBoolean()));

    /** Registra no canal de jogo (chamado do init do mod). */
    public static void registrar() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> despachar(player, payload.pressionar()));
        });
    }

    /**
     * Ponto de entrada dos GAME TESTS: mesmo caminho do servidor (despachar),
     * sem precisar de conexão de rede. Package-private de propósito.
     */
    static void despacharTeste(ServerPlayer player, boolean pressionar) {
        despachar(player, pressionar);
    }

    /** Encontra a arma e executa a ação da tecla (validação 100% servidor). */
    private static void despachar(ServerPlayer player, boolean pressionar) {
        net.minecraft.world.InteractionHand maoArma = null;
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack na = player.getItemInHand(hand);
            if (na.is(IntoxicantesMod.ESCOPETA) || na.is(IntoxicantesMod.REVOLVER)) {
                maoArma = hand;
                break;
            }
        }
        if (maoArma == null) {
            return; // sem arma de fogo: a tecla é inerte (não rouba uso de nada)
        }
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(maoArma);

        if (stack.is(IntoxicantesMod.ESCOPETA)) {
            EscopetaItem.recarregarViaTecla(player, maoArma, pressionar);
        } else {
            RevolverItem.recarregarViaTecla(player, maoArma, pressionar);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================================================== S2C: STATUS PRA HUD

    /**
     * v1.2.41 — S2C: o servidor avisa o client que a recarga por TECLA começou
     * ou terminou (o HUD pinta o texto em verde enquanto a tecla comanda).
     */
    public record TipoStatus(boolean ativa, String arma) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<TipoStatus> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        IntoxicantesMod.MOD_ID, "recarga_status"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TipoStatus> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> {
                            buf.writeBoolean(p.ativa());
                            buf.writeUtf(p.arma(), 16);
                        },
                        buf -> new TipoStatus(buf.readBoolean(), buf.readUtf(16)));

        /** Servidor manda o status pro dono da arma. */
        public static void mandar(ServerPlayer player, boolean ativa, String arma) {
            ServerPlayNetworking.send(player, new TipoStatus(ativa, arma));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registra o canal S2C do status (chamado junto do registrar()). */
    public static void registrarStatus() {
        PayloadTypeRegistry.clientboundPlay().register(TipoStatus.TYPE, TipoStatus.STREAM_CODEC);
    }
}
