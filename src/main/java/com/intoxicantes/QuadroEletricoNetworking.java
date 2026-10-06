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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.regex.Pattern;

import com.intoxicantes.energia.CircuitoEletrico;

/**
 * v1.2.71 — REDE DA GUI DO QUADRO ELÉTRICO (módulo 3). Mesma disciplina da
 * Central de Comando: S2C {@code AbrirQuadroPayload} manda o ESTADO COMPLETO
 * (entrada + os 4 circuitos) e o client abre a tela; C2S
 * {@code AcaoQuadroPayload} devolve a ação, que o SERVIDOR revalida (alcance,
 * índice) antes de gravar na BE. Depois de cada ação o servidor reenvia o
 * estado — a tela fica viva sem o client adivinhar nada.
 *
 * Ações: 0 = liga/desliga o circuito (alavanca), 1 = rearma o desarmado,
 * 2 = cicla a amperagem do disjuntor, 3 = alterna a tensão 127/220,
 * 4 = renomeia o circuito, 5 = fecha a tampa (o ESC da tela avisa).
 */
public final class QuadroEletricoNetworking {

    /** Tamanho máximo do nome de circuito (etiqueta do quadro). */
    public static final int MAX_NOME = 20;
    private static final Pattern TEXTO_CONTROLE = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private record Observador(net.minecraft.server.level.ServerLevel level, BlockPos pos) {}
    private static final java.util.Map<java.util.UUID, Observador> observadores = new java.util.HashMap<>();

    private QuadroEletricoNetworking() {}

    // ==================================================== REGISTRO

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> observadores.clear());
        PayloadTypeRegistry.clientboundPlay().register(
                AbrirQuadroPayload.TYPE, AbrirQuadroPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                AcaoQuadroPayload.TYPE, AcaoQuadroPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AcaoQuadroPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(
                        () -> aplicar(ctx.player(), payload)));
    }

    // ==================================================== ABRIR (server -> client)

    /** Manda o estado completo do quadro e abre a GUI no client. */
    public static void abrir(ServerPlayer player,
            QuadroEletricoBlock.QuadroEletricoBlockEntity quadro) {
        observadores.put(player.getUUID(), new Observador(player.level(), quadro.getBlockPos()));
        ServerPlayNetworking.send(player, AbrirQuadroPayload.doQuadro(quadro, true));
    }

    /** Atualiza a medição na tela aberta, sem reabrir nem perder a etiqueta em edição. */
    public static void atualizarObservadores(QuadroEletricoBlock.QuadroEletricoBlockEntity quadro) {
        if (!(quadro.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var it = observadores.entrySet().iterator();
        while (it.hasNext()) {
            var entrada = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entrada.getKey());
            if (player == null || !player.isAlive() || player.level() != entrada.getValue().level()
                    || !entrada.getValue().pos().closerToCenterThan(player.position(), 8.0)) {
                it.remove();
                continue;
            }
            if (entrada.getValue().level() == level && entrada.getValue().pos().equals(quadro.getBlockPos()))
                ServerPlayNetworking.send(player, AbrirQuadroPayload.doQuadro(quadro, false));
        }
    }

    // ==================================================== AÇÕES (client -> server)

    private static void aplicar(ServerPlayer player, AcaoQuadroPayload p) {
        if (!player.isAlive() || player.isSpectator() || !player.level().isLoaded(p.pos())) return;
        if (!p.pos().closerToCenterThan(player.position(), 8.0)) {
            return;   // alcance (mesma régua da Central)
        }
        var level = player.level();
        if (!(level.getBlockEntity(p.pos())
                instanceof QuadroEletricoBlock.QuadroEletricoBlockEntity quadro)) {
            return;
        }
        if (player.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5,
                p.pos().getZ() + 0.5) > 64.0) {
            return;
        }
        var circuitos = quadro.circuitos();
        int i = p.indice();
        boolean mexeu = false;
        switch (p.acao()) {
            case 0 -> {   // alavanca: LIGADO <-> DESLIGADO (desarmado exige rearme)
                if (i >= 0 && i < circuitos.size()) {
                    CircuitoEletrico c = circuitos.get(i);
                    if (c.estado == CircuitoEletrico.DESARMADO) {
                        mexeu = c.rearma();
                    } else {
                        c.estado = (c.estado == CircuitoEletrico.LIGADO)
                                ? CircuitoEletrico.DESLIGADO
                                : CircuitoEletrico.LIGADO;
                        mexeu = true;
                    }
                }
            }
            case 1 -> {   // rearme manual
                if (i >= 0 && i < circuitos.size()) {
                    mexeu = circuitos.get(i).rearma();
                }
            }
            case 2 -> {   // cicla a amperagem (10/16/20/25/32/40)
                if (i >= 0 && i < circuitos.size()) {
                    CircuitoEletrico c = circuitos.get(i);
                    c.disjuntorAmperes = proximaAmperagem(c.disjuntorAmperes);
                    mexeu = true;
                }
            }
            case 3 -> {   // tensão 127 <-> 220
                if (i >= 0 && i < circuitos.size()) {
                    CircuitoEletrico c = circuitos.get(i);
                    c.tensao = (c.tensao == com.intoxicantes.energia.Grandezas.V_220)
                            ? com.intoxicantes.energia.Grandezas.V_127
                            : com.intoxicantes.energia.Grandezas.V_220;
                    mexeu = true;
                }
            }
            case 4 -> {   // renomeia (etiqueta do circuito)
                if (i >= 0 && i < circuitos.size() && p.texto() != null) {
                    String nome = p.texto().trim();
                    nome = TEXTO_CONTROLE.matcher(nome).replaceAll("").trim();
                    if (nome.length() > MAX_NOME) {
                        nome = nome.substring(0, MAX_NOME);
                    }
                    if (!nome.isEmpty()) {
                        circuitos.get(i).nome = nome;
                        mexeu = true;
                    }
                }
            }
            case 5 -> {   // fechar: abaixa a tampa
                observadores.remove(player.getUUID());
                if (level.getBlockState(p.pos()).hasProperty(
                        QuadroEletricoBlock.ABERTO)) {
                    level.setBlock(p.pos(), level.getBlockState(p.pos())
                            .setValue(QuadroEletricoBlock.ABERTO, false),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
                return;
            }
            default -> { }
        }
        if (mexeu) {
            quadro.reconstruirTopologia();
            quadro.setChanged();
            level.playSound(null, p.pos(), SoundEvents.LEVER_CLICK,
                    SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        // estado fresco de volta (a tela fica viva)
        abrir(player, quadro);
    }

    private static long proximaAmperagem(long atual) {
        long[] as = CircuitoEletrico.AMPERAGENS;
        for (int i = 0; i < as.length; i++) {
            if (as[i] == atual) {
                return as[(i + 1) % as.length];
            }
        }
        return as[0];
    }

    // ==================================================== PAYLOADS

    /** S2C: o estado completo do quadro (entrada + circuitos) pra GUI desenhar. */
    public record AbrirQuadroPayload(BlockPos pos, boolean abrirTela, int fontes,
            long bufferE, long capacidadeBufferE, long disponivelE,
            long consumoTotalW,
            List<CircuitoSnapshot> circuitos) implements CustomPacketPayload {

        /** A fatia de um circuito que a GUI precisa. */
        public record CircuitoSnapshot(String nome, long disjuntorA, long tensao,
                int estado, long consumoW, long limiteBitolaA, long totalE) {}

        public static final CustomPacketPayload.Type<AbrirQuadroPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        "intoxicantes", "abrir_quadro"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirQuadroPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AbrirQuadroPayload::escrever, AbrirQuadroPayload::ler);

        private static AbrirQuadroPayload doQuadro(
                QuadroEletricoBlock.QuadroEletricoBlockEntity quadro, boolean abrirTela) {
            List<CircuitoSnapshot> cs = new java.util.ArrayList<>();
            var todos = quadro.circuitos();
            for (int i = 0; i < todos.size(); i++) {
                CircuitoEletrico c = todos.get(i);
                cs.add(new CircuitoSnapshot(c.nome, c.disjuntorAmperes, c.tensao,
                        c.estado, c.consumoW(), quadro.limiteBitolaA(i),
                        c.totalConsumidoE));
            }
            return new AbrirQuadroPayload(quadro.getBlockPos(), abrirTela,
                    quadro.fontes().size(), quadro.buffer().getEnergy(),
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.CAPACIDADE_BUFFER,
                    quadro.disponivelE(), quadro.consumoW(), cs);
        }

        private static void escrever(AbrirQuadroPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(p.pos());
            buf.writeBoolean(p.abrirTela());
            buf.writeVarInt(p.fontes());
            buf.writeLong(p.bufferE());
            buf.writeLong(p.capacidadeBufferE());
            buf.writeLong(p.disponivelE());
            buf.writeLong(p.consumoTotalW());
            ByteBufCodecs.VAR_INT.encode(buf, p.circuitos().size());
            for (CircuitoSnapshot c : p.circuitos()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, c.nome().substring(0,
                        Math.min(c.nome().length(), MAX_NOME)));
                buf.writeLong(c.disjuntorA());
                buf.writeLong(c.tensao());
                buf.writeByte(c.estado());
                buf.writeLong(c.consumoW());
                buf.writeLong(c.limiteBitolaA());
                buf.writeLong(c.totalE());
            }
        }

        private static AbrirQuadroPayload ler(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean abrirTela = buf.readBoolean();
            int fontes = buf.readVarInt();
            long bufferE = buf.readLong();
            long capacidade = buf.readLong();
            long disponivel = buf.readLong();
            long consumo = buf.readLong();
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n != 4) throw new IllegalArgumentException("Quadro circuit count");
            List<CircuitoSnapshot> cs = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                cs.add(new CircuitoSnapshot(ByteBufCodecs.STRING_UTF8.decode(buf),
                        buf.readLong(), buf.readLong(), buf.readUnsignedByte(),
                        buf.readLong(), buf.readLong(), buf.readLong()));
            }
            return new AbrirQuadroPayload(pos, abrirTela, fontes, bufferE, capacidade,
                    disponivel, consumo, cs);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: a GUI mandou uma ação (alavanca/rearme/disjuntor/tensão/nome/fechar). */
    public record AcaoQuadroPayload(BlockPos pos, int acao, int indice,
            String texto) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AcaoQuadroPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                        "intoxicantes", "acao_quadro"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AcaoQuadroPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AcaoQuadroPayload::escrever, AcaoQuadroPayload::ler);

        private static void escrever(AcaoQuadroPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(p.pos());
            buf.writeByte(Math.clamp(p.acao(), 0, 5));
            buf.writeByte(Math.clamp(p.indice(), 0, 3));
            ByteBufCodecs.STRING_UTF8.encode(buf,
                    p.texto() == null ? "" : p.texto().substring(0,
                            Math.min(p.texto().length(), MAX_NOME)));
        }

        private static AcaoQuadroPayload ler(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int acao = buf.readUnsignedByte();
            int indice = buf.readUnsignedByte();
            String texto = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new AcaoQuadroPayload(pos, acao, indice, texto);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
