package com.intoxicantes;

import com.google.gson.Gson;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** Sessão criada por interação real; o cliente nunca informa resultado ou prêmio. */
public final class PeruNetworking {
    private PeruNetworking() {}
    public static final Gson JSON = new Gson();
    private static final Map<UUID, Sessao> sessoes = new HashMap<>();
    private static final class Sessao {
        final UUID token, peru;
        final ServerLevel nivel;
        long expira, ultimaAcao;
        Sessao(UUID token, UUID peru, ServerLevel nivel, long agora) {
            this.token = token; this.peru = peru; this.nivel = nivel;
            expira = agora + 200; ultimaAcao = agora - 3;
        }
    }
    public record Estado(UUID sessao, boolean abrir, boolean valido, String mensagem,
            long tempoMundo, JogoDoBicho.Snapshot dados) implements CustomPacketPayload {
        public static final Type<Estado> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "peru_estado"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Estado> CODEC = new StreamCodec<>() {
            @Override public Estado decode(RegistryFriendlyByteBuf b) {
                return JSON.fromJson(b.readUtf(30000), Estado.class);
            }
            @Override public void encode(RegistryFriendlyByteBuf b, Estado p) { b.writeUtf(JSON.toJson(p), 30000); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** ação 0 atualizar, 1 confirmar aposta, 2 fechar. */
    public record Acao(UUID sessao, UUID pedido, int acao, int tipo, int grupo, int dezena, int valor,
                       long dia, int horario, int multiplicador)
            implements CustomPacketPayload {
        public static final Type<Acao> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "peru_acao"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Acao> CODEC = new StreamCodec<>() {
            @Override public Acao decode(RegistryFriendlyByteBuf b) {
                return new Acao(b.readUUID(), b.readUUID(), b.readVarInt(), b.readVarInt(),
                        b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong(), b.readVarInt(), b.readVarInt());
            }
            @Override public void encode(RegistryFriendlyByteBuf b, Acao p) {
                b.writeUUID(p.sessao); b.writeUUID(p.pedido); b.writeVarInt(p.acao); b.writeVarInt(p.tipo);
                b.writeVarInt(p.grupo); b.writeVarInt(p.dezena); b.writeVarInt(p.valor);
                b.writeLong(p.dia); b.writeVarInt(p.horario); b.writeVarInt(p.multiplicador);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Estado.TYPE, Estado.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Acao.TYPE, Acao.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Acao.TYPE,
                (p, ctx) -> ctx.server().execute(() -> agir(ctx.player(), p)));
    }
    public static void reset() { sessoes.clear(); }
    public static boolean negociando(UUID peru) {
        return sessoes.values().stream().anyMatch(s -> s.peru.equals(peru));
    }
    public static void abrir(ServerPlayer player, PeruEntity peru) {
        if (!player.isAlive() || player.isSpectator() || !peru.isAlive()
                || player.level() != peru.level() || player.distanceToSqr(peru) > 36
                || !player.hasLineOfSight(peru)) return;
        Sessao s = new Sessao(UUID.randomUUID(), peru.getUUID(), player.level(),
                player.level().getServer().overworld().getGameTime());
        sessoes.put(player.getUUID(), s);
        enviar(player, s, true, true, "");
    }
    private static PeruEntity peru(ServerPlayer player, Sessao s) {
        if (player.level() != s.nivel) return null;
        var entity = player.level().getEntity(s.peru);
        return entity instanceof PeruEntity p && p.isAlive() && player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(p) <= 64 && player.hasLineOfSight(p) ? p : null;
    }
    private static void agir(ServerPlayer player, Acao p) {
        Sessao s = sessoes.get(player.getUUID());
        if (s == null || !s.token.equals(p.sessao)) return;
        if (p.acao == 2) { sessoes.remove(player.getUUID()); return; }
        long now = player.level().getServer().overworld().getGameTime();
        if (now > s.expira || peru(player, s) == null) {
            enviar(player, s, false, false, "fora_alcance"); sessoes.remove(player.getUUID()); return;
        }
        if (p.acao != 0 && p.acao != 1) return;
        s.expira = now + 200;
        if (p.acao == 0) return;
        if (p.acao != 1 || now - s.ultimaAcao < 3) return;
        s.ultimaAcao = now;
        if (p.tipo < 0 || p.tipo > 1) { enviar(player, s, false, true, "invalida"); return; }
        JogoDoBicho.Snapshot atual = JogoDoBicho.snapshot(player.level().getServer(), player.getUUID());
        JogoDoBicho.Aposta anterior = atual.apostas().stream().filter(a -> a.id().equals(p.pedido)).findFirst().orElse(null);
        long dia = anterior == null ? atual.proximoDia() : anterior.dia();
        int horario = anterior == null ? atual.horarioSorteio() : anterior.horarioSorteio();
        int multiplicador = anterior != null ? anterior.multiplicador()
                : p.tipo == 0 ? atual.multiplicadorGrupo() : atual.multiplicadorDezena();
        // Confirmar a ficha das 17:59 às 18:00 não autoriza mover o bilhete para outro dia.
        // Um pedido já registrado conserva a ficha original e pode ser repetido sem novo débito.
        if (p.dia != dia || p.horario != horario || p.multiplicador != multiplicador) {
            enviar(player, s, false, true, "sorteio_mudou"); return;
        }
        JogoDoBicho.Resposta r = JogoDoBicho.apostar(player.level().getServer(), p.pedido,
                player.getUUID(), JogoDoBicho.Tipo.values()[p.tipo], p.grupo, p.dezena, p.valor);
        enviar(player, s, false, true, r.status().name().toLowerCase(Locale.ROOT));
    }
    private static void enviar(ServerPlayer p, Sessao s, boolean abrir, boolean valido, String mensagem) {
        ServerPlayNetworking.send(p, new Estado(s.token, abrir, valido, mensagem,
                CalendarioEsquinao.tempoTotal(p.level().getServer().overworld()),
                JogoDoBicho.snapshot(p.level().getServer(), p.getUUID())));
    }
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20 != 0) return;
        Iterator<Map.Entry<UUID, Sessao>> it = sessoes.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next(); ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            Sessao s = entry.getValue();
            if (p == null) { it.remove(); continue; }
            boolean valido = server.overworld().getGameTime() <= s.expira && peru(p, s) != null;
            enviar(p, s, false, valido, valido ? "" : "fora_alcance");
            if (!valido) it.remove();
        }
    }
}
