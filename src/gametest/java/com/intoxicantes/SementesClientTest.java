package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Carregamento real dos seis saquinhos, suas perspectivas e plantio no sobrevivência. */
public class SementesClientTest implements FabricClientGameTest {
    private record Cultivo(String id, Item semente, Block planta) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        Cultivo[] cultivos = {
                new Cultivo("semente_maconha", IntoxicantesMod.SEMENTE_MACONHA, IntoxicantesMod.MACONHA_PLANT),
                new Cultivo("semente_lupulo", IntoxicantesMod.SEMENTE_LOUPULO, IntoxicantesMod.LOUPULO_PLANT),
                new Cultivo("semente_uva", IntoxicantesMod.SEMENTE_UVA, IntoxicantesMod.UVA_PLANT),
                new Cultivo("semente_cafe", IntoxicantesMod.SEMENTE_CAFE, IntoxicantesMod.CAFE_PLANT),
                new Cultivo("semente_papoula", IntoxicantesMod.SEMENTE_PAPOULA, IntoxicantesMod.PAPOULA_PLANT),
                new Cultivo("semente_cevada", IntoxicantesMod.SEMENTE_CEVADA, IntoxicantesMod.CEVADA_PLANT)
        };
        int escalaAnterior = context.computeOnClient(client -> client.options.guiScale().get());
        int fovAnterior = context.computeOnClient(client -> client.options.fov().get());
        boolean balancoAnterior = context.computeOnClient(client -> client.options.bobView().get());
        CameraType cameraAnterior = context.computeOnClient(client -> client.options.getCameraType());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.fov().set(70);
            client.options.bobView().set(false);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.resizeGui();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, mc);
                level.getGameRules().set(GameRules.SPAWN_MOBS, false, mc);
                level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, mc);
                for (int x = -9; x <= 9; x++) for (int z = -7; z <= 7; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = 90; y <= 95; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(0.5, 90, -3.5);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                for (int i = 0; i < cultivos.length; i++) {
                    var solo = solo(i);
                    level.setBlockAndUpdate(solo, cultivos[i].planta() == IntoxicantesMod.UVA_PLANT
                            ? Blocks.DIRT.defaultBlockState()
                            : Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                    player.getInventory().setItem(i, new ItemStack(cultivos[i].semente(), 2));
                }
            });
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> client.player.getMainHandItem().is(cultivos[0].semente()));
            context.runOnClient(client -> {
                for (Cultivo cultivo : cultivos) conferirModeloCarregado(client, cultivo);
            });
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("sementes-inventario-colecao");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null);

            for (int i = 0; i < cultivos.length; i++) {
                Cultivo cultivo = cultivos[i];
                BlockPos solo = solo(i);
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    player.getInventory().clearContent();
                    player.getInventory().setSelectedSlot(0);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(cultivo.semente(), 2));
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(cultivo.semente(), 1));
                    player.teleportTo(solo.getX() + 0.5, 90, -2.5);
                });
                context.waitFor(client -> client.player.getMainHandItem().is(cultivo.semente())
                        && client.player.getMainHandItem().getCount() == 2
                        && client.player.getOffhandItem().is(cultivo.semente()));
                context.getInput().lookAt(0, -20);
                context.waitTicks(8);
                context.takeScreenshot("sementes-" + cultivo.id() + "-duas-maos");
                context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                context.waitTicks(5);
                context.takeScreenshot("sementes-" + cultivo.id() + "-terceira-pessoa");
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                context.waitTicks(5);

                mirar(context, Vec3.atCenterOf(solo).add(0,
                        cultivo.planta() == IntoxicantesMod.UVA_PLANT ? 0.5 : 0.4375, 0));
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                        && hit.getBlockPos().equals(solo) && hit.getDirection() == Direction.UP);
                // Usa o atalho de interação configurado, inclusive se estiver no mouse.
                context.getInput().pressKey(options -> options.keyUse);
                server.waitFor(mc -> mc.overworld().getBlockState(solo.above()).is(cultivo.planta()));
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    if (!player.getMainHandItem().is(cultivo.semente())
                            || player.getMainHandItem().getCount() != 1
                            || !player.getOffhandItem().is(cultivo.semente())
                            || player.getOffhandItem().getCount() != 1
                            || player.getInventory().countItem(cultivo.semente()) != 2) {
                        throw new AssertionError(cultivo.id() + ": plantio perdeu ou duplicou sementes");
                    }
                    var estado = mc.overworld().getBlockState(solo.above());
                    if (!(cultivo.planta() instanceof CropBlock planta) || planta.getAge(estado) != 0
                            || (estado.hasProperty(UvCropBlock.UV_AGE) && estado.getValue(UvCropBlock.UV_AGE) != 0)) {
                        throw new AssertionError(cultivo.id() + ": plantio não começou como muda");
                    }
                });
                context.waitFor(client -> client.player.getMainHandItem().getCount() == 1);
                context.waitTicks(5);
                context.takeScreenshot("sementes-" + cultivo.id() + "-plantio");
            }
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(-2.5, 90, -4.5));
            context.waitTicks(5);
            mirar(context, new Vec3(0, 90.3, 0.5));
            context.takeScreenshot("sementes-cultivos-plantados-colecao");
        } finally {
            context.runOnClient(client -> {
                client.options.guiScale().set(escalaAnterior);
                client.options.fov().set(fovAnterior);
                client.options.bobView().set(balancoAnterior);
                client.options.setCameraType(cameraAnterior);
                client.resizeGui();
            });
        }
    }

    private static BlockPos solo(int indice) {
        return new BlockPos(indice * 2 - 5, 89, 0);
    }

    private static void conferirModeloCarregado(Minecraft client, Cultivo cultivo) {
        var modelo = new ItemStackRenderState();
        var stack = new ItemStack(cultivo.semente());
        client.getItemModelResolver().updateForLiving(modelo, stack, ItemDisplayContext.NONE, client.player);
        if (modelo.isEmpty()) throw new AssertionError(cultivo.id() + ": item sem modelo");
        var volume = modelo.getModelBoundingBox();
        if (volume.hasNaN() || volume.getXsize() < 0.30 || volume.getYsize() < 0.48 || volume.getZsize() < 0.25) {
            throw new AssertionError(cultivo.id() + ": saquinho carregado sem volume 3D: " + volume);
        }
        var material = modelo.pickParticleMaterial(RandomSource.create(0));
        if (material == null) throw new AssertionError(cultivo.id() + ": atlas não carregou");
        var atlas = material.sprite().contents();
        var esperado = Identifier.fromNamespaceAndPath("intoxicantes", "item/sementes/" + cultivo.id());
        if (!atlas.name().equals(esperado) || atlas.width() != 128 || atlas.height() != 128) {
            throw new AssertionError(cultivo.id() + ": atlas incorreto: " + atlas.name()
                    + " " + atlas.width() + "x" + atlas.height());
        }
        for (ItemDisplayContext perspectiva : new ItemDisplayContext[] { ItemDisplayContext.GUI,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                ItemDisplayContext.GROUND, ItemDisplayContext.FIXED }) {
            modelo.clear();
            client.getItemModelResolver().updateForLiving(modelo, stack, perspectiva, client.player);
            if (modelo.isEmpty() || modelo.getModelBoundingBox().hasNaN()) {
                throw new AssertionError(cultivo.id() + ": perspectiva inválida: " + perspectiva);
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
