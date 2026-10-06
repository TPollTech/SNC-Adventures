package com.intoxicantes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** A gôndola anota pedidos; somente o caixa entrega mercadoria e recebe dinheiro. */
public final class PrateleiraNetworking {
    private static final int MAX_LINHAS = 64;
    private static final int MAX_UNIDADES = 1_000_000;
    private static final int RAIO_LOJA = 24;
    private static final long DURACAO_TICKS = 6000;
    private static final Map<UUID, Map<Loja, Carrinho>> CARRINHOS = new HashMap<>();
    private static final Map<UUID, Orcamento> PENDENTES = new HashMap<>();
    /** v1.2.77: ultimo gameTime do CALÇO por freguês (o tapa não pode virar metralhadora). */
    private static final Map<UUID, Long> ULTIMO_CALCO = new HashMap<>();
    /** Intervalo mínimo entre dois calços do MESMO freguês (2 ticks = 0,1s). */
    private static final long CALCO_INTERVALO = 2L;
    /** Teto do "+pitch" do calço: 24 doses anotadas na gôndola. */
    private static final int CALCO_PASSOS = 24;
    private static final AtomicLong TOKENS = new AtomicLong(System.nanoTime());

    private record Loja(ServerLevel level, BlockPos caixa) {}
    private static final class Linha {
        final BlockPos fonte;
        final int slot;
        final ItemStack produto;
        final int preco, dose;
        int quantidade;
        Linha(BlockPos fonte, int slot, ItemStack produto, int preco, int dose, int quantidade) {
            this.fonte = fonte.immutable();
            this.slot = slot;
            this.produto = produto.copyWithCount(dose);
            this.preco = preco;
            this.dose = dose;
            this.quantidade = quantidade;
        }
    }
    private static final class Carrinho {
        final Loja loja;
        final List<Linha> linhas = new ArrayList<>();
        Carrinho(Loja loja) { this.loja = loja; }
    }
    private record Oferta(BlockPos fonte, int slot, ItemStack produto, int preco, int dose,
            int quantidade, int estoque, PrateleiraMercadoBlockEntity entidade) {}
    private record Orcamento(long token, Carrinho carrinho, UUID atendente, long criado,
            List<Oferta> ofertas, int total) {}

    private PrateleiraNetworking() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(AbrirCompraPayload.TYPE, AbrirCompraPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CarrinhoResumoPayload.TYPE, CarrinhoResumoPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfirmarCompraPayload.TYPE, ConfirmarCompraPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AtualizarCarrinhoPayload.TYPE, AtualizarCarrinhoPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ConfirmarCompraPayload.TYPE, (payload, ctx) -> {
            var antes = PENDENTES.get(ctx.player().getUUID());
            responderCompra(ctx.player(), payload);
            if (antes != null) enviarResumo(ctx.player(), antes.carrinho().loja);
        });
        ServerPlayNetworking.registerGlobalReceiver(AtualizarCarrinhoPayload.TYPE, (payload, ctx) -> {
            var antes = PENDENTES.get(ctx.player().getUUID());
            var resposta = atualizarCarrinho(ctx.player(), payload);
            if (resposta != null) ServerPlayNetworking.send(ctx.player(), resposta);
            if (antes != null) enviarResumo(ctx.player(), antes.carrinho().loja);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDENTES.remove(handler.player.getUUID());
            CARRINHOS.remove(handler.player.getUUID());
            ULTIMO_CALCO.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDENTES.clear();
            CARRINHOS.clear();
            ULTIMO_CALCO.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            PENDENTES.entrySet().removeIf(entry -> {
                var player = server.getPlayerList().getPlayer(entry.getKey());
                return player == null || !sessaoValida(player, entry.getValue());
            });
        });
    }

    /** Nome legado preservado: o clique agora só acrescenta pedidos, nunca compra. */
    public static void solicitarCompra(ServerLevel level, ServerPlayer player, BlockPos pos,
            int slot, boolean fundo, boolean fileiraToda) {
        if (adicionarAoCarrinho(level, player, pos, slot, fundo, fileiraToda)) {
            tocarCalco(level, player, pos);
            var caixa = CaixaMercadoBlock.encontrar(level, pos, RAIO_LOJA);
            if (caixa != null) enviarResumo(player, new Loja(level, caixa));
            player.sendSystemMessage(Component.translatable("caixa.intoxicantes.adicionado"), true);
        }
    }

    /**
     * v1.2.77 — O CALÇO DA REGISTRADORA no TAPA: cada dose que entra no
     * carrinho toca o MESMO "ka-ching" do pagamento
     * ({@link IntoxicantesMod#CAIXA_REGISTRADORA}, o som do society de rua),
     * agora na gôndola e com o PITCH SUBINDO conforme o CARRINHO enche de
     * doses daquela gôndola — dá pra contar no áudio quantas já caíram.
     * O som é do SERVIDOR (mesmo caminho do pagamento): só toca quando a
     * mercadoria REALMENTE caiu no carrinho — tapa em vão não faz barulho.
     * O intervalo de {@link #CALCO_INTERVALO} segura o hold-clique.
     */
    private static void tocarCalco(ServerLevel level, ServerPlayer player, BlockPos pos) {
        long agora = level.getGameTime();
        Long ultimo = ULTIMO_CALCO.get(player.getUUID());
        if (ultimo != null && agora - ultimo < CALCO_INTERVALO) return;
        ULTIMO_CALCO.put(player.getUUID(), agora);
        float pitch = 0.94F + Math.min(CALCO_PASSOS, unidadesDaGondola(player, level, pos))
                * 0.022F;
        level.playSound(null, pos, IntoxicantesMod.CAIXA_REGISTRADORA,
                SoundSource.NEUTRAL, 0.45F, pitch);
    }

    /** Quantas doses do MESMO carrinho saíram desta gôndola (base do pitch do calço). */
    private static int unidadesDaGondola(ServerPlayer player, ServerLevel level, BlockPos fonte) {
        var lojas = CARRINHOS.get(player.getUUID());
        if (lojas == null) return 0;
        int total = 0;
        for (var carrinho : lojas.values()) {
            if (carrinho.loja.level() != level) continue;
            for (var linha : carrinho.linhas) if (linha.fonte.equals(fonte)) total += linha.quantidade;
        }
        return total;
    }

    static boolean adicionarAoCarrinho(ServerLevel level, ServerPlayer player, BlockPos pos,
            int slot, boolean fundo, boolean fileiraToda) {
        if (slot < 0 || slot >= PrateleiraMercadoBlockEntity.SLOTS || !alcanca(player, level, pos, 8)
                || !(level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be)) return false;
        BlockPos caixa = CaixaMercadoBlock.encontrar(level, pos, RAIO_LOJA);
        if (caixa == null || caixa.distSqr(pos) > RAIO_LOJA * RAIO_LOJA || !caixaValida(level, caixa)) {
            avisar(player, "caixa.intoxicantes.sem_caixa");
            return false;
        }
        var loja = new Loja(level, caixa);
        var lojas = CARRINHOS.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        if (!lojas.containsKey(loja) && lojas.size() >= 8) return false;
        var carrinho = lojas.computeIfAbsent(loja, Carrinho::new);
        int deslocamento = fundo ? PrateleiraMercadoBlockEntity.SLOTS : 0;
        boolean entrou = false;
        for (int i = fileiraToda ? 0 : slot; i < (fileiraToda ? PrateleiraMercadoBlockEntity.SLOTS : slot + 1); i++) {
            int indice = deslocamento + i;
            var pilha = be.getItem(indice);
            int preco = be.precoDoSlot(indice), dose = be.doseDoSlot(indice);
            if (pilha.isEmpty() || preco <= 0 || dose <= 0 || dose > pilha.getMaxStackSize()) continue;
            Linha linha = null;
            for (var atual : carrinho.linhas) if (atual.fonte.equals(pos) && atual.slot == indice) { linha = atual; break; }
            if (linha != null && (!ItemStack.isSameItemSameComponents(linha.produto, pilha)
                    || linha.preco != preco || linha.dose != dose)) continue;
            int quantidade = linha == null ? 0 : linha.quantidade;
            int entra = Math.min(dose, Math.min(MAX_UNIDADES, pilha.getCount()) - quantidade);
            if (entra <= 0 || linha == null && carrinho.linhas.size() >= MAX_LINHAS) continue;
            if (linha == null) carrinho.linhas.add(new Linha(pos, indice, pilha, preco, dose, entra));
            else linha.quantidade += entra;
            entrou = true;
        }
        if (entrou) {
            be.marcarComercial();
            PENDENTES.remove(player.getUUID());
        } else avisar(player, "caixa.intoxicantes.indisponivel");
        return entrou;
    }

    /** Um caixa associado ao Gago trata inclusive o carrinho vazio, sem abrir venda direta. */
    public static boolean abrirCaixa(ServerPlayer player, GagoEntity gago) {
        var caixa = caixaDoGago(gago);
        if (caixa == null) return false;
        var payload = prepararCaixa(player, gago);
        if (payload != null) {
            ServerPlayNetworking.send(player, payload);
            enviarResumo(player, new Loja((ServerLevel) gago.level(), caixa));
        }
        return true;
    }

    static AbrirCompraPayload prepararCaixa(ServerPlayer player, GagoEntity gago) {
        PENDENTES.remove(player.getUUID());
        var caixa = caixaDoGago(gago);
        if (caixa == null || !(gago.level() instanceof ServerLevel level)
                || !alcanca(player, level, caixa, 8) || player.distanceToSqr(gago) > 36) return null;
        var loja = new Loja(level, caixa);
        var lojas = CARRINHOS.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        if (!lojas.containsKey(loja) && lojas.size() >= 8) return null;
        var carrinho = lojas.computeIfAbsent(loja, Carrinho::new);
        return cotar(player, carrinho, gago.getUUID(), TOKENS.incrementAndGet(), level.getGameTime());
    }

    private static AbrirCompraPayload cotar(ServerPlayer player, Carrinho carrinho, UUID gago,
            long token, long criado) {
        List<ItemStack> produtos = new ArrayList<>();
        List<Integer> precos = new ArrayList<>(), quantidades = new ArrayList<>(), limites = new ArrayList<>();
        List<Oferta> ofertas = new ArrayList<>();
        long total = 0;
        for (var linha : carrinho.linhas) {
            int limite = limiteAtual(carrinho.loja, linha);
            var be = limite > 0 ? (PrateleiraMercadoBlockEntity) carrinho.loja.level().getBlockEntity(linha.fonte) : null;
            produtos.add(linha.produto.copy());
            precos.add(linha.preco);
            quantidades.add(linha.quantidade);
            limites.add(limite);
            // o estoque do slot na HORA da cotação: a confirmação rejeita
            // qualquer diferença (estoque mexido = orçamento velho)
            ofertas.add(new Oferta(linha.fonte, linha.slot, linha.produto.copy(), linha.preco,
                    linha.dose, linha.quantidade,
                    be == null ? -1 : be.getItem(linha.slot).getCount(), be));
            total += precoLinha(linha.preco, linha.quantidade, linha.dose);
            if (total > Integer.MAX_VALUE) {
                avisar(player, "prateleira.intoxicantes.compra_alterada");
                return null;
            }
        }
        PENDENTES.put(player.getUUID(), new Orcamento(token, carrinho, gago, criado, List.copyOf(ofertas), (int) total));
        return new AbrirCompraPayload(token, produtos, precos, quantidades, limites, (int) total, PlayerMoney.get(player));
    }

    /** Revisões mantêm o token da sessão; remover é quantidade zero. */
    static AbrirCompraPayload atualizarCarrinho(ServerPlayer player, AtualizarCarrinhoPayload payload) {
        var orcamento = PENDENTES.get(player.getUUID());
        if (orcamento == null || orcamento.token() != payload.token() || !sessaoValida(player, orcamento)) return null;
        var carrinho = orcamento.carrinho();
        if (payload.linha() < 0 || payload.linha() >= carrinho.linhas.size()
                || payload.quantidade() < 0 || payload.quantidade() > MAX_UNIDADES) return null;
        var linha = carrinho.linhas.get(payload.linha());
        if (payload.quantidade() == 0) carrinho.linhas.remove(payload.linha());
        else if (payload.quantidade() <= limiteAtual(carrinho.loja, linha)) linha.quantidade = payload.quantidade();
        else avisar(player, "caixa.intoxicantes.indisponivel");
        return cotar(player, carrinho, orcamento.atendente(), orcamento.token(), carrinho.loja.level().getGameTime());
    }

    /** Debita uma vez, retira todas as fontes e entrega tudo; falha não compra parcialmente. */
    static boolean responderCompra(ServerPlayer player, ConfirmarCompraPayload payload) {
        var orcamento = PENDENTES.get(player.getUUID());
        if (orcamento == null || orcamento.token() != payload.token()) return false;
        PENDENTES.remove(player.getUUID());
        if (!payload.confirmar()) return false;
        if (!sessaoValida(player, orcamento)) return mudou(player);
        if (orcamento.ofertas().isEmpty() || orcamento.total() <= 0) {
            avisar(player, "caixa.intoxicantes.vazio");
            return false;
        }
        var loja = orcamento.carrinho().loja;
        List<ItemStack> entrega = new ArrayList<>();
        List<ItemStack> anteriores = new ArrayList<>();
        long total = 0;
        for (var oferta : orcamento.ofertas()) {
            if (!loja.level().hasChunkAt(oferta.fonte())
                    || loja.level().getBlockEntity(oferta.fonte()) != oferta.entidade()
                    || oferta.entidade() == null || oferta.fonte().distSqr(loja.caixa()) > RAIO_LOJA * RAIO_LOJA
                    || !loja.caixa().equals(CaixaMercadoBlock.encontrar(loja.level(), oferta.fonte(), RAIO_LOJA))) return mudou(player);
            var be = oferta.entidade();
            var pilha = be.getItem(oferta.slot());
            if (oferta.quantidade() <= 0 || oferta.quantidade() > pilha.getCount() || oferta.preco() <= 0
                    || !ItemStack.isSameItemSameComponents(pilha, oferta.produto())
                    || be.precoDoSlot(oferta.slot()) != oferta.preco() || be.doseDoSlot(oferta.slot()) != oferta.dose()
                    || pilha.getCount() != oferta.estoque()) return mudou(player);
            total += precoLinha(oferta.preco(), oferta.quantidade(), oferta.dose());
            anteriores.add(pilha.copy());
            entrega.add(pilha.copyWithCount(oferta.quantidade()));
        }
        if (total != orcamento.total() || total > Integer.MAX_VALUE) return mudou(player);
        var inventarioAnterior = copiarInventario(player);
        var inventarioFinal = encaixar(inventarioAnterior, entrega);
        if (inventarioFinal == null) {
            avisar(player, "caixa.intoxicantes.inventario_cheio");
            return false;
        }
        int saldoAnterior = PlayerMoney.get(player);
        if (!PlayerMoney.subtrair(player, orcamento.total())) {
            player.sendSystemMessage(Component.translatable("block.intoxicantes.prateleira_sem_dinheiro", orcamento.total()), true);
            return false;
        }
        Set<PrateleiraMercadoBlockEntity> alteradas = new HashSet<>();
        try {
            for (int i = 0; i < orcamento.ofertas().size(); i++) {
                var oferta = orcamento.ofertas().get(i);
                var retirada = oferta.entidade().removeItem(oferta.slot(), oferta.quantidade());
                if (!ItemStack.matches(retirada, entrega.get(i))) throw new IllegalStateException("Estoque mudou durante finalização do caixa");
                alteradas.add(oferta.entidade());
            }
            aplicarInventario(player, inventarioFinal);
        } catch (RuntimeException | Error falha) {
            for (int i = 0; i < orcamento.ofertas().size(); i++) {
                var oferta = orcamento.ofertas().get(i);
                oferta.entidade().setItem(oferta.slot(), anteriores.get(i));
                oferta.entidade().sincronizarVenda();
            }
            aplicarInventario(player, inventarioAnterior);
            PlayerMoney.set(player, saldoAnterior);
            throw falha;
        }
        orcamento.carrinho().linhas.clear();
        for (var be : alteradas) be.sincronizarVenda();
        player.inventoryMenu.broadcastChanges();
        loja.level().playSound(null, loja.caixa(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, .5F, 1.2F);
        player.sendSystemMessage(Component.translatable("caixa.intoxicantes.pago", orcamento.total()), true);
        return true;
    }

    private static List<ItemStack> copiarInventario(ServerPlayer player) {
        var slots = new ArrayList<ItemStack>(36);
        for (int i = 0; i < 36; i++) slots.add(player.getInventory().getItem(i).copy());
        return slots;
    }

    private static List<ItemStack> encaixar(List<ItemStack> inventario, List<ItemStack> entrega) {
        var slots = new ArrayList<ItemStack>(36);
        for (var item : inventario) slots.add(item.copy());
        for (var item : entrega) {
            int restante = item.getCount();
            for (var atual : slots) {
                if (!ItemStack.isSameItemSameComponents(atual, item)) continue;
                int entra = Math.min(restante, Math.max(0, atual.getMaxStackSize() - atual.getCount()));
                atual.grow(entra);
                restante -= entra;
            }
            for (int i = 0; i < 36 && restante > 0; i++) if (slots.get(i).isEmpty()) {
                int entra = Math.min(restante, item.getMaxStackSize());
                slots.set(i, item.copyWithCount(entra));
                restante -= entra;
            }
            if (restante != 0) return null;
        }
        return slots;
    }

    private static void aplicarInventario(ServerPlayer player, List<ItemStack> slots) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, slots.get(i).copy());
    }

    private static int limiteAtual(Loja loja, Linha linha) {
        if (!caixaValida(loja.level(), loja.caixa()) || !loja.level().hasChunkAt(linha.fonte)
                || linha.fonte.distSqr(loja.caixa()) > RAIO_LOJA * RAIO_LOJA
                || !loja.caixa().equals(CaixaMercadoBlock.encontrar(loja.level(), linha.fonte, RAIO_LOJA))
                || !(loja.level().getBlockEntity(linha.fonte) instanceof PrateleiraMercadoBlockEntity be)) return 0;
        return ItemStack.isSameItemSameComponents(be.getItem(linha.slot), linha.produto)
                && be.precoDoSlot(linha.slot) == linha.preco && be.doseDoSlot(linha.slot) == linha.dose
                ? Math.min(MAX_UNIDADES, be.getItem(linha.slot).getCount()) : 0;
    }

    private static BlockPos caixaDoGago(GagoEntity gago) {
        if (!(gago.level() instanceof ServerLevel level) || !gago.isAlive() || gago.isPuto() || gago.isBaby()) return null;
        var caixa = CaixaMercadoBlock.encontrar(level, gago.blockPosition(), 2);
        if (caixa == null || !caixaValida(level, caixa) || CaixaMercadoBlock.atendente(level, caixa) != gago) return null;
        var frente = level.getBlockState(caixa).getValue(CaixaMercadoBlock.FACING);
        var posto = CaixaMercadoBlock.posAtendente(caixa, frente);
        return gago.distanceToSqr(posto.getX() + .5, posto.getY(), posto.getZ() + .5) <= .81 ? caixa : null;
    }

    private static boolean caixaValida(ServerLevel level, BlockPos caixa) {
        if (!level.hasChunkAt(caixa) || !level.hasChunkAt(caixa.above())) return false;
        var baixo = level.getBlockState(caixa);
        var alto = level.getBlockState(caixa.above());
        return baixo.getBlock() instanceof CaixaMercadoBlock && baixo.getValue(CaixaMercadoBlock.HALF) == DoubleBlockHalf.LOWER
                && alto.is(baixo.getBlock()) && alto.getValue(CaixaMercadoBlock.HALF) == DoubleBlockHalf.UPPER
                && alto.getValue(CaixaMercadoBlock.FACING) == baixo.getValue(CaixaMercadoBlock.FACING);
    }

    private static boolean sessaoValida(ServerPlayer player, Orcamento orcamento) {
        var loja = orcamento.carrinho().loja;
        long idade = loja.level().getGameTime() - orcamento.criado();
        if (idade < 0 || idade > DURACAO_TICKS || !alcanca(player, loja.level(), loja.caixa(), 8)
                || !caixaValida(loja.level(), loja.caixa())) return false;
        var entity = loja.level().getEntity(orcamento.atendente());
        return entity instanceof GagoEntity gago && loja.caixa().equals(caixaDoGago(gago)) && player.distanceToSqr(gago) <= 36;
    }

    /** Gametest: o total de UNIDADES anotadas nos carrinhos do freguês. */
    static int unidadesEmCarrinhoDeTeste(ServerPlayer player) {
        var lojas = CARRINHOS.get(player.getUUID());
        if (lojas == null) return 0;
        int total = 0;
        for (var carrinho : lojas.values()) for (var linha : carrinho.linhas) total += linha.quantidade;
        return total;
    }

    /** Gametest: zera os carrinhos do freguês (cada cenário começa limpo). */
    static void limparCarrinhosDeTeste(ServerPlayer player) {
        var lojas = CARRINHOS.get(player.getUUID());
        if (lojas != null) for (var carrinho : lojas.values()) carrinho.linhas.clear();
        PENDENTES.remove(player.getUUID());
    }

    private static boolean alcanca(ServerPlayer player, ServerLevel level, BlockPos pos, int raio) {
        return player.isAlive() && !player.isSpectator() && player.level() == level
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= raio * raio && level.hasChunkAt(pos);
    }

    private static long precoLinha(int preco, int unidades, int dose) {
        return ((long) preco * unidades + dose - 1L) / dose;
    }

    private static void enviarResumo(ServerPlayer player, Loja loja) {
        if (player.level() != loja.level()) return;
        ServerPlayNetworking.send(player, resumo(player, loja));
    }

    private static CarrinhoResumoPayload resumo(ServerPlayer player, Loja loja) {
        var lojas = CARRINHOS.get(player.getUUID());
        var carrinho = lojas == null ? null : lojas.get(loja);
        long total = 0;
        int unidades = 0;
        if (carrinho != null) for (var linha : carrinho.linhas) {
            total += precoLinha(linha.preco, linha.quantidade, linha.dose);
            unidades += linha.quantidade;
        }
        return new CarrinhoResumoPayload(loja.caixa(), (int) Math.min(Integer.MAX_VALUE, total), unidades);
    }

    private static boolean mudou(ServerPlayer player) {
        avisar(player, "prateleira.intoxicantes.compra_alterada");
        return false;
    }

    private static void avisar(ServerPlayer player, String chave) {
        player.sendSystemMessage(Component.translatable(chave), true);
    }

    public record AbrirCompraPayload(long token, List<ItemStack> produtos, List<Integer> precos,
            List<Integer> quantidades, List<Integer> limites, int total, int saldo) implements CustomPacketPayload {
        public AbrirCompraPayload {
            int tamanho = produtos.size();
            if (tamanho > MAX_LINHAS || precos.size() != tamanho || quantidades.size() != tamanho
                    || limites.size() != tamanho || total < 0 || saldo < 0) throw new IllegalArgumentException("Carrinho inválido");
            for (int i = 0; i < tamanho; i++) if (produtos.get(i).isEmpty() || precos.get(i) <= 0
                    || quantidades.get(i) <= 0 || quantidades.get(i) > MAX_UNIDADES
                    || limites.get(i) < 0 || limites.get(i) > MAX_UNIDADES) throw new IllegalArgumentException("Linha inválida do carrinho");
            produtos = produtos.stream().map(ItemStack::copy).toList();
            precos = List.copyOf(precos);
            quantidades = List.copyOf(quantidades);
            limites = List.copyOf(limites);
        }
        public static final Type<AbrirCompraPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "abrir_compra_prateleira"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbrirCompraPayload> STREAM_CODEC = CustomPacketPayload.codec((p, buf) -> {
            buf.writeLong(p.token());
            ByteBufCodecs.VAR_INT.encode(buf, p.produtos().size());
            for (int i = 0; i < p.produtos().size(); i++) {
                ItemStack.STREAM_CODEC.encode(buf, p.produtos().get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.precos().get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.quantidades().get(i));
                ByteBufCodecs.VAR_INT.encode(buf, p.limites().get(i));
            }
            ByteBufCodecs.VAR_INT.encode(buf, p.total());
            ByteBufCodecs.VAR_INT.encode(buf, p.saldo());
        }, buf -> {
            long token = buf.readLong();
            int tamanho = ByteBufCodecs.VAR_INT.decode(buf);
            if (tamanho < 0 || tamanho > MAX_LINHAS) throw new IllegalArgumentException("Carrinho excede limite de linhas");
            List<ItemStack> produtos = new ArrayList<>();
            List<Integer> precos = new ArrayList<>(), quantidades = new ArrayList<>(), limites = new ArrayList<>();
            for (int i = 0; i < tamanho; i++) {
                produtos.add(ItemStack.STREAM_CODEC.decode(buf));
                precos.add(ByteBufCodecs.VAR_INT.decode(buf));
                quantidades.add(ByteBufCodecs.VAR_INT.decode(buf));
                limites.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new AbrirCompraPayload(token, produtos, precos, quantidades, limites,
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AtualizarCarrinhoPayload(long token, int linha, int quantidade) implements CustomPacketPayload {
        public static final Type<AtualizarCarrinhoPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "atualizar_carrinho"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AtualizarCarrinhoPayload> STREAM_CODEC = CustomPacketPayload.codec((p, buf) -> {
            buf.writeLong(p.token());
            ByteBufCodecs.VAR_INT.encode(buf, p.linha());
            ByteBufCodecs.VAR_INT.encode(buf, p.quantidade());
        }, buf -> new AtualizarCarrinhoPayload(buf.readLong(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConfirmarCompraPayload(long token, boolean confirmar) implements CustomPacketPayload {
        public static final Type<ConfirmarCompraPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "confirmar_compra_prateleira"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmarCompraPayload> STREAM_CODEC = CustomPacketPayload.codec((p, buf) -> {
            buf.writeLong(p.token());
            buf.writeBoolean(p.confirmar());
        }, buf -> new ConfirmarCompraPayload(buf.readLong(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CarrinhoResumoPayload(BlockPos caixa, int total, int unidades) implements CustomPacketPayload {
        public CarrinhoResumoPayload {
            if (total < 0 || unidades < 0) throw new IllegalArgumentException("Resumo inválido do caixa");
            caixa = caixa.immutable();
        }
        public static final Type<CarrinhoResumoPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("intoxicantes", "resumo_carrinho"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CarrinhoResumoPayload> STREAM_CODEC = CustomPacketPayload.codec((p, buf) -> {
            buf.writeBlockPos(p.caixa());
            ByteBufCodecs.VAR_INT.encode(buf, p.total());
            ByteBufCodecs.VAR_INT.encode(buf, p.unidades());
        }, buf -> new CarrinhoResumoPayload(buf.readBlockPos(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
