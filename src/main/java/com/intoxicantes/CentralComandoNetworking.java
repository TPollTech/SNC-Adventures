package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * REDE DA CENTRAL DE COMANDO (v1.2.36) — o "controle remoto" do painel:
 * S2C {@code AbrirCentralPayload} manda o estado atual (texto, cor, brilho,
 * modo, travado) e o client abre a tela; C2S {@code AplicarCentralPayload}
 * devolve a edição, que o SERVIDOR valida de novo (alcance, travas,
 * tamanho) antes de gravar no block entity. Mesma disciplina do cardápio:
 * o client é só pintura — regra de negócio é do servidor.
 */
public final class CentralComandoNetworking {

    private CentralComandoNetworking() {}

    // ==================================================== REGISTRO

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(
                AbrirCentralPayload.TYPE, AbrirCentralPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                AplicarCentralPayload.TYPE, AplicarCentralPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AplicarCentralPayload.TYPE,
                (payload, ctx) -> aplicar(ctx.player(), payload));
    }

    // ==================================================== ABRIR (server -> client)

    /** Manda o estado do painel/letreiro e abre a Central no client. */
    public static void abrir(ServerPlayer player, PlacaEsquinaoBlockEntity be) {
        List<String> linhas = new ArrayList<>(be.getLinhas());
        ServerPlayNetworking.send(player, new AbrirCentralPayload(
                true, be.getBlockPos(), linhas, be.getCor(), 15,
                0, be.isLinkMercado(), be.isTrancada()));
    }

    /** Overload pro painel craftável (brilho/modo próprios). */
    public static void abrir(ServerPlayer player, PainelLedBlockEntity be) {
        ServerPlayNetworking.send(player, new AbrirCentralPayload(
                false, be.getBlockPos(), new ArrayList<>(be.getLinhas()),
                be.getCor(), be.getBrilho(), be.getModo(), false, false));
    }

    // ==================================================== APLICAR (client -> server)

    private static void aplicar(ServerPlayer player, AplicarCentralPayload p) {
        if (!p.pos().closerToCenterThan(player.position(), 8.0)) return; // alcançe
        var level = player.level();
        if (level.getBlockEntity(p.pos()) instanceof PlacaEsquinaoBlockEntity be) {
            // letreiro do mercado: precisa de chave se estiver trancado.
            // v1.2.71: a chave vale na PRINCIPAL OU NA SECUNDÁRIA — a abertura
            // já aceitava a chave na mão secundária (o controle ocupa a
            // principal), mas o aplicar exigia na principal: com o controle
            // na mão, um letreiro trancado nunca era editável (o aplicava
            // falhava silencioso — o "controle remoto não funciona").
            if (be.isLinkMercado() && be.isTrancada()
                    && !temChave(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "block.intoxicantes.placa_esquinao.trancada"));
                return;
            }
            be.setLinhas(p.linhas());
            be.setCor(p.cor());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.5F);
        } else if (level.getBlockEntity(p.pos()) instanceof PainelLedBlockEntity be) {
            be.aplicar(p.linhas(), p.cor(), p.brilho(), p.modo());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.5F);
        }
    }

    /** A CHAVE (comparador) na principal OU na secundária — o controle vive na principal. */
    private static boolean temChave(ServerPlayer player) {
        return player.getMainHandItem().is(net.minecraft.world.item.Items.COMPARATOR)
                || player.getOffhandItem().is(net.minecraft.world.item.Items.COMPARATOR);
    }

    // ==================================================== PAYLOADS

    /** S2C: estado completo do painel pra Central desenhar. */
    public record AbrirCentralPayload(boolean letreiro, BlockPos pos,
            List<String> linhas, int cor, int brilho, int modo,
            boolean vinculado, boolean trancado) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AbrirCentralPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        "intoxicantes", "abrir_central"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirCentralPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AbrirCentralPayload::escrever, AbrirCentralPayload::ler);

        private static void escrever(AbrirCentralPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(p.letreiro());
            buf.writeBlockPos(p.pos());
            ByteBufCodecs.VAR_INT.encode(buf, p.linhas().size());
            for (String l : p.linhas()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, l);
            }
            buf.writeInt(p.cor());
            buf.writeByte(p.brilho());
            buf.writeByte(p.modo());
            buf.writeBoolean(p.vinculado());
            buf.writeBoolean(p.trancado());
        }

        private static AbrirCentralPayload ler(RegistryFriendlyByteBuf buf) {
            boolean letreiro = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n < 0 || n > 4) throw new IllegalArgumentException("Central line count");
            List<String> linhas = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                linhas.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return new AbrirCentralPayload(letreiro, pos, linhas, buf.readInt(),
                    buf.readUnsignedByte(), buf.readUnsignedByte(),
                    buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: a Central aplicou o texto/cor/brilho/modo. */
    public record AplicarCentralPayload(BlockPos pos, List<String> linhas,
            int cor, int brilho, int modo) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AplicarCentralPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        "intoxicantes", "aplicar_central"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AplicarCentralPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AplicarCentralPayload::escrever, AplicarCentralPayload::ler);

        private static void escrever(AplicarCentralPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(p.pos());
            ByteBufCodecs.VAR_INT.encode(buf, p.linhas().size());
            for (String l : p.linhas()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, l);
            }
            buf.writeInt(p.cor());
            buf.writeByte(p.brilho());
            buf.writeByte(p.modo());
        }

        private static AplicarCentralPayload ler(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n < 0 || n > 4) throw new IllegalArgumentException("Central line count");
            List<String> linhas = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                linhas.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return new AplicarCentralPayload(pos, linhas, buf.readInt(),
                    buf.readUnsignedByte(), buf.readUnsignedByte());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
