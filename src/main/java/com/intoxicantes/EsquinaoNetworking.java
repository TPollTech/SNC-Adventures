package com.intoxicantes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Rede do Cardapio do Mercado Esquinao.
 *
 * O Gago NAO usa a UI de vilarejo vanilla: clique-direito abre uma tela propria
 * (EsquinaoCardapioScreen) alimentada por payload S2C; as compras voltam por
 * payload C2S e sao validadas SEMPRE no servidor (saldo, distancia, sessao).
 *
 * Sessao: enquanto a tela esta aberta, o player esta "em atendimento" no Gago
 * (setTradingPlayer — o Gago congela a IA). Um watchdog derruba a sessao se o
 * fregues correr longe, trocar de dimensao ou se o Gago morrer.
 */
public final class EsquinaoNetworking {
    /** Sessoes de atendimento abertas: UUID do fregues -> Gago que atende. */
    private static final Map<UUID, GagoEntity> SESSOES = new HashMap<>();
    /** v1.2.44: sessoes do PONTO DO TRAFICANTE (tela propria, mesmo esquema). */
    private static final Map<UUID, TraficanteEntity> SESSOES_T = new HashMap<>();

    private EsquinaoNetworking() {}

    // ==================================================== REGISTRO (common init: roda nos 2 lados)

    public static void register() {
        // Codecs (o registro e global na JVM: vale pros dois lados logados)
        PayloadTypeRegistry.clientboundPlay().register(
                AbrirCardapioPayload.TYPE, AbrirCardapioPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ComprarPayload.TYPE, ComprarPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                FecharCardapioPayload.TYPE, FecharCardapioPayload.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(
                FecharCardapioPayload.TYPE, FecharCardapioPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VenderPayload.TYPE, VenderPayload.STREAM_CODEC);
        // v1.2.51: UM receiver só, que ROTEIA pela sessão do jogador. Antes havia
        // dois registerGlobalReceiver para o mesmo payload (Mercado e Ponto do
        // traficante) — o segundo SOBRESCREVIA o primeiro no Fabric, e o
        // Buy/Sell do Mercado ia pro handler do traficante, que ignorava
        // silenciosamente (Purchase ficava em 0 pra sempre).
        ServerPlayNetworking.registerGlobalReceiver(ComprarPayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    if (SESSOES.containsKey(player.getUUID())) {
                        negociar(player, payload.indice(), false);
                    } else {
                        comprarNoTraficante(player, payload.indice());
                    }
                });
        ServerPlayNetworking.registerGlobalReceiver(VenderPayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    if (SESSOES.containsKey(player.getUUID())) {
                        negociar(player, payload.indice(), true);
                    } else {
                        venderNoTraficante(player, payload.indice());
                    }
                });

        // v1.2.44 — O PONTO DO TRAFICANTE (tela propria dele)
        PayloadTypeRegistry.clientboundPlay().register(
                AbrirPontoPayload.TYPE, AbrirPontoPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                DestrancoPayload.TYPE, DestrancoPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                FiadoPayload.TYPE, FiadoPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                DiazinhoPayload.TYPE, DiazinhoPayload.STREAM_CODEC);
        // v1.2.51: os receivers de Comprar/Vender do PONTO ficam no roteador
        // único lá em cima (registrar o mesmo TYPE duas vezes sobrescrevia o
        // receiver do Mercado).
        ServerPlayNetworking.registerGlobalReceiver(DestrancoPayload.TYPE,
                (payload, ctx) -> {
                    TraficanteEntity t = SESSOES_T.get(ctx.player().getUUID());
                    if (t != null) t.largarAtendimento();
                    ingressarTraf(ctx.player(), t);
                });
        ServerPlayNetworking.registerGlobalReceiver(FiadoPayload.TYPE,
                (payload, ctx) -> {
                    TraficanteEntity t = SESSOES_T.get(ctx.player().getUUID());
                    if (t != null) {
                        t.emprestarFiado(ctx.player());
                        // v1.2.71: o saldo/divida NO RÓTULO atualiza NA HORA
                        // (bug do playtest: o valor na tela só mudava ao
                        // fechar e reabrir o ponto)
                        enviarPonto(ctx.player(), t, false);
                    }
                });
        ServerPlayNetworking.registerGlobalReceiver(DiazinhoPayload.TYPE,
                (payload, ctx) -> {
                    TraficanteEntity t = SESSOES_T.get(ctx.player().getUUID());
                    if (t != null) {
                        t.pagarDivida(ctx.player());
                        enviarPonto(ctx.player(), t, false); // idem: saldo na hora
                    }
                });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSOES.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> encerrarSessao(handler.player));

        // v1.2.44: o C2S 'fechar' também desmonta a sessão do traficante
        ServerPlayNetworking.registerGlobalReceiver(FecharCardapioPayload.TYPE,
                (payload, ctx) -> encerrarSessao(ctx.player()));

        // Watchdog: fregues longe demais / mudou de dimensao / Gago morreu
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) {
                return; // a cada meio segundo basta
            }
            // v1.2.44: watchdog das sessoes do traficante (mesma regra do Gago:
            // vivo, perto, mesma dimensao, ainda em atendimento)
            List<UUID> encerradasT = null;
            for (Map.Entry<UUID, TraficanteEntity> sessao : SESSOES_T.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(sessao.getKey());
                TraficanteEntity traficante = sessao.getValue();
                boolean valida = player != null && traficante != null && traficante.isAlive()
                        && !traficante.isRemoved() && player.isAlive()
                        && player.level() == traficante.level()
                        && traficante.getTradingPlayer() == player
                        && player.distanceToSqr(traficante) <= 6.0 * 6.0;
                if (valida) {
                    continue;
                }
                if (encerradasT == null) {
                    encerradasT = new ArrayList<>();
                }
                encerradasT.add(sessao.getKey());
                if (player != null) {
                    ServerPlayNetworking.send(player, new FecharCardapioPayload());
                }
                if (traficante != null && traficante.getTradingPlayer() != null
                        && traficante.getTradingPlayer().getUUID().equals(sessao.getKey())) {
                    traficante.largarAtendimento();
                }
            }
            if (encerradasT != null) {
                for (UUID uuid : encerradasT) {
                    SESSOES_T.remove(uuid);
                }
            }
            List<UUID> encerradas = null;
            for (Map.Entry<UUID, GagoEntity> sessao : SESSOES.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(sessao.getKey());
                GagoEntity gago = sessao.getValue();
                boolean valida = player != null && gago != null && gago.isAlive()
                        && !gago.isRemoved() && !gago.isPuto() && player.isAlive()
                        && player.level() == gago.level()
                        && gago.getTradingPlayer() == player
                        && player.distanceToSqr(gago) <= 6.0 * 6.0;
                if (valida) {
                    continue;
                }
                if (encerradas == null) {
                    encerradas = new ArrayList<>();
                }
                encerradas.add(sessao.getKey());
                if (player != null) {
                    // v1.2.14: pro lang (era pt cravado)
                    player.sendSystemMessage(Component.translatable(
                            "commerce.intoxicantes.sessao.encerrada"));
                    ServerPlayNetworking.send(player, new FecharCardapioPayload());
                }
                if (gago != null && gago.getTradingPlayer() != null
                        && gago.getTradingPlayer().getUUID().equals(sessao.getKey())) {
                    gago.encerrarAtendimento();
                }
            }
            if (encerradas != null) {
                for (UUID uuid : encerradas) {
                    SESSOES.remove(uuid);
                }
            }
        });
    }

    // ==================================================== PAYLOADS

    /**
     * S2C: estado completo do PONTO DO TRAFICANTE (v1.2.44; v1.2.66 + cru de
     * droga; v1.2.71 + PREÇOS REAIS do estoque).
     *
     * v1.2.71 — O RÓTULO É A COBRANÇA (bug do playtest: comprava seda por 10
     * e a tela dizia 12): o payload antigo NÃO carregava o preço dos 7
     * produtos — a tela usava tabela fixa, enquanto a cobrança aplica a
     * cotação do dia + descontos (fila/fidelidade). Agora `precos` vem do
     * MESMO cálculo do TraficanteEntity.precoDe (ordem = traficanteFixo) —
     * o que o freguês vê é o que sai da carteira.
     */
    public record AbrirPontoPayload(boolean abrir, int estoqueMaconha, int estoqueCocaina,
            int estoqueHeroina, int estoqueLsd, int estoqueBaseado, int estoqueOpio,
            int estoqueExtrato, int desconto, int estoqueDiamante, int fiadoDevendo,
            int fiadoNivel, int saldo, int divida, int exclusiveN, int exclusiveEstoque,
            int exclusivePreco, int comprasHoje, List<Integer> cruPrecos,
            List<Integer> precos) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AbrirPontoPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "abrir_ponto"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirPontoPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AbrirPontoPayload::escrever, AbrirPontoPayload::ler);

        private static void escrever(AbrirPontoPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(p.abrir);
            for (int v : new int[]{p.estoqueMaconha, p.estoqueCocaina, p.estoqueHeroina,
                    p.estoqueLsd, p.estoqueBaseado, p.estoqueOpio, p.estoqueExtrato,
                    p.desconto, p.estoqueDiamante, p.fiadoDevendo, p.fiadoNivel,
                    p.saldo, p.divida, p.exclusiveN, p.exclusiveEstoque, p.exclusivePreco,
                    p.comprasHoje}) {
                ByteBufCodecs.VAR_INT.encode(buf, v);
            }
            ByteBufCodecs.VAR_INT.encode(buf, p.cruPrecos.size());
            for (int preco : p.cruPrecos) {
                ByteBufCodecs.VAR_INT.encode(buf, preco);
            }
            ByteBufCodecs.VAR_INT.encode(buf, p.precos().size());
            for (int preco : p.precos()) {
                ByteBufCodecs.VAR_INT.encode(buf, preco);
            }
        }

        private static AbrirPontoPayload ler(RegistryFriendlyByteBuf buf) {
            boolean abrir = buf.readBoolean();
            int[] v = new int[17];
            for (int i = 0; i < v.length; i++) {
                v[i] = ByteBufCodecs.VAR_INT.decode(buf);
            }
            int crus = ByteBufCodecs.VAR_INT.decode(buf);
            if (crus < 0 || crus > 16) throw new IllegalArgumentException("Invalid raw-drug price count");
            List<Integer> cruPrecos = new ArrayList<>(crus);
            for (int i = 0; i < crus; i++) {
                cruPrecos.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            int nPrecos = ByteBufCodecs.VAR_INT.decode(buf);
            if (nPrecos < 0 || nPrecos > 16) throw new IllegalArgumentException("Invalid price count");
            List<Integer> precos = new ArrayList<>(nPrecos);
            for (int i = 0; i < nPrecos; i++) {
                precos.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new AbrirPontoPayload(abrir, v[0], v[1], v[2], v[3], v[4], v[5], v[6],
                    v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16],
                    cruPrecos, precos);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: saiu da fila e voltou pro balcao (o botao 'Bora pro fim da fila'). */
    public record DestrancoPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DestrancoPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "destranco"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DestrancoPayload> STREAM_CODEC =
                CustomPacketPayload.codec((p, buf) -> {}, buf -> new DestrancoPayload());
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: 'me empresta aí' — o fiado do traficante. */
    public record FiadoPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FiadoPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "fiado"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FiadoPayload> STREAM_CODEC =
                CustomPacketPayload.codec((p, buf) -> {}, buf -> new FiadoPayload());
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: pagar a divida do fiado. */
    public record DiazinhoPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DiazinhoPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "diazinho"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DiazinhoPayload> STREAM_CODEC =
                CustomPacketPayload.codec((p, buf) -> {}, buf -> new DiazinhoPayload());
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void negociar(ServerPlayer player, int index, boolean selling) {
        GagoEntity gago = SESSOES.get(player.getUUID());
        if (gago == null) return;
        MarketTransactions.Result result = MarketTransactions.trade(player, gago, index, selling);
        if (result == MarketTransactions.Result.INVALID) {
            encerrarSessao(player);
            ServerPlayNetworking.send(player, new FecharCardapioPayload());
            return;
        }
        player.sendSystemMessage(Component.translatable("commerce.intoxicantes.result."
                + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        player.playSound(result == MarketTransactions.Result.OK ? SoundEvents.EXPERIENCE_ORB_PICKUP
                : SoundEvents.VILLAGER_NO, 0.7F, 1.1F);
        enviarCardapio(player, gago, false);
    }

    // ==================================================== SESSAO

    /** Abre o atendimento: trava o Gago no balcao e manda o cardapio pro client. */
    public static void iniciarSessao(ServerPlayer player, GagoEntity gago) {
        GagoEntity anterior = SESSOES.get(player.getUUID());
        if (anterior != null && anterior != gago && anterior.getTradingPlayer() == player) {
            anterior.encerrarAtendimento();
        }
        if (gago.isTrading() && gago.getTradingPlayer() != player) return;
        gago.refreshTradeStock();
        SESSOES.put(player.getUUID(), gago);
        gago.setTradingPlayer(player);
        // v1.2.51: congela a IA durante o atendimento (gago de ovo/passeando
        // saía ANDANDO com a tela aberta até o watchdog fechar por distância)
        gago.congelarParaAtendimento();
        enviarCardapio(player, gago, true);
    }

    /** Monta e envia o cardapio do fregues (precos com o desconto do nivel). */
    public static void enviarCardapio(ServerPlayer player, GagoEntity gago, boolean abrir) {
        var catalog = TradeCatalog.gago(FidelidadeData.getTier(player));
        var harvests = TradeCatalog.harvests();
        List<ItemStack> produtos = catalog.stream().map(TradeCatalog.Entry::stack).toList();
        List<Integer> cheios = catalog.stream().map(TradeCatalog.Entry::price).toList();
        List<Integer> precos = catalog.stream().map(e -> FidelidadeData.precoComDesconto(player, e.price())).toList();
        List<Integer> estoque = catalog.stream().map(gago.marketInventory()::remaining).toList();
        int tier = FidelidadeData.getTier(player);
        int proximo = tier < FidelidadeData.META_TIER.length - 1 ? FidelidadeData.META_TIER[tier + 1] : -1;
        ServerPlayNetworking.send(player, new AbrirCardapioPayload(abrir, tier, FidelidadeData.getCompras(player),
                proximo, produtos, precos, cheios, FidelidadeData.primeiroExclusivo(), PlayerMoney.get(player), estoque,
                harvests.stream().map(TradeCatalog.Entry::stack).toList(),
                harvests.stream().map(TradeCatalog.Entry::price).toList(),
                harvests.stream().map(gago.marketInventory()::remaining).toList(),
                harvests.stream().map(e -> MarketTransactions.countHarvest(player, e)).toList()));
    }

    /** Fecha o atendimento do fregues (Esc, botao Fechar ou watchdog). */
    public static void encerrarSessao(ServerPlayer player) {
        GagoEntity gago = SESSOES.remove(player.getUUID());
        if (gago != null && gago.getTradingPlayer() == player) {
            gago.encerrarAtendimento();
        }
        TraficanteEntity traficante = SESSOES_T.remove(player.getUUID());
        if (traficante != null && traficante.getTradingPlayer() == player) {
            traficante.largarAtendimento();
        }
    }

    // ==================================================== PONTO DO TRAFICANTE (v1.2.44)

    /** Nomes do catalogo de venda do traficante (ordem do TradeCatalog.traficanteFixo). */
    static final String[] TITULOS = {
            "maconha_seda", "cocaina", "heroina", "lsd", "baseado", "opio", "extrato_cafeina"
    };

    /** Monta e envia o estado completo do ponto (estoque, desconto, fiado, Exclusive). */
    public static void enviarPonto(ServerPlayer player, TraficanteEntity t, boolean abrir) {
        ServerPlayNetworking.send(player, new AbrirPontoPayload(abrir,
                t.estoqueDe(TITULOS[0]), t.estoqueDe(TITULOS[1]),
                t.estoqueDe(TITULOS[2]), t.estoqueDe(TITULOS[3]),
                t.estoqueDe(TITULOS[4]), t.estoqueDe(TITULOS[5]),
                t.estoqueDe(TITULOS[6]),
                t.getDescontoAtivo(), t.estoqueDe("diamante"),
                t.getFiadoPlayer(player), t.getFiadoNivel(player),
                PlayerMoney.get(player), PlayerMoney.getDivida(player),
                t.getExclusiveN(), t.getExclusiveEstoque(), t.getExclusivePreco(player),
                t.getComprasHoje(player),
                // v1.2.66: preço de HOJE de cada cru que ele compra (ordem = cruDrogas())
                TradeCatalog.cruDrogas().stream().map(e -> t.precoCruDe(player, e)).toList(),
                // v1.2.71: preço REAL de cada produto (cotação do dia + descontos,
                // o MESMO cálculo da cobrança — rótulo = carteira, ordem = traficanteFixo)
                TradeCatalog.traficanteFixo().stream().map(e -> t.precoDe(player, e)).toList()));
    }

    /** Sessao do traficante: trava a IA e manda o estado pro client. */    /**
     * v1.2.66 — ABRE-SESSÃO do traficante: garante estoque do dia sorteado
     * ANTES de construir o payload (antes o primeiro clique mandava -1 no
     * diamante e 0 em tudo — a tela nascia "esgotada").
     */
    public static void ingressarTraf(ServerPlayer player, TraficanteEntity traficante) {
        if (traficante == null || !traficante.isAlive() || traficante.isRemoved()) {
            return;
        }
        TraficanteEntity anterior = SESSOES_T.get(player.getUUID());
        if (anterior != null && anterior != traficante && anterior.getTradingPlayer() == player) {
            anterior.largarAtendimento();
        }
        traficante.refreshTradeStock(); // PRIMEIRO: o estoque do dia tem que existir
        SESSOES_T.put(player.getUUID(), traficante);
        traficante.setTradingPlayer(player);
        enviarPonto(player, traficante, true);
    }

    /** Compra (ou aluga) o item de indice tal do ponto do traficante. */
    public static void comprarNoTraficante(ServerPlayer player, int indice) {
        TraficanteEntity t = SESSOES_T.get(player.getUUID());
        if (t == null) return;
        if (!t.comprar(player, indice)) {
            player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 0.7F, 1.0F);
        }
        enviarPonto(player, t, false);
    }

    /**
     * v1.2.66 — Vende CRU DE DROGA pro traficante (ele paga na hora, sem fila;
     * insumo de bebida é com o Gago, droga é com o camelo).
     */
    public static void venderNoTraficante(ServerPlayer player, int indice) {
        TraficanteEntity t = SESSOES_T.get(player.getUUID());
        if (t == null) return;
        if (!t.venderCruDroga(player, indice)) {
            player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 0.7F, 1.0F);
        }
        enviarPonto(player, t, false);
    }

    /** Saldo de R$ de um fregues (a tela do traficante le). */
    public static int saldoDe(ServerPlayer player) {
        return PlayerMoney.get(player);
    }

    /** Debita (false se nao tem) e avisa a carteira. */
    public static boolean cobrarDe(ServerPlayer player, int quantia) {
        return PlayerMoney.subtrair(player, quantia);
    }

    /** Credita (paguei divida, ganhei venda). */
    public static void ajustarSaldoDe(ServerPlayer player, int quantia) {
        PlayerMoney.add(player, quantia);
    }

    // ==================================================== PAYLOADS

    /** S2C: cardapio completo do Esquinao, ja com os precos do fidelidade. */
    public record AbrirCardapioPayload(boolean abrir, int nivel, int compras, int comprasProximoNivel,
            List<ItemStack> produtos, List<Integer> precos, List<Integer> precoCheio,
            int primeiroExclusivo, int saldo, List<Integer> estoques,
            List<ItemStack> colheitas, List<Integer> pagamentos, List<Integer> cotas,
            List<Integer> disponiveis) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AbrirCardapioPayload> TYPE =
                new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("intoxicantes", "abrir_cardapio"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirCardapioPayload> STREAM_CODEC =
                CustomPacketPayload.codec(AbrirCardapioPayload::escrever, AbrirCardapioPayload::ler);

        private static void escrever(AbrirCardapioPayload p, RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(p.abrir);
            ByteBufCodecs.VAR_INT.encode(buf, p.nivel);
            ByteBufCodecs.VAR_INT.encode(buf, p.compras);
            ByteBufCodecs.VAR_INT.encode(buf, p.comprasProximoNivel);
            ByteBufCodecs.VAR_INT.encode(buf, p.produtos.size());
            for (ItemStack item : p.produtos) {
                ItemStack.STREAM_CODEC.encode(buf, item);
            }
            for (int preco : p.precos) {
                ByteBufCodecs.VAR_INT.encode(buf, preco);
            }
            for (int preco : p.precoCheio) {
                ByteBufCodecs.VAR_INT.encode(buf, preco);
            }
            ByteBufCodecs.VAR_INT.encode(buf, p.primeiroExclusivo);
            ByteBufCodecs.VAR_INT.encode(buf, p.saldo);
            for (int stock : p.estoques) ByteBufCodecs.VAR_INT.encode(buf, stock);
            ByteBufCodecs.VAR_INT.encode(buf, p.colheitas.size());
            for (int i = 0; i < p.colheitas.size(); i++) {
                ItemStack.STREAM_CODEC.encode(buf, p.colheitas.get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.pagamentos.get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.cotas.get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.disponiveis.get(i));
            }
        }

        private static AbrirCardapioPayload ler(RegistryFriendlyByteBuf buf) {
            boolean abrir = buf.readBoolean();
            int nivel = ByteBufCodecs.VAR_INT.decode(buf);
            int compras = ByteBufCodecs.VAR_INT.decode(buf);
            int proximo = ByteBufCodecs.VAR_INT.decode(buf);
            int tamanho = ByteBufCodecs.VAR_INT.decode(buf);
            if (tamanho < 0 || tamanho > 128) throw new IllegalArgumentException("Invalid market catalog size");
            List<ItemStack> produtos = new ArrayList<>(tamanho);
            for (int i = 0; i < tamanho; i++) {
                produtos.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            List<Integer> precos = new ArrayList<>(tamanho);
            List<Integer> cheios = new ArrayList<>(tamanho);
            for (int i = 0; i < tamanho; i++) {
                precos.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            for (int i = 0; i < tamanho; i++) {
                cheios.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            int primeiroExclusivo = ByteBufCodecs.VAR_INT.decode(buf);
            int saldo = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> estoques = new ArrayList<>();
            for (int i = 0; i < tamanho; i++) estoques.add(ByteBufCodecs.VAR_INT.decode(buf));
            int crops = ByteBufCodecs.VAR_INT.decode(buf);
            if (crops < 0 || crops > 16) throw new IllegalArgumentException("Invalid harvest catalog size");
            List<ItemStack> colheitas = new ArrayList<>();
            List<Integer> pagamentos = new ArrayList<>(), cotas = new ArrayList<>(), disponiveis = new ArrayList<>();
            for (int i = 0; i < crops; i++) {
                colheitas.add(ItemStack.STREAM_CODEC.decode(buf));
                pagamentos.add(ByteBufCodecs.VAR_INT.decode(buf));
                cotas.add(ByteBufCodecs.VAR_INT.decode(buf));
                disponiveis.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new AbrirCardapioPayload(abrir, nivel, compras, proximo, produtos,
                    precos, cheios, primeiroExclusivo, saldo, estoques, colheitas, pagamentos, cotas, disponiveis);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: fregues clicou em Comprar no indice tal do cardapio. */
    public record ComprarPayload(int indice) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ComprarPayload> TYPE =
                new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("intoxicantes", "comprar"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ComprarPayload> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> ByteBufCodecs.VAR_INT.encode(buf, p.indice()),
                        buf -> new ComprarPayload(ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record VenderPayload(int indice) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VenderPayload> TYPE =
                new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("intoxicantes", "vender_colheita"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VenderPayload> STREAM_CODEC =
                CustomPacketPayload.codec((p, buf) -> ByteBufCodecs.VAR_INT.encode(buf, p.indice()),
                        buf -> new VenderPayload(ByteBufCodecs.VAR_INT.decode(buf)));
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Nos dois sentidos: "fecha a tela do cardapio". Sem corpo. */
    public record FecharCardapioPayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<FecharCardapioPayload> TYPE =
                new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("intoxicantes", "fechar_cardapio"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FecharCardapioPayload> STREAM_CODEC =
                CustomPacketPayload.codec(
                        (p, buf) -> {},
                        buf -> new FecharCardapioPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
