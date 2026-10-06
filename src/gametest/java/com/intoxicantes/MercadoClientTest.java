package com.intoxicantes;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * v1.2.76 — O MERCADO NO MINECRAFT DE VERDADE (client gametest): o template
 * REAL (structure/mercado_gago.nbt) é colocado no mundo de teste e o salão
 * é conferido bloco a bloco: só a gôndola que vende no mobiliário (nenhum
 * quartzo/slab de "prateleira de imitação"), as DUAS ilhas de 3 colunas com
 * respiro entre fileiras, nascidas ABASTECIDAS (18 slots por face), o CAIXA
 * no meio do balcão com o Gago de
 * plantão atrás — e o NOME do dono da esquina na cabeça dele.
 */
public class MercadoClientTest implements FabricClientGameTest {
    /** O chão do mundo de teste (o jogador nasce em -60). */
    private static final int CHAO = -60;
    /** O template é colocado com o y0 (lote/piso) em CHAO+1. */
    private static final int Y0 = CHAO + 1;
    /** As duas ilhas: 3 colunas por ilha, com corredores laterais de 1 bloco. */
    private static final int[][] ILHAS = {{6, 8, 10}, {16, 18, 20}};
    /** Fileiras separadas por corredor interno de 2 blocos (z7/z8). */
    private static final int[] FILEIRAS = {6, 9};
    /** O balcão (slab) e o CAIXA com o Gago atrás. */
    private static final int BALCAO_X = 10;
    private static final int BALCAO_Z = 12;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();

            // ======== COLOCA O TEMPLATE REAL DO MERCADO =====
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int x = -3; x <= 30; x++) for (int z = -3; z <= 30; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, CHAO, z),
                            Blocks.STONE.defaultBlockState());
                    for (int y = CHAO + 1; y <= Y0 + 9; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z),
                                Blocks.AIR.defaultBlockState());
                    }
                }
                var template = mc.getStructureTemplateManager().getOrCreate(
                        Identifier.fromNamespaceAndPath("intoxicantes", "mercado_gago"));
                template.placeInWorld(level, new BlockPos(0, Y0, 0), BlockPos.ZERO,
                        new StructurePlaceSettings()
                                .setRotation(Rotation.NONE)
                                .setMirror(Mirror.NONE)
                                .setIgnoreEntities(false)
                                .setFinalizeEntities(true),
                        level.getRandom(), 2);
            });
            context.waitTicks(20);

            // ======== O SALÃO: SÓ a gôndola que vende no mobiliário =====
            server.runOnServer(mc -> {
                var level = mc.overworld();
                int gondolas = 0;
                for (int[] ilha : ILHAS) {
                    for (int x : ilha) for (int z : FILEIRAS) for (int y = 1; y <= 2; y++) {
                        var pos = new BlockPos(x, Y0 + y, z);
                        if (!(level.getBlockState(pos).getBlock() instanceof PrateleiraMercadoBlock)) {
                            throw new AssertionError("Falta a gôndola em " + pos.toShortString()
                                    + " (veio " + level.getBlockState(pos).getBlock() + ")");
                        }
                        gondolas++;
                    }
                }
                if (gondolas != 24) {
                    throw new AssertionError("Deveriam ser 24 blocos de gôndola (3+3 colunas x "
                            + "2 fileiras separadas x 2 alturas), veio " + gondolas);
                }
                // As duas faces de cada bloco nascem com os 18 slots.
                for (int[] ilha : ILHAS) {
                    for (int x : ilha) for (int z : FILEIRAS) {
                        if (!(level.getBlockEntity(new BlockPos(x, Y0 + 1, z))
                                instanceof PrateleiraMercadoBlockEntity be)) {
                            throw new AssertionError("Gôndola sem BE em " + x + "," + z);
                        }
                        int esperado = PrateleiraMercadoBlockEntity.TOTAL;
                        int cheios = 0;
                        for (int s = 0; s < esperado; s++) if (!be.getItem(s).isEmpty()) cheios++;
                        if (cheios != esperado) {
                            throw new AssertionError("Gôndola (" + x + "," + z + ") nasceu com "
                                    + cheios + "/" + esperado + " slots — o salão tem que nascer "
                                    + "ABASTECIDO (entrega na quarta)");
                        }
                    }
                }
                // NENHUMA prateleira de imitação: quartzo/slab só no balcão
                for (int x = 3; x <= 23; x++) for (int z = 1; z <= 16; z++) {
                    for (int y = 1; y <= 2; y++) {
                        if (z == 12 && x >= 8 && x <= 12) continue; // o balcão
                        var estado = level.getBlockState(new BlockPos(x, Y0 + y, z));
                        var bloco = estado.getBlock();
                        if (bloco == Blocks.SMOOTH_QUARTZ || bloco == Blocks.SMOOTH_STONE_SLAB) {
                            throw new AssertionError("Sobrou " + bloco + " fingindo prateleira em ("
                                    + x + "," + y + "," + z + ") — o salão é gôndola + balcão");
                        }
                    }
                }
                // O CAIXA no meio do balcão, com o POSTO do Gago livre atrás
                for (int y = 1; y <= 2; y++) {
                    if (!(level.getBlockState(new BlockPos(BALCAO_X, Y0 + y, BALCAO_Z))
                            .getBlock() instanceof CaixaMercadoBlock)) {
                        throw new AssertionError("O CAIXA sumiu do balcão (y" + y + ")");
                    }
                }
                if (!level.getBlockState(new BlockPos(BALCAO_X, Y0 + 1, BALCAO_Z - 1)).isAir()) {
                    throw new AssertionError("O POSTO do Gago precisa estar livre (atrás do caixa)");
                }
            });

            // ======== O GAGO NO BALCÃO, COM O NOME NA CABEÇA =====
            // O mercado é registrado (o centro do template) e o GERENCIADOR roda:
            // é ele que planta o dono no balcão e trava o plantão.
            server.runOnServer(mc -> MarketSystem.setMarketPos(
                    new BlockPos(13, Y0, 13)));
            try {
                context.waitFor(client -> client.level != null
                        && !client.level.getEntitiesOfClass(GagoEntity.class,
                                new net.minecraft.world.phys.AABB(
                                        new Vec3(BALCAO_X, Y0, BALCAO_Z - 3),
                                        new Vec3(BALCAO_X + 1, Y0 + 4, BALCAO_Z + 4))).isEmpty());
                server.runOnServer(mc -> {
                    var level = mc.overworld();
                    MarketSystem.gerenciarGago(level, new BlockPos(13, Y0, 13));
                    var caixa = new BlockPos(BALCAO_X, Y0 + 1, BALCAO_Z);
                    var atendente = CaixaMercadoBlock.atendente(level, caixa);
                    if (atendente == null) {
                        throw new AssertionError("O Gago não está ATENDENDO no balcão (posto "
                                + caixa.relative(Direction.NORTH).toShortString() + ")");
                    }
                    if (!atendente.hasCustomName() || !atendente.isCustomNameVisible()
                            || atendente.getCustomName() == null) {
                        throw new AssertionError("O Gago de plantão precisa do NOME na cabeça "
                                + "(como o nome dos players)");
                    }
                    if (!atendente.isNoAi() || !atendente.estaDePlantao()) {
                        throw new AssertionError("De plantão no balcão ele é estatueta (NoAI) "
                                + "ancorado no posto — o cliente serve SEM ele passear");
                    }
                });
            } finally {
                server.runOnServer(mc -> MarketSystem.setMarketPos(null));
            }

            // ======== AS FOTOS DO SALÃO NOVO =====
            // 1) visão geral do salão, do canto de dentro (as duas ilhas, o
            //    balcão com o CAIXA e o Gago de plantão, as geladeiras no fundo)
            irPara(context, server, new Vec3(21.5, Y0 + 3, 15.5), new Vec3(9.5, Y0 + 1, 5.5));
            context.waitTicks(10);
            context.takeScreenshot("mercado-salao-geral");
            // 2) o corredor central, com as duas ilhas nos dois lados
            irPara(context, server, new Vec3(12.5, Y0 + 2, 9.5), new Vec3(12.5, Y0 + 2, 1.5));
            context.waitTicks(10);
            context.takeScreenshot("mercado-ilhas-corredor-central");
            // 3) a gôndola de perto, pelo corredor sul: etiqueta e produtos
            irPara(context, server, new Vec3(8.5, Y0 + 2, 10.5), new Vec3(8.5, Y0 + 1.5, 9.5));
            context.waitTicks(10);
            context.takeScreenshot("mercado-gondola-de-perto");
            // 4) O BALCÃO: o Gago de plantão com o nome na cabeça + o CAIXA
            irPara(context, server, new Vec3(12.5, Y0 + 2, 15.5), new Vec3(10.5, Y0 + 2.2, 11.5));
            context.waitTicks(10);
            context.takeScreenshot("mercado-balcao-gago-com-nome");
        }
    }

    /** Teleporta o freguês e mira no alvo (mesma conta de yaw/pitch do jogador). */
    private static void irPara(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
            Vec3 caminha, Vec3 alvo) {
        server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst()
                .teleportTo(caminha.x, caminha.y, caminha.z));
        context.waitFor(client -> client.player.distanceToSqr(caminha) < .05);
        float[] angulos = context.computeOnClient(client -> {
            Vec3 delta = alvo.subtract(client.player.getEyePosition());
            return new float[] {
                    (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))) };
        });
        context.getInput().lookAt(angulos[0], angulos[1]);
        context.waitTicks(3);
    }
}
