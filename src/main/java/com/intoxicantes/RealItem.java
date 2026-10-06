package com.intoxicantes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dinheiro físico do SNC Adventures — a família R$ inteira.
 *
 * UPGRADE DA ECONOMIA: antes o mod tinha UM item (a cédula azul, hoje
 * "real") e cada unidade valia 1 — a nota de 2 "valia 1". Agora cada
 * nota/moeda tem o VALOR FACIAL dela (moeda_1=1, real=2, nota_5..nota_200)
 * e toda a economia física soma VALOR, não unidade:
 *
 * <ul>
 *   <li>{@link #valorDaPilha} / {@link #totalNoInventario} — /depositar;</li>
 *   <li>{@link #trocar} — /sacar e drops entregam o TROCO CERTINHO
 *       (R$ 340 vira nota de 200 + nota de 100 + 2 de 20, não 340 itens);</li>
 *   <li>{@link #custoDe} — preços de comércio vanilla viram custo em
 *       NOTAS (custo A + complemento B do MerchantOffer).</li>
 * </ul>
 *
 * A fonte da verdade do valor mora AQUI: o {@link IntoxicantesMod} registra
 * cada item pelo factory {@code dinheiro(name, valor)} e o mapa
 * {@link #VALORES} (ordem de registro = ordem do troco) faz o resto.
 */
public class RealItem extends Item {
    /** Valor facial por item, em ordem crescente de registro (moeda→nota alta). */
    private static final Map<Item, Integer> VALORES = new LinkedHashMap<>();

    public RealItem(Properties properties) {
        super(properties);
    }

    /** Registra o valor facial de um dinheiro (chamado pelo factory do mod). */
    public static void registrarValor(Item item, int valor) {
        VALORES.put(item, valor);
    }

    /** Valor facial da nota/moeda (0 se o item não for dinheiro). */
    public static int valorDe(Item item) {
        return VALORES.getOrDefault(item, 0);
    }

    /** É dinheiro da família R$? */
    public static boolean eDinheiro(Item item) {
        return VALORES.containsKey(item);
    }

    /** Valor total de uma pilha (unidades × valor facial; 0 se não é dinheiro). */
    public static int valorDaPilha(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int valor = valorDe(stack.getItem());
        return valor > 0 ? valor * stack.getCount() : 0;
    }

    /** Soma o VALOR de todo o dinheiro da família R$ no inventário. */
    public static int totalNoInventario(Player player) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            total += valorDaPilha(inv.getItem(i));
        }
        return total;
    }

    /**
     * Decompõe um valor em pilhas de notas (greedy: da maior pra menor).
     * Com a moeda de 1 na família TODO inteiro fecha exato — /sacar 7
     * entrega nota de 5 + 2 moedas, nunca "7 itens de 1".
     */
    public static List<ItemStack> trocar(int quantia) {
        List<ItemStack> pilhas = new ArrayList<>();
        int restante = Math.max(0, quantia);
        List<Map.Entry<Item, Integer>> ordem = new ArrayList<>(VALORES.entrySet());
        for (int i = ordem.size() - 1; i >= 0 && restante > 0; i--) {
            Item item = ordem.get(i).getKey();
            int valor = ordem.get(i).getValue();
            int quantas = restante / valor;
            if (quantas <= 0) {
                continue;
            }
            while (quantas > 0) {
                int pilha = Math.min(64, quantas);
                quantas -= pilha;
                restante -= pilha * valor;
                pilhas.add(new ItemStack(item, pilha));
            }
        }
        return pilhas;
    }

    /**
     * O custo em DINHEIRO FÍSICO de um preço — o par (custoA, custoB) do
     * comércio vanilla. Custo A = a maior nota necessária; custo B = o
     * resto, arredondado PRA CIMA se não fechar exato numa pilha (pagar
     * mais é comércio normal; pagar menos, nunca).
     * Teto natural: 2 pilhas de 64 × R$ 200 = R$ 25.600.
     */
    public static ItemStack[] custoDe(int preco) {
        int alvo = Math.min(Math.max(1, preco), 25600);
        List<Map.Entry<Item, Integer>> ordem = new ArrayList<>(VALORES.entrySet());
        for (int i = ordem.size() - 1; i >= 0; i--) {
            Item itemA = ordem.get(i).getKey();
            int valorA = ordem.get(i).getValue();
            if (valorA > alvo) {
                continue;
            }
            ItemStack a = new ItemStack(itemA, Math.min(64, alvo / valorA));
            int sobra = alvo - a.getCount() * valorA;
            if (sobra <= 0) {
                return new ItemStack[]{a, ItemStack.EMPTY};
            }
            // resto: a MENOR combinação exata numa única pilha — moedas
            // primeiro (a moeda de 1 fecha qualquer resto até 64 exato),
            // subindo de denominação. Nunca fica devendo e nunca estoura:
            // pagar 5+2 numa compra de 6 é caixa normal; pagar 5+5, não.
            for (int j = 0; j <= i; j++) {
                Item itemB = ordem.get(j).getKey();
                int valorB = ordem.get(j).getValue();
                int quantas = (sobra + valorB - 1) / valorB; // ceil: nunca fica devendo
                if (quantas > 0 && quantas <= 64) {
                    return new ItemStack[]{a, new ItemStack(itemB, quantas)};
                }
            }
            return new ItemStack[]{a, ItemStack.EMPTY};
        }
        return new ItemStack[]{new ItemStack(IntoxicantesMod.MOEDA_1, alvo), ItemStack.EMPTY};
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> output,
            net.minecraft.world.item.TooltipFlag flag) {
        int valor = valorDe(stack.getItem());
        if (valor > 0) {
            output.accept(net.minecraft.network.chat.Component.translatable(
                    "commerce.intoxicantes.real.valor", valor));
        }
        output.accept(net.minecraft.network.chat.Component.translatable("commerce.intoxicantes.real.hint"));
        super.appendHoverText(stack, context, display, output, flag);
    }
}
