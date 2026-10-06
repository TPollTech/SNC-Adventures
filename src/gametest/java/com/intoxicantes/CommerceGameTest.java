package com.intoxicantes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Exercises vanilla offers, actual NPC save data and the daily stock policy. */
public class CommerceGameTest {
    @GameTest
    public void immaturePlantsCannotGenerateSellableHarvest(GameTestHelper helper) {
        var crops = java.util.List.of(IntoxicantesMod.LOUPULO_PLANT, IntoxicantesMod.UVA_PLANT,
                IntoxicantesMod.CAFE_PLANT, IntoxicantesMod.MACONHA_PLANT, IntoxicantesMod.PAPOULA_PLANT);
        var seeds = java.util.List.of(IntoxicantesMod.SEMENTE_LOUPULO, IntoxicantesMod.SEMENTE_UVA,
                IntoxicantesMod.SEMENTE_CAFE, IntoxicantesMod.SEMENTE_MACONHA, IntoxicantesMod.SEMENTE_PAPOULA);
        for (int i = 0; i < crops.size(); i++) {
            var crop = (UvCropBlock) crops.get(i);
            var product = java.util.List.of(IntoxicantesMod.LOUPULO_FRESCO, IntoxicantesMod.UVA,
                    IntoxicantesMod.CAFE_VERDE, IntoxicantesMod.MACONHA_SEDA, IntoxicantesMod.OPIO).get(i);
            var state = crop.defaultBlockState().setValue(UvCropBlock.AGE, 0).setValue(UvCropBlock.UV_AGE, 0);
            var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), null);
            helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(product)), "Breaking seedlings must not create sellable products");
            final var seed = seeds.get(i);
            helper.assertTrue(drops.stream().filter(stack -> stack.is(seed)).mapToInt(ItemStack::getCount).sum() == 1,
                    "A seedling returns only its planted seed");
            state = state.setValue(UvCropBlock.AGE, 4);
            helper.assertTrue(crop.isRandomlyTicking(state), "Fully grown crop must still tick for UV ripening");
            drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), null);
            helper.assertTrue(drops.stream().filter(stack -> stack.is(product)).mapToInt(ItemStack::getCount).sum() == 1,
                    "Grown but unripe crop yields one product");
            state = state.setValue(UvCropBlock.UV_AGE, 3);
            if (crop instanceof UvaParreiraBlock) {
                helper.assertTrue(crop.isRandomlyTicking(state),
                        "A mature vine keeps ticking to ripen its independently regrowing bunches");
            } else {
                helper.assertFalse(crop.isRandomlyTicking(state), "Fully ripe crop stops ticking");
            }
            drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), null);
            // v1.2.16: regra UNIFORME (gerador = disk = teste): esperar a
            // maturação vale 3x o produto pra TODAS as culturas (antes o lúpulo
            // tinha 4 afinado à mão — a regeneração das textures expôs a deriva)
            helper.assertTrue(drops.stream().filter(stack -> stack.is(product)).mapToInt(ItemStack::getCount).sum() == 3,
                    "Ripe crops yield the uniform 3x bonus (seed always returns)");
        }
        helper.succeed();
    }

    @GameTest
    public void sharedQuotaAndInvalidCustomersCannotDuplicateMoney(GameTestHelper helper) {
        GagoEntity merchant = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        merchant.refreshTradeStock();
        var player = helper.makeMockServerPlayerInLevel();
        player.absSnapTo(merchant.getX(), merchant.getY(), merchant.getZ());
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.UVA, 16));
        PlayerMoney.set(player, 0);
        helper.assertTrue(MarketTransactions.trade(player, merchant, 1, true) == MarketTransactions.Result.INVALID,
                "Trade without an active merchant session is rejected");
        merchant.setTradingPlayer(player);
        var entry = TradeCatalog.harvests().get(1);
        for (int i = 0; i < entry.stock() - 1; i++) merchant.marketInventory().consume(entry);
        helper.assertTrue(MarketTransactions.trade(player, merchant, 1, true) == MarketTransactions.Result.OK,
                "Last remaining quota can be used");
        helper.assertTrue(MarketTransactions.trade(player, merchant, 1, true) == MarketTransactions.Result.OUT_OF_STOCK,
                "Repeated request cannot exceed shared quota");
        helper.assertTrue(PlayerMoney.get(player) == 6 && player.getInventory().countItem(IntoxicantesMod.UVA) == 8,
                "Rejected repeat preserves money and remaining crop");
        helper.assertTrue(MarketTransactions.trade(player, merchant, -1, false) == MarketTransactions.Result.INVALID,
                "Negative index rejected");
        helper.assertTrue(MarketTransactions.trade(player, merchant, 99, false) == MarketTransactions.Result.INVALID,
                "Locked or nonexistent offers cannot be bought");
        merchant.setTradingPlayer(null);
        helper.succeed();
    }

    @GameTest
    public void harvestPaysExactlyAndRejectsShortPayment(GameTestHelper helper) {
        GagoEntity merchant = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        merchant.refreshTradeStock();
        var player = helper.makeMockServerPlayerInLevel();
        player.absSnapTo(merchant.getX(), merchant.getY(), merchant.getZ());
        merchant.setTradingPlayer(player);
        PlayerMoney.set(player, 100);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 7));
        helper.assertTrue(MarketTransactions.trade(player, merchant, 0, true) == MarketTransactions.Result.NO_HARVEST,
                "Insufficient harvest must not pay");
        helper.assertTrue(PlayerMoney.get(player) == 100 && player.getInventory().getItem(0).getCount() == 7,
                "Rejected trade must preserve cash and harvest");
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 4));
        player.getInventory().setItem(1, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 8));
        helper.assertTrue(MarketTransactions.trade(player, merchant, 0, true) == MarketTransactions.Result.OK,
                "Full harvest is purchased across multiple inventory slots");
        helper.assertTrue(PlayerMoney.get(player) == 106, "Eight hops pay exactly six reais");
        helper.assertTrue(MarketTransactions.countHarvest(player, TradeCatalog.harvests().get(0)) == 4,
                "Exactly eight crops consumed");
        helper.assertTrue(MarketTransactions.trade(player, merchant, 0, false) == MarketTransactions.Result.OK,
                "Earned cash can buy a beverage");
        helper.assertTrue(PlayerMoney.get(player) == 91, "Beverage deducts the displayed price");
        helper.assertTrue(player.getInventory().countItem(IntoxicantesMod.CERVEJA) == 1,
                "Purchase delivers the item");
        merchant.setTradingPlayer(null);
        helper.succeed();
    }

    @GameTest
    public void beverageIngredientsPayCorrectBatchesWithoutResaleProfit(GameTestHelper helper) {
        var expected = java.util.List.of(IntoxicantesMod.LOUPULO_FRESCO, IntoxicantesMod.UVA,
                net.minecraft.world.item.Items.WHEAT, IntoxicantesMod.CANA_DE_ACUCAR,
                net.minecraft.world.item.Items.HONEY_BOTTLE, net.minecraft.world.item.Items.GLASS_BOTTLE);
        var ingredients = TradeCatalog.harvests();
        helper.assertTrue(ingredients.stream().map(TradeCatalog.Entry::item).toList().equals(expected),
                "Gago buys exactly the six ingredients used by his beverage recipes");
        var merchant = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        merchant.refreshTradeStock();
        var player = helper.makeMockServerPlayerInLevel();
        player.absSnapTo(merchant.getX(), merchant.getY(), merchant.getZ());
        merchant.setTradingPlayer(player);
        PlayerMoney.set(player, 0);
        int balance = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            var entry = ingredients.get(i);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(entry.item(), entry.count() - 1));
            helper.assertTrue(MarketTransactions.trade(player, merchant, i, true) == MarketTransactions.Result.NO_HARVEST,
                    "Each ingredient requires its own full batch");
            player.getInventory().setItem(0, entry.stack());
            helper.assertTrue(MarketTransactions.trade(player, merchant, i, true) == MarketTransactions.Result.OK,
                    "Each ingredient can be sold");
            balance += entry.price();
            helper.assertTrue(PlayerMoney.get(player) == balance && player.getInventory().countItem(entry.item()) == 0,
                    "Exact ingredients and containers are handed over, exact price paid");
            helper.assertTrue(merchant.marketInventory().remaining(entry) == entry.stock() - 1, "Only one quota consumed");
            for (var retail : TradeCatalog.gago(3)) if (retail.item() == entry.item()) {
                int discounted = Math.max(1, (int) Math.floor(retail.price() * 0.7));
                helper.assertTrue(discounted * entry.count() > entry.price() * retail.count(),
                        "Even maximum loyalty discount cannot profit from buying bottles to resell");
            }
        }
        merchant.setTradingPlayer(null);
        helper.succeed();
    }

    @GameTest
    public void everyPaymentFitsAndStreetPricesAreLower(GameTestHelper helper) {
        var shop = TradeCatalog.gago(0);
        helper.assertTrue(shop.size() == 71 && TradeCatalog.gago(3).size() == 76,
                "O varejo reúne 21 produtos históricos e 50 alimentos; fidelidade acrescenta 5 exclusivos");
        helper.assertTrue(FidelidadeData.primeiroExclusivo() == shop.size(),
                "A fronteira de exclusivos acompanha o catálogo básico, incluindo os alimentos");
        String[] historicos = {"cerveja", "vinho", "hidromel", "cachaca", "rum", "suco_detox",
                "agua_de_coco", "cha_lupulo", "cerveja_vazia", "vinho_vazia", "cachaca_vazia",
                "hidromel_vazia", "rum_vazia", "suco_detox_vazia", "agua_de_coco_vazia", "cha_lupulo_vazia",
                "semente_loupulo", "semente_uva", "semente_cevada", "lampada_uv", "glass_bottle"};
        var fidelidade = TradeCatalog.gago(3);
        var ids = new HashSet<String>();
        var produtos = new HashSet<Item>();
        for (int i = 0; i < shop.size(); i++) {
            var entrada = shop.get(i);
            helper.assertTrue(ids.add(entrada.id()) && produtos.add(entrada.item()),
                    "O varejo comum não duplica ID nem produto: " + entrada.id());
            helper.assertTrue(fidelidade.get(i).equals(entrada),
                    "A fidelidade preserva o catálogo comum antes dos exclusivos");
            if (i < historicos.length) helper.assertTrue(entrada.id().equals(historicos[i]),
                    "ID histórico permanece no índice " + i + ": " + historicos[i]);
        }
        HashSet<Item> seen = new HashSet<>();
        for (int seed = 0; seed < 100; seed++) {
            MerchantOffers street = TradeCatalog.traficante(RandomSource.create(seed), seed);
            helper.assertTrue(street.size() == 3, "Street dealer has exactly three offers");
            HashSet<Item> dailyItems = new HashSet<>();
            for (MerchantOffer offer : street) {
                Item item = offer.getResult().getItem();
                helper.assertTrue(dailyItems.add(item), "No duplicate street offer");
                seen.add(item);
                helper.assertTrue(offer.getMaxUses() == 4, "Street stock is scarce");
                for (var retail : shop) {
                    if (retail.item() == item) {
                        helper.assertTrue(offer.getCostA().getCount() < retail.price(),
                                "Street price must be lower than the market price");
                    }

                }
                for (var harvest : TradeCatalog.harvests()) {
                    if (harvest.item() == item) helper.assertTrue(offer.getCostA().getCount() * harvest.count() > harvest.price(),
                            "Buying to resell must not create free money");
                }
            }
        }
        helper.assertTrue(seen.size() == 7, "Rotation includes the entire street catalog");
        // A rua vende drogas; nenhum desses produtos entra no varejo culinário.
        for (Item item : seen) {
            helper.assertTrue(shop.stream().noneMatch(entry -> entry.item() == item),
                    "Street offers never sell what the Gago's grocery market sells");
        }
        helper.succeed();
    }

    /**
     * v1.2.66 — A DIVISÃO DA ESQUINA: Gago compra SÓ insumo de bebida e vende
     * SÓ bebida; camelo compra SÓ cru de droga e vende SÓ droga. Nenhum item
     * atravessa (a economia fica interligada sem virar free money).
     */
    @GameTest
    public void dealerBuysRawDrugsAndGagoBuysOnlyBeverageIngredients(GameTestHelper helper) {
        // o cru do camelo não aparece em NENHUMA lista do Gago
        for (var cru : TradeCatalog.cruDrogas()) {
            helper.assertTrue(TradeCatalog.harvests().stream().noneMatch(h -> h.item() == cru.item()),
                    "Gago never buys the dealer's raw drugs");
            helper.assertTrue(TradeCatalog.gago(3).stream().noneMatch(h -> h.item() == cru.item()),
                    "Gago's beverage bar never sells raw drugs");
            // e vender o lote paga MENOS que o varejo das mesmas unidades (sem free money)
            var varejo = TradeCatalog.traficanteFixo().stream()
                    .filter(e -> e.item() == cru.item()).findFirst().orElseThrow();
            helper.assertTrue(Math.round(varejo.price() * 0.9F) < cru.count() * varejo.price(),
                    "The batch payout is below the retail value of the same units");
        }
        // a colheita do Gago não aparece no catálogo do camelo (nem varejo, nem cru)
        for (var h : TradeCatalog.harvests()) {
            helper.assertTrue(TradeCatalog.traficanteFixo().stream().noneMatch(e -> e.item() == h.item()),
                    "Dealer never sells the Gago's beverage ingredients");
            helper.assertTrue(TradeCatalog.cruDrogas().stream().noneMatch(e -> e.item() == h.item()),
                    "Dealer never buys beverage ingredients");
        }
        // balcão do Gago não tem NENHUMA droga (v1.2.66: bebida é com ele, droga não)
        var drogasDaRua = TradeCatalog.traficanteFixo().stream().map(TradeCatalog.Entry::item).toList();
        for (int tier = 0; tier <= 3; tier++) {
            for (var entry : TradeCatalog.gago(tier)) {
                helper.assertTrue(!drogasDaRua.contains(entry.item()),
                        "Gago's bar sells no drugs at tier " + tier);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void restockAtSevenIsDeferredWhileTradingAndIgnoresRewind(GameTestHelper helper) {
        DailyTradeStock stock = new DailyTradeStock();
        MerchantOffers offers = stock.refresh(null, 1000, false, () -> TradeCatalog.traficante(RandomSource.create(7), 7L));
        offers.get(0).setToOutOfStock();
        helper.assertTrue(stock.refresh(offers, 24999, false, () -> TradeCatalog.traficante(RandomSource.create(7), 7L)) == offers,
                "No early refill before 07:00");
        helper.assertTrue(stock.refresh(offers, 25000, true, () -> TradeCatalog.traficante(RandomSource.create(7), 7L)) == offers,
                "An open trade keeps its offers stable");
        MerchantOffers next = stock.refresh(offers, 25000, false, () -> TradeCatalog.traficante(RandomSource.create(7), 7L));
        helper.assertFalse(next.get(0).isOutOfStock(), "Stock returns at 07:00 after trading ends");
        next.get(0).setToOutOfStock();
        helper.assertTrue(stock.refresh(next, 0, false, () -> TradeCatalog.traficante(RandomSource.create(7), 7L)) == next,
                "Rewinding time does not replenish stock");
        helper.assertTrue(stock.refresh(next, 25000, false, () -> TradeCatalog.traficante(RandomSource.create(7), 7L)) == next,
                "Repeating the same morning does not replenish stock");
        helper.assertTrue(DailyTradeStock.tradingDay(999) == -1 && DailyTradeStock.tradingDay(1000) == 0,
                "First-day boundary is exactly 07:00");
        helper.succeed();
    }

    @GameTest
    public void oldOffersMigrateWithoutRestoringUsedStock(GameTestHelper helper) {
        MerchantOffers old = new MerchantOffers();
        old.add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(IntoxicantesMod.REAL, 15),
                new ItemStack(IntoxicantesMod.CERVEJA), 99, 0, 0.05F));
        for (int i = 0; i < 9; i++) old.get(0).increaseUses();
        MarketInventory stock = new MarketInventory();
        stock.refresh(1000, false, old);
        helper.assertTrue(stock.remaining(TradeCatalog.gago(0).get(0)) == 0, "Migration preserves spent stock");
        stock.refresh(1200, false, old);
        helper.assertTrue(stock.remaining(TradeCatalog.gago(0).get(0)) == 0, "Migration only runs once");
        stock.refresh(25000, true, old);
        helper.assertTrue(stock.remaining(TradeCatalog.gago(0).get(0)) == 0, "Open shop cannot restock");
        stock.refresh(25000, false, old);
        helper.assertTrue(stock.remaining(TradeCatalog.gago(0).get(0)) == 8, "Restock restores daily quota");
        helper.succeed();
    }

    @GameTest
    public void npcSaveReloadPreservesStockAndMerchantsAreIndependent(GameTestHelper helper) {
        GagoEntity first = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        GagoEntity second = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(3, 1, 1));
        first.refreshTradeStock();
        second.refreshTradeStock();
        var beer = TradeCatalog.gago(0).get(0);
        for (int i = 0; i < beer.stock(); i++) first.marketInventory().consume(beer);
        helper.assertTrue(second.marketInventory().remaining(beer) == 8, "Each merchant owns its stock");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        first.saveWithoutId(output);
        var input = TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult());
        second.load(input);
        second.refreshTradeStock();
        helper.assertTrue(second.marketInventory().remaining(beer) == 0, "Reload must not refill used stock");
        TraficanteEntity dealer = helper.spawn(IntoxicantesMod.TRAFICANTE, new BlockPos(2, 1, 3));
        dealer.refreshTradeStock();
        dealer.getOffers().get(0).increaseUses();
        var streetOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        dealer.saveWithoutId(streetOutput);
        Item originalItem = dealer.getOffers().get(0).getResult().getItem();
        dealer.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), streetOutput.buildResult()));
        dealer.refreshTradeStock();
        helper.assertTrue(dealer.getOffers().get(0).getUses() == 1 && dealer.getOffers().get(0).getResult().is(originalItem),
                "Street selection and purchases survive reload");
        helper.succeed();
    }

    /** Ajusta o saldo pelo ledger real, exercitando persistência como os outros testes. */
    private static void setSaldoTeste(ServerPlayer player, int valor) {
        PlayerMoney.set(player, valor);
    }

    @GameTest
    public void balanceSaturatesAtCeilingAndNeverGoesNegative(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        // v1.2.2: 2.000.000.000 + 2.000.000.000 em int = saldo NEGATIVO que o set zerava
        setSaldoTeste(player, 2_000_000_000);
        PlayerMoney.add(player, 2_000_000_000);
        helper.assertTrue(PlayerMoney.get(player) == Integer.MAX_VALUE,
                "Overflow of add saturates at Integer.MAX_VALUE");
        PlayerMoney.add(player, 100);
        helper.assertTrue(PlayerMoney.get(player) == Integer.MAX_VALUE,
                "Ceiling holds after repeated additions");
        // /pagar (subtrair + add no destinatario) no limite: quem paga nunca fica negativo
        setSaldoTeste(player, 1_000_000_000);
        PlayerMoney.subtrair(player, 1_500_000_000);
        helper.assertTrue(PlayerMoney.get(player) == 1_000_000_000,
                "Payer without funds is untouched by subtrair");
        PlayerMoney.add(player, Integer.MAX_VALUE);
        helper.assertTrue(PlayerMoney.get(player) == Integer.MAX_VALUE,
                "Receiver of an enormous payment saturates instead of wrapping");
        PlayerMoney.add(player, -50);
        helper.assertTrue(PlayerMoney.get(player) == Integer.MAX_VALUE - 50,
                "Negative adjustment still works under saturation");
        helper.succeed();
    }

    /** Config de teste: troca a instancia em memoria (o config real no disco fica intocado). */
    private static void configDeTeste(ModConfig alteracoes) {
        try {
            var instancia = ModConfig.class.getDeclaredField("instancia");
            instancia.setAccessible(true);
            instancia.set(null, alteracoes);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("instancia inacessivel", e);
        }
    }

    /** Igual ao mock da GameTestHelper (perfil + connection embutida + placeNewPlayer),
     *  mas com gameMode() = SURVIVAL: o mock vanilla e' CREATIVE hardcoded na
     *  subclasse anonima, inalteravel por reflexao ou mutacao de campos. */
    private static ServerPlayer vitimaSobrevivente(GameTestHelper helper) {
        var level = helper.getLevel();
        var perfil = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "vitima-teste");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(perfil, false);
        ServerPlayer vitima = new ServerPlayer(level.getServer(), level, perfil, cookie.clientInformation()) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        var conexao = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conexao);
        level.getServer().getPlayerList().placeNewPlayer(conexao, vitima, cookie);
        // Complete the vanilla load handshake: unloaded clients are immune to damage.
        vitima.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return vitima;
    }

    /** Injeta compras de fidelidade no mapa estatico sem tocar em arquivo. */
    private static void fidelidadeTeste(ServerPlayer player, int compras) {
        try {
            var campo = FidelidadeData.class.getDeclaredField("compras");
            campo.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, Integer> mapa = (java.util.Map<java.util.UUID, Integer>) campo.get(null);
            mapa.put(player.getUUID(), compras);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("compras inacessivel", e);
        }
    }

    /** Conquista concedida? (holder inexistente = dados de progressao quebrados) */
    private static boolean progresso(GameTestHelper helper, ServerPlayer player,
            net.minecraft.resources.Identifier id) {
        var holder = player.level().getServer().getAdvancements().get(id);
        helper.assertTrue(holder != null, "Advancement exists in the datapack: " + id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest
    public void loyaltyCardTiersAndAdvancementsProgressCorrectly(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        fidelidadeTeste(player, 0);
        helper.assertTrue(FidelidadeData.getTier(player) == 0 && FidelidadeData.descontoDe(player) == 0,
                "New customer starts on tier 0 with no discount");
        // 4 compras: ainda Fregues comum (a RAIZ sai ja na 1a compra; os tiers sao 5/15/30)
        for (int i = 0; i < 4; i++) {
            FidelidadeData.registrarCompra(player);
        }
        helper.assertTrue(FidelidadeData.getTier(player) == 0,
                "Four purchases stay on tier 0");
        helper.assertTrue(progresso(helper, player, Progressoes.RAIZ),
                "The very first purchase grants the root advancement");
        helper.assertFalse(progresso(helper, player, Progressoes.FREGUES_DA_ESQUINA),
                "Four purchases do not reach Fregues da Esquina");
        // 5a compra: Fregues da Esquina (tier 1, 5%) + raiz
        FidelidadeData.registrarCompra(player);
        helper.assertTrue(FidelidadeData.getTier(player) == 1 && FidelidadeData.descontoDe(player) == 5,
                "Five purchases reach tier 1 with 5% discount");
        helper.assertTrue(progresso(helper, player, Progressoes.RAIZ),
                "First purchase grants the root advancement");
        helper.assertTrue(progresso(helper, player, Progressoes.FREGUES_DA_ESQUINA),
                "Tier 1 grants Fregues da Esquina");
        helper.assertFalse(progresso(helper, player, Progressoes.DONO_DA_ESQUINA),
                "Tier 3 advancement is still locked");
        // pulo direto pro Dono da Esquina (30 compras, 15%)
        fidelidadeTeste(player, 29);
        FidelidadeData.registrarCompra(player);
        helper.assertTrue(FidelidadeData.getTier(player) == 3 && FidelidadeData.descontoDe(player) == 15,
                "Thirty purchases reach tier 3 with 15% discount");
        helper.assertTrue(progresso(helper, player, Progressoes.DONO_DA_ESQUINA),
                "Tier 3 grants Dono da Esquina");
        helper.assertTrue(FidelidadeData.precoComDesconto(player, 100) == 85,
                "Tier 3 discount prices a 100-real item at 85");
        helper.assertTrue(FidelidadeData.precoComDesconto(player, 1) == 1,
                "Discount never drops the price below one real");
        fidelidadeTeste(player, 0); // nao polui o JSON do mundo de teste
        helper.succeed();
    }

    @GameTest
    public void perfectHarvestAdvancementOnlyAtFullRipeness(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var crop = (UvCropBlock) IntoxicantesMod.MACONHA_PLANT;
        // imatura (AGE 2, UV 0): colher NAO concede
        helper.setBlock(new BlockPos(3, 1, 1), net.minecraft.world.level.block.Blocks.FARMLAND);
        var verde = crop.defaultBlockState().setValue(UvCropBlock.AGE, 2).setValue(UvCropBlock.UV_AGE, 0);
        helper.setBlock(new BlockPos(3, 2, 1), verde);
        crop.playerWillDestroy(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 1)), verde, player);
        helper.assertFalse(progresso(helper, player, Progressoes.COLHEITA_PERFEITA),
                "Harvesting an unripe crop grants nothing");
        // no ponto (AGE 4 + UV 3): concede
        helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.FARMLAND);
        var perfeita = crop.defaultBlockState().setValue(UvCropBlock.AGE, 4).setValue(UvCropBlock.UV_AGE, 3);
        helper.setBlock(new BlockPos(1, 2, 1), perfeita);
        crop.playerWillDestroy(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), perfeita, player);
        helper.assertTrue(progresso(helper, player, Progressoes.COLHEITA_PERFEITA),
                "Harvesting at AGE 4 + UV 3 grants Colheita Perfeita");
        helper.succeed();
    }

    @GameTest
    public void gagoEscalatesAgainstJointSmokeAtCounter(GameTestHelper helper) {
        GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        // vitima sobrevivente (o mock do helper e CREATIVE hardcoded): fumante
        var fumante = vitimaSobrevivente(helper);
        fumante.absSnapTo(gago.getX() + 1.5, gago.getY(), gago.getZ());
        fumante.getInventory().clearContent();
        fumante.getInventory().setItem(0, new ItemStack(IntoxicantesMod.BASEADO));
        fumante.getFoodData().setFoodLevel(20);
        fumante.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(IntoxicantesMod.BASEADO));
        fumante.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(fumante.isUsingItem(), "Victim is puffing the joint next to the counter");

        // 1a conversa: reclama (fumaca.1), nao apaga ainda
        gago.fiscalizarFumaca();
        helper.assertTrue(gago.fumacaConversas == 1 && gago.cooldownFumaca > 0,
                "First smoke talk scolds and starts the throttle");
        helper.assertTrue(fumante.isUsingItem(), "First offense only scolds");

        // throttle ativo: sem fala nova mesmo com fumaca rolando
        gago.fiscalizarFumaca();
        helper.assertTrue(gago.fumacaConversas == 1,
                "Throttle blocks spam while cooling down");

        // 2a conversa (throttle expirado): reclama de novo, ainda nao apaga
        gago.cooldownFumaca = 0;
        gago.fiscalizarFumaca();
        helper.assertTrue(gago.fumacaConversas == 2, "Second talk escalates the scolding");
        helper.assertTrue(fumante.isUsingItem(), "Second talk still only scolds");

        // 3a conversa: apaga na mao — uso interrompido + 10s de cooldown
        gago.cooldownFumaca = 0;
        gago.fiscalizarFumaca();
        helper.assertTrue(!fumante.isUsingItem(), "Third talk SNATCHES the joint (use interrupted)");
        helper.assertTrue(fumante.getCooldowns().isOnCooldown(new ItemStack(IntoxicantesMod.BASEADO)),
                "Snuffed joint goes on a 10s cooldown");
        helper.assertTrue(gago.fumacaConversas == 0,
                "Counter resets after the snuff so the next joint starts over");

        // com a fumaça sumida (uso interrompido), contador nao reaparece do nada
        resetFumacaTeste(gago);
        helper.succeed();
    }

    /** Zera o estado de fumaca do Gago (isolamento entre testes). */
    private static void resetFumacaTeste(GagoEntity gago) {
        gago.fumacaConversas = 0;
        gago.cooldownFumaca = 0;
    }

    /** Uma passada de 1s com o relógio do mock alinhado na janela (20 ticks). */
    private static void passaSegundoTeste(ServerPlayer player) {
        player.tickCount = 20;
        Embriaguez.tickJogador(player);
    }

    /** Injeta a sobriedade acumulada (reflection, padrão dos outros helpers). */
    private static void sobriedadeTeste(ServerPlayer player, int segundos) {
        try {
            var campo = Embriaguez.class.getDeclaredField("SOBRIEDADE");
            campo.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, Integer> mapa =
                    (java.util.Map<java.util.UUID, Integer>) campo.get(null);
            mapa.put(player.getUUID(), segundos); // a passada soma +1 e atinge o alvo
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("SOBRIEDADE inacessivel", e);
        }
    }

    @GameTest
    public void drunkennessDosesScrambleChatAndDecayOverTime(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        Embriaguez.setNivelTeste(player, 0);

        // dose de cachaça (3): sobe pra 3 = LIMIAR FONAR
        Embriaguez.beber(player, 3);
        helper.assertTrue(Embriaguez.nivel(player) == 3,
                "One cachaça dose reaches the slurred speech threshold");

        // fala limpa abaixo do limiar, fonada no limiar
        String fala = "Vou levar duas cervejas";
        helper.assertTrue(Embriaguez.fonar(fala, 2).equals(fala),
                "Below threshold speech stays clean");
        String fonada = Embriaguez.fonar(fala, 3);
        helper.assertTrue(!fonada.equals(fala),
                "At the threshold the speech comes out scrambled");
        helper.assertTrue(fonada.length() >= fala.length(),
                "Slurring stretches or swaps but never loses content");
        // nivel alto embaralha MAIS que o limiar
        helper.assertTrue(!Embriaguez.fonar(fala, 6).equals(fonada),
                "Higher level scrambles harder than the threshold");
        // v1.2.10: caído soluça NA FALA (*hic!* intercalado)
        helper.assertTrue(Embriaguez.fonar(fala, 6).contains("*hic!*"),
                "Passed-out level punctuates the speech with hiccups");

        // decay: 60 passadas de 1s derrubam 1 nivel (decay padrao = 60s)
        Embriaguez.setNivelTeste(player, 3);
        for (int i = 0; i < 60; i++) {
            passaSegundoTeste(player);
        }
        helper.assertTrue(Embriaguez.nivel(player) == 2,
                "A clean minute of sobriety drops one level");

        // dose nova zera a conta da sobriedade (o gole reseta o relógio)
        Embriaguez.beber(player, 1);
        helper.assertTrue(Embriaguez.nivel(player) == 3,
                "New dose tops the level back up");

        Embriaguez.setNivelTeste(player, 0);
        helper.succeed();
    }

    @GameTest
    public void drunkennessCapRefluxAndPersistenceRoundTrip(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.embriaguezDecaySegundos = 10;
        teste.embriaguezLimiarFonar = 3;
        teste.embriaguezLimiarHic = 5;
        teste.embriaguezCap = 4;
        teste.embriaguezTrancaTeto = true;
        configDeTeste(teste);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            Embriaguez.setNivelTeste(player, 0);

            // aponta a persistencia pra um arquivo temporario (game test nao tem mundo)
            java.io.File tmp;
            try {
                tmp = java.io.File.createTempFile("intoxicantes_embriaguez", ".json");
            } catch (java.io.IOException e) {
                throw new AssertionError("temp file indisponivel", e);
            }
            tmp.deleteOnExit();
            Embriaguez.initTeste(tmp);

            // config manda: 4 doses (2+2) enchem o TETO CONFIGURADO (não um 7 fixo)
            Embriaguez.beber(player, 2);
            Embriaguez.beber(player, 2);
            helper.assertTrue(Embriaguez.nivel(player) == 4,
                    "Cap comes from the config, not a hardcoded 7");

            // 5a dose no teto: REFLUXO — o corpo devolve e o nivel fica no cap
            Embriaguez.beber(player, 3);
            helper.assertTrue(Embriaguez.nivel(player) == 4,
                    "Ceiling with the cork on stays at the cap (reflux)");

            // persistencia: estado vai pro arquivo, memoria zera, load reconstroi
            Embriaguez.save();
            Embriaguez.setNivelTeste(player, 0);
            helper.assertTrue(Embriaguez.nivel(player) == 0,
                    "Memory wiped to prove the reload does the work");
            Embriaguez.loadTeste();
            helper.assertTrue(Embriaguez.nivel(player) == 4,
                    "Drunkenness survives save/load round-trip (relog/restart)");

            // decay usa o config: 10s acumulados + uma passada de 1s = 1 nivel
            sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
            passaSegundoTeste(player);
            helper.assertTrue(Embriaguez.nivel(player) == 3,
                    "Decay interval comes from the config (one level per 10s here)");

            Embriaguez.setNivelTeste(player, 0);
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    @GameTest
    public void gagoRidesADrunkCustomerAtTheCounter(GameTestHelper helper) {
        GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        var player = helper.makeMockServerPlayerInLevel();
        player.absSnapTo(gago.getX() + 1.5, gago.getY(), gago.getZ());

        // doses sem cruzar o limiar: nada de deboche
        Embriaguez.beber(player, 1);
        Embriaguez.beber(player, 1);
        helper.assertTrue(gago.cooldownZoacao == 0,
                "Staying under the threshold earns no mockery");

        // cruzou o limiar da fala bêbada: o deboche vem SOZINHO (beber -> Gago)
        Embriaguez.beber(player, 1);
        helper.assertTrue(gago.cooldownZoacao > 0,
                "Crossing the slurred threshold pulls the Gago's mockery automatically");

        // throttle: deboche recente bloqueia repetição (cooldown nao reseta pra 600)
        gago.cooldownZoacao = 590;
        gago.zoFreguesBebado(player);
        helper.assertTrue(gago.cooldownZoacao <= 590,
                "Throttle blocks mockery spam while cooling down");

        // fregues sóbrio de novo: gatilho direto não debocha, estado limpo
        Embriaguez.setNivelTeste(player, 0);
        gago.cooldownZoacao = 0;
        gago.zoFreguesBebado(player);
        helper.assertTrue(gago.cooldownZoacao == 0,
                "A sober customer gets no mockery even from a direct trigger");
        helper.succeed();
    }

    @GameTest
    public void gagoShotgunFollowsConfigNotHardcodedValues(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.escopetaCooldownTicks = 10;
        teste.escopetaDanoPorBalim = 0.5F;
        teste.escopetaBalins = 1;
        teste.escopetaAlcanceMaximo = 12;
        configDeTeste(teste);
        try {
            // canAttack() recusa alvo Player em dificuldade PACIFICA: o servidor de
            // game test nasce peaceful — NORMAL pra o Gago poder ficar puto kkkk
            helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
            // makeMockServerPlayerInLevel() devolve um mock cuja subclasse anonima
            // (GameTestHelper$3) SOBRESCREVE gameMode() pra devolver CREATIVE
            // hardcoded no bytecode: nenhuma mutacao no ServerPlayerGameMode muda
            // isso, e asValidTarget() descarta alvo criativo. Criamos um irmao do
            // mock do vanilla em modo sobrevivencia — mesmo padrao do helper.
            var vitima = vitimaSobrevivente(helper);
            vitima.absSnapTo(gago.getX() + 2, gago.getY(), gago.getZ());
            vitima.setPermanentlyInvulnerable(false);
            vitima.setInvulnerableTime(0);
            vitima.getAbilities().invulnerable = false; // Player.hurtServer recusa dano se true
            float vidaAntes = vitima.getHealth();
            gago.enraivecer(vitima);
            helper.assertTrue(gago.getTarget() == vitima,
                "Angered Gago targets the offender (survival mock)");
            helper.assertTrue(gago.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).is(IntoxicantesMod.ESCOPETA),
                "Angered Gago draws the shotgun");
            // tiro direto via package-private: o tick do NPC dispararia sozinho,
            // mas o teste precisa do cooldown EXATO pra provar que vem do config
            gago.atirarEscopeta(vitima);
            helper.assertTrue(gago.cooldownTiro == teste.escopetaCooldownTicks,
                "Shotgun cooldown comes from the config (was hardcoded 60)");
            var area = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    gago.getBoundingBox().inflate(teste.escopetaAlcanceMaximo));
            helper.assertTrue(vitima.getHealth() < vidaAntes,
                "Diagnostico dano: vida=" + vitima.getHealth() + "/" + vidaAntes
                    + " naArea=" + area.contains(vitima) + " totalArea=" + area.size()
                    + " dist=" + gago.distanceTo(vitima)
                    + " invEnt=" + vitima.isInvulnerable()
                    + " invAbi=" + vitima.getAbilities().invulnerable
                    + " invTemp=" + vitima.getInvulnerableTime()
                    + " morto=" + vitima.isDeadOrDying());
            // restabelece: a raiva passou, sem arma na mao, config original de volta
            gago.acalmar(vitima);
            helper.assertTrue(gago.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(),
                "Calming the Gago puts the shotgun away");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.19 — Report do playtest: a expulsao teleportava com OFFSET FIXO
     * (+5,+1,+5 do mercado, sem dimensao) — com a fundacao da v1.2.18 o fregues
     * nascia DENTRO da pedra ("me trancou nas pedras wtf") ou caiu pro void.
     * A casa nova tem que ter pe no chao, corpo em ar e ficar LONGE do Gago.
     */
    @GameTest
    public void expelledCustomerLandsOnSolidGroundAwayFromTheGago(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        // run hermetico: sem posicao de mercado pre-salva, a expulsao (e qualquer
        // gerenciador de plantao) NAO usa a estrutura de outro teste como ancora
        BlockPos mercado = helper.absolutePos(new BlockPos(3, 1, 3));
        MarketSystem.setMarketPos(null);
        try {
            MarketSystem.setMarketPos(mercado);
            var gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
            var vitima = vitimaSobrevivente(helper);
            vitima.absSnapTo(mercado.getX() + 0.5, mercado.getY(), mercado.getZ() + 0.5);

            // piso solido a 6 blocos (anel 6), corpo em ar — a casa que o teste prova
            BlockPos casaEsperada = mercado.offset(6, 0, 0);
            helper.setBlock(casaEsperada.below(), net.minecraft.world.level.block.Blocks.STONE);
            gago.enraivecer(vitima);

            helper.assertTrue(vitima.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD),
                "Customer must stay in the overworld (was dropped to the bottom of the world)");
            BlockPos pe = vitima.blockPosition();
            var piso = helper.getLevel().getBlockState(pe.below());
            helper.assertTrue(piso.isSolidRender(),
                "Expelled customer must stand on solid ground (was locked inside the stones)");
            helper.assertTrue(helper.getLevel().getBlockState(pe).isAir()
                    && helper.getLevel().getBlockState(pe.above()).isAir(),
                "Expelled customer must have air for body and head");
            helper.assertTrue(gago.blockPosition().distSqr(vitima.blockPosition()) >= 25,
                "House is chosen far from the Gago (he does not keep beating the customer)");
            helper.assertTrue(vitima.getDeltaMovement().lengthSqr() == 0.0
                    && vitima.fallDistance == 0.0F,
                "Teleport does not inherit velocity or fall damage");
        } finally {
            MarketSystem.setMarketPos(null);
        }
        helper.succeed();
    }

    /**
     * v1.2.11 — O CASO DO WITHER, reproducao direta do report: o Gago atira
     * volleys de balins IGUAIS (3.0) num wither blindado (vida <= 50%). Com os
     * i-frames do 26.3 (damageCooldownTime + regra "so dano maior"), o 2o balim
     * em diante quicava — e o volley seguinte tambem. Chumbo.aplicar zera o
     * cooldown REAL: os dois volleys completos entram.
     */
    @GameTest
    public void gagoBuckshotLandsOnTheWitherThroughItsShield(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        var atirador = helper.makeMockServerPlayerInLevel();
        var tipoWither = helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("wither"));
        var wither = (net.minecraft.world.entity.boss.wither.WitherBoss) helper.spawn(
                tipoWither, new BlockPos(2, 1, 1));
        // blindado: vida <= 50% (isPowered) — o estado do report
        wither.setHealth(wither.getMaxHealth() / 2.0F);
        var fonte = atirador.damageSources().mobAttack(atirador);
        float base = wither.getHealth();

        // volley 1: 8 "balins" de 3.0 — SEM o fix, so o 1o entraria (~2.7 de dano
        // apos a armadura do wither); com o fix, os 8 entram (~21.6)
        for (int i = 0; i < 8; i++) {
            Chumbo.aplicar(helper.getLevel(), wither, fonte, 3.0F);
        }
        float danoV1 = base - wither.getHealth();
        helper.assertTrue(danoV1 > 16.0F,
                "Volley 1 lands far beyond a single pellet (armor-taxed): " + danoV1);

        // volley 2, mesma coisa (1s depois no jogo): espelha o primeiro volley
        float antesV2 = wither.getHealth();
        for (int i = 0; i < 8; i++) {
            Chumbo.aplicar(helper.getLevel(), wither, fonte, 3.0F);
        }
        float danoV2 = antesV2 - wither.getHealth();
        helper.assertTrue(Math.abs(danoV2 - danoV1) < 0.5F,
                "Volley 2 mirrors volley 1 (the reported Gago-vs-wither case): " + danoV2);
        helper.succeed();
    }

    /**
     * v1.2.11 — O CASO DO WITHER: os i-frames do 26.3 vivem em damageCooldownTime
     * (campo publico) e a regra e' "cooldown ativo so aceita dano MAIOR que o
     * ultimo". O setInvulnerableTime(0) legado NAO limpa esse campo — os 8 balins
     * viravam 1 e o volley seguinte do Gago quicava. Prova: com o reset correto,
     * dois hits IGUAIS seguidos entram (impossivel com o reset velho).
     */
    @GameTest
    public void shotgunIFrameResetUsesThe263CooldownField(GameTestHelper helper) {
        var atirador = helper.makeMockServerPlayerInLevel();
        var tipoZumbi = helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        var alvo = (net.minecraft.world.entity.monster.zombie.Zombie) helper.spawn(
                tipoZumbi, new BlockPos(2, 1, 1));
        var fonte = atirador.damageSources().playerAttack(atirador);

        // 1o hit: entra (cooldown do alvo esta zerado)
        Chumbo.aplicar(helper.getLevel(), alvo, fonte, 4.0F);
        float aposPrimeiro = alvo.getHealth();
        helper.assertTrue(alvo.damageCooldownTime > 0,
                "A landed hit starts the 26.3 damage cooldown");

        // 2o hit IGUAL logo em seguida (i-frames NO ar, dano nao-maior):
        // sem o reset correto, este hit QUICARIA (a regra nova exige dano maior)
        Chumbo.aplicar(helper.getLevel(), alvo, fonte, 4.0F);
        helper.assertTrue(alvo.getHealth() < aposPrimeiro,
                "Second equal pellet lands because aplicar clears damageCooldownTime");

        // e o reset do campo legado sozinho NAO basta — prova do porque do fix
        var alvo2 = (net.minecraft.world.entity.monster.zombie.Zombie) helper.spawn(
                tipoZumbi, new BlockPos(4, 1, 1));
        alvo2.hurtServer(helper.getLevel(), fonte, 4.0F);
        float aposPrimeiro2 = alvo2.getHealth();
        alvo2.setInvulnerableTime(0); // o jeito VELHO de resetar (nao toca o campo novo)
        boolean quicou = !alvo2.hurtServer(helper.getLevel(), fonte, 4.0F);
        helper.assertTrue(quicou,
                "Legacy setInvulnerableTime(0) does NOT clear the 26.3 cooldown (the bug)");
        helper.assertTrue(alvo2.getHealth() == aposPrimeiro2,
                "Rejected second hit leaves health untouched");
        helper.succeed();
    }

    /** Injeta o remaining de uso (simula o timer do gole correndo). */
    private static void usoRemainingTeste(ServerPlayer player, int ticks) {
        try {
            var campo = Embriaguez.class.getDeclaredField("USO_REMAINING");
            campo.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, Integer> mapa =
                    (java.util.Map<java.util.UUID, Integer>) campo.get(null);
            mapa.put(player.getUUID(), ticks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("USO_REMAINING inacessivel", e);
        }
    }

    /**
     * v1.2.12 — O BUG DO CLIQUE SEM BEBER: dose so conta se o gole COMPLETOU
     * (remaining chegou ao fim). Interromper no meio (soltar o botao, trocar de
     * item, ter o baseado apagado pelo Gago) NAO conta mais — e a cura com
     * cafeina desce 2 niveis. v1.2.14: o consumivel de teste e' CERVEJA — so
     * ALCOOL alimenta o medidor (erva/pó/comprimido nao emborracham).
     */
    @GameTest
    public void interruptedSipDoesNotDoseAndCaffeineCures(GameTestHelper helper) {
        var bebedor = vitimaSobrevivente(helper);
        bebedor.getInventory().clearContent();
        bebedor.getInventory().setItem(0, new ItemStack(IntoxicantesMod.CERVEJA));
        bebedor.getFoodData().setFoodLevel(20);

        // --- interrompido no meio: NADA conta (o bug do beta tester)
        bebedor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(IntoxicantesMod.CERVEJA));
        bebedor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        passaSegundoTeste(bebedor); // registra EM_USO + USO_REMAINING
        bebedor.releaseUsingItem(); // soltou o botao no meio do puxao
        passaSegundoTeste(bebedor); // detecta o fim do uso com remaining alto
        helper.assertTrue(Embriaguez.nivel(bebedor) == 0,
                "Interrupted sip counts NOTHING (was a dose before the fix)");

        // --- gole completo: dose entra (remaining <= 1 quando o uso acaba);
        // cerveja vale dose 2 (medidor agora e' SO de alcool)
        bebedor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        passaSegundoTeste(bebedor); // registra o inicio
        usoRemainingTeste(bebedor, 1); // simula o timer chegando ao fim
        bebedor.releaseUsingItem(); // fim do uso (completo)
        passaSegundoTeste(bebedor);
        helper.assertTrue(Embriaguez.nivel(bebedor) == 2,
                "A COMPLETED beer counts its dose (alcohol-only meter)");

        // --- v1.2.14: ERVA NAO EMBORRACHA — um baseado COMPLETO não sobe o
        // medidor (a fumaça e os efeitos próprios do item cuidam do resto)
        bebedor.getInventory().setItem(0, new ItemStack(IntoxicantesMod.BASEADO));
        bebedor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(IntoxicantesMod.BASEADO));
        bebedor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        passaSegundoTeste(bebedor);
        usoRemainingTeste(bebedor, 1);
        bebedor.releaseUsingItem();
        passaSegundoTeste(bebedor);
        helper.assertTrue(Embriaguez.nivel(bebedor) == 2,
                "A completed JOINT does NOT feed the alcohol meter (report: 'bêbado com maconha não faz sentido')");

        // --- a cura da bebedeira: cafeina derruba 2 niveis
        Embriaguez.setNivelTeste(bebedor, 5);
        bebedor.getInventory().setItem(0, new ItemStack(IntoxicantesMod.EXTRATO_CAFEINA));
        bebedor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(IntoxicantesMod.EXTRATO_CAFEINA));
        bebedor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        passaSegundoTeste(bebedor);
        usoRemainingTeste(bebedor, 1);
        bebedor.releaseUsingItem();
        passaSegundoTeste(bebedor);
        helper.assertTrue(Embriaguez.nivel(bebedor) == 3,
                "Caffeine extract cures 2 drunkenness levels");

        // --- piso zero: bêbado de nível 1 vira limpo, nunca negativo
        Embriaguez.setNivelTeste(bebedor, 1);
        bebedor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        passaSegundoTeste(bebedor);
        usoRemainingTeste(bebedor, 1);
        bebedor.releaseUsingItem();
        passaSegundoTeste(bebedor);
        helper.assertTrue(Embriaguez.nivel(bebedor) == 0,
                "Cure floors at zero");
        helper.succeed();
    }

    /**
     * v1.2.12 — O GAGO NAO E' SERRILHA DE BOSS: contra alvo de vida maxima alta
     * o volley dele capA (config, padrao 6.0) — com tiro por segundo e municao
     * infinita, ele derretia um wither. Fregues comum leva dano pleno.
     */
    @GameTest
    public void gagoVolleyIsCappedAgainstBossesButNotCustomers(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        var tipoWither = helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("wither"));
        var wither = (net.minecraft.world.entity.boss.wither.WitherBoss) helper.spawn(
                tipoWither, new BlockPos(2, 1, 1));
        wither.absSnapTo(gago.getX() + 2, gago.getY(), gago.getZ());
        wither.setHealth(wither.getMaxHealth()); // longe do escudo, so pro cap

        float base = wither.getHealth();
        gago.atirarEscopeta(wither);
        float dano = base - wither.getHealth();
        helper.assertTrue(dano > 0.0F,
                "Boss still takes buckshot damage");
        helper.assertTrue(dano <= ModConfig.get().escopetaCapDanoBoss + 0.01F,
                "Boss damage is capped (was 24/s of shredding): " + dano);

        // fregues comum (player sobrevivente, 20 de vida maxima): SEM cap — dano
        // pleno do volley. O wither sai da area (o chefe de 3 blocos bloqueava a
        // linha de visao na mesma direcao) e o fregues ocupa o ponto provado.
        wither.discard();
        var fregues = vitimaSobrevivente(helper);
        fregues.absSnapTo(gago.getX() + 2, gago.getY(), gago.getZ());
        fregues.setPermanentlyInvulnerable(false);
        fregues.setInvulnerableTime(0);
        fregues.getAbilities().invulnerable = false; // igual ao teste da 1.2.4
        float baseZ = fregues.getHealth();
        gago.cooldownTiro = 0; // o tiro no wither acima gastou o cooldown do Gago
        gago.atirarEscopeta(fregues);
        float danoZ = baseZ - fregues.getHealth();
        helper.assertTrue(danoZ > ModConfig.get().escopetaCapDanoBoss,
                "Regular customer takes the FULL volley (cap is boss-only): " + danoZ);
        helper.succeed();
    }

    /**
     * v1.2.13 — A RESSACA: voltar a ZERO vindo de estado bêbado derruba o
     * fregues (nausea+lentidao+fraqueza) por `embriaguezRessacaSegundos`; o
     * "cabelo do cachorro" (dose durante a ressaca) cura na hora. Config em 0
     * = desligada. Ressaca em nivel 1-2 (alegre) NAO existe.
     */
    @GameTest
    public void hangoverHitsAfterTheLastLevelAndDogHairCuresIt(GameTestHelper helper) {
        ModConfig teste = new ModConfig();
        teste.embriaguezRessacaSegundos = 60; // 60s de ressaca
        ModConfig atual = ModConfig.get();
        configDeTeste(teste);
        try {
            var player = vitimaSobrevivente(helper);
            Embriaguez.setNivelTeste(player, 0);

            // alegre (nível 1) zerando NÃO dá ressaca: só quem cruzou o limiar
            Embriaguez.setNivelTeste(player, 1);
            sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
            passaSegundoTeste(player); // decay -> 0
            helper.assertTrue(Embriaguez.nivel(player) == 0,
                    "Tipsy level decays to zero");
            helper.assertFalse(Embriaguez.emRessaca(player),
                    "No hangover when you never crossed the drunk threshold");

            // bêbado (nível 3 = limiar) zerando: RESSACA entra, com efeitos.
            // O decay cai 1 NÍVEL por passada: 3->2->1->0 são TRÊS passadas —
            // a memória de travessia (ESTEVE_BEBADO) é o que sobrevive a elas
            Embriaguez.setNivelTeste(player, 3);
            for (int i = 0; i < 3; i++) {
                sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
                passaSegundoTeste(player); // decay -1 nível; o último cruza pra ressaca
            }
            helper.assertTrue(Embriaguez.nivel(player) == 0,
                    "Three decays take the drunk customer to zero");
            helper.assertTrue(Embriaguez.emRessaca(player),
                    "Reaching zero from DRUNK starts the hangover");
            // os efeitos entram na PASSADA seguinte (a ressaca comeca no decay)
            passaSegundoTeste(player);
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                    "Hangover applies slowness");
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
                    "Hangover applies weakness");

            // CABELO DO CACHORRO: dose durante a ressaca remove ela (e sobe nivel)
            Embriaguez.beber(player, 1);
            helper.assertFalse(Embriaguez.emRessaca(player),
                    "Hair of the dog cures the hangover instantly");
            helper.assertTrue(Embriaguez.nivel(player) == 1,
                    "The curing dose still counts as a dose");

            // config desligada (0s): voltar a zero NÃO deixa ressaca
            teste.embriaguezRessacaSegundos = 0;
            Embriaguez.setNivelTeste(player, 3);
            for (int i = 0; i < 3; i++) {
                sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
                passaSegundoTeste(player);
            }
            helper.assertTrue(Embriaguez.nivel(player) == 0,
                    "Second decay run reaches zero");
            helper.assertFalse(Embriaguez.emRessaca(player),
                    "Config with 0s hangover disables the feature");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.46 — A CAFEINA DE VERDADE: o extrato cura a bebedeira (2 níveis),
     * a cura MATA a ressaca (o café do dia seguinte) e a cura em si NUNCA
     * PROVOCA ressaca (só a BEBIDA que zera cobra a manhã seguinte).
     */
    @GameTest
    public void caffeineExtractCuresDrunkAndHangover(GameTestHelper helper) {
        ModConfig teste = new ModConfig();
        teste.embriaguezRessacaSegundos = 60;
        ModConfig atual = ModConfig.get();
        configDeTeste(teste);
        try {
            var player = vitimaSobrevivente(helper);
            Embriaguez.setNivelTeste(player, 0);

            // bêbado (nível 3): o extrato derruba 2 níveis de uma vez
            Embriaguez.setNivelTeste(player, 3);
            Embriaguez.curar(player, 2);
            helper.assertTrue(Embriaguez.nivel(player) == 1,
                    "Caffeine extract drops two drunkenness levels");

            // ressaca ATIVA + nível 0: o café MATA ela (antes não fazia nada)
            Embriaguez.setNivelTeste(player, 3);
            for (int i = 0; i < 3; i++) {
                sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
                passaSegundoTeste(player); // decay até zero: ressaca entra
            }
            helper.assertTrue(Embriaguez.emRessaca(player),
                    "Hangover is active before the cure");
            Embriaguez.curar(player, 2);
            helper.assertFalse(Embriaguez.emRessaca(player),
                    "Caffeine extract KILLS the hangover");

            // e a cura em si NÃO deixa ressaca: sair da bebedeira pelo café
            // (de nível 2 direto a 0) não cobra a manhã seguinte
            Embriaguez.setNivelTeste(player, 2);
            Embriaguez.curar(player, 2);
            helper.assertTrue(Embriaguez.nivel(player) == 0,
                    "Cure from level 2 reaches zero");
            sobriedadeTeste(player, teste.embriaguezDecaySegundos - 1);
            passaSegundoTeste(player);
            helper.assertFalse(Embriaguez.emRessaca(player),
                    "The cure itself never triggers a hangover");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.46 — O TAPA ACORDA O VENDEDOR: soco no Gago de plantão tira ele
     * da posição (não quica), solta a IA e saca a 12 (canal do vandalismo);
     * e o soco do SURVIVOR remove vida de verdade.
     */
    @GameTest
    public void gagoPunchWhileOnDutyWakesHimUp(GameTestHelper helper) {
        var level = helper.getLevel();
        // canAttack() recusa alvo Player em dificuldade PACIFICA (idem outro teste)
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        GagoEntity gago = helper.spawn(IntoxicantesMod.GAGO, new BlockPos(1, 1, 1));
        var vitima = vitimaSobrevivente(helper);
        vitima.absSnapTo(gago.getX() + 2, gago.getY(), gago.getZ());
        // o placeNewPlayer aplica o gamemode padrão do servidor nos abilities:
        // sem isso o guard anti-criativo do hurtServer engole a raiva kkkk
        vitima.getAbilities().invulnerable = false;
        vitima.getAbilities().instabuild = false;

        // de plantão (NoAI): o estado que fazia o hit "não fazer nada"
        gago.setupPostoMercado(gago.blockPosition());
        float vidaAntes = gago.getHealth();
        var soco = level.damageSources().playerAttack(vitima);

        // 1o tapa: DANO de verdade (não quica) + só a advertência do vandalismo
        gago.hurtServer(level, soco, 3.0F);
        helper.assertTrue(gago.getHealth() < vidaAntes,
                "The punch actually damages the on-duty Gago");
        helper.assertTrue(gago.isNoAi(), "First tap only warns (the advertencia)");
        helper.assertTrue(gago.getTarget() == null,
                "First tap does not enrage the shopkeeper");

        // repetiu dentro da advertência: saca a 12 e vai atrás (IA solta)
        gago.hurtServer(level, soco, 3.0F);
        helper.assertTrue(gago.getTarget() == vitima,
                "The repeated punch enrages the Gago");
        helper.assertTrue(
                gago.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                        .is(IntoxicantesMod.ESCOPETA),
                "The enraged Gago draws the shotgun");
        helper.assertTrue(!gago.isNoAi(), "The AI is released to chase");
        helper.succeed();
    }

    /**
     * v1.2.13 — A VANTAGEM DO GAGO + O CAMBALEIO: fregues bêbado paga markup
     * por nível acima do limiar (teto 30%), a tela e a cobranca usam o mesmo
     * metodo; e no nivel do soluco o corpo empurra o fregues sem pedir licenca.
     */
    @GameTest
    public void drunkCustomerPaysMarkupAndStumbles(GameTestHelper helper) {
        ModConfig teste = new ModConfig();
        teste.embriaguezMarkup = 10;
        teste.embriaguezLimiarFonar = 3;
        ModConfig atual = ModConfig.get();
        configDeTeste(teste);
        try {
            var player = vitimaSobrevivente(helper);

            // limpo e alegre (nivel 1 < limiar 3): preco NORMAL
            Embriaguez.setNivelTeste(player, 0);
            int precoLimpo = FidelidadeData.precoComDesconto(player, 10);
            helper.assertTrue(precoLimpo == 10,
                    "Sober customer pays the regular price");
            Embriaguez.setNivelTeste(player, 1);
            helper.assertTrue(FidelidadeData.precoComDesconto(player, 10) == 10,
                    "Tipsy (below the threshold) pays regular too");

            // no limiar (3): markup de 1 nivel (10%)
            Embriaguez.setNivelTeste(player, 3);
            helper.assertTrue(FidelidadeData.precoComDesconto(player, 10) == 11,
                    "Drunk at the threshold pays +1 level of markup");

            // nível 5: markup de 3 níveis (30%)... pega o teto ABSOLUTO de 30%
            Embriaguez.setNivelTeste(player, 5);
            helper.assertTrue(FidelidadeData.precoComDesconto(player, 10) == 13,
                    "Drunk at level 5 pays +3 levels of markup (30%) capped");

            // teto absoluto: 6 níveis acima tentaria 60%, mas o cap segura em 30%
            Embriaguez.setNivelTeste(player, 6);
            helper.assertTrue(FidelidadeData.precoComDesconto(player, 10) == 13,
                "Markup cap is 30% absolute regardless of level");

            // CAMBALEIO: empurrao verificavel (delta horizontal, sem vertical)
            Embriaguez.setNivelTeste(player, 5); // >= limiar do soluco (5)
            // CAMBALEIO: empurrao verificavel — o push aparece no deltaMovement
            // (a posicao so muda depois de um tick de fisica, que o teste nao roda)
            boolean empurrou = false;
            for (int i = 0; i < 12 && !empurrou; i++) {
                Embriaguez.cambaleia(player);
                net.minecraft.world.phys.Vec3 delta = player.getDeltaMovement();
                empurrou = Math.abs(delta.x) > 1.0E-6 || Math.abs(delta.z) > 1.0E-6;
            }
            helper.assertTrue(empurrou, "Stumble pushes the drunk customer around");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.15 — O BALANCE DA FAZENDA (report: "as plantas crescem bem rápido"):
     * o boost ×2 de crescimento é EXCLUSIVO da LÂMPADA UV — sol forte não
     * multiplica mais (fazenda ao ar livre anda no ritmo vanilla). A MATURAÇÃO
     * continua valendo sob sol forte (early game não trava).
     */
    @GameTest
    public void uvLampBoostsGrowthButSunlightDoesNot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 1));
        // solo LISO (sem farmland): a velocidade base é o 0.15 fixo, isolado
        // de bioma/umidade — a conta fica exata
        helper.setBlock(2, 1, 1, IntoxicantesMod.MACONHA_PLANT);
        helper.setBlock(2, 0, 1, net.minecraft.world.level.block.Blocks.DIRT);

        // SEM lâmpada: 0.15 (solo comum) — mesmo a céu aberto (gametest é open
        // sky: sol forte, que NÃO multiplica mais)
        float semUv = ((UvCropBlock) IntoxicantesMod.MACONHA_PLANT)
                .velocidadeCrescimento(level, base);
        helper.assertTrue(Math.abs(semUv - 0.15F) < 0.001F,
                "Open-sky crop on plain dirt grows at the BASE rate (sunlight no longer doubles it): " + semUv);

        // com lâmpada ao lado: 0.15 × 2 = 0.30 — o boost é da TECNOLOGIA
        helper.setBlock(4, 1, 1, IntoxicantesMod.LAMPADA_UV);
        float comUv = ((UvCropBlock) IntoxicantesMod.MACONHA_PLANT)
                .velocidadeCrescimento(level, base);
        helper.assertTrue(Math.abs(comUv - 0.30F) < 0.001F,
                "UV lamp doubles the growth rate (the mod's tech is worth the investment): " + comUv);

        // e a maturação continua valendo sob sol forte (isUvLit inclui o céu)
        helper.assertTrue(IntoxicantesMod.isUvLit(level, base),
                "Open sky still counts as UV for RIPENING (early game keeps working)");
        helper.succeed();
    }

    /**
     * v1.2.17 — O DISJUNTOR DA LÂMPADA UV: redstone liga e DESLIGA (plantação
     * indoor com risco de corte de energia). Regra: sinal = LIGADA; controle
     * sem sinal = DESLIGADA; sem circuito = LIGADA (saves antigos funcionam).
     */
    @GameTest
    public void uvLampHasARedstoneBreaker(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 1));
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_UV.defaultBlockState()
                .setValue(LampadaUvBlock.LIT, Boolean.TRUE));

        // --- caso 1: SEM circuito nenhum = LIGADA (plugada na tomada)
        helper.assertTrue(LampadaUvBlock.deveEstarLigada(level, pos),
                "No redstone around: the lamp is ON (plugged in, saves keep working)");

        // --- caso 2: sinal chegando (REDSTONE BLOCK adjacente) = LIGADA
        level.setBlockAndUpdate(pos.offset(1, 0, 0),
                net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(LampadaUvBlock.deveEstarLigada(level, pos),
                "Active signal: the lamp is ON");

        // --- caso 3: alavanca DESLIGADA adjacente = DESLIGADA (disjuntor aberto)
        level.setBlockAndUpdate(pos.offset(1, 0, 0),
                net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeverBlock.FACING,
                                net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.LeverBlock.POWERED,
                                Boolean.FALSE));
        helper.assertFalse(LampadaUvBlock.deveEstarLigada(level, pos),
                "Unpowered lever next to it: the breaker is OFF (energy cut)");

        // e a maturação PÁRA: a planta não enxerga mais a lâmpada apagada
        BlockPos planta = pos.offset(1, -1, 0);
        level.setBlockAndUpdate(planta, IntoxicantesMod.MACONHA_PLANT.defaultBlockState()
                .setValue(UvCropBlock.AGE, 4).setValue(UvCropBlock.UV_AGE, 0));
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_UV.defaultBlockState()
                .setValue(LampadaUvBlock.LIT, Boolean.FALSE));
        helper.assertFalse(IntoxicantesMod.temLampadaUvPerto(level, planta),
                "A LIT=false lamp does NOT ripen the crop below");
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_UV.defaultBlockState()
                .setValue(LampadaUvBlock.LIT, Boolean.TRUE));
        helper.assertTrue(IntoxicantesMod.temLampadaUvPerto(level, planta),
                "A LIT=true lamp ripens the crop again");

        // --- alavanca LIGADA = energia chegando (disjuntor fechado)
        level.setBlockAndUpdate(pos.offset(1, 0, 0),
                net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeverBlock.FACING,
                                net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.LeverBlock.POWERED,
                                Boolean.TRUE));
        helper.assertTrue(LampadaUvBlock.deveEstarLigada(level, pos),
                "Powered lever: energy flows, lamp is ON");
        helper.succeed();
    }

    /**
     * v1.2.18 — O LETREIRO CUSTOM. v1.2.31: DISPLAY DE FACHADA — a faixa
     * larga MONTADA NA FACHADA (sem torres/pólos e nada na frente da porta):
     * painel central com o texto, extensões sem block entity, nome em UMA
     * linha esticada pela largura e queda em cadeia da faixa inteira.
     */
    @GameTest
    public void esquinaoSignboardIsARealCustomSign(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painel = helper.absolutePos(new BlockPos(2, 3, 1));

        // colocar o PAINEL: NADA nasce sozinho na calçada (o display é
        // montado no prédio — a anatomia de torres virou legado)
        level.setBlockAndUpdate(painel, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.PAINEL));
        var be = level.getBlockEntity(painel);
        helper.assertTrue(be instanceof PlacaEsquinaoBlockEntity,
                "The panel part has the signboard block entity");
        var placa = (PlacaEsquinaoBlockEntity) be;

        // texto de fábrica: o nome da loja em UMA linha (o renderer estica)
        var linhas = placa.getLinhas();
        helper.assertTrue(linhas.size() == 1 && "MERCADO ESQUINÃO".equals(linhas.get(0)),
                "The signboard holds the store name in a single LED line");
        helper.assertTrue(placa.getLargura() == PlacaEsquinaoBlockEntity.LARGURA_FACHADA,
                "Fresh signboard is the full-facade display");

        // extensões da faixa: SEM block entity (o texto renderiza 1× só)
        level.setBlockAndUpdate(painel.west(), IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.EXTENSAO));
        level.setBlockAndUpdate(painel.east(), IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.EXTENSAO));
        helper.assertTrue(level.getBlockEntity(painel.west()) == null
                        && level.getBlockEntity(painel.east()) == null,
                "Extensions have no block entity (text renders once)");

        // persistência: save/load preserva as linhas e a largura
        var saida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        placa.saveWithoutMetadata(saida);
        var entrada = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saida.buildResult());
        var reidratada = new PlacaEsquinaoBlockEntity(painel, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState());
        reidratada.loadWithComponents(entrada);
        helper.assertTrue(reidratada.getLinhas().size() == 1,
                "Signboard text survives save/load");

        // desmontagem: quebrar o PAINEL derruba a FAIXA INTEIRA (é um objeto)
        level.destroyBlock(painel, false);
        helper.assertTrue(level.getBlockState(painel.west()).isAir()
                        && level.getBlockState(painel.east()).isAir(),
                "Breaking one strip block fells the whole display row");

        helper.succeed();
    }

    /**
     * v1.2.31 — A MIGRAÇÃO: placa da anatomia VELHA (torres na calçada,
     * versao<3) é reformada pelo zelador em 1 passada: as torres caem, a
     * faixa nova nasce MONTADA NA FACHADA (1 acima, 1 pra dentro) com a
     * largura do prédio, e o painel velho (na frente da porta) sai.
     */
    @GameTest
    public void facadeMigrationRebuildsOldSignboardOnTheFacade(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos velhoPainel = helper.absolutePos(new BlockPos(7, 3, 2));

        // A FACHADA do prédio: a migração mede a largura dela (isSolidRender
        // em y=3, 1 pra dentro do painel velho) — sem a parede, a faixa
        // "encosta" pra 5 e o teste mediria mentira.
        for (int dx = -7; dx <= 7; dx++) {
            level.setBlockAndUpdate(velhoPainel.offset(dx, 0, -1),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }

        // a placa velha exatamente como o mundo 1.2.27~30 guarda: painel +
        // torres de 4 a ±2, NBT SEM a chave "versao" (o load lê 1)
        level.setBlockAndUpdate(velhoPainel, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.PAINEL));
        var be = (PlacaEsquinaoBlockEntity) level.getBlockEntity(velhoPainel);
        var saida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        be.saveWithoutMetadata(saida);
        var tag = saida.buildResult();
        tag.remove("versao");
        tag.putString("linha0", "MERCADO");
        tag.putString("linha1", "ESQUINÃO");
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(be.getVersao() < PlacaEsquinaoBlockEntity.VERSAO_ANATOMIA,
                "Signboard without the version key reads as old anatomy");
        for (int dy = -1; dy <= 2; dy++) {
            PlacaEsquinaoBlock.Nivel nivel = dy == -1 ? PlacaEsquinaoBlock.Nivel.RODAPE
                    : dy == 2 ? PlacaEsquinaoBlock.Nivel.TOPO : PlacaEsquinaoBlock.Nivel.COLUNA;
            level.setBlockAndUpdate(velhoPainel.west(2).offset(0, dy, 0),
                    IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                            .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                            .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.ESQUERDA)
                            .setValue(PlacaEsquinaoBlock.NIVEL, nivel));
            level.setBlockAndUpdate(velhoPainel.east(2).offset(0, dy, 0),
                    IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                            .setValue(PlacaEsquinaoBlock.FACING, net.minecraft.core.Direction.SOUTH)
                            .setValue(PlacaEsquinaoBlock.PARTE, PlacaEsquinaoBlock.Parte.DIREITA)
                            .setValue(PlacaEsquinaoBlock.NIVEL, nivel));
        }

        // 1 passada do zelador da autocura
        PlacaEsquinaoBlockEntity.zeladorDaAutocura(level, velhoPainel, be);

        // as torres caíram, a faixa atravessa a fachada, o painel velho saiu
        BlockPos centro = velhoPainel.above()
                .relative(net.minecraft.core.Direction.NORTH); // facing sul → 1 pra dentro
        helper.assertTrue(level.getBlockState(velhoPainel).isAir()
                        && level.getBlockState(velhoPainel.west(2)).isAir()
                        && level.getBlockState(velhoPainel.east(2).above(2)).isAir(),
                "Old towers and floating panel are gone");
        for (int dx = -7; dx <= 7; dx++) {
            helper.assertTrue(level.getBlockState(centro.west(dx))
                            .getBlock() == IntoxicantesMod.PLACA_ESQUINAO,
                    "The facade strip spans the whole building");
        }
        var novoBe = level.getBlockEntity(centro);
        helper.assertTrue(novoBe instanceof PlacaEsquinaoBlockEntity
                        && ((PlacaEsquinaoBlockEntity) novoBe).getLargura()
                                == PlacaEsquinaoBlockEntity.LARGURA_FACHADA
                        && ((PlacaEsquinaoBlockEntity) novoBe).getVersao()
                                == PlacaEsquinaoBlockEntity.VERSAO_ANATOMIA
                        && "MERCADO".equals(((PlacaEsquinaoBlockEntity) novoBe).getLinhas().get(0)),
                "The migrated display keeps the text and the new anatomy");

        helper.succeed();
    }

    /**
     * v1.2.19 — O LETREIRO VIVO + O PORTÃO DO GAGO: estado ABERTO/FECHADO na
     * placa (persistente), portão fechado recusando cliente e poste de luz
     * seguindo o relógio (19h~5h aceso).
     */
    @GameTest
    public void esquinaoSignboardOpenClosedAndStreetlight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painel = helper.absolutePos(new BlockPos(2, 2, 1));

        // --- a placa do letreiro: link + estado aberto/fechado persistem
        var tag = PlacaEsquinaoBlockEntity.nbtDoTemplate();
        var entrada = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
        var placa = new PlacaEsquinaoBlockEntity(painel, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState());
        placa.loadWithComponents(entrada);
        helper.assertTrue(placa.isLinkMercado(), "Template signboard is linked to the market");
        helper.assertTrue(placa.isAberto(), "Signboard starts open by default");
        placa.abertoChanged(false);
        var saida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        placa.saveWithoutMetadata(saida);
        var reidratada = new PlacaEsquinaoBlockEntity(painel, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState());
        reidratada.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), saida.buildResult()));
        helper.assertTrue(reidratada.isLinkMercado() && !reidratada.isAberto(),
                "Open/closed state survives save/load");

        // --- v1.2.44 MERCADO 24H: o portão saiu do jogo — o Gago atende
        // SEMPRE, mesmo com DATA_ABERTO=false de save antigo (a flag de
        // compatibilidade não recusa mais cliente)
        var vendedor = IntoxicantesMod.GAGO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        vendedor.absSnapTo(painel.getX() + 0.5, painel.getY(), painel.getZ() + 0.5, 0F, 0F);
        level.addFreshEntity(vendedor);
        vendedor.setMercadoAberto(false);
        helper.assertFalse(vendedor.isMercadoAberto(), "Gate flag still syncs for old saves");
        var fregues = helper.makeMockServerPlayerInLevel();
        fregues.teleportTo(painel.getX() + 0.5, painel.getY(), painel.getZ() + 1.5);
        var resposta = vendedor.mobInteract(fregues, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(resposta == net.minecraft.world.InteractionResult.SUCCESS_SERVER,
                "24h market serves the customer even with gate flag false");

        // --- o POSTE DE LUZ: LIT segue o horário (meio-dia apaga, madrugada acende)
        // v1.2.24: a BASE semeada ergue o poste INTEIRO (corpo + topo)
        BlockPos poste = helper.absolutePos(new BlockPos(4, 1, 3));
        level.setBlockAndUpdate(poste, IntoxicantesMod.POSTE_LUZ.defaultBlockState());
        var posteBloco = (PosteLuzBlock) level.getBlockState(poste).getBlock();
        PosteLuzBlock.selfHealAPartirDaBase(level, poste);
        helper.assertTrue(
                level.getBlockState(poste.above()).getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.CORPO
                        && level.getBlockState(poste.above(2)).getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.TOPO,
                "Streetlight base rises body and lamp head above");
        var clockHolder = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
                .getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
        java.util.function.LongConsumer vaiPraHora = hora ->
                level.clockManager().setTotalTicks(clockHolder,
                        Math.floorDiv(level.getOverworldClockTime(), 24000L) * 24000L + hora);

        // meio-dia (6000): o sol mata o poste — checagem DIRETA via tick (o
        // teste é do mesmo package, o método protegido é alcançável)
        vaiPraHora.accept(6000L);
        posteBloco.tick(level.getBlockState(poste), level, poste, level.getRandom());
        helper.assertFalse(level.getBlockState(poste).getValue(PosteLuzBlock.LIT),
                "Streetlight is OFF at noon");        // madrugada (00:00 = 18000): o poste acende
        vaiPraHora.accept(18000L);
        posteBloco.tick(level.getBlockState(poste), level, poste, level.getRandom());
        helper.assertTrue(level.getBlockState(poste).getValue(PosteLuzBlock.LIT),
                "Streetlight is ON at midnight");

        helper.succeed();
    }

    /**
     * v1.2.20 — O ZELADOR DA PROPRIEDADE: árvore invadindo a loja (folha,
     * tronco e muda do bioma) é expulsa na varredura; a loja em si fica ilesa.
     */
    @GameTest
    public void marketGroundskeeperRemovesInvasiveTree(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos centro = helper.absolutePos(new BlockPos(2, 1, 2));
        // planta a invasão dentro da propriedade: folha, tronco e muda
        level.setBlockAndUpdate(centro,
                net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(centro.east(),
                net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(centro.west(),
                net.minecraft.world.level.block.Blocks.OAK_SAPLING.defaultBlockState());

        // e a LOJA da propriedade (lâmpada UV da plantação indoor) deve ficar
        BlockPos lampada = centro.above();
        level.setBlockAndUpdate(lampada, IntoxicantesMod.LAMPADA_UV.defaultBlockState());

        // uma passada do zelador (mesmo package: chamada direta, determinística)
        MarketSystem.zeladorDaPropriedade(level, centro);

        helper.assertTrue(!level.getBlockState(centro).is(
                net.minecraft.world.level.block.Blocks.OAK_LEAVES), "Leaves evicted");
        helper.assertTrue(!level.getBlockState(centro.east()).is(
                net.minecraft.world.level.block.Blocks.OAK_LOG), "Log evicted");
        helper.assertTrue(!level.getBlockState(centro.west()).is(
                net.minecraft.world.level.block.Blocks.OAK_SAPLING), "Sapling evicted");
        helper.assertTrue(level.getBlockState(lampada).is(IntoxicantesMod.LAMPADA_UV),
                "The shop itself is untouched");

        helper.succeed();
    }

    /** Concatena dois arrays de BlockPos (helper do teste da placa). */
    private static BlockPos[] concat(BlockPos[] a, BlockPos[] b) {
        BlockPos[] out = new BlockPos[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * v1.2.24 — O ZELADOR SELETIVO: a placa VELHA (sem a marca "nova" no NBT,
     * anatomia 1.2.18–1.2.23) cai na passada do zelador; a placa NOVA fica
     * intacta (antes o concerto derrubava TUDO, inclusive placa recém-colocada).
     */
    @GameTest
    public void groundskeeperDismantlesOnlyLegacySignboards(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos centro = helper.absolutePos(new BlockPos(2, 1, 2));

        // ---- a placa VELHA: coloca (nasce nova=true), reescreve o NBT SEM a
        // marca (exatamente o que o load de um save 1.2.23 produz) → velha
        BlockPos velha = centro.north();
        level.setBlockAndUpdate(velha, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState());
        var beVelha = (PlacaEsquinaoBlockEntity) level.getBlockEntity(velha);
        var saida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        beVelha.saveWithoutMetadata(saida);
        var tag = saida.buildResult();
        tag.remove("nova"); // o save do mundo velho não tem a chave
        var entrada = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
        beVelha.loadWithComponents(entrada);
        beVelha.setChanged();
        helper.assertFalse(beVelha.isNova(), "Signboard without the mark reads as legacy");

        // ---- a placa NOVA: recém-colocada (BE de fábrica = nova=true)
        BlockPos nova = centro.south();
        level.setBlockAndUpdate(nova, IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState());
        var beNova = (PlacaEsquinaoBlockEntity) level.getBlockEntity(nova);
        helper.assertTrue(beNova != null && beNova.isNova(), "Fresh signboard reads as new");

        // uma passada do zelador: a velha some, a nova fica
        MarketSystem.zeladorDaPropriedade(level, centro);

        helper.assertTrue(level.getBlockState(velha).isAir(),
                "Legacy signboard is dismantled by the groundskeeper");
        helper.assertTrue(level.getBlockState(nova).is(IntoxicantesMod.PLACA_ESQUINAO),
                "New signboard survives the groundskeeper");

        helper.succeed();
    }

    /**
     * v1.2.24 — A DECORAÇÃO DA ESQUINA: a faixa de pedestre é PLANA (anda por
     * cima sem degrau) e morre se o asfalto debaixo sumir; o hidrante fica de
     * pé, responde ao uso (jato d'água) e não se move.
     */
    @GameTest
    public void crosswalkLiesFlatAndHydrantSplashes(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos chao = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos posFaixa = chao.above();
        BlockPos posHidrante = chao.east().above();

        // ---- faixa de pedestre sobre o asfalto
        level.setBlockAndUpdate(chao, IntoxicantesMod.ASFALTO.defaultBlockState());
        level.setBlockAndUpdate(posFaixa, IntoxicantesMod.FAIXA_PEDESTRE.defaultBlockState());
        var shape = level.getBlockState(posFaixa).getShape(level, posFaixa);
        helper.assertTrue(shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.0625,
                "Crosswalk paint lies flat (1px, walkable without a step)");

        // o chão cedeu? a tinta vai junto (nada de faixa flutuante)
        level.destroyBlock(chao, false);
        helper.assertTrue(level.getBlockState(posFaixa).isAir(),
                "Crosswalk paint vanishes when the asphalt below is gone");

        // ---- hidrante: coloca, usa (jato d'água) e segue inteiro
        level.setBlockAndUpdate(posHidrante, IntoxicantesMod.HIDRANTE.defaultBlockState());
        var shapeH = level.getBlockState(posHidrante).getShape(level, posHidrante);
        helper.assertTrue(shapeH.max(net.minecraft.core.Direction.Axis.Y) >= 0.85
                        && shapeH.max(net.minecraft.core.Direction.Axis.Y) < 1.0,
                "Hydrant is TALL street furniture now (15px), not a nanico squat");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(posHidrante),
                net.minecraft.core.Direction.UP, posHidrante, false);
        level.getBlockState(posHidrante).useWithoutItem(level, player, hit);
        helper.assertTrue(level.getBlockState(posHidrante).is(IntoxicantesMod.HIDRANTE),
                "Hydrant survives its own water jet");

        helper.succeed();
    }

    /**
     * v1.2.68 — A LÂMPADA LED do teto do mercado: tubo convencional suspenso
     * (difusor + ponteiras + hastes), SEMPRE aceso por enquanto (luz 15) e
     * fino o bastante pra não ocluir o vizinho.
     */
    @GameTest
    public void lampadaLedShinesAtFullPowerAndNeverOccludes(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks
                .CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_LED.defaultBlockState());
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
                "LED lamp shines at full power (always-on prototype)");
        helper.assertTrue(!level.getBlockState(pos).canOcclude(),
                "LED tube does not occlude neighbors (suspended thin shape)");
        helper.succeed();
    }

    /**
     * v1.2.24 — A FONTE DE LED NUNCA MAIS EXPLODE (o crash do render frame:
     * glifo desafasado pedia índice 46 num array de 46) e o texto SEMPRE cabe
     * na faixa de LED entre as torres. Varredura completa: TODO caractere
     * acentuado do português (ex. o Ã de ESQUINÃO) devolve glifo de exatamente
     * 7 linhas; o cálculo de escala nunca estoura a caixa alvo.
     */
    @GameTest
    public void ledFontGlyphsNeverExplodeAndFitTheBand(GameTestHelper helper) {
        // ---- 1. TODO caractere legível devolve glifo 7×5, sem OOB
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789&.,!?-:$/'*+%°"
                + "ÁÀÂÃÄáàâãäÉÈÊËéèêë\u00CD\u00CC\u00CE\u00CFíìîï"
                + "ÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ ";
        for (char c : abc.toCharArray()) {
            byte[] g = LedFont.glifo(c);
            helper.assertTrue(g.length == LedFont.ALTURA_GLYPH,
                    "Glyph of '" + c + "' has exactly 7 rows (never out of bounds)");
            for (byte row : g) {
                helper.assertTrue((row & ~0x1F) == 0,
                        "Glyph of '" + c + "' fits the 5px width");
            }
        }
        // caractere desconhecido: espaço em branco, nunca crash
        helper.assertTrue(LedFont.glifo('\u0000').length == LedFont.ALTURA_GLYPH,
                "Unknown chars fall back to a blank glyph");

        // ---- 2. o CICLO do letreiro (v1.2.25): nome e status alternam SOZINHOS
        // na faixa de 48×16px — e cada fase tem que entrar GRANDE (letra de
        // posto), nunca espremida. Guarda anti-tracinhos: escala ≥ 0.9.
        List<String> faseNome = List.of(PlacaEsquinaoBlockEntity.LINHAS_PADRAO);
        List<String> faseStatus = List.of(PlacaEsquinaoBlockEntity.STATUS_ABERTO,
                PlacaEsquinaoBlockEntity.STATUS_FECHADO);
        // v1.2.31: a caixa agora é o DISPLAY DE FACHADA (largura do prédio ×
        // 16px de altura) — nome e status entram GRANDES (letra de display).
        int faixaLarguraPx = PlacaEsquinaoBlockEntity.LARGURA_FACHADA * 16;
        for (List<String> fase : List.of(faseNome, faseStatus)) {
            float escala = LedFont.escalaPara(fase, faixaLarguraPx, 16);
            helper.assertTrue(escala > 0F && escala <= 1.0F,
                    "Cycle content scales to fit the facade LED band");
            helper.assertTrue(escala >= 0.9F,
                    "LED text is NEVER tiny (no crumb-sized glyphs on the signboard)");
            // larguraMax JÁ conta glifo+gap; com a escala, não passa da faixa
            int larguraFinal = (int) Math.ceil(LedFont.larguraMax(fase) * escala);
            helper.assertTrue(larguraFinal <= faixaLarguraPx,
                    "Scaled content never overflows the LED band width");
        }

        // ---- 3. o texto de fábrica do template (o MESMO que o mundo real usa):
        // 1 linha esticada + a anatomia do display de fachada no NBT
        var tag = PlacaEsquinaoBlockEntity.nbtDoTemplate();
        helper.assertTrue(tag.contains("linha0") && !tag.contains("linha1")
                        && tag.getIntOr("largura", 0) == PlacaEsquinaoBlockEntity.LARGURA_FACHADA
                        && tag.getIntOr("versao", 0) == PlacaEsquinaoBlockEntity.VERSAO_ANATOMIA,
                "Template NBT carries the stretched line and the facade anatomy");

        helper.succeed();
    }

    /**
     * v1.2.68 — A REFORMA DO PÁTIO AGORA É MÃO CONTRÁRIA: mundos 1.2.61–67
     * nasceram com hidrante NO MEIO do asfalto, 4 postes DENTRO das vagas e
     * a travessia cruzando o estacionamento ("sem sentido nenhum" do
     * playtest). Uma passada do zelador RECOLHE todo esse legado (só sobre
     * piso do próprio lote), a travessia da CALÇADA fica inteira e NADA é
     * plantado/pintado no asfalto. A segunda passada é idempotente.
     */
    @GameTest
    public void groundskeeperCollectsLegacyPatioClutterAndKeepsSidewalkCrosswalk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos centro = helper.absolutePos(new BlockPos(2, 1, 2));

        // o pátio bugado do 1.2.61–67: asfalto no chão + o legado em cima
        BlockPos hidranteVaga = centro.offset(3, 1, 15);   // template (10,20)
        BlockPos[] faixaAsfalto = new BlockPos[3];         // a tinta do v2: (12..14, z21)
        for (int i = 0; i < 3; i++) faixaAsfalto[i] = centro.offset(5 + i, 1, 16);
        BlockPos[] postesEmVaga = { centro.offset(-4, 1, 15), centro.offset(16, 1, 15),
                centro.offset(-4, 1, 18), centro.offset(16, 1, 18) }; // (3/23, 20/23)
        BlockPos[] velhos = { centro.offset(-5, 1, 9), centro.offset(5, 1, 9) }; // 1.2.24~28
        for (BlockPos p : concat(faixaAsfalto, concat(new BlockPos[]{hidranteVaga},
                concat(postesEmVaga, velhos)))) {
            level.setBlockAndUpdate(p.below(), IntoxicantesMod.ASFALTO.defaultBlockState());
            level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(hidranteVaga, IntoxicantesMod.HIDRANTE.defaultBlockState());
        for (BlockPos p : faixaAsfalto) {
            level.setBlockAndUpdate(p, IntoxicantesMod.FAIXA_PEDESTRE.defaultBlockState());
        }
        for (BlockPos p : postesEmVaga) {
            level.setBlockAndUpdate(p, IntoxicantesMod.POSTE_LUZ.defaultBlockState()); // base
        }
        // travessia da CALÇADA (template 12..14,19 = +5..7,+14): tem que FICAR
        BlockPos[] faixaCalcada = new BlockPos[3];
        for (int i = 0; i < 3; i++) {
            faixaCalcada[i] = centro.offset(5 + i, 1, 14);
            level.setBlockAndUpdate(faixaCalcada[i].below(),
                    net.minecraft.world.level.block.Blocks.CONCRETE.pick(
                            net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
            level.setBlockAndUpdate(faixaCalcada[i], IntoxicantesMod.FAIXA_PEDESTRE.defaultBlockState());
        }
        // chão estranho ao lado: nada nasce/some aí
        BlockPos vizinho = centro.east(3);
        level.setBlockAndUpdate(vizinho, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(vizinho.below(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()); // fora do lote

        MarketSystem.zeladorDaPropriedade(level, centro);

        // legado do pátio RECOLHIDO (sem drop)
        helper.assertTrue(level.getBlockState(hidranteVaga).isAir(),
                "In-asphalt hydrant is collected by the groundskeeper");
        for (BlockPos p : faixaAsfalto) {
            helper.assertTrue(level.getBlockState(p).isAir(),
                    "Crosswalk paint across the parking lot is removed");
        }
        for (BlockPos p : postesEmVaga) {
            helper.assertTrue(level.getBlockState(p).isAir(),
                    "In-slot light poles fall (whole pole, no drop)");
        }
        for (BlockPos p : velhos) {
            helper.assertTrue(level.getBlockState(p).isAir(),
                    "Old in-slot hydrants stay collected");
        }
        // a travessia da calçada SOBREVIVE
        for (BlockPos p : faixaCalcada) {
            helper.assertTrue(level.getBlockState(p).is(IntoxicantesMod.FAIXA_PEDESTRE),
                    "Sidewalk crosswalk survives the retrofit");
        }
        // sobre chão de jogador: NADA muda
        helper.assertTrue(level.getBlockState(vizinho).isAir(),
                "Retrofit never touches foreign ground");

        // segunda passada: idempotente
        MarketSystem.zeladorDaPropriedade(level, centro);
        helper.assertTrue(level.getBlockState(faixaCalcada[0]).is(IntoxicantesMod.FAIXA_PEDESTRE),
                "Second pass is idempotent");
        helper.assertTrue(level.getBlockState(hidranteVaga).isAir(),
                "Collected clutter stays collected");

        helper.succeed();
    }

    /**
     * v1.2.25 — A REGIÃO PERSISTIDA: setMarketPos zera a região (mundo velho
     * = clássico), o descobridor grava a chave real (setRegiaoMercado) e o
     * save/load leva a pele no NBT — o /gagomarket rebuild ergue o prédio
     * da MESMA cor que o mundo nasceu.
     */
    @GameTest
    public void marketRegionPersistsThroughSaveRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();

        // mundo velho: região vazia lê como clássico
        MarketSystem.setMarketPos(new BlockPos(0, -64, 0));
        helper.assertTrue(MarketSystem.getRegiaoMercado().isEmpty(),
                "Legacy saves read as the classic building");

        // descoberta: a chave da estrutura vira a região
        MarketSystem.setRegiaoMercado("sertao");
        helper.assertTrue("sertao".equals(MarketSystem.getRegiaoMercado()),
                "Discovered structure key becomes the persisted region");

        // round-trip REAL: save() no diretorio temporario, zera tudo, load()
        // volta — a pele sobrevive ao restart do servidor (mesmo arquivo do
        // contrato de produção: intoxicantes_market.dat)
        java.nio.file.Path dirTemp = null;
        try {
            dirTemp = java.nio.file.Files.createTempDirectory("intoxicantes_test");
            MarketSystem.save(dirTemp.toFile());
            MarketSystem.setRegiaoMercado("");
            MarketSystem.setMarketPos(null);
            MarketSystem.load(dirTemp.toFile());
            helper.assertTrue("sertao".equals(MarketSystem.getRegiaoMercado()),
                    "Region survives the save/load round trip");
            helper.assertTrue(MarketSystem.getMarketPos() != null
                            && MarketSystem.getMarketPos().getY() == -64,
                    "Market position also survives the round trip");
        } catch (java.io.IOException e) {
            helper.fail("Round trip I/O failed: " + e.getMessage());
        } finally {
            // teste hermético: limpa o estado global e o arquivo temporario
            MarketSystem.setMarketPos(null);
            MarketSystem.setRegiaoMercado("");
            if (dirTemp != null) {
                try (var walk = java.nio.file.Files.walk(dirTemp)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> p.toFile().delete());
                } catch (java.io.IOException ignorado) {
                    // limpeza best-effort: o teste já decidiu o resultado
                }
            }
        }

        helper.succeed();
    }

    /**
     * v1.2.24 — O POSTE DE 3 BLOCOS: qualquer parte ergue o poste INTEIRO
     * (base→corpo+topo; topo→corpo+base), nunca apaga bloco alheio no caminho
     * e quebrar uma parte derruba as três.
     */
    @GameTest
    public void streetlightIsThreeBlocksBuiltFromAnyEnd(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos chao = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos posBase = chao.above();
        BlockPos posCorpo = posBase.above();
        BlockPos posTopo = posCorpo.above();

        // um "invasor" no caminho do topo: a montagem NUNCA pode apagar ele
        level.setBlockAndUpdate(posTopo,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());

        // colocando a BASE, corpo e topo sobem (o topo NÃO nasce sobre pedra)
        level.setBlockAndUpdate(posBase, IntoxicantesMod.POSTE_LUZ.defaultBlockState());
        PosteLuzBlock.selfHealAPartirDaBase(level, posBase);
        helper.assertTrue(
                level.getBlockState(posCorpo).getBlock() instanceof PosteLuzBlock
                        && level.getBlockState(posCorpo).getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.CORPO,
                "Placing the base raises the pole body");
        helper.assertTrue(level.getBlockState(posTopo).is(
                net.minecraft.world.level.block.Blocks.STONE),
                "Mounting never overwrites a foreign block");

        // colocando o TOPO em outro lugar, corpo e base sobem POR BAIXO
        BlockPos outroTopo = chao.east(3).above(2);
        level.setBlockAndUpdate(outroTopo, IntoxicantesMod.POSTE_LUZ.defaultBlockState()
                .setValue(PosteLuzBlock.PARTE, PosteLuzBlock.Parte.TOPO));
        PosteLuzBlock.selfHealAPartirDoTopo(level, outroTopo);
        helper.assertTrue(
                level.getBlockState(outroTopo.below()).getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.CORPO
                        && level.getBlockState(outroTopo.below(2)).getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.BASE,
                "Seeding the lamp head builds the pole downward");

        // quebrar UMA parte derruba o poste inteiro (aqui: base+corpo; o topo
        // era a pedra invasora e SEGUE lá — derrubar poste não derruba vizinho)
        level.destroyBlock(posCorpo, false);
        helper.assertTrue(level.getBlockState(posBase).isAir(),
                "Breaking the body fells the base");
        helper.assertTrue(level.getBlockState(posTopo).is(
                net.minecraft.world.level.block.Blocks.STONE),
                "The foreign block above survives the pole felling");

        // no poste completo (montado do topo), quebrar a BASE derruba corpo+topo
        level.destroyBlock(outroTopo.below(2), false);
        helper.assertTrue(level.getBlockState(outroTopo.below()).isAir()
                        && level.getBlockState(outroTopo).isAir(),
                "Breaking the base fells body and lamp head");

        helper.succeed();
    }

    /**
     * v1.2.30 — O GAGO NÃO SUFFOCA, NÃO SAI E NÃO VIRA LOOT NO CHÃO: o ciclo
     * que o playtest flagrou ("às vezes o gago simplesmente some, e fica
     * dropando um monte de item do mod dentro do mercado") tinha 3 elos, e
     * o teste mata os 3: (1) gerenciarGago com o posto OCUPADO limpa o bloco
     * em vez de teleportar o Gago pra dentro dele; (2) dano de parede
     * (IN_WALL) não passa em serviço; (3) morte no posto não dropa loot.
     */
    @GameTest
    public void gagoNeverSuffocatesOrLootsAtPost(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos centro = helper.absolutePos(new BlockPos(2, 1, 2));
        MarketSystem.setMarketPos(centro);

        // ---- ELO 1: postos ocupados (a reforma da 1.2.26 plantou bloco no
        // balcão; o plantio velho podia bloquear a porta também) — o
        // gerenciador LIMPA o posto do plantão vigente e o Gago nasce de pé.
        // Os DOIS candidatos ficam bloqueados: o destino depende do relógio.
        BlockPos posto = MarketSystem.getPosBalcao(centro);
        BlockPos porta = MarketSystem.getPosPorta(centro);
        for (BlockPos p : new BlockPos[]{posto, posto.above(), porta, porta.above()}) {
            level.setBlockAndUpdate(p,
                    net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState());
        }

        MarketSystem.gerenciarGago(level, centro); // package-private: chamada direta

        // a LIMPEZA do posto derruba os blocos plantados pelo próprio teste
        // (quartzo) como item — isso é a REFORMA (arranjo novo da 1.2.36),
        // não loot de mercado: o chão é limpo antes dos elos 2/3 pra
        // asserção de morte medir só o loot do Gago.
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(centro).inflate(24))) {
            item.discard();
        }

        // v1.2.76: o plantão é SEMPRE o balcão (o guichê da madrugada saiu)
        BlockPos alvo = posto;
        helper.assertTrue(level.getBlockState(alvo).isAir()
                        && level.getBlockState(alvo.above()).isAir(),
                "Occupied post is cleared before the Gago takes it");
        var gagos = level.getEntitiesOfClass(GagoEntity.class,
                new net.minecraft.world.phys.AABB(centro).inflate(48));
        helper.assertTrue(!gagos.isEmpty(), "A Gago exists after management");
        GagoEntity gago = gagos.get(0);

        // ---- ELO 2: sufocamento NÃO passa em serviço (mesmo com o Gago
        // forçado pra dentro de um bloco, o IN_WALL não o fere)
        gago.setupPostoMercado(alvo); // emPostoMercado = true
        float vidaAntes = gago.getHealth();
        var fonte = gago.damageSources().inWall();
        gago.hurtServer(level, fonte, 5.0F);
        helper.assertTrue(gago.isAlive() && gago.getHealth() == vidaAntes,
                "In-wall damage is ignored while on duty");

        // ---- ELO 3: morte no posto NÃO vira loot (sem cachaça/cerveja/R$
        ///cartucho esparzindo no mercado — defesa em profundidade)
        gago.hurtServer(level, gago.damageSources().genericKill(),
                Float.MAX_VALUE);
        // (genericKill bypassa invulnerabilidades; emPostoMercado derrubou a
        // IA no hurtServer — o drop guarda pela POSIÇÃO do posto)
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(alvo).inflate(3));
        helper.assertTrue(drops.isEmpty(),
                "Dying at the post drops no market loot");

        MarketSystem.setMarketPos(null); // hermeticidade: não vaza pro resto da suíte
        helper.succeed();
    }

    // ==================================================== v1.2.32: A 12 EM NIVEL GUN MOD

    @GameTest
    public void escopetaMechanismReloadPumpAndDryFire(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.escopetaCooldownTicks = 2;   // quase zero: o ciclo roda rapido
        teste.escopetaTicksPorShell = 2;   // 1 shell a cada 2 ticks
        teste.escopetaTicksPump = 3;       // pump curto pro teste
        teste.escopetaCapacidadeTubo = 3;
        teste.escopetaBalins = 1;
        teste.escopetaDanoPorBalim = 0.5F;
        teste.escopetaAlcanceMaximo = 12;
        configDeTeste(teste);
        try {
            helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            var atirador = vitimaSobrevivente(helper);
            // placeNewPlayer nasce com abilities de criativo: o instabuild faria
            // o use() ver "reserva infinita" e iniciar recarga no click seco
            atirador.getAbilities().instabuild = false;
            atirador.getAbilities().invulnerable = false;
            BlockPos p1 = helper.absolutePos(new BlockPos(1, 1, 1));
            atirador.absSnapTo(p1.getX(), p1.getY(), p1.getZ());
            atirador.setYRot(0.0F); // olhando pro sul (+Z), onde o alvo vai nascer
            atirador.getInventory().clearContent(); // entrega do guia na 1a entrada nao pode ocupar a mao
            atirador.getInventory().setSelectedSlot(0);
            atirador.getInventory().add(new ItemStack(IntoxicantesMod.ESCOPETA));
            ItemStack arma = atirador.getMainHandItem();
            helper.assertTrue(arma.is(IntoxicantesMod.ESCOPETA), "shotgun in main hand");

            // ---- 1) ARMA VAZIA, SEM CARTUCHO: click seco — nada consumido
            // v1.2.57: o disparo mora no GATILHO (payload), não no use()
            GatilhoPayload.dispararTeste(atirador);
            EscopetaEstado vazio = EscopetaItem.estado(arma);
            helper.assertTrue(vazio.fase() == EscopetaEstado.FASE_PRONTA && !vazio.camara()
                            && vazio.noTubo() == 0,
                    "Dry fire leaves the mechanism untouched");

            // ---- 2) COM CARTUCHOS: a TECLA R inicia a recarga (fase TECLA) e o
            // mecanismo fecha SOZINHO ao encher tubo+câmara (v1.2.48)
            atirador.getInventory().add(new ItemStack(IntoxicantesMod.CARTUCHO, 5));
            RecargaPayload.despacharTeste(atirador, true);
            helper.assertTrue(EscopetaItem.estado(arma).fase()
                            == EscopetaEstado.FASE_TECLA,
                    "Pressing R starts the reload (TECLA)");
            for (int t = 0; t < 60 && EscopetaItem.estado(arma).fase()
                    == EscopetaEstado.FASE_TECLA; t++) {
                arma.inventoryTick(helper.getLevel(), atirador,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            EscopetaEstado fechada = EscopetaItem.estado(arma);
            helper.assertTrue(fechada.camara() && fechada.fase() == EscopetaEstado.FASE_PRONTA,
                    "One R press fills the tube and chambers the first shell (fase="
                            + fechada.fase() + ")");
            int noPente = 5 - (EscopetaItem.contarCartuchos(atirador) + fechada.noTubo()
                    + (fechada.camara() ? 1 : 0));
            helper.assertTrue(noPente == 0,
                    "Every shell came from the inventory (nenhum sumido)");

            // ---- 3) TIRO (GATILHO ESQUERDO): consome a CAMARA e engata o pump
            var alvo = helper.spawn(IntoxicantesMod.TRAFICANTE, new BlockPos(1, 1, 3));
            float vidaAlvo = alvo.getHealth();
            GatilhoPayload.dispararTeste(atirador);
            EscopetaEstado aposTiro = EscopetaItem.estado(arma);
            helper.assertTrue(!aposTiro.camara(),
                    "Firing consumes the CHAMBER, not the inventory");
            helper.assertTrue(aposTiro.fase() == EscopetaEstado.FASE_BOMBA
                            && aposTiro.timer() > 0,
                    "Firing engages the pump cycle");
            helper.assertTrue(alvo.getHealth() < vidaAlvo,
                    "Shot damages the target: vida=" + alvo.getHealth());

            // ---- 5) SIMULO os ticks do pump: proximo shell do tubo entra na camara
            for (int t = 0; t < teste.escopetaTicksPump + 1; t++) {
                arma.getItem().inventoryTick(arma, helper.getLevel(), atirador,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            EscopetaEstado aposPump = EscopetaItem.estado(arma);
            helper.assertTrue(aposPump.camara() && aposPump.fase()
                            == EscopetaEstado.FASE_PRONTA,
                    "Pump cycle re-chambers from the tube");
            helper.assertTrue(aposPump.noTubo() == aposTiro.noTubo() - 1,
                    "Re-chambering drains the tube: tubo=" + aposPump.noTubo());
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    // ==================================================== v1.2.33: O .38 EM NIVEL GUN MOD

    @GameTest
    public void revolverMechanismReloadSpinAndCascavelFire(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.revolverCooldownTicks = 2;   // quase zero: o ciclo roda rapido
        teste.revolverTicksPorShell = 2;   // 1 shell a cada 2 ticks
        teste.revolverTicksFecho = 2;      // fecho curto pro teste
        teste.revolverDano = 0.5F;
        teste.revolverAlcanceMaximo = 12;
        configDeTeste(teste);
        try {
            helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            var atirador = vitimaSobrevivente(helper);
            atirador.getAbilities().instabuild = false;
            atirador.getAbilities().invulnerable = false;
            BlockPos p1 = helper.absolutePos(new BlockPos(1, 1, 1));
            atirador.absSnapTo(p1.getX(), p1.getY(), p1.getZ());
            atirador.setYRot(0.0F); // olhando pro sul (+Z), onde o alvo vai nascer
            atirador.getInventory().clearContent(); // entrega do guia na 1a entrada nao pode ocupar a mao
            atirador.getInventory().setSelectedSlot(0);
            atirador.getInventory().add(new ItemStack(IntoxicantesMod.REVOLVER));
            ItemStack arma = atirador.getMainHandItem();
            helper.assertTrue(arma.is(IntoxicantesMod.REVOLVER), ".38 in main hand");

            // ---- 1) TAMBOR VAZIO, SEM CARTUCHO: click seco — nada consumido
            // v1.2.57: o disparo mora no GATILHO (payload), não no use()
            GatilhoPayload.dispararTeste(atirador);
            RevolverEstado vazio = RevolverItem.estado(arma);
            helper.assertTrue(vazio.fase() == RevolverEstado.FASE_PRONTA && vazio.balas() == 0,
                    "Dry fire leaves the drum untouched");

            // ---- 2) COM CARTUCHOS: a TECLA R inicia a recarga e fecha SOZINHA
            atirador.getInventory().add(new ItemStack(IntoxicantesMod.CARTUCHO_38, 6));
            RecargaPayload.despacharTeste(atirador, true);
            helper.assertTrue(RevolverItem.estado(arma).fase()
                            == RevolverEstado.FASE_TECLA,
                    "Pressing R starts the cylinder reload (TECLA)");
            for (int t = 0; t < 60 && RevolverItem.estado(arma).fase()
                    == RevolverEstado.FASE_TECLA; t++) {
                arma.inventoryTick(helper.getLevel(), atirador,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            RevolverEstado fechada = RevolverItem.estado(arma);
            helper.assertTrue(fechada.fase() == RevolverEstado.FASE_FERRAMENTA
                            && fechada.timer() > 0,
                    "Releasing the reload engages the latch closing");

            // ---- 4) SIMULO o fecho: tambor travado, PRONTA pra atirar
            for (int t = 0; t < teste.revolverTicksFecho + 1; t++) {
                arma.inventoryTick(helper.getLevel(), atirador,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            RevolverEstado fechado = RevolverItem.estado(arma);
            helper.assertTrue(fechado.fase() == RevolverEstado.FASE_PRONTA,
                    "Latch closes into PRONTA");
            helper.assertTrue(fechado.alinhadaCarregada(),
                    "The chamber aligned with the barrel is loaded");
            int conservacao = RevolverItem.contarCartucho38(atirador) + fechado.balas();
            helper.assertTrue(conservacao == 6,
                    "Every .38 came from the inventory (nenhum sumido): " + conservacao);

            // ---- 5) CASCAVEL: 3 tiros seguidos do tambor, sem recarregar
            var alvo = helper.spawn(IntoxicantesMod.TRAFICANTE, new BlockPos(1, 1, 3));
            float vidaAlvo = alvo.getHealth();
            int balasAntes = fechado.balas();
            for (int tiro = 0; tiro < 3; tiro++) {
                // cooldown do tiro anterior expira por tempo de jogo: reseta pra 0
                atirador.getCooldowns().addCooldown(arma, 0);
                GatilhoPayload.dispararTeste(atirador);
                RevolverEstado apos = RevolverItem.estado(arma);
                helper.assertTrue(apos.balas() == balasAntes - tiro - 1,
                        "Shot " + (tiro + 1) + " consumes exactly one chamber: "
                                + apos.balas());
                helper.assertTrue(apos.fase() == RevolverEstado.FASE_FERRAMENTA,
                        "Shot engages the latch cycle");
                // fecha o fecho pra proxima bala ficar pronta
                for (int t = 0; t < teste.revolverTicksFecho + 1; t++) {
                    arma.getItem().inventoryTick(arma, helper.getLevel(), atirador,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                helper.assertTrue(RevolverItem.estado(arma).fase()
                                == RevolverEstado.FASE_PRONTA,
                        "Latch closes for the next shot");
            }
            helper.assertTrue(alvo.getHealth() < vidaAlvo,
                    "Shots damage the target: vida=" + alvo.getHealth());

            // ---- 6) GIRO NO SECO: câmara seca com bala em OUTRO buraco — o tambor
            // gira no click (o .38 de filme) em vez de exigir recarga
            atirador.getCooldowns().addCooldown(arma, 0); // cooldown do último tiro expira
            arma.set(RevolverItem.ESTADO, new RevolverEstado(
                    1 << 3, 0, 0, RevolverEstado.FASE_PRONTA)); // bit 3, câmara 0 seca
            GatilhoPayload.dispararTeste(atirador);
            RevolverEstado girado = RevolverItem.estado(arma);
            helper.assertTrue(girado.camara() == 1 && girado.fase()
                            == RevolverEstado.FASE_FERRAMENTA,
                    "Spin advances the cylinder to the next chamber");
            helper.assertTrue(girado.balas() == 1,
                    "Spin does NOT consume the cartridge");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.41 — A TECLA R na 12: payload C2S aperta → fase TECLA → shells entram
     * pelo motor (inventoryTick). v1.2.48 — R de UM APERTO: a janela fecha
     * SOZINHA ao encher o tubo; soltar não interrompe mais. Mesma anatomia do
     * botão direito, sem travar pose.
     */
    @GameTest
    public void escopetaRecargaTeclaR(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.escopetaCapacidadeTubo = 5;
        teste.escopetaTicksPorShell = 2; // recarga rápida no teste
        configDeTeste(teste);
        try {
            ServerPlayer player = vitimaSobrevivente(helper);
            player.getAbilities().instabuild = false; // consumo real de munição
            player.getInventory().clearContent(); // entrega do guia na 1a entrada nao pode ocupar a mao
            player.getInventory().setSelectedSlot(0); // R exige arma na main
            player.getInventory().add(new ItemStack(IntoxicantesMod.ESCOPETA));
            player.getInventory().add(new ItemStack(IntoxicantesMod.CARTUCHO, 10));

            // APERTA R (o payload chama o mesmo caminho do servidor)
            RecargaPayload.despacharTeste(player, true);
            ItemStack naMao = pilhaDe(player, IntoxicantesMod.ESCOPETA);
            EscopetaEstado inicio = EscopetaItem.estado(naMao);
            helper.assertTrue(inicio.fase() == EscopetaEstado.FASE_TECLA,
                "Press R enters TECLA phase (was " + inicio.fase() + ")");

            // UM APERTO SÓ: 10 cartuchos na reserva e tubo de 5 — o mecanismo
            // precisa fechar SOZINHO (1 shell a cada TICKS_SHELL=2 + camara),
            // sem ninguém soltar nem segurar nada:
            boolean fechouSozinho = false;
            for (int t = 0; t < 30; t++) {
                naMao.inventoryTick(helper.getLevel(), player,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                if (EscopetaItem.estado(naMao).fase() != EscopetaEstado.FASE_TECLA) {
                    fechouSozinho = true;
                    break;
                }
            }
            helper.assertTrue(fechouSozinho,
                "Reload closes BY ITSELF when the tube fills (1 press)");

            // SOLTA R DEPOIS DO FECHO: inerte (não reabre, não atrapalha)
            RecargaPayload.despacharTeste(player, false);
            EscopetaEstado fechada = EscopetaItem.estado(naMao);
            helper.assertTrue(fechada.fase() == EscopetaEstado.FASE_PRONTA,
                "Release after auto-close stays PRONTA (fase=" + fechada.fase() + ")");
            helper.assertTrue(fechada.noTubo() + (fechada.camara() ? 1 : 0)
                    == teste.escopetaCapacidadeTubo,
                "Full load: the tube feeds the chamber on close (tubo=" + fechada.noTubo()
                    + " camara=" + fechada.camara() + ")");
            int gastados = 10 - contarNoInventario(player, IntoxicantesMod.CARTUCHO);
            helper.assertTrue(gastados == teste.escopetaCapacidadeTubo,
                "Each shell consumed 1 cartridge (gastos=" + gastados + ")");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /**
     * v1.2.41 — A TECLA R no .38: aperta entra na fase TECLA, balas caem nos
     * buracos a partir da câmara. v1.2.48 — UM APERTO: o fecho acontece
     * SOZINHO quando o tambor enche; soltar não faz nada.
     */
    @GameTest
    public void revolverRecargaTeclaR(GameTestHelper helper) {
        ModConfig atual = ModConfig.get();
        ModConfig teste = new ModConfig();
        teste.revolverTicksPorShell = 2;
        configDeTeste(teste);
        try {
            ServerPlayer player = vitimaSobrevivente(helper);
            player.getAbilities().instabuild = false; // consumo real de munição
            player.getInventory().clearContent(); // entrega do guia na 1a entrada nao pode ocupar a mao
            player.getInventory().setSelectedSlot(0); // R exige arma na main
            player.getInventory().add(new ItemStack(IntoxicantesMod.REVOLVER));
            player.getInventory().add(new ItemStack(IntoxicantesMod.CARTUCHO_38, 12));

            RecargaPayload.despacharTeste(player, true);
            ItemStack naMao = pilhaDe(player, IntoxicantesMod.REVOLVER);
            RevolverEstado inicio = RevolverItem.estado(naMao);
            helper.assertTrue(inicio.fase() == RevolverEstado.FASE_TECLA,
                "Press R enters TECLA phase (was " + inicio.fase() + ")");

            // UM APERTO SÓ: o tambor enche e o fecho acontece SOZINHO
            for (int t = 0; t < 40 && RevolverItem.estado(naMao).fase()
                    == RevolverEstado.FASE_TECLA; t++) {
                naMao.inventoryTick(helper.getLevel(), player,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            // SOLTA R DEPOIS DO FECHO: inerte (não reabre, não atrapalha)
            RecargaPayload.despacharTeste(player, false);
            RevolverEstado fechada = RevolverItem.estado(naMao);
            helper.assertTrue(fechada.fase() == RevolverEstado.FASE_FERRAMENTA,
                "Cylinder closed by fecho on its own (fase=" + fechada.fase() + ")");
            helper.assertTrue(fechada.balas() == RevolverEstado.CAPACIDADE,
                "Full cylinder after ONE press (balas=" + fechada.balas() + ")");
            int gastados = 12 - contarNoInventario(player, IntoxicantesMod.CARTUCHO_38);
            helper.assertTrue(gastados == fechada.balas(),
                "Each round consumed 1 cartridge (gastos=" + gastados + " balas="
                    + fechada.balas() + ")");
        } finally {
            configDeTeste(atual);
        }
        helper.succeed();
    }

    /** Acha a stack de um item no inventário do player (helper dos testes). */
    private static ItemStack pilhaDe(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(item)) {
                return slot;
            }
        }
        throw new IllegalStateException("item não encontrado no inventário: " + item);
    }

    /** Conta itens de um tipo no inventário do player (helper dos testes). */
    private static int contarNoInventario(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(item)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    // ==================================================== BEBIDAS (v1.2.50)

    /** Executa o clique do jogador no bloco (fluxo real das maquinas).
     * v1.2.59: quem consome a dose da mão é a PRÓPRIA máquina (split do
     * buffer) — nenhum shrink externo aqui, senão é consumo duplo. */
    private static void beClique(net.minecraft.server.level.ServerLevel level, BlockPos pos,
            ServerPlayer player, ItemStack mao) {
        var be = level.getBlockEntity(pos);
        if (be instanceof MaquinaPrimaBlockEntity maquina) {
            if (!mao.isEmpty() && maquina.tentarCarregar(level, player, mao)) {
                // dose já entrou no buffer (o motor gasta lote a lote)
            } else {
                maquina.interagir(level, player);
            }
        } else if (be instanceof DornaBebidaBlockEntity dorna) {
            if (!mao.isEmpty() && dorna.tentarCarregar(level, player, mao)) {
                // dose já entrou no buffer
            } else {
                dorna.interagir(level, player);
            }
        } else if (be instanceof AlambiqueBlockEntity alambique) {
            if (!mao.isEmpty() && alambique.tentarCarregar(level, player, mao)) {
                // dose já entrou no buffer
            } else {
                alambique.interagir(level, player);
            }
        } else if (be instanceof BarrilBebidaBlockEntity barril) {
            barril.interagir(level, player, mao);
        }
    }

    private static int conta(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var st = player.getInventory().getItem(i);
            if (st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
    }

    @GameTest(maxTicks = 3000)
    public void cadeiaCachacaMoendaDornaAlambiqueBarril(GameTestHelper helper) {
        // escala de teste (0.05) fixada no boot: TestesBebidaBoot (global, sem briga entre testes paralelos)
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var centro = helper.absolutePos(BlockPos.ZERO);

        // MOENDA: 4 canas -> 4 caldo + bagaco (2s na velocidade de teste)
        var posMoenda = centro.offset(2, 0, 0);
        level.setBlockAndUpdate(posMoenda, IntoxicantesMod.MOENDA_CANA.defaultBlockState());
        beClique(level, posMoenda, player, new ItemStack(IntoxicantesMod.CANA_DE_ACUCAR, 4));
        helper.runAfterDelay(60, () -> {
            beClique(level, posMoenda, player, ItemStack.EMPTY);
            helper.assertTrue(conta(player, IntoxicantesMod.CALDO_DE_CANA) >= 4,
                    "Moenda deve render 4 caldo de cana");
            helper.assertTrue(conta(player, IntoxicantesMod.BAGACO_DE_CANA) >= 1,
                    "Moenda deve render bagaco de cana");

            // DORNA: 4 caldo -> 4 mosto fermentado (7min/20 = 21s -> 440 ticks)
            var posDorna = centro.offset(0, 0, 2);
            level.setBlockAndUpdate(posDorna, IntoxicantesMod.DORNA_BEBIDA.defaultBlockState());
            beClique(level, posDorna, player, new ItemStack(IntoxicantesMod.CALDO_DE_CANA, 4));
            helper.runAfterDelay(500, () -> {
                beClique(level, posDorna, player, ItemStack.EMPTY);
                helper.assertTrue(conta(player, IntoxicantesMod.MOSTO_CANA_FERMENTADO) >= 4,
                        "Dorna deve fermentar caldo em mosto");

                // ALAMBIQUE (com fogo): 4 mosto -> 2 cachaca jovem (90s/20 = 4.5s)
                var posAlambique = centro.offset(0, 0, 4);
                level.setBlockAndUpdate(posAlambique.below(),
                        net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
                level.setBlockAndUpdate(posAlambique, IntoxicantesMod.ALAMBIQUE.defaultBlockState());
                beClique(level, posAlambique, player, new ItemStack(IntoxicantesMod.MOSTO_CANA_FERMENTADO, 4));
                helper.runAfterDelay(140, () -> {
                    beClique(level, posAlambique, player, ItemStack.EMPTY);
                    helper.assertTrue(conta(player, IntoxicantesMod.CACHACA_JOVEM) >= 2,
                            "Alambique deve destilar mosto em cachaca jovem");

                    // BARRIL DE CACHACA: 2 jovens, matura (600s/20 = 30s = 620t)
                    var posBarril = centro.offset(2, 0, 4);
                    level.setBlockAndUpdate(posBarril, IntoxicantesMod.BARRIL_CACHACA.defaultBlockState());
                    beClique(level, posBarril, player, new ItemStack(IntoxicantesMod.CACHACA_JOVEM, 2));
                    helper.runAfterDelay(660, () -> {
                        for (int i = 0; i < 4; i++) {
                            beClique(level, posBarril, player,
                                    new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 1));
                        }
                        helper.assertTrue(conta(player, IntoxicantesMod.CACHACA) >= 4,
                                "Barril de cachaca deve render 4 garrafas");
                                            helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(maxTicks = 3000)
    public void cadeiaCervejaCaldeiraoBarril(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var centro = helper.absolutePos(BlockPos.ZERO);

        // CALDEIRAO (agua embaixo): 4 malte (40s/20=2s) -> lupulo (30s/20=1.5s)
        var posCaldeirao = centro.offset(0, 0, 2);
        level.setBlockAndUpdate(posCaldeirao.below(),
                net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(posCaldeirao, IntoxicantesMod.CALDEIRAO_MOSTURA.defaultBlockState());
        beClique(level, posCaldeirao, player, new ItemStack(IntoxicantesMod.MALTE, 4));
        helper.runAfterDelay(80, () -> {
            beClique(level, posCaldeirao, player, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 1));
            helper.runAfterDelay(80, () -> {
                beClique(level, posCaldeirao, player, ItemStack.EMPTY);
                helper.assertTrue(conta(player, IntoxicantesMod.MOSTO_CERVEJA_LUPULADO) >= 4,
                        "Caldeirao deve render mosto lupulado (malte + lupulo)");

                // BARRIL DE CERVEJA: fermenta 480s/20=24s + condiciona 120s/20=6s
                var posBarril = centro.offset(2, 0, 2);
                level.setBlockAndUpdate(posBarril, IntoxicantesMod.BARRIL_CERVEJA.defaultBlockState());
                beClique(level, posBarril, player, new ItemStack(IntoxicantesMod.MOSTO_CERVEJA_LUPULADO, 4));
                helper.runAfterDelay(660, () -> {
                    for (int i = 0; i < 4; i++) {
                        beClique(level, posBarril, player,
                                new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 1));
                    }
                    helper.assertTrue(conta(player, IntoxicantesMod.CERVEJA) >= 4,
                            "Barril de cerveja deve render 4 garrafas");
                                    helper.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 3000)
    public void cadeiaRumMelacoBarril(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var centro = helper.absolutePos(BlockPos.ZERO);

        // DORNA: 1 melaco -> 4 mosto de rum
        var posDorna = centro.offset(0, 0, 2);
        level.setBlockAndUpdate(posDorna, IntoxicantesMod.DORNA_BEBIDA.defaultBlockState());
        beClique(level, posDorna, player, new ItemStack(IntoxicantesMod.MELACO, 1));
        helper.runAfterDelay(500, () -> {
            beClique(level, posDorna, player, ItemStack.EMPTY);
            helper.assertTrue(conta(player, IntoxicantesMod.MOSTO_RUM_FERMENTADO) >= 4,
                    "Dorna deve fermentar melaco em mosto de rum");

            // ALAMBIQUE -> RUM JOVEM
            var posAlambique = centro.offset(0, 0, 4);
            level.setBlockAndUpdate(posAlambique.below(),
                    net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
            level.setBlockAndUpdate(posAlambique, IntoxicantesMod.ALAMBIQUE.defaultBlockState());
            beClique(level, posAlambique, player, new ItemStack(IntoxicantesMod.MOSTO_RUM_FERMENTADO, 4));
            helper.runAfterDelay(140, () -> {
                beClique(level, posAlambique, player, ItemStack.EMPTY);
                helper.assertTrue(conta(player, IntoxicantesMod.RUM_JOVEM) >= 2,
                        "Alambique deve destilar rum jovem");

                // BARRIL DE RUM: envelhece 600s/20 = 30s
                var posBarril = centro.offset(2, 0, 4);
                level.setBlockAndUpdate(posBarril, IntoxicantesMod.BARRIL_RUM.defaultBlockState());
                beClique(level, posBarril, player, new ItemStack(IntoxicantesMod.RUM_JOVEM, 2));
                helper.runAfterDelay(660, () -> {
                    for (int i = 0; i < 4; i++) {
                        beClique(level, posBarril, player,
                                new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 1));
                    }
                    helper.assertTrue(conta(player, IntoxicantesMod.RUM) >= 4,
                            "Barril de rum deve render 4 garrafas");
                                    helper.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 3000)
    public void cadeiaVinhoPrensaBarril(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var centro = helper.absolutePos(BlockPos.ZERO);

        // PRENSA: 6 uvas -> 4 mosto de uva
        var posPrensa = centro.offset(0, 0, 2);
        level.setBlockAndUpdate(posPrensa, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        beClique(level, posPrensa, player, new ItemStack(IntoxicantesMod.UVA, 6));
        helper.runAfterDelay(80, () -> {
            beClique(level, posPrensa, player, ItemStack.EMPTY);
            helper.assertTrue(conta(player, IntoxicantesMod.MOSTO_DE_UVA) >= 4,
                    "Prensa deve esmagar uvas em mosto");

            // BARRIL DE VINHO: fermenta 300s/20=15s + matura 300s/20=15s
            var posBarril = centro.offset(2, 0, 2);
            level.setBlockAndUpdate(posBarril, IntoxicantesMod.BARRIL_VINHO.defaultBlockState());
            beClique(level, posBarril, player, new ItemStack(IntoxicantesMod.MOSTO_DE_UVA, 4));
            helper.runAfterDelay(660, () -> {
                for (int i = 0; i < 4; i++) {
                    beClique(level, posBarril, player,
                            new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 1));
                }
                helper.assertTrue(conta(player, IntoxicantesMod.VINHO) >= 4,
                        "Barril de vinho deve render 4 garrafas");
                            helper.succeed();
            });
        });
    }

    @GameTest
    public void dornaPersisteLoteAposSaveLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, IntoxicantesMod.DORNA_BEBIDA.defaultBlockState());
        beClique(level, pos, player, new ItemStack(IntoxicantesMod.CALDO_DE_CANA, 4));

        helper.runAfterDelay(20, () -> {
            var be = level.getBlockEntity(pos);
            helper.assertTrue(be instanceof DornaBebidaBlockEntity, "Dorna precisa de BlockEntity");
            var saida = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            be.saveWithoutMetadata(saida);
            var entrada = TagValueInput.create(ProblemReporter.DISCARDING,
                    level.registryAccess(), saida.buildResult());
            be.loadWithComponents(entrada);
            helper.assertTrue(((DornaBebidaBlockEntity) be).fermentando(),
                    "O lote da dorna deve sobreviver a save/load");
            helper.succeed();
        });
    }

}
