package com.intoxicantes;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class CommerceClientTest implements FabricClientGameTest {
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
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                    for (int y = 90; y < 94; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                player.teleportTo(0.5, 90, -1.5);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 64));
                player.getInventory().setItem(1, new ItemStack(IntoxicantesMod.LOUPULO_FRESCO, 32));
                player.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 8));
                player.getInventory().setItem(3, new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 4));
                PlayerMoney.set(player, 500);
                GagoEntity npc = IntoxicantesMod.GAGO.create(level, EntitySpawnReason.COMMAND);
                if (npc == null) throw new AssertionError("Merchant did not spawn");
                npc.absSnapTo(0.5, 90, 1.5, 180, 0);
                npc.setNoAi(true);
                npc.setPersistenceRequired();
                npc.refreshTradeStock();
                level.addFreshEntity(npc);
            });
            context.waitTicks(20);
            context.getInput().lookAt(0, 0);
            context.waitTicks(5);
            context.takeScreenshot("commerce-before-interaction");
            context.runOnClient(client -> IntoxicantesMod.LOGGER.info("[Commerce test] player={} hit={} screen={}",
                    client.player.position(), client.hitResult, client.gui.screen()));
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    && hit.getEntity() instanceof GagoEntity);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            server.runOnServer(mc -> mc.overworld().getEntitiesOfClass(GagoEntity.class,
                    new net.minecraft.world.phys.AABB(-5, 89, -5, 5, 95, 5)).forEach(npc ->
                    IntoxicantesMod.LOGGER.info("[Commerce test] trading={} baby={} angry={}", npc.isTrading(), npc.isBaby(), npc.isPuto())));
            context.waitForScreen(EsquinaoCardapioScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("commerce-buy-desktop");
            click(context, 269, 109); // Buy one beer through the actual screen -> packet -> server.
            server.waitFor(mc -> PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) == 485);
            context.waitTicks(5);
            click(context, 150, 84); // Sell harvest tab (abas de 100px: 6..106 buy, 109..209 sell).
            context.takeScreenshot("commerce-harvest-desktop");
            for (int purchase = 1; purchase <= 12; purchase++) {
                click(context, 269, 109);
                int expected = 485 + purchase * 6;
                server.waitFor(mc -> PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) == expected);
                context.waitTicks(3);
            }
            context.takeScreenshot("commerce-harvest-sold-out");
            click(context, 269, 109); // Exhausted button must not pay again.
            context.waitTicks(5);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (PlayerMoney.get(player) != 557 || player.getInventory().countItem(IntoxicantesMod.LOUPULO_FRESCO) != 0) {
                    throw new AssertionError("Daily quota or exact harvest consumption failed");
                }
                if (player.getInventory().countItem(IntoxicantesMod.CERVEJA) != 1) throw new AssertionError("Beer not delivered");
            });
            context.getInput().resizeWindow(640, 480);
            context.waitTicks(5);
            context.takeScreenshot("commerce-harvest-small-window");
            move(context, 303, 99);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            move(context, 303, 191);
            context.waitTicks(2);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.takeScreenshot("ingredients-scroll-bottom");
            click(context, 269, 173); // Last entry: eight bottles, four reais.
            server.waitFor(mc -> PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) == 561);
            context.waitTicks(3);
            move(context, 120, 110);
            context.getInput().scroll(1); // Honey now in the third row.
            click(context, 79, 84);
            click(context, 150, 84); // Selling tab preserves its scroll position.
            click(context, 269, 173);
            server.waitFor(mc -> PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) == 569);
            context.waitTicks(3);
            context.takeScreenshot("ingredients-honey-sold");
            click(context, 79, 84);
            context.waitTicks(3);
            context.takeScreenshot("commerce-buy-small-window");
            context.getInput().pressKey(InputConstants.KEY_E); // Escape closes and releases the NPC.
            server.waitFor(mc -> mc.overworld().getEntitiesOfClass(GagoEntity.class,
                    new net.minecraft.world.phys.AABB(-5, 89, -5, 5, 95, 5)).stream().noneMatch(GagoEntity::isTrading));
        }
    }

    private static void click(ClientGameTestContext context, int x, int y) {
        move(context, x, y);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static void move(ClientGameTestContext context, int x, int y) {
        double[] position = context.computeOnClient(client -> {
            var screen = client.gui.screen();
            if (!(screen instanceof EsquinaoCardapioScreen)) throw new AssertionError("Market screen closed unexpectedly");
            int left = (screen.width - 310) / 2;
            int top = (screen.height - Math.min(380, screen.height - 12)) / 2;
            return new double[] { (left + x) * (double) client.getWindow().getWidth() / screen.width,
                    (top + y) * (double) client.getWindow().getHeight() / screen.height };
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }
}
