package com.intoxicantes;

import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Per-merchant stock; persists across unloads and never refreshes an open trade. */
final class DailyTradeStock {
    // Real NBT schema version: version 1 migrates the unlimited 1.1.1 catalogs.
    private static final int SCHEMA_VERSION = 1;
    private int schemaVersion;
    private long lastRestockDay = Long.MIN_VALUE;

    static long tradingDay(long overworldTicks) {
        // Vanilla day starts at 06:00. The supply delivery arrives at 07:00.
        return Math.floorDiv(overworldTicks - 1000L, 24000L);
    }

    MerchantOffers refresh(MerchantOffers current, long ticks, boolean trading,
            Supplier<MerchantOffers> catalog) {
        if (trading) return current;
        long day = tradingDay(ticks);
        boolean migration = schemaVersion < SCHEMA_VERSION;
        if (migration || current == null || current.isEmpty() || day > lastRestockDay) {
            MerchantOffers next = catalog.get();
            if (migration && current != null) preserveUsedStock(current, next);
            schemaVersion = SCHEMA_VERSION;
            lastRestockDay = day;
            return next;
        }
        // Moving time backwards cannot refill stock repeatedly.
        return current;
    }

    private static void preserveUsedStock(MerchantOffers previous, MerchantOffers next) {
        for (MerchantOffer offer : next) {
            for (MerchantOffer old : previous) {
                if (old.getBaseCostA().is(offer.getBaseCostA().getItem())
                        && ItemStack.isSameItemSameComponents(old.getResult(), offer.getResult())) {
                    int used = Math.min(old.getUses(), offer.getMaxUses());
                    for (int i = 0; i < used; i++) offer.increaseUses();
                    break;
                }
            }
        }
    }

    void load(ValueInput input) {
        schemaVersion = input.getIntOr("CommerceSchema", 0);
        lastRestockDay = input.getLongOr("LastRestockDay", Long.MIN_VALUE);
    }

    void save(ValueOutput output) {
        output.putInt("CommerceSchema", schemaVersion);
        output.putLong("LastRestockDay", lastRestockDay);
    }
}
