package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * v1.2.69 — A GUI das máquinas NO MINECRAFT DE VERDADE (client gametest):
 * abre a prensa pelo clique direito real do jogador, valida que o painel
 * desenhado bate com a geometria do menu (o offset duplo do 26.3 fazia o
 * painel nascer no canto inferior direito) e registra screenshots.
 */
public class GuiMaquinasClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                // plataforma plana, máquina ao alcance do clique (2 blocos à frente)
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                    for (int y = 90; y < 94; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.UVA, 12));
                // prensa na PARTE BAIXA (o BE mora embaixo)
                level.setBlockAndUpdate(new BlockPos(0, 90, 2), IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
                player.teleportTo(0.5, 90, 0.5);
            });
            context.waitTicks(20);
            mirar(context, new net.minecraft.world.phys.Vec3(0.5, 90.5, 2.5));
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(new BlockPos(0, 90, 2)));
            // clique normal (sem shift) abre a GUI (useItemOn abre com item na mão)
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(TelaMaquinaBase.class);
            context.waitTicks(10);

            // ============ PROVA DO LAYOUT: painel == geometria do menu ============
            int[] canto = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof TelaMaquinaBase tela)) {
                    throw new AssertionError("A GUI da prensa não está aberta");
                }
                int esperadoX = (client.getWindow().getGuiScaledWidth() - TelaMaquinaBase.IMG_W) / 2;
                int esperadoY = (client.getWindow().getGuiScaledHeight() - TelaMaquinaBase.IMG_H) / 2;
                if (tela.painelX() != esperadoX || tela.painelY() != esperadoY) {
                    throw new AssertionError("Painel fora do centro: leftPos=" + tela.painelX()
                            + "/" + esperadoX + " topPos=" + tela.painelY() + "/" + esperadoY);
                }
                // o slot de insumo DEVE ficar dentro do painel (não no canto da tela)
                var slot = tela.getMenu().slots.get(0);
                int slotX = tela.painelX() + slot.x;
                int slotY = tela.painelY() + slot.y;
                if (slotX < tela.painelX() || slotX > tela.painelX() + TelaMaquinaBase.IMG_W
                        || slotY < tela.painelY() || slotY > tela.painelY() + TelaMaquinaBase.IMG_H) {
                    throw new AssertionError("Slot de insumo fora do painel: " + slotX + "," + slotY);
                }
                IntoxicantesMod.LOGGER.info("[GuiMaquinas] painel em {},{} tamanho {}x{} — layout OK",
                        tela.painelX(), tela.painelY(), TelaMaquinaBase.IMG_W, TelaMaquinaBase.IMG_H);
                return new int[] { tela.painelX() + 8 + 8, tela.painelY() + 206 + 8 };
            });
            context.takeScreenshot("gui-prensa-layout");

            // ============ INTERAÇÃO REAL: pega as uvas na hotbar, solta no insumo ============
            // hotbar slot 0: x=8, y=206 no painel; insumo da prensa: {26,28} (TipoMaquina)
            int[] alvos = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof TelaMaquinaBase tela)) {
                    throw new AssertionError("A GUI fechou antes da interação");
                }
                return new int[] {
                        tela.painelX() + 8 + 8, tela.painelY() + 206 + 8,   // hotbar 0 (centro)
                        tela.painelX() + 26 + 8, tela.painelY() + 28 + 8 }; // insumo (centro)
            });
            mover(context, alvos[0], alvos[1]);
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // pega a pilha
            context.waitTicks(2);
            mover(context, alvos[2], alvos[3]);
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // solta no slot
            context.waitTicks(2);
            // as 12 uvas inteiras entraram no buffer da prensa (2 doses pro motor)
            server.waitFor(mc -> {
                var be = mc.overworld().getBlockEntity(new BlockPos(0, 90, 2));
                return be instanceof MaquinaPrimaBlockEntity prensa
                        && prensa.getItem(0).getCount() == 12;
            });
            context.waitTicks(5);
            context.takeScreenshot("gui-prensa-carregada");

            // ============ ENCERRA ============
            context.getInput().pressKey(InputConstants.KEY_E);
            context.waitTicks(5);
        }
    }

    private static void mirar(ClientGameTestContext context, net.minecraft.world.phys.Vec3 alvo) {
        float[] angulos = context.computeOnClient(client -> {
            net.minecraft.world.phys.Vec3 delta = alvo.subtract(client.player.getEyePosition());
            return new float[] {
                    (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))) };
        });
        context.getInput().lookAt(angulos[0], angulos[1]);
        context.waitTicks(2);
    }

    private static void mover(ClientGameTestContext context, int x, int y) {
        double[] pos = context.computeOnClient(client -> new double[] {
                x * (double) client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth(),
                y * (double) client.getWindow().getHeight() / client.getWindow().getGuiScaledHeight() });
        context.getInput().setCursorPos(pos[0], pos[1]);
    }
}
