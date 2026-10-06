package com.intoxicantes;

import net.minecraft.world.item.Item;

/**
 * v1.2.65 — O RECIPIENTE DEVOLVIDO: cada bebida 3D tem a sua garrafa vazia
 * (mesma identidade, tampa e rótulo; sem líquido nem espuma). Ela volta pra
 * mão do fregues pelo USE_REMAINDER quando a bebida acaba e empilha só com
 * ela mesma — a vazia da cerveja nunca vira a do vinho (comportamento padrão
 * de Item: mesma identidade + mesmos componentes).
 */
public class GarrafaVaziaItem extends Item {
    public GarrafaVaziaItem(Properties properties) {
        super(properties);
    }
}
