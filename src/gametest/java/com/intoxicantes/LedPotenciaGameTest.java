package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v1.2.70 — A FAMÍLIA DE LÂMPADAS POR POTÊNCIA: 10 watts (5 → 200), a luz
 * ESCALA com o watt, o clique liga/desliga e a receita é em série.
 */
public class LedPotenciaGameTest {

    /**
     * A ESCALA: cada watt ilumina o nível certo (5W=7 … 200W=15), o bulbo
     * NUNCA vaza glow (zeroGlow — a torch é quem dona do glow 2 na luz 7) e
     * a família é BULBO/REFLETOR/HIGH-BAY pelos watts.
     */
    @GameTest
    public void luzEscalaComOWattEFamiliasVisuais(GameTestHelper helper) {
        int[] esperadoLuz = {7, 8, 8, 9, 10, 11, 13, 14, 15, 15};
        for (int i = 0; i < IntoxicantesMod.LAMPADAS_POTENCIA.size(); i++) {
            Block bloco = IntoxicantesMod.LAMPADAS_POTENCIA.get(i);
            int watts = LedPotenciaBlock.wattsDoBloco(bloco);
            helper.assertTrue(watts == LedPotenciaBlock.WATTS[i],
                    "Block " + BuiltInRegistries.BLOCK.getKey(bloco)
                            + " carries watt " + LedPotenciaBlock.WATTS[i]);
            helper.assertTrue(LedPotenciaBlock.luzPorWatts(watts) == esperadoLuz[i],
                    watts + "W shines at light " + esperadoLuz[i]);

            BlockState acesa = bloco.defaultBlockState().setValue(LedPotenciaBlock.LIT, true);
            helper.assertTrue(bloco.defaultBlockState().getValue(LedPotenciaBlock.WATTAGE) == i, "Each LED stores its correct power index");
            helper.assertTrue(!bloco.defaultBlockState().getValue(LedPotenciaBlock.LIT), "A new LED needs electricity");
            helper.assertTrue(acesa.getLightEmission() == esperadoLuz[i],
                    "Placed " + watts + "W emits light " + esperadoLuz[i]);
            BlockState morta = acesa.setValue(LedPotenciaBlock.LIT, Boolean.FALSE);
            helper.assertTrue(morta.getLightEmission() == 0,
                    "A dead " + watts + "W emits nothing");
            helper.assertTrue(!morta.canOcclude(),
                    "LED does not occlude neighbors");

            // a família visual: bulbo (5-20), refletor (30-50), high-bay (100-200)
            LedPotenciaBlock.Familia familia = LedPotenciaBlock.familiaDoBloco(bloco);
            LedPotenciaBlock.Familia esperada = watts >= 100
                    ? LedPotenciaBlock.Familia.HIGH_BAY
                    : (watts >= 30 ? LedPotenciaBlock.Familia.REFLETOR
                    : LedPotenciaBlock.Familia.BULBO);
            helper.assertTrue(familia == esperada,
                    watts + "W is a " + esperada + " (got " + familia + ")");
        }
        helper.succeed();
    }

    /** O LIGA/DESLIGA: clique alterna LIT (e a luz some/volta com o estado). */
    @GameTest
    public void cliqueLigaEDesligaALampada(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0,
                "An unpowered 15W starts dark");
        BlockPos painelPos = pos.west(2);
        level.setBlockAndUpdate(painelPos, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        var painel = (QuadroEletricoBlock.QuadroEletricoBlockEntity) level.getBlockEntity(painelPos);
        painel.buffer().insert(1000L, false);
        painel.reconstruirTopologia();
        painel.tickRede(level);
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 9, "Powered 15W lights at level 9");

        var player = helper.makeMockServerPlayerInLevel();
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(level.getBlockState(pos).getValue(LedPotenciaBlock.LIT) == Boolean.FALSE
                        && level.getBlockState(pos).getLightEmission() == 0,
                "Click turns the bulb OFF (light 0, neighbors already updated)");
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        painel.tickRede(level);
        helper.assertTrue(level.getBlockState(pos).getValue(LedPotenciaBlock.LIT) == Boolean.TRUE
                        && level.getBlockState(pos).getLightEmission() == 9,
                "Second click turns the bulb back ON");
        helper.succeed();
    }

    /** A RECEITA EM SÉRIE: cada degrau usa o anterior + o ingrediente da família. */
    @GameTest
    public void receitaEmSerieSobeDegrauPorDegrau(GameTestHelper helper) {
        var receitas = helper.getLevel().getServer().getRecipeManager();
        // a cadeia TODA existe: 10 receitas, uma por watt (a escada completa)
        for (int watts : LedPotenciaBlock.WATTS) {
            var chave = net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            IntoxicantesMod.MOD_ID, "lampada_led_" + watts + "w"));
            helper.assertTrue(receitas.byKey(chave).isPresent(),
                    watts + "W has its own recipe (the ladder is complete)");
        }
        helper.succeed();
    }

    /** A TROCA rápida: segurar outra potência e clicar troca no lugar (a antiga volta). */
    @GameTest
    public void trocaRapidaEntrePotencias(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_LED_5W.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(IntoxicantesMod.LAMPADA_LED_200W));

        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(new ItemStack(IntoxicantesMod.LAMPADA_LED_200W),
                level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getBlockState(pos).getBlock() == IntoxicantesMod.LAMPADA_LED_200W,
                "Clicking with a 200W swaps the 5W in place");
        helper.assertTrue(player.getInventory().countItem(IntoxicantesMod.LAMPADA_LED_5W.asItem()) == 1,
                "The old 5W returns to the inventory (nothing vanishes)");
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0,
                "Swapping power does not create free electricity");
        BlockPos painelPos = pos.west(2);
        level.setBlockAndUpdate(painelPos, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        var painel = (QuadroEletricoBlock.QuadroEletricoBlockEntity) level.getBlockEntity(painelPos);
        painel.buffer().insert(1000L, false);
        painel.reconstruirTopologia();
        painel.tickRede(level);
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
                "A supplied 200W high-bay shines at full power");
        helper.succeed();
    }
}
