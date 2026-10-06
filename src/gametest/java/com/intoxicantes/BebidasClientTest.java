package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Percorre a interação real de cada bebida no cliente e servidor integrados. */
public class BebidasClientTest implements FabricClientGameTest {
    private static final BlockPos MESA = new BlockPos(0, 90, 0);
    private static final AABB AREA = new AABB(-1, 90, -1, 2, 92, 2);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        Item[] bebidas = { IntoxicantesMod.CERVEJA, IntoxicantesMod.VINHO,
                IntoxicantesMod.CACHACA, IntoxicantesMod.HIDROMEL, IntoxicantesMod.RUM,
                IntoxicantesMod.SUCO_DETOX, IntoxicantesMod.AGUA_DE_COCO, IntoxicantesMod.CHA_LUPULO };
        // v1.2.65: cada bebida devolve A GARRAFA VAZIA DELA (USE_REMAINDER), não
        // a garrafa de vidro vanilla — o paralelo abaixo é a expectativa certa.
        Item[] vazias = { IntoxicantesMod.CERVEJA_VAZIA, IntoxicantesMod.VINHO_VAZIA,
                IntoxicantesMod.CACHACA_VAZIA, IntoxicantesMod.HIDROMEL_VAZIA, IntoxicantesMod.RUM_VAZIA,
                IntoxicantesMod.SUCO_DETOX_VAZIA, IntoxicantesMod.AGUA_DE_COCO_VAZIA, IntoxicantesMod.CHA_LUPULO_VAZIA };
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                    for (int y = 90; y <= 94; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(MESA, Blocks.OAK_SLAB.defaultBlockState());
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(0.5, 90, -2.5);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
            });
            world.getConnection().waitForChunksRender();
            for (int i = 0; i < bebidas.length; i++) {
                Item bebida = bebidas[i];
                Item vazio = vazias[i];
                String nome = "Garrafa de teste " + i;
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                    Embriaguez.setNivelTeste(player, 0);
                    ItemStack stack = new ItemStack(bebida, 2);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(nome));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                });
                context.waitFor(client -> client.player.getMainHandItem().is(bebida));
                mirar(context, new Vec3(0.5, 91.1, 0.5));
                context.takeScreenshot("bebida-" + i + "-na-mao");
                context.getInput().holdShift();
                context.waitTicks(3);
                mirar(context, new Vec3(0.5, 90.5, 0.5));
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                        && hit.getBlockPos().equals(MESA) && hit.getDirection() == Direction.UP);
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                context.getInput().releaseShift();
                server.waitFor(mc -> mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA).size() == 1);
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    if (player.getMainHandItem().getCount() != 1) throw new AssertionError("Colocação não retirou uma unidade");
                    if (player.getInventory().countItem(Items.GLASS_BOTTLE) != 0) throw new AssertionError("Colocação gerou recipiente");
                    var garrafa = mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA).getFirst();
                    if (Math.abs(garrafa.getY() - 90.5) > 0.001) throw new AssertionError("Garrafa não acompanha altura da laje");
                    // Guardar a unidade restante libera a mão para a retirada real.
                    player.getInventory().setItem(9, player.getMainHandItem());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
                context.waitFor(client -> client.player.getMainHandItem().isEmpty());
                mirar(context, new Vec3(0.5, 90.8, 0.5));
                context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() instanceof BebidaDecorativaEntity);
                context.takeScreenshot("bebida-" + i + "-sobre-laje");
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                server.waitFor(mc -> mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA).isEmpty());
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    ItemStack stack = player.getMainHandItem();
                    if (!stack.is(bebida) || stack.getCount() != 1
                            || !Component.literal(nome).equals(stack.get(DataComponents.CUSTOM_NAME))) {
                        throw new AssertionError("Recolhimento perdeu bebida, quantidade ou nome");
                    }
                });
                context.waitFor(client -> client.player.getMainHandItem().is(bebida));
                context.getInput().lookAt(0, -25);
                // Confirma o começo real do gole antes de interrompê-lo. Um
                // clique ainda em cooldown não pode fazer esse caso passar.
                context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                try {
                    context.waitFor(client -> client.player.isUsingItem());
                    context.waitTicks(5);
                } finally {
                    context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                }
                context.waitTicks(3);
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    if (!player.getMainHandItem().is(bebida)
                            || player.getInventory().countItem(vazio) != 0) {
                        throw new AssertionError("Interromper o consumo gastou a bebida ou gerou recipiente");
                    }
                });
                context.getInput().holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 48);
                server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getMainHandItem().is(vazio));
                server.runOnServer(mc -> {
                    var inventory = mc.getPlayerList().getPlayers().getFirst().getInventory();
                    if (inventory.countItem(vazio) != 1 || inventory.countItem(bebida) != 1) {
                        throw new AssertionError("Consumo não preservou a outra unidade e exatamente um recipiente");
                    }
                });
            }
        }
    }

    private static void mirar(ClientGameTestContext context, Vec3 alvo) {
        float[] angulos = context.computeOnClient(client -> {
            Vec3 delta = alvo.subtract(client.player.getEyePosition());
            return new float[] {
                    (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))) };
        });
        context.getInput().lookAt(angulos[0], angulos[1]);
        context.waitTicks(2);
    }
}
