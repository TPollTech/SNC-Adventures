package com.intoxicantes;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * O GUIA DO SNC ADVENTURES — o livro-guia oficial do mod (item de verdade,
 * clássico dos mods): use com o botão direito e a tela do guia abre no client.
 *
 * DECISÃO DO USUÁRIO: o guia NÃO é acessível por comando — o ÚNICO caminho
 * de acesso/reposição é TER O LIVRO (entrega na 1ª entrada do mundo; perdeu?
 * crafta outro: livro vanilla + R$, em data/intoxicantes/recipe/guia_snc.json).
 *
 * A abertura é client-side (a tela é pintura; todo o conteúdo mora no
 * GuiaConteudo, que lê ProcessosBebida/ModConfig). O servidor não participa
 * do use() — igual o livro vanilla, sem payload nem trava.
 */
public class GuiaItem extends Item {

    public GuiaItem(Properties properties) {
        super(properties.rarity(Rarity.RARE)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        Component.translatable("item.intoxicantes.guia_snc.lore")))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand mao) {
        if (level.isClientSide()) {
            GuiaClient.abrir();
        }
        return InteractionResult.SUCCESS;
    }
}
