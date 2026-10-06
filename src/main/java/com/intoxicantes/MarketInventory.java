package com.intoxicantes;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Shared by all customers of one Gago; keys remain stable across loyalty unlocks. */
final class MarketInventory {
    private final Map<String, Integer> used = new HashMap<>();
    private long lastRestockDay = Long.MIN_VALUE;
    private boolean initialized;

    void refresh(long ticks, boolean trading, MerchantOffers legacyOffers) {
        if (trading) return;
        long day = DailyTradeStock.tradingDay(ticks);
        if (!initialized) {
            // compatibility: migrate pre-1.2 vanilla offers once, only when CommerceStock is absent.
            if (legacyOffers != null) {
                for (var entry : TradeCatalog.gago(3)) {
                    for (var old : legacyOffers) {
                        if (old.getResult().is(entry.item()) && old.getResult().getCount() == entry.count()) {
                            used.put(entry.id(), Math.min(entry.stock(), old.getUses()));
                            break;
                        }
                    }
                }
            }
            initialized = true;
            lastRestockDay = day;
        } else if (day > lastRestockDay) {
            used.clear();
            lastRestockDay = day;
        }
    }

    int remaining(TradeCatalog.Entry entry) {
        return Math.max(0, entry.stock() - used.getOrDefault(entry.id(), 0));
    }

    boolean consume(TradeCatalog.Entry entry) {
        if (remaining(entry) == 0) return false;
        used.merge(entry.id(), 1, Integer::sum);
        return true;
    }

    void load(ValueInput input) {
        used.clear();
        initialized = input.getBooleanOr("CommerceStock", false);
        lastRestockDay = input.getLongOr("LastRestockDay", Long.MIN_VALUE);
        var quantities = input.childOrEmpty("DailyPurchases");
        for (var entry : TradeCatalog.all()) {
            int count = quantities.getIntOr(entry.id(), 0);
            used.put(entry.id(), Math.clamp(count, 0, entry.stock()));
        }
    }

    void save(ValueOutput output) {
        output.putBoolean("CommerceStock", initialized);
        output.putLong("LastRestockDay", lastRestockDay);
        var quantities = output.child("DailyPurchases");
        used.forEach(quantities::putInt);
    }
}
