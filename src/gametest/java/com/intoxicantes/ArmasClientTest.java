package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * v1.2.70 — O GATILHO ESQUERDO com os CONTROLES REAIS do jogo (nada de
 * chamada direta de método): o clique esquerdo do mouse entra pelo
 * MouseHandler.onButton, o MouseBotaoMixin puxa o gatilho, o
 * GatilhoPayload chega ao servidor e a ESCOPETA dispara de verdade.
 *
 * Registra o bug que o hotfix conserta: com o 26.3 entregando botões em
 * códigos SDL (esquerdo = 1), o mixin antigo (códigos GLFW 0/1) engolia o
 * esquerdo sem atirar. Se este teste falha, o gatilho quebrou de novo.
 *
 * Cobre também o ADS do botão direito (espelho rastreado no mixin — o
 * vanilla nunca seta isRightPressed() com o evento engolido) e o ciclo do
 * pump: o segundo clique só dispara depois da câmara recarregada.
 */
public class ArmasClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                level.getGameRules().set(GameRules.SPAWN_MOBS, false, mc);
                // arena limpa: chão de pedra, nada para acertar na frente
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                        for (int y = 90; y <= 96; y++) {
                            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(0.5, 90, 0.5);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                // escopeta pronta: câmara engatilhada + tubo cheio
                ItemStack escopeta = new ItemStack(IntoxicantesMod.ESCOPETA);
                escopeta.set(EscopetaItem.ESTADO,
                        new EscopetaEstado(5, true, 0, EscopetaEstado.FASE_PRONTA));
                player.setItemInHand(InteractionHand.MAIN_HAND, escopeta);
            });
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> client.player.getMainHandItem().is(IntoxicantesMod.ESCOPETA));
            context.waitTicks(10);
            context.getInput().lookAt(0, 0);
            context.waitTicks(3);

            // ============ 1º TIRO: o clique ESQUERDO dispara ============
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            server.waitFor(mc -> !EscopetaItem.estado(
                    mc.getPlayerList().getPlayers().getFirst().getMainHandItem()).camara());
            server.runOnServer(mc -> {
                EscopetaEstado estado = EscopetaItem.estado(
                        mc.getPlayerList().getPlayers().getFirst().getMainHandItem());
                if (estado.noTubo() != 5 || estado.camara()) {
                    throw new AssertionError("O clique esquerdo não disparou: tubo="
                            + estado.noTubo() + " camara=" + estado.camara());
                }
            });
            context.takeScreenshot("armas-gatilho-esquerdo-disparou");

            // o pump cicla sozinho e recarrega a câmara do tubo
            server.waitFor(mc -> EscopetaItem.estado(
                    mc.getPlayerList().getPlayers().getFirst().getMainHandItem()).camara());

            // ============ ADS: segurar o DIREITO liga a mira ============
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(mc -> ArmasClient.mirando());
            context.waitTicks(10); // zoom suave assenta
            context.takeScreenshot("armas-ads-direito");
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitFor(mc -> !ArmasClient.mirando());

            // ============ 2º TIRO: mecanismo pronto de novo ============
            context.waitTicks(5);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            server.waitFor(mc -> {
                EscopetaEstado estado = EscopetaItem.estado(
                        mc.getPlayerList().getPlayers().getFirst().getMainHandItem());
                return !estado.camara() && estado.noTubo() == 4;
            });
            context.takeScreenshot("armas-segundo-tiro-pump-ciclou");
        }
    }
}
