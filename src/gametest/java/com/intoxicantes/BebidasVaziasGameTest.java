package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v1.2.65 — A GARRAFA QUE VOLTA + a prova server-side do que o player vê:
 *
 *  1. VAZIA CORRETA: beber de verdade (finishUsingItem, o caminho do clique
 *     no jogo) devolve a GARRAFA VAZIA DA PRÓPRIA bebida — não a de outra,
 *     não a garrafa de vidro vanilla.
 *  2. EFEITO APLICADO: o mesmo gole aplica os efeitos assinatura — se o
 *     player disse "efeito não funcionou", aqui o servidor prova que o
 *     caminho do consumo aplica o que promete.
 *  3. MENU DAS MÁQUINAS: o createMenu real de cada máquina constrói o menu
 *     server-side sem quebrar (é o primeiro passo do caminho da GUI). Nas
 *     máquinas grandes o BlockEntity mora em UMA das partes: procuramos o
 *     MenuProvider na estrutura 2×2, como o clique do player faria.
 */
public class BebidasVaziasGameTest {

    /** Bebe a pilha na mão do player e devolve O RECIPIENTE (retorno do gole). */
    private static ItemStack beber(ServerPlayer player, Item bebida) {
        ItemStack stack = new ItemStack(bebida);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.finishUsingItem(player.level(), player);
    }

    /** O USE_REMAINDER da bebida, materializado (é o recipiente que volta). */
    private static ItemStack sobraDe(Item bebida) {
        UseRemainder resto = new ItemStack(bebida).get(DataComponents.USE_REMAINDER);
        return resto == null ? ItemStack.EMPTY : resto.convertInto().create();
    }

    // ==================================================== 1. O RECIPIENTE CERTO

    @GameTest
    public void beberCervejaDevolveGarrafaVaziaDaCerveja(GameTestHelper helper) {
        ItemStack sobra = sobraDe(IntoxicantesMod.CERVEJA);
        helper.assertTrue(sobra.is(IntoxicantesMod.CERVEJA_VAZIA),
                "beber cerveja devolve a GARRAFA VAZIA DA CERVEJA (veio " + sobra.getItem() + ")");
        helper.assertTrue(!sobra.is(IntoxicantesMod.VINHO_VAZIA),
                "a vazia do vinho não tem nada a ver aqui");
        helper.assertTrue(!sobra.is(net.minecraft.world.item.Items.GLASS_BOTTLE),
                "a garrafa de vidro vanilla saiu do circuito das bebidas 3D");
        helper.succeed();
    }

    @GameTest
    public void cadaBebidaTemSuaPropriaVazia(GameTestHelper helper) {
        Item[][] pares = {
                {IntoxicantesMod.CERVEJA, IntoxicantesMod.CERVEJA_VAZIA},
                {IntoxicantesMod.VINHO, IntoxicantesMod.VINHO_VAZIA},
                {IntoxicantesMod.CACHACA, IntoxicantesMod.CACHACA_VAZIA},
                {IntoxicantesMod.HIDROMEL, IntoxicantesMod.HIDROMEL_VAZIA},
                {IntoxicantesMod.RUM, IntoxicantesMod.RUM_VAZIA},
                {IntoxicantesMod.SUCO_DETOX, IntoxicantesMod.SUCO_DETOX_VAZIA},
                {IntoxicantesMod.AGUA_DE_COCO, IntoxicantesMod.AGUA_DE_COCO_VAZIA},
                {IntoxicantesMod.CHA_LUPULO, IntoxicantesMod.CHA_LUPULO_VAZIA},
        };
        for (Item[] par : pares) {
            ItemStack sobra = sobraDe(par[0]);
            helper.assertTrue(sobra.is(par[1]),
                    "recipiente errado: beber " + par[0] + " devolveu "
                            + sobra.getItem() + " — esperado " + par[1]);
        }
        helper.succeed();
    }

    // ==================================================== 2. O EFEITO CHEGA

    @GameTest
    public void beberCervejaAplicaEfeitoAssinatura(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        beber(player, IntoxicantesMod.CERVEJA);
        helper.assertTrue(player.getEffect(MobEffects.STRENGTH) != null,
                "a FORÇA da cerveja deve ser aplicada ao beber");
        helper.assertTrue(player.getEffect(MobEffects.NAUSEA) != null,
                "e a NAUSEA também");
        helper.succeed();
    }

    // ==================================================== 3. O MENU DAS MÁQUINAS

    /** Acha o MenuProvider da máquina (multi-blocos: o BE mora numa parte). */
    private AbstractContainerMenu menuDaMaquina(GameTestHelper helper, ServerPlayer player,
            Block bloco, int id) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        BlockEntity be = ((net.minecraft.world.level.block.EntityBlock) bloco)
                .newBlockEntity(pos, bloco.defaultBlockState());
        helper.assertTrue(be instanceof MenuProvider,
                bloco + " abre menu (MenuProvider)");
        return ((MenuProvider) be).createMenu(id, player.getInventory(), player);
    }

    @GameTest
    public void menuDasMaquinasConstroiParaTodasAsSeis(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Block[] maquinas = {
                IntoxicantesMod.MOENDA_CANA, IntoxicantesMod.PRENSA_UVAS,
                IntoxicantesMod.CALDEIRAO_MOSTURA, IntoxicantesMod.DORNA_BEBIDA,
                IntoxicantesMod.ALAMBIQUE, IntoxicantesMod.BARRIL_CERVEJA,
        };
        int id = 0;
        for (Block bloco : maquinas) {
            AbstractContainerMenu menu = menuDaMaquina(helper, player, bloco, id++);
            helper.assertTrue(menu != null,
                    bloco + " constrói o menu server-side");
        }
        helper.succeed();
    }

    // ==================================================== 4. O BARRIL E A VAZIA (v1.2.66)

    /** Barril de teste em fase PRONTA com o lote do rótulo (sem esperar o timer). */
    private BarrilBebidaBlockEntity barrilPronto(GameTestHelper helper, Block blocoBarril) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(BlockPos.ZERO, blocoBarril);
        BarrilBebidaBlockEntity barril = (BarrilBebidaBlockEntity)
                helper.getLevel().getBlockEntity(pos);
        var rec = ProcessosBebida.barrilDe(barril.getBebida());
        helper.assertTrue(rec.isPresent(), "barril tem receita (rótulo " + barril.getBebida() + ")");
        barril.setItem(0, new ItemStack(rec.get().input(), rec.get().qtdIn()));
        barril.testeAvancarFaseAtePronta(helper.getLevel());
        helper.assertTrue(barril.faseClient() == BarrilBebidaBlockEntity.FASE_PRONTA,
                "barril de teste ficou PRONTO");
        return barril;
    }

    /**
     * v1.2.66 — O CIRCUITO DO VIDRO: o barril engarrafa com a VAZIA DA
     * PRÓPRIA bebida na mão (não com a de outra, não gasta vidro vanilla),
     * e a bebida volta a devolver a vazia ao ser bebida.
     */
    @GameTest(maxTicks = 100)
    public void barrilEngarrafaComAVaziaDaPropriaBebida(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BarrilBebidaBlockEntity barril = barrilPronto(helper, IntoxicantesMod.BARRIL_CERVEJA);
        player.getInventory().clearContent();

        // a VAZIA CERTA engarrafa (e não consome garrafa de vidro vanilla)
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.CERVEJA_VAZIA, 2));
        helper.assertTrue(barril.interagir(helper.getLevel(), player,
                player.getMainHandItem()), "barril aceita a VAZIA da própria bebida");
        helper.assertTrue(conta(player, IntoxicantesMod.CERVEJA) == 1,
                "engarrafou 1 cerveja com a vazia da cerveja");
        helper.assertTrue(conta(player, net.minecraft.world.item.Items.GLASS_BOTTLE) == 0,
                "não gastou garrafa de vidro vanilla");
        helper.assertTrue(conta(player, IntoxicantesMod.CERVEJA_VAZIA) == 1,
                "consumiu 1 vazia");

        // a VAZIA DE OUTRA bebida não engarrafa
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.VINHO_VAZIA, 1));
        helper.assertTrue(!barril.interagir(helper.getLevel(), player,
                player.getMainHandItem()), "barril RECUSA a vazia de outra bebida");
        helper.assertTrue(conta(player, IntoxicantesMod.CERVEJA) == 1,
                "recusa não gera produto");
        helper.succeed();
    }

    /** Conta itens no inventário (mesma convenção do CommerceGameTest). */
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
}
