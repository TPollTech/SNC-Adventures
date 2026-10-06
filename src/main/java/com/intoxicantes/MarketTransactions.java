package com.intoxicantes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative cash and inventory exchange for the custom market screen. */
final class MarketTransactions {
    enum Result { OK, INVALID, OUT_OF_STOCK, NO_MONEY, NO_HARVEST, BALANCE_LIMIT }

    private MarketTransactions() {}

    static Result trade(ServerPlayer player, GagoEntity merchant, int index, boolean selling) {
        if (!merchant.isAlive() || merchant.isRemoved() || merchant.isPuto()
                || player.isSpectator() || !player.isAlive() || player.level() != merchant.level()
                || merchant.getTradingPlayer() != player || player.distanceToSqr(merchant) > 36) {
            return Result.INVALID;
        }
        var catalog = selling ? TradeCatalog.harvests() : TradeCatalog.gago(FidelidadeData.getTier(player));
        if (index < 0 || index >= catalog.size()) return Result.INVALID;
        var entry = catalog.get(index);
        var stock = merchant.marketInventory();
        if (stock.remaining(entry) == 0) return Result.OUT_OF_STOCK;
        if (selling) {
            if (countHarvest(player, entry) < entry.count()) return Result.NO_HARVEST;
            if ((long) PlayerMoney.get(player) + entry.price() > Integer.MAX_VALUE) return Result.BALANCE_LIMIT;
            int remaining = entry.count();
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.is(entry.item())) continue;
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
            PlayerMoney.add(player, entry.price());
        } else {
            int price = FidelidadeData.precoComDesconto(player, entry.price());
            if (!PlayerMoney.subtrair(player, price)) return Result.NO_MONEY;
            ItemStack result = entry.stack();
            if (!player.getInventory().add(result)) {
                player.drop(result, false, net.minecraft.util.Prediction.PREDICTED);
            }
            FidelidadeData.registrarCompra(player);
        }
        stock.consume(entry);
        // v1.2.7: a transacao TEM party — caixa registradora + notas de R$ voando
        // na cara do fregues (visivel pra quem está perto: a esquina rica é visível)
        player.level().playSound(null, merchant.getX(), merchant.getY(), merchant.getZ(),
                IntoxicantesMod.CAIXA_REGISTRADORA, SoundSource.NEUTRAL, 0.8F, 1.0F);
        player.level().sendParticles(Particulas.DINHEIRO,
                merchant.getX(), merchant.getY() + 1.9, merchant.getZ(),
                12, 0.35, 0.4, 0.35, 0.04);
        player.level().sendParticles(ParticleTypes.WAX_ON,
                merchant.getX(), merchant.getY() + 1.9, merchant.getZ(),
                4, 0.3, 0.3, 0.3, 0.01);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return Result.OK;
    }

    static int countHarvest(ServerPlayer player, TradeCatalog.Entry entry) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(entry.item())) count += stack.getCount();
        }
        return count;
    }
}
