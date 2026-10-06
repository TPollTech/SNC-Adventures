package com.intoxicantes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v1.2.69 — A PRATELEIRA DE MERCADO e o CALENDÁRIO: a gôndola que vende
 * qualquer item, cobra R$ pela etiqueta e repõe o estoque de partida
 * QUARTA-FEIRA (estilo My Summer Car), com a semana visível no HUD.
 */
public class PrateleiraGameTest {

    // ==================================================== CALENDÁRIO

    /** A semana deriva do dia comercial (07:00): sáb/dom/seg/ter/QUARTA/qui/sex. */
    @GameTest
    public void calendarioDerivaDiaDaSemanaDoDiaComercial(GameTestHelper helper) {
        // dia comercial 0 = SÁBADO (convenção da casa: semana começa no sábado);
        // o dia comercial 0 corre de 07:00 do diaTime 0 até 06:59 do dia 1
        helper.assertTrue(CalendarioEsquinao.diaDaSemana(1000L) == CalendarioEsquinao.SATURDAY,
                "Trading day 0 is a Saturday");
        helper.assertTrue(CalendarioEsquinao.diaDaSemana(24000L + 999L) == CalendarioEsquinao.SATURDAY,
                "06:59 still belongs to Saturday");
        helper.assertTrue(CalendarioEsquinao.diaDaSemana(24000L + 1000L) == CalendarioEsquinao.SUNDAY,
                "07:00 flips the calendar to Sunday");
        // o dia comercial muda às 07:00 (tick 1000); dias 0..6 = sáb..sex, dia 4 = QUARTA
        long quartaManha = 4L * 24000L + 1000L;   // quarta 07:00 (a entrega chega)
        long tercaAmanhecer = 4L * 24000L + 999L; // 06:59:59 ainda é TERÇA
        helper.assertTrue(CalendarioEsquinao.diaDaSemana(tercaAmanhecer) == CalendarioEsquinao.TUESDAY,
                "06:59 still belongs to Tuesday (the delivery hasn't arrived)");
        helper.assertTrue(CalendarioEsquinao.diaDaSemana(quartaManha) == CalendarioEsquinao.WEDNESDAY,
                "07:00 flips the calendar to Wednesday");
        helper.assertTrue(CalendarioEsquinao.DIA_ENTREGA == CalendarioEsquinao.WEDNESDAY,
                "Delivery day is Wednesday (My Summer Car style)");
        helper.assertTrue(CalendarioEsquinao.diasAteEntrega(CalendarioEsquinao.MONDAY) == 2,
                "From Monday there are 2 days until the delivery");
        helper.assertTrue(CalendarioEsquinao.diasAteEntrega(CalendarioEsquinao.WEDNESDAY) == 0,
                "Delivery day counts as day 0 (today)");
        // a semana do AMANHECER (06:00) é uma casa atrás: mesma deriva, outro portão
        helper.assertTrue(CalendarioEsquinao.diaDaSemanaAmanhecer(3L * 24000L) == CalendarioEsquinao.TUESDAY,
                "Sunrise week flips at 06:00 (HUD names the waking date)");
        helper.succeed();
    }

    // ==================================================== A PRATELEIRA

    /** Coloca uma prateleira padrão e devolve a BE. */
    private PrateleiraMercadoBlockEntity plantar(GameTestHelper helper) {
        return plantar(helper, 0);
    }

    private PrateleiraMercadoBlockEntity plantar(GameTestHelper helper, int secao) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, IntoxicantesMod.PRATELEIRA_MERCADO.defaultBlockState());
        var original = (PrateleiraMercadoBlockEntity) level.getBlockEntity(pos);
        var dados = new CompoundTag();
        var raiz = new CompoundTag();
        raiz.putInt("secao", secao);
        dados.put("prateleira", raiz);
        return recarregar(helper, original, dados);
    }

    private CompoundTag salvar(GameTestHelper helper, PrateleiraMercadoBlockEntity be) {
        var saida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        be.saveWithoutMetadata(saida);
        return saida.buildResult();
    }

    private PrateleiraMercadoBlockEntity recarregar(GameTestHelper helper,
            PrateleiraMercadoBlockEntity original, CompoundTag dados) {
        var nova = new PrateleiraMercadoBlockEntity(original.getBlockPos(), original.getBlockState());
        nova.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), dados));
        helper.getLevel().removeBlockEntity(original.getBlockPos());
        helper.getLevel().setBlockEntity(nova);
        return nova;
    }

    /** Item fora da tabela é gratuito; produto do catálogo usa preço e dose reais. */
    @GameTest
    public void prateleiraAceitaQualquerItemEVendePelaEtiqueta(GameTestHelper helper) {
        PrateleiraMercadoBlockEntity be = plantar(helper);
        var level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerMoney.set(player, 100);

        be.reabastecerDaMao(level, player, new ItemStack(Items.STICK, 4), 0, false);
        helper.assertTrue(be.getItem(0).getCount() == 4
                        && be.getItem(0).is(Items.STICK),
                "A prateleira continua aceitando itens fora do catálogo");
        helper.assertTrue(be.precoDoSlot(0) == 0,
                "Item fora da tabela mantém preço zero");
        be.reabastecerDaMao(level, player, new ItemStack(IntoxicantesMod.SEMENTE_UVA, 8), 4, false);
        helper.assertTrue(be.getItem(4).is(IntoxicantesMod.SEMENTE_UVA)
                        && be.precoDoSlot(4) == 4,
                "Semente reposta manualmente recebe o preço de catálogo");

        // COMPRAR: mão vazia tira 1 e NÃO cobra (produto do dono é de graça)
        be.comprar(level, player, 0, false, false);
        helper.assertTrue(be.getItem(0).getCount() == 3
                        && player.getInventory().countItem(Items.STICK) == 1,
                "Clique em item fora da tabela entrega uma unidade");
        helper.assertTrue(PlayerMoney.get(player) == 100,
                "Owner stock (no price tag) costs nothing");

        be.comprar(level, player, 4, false, false);
        helper.assertTrue(PlayerMoney.get(player) == 96 && be.getItem(4).getCount() == 4
                        && player.getInventory().countItem(IntoxicantesMod.SEMENTE_UVA) == 4,
                "Dose de quatro sementes cobra R$4 e entrega exatamente quatro");
        // A etiqueta alterada continua valendo para a dose completa.
        be.definirPreco(4, 7);
        be.comprar(level, player, 4, false, false);
        helper.assertTrue(PlayerMoney.get(player) == 89 && be.getItem(4).isEmpty()
                        && player.getInventory().countItem(IntoxicantesMod.SEMENTE_UVA) == 8,
                "Preço definido para a etiqueta cobra a dose completa sem duplicação");

        // SEM DINHEIRO: a venda é recusada (o freguês fica devendo nada)
        be.reabastecerDaMao(level, player, new ItemStack(IntoxicantesMod.SEMENTE_UVA, 4), 4, false);
        PlayerMoney.set(player, 3);
        be.comprar(level, player, 4, false, false);
        helper.assertTrue(PlayerMoney.get(player) == 3 && be.getItem(4).getCount() == 4
                        && player.getInventory().countItem(IntoxicantesMod.SEMENTE_UVA) == 8,
                "Sem saldo para a dose, dinheiro e itens permanecem intactos");
        helper.succeed();
    }

    /** O REESTOQUE de quarta: repõe os PARTIDOS da agenda do Gago, 1x/semana. */
    @GameTest
    public void reestoqueQuartaFeiraRepoeEstoqueDePartidaUmaVez(GameTestHelper helper) {
        PrateleiraMercadoBlockEntity be = plantar(helper);
        var level = helper.getLevel();
        helper.assertTrue(be.getItem(1).isEmpty(),
                "Shelves are born EMPTY (the truck brings Wednesday's stock)");

        // o freguês esvaziou o slot do vinho na terça...
        be.setItem(1, new ItemStack(IntoxicantesMod.VINHO, 1)); // sobrou 1 vinho
        be.setItem(7, new ItemStack(IntoxicantesMod.SEMENTE_LOUPULO, 3));
        be.setItem(8, new ItemStack(IntoxicantesMod.SEMENTE_UVA, 4));

        // ...e na QUARTA o caminhão COMPLETA até o estoque de partida (MSC)
        be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 4);
        helper.assertTrue(be.getItem(1).is(IntoxicantesMod.VINHO)
                        && be.getItem(1).getCount() == 8,
                "Wednesday restock completes the tag item to its starter stock");
        helper.assertTrue(be.getItem(0).is(IntoxicantesMod.CERVEJA),
                "The delivery fills every catalog slot");
        helper.assertTrue(be.getItem(7).is(IntoxicantesMod.SEMENTE_LOUPULO)
                        && be.getItem(7).getCount() == 32
                        && be.getItem(8).getCount() == 32,
                "Oito compras de quatro sementes repõem 32 unidades por slot");
        helper.assertTrue(be.precoDoSlot(1) == 25,
                "Restocked slots keep their price tag");

        // o que o DONO pôs por conta (sem preço) NUNCA é tocado pela entrega
        be.setItem(5, new ItemStack(Items.STICK, 10));
        be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 11);
        helper.assertTrue(be.getItem(5).is(Items.STICK)
                        && be.getItem(5).getCount() == 10,
                "Owner stock without a tag is never touched by the delivery");
        helper.succeed();
    }

    /** Dia errado (não-quarta) NÃO repõe nada — e a 2ª entrega da mesma semana não vem. */
    @GameTest
    public void reestoqueSoNoDiaDeEntregaAgendado(GameTestHelper helper) {
        PrateleiraMercadoBlockEntity be = plantar(helper);
        var level = helper.getLevel();

        // segunda-feira: o caminhão não vem
        be.testeForcarEntrega(level, CalendarioEsquinao.MONDAY, 2);
        helper.assertTrue(be.isEmpty(),
                "A non-Wednesday day restocks nothing");

        // a entrega de quarta repõe...
        be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 4);
        helper.assertTrue(!be.isEmpty(), "Wednesday delivers the stock");
        int cervejas = be.getItem(0).getCount();

        // ...e o MESMO dia de novo NÃO empilha (1x/dia)
        be.setItem(0, new ItemStack(be.getItem(0).getItem(), cervejas - 3)); // o freguês levou 3
        be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 4);
        helper.assertTrue(be.getItem(0).getCount() == cervejas - 3,
                "Same-day repeat delivery does not refill again");

        // ...a QUARTA da SEMANA SEGUINTE repõe de novo (o ciclo semanal fecha)
        be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 11);
        helper.assertTrue(be.getItem(0).getCount() == cervejas,
                "Next week's Wednesday restocks again (the weekly cycle)");
        helper.succeed();
    }

    /** Quebrou a gôndola: os produtos caem (nada some). */
    @GameTest
    public void prateleiraQuebradaDevolveOsProdutos(GameTestHelper helper) {
        PrateleiraMercadoBlockEntity be = plantar(helper);
        be.testeForcarEntrega(helper.getLevel(), CalendarioEsquinao.DIA_ENTREGA, 4);
        Map<Item, Integer> esperado = new HashMap<>();
        for (int slot = 0; slot < PrateleiraMercadoBlockEntity.TOTAL; slot++) {
            var item = be.getItem(slot);
            esperado.merge(item.getItem(), item.getCount(), Integer::sum);
        }
        be.soltarConteudo(helper.getLevel());
        helper.assertTrue(conteudoNoChao(helper).equals(esperado),
                "A quebra devolve exatamente todos os produtos e quantidades dos dezoito slots");
        helper.assertTrue(be.isEmpty(), "The shelf empties when it drops");
        be.soltarConteudo(helper.getLevel());
        helper.assertTrue(conteudoNoChao(helper).equals(esperado),
                "Uma segunda chamada não duplica os produtos já soltos");
        helper.succeed();
    }

    private Map<Item, Integer> conteudoNoChao(GameTestHelper helper) {
        Map<Item, Integer> itens = new HashMap<>();
        var chao = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(2));
        for (var entidade : chao) {
            itens.merge(entidade.getItem().getItem(), entidade.getItem().getCount(), Integer::sum);
        }
        return itens;
    }

    /** Produto e etiqueta apontam ao mesmo slot nas quatro faces do corredor. */
    @GameTest
    public void miraDoCliqueEscolheOSlotDaPrateleira(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 20, 30);
        for (Direction frente : Direction.Plane.HORIZONTAL) {
            var state = IntoxicantesMod.PRATELEIRA_MERCADO.defaultBlockState()
                    .setValue(PrateleiraMercadoBlock.FACING, frente);
            Direction direita = frente.getClockWise();
            for (int fileira = 0; fileira < 3; fileira++) for (int coluna = 0; coluna < 3; coluna++) {
                double across = PrateleiraMercadoBlockEntity.COLUNAS[coluna] / 16.0 - .5;
                double forward = .5;
                double x = pos.getX() + .5 + direita.getStepX() * across + frente.getStepX() * forward;
                double z = pos.getZ() + .5 + direita.getStepZ() * across + frente.getStepZ() * forward;
                for (double altura : new double[] {PrateleiraMercadoBlockEntity.NIVEIS[fileira] + .5,
                        PrateleiraMercadoBlockEntity.NIVEIS[fileira] - .5}) {
                    var hit = new BlockHitResult(new Vec3(x, pos.getY() + altura / 16.0, z), frente, pos, false);
                    helper.assertTrue(PrateleiraMercadoBlock.slotDaMira(hit, state) == fileira * 3 + coluna,
                            "Produto/etiqueta de " + frente + " mira exatamente o slot " + (fileira * 3 + coluna));
                }
            }
        }
        helper.succeed();
    }

    private record Loja(PrateleiraMercadoBlockEntity be, GagoEntity gago) {}

    /**
     * A LOJA INTEIRA do teste: prateleira em (1,1,1) e o balcão em (3,1,2)
     * com o Gago fixado no posto atrás — o fluxo novo (carrinho → caixa).
     */
    private Loja plantarLoja(GameTestHelper helper) {
        var be = plantar(helper);
        var level = helper.getLevel();
        BlockPos caixa = helper.absolutePos(new BlockPos(3, 1, 2));
        var estado = IntoxicantesMod.CAIXA_MERCADO.defaultBlockState()
                .setValue(CaixaMercadoBlock.FACING, Direction.WEST)
                .setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.LOWER);
        // o template de teste é oco: o balcão e o posto precisam de chão
        level.setBlockAndUpdate(caixa.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(caixa, estado);
        level.setBlockAndUpdate(caixa.above(),
                estado.setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos posto = CaixaMercadoBlock.posAtendente(caixa, Direction.WEST);
        level.setBlockAndUpdate(posto.below(), Blocks.STONE.defaultBlockState());
        GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(4, 1, 2));
        gago.absSnapTo(posto.getX() + 0.5, posto.getY(), posto.getZ() + 0.5,
                Direction.WEST.toYRot(), 0F);
        gago.setupPostoMercado(posto);
        return new Loja(be, gago);
    }

    private ServerPlayer freguesPerto(GameTestHelper helper, PrateleiraMercadoBlockEntity be, int saldo) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var pos = be.getBlockPos();
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        player.getInventory().clearContent();
        PlayerMoney.set(player, saldo);
        return player;
    }

    /** Cotar/cancelar/repetir o pacote nunca duplica o débito ou a entrega. */
    @GameTest
    public void confirmacaoCancelaSemCobrarECompraUmaVez(GameTestHelper helper) {
        var loja = plantarLoja(helper);
        var be = loja.be();
        be.setItem(0, new ItemStack(Items.BREAD, 3));
        var player = freguesPerto(helper, be, 100);
        helper.assertTrue(PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player,
                        be.getBlockPos(), 0, false, false),
                "O clique anota o pão no carrinho");
        var cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        helper.assertTrue(cotacao != null && cotacao.total() == 5 && cotacao.saldo() == 100
                        && cotacao.produtos().size() == 1 && cotacao.produtos().getFirst().getCount() == 1,
                "Orçamento usa a dose/preço reais e informa o saldo");
        helper.assertTrue(PlayerMoney.get(player) == 100 && be.getItem(0).getCount() == 3
                        && player.getInventory().countItem(Items.BREAD) == 0,
                "Abrir a confirmação não debita nem retira produtos");
        helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), false)), "Cancelar não compra");
        helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)), "Token cancelado não pode ser reutilizado");
        helper.assertTrue(PlayerMoney.get(player) == 100 && be.getItem(0).getCount() == 3, "Cancelar preservou carteira/estoque");
        var nova = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        var confirmar = new PrateleiraNetworking.ConfirmarCompraPayload(nova.token(), true);
        helper.assertTrue(PrateleiraNetworking.responderCompra(player, confirmar), "Confirmar compra uma dose");
        helper.assertFalse(PrateleiraNetworking.responderCompra(player, confirmar), "Replay é ignorado");
        helper.assertTrue(PlayerMoney.get(player) == 95 && be.getItem(0).getCount() == 2
                        && player.getInventory().countItem(Items.BREAD) == 1,
                "A confirmação repetida cobra R$5 e entrega exatamente um pão");
        helper.succeed();
    }

    /** A etiqueta aberta perde validade se preço, dose, componentes ou estoque mudarem. */
    @GameTest
    public void confirmacaoRevalidaProdutoPrecoDoseEQuantidade(GameTestHelper helper) {
        var loja = plantarLoja(helper);
        var be = loja.be();
        var original = new ItemStack(Items.BREAD, 3);
        be.setItem(0, original.copy());
        var player = freguesPerto(helper, be, 100);
        for (int alteracao = 0; alteracao < 4; alteracao++) {
            PrateleiraNetworking.limparCarrinhosDeTeste(player);
            PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player, be.getBlockPos(), 0, false, false);
            var cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
            switch (alteracao) {
                case 0 -> be.getItem(0).shrink(1);
                case 1 -> be.definirPreco(0, 6);
                case 2 -> be.getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("Outro pão"));
                default -> {
                    var dados = salvar(helper, be);
                    dados.getCompoundOrEmpty("prateleira").getCompoundOrEmpty("doses").putInt("0", 2);
                    be = recarregar(helper, be, dados);
                }
            }
            helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                    new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)),
                    "Cotação rejeita mudança de estoque/preço/componentes/dose: " + alteracao);
            helper.assertTrue(PlayerMoney.get(player) == 100 && player.getInventory().countItem(Items.BREAD) == 0,
                    "Cotação alterada não cobra nem entrega");
            be.setItem(0, ItemStack.EMPTY);
            be.setItem(0, original.copy());
        }
        helper.succeed();
    }

    /** A confirmação do carrinho é integral: dinheiro insuficiente não compra só parte. */
    @GameTest
    public void confirmacaoCarrinhoExigeSaldoParaOTotal(GameTestHelper helper) {
        var loja = plantarLoja(helper);
        var be = loja.be();
        be.setItem(0, new ItemStack(Items.BREAD, 2));
        be.setItem(1, new ItemStack(IntoxicantesMod.COCO_FRUTO, 2));
        var player = freguesPerto(helper, be, 8);
        helper.assertTrue(PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player,
                        be.getBlockPos(), 0, false, true),
                "Shift+clique anota a fileira inteira no carrinho");
        var cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        helper.assertTrue(cotacao != null && cotacao.total() == 9 && cotacao.produtos().size() == 2,
                "Carrinho soma uma dose por slot não vazio");
        helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)), "R$8 não paga o total de R$9");
        helper.assertTrue(PlayerMoney.get(player) == 8 && be.getItem(0).getCount() == 2 && be.getItem(1).getCount() == 2,
                "Saldo insuficiente preserva todos os produtos do carrinho");
        PlayerMoney.set(player, 20);
        cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        helper.assertTrue(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)), "Novo orçamento compra com saldo suficiente");
        helper.assertTrue(PlayerMoney.get(player) == 11 && be.getItem(0).getCount() == 1 && be.getItem(1).getCount() == 1
                        && player.getInventory().countItem(Items.BREAD) == 1
                        && player.getInventory().countItem(IntoxicantesMod.COCO_FRUTO) == 1,
                "Carrinho entrega uma dose de cada produto e cobra o total apresentado");
        helper.succeed();
    }

    @GameTest
    public void confirmacaoRejeitaDistanciaSlotInvalidoEPrateleiraQuebrada(GameTestHelper helper) {
        var loja = plantarLoja(helper);
        var be = loja.be();
        be.setItem(0, new ItemStack(Items.BREAD, 3));
        var player = freguesPerto(helper, be, 100);
        helper.assertFalse(PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player, be.getBlockPos(), -1, false, false)
                        || PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player, be.getBlockPos(), 9, false, true),
                "Índice inválido nunca entra no carrinho");
        helper.assertTrue(PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player, be.getBlockPos(), 0, false, false),
                "O pão anotado no carrinho");
        var cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        var pos = be.getBlockPos();
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 20);
        helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)), "Distância é revalidada ao confirmar");
        helper.assertTrue(PrateleiraNetworking.prepararCaixa(player, loja.gago()) == null,
                "Jogador distante não abre outro orçamento");
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.assertFalse(PrateleiraNetworking.responderCompra(player,
                new PrateleiraNetworking.ConfirmarCompraPayload(cotacao.token(), true)), "Prateleira removida cancela a cotação");
        helper.assertTrue(PlayerMoney.get(player) == 100 && player.getInventory().countItem(Items.BREAD) == 0,
                "Falhas de autorização não cobram nem entregam");
        helper.succeed();
    }

    /** Toda seção expõe a dose e o preço canônicos, limitada à pilha física do produto. */
    @GameTest
    public void secoesCobremComidasIngredientesEOitoBebidas(GameTestHelper helper) {
        var expostos = new HashSet<String>();
        for (int secao = 0; secao < TradeCatalog.secoesPrateleira(); secao++) {
            var be = plantar(helper, secao);
            be.testeForcarEntrega(helper.getLevel(), CalendarioEsquinao.DIA_ENTREGA, 4);
            var agenda = TradeCatalog.prateleira(secao);
            helper.assertTrue(agenda.size() == 9, "Cada seção possui exatamente nove produtos");
            var dados = salvar(helper, be).getCompoundOrEmpty("prateleira");
            for (int slot = 0; slot < 9; slot++) {
                var oferta = agenda.get(slot);
                var item = be.getItem(slot);
                int unidades = Math.min(oferta.stack().getMaxStackSize(), oferta.count() * oferta.stock());
                helper.assertTrue(item.is(oferta.item()) && item.getCount() == unidades,
                        oferta.id() + ": estoque em unidades respeita dose, compras e pilha máxima");
                helper.assertTrue(be.precoDoSlot(slot) == oferta.price()
                                && dados.getCompoundOrEmpty("doses").getIntOr("" + slot, -1) == oferta.count()
                                && dados.getCompoundOrEmpty("reposicao").getStringOr("" + slot, "").equals(oferta.id()),
                        oferta.id() + ": preço, dose e identidade da reposição vêm do catálogo");
                helper.assertTrue(!oferta.id().endsWith("_vazia") && !oferta.id().startsWith("semente_")
                                && !oferta.id().equals("lampada_uv") && !oferta.id().equals("glass_bottle"),
                        "Agenda alimentar não expõe recipientes vazios, sementes nem iluminação");
                expostos.add(oferta.id());
            }
        }
        var esperados = new HashSet<String>();
        for (var oferta : TradeCatalog.gago(0)) {
            if (!oferta.id().endsWith("_vazia") && !oferta.id().startsWith("semente_")
                    && !oferta.id().equals("lampada_uv") && !oferta.id().equals("glass_bottle")) {
                esperados.add(oferta.id());
            }
        }
        helper.assertTrue(expostos.equals(esperados), "As seções cobrem todos os produtos culinários do varejo");
        for (String id : new String[] {"cerveja", "vinho", "hidromel", "cachaca", "rum", "suco_detox",
                "agua_de_coco", "cha_lupulo", "pao_cevada", "coco", "bread", "milk_bucket", "honey_bottle"}) {
            helper.assertTrue(expostos.contains(id), "A cobertura inclui " + id);
        }
        helper.assertTrue(TradeCatalog.prateleira(TradeCatalog.secoesPrateleira()).equals(TradeCatalog.prateleira(0))
                        && TradeCatalog.prateleira(-1).equals(TradeCatalog.prateleira(TradeCatalog.secoesPrateleira() - 1)),
                "Seções fora do intervalo são normalizadas sem trocar sua ordem");
        helper.succeed();
    }

    /** A sobra de três uvas de um pacote de oito custa ceil(10×3/8)=R$4. */
    @GameTest
    public void pacoteIncompletoCobraSoAFracaoEntregue(GameTestHelper helper) {
        var be = plantar(helper);
        var level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerMoney.set(player, 20);
        be.reabastecerDaMao(level, player, new ItemStack(IntoxicantesMod.UVA, 3), 0, false);
        helper.assertTrue(be.precoDoSlot(0) == 10, "Preço da etiqueta representa o pacote completo de oito uvas");
        helper.assertTrue(be.comprar(level, player, 0, false, false), "O pacote incompleto pode ser comprado");
        helper.assertTrue(PlayerMoney.get(player) == 16 && be.getItem(0).isEmpty()
                        && player.getInventory().countItem(IntoxicantesMod.UVA) == 3,
                "A venda entrega só as três uvas existentes e cobra a fração arredondada para R$4");
        helper.assertFalse(be.comprar(level, player, 0, false, false), "Slot vazio não produz outra compra");
        helper.assertTrue(PlayerMoney.get(player) == 16, "Slot vazio não cobra novamente");
        helper.succeed();
    }

    /** Migrar um save legado preserva quantidades e a agenda de produtos já esgotados. */
    @GameTest
    public void migracaoLegadaPreservaEstoqueEProdutoEsgotado(GameTestHelper helper) {
        var original = plantar(helper);
        original.setItem(1, new ItemStack(IntoxicantesMod.VINHO, 3));
        original.definirPreco(1, 23);
        original.setItem(2, new ItemStack(IntoxicantesMod.HIDROMEL, 1));
        original.setItem(2, ItemStack.EMPTY);
        original.setItem(5, new ItemStack(Items.STICK, 7));
        original.setItem(7, new ItemStack(IntoxicantesMod.SEMENTE_LOUPULO, 3));
        var legado = salvar(helper, original);
        var raiz = legado.getCompoundOrEmpty("prateleira");
        raiz.remove("secao");
        raiz.remove("reposicao");
        raiz.remove("doses");
        var migrada = recarregar(helper, original, legado);
        helper.assertTrue(migrada.getItem(1).is(IntoxicantesMod.VINHO) && migrada.getItem(1).getCount() == 3
                        && migrada.precoDoSlot(1) == 23 && migrada.getItem(2).isEmpty()
                        && migrada.getItem(5).is(Items.STICK) && migrada.getItem(5).getCount() == 7
                        && migrada.getItem(7).getCount() == 3,
                "Abrir save legado preserva preços e quantidades, inclusive o slot esgotado");
        var dados = salvar(helper, migrada);
        helper.assertTrue(dados.getCompoundOrEmpty("prateleira").getCompoundOrEmpty("reposicao")
                        .getStringOr("2", "").equals("hidromel"),
                "Produto legado esgotado é identificado pela agenda antiga");
        helper.assertTrue(dados.getCompoundOrEmpty("prateleira").getCompoundOrEmpty("doses")
                        .getIntOr("1", -1) == 1
                        && dados.getCompoundOrEmpty("prateleira").getCompoundOrEmpty("doses")
                        .getIntOr("7", -1) == 4,
                "Save sem doses recupera uma garrafa e pacote de quatro sementes pelo catálogo");
        var recarregada = recarregar(helper, migrada, dados);
        helper.assertTrue(salvar(helper, recarregada).equals(dados),
                "Segundo roundtrip preserva seção, preço, dose, itens e IDs migrados");
        recarregada.testeForcarEntrega(helper.getLevel(), CalendarioEsquinao.DIA_ENTREGA, 4);
        helper.assertTrue(recarregada.getItem(2).is(IntoxicantesMod.HIDROMEL) && recarregada.getItem(2).getCount() == 8
                        && recarregada.getItem(1).is(IntoxicantesMod.VINHO) && recarregada.getItem(1).getCount() == 8
                        && recarregada.getItem(7).getCount() == 32,
                "A próxima entrega repõe os mesmos produtos legados e converte sementes para 32 unidades");
        helper.assertTrue(recarregada.getItem(5).is(Items.STICK) && recarregada.getItem(5).getCount() == 7,
                "Produto particular fora do catálogo não é substituído na migração nem na entrega");
        helper.succeed();
    }

    /**
     * A LOJA FECHA O PEDIDO: clique na gôndola anota no carrinho, abrir o
     * caixa (Gago no posto) cota, e o − do CARRINHO no payload zera a linha —
     * a mesma recotação que os botões da tela pedem (CarrinhoClientTest).
     */
    @GameTest
    public void fluxoDoCaixaAnotaCotaEAtualiza(GameTestHelper helper) {
        var loja = plantarLoja(helper);
        var be = loja.be();
        be.setItem(0, new ItemStack(Items.BREAD, 4));
        var player = freguesPerto(helper, be, 100);
        helper.assertTrue(PrateleiraNetworking.adicionarAoCarrinho(helper.getLevel(), player,
                        be.getBlockPos(), 0, false, false),
                "O clique anota o pão no carrinho");
        var cotacao = PrateleiraNetworking.prepararCaixa(player, loja.gago());
        helper.assertTrue(cotacao != null && cotacao.quantidades().getFirst() == 1
                        && cotacao.total() == 5,
                "O caixa cota o carrinho com a dose da etiqueta");
        // o botão + do client: 1 dose a mais (AtualizarCarrinhoPayload)
        var recotado = PrateleiraNetworking.atualizarCarrinho(player,
                new PrateleiraNetworking.AtualizarCarrinhoPayload(cotacao.token(), 0, 2));
        helper.assertTrue(recotado != null && recotado.quantidades().getFirst() == 2
                        && recotado.total() == 10,
                "O + recota no servidor: 2 doses, total dobrado");
        // o botão − volta à dose; remover (0) tira a linha
        recotado = PrateleiraNetworking.atualizarCarrinho(player,
                new PrateleiraNetworking.AtualizarCarrinhoPayload(cotacao.token(), 0, 1));
        helper.assertTrue(recotado != null && recotado.quantidades().getFirst() == 1,
                "O − devolve à dose");
        recotado = PrateleiraNetworking.atualizarCarrinho(player,
                new PrateleiraNetworking.AtualizarCarrinhoPayload(cotacao.token(), 0, 0));
        helper.assertTrue(recotado != null && recotado.produtos().isEmpty() && recotado.total() == 0,
                "Remover zera a linha e o total");
        helper.assertTrue(PlayerMoney.get(player) == 100 && be.getItem(0).getCount() == 4,
                "Cotar e recotar não debita nem mexe no estoque");
        helper.succeed();
    }

    /** Repor uma pilha legada gratuita de produto catalogado atualiza sua etiqueta. */
    @GameTest
    public void reposicaoManualPrecificaPilhaLegadaDoCatalogo(GameTestHelper helper) {
        var original = plantar(helper);
        original.setItem(0, new ItemStack(Items.BREAD, 2));
        original.definirPreco(0, 0);
        var legado = salvar(helper, original);
        var raiz = legado.getCompoundOrEmpty("prateleira");
        raiz.remove("secao");
        raiz.remove("reposicao");
        var be = recarregar(helper, original, legado);
        helper.assertTrue(be.precoDoSlot(0) == 0 && be.getItem(0).getCount() == 2,
                "A leitura preserva o preço legado antes de uma nova reposição");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerMoney.set(player, 20);
        var mao = new ItemStack(Items.BREAD, 1);
        helper.assertTrue(be.reabastecerDaMao(helper.getLevel(), player, mao, 0, false)
                        && mao.isEmpty() && be.getItem(0).getCount() == 3 && be.precoDoSlot(0) == 5,
                "Merge de pão manual aplica preço do catálogo sem duplicar unidades");
        be.comprar(helper.getLevel(), player, 0, false, false);
        helper.assertTrue(PlayerMoney.get(player) == 15 && be.getItem(0).getCount() == 2
                        && player.getInventory().countItem(Items.BREAD) == 1,
                "O pão reposto cobra R$5 e entrega uma unidade");
        helper.succeed();
    }
}
