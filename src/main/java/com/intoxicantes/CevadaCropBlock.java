package com.intoxicantes;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A CEVADA (v1.2.50) — o cereal da cerveja: crop vanilla-style (7 estágios,
 * como o trigo) que rende o grão + as próprias sementes. Açúcar tem cana,
 * cerveja tem cevada: a matéria-prima é DIFERENTE do trigo (spec 11 pede
 * fidelidade — nada de "vinho de trigo" kkkk).
 *
 * Wild patch: manchas selvagens em planícies/taiga (JSON de worldgen gerado
 * por tools/gen_worldgen.py).
 */
public class CevadaCropBlock extends CropBlock {

    public CevadaCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public ItemLike getBaseSeedId() {
        return IntoxicantesMod.SEMENTE_CEVADA;
    }
}
