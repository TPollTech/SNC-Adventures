package com.intoxicantes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.intoxicantes.energia.CabosSuspensos;
import com.intoxicantes.energia.EloCabo;

/**
 * v1.2.80 — A REDE DOS CABOS SUSPENSOS: o servidor manda ao cliente só os
 * elos visíveis do jogador (nada de varrer o mundo no cliente: quem sabe o
 * que existe é o SavedData da dimensão). O envio acontece 1×/s junto da
 * medição da rede e só quando a fatia muda — o hash evita repetir o mesmo
 * pacote enquanto o jogador anda no mesmo trecho.
 */
public final class CabosNetworking {

    /** Raio de envio (o cliente desenha o que está perto, como o chunk render). */
    public static final double RAIO_CLIENTE = 48.0;
    /** Teto de elos por pacote (defensivo: o cliente nunca recebe lixo). */
    private static final int MAX_POR_PACOTE = 2048;

    private static final Map<UUID, Integer> ultimoEnvio = new HashMap<>();

    private CabosNetworking() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CabosPayload.TYPE,
                CabosPayload.STREAM_CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ultimoEnvio.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> esquecer(handler.player.getUUID()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> esquecer(handler.player.getUUID()));
    }

    /** Esquece a assinatura do jogador que saiu (a memória não vaza). */
    public static void esquecer(java.util.UUID jogador) {
        ultimoEnvio.remove(jogador);
    }

    /** Envia a fatia visível de cada jogador do nível (1×/s). */
    public static void sincronizar(ServerLevel level) {
        sincronizar(level, false);
    }

    /**
     * Envio na hora de uma mudança na lista (elo criado ou recolhido): o
     * cabo tem que aparecer NO MESMO CLIQUE, não até 1 s depois. O cadência
     * de 1×/s continua cuidando do jogador andando pela instalação.
     */
    public static void sincronizarAgora(ServerLevel level) {
        sincronizar(level, true);
    }

    private static void sincronizar(ServerLevel level, boolean forcar) {
        CabosSuspensos cabos = CabosSuspensos.get(level);
        for (ServerPlayer player : level.players()) {
            List<EloCabo> visiveis = cabos.elosVisiveis(player.blockPosition(), RAIO_CLIENTE);
            if (visiveis.size() > MAX_POR_PACOTE) {
                visiveis = visiveis.subList(0, MAX_POR_PACOTE);
            }
            int assinatura = 31 * level.dimension().hashCode() + assinatura(visiveis);
            if (!forcar && ultimoEnvio.getOrDefault(player.getUUID(), Integer.MIN_VALUE) == assinatura) {
                continue;
            }
            ultimoEnvio.put(player.getUUID(), assinatura);
            ServerPlayNetworking.send(player, new CabosPayload(level.dimension(), List.copyOf(visiveis)));
        }
    }

    /** Assinatura barata do conjunto (muda quando um elo entra ou sai). */
    private static int assinatura(List<EloCabo> elos) {
        int hash = 1;
        for (EloCabo elo : elos) {
            hash = hash * 31 + elo.hashCode();
        }
        return hash;
    }

    /** S2C: os elos visíveis do jogador (o client desenha a curva). */
    public record CabosPayload(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensao,
            List<EloCabo> elos) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<CabosPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        "intoxicantes", "cabos"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CabosPayload> STREAM_CODEC =
                CustomPacketPayload.codec(CabosPayload::escrever, CabosPayload::ler);

        private static void escrever(CabosPayload p, RegistryFriendlyByteBuf buf) {
            net.minecraft.resources.ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION)
                    .encode(buf, p.dimensao());
            ByteBufCodecs.VAR_INT.encode(buf, p.elos().size());
            for (EloCabo elo : p.elos()) {
                buf.writeBlockPos(elo.a());
                buf.writeByte(elo.faceA().ordinal());
                buf.writeBlockPos(elo.b());
                buf.writeByte(elo.faceB().ordinal());
                buf.writeFloat((float) elo.bitola());
            }
        }

        private static CabosPayload ler(RegistryFriendlyByteBuf buf) {
            var dimensao = net.minecraft.resources.ResourceKey.streamCodec(
                    net.minecraft.core.registries.Registries.DIMENSION).decode(buf);
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n < 0 || n > MAX_POR_PACOTE) {
                throw new IllegalArgumentException("Cabos payload size");
            }
            List<EloCabo> elos = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                BlockPos a = buf.readBlockPos();
                Direction faceA = direcao(buf.readUnsignedByte());
                BlockPos b = buf.readBlockPos();
                Direction faceB = direcao(buf.readUnsignedByte());
                elos.add(new EloCabo(a, faceA, b, faceB, buf.readFloat()));
            }
            return new CabosPayload(dimensao, List.copyOf(elos));
        }

        private static Direction direcao(int ordinal) {
            Direction[] valores = Direction.values();
            return valores[Math.floorMod(ordinal, valores.length)];
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}