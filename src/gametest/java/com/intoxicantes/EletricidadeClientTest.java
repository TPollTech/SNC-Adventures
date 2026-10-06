package com.intoxicantes;

import com.intoxicantes.energia.SNCEnergiesAdapter;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Regressão pela interface usada pelo jogador, em mundo isolado. */
public class EletricidadeClientTest implements FabricClientGameTest {
    private static final BlockPos PAINEL = new BlockPos(0, 90, 2);
    private static final BlockPos CHAVE = PAINEL.east(2);
    private static final BlockPos LED = PAINEL.east(3).above();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            client.resizeGui();
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                for (int x = -4; x <= 7; x++) for (int z = -4; z <= 5; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                    for (int y = 90; y <= 94; y++)
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                player.getInventory().clearContent();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                level.setBlockAndUpdate(PAINEL, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
                level.setBlockAndUpdate(PAINEL.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
                level.setBlockAndUpdate(CHAVE, IntoxicantesMod.INTERRUPTOR_SIMPLES.defaultBlockState());
                level.setBlockAndUpdate(PAINEL.east(3), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
                level.setBlockAndUpdate(LED, IntoxicantesMod.LAMPADA_LED_50W.defaultBlockState());
                var gerador = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                        net.minecraft.resources.Identifier.parse("snc_energies:coal_generator")).orElseThrow();
                level.setBlockAndUpdate(PAINEL.below(), gerador.defaultBlockState());
                var storage = SNCEnergiesAdapter.storageDe(level.getBlockEntity(PAINEL.below()), Direction.UP);
                try {
                    storage.getClass().getMethod("insert", long.class, boolean.class).invoke(storage, 64000L, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Real source could not be filled", e);
                }
                player.teleportTo(2.5, 90, 0.5);
            });
            context.waitTicks(30);
            server.runOnServer(mc -> {
                if (mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT))
                    throw new AssertionError("Open switch cannot power floodlight");
            });
            mirar(context, CHAVE, new Vec3(2.5, 90.55, 2.94));
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT));
            context.waitTicks(5);
            context.takeScreenshot("eletrica-refletor-alimentado");
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> !mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT));
            context.takeScreenshot("eletrica-interruptor-cortou");
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT));

            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90, 0.5));
            context.waitTicks(5);
            mirar(context, PAINEL, new Vec3(0.5, 90.7, 2.94));
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(QuadroEletricoScreen.class);
            context.runOnClient(client -> {
                if (client.gui.screen().isPauseScreen()) throw new AssertionError("Panel must not pause electrical simulation");
                if (!(client.gui.screen() instanceof QuadroEletricoScreen painel)
                        || !painel.layoutCabeNaTela())
                    throw new AssertionError("Electrical panel must fit inside the GUI viewport");
                String leitura = net.minecraft.network.chat.Component.translatable(
                        "eletricidade.intoxicantes.multimetro.lampada", 50, "ON", "ON", "tomadas", "127 V", "0.4 A").getString();
                if (leitura.contains("eletricidade.intoxicantes") || !leitura.contains("50"))
                    throw new AssertionError("Lamp diagnostics must have a working translation");
            });
            context.waitTicks(25);
            context.takeScreenshot("eletrica-quadro-medicao-viva");
            for (int guiScale : new int[]{3, 4}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(guiScale);
                    client.resizeGui();
                    if (!(client.gui.screen() instanceof QuadroEletricoScreen painel)
                            || !painel.layoutCabeNaTela())
                        throw new AssertionError("Panel must fit GUI scale " + guiScale);
                });
                context.waitTicks(2);
            }
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            // A alavanca C2 usa a mesma caixa desenhada pela tela.
            clicarAlavanca(context, 1);
            server.waitFor(mc -> !mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT));
            clicarAlavanca(context, 1);
            server.waitFor(mc -> mc.overworld().getBlockState(LED).getValue(LedPotenciaBlock.LIT));
            server.runOnServer(mc -> {
                var quadro = (QuadroEletricoBlock.QuadroEletricoBlockEntity) mc.overworld().getBlockEntity(PAINEL);
                if (quadro.circuitos().get(1).consumoW() != 50L
                        || quadro.circuitos().get(1).totalConsumidoE <= 0L)
                    throw new AssertionError("Live panel must meter actual floodlight consumption");
            });
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);

            // Cabo ponto a ponto: montar as pontas, mas conectar/cortar apenas
            // com os mesmos cliques e teclas usados pelo jogador.
            BlockPos a = new BlockPos(2, 92, 2);
            BlockPos b = new BlockPos(6, 92, 2);
            BlockPos carga = new BlockPos(7, 90, 2);
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                level.setBlockAndUpdate(PAINEL.east().above(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
                level.setBlockAndUpdate(a.west(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
                level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                        .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(b.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
                level.setBlockAndUpdate(b.east().below(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
                level.setBlockAndUpdate(carga, IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
                level.setBlockAndUpdate(b, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                        .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
                player.getInventory().clearContent();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(IntoxicantesMod.BOBINA_COBRE_2_5MM, 2));
                player.teleportTo(4.5, 90, -0.5);
                player.getAbilities().instabuild = false;
            });
            context.waitTicks(10);
            mirar(context, a, Vec3.atCenterOf(a));
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst()
                    .getMainHandItem().get(IntoxicantesMod.TIPO_ORIGEM_ELO) != null);
            mirar(context, b, Vec3.atCenterOf(b));
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> com.intoxicantes.energia.CabosSuspensos.get(mc.overworld()).contemPar(a, b)
                    && mc.overworld().getBlockState(carga).getValue(LedPotenciaBlock.LIT));
            context.waitFor(client -> CabosClient.elosRecebidos() == 1);
            context.waitTicks(10);
            mirar(context, a, Vec3.atCenterOf(a));
            context.takeScreenshot("eletrica-cabo-suspenso");
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(5);
            mirar(context, a, Vec3.atCenterOf(a));
            context.getInput().pressKey(options -> options.keyUse);
            server.waitFor(mc -> !com.intoxicantes.energia.CabosSuspensos.get(mc.overworld()).contemPar(a, b)
                    && !mc.overworld().getBlockState(carga).getValue(LedPotenciaBlock.LIT));
            context.waitFor(client -> CabosClient.elosRecebidos() == 0);
            context.getInput().releaseKey(options -> options.keyShift);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (player.getMainHandItem().getCount() != 2)
                    throw new AssertionError("Cutting must return exactly the spool spent by the span");
            });
            context.takeScreenshot("eletrica-cabo-recolhido");
        }
    }

    private static void mirar(ClientGameTestContext context, BlockPos pos, Vec3 alvo) {
        float[] angulos = context.computeOnClient(client -> {
            Vec3 delta = alvo.subtract(client.player.getEyePosition());
            return new float[] {(float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))};
        });
        context.getInput().lookAt(angulos[0], angulos[1]);
        context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getBlockPos().equals(pos));
    }

    private static void clicarAlavanca(ClientGameTestContext context, int circuito) {
        double[] ponto = context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof QuadroEletricoScreen tela))
                throw new AssertionError("Electrical panel is not open");
            double[] centro = tela.centroAlavanca(circuito);
            return new double[] {centro[0] * client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth(),
                    centro[1] * client.getWindow().getHeight() / client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(ponto[0], ponto[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }
}
