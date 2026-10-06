package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * v1.2.75 — A COMPRA DE TAPA NO MINECRAFT DE VERDADE (client gametest):
 * gôndola + caixa + Gago num mundo real. O TAPA com o botão ESQUERDO na
 * prateleira anota no carrinho (e o segundo tapa SOMA de novo), o VISOR da
 * registradora mostra o total na hora (R$ 5 → R$ 10 → R$ 0 após pagar), o
 * caixa abre a PrateleiraCompraScreen e o CONFIRMAR paga de verdade:
 * carteira debita, itens entram no inventário e o estoque da gôndola baixa.
 */
public class CarrinhoClientTest implements FabricClientGameTest {
    private static final BlockPos GONDOLA = new BlockPos(0, 90, 2);
    private static final BlockPos CAIXA = new BlockPos(0, 90, 5);
    private static final net.minecraft.world.phys.Vec3 MIRA_GONDOLA =
            new net.minecraft.world.phys.Vec3(0.5, 90.2, 2.1);
    private static final net.minecraft.world.phys.Vec3 MIRA_CAIXA =
            new net.minecraft.world.phys.Vec3(0.5, 90.4, 5.2);
    /** A etiqueta que a mira pega: coluna do MEIO (1), fileira de baixo (0). */
    private static final int SLOT_ALVO = 1;
    /** Preço cravado no SLOT_ALVO: um tapa = uma dose = R$ 5 na registradora. */
    private static final int PRECO_DOSE = 5;
    private static final int ESTOQUE = 8;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                // salão de teste: piso na 89, ar até a 94
                for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.STONE.defaultBlockState());
                    for (int y = 90; y < 95; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                // a GÔNDOLA em (0,90,2) olhando pro jogador (facing=north)
                level.setBlockAndUpdate(GONDOLA,
                        IntoxicantesMod.PRATELEIRA_MERCADO.defaultBlockState());
                var be = (PrateleiraMercadoBlockEntity) level.getBlockEntity(GONDOLA);
                be.testeForcarEntrega(level, CalendarioEsquinao.DIA_ENTREGA, 4);
                // a etiqueta do SLOT_ALVO (a que a mira pega) é cravada no
                // teste: PÃO com R$ 5 a dose e a DOSE do catálogo — assim a
                // proposta não depende do dia do calendário comercial
                be.setItem(SLOT_ALVO, new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.BREAD, ESTOQUE));
                be.definirPreco(SLOT_ALVO, PRECO_DOSE);
                be.sincronizarVenda();
                // o CAIXA em (0,90,5): frente pro norte (pro freguês); o posto
                // de TRÁS (0,90,6) recebe o Gago (CaixaMercadoBlock.vincularAtendente
                // cola o NPC mais próximo e o MarketSystem não nasce no mundo de teste)
                var caixa = IntoxicantesMod.CAIXA_MERCADO.defaultBlockState()
                        .setValue(CaixaMercadoBlock.FACING, Direction.NORTH)
                        .setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.LOWER);
                level.setBlockAndUpdate(CAIXA, caixa);
                level.setBlockAndUpdate(CAIXA.above(),
                        caixa.setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.UPPER));
                var gago = IntoxicantesMod.GAGO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                gago.absSnapTo(0.5, 90.0, 6.5, Direction.NORTH.toYRot(), 0F);
                gago.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(0, 90, 6)),
                        net.minecraft.world.entity.EntitySpawnReason.EVENT, null);
                level.addFreshEntity(gago);
                // o freguês com carteira cheia, a 2 blocos da gôndola
                player.getInventory().clearContent();
                player.teleportTo(0.5, 90.0, 0.0);
                PlayerMoney.set(player, 500);
            });
            context.waitTicks(50); // o ticker do caixa (40t) ancora o Gago no posto

            // a dose do SLOT_ALVO (1 = uma unidade, 4 = sementes): tudo abaixo
            // (visor, proposta, entrega, estoque) é derivado dela
            int[] ficha = server.computeOnServer(mc -> {
                var be = (PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(GONDOLA);
                return new int[] {be.doseDoSlot(SLOT_ALVO), be.precoDoSlot(SLOT_ALVO)};
            });
            int dose = ficha[0];
            if (dose <= 0 || ficha[1] != PRECO_DOSE) {
                throw new AssertionError("A etiqueta do slot " + SLOT_ALVO + " não está à venda: "
                        + "preço=" + ficha[1] + " dose=" + dose);
            }

            // ======== O TAPA ESQUERDO anota 1 dose e o VISOR mostra R$ 5 ========
            mirar(context, MIRA_GONDOLA);
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(GONDOLA));
            int mirado = context.computeOnClient(client -> client.hitResult
                    instanceof net.minecraft.world.phys.BlockHitResult hit
                    ? PrateleiraMercadoBlock.slotDaMira(hit, client.level.getBlockState(GONDOLA)) : -1);
            if (mirado != SLOT_ALVO) {
                throw new AssertionError("A mira caiu no slot " + mirado + ", o teste exige o " + SLOT_ALVO);
            }
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitFor(client -> "R$ 5".equals(CaixaMercadoRenderer.textoDoVisorDeTeste()));
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (PrateleiraNetworking.unidadesEmCarrinhoDeTeste(player) < 1) {
                    throw new AssertionError("O tapa esquerdo na gôndola não anotou nada no carrinho");
                }
            });
            context.takeScreenshot("carrinho-visor-5");

            // ======== O SEGUNDO TAPA soma de novo: o visor vai pra R$ 10 (2 doses) ========
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitFor(client -> "R$ 10".equals(CaixaMercadoRenderer.textoDoVisorDeTeste()));
            context.waitTicks(5);
            context.takeScreenshot("carrinho-visor-10");

            // ======== O CAIXA abre a tela do carrinho (clique direito) ========
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90.0, 3.2));
            context.waitTicks(10);
            mirar(context, MIRA_CAIXA);
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(CAIXA));
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(PrateleiraCompraScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("carrinho-aberto");

            // ======== O CONFIRMAR PAGA: carteira 500−2×R$5, 2 doses no inventário ========
            int[] confirmar = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
                    throw new AssertionError("A tela do carrinho não está aberta");
                }
                if (tela.proposta().total() != 2 * PRECO_DOSE
                        || tela.proposta().quantidades().getFirst() != 2 * dose) {
                    throw new AssertionError("A proposta não bate com o que o visor mostrou");
                }
                return tela.botaoConfirmarParaTeste();
            });
            clicar(context, confirmar[0], confirmar[1]);
            context.waitFor(client -> client.gui.screen() == null);
            context.waitTicks(10);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var be = (PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(GONDOLA);
                if (PlayerMoney.get(player) != 490) {
                    throw new AssertionError("O confirmar não debitou R$10: carteira=" + PlayerMoney.get(player));
                }
                if (player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) != 2 * dose) {
                    throw new AssertionError("O confirmar não entregou as 2 doses: pão="
                            + player.getInventory().countItem(net.minecraft.world.item.Items.BREAD));
                }
                if (be.getItem(SLOT_ALVO).getCount() != ESTOQUE - 2 * dose) {
                    throw new AssertionError("A gôndola não baixou o estoque: pão="
                            + be.getItem(SLOT_ALVO).getCount());
                }
                if (PrateleiraNetworking.unidadesEmCarrinhoDeTeste(player) != 0) {
                    throw new AssertionError("O carrinho não zerou após pagar");
                }
            });
            // o visor ZERA depois do pagamento (o carrinho novo nasce vazio)
            context.waitFor(client -> "R$ 0".equals(CaixaMercadoRenderer.textoDoVisorDeTeste()));
            context.takeScreenshot("carrinho-pago");

            // ======== +/− na tela: um tapa, abre o caixa, + recota 1→2, − devolve 2→1 ========
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90.0, 0.0));
            context.waitTicks(10);
            mirar(context, MIRA_GONDOLA);
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(GONDOLA));
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitFor(client -> "R$ 5".equals(CaixaMercadoRenderer.textoDoVisorDeTeste()));
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90.0, 3.2));
            context.waitTicks(10);
            mirar(context, MIRA_CAIXA);
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getBlockPos().equals(CAIXA));
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(PrateleiraCompraScreen.class);
            esperarRecotacao(context);
            int[] mais = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
                    throw new AssertionError("A tela do carrinho não está aberta pro +");
                }
                int[] r = tela.botaoMaisParaTeste(0);
                return new int[] { r[0] + r[2] / 2, r[1] + r[3] / 2 };
            });
            clicar(context, mais[0], mais[1]);
            esperarRecotacao(context);
            int quantidadeAposMais = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
                    throw new AssertionError("A tela fechou após o +");
                }
                return tela.proposta().quantidades().getFirst();
            });
            if (quantidadeAposMais != 2 * dose) {
                throw new AssertionError("O botão + não recotou no servidor: quantidade=" + quantidadeAposMais);
            }
            int[] menos = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
                    throw new AssertionError("A tela não está aberta pro −");
                }
                int[] r = tela.botaoMenosParaTeste(0);
                return new int[] { r[0] + r[2] / 2, r[1] + r[3] / 2 };
            });
            clicar(context, menos[0], menos[1]);
            esperarRecotacao(context);
            int quantidadeFinal = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
                    throw new AssertionError("A tela fechou após o −");
                }
                return tela.proposta().quantidades().getFirst();
            });
            if (quantidadeFinal != dose) {
                throw new AssertionError("O botão − não devolveu à dose: quantidade=" + quantidadeFinal);
            }

            // ======== Ajustar NÃO paga: a carteira segue 490 ========
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (PlayerMoney.get(player) != 490) {
                    throw new AssertionError("Ajustes de carrinho não podem debitar a carteira");
                }
                player.teleportTo(0.5, 90.0, 0.0);
            });
            context.takeScreenshot("carrinho-ajustado");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);
        }
    }

    /** Clica com o mouse real na coordenada da GUI (converte pra pixels da janela). */
    private static void clicar(ClientGameTestContext context, int x, int y) {
        mover(context, x, y);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    /** A recotação é um vai-e-vem de rede: espera a tela destravar (payload novo). */
    private static void esperarRecotacao(ClientGameTestContext context) {
        context.waitFor(client -> !(client.gui.screen() instanceof PrateleiraCompraScreen tela)
                || !tela.aguardandoRecotacao());
        context.waitTicks(5);
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
