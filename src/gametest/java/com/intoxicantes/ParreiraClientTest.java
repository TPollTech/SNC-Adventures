package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Plantio e colheita pelos controles reais, em um mundo temporário isolado. */
public class ParreiraClientTest implements FabricClientGameTest {
    private static final BlockPos RAIZ = new BlockPos(0, 90, 0);
    private static final BlockPos POSTE = RAIZ.east();
    private static final BlockPos COLHEITA_CACHO = POSTE.above(2);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        String idiomaAnterior = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        // Terminar a recarga no menu evita que o overlay de recursos interfira
        // na câmera e nos controles do plantio no mundo recém-aberto.
        alterarIdioma(context, "pt_br");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> I18n.get("guia.intoxicantes.cultivos.uva.titulo").equals("Parreira de uva"));
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                // Fixar a evolução aleatória apenas no cenário visual mantém
                // cada captura no estágio declarado. O servidor continua
                // executando o ticker, as interações e a sincronização reais.
                level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, mc);
                level.getGameRules().set(GameRules.SPAWN_MOBS, false, mc);
                level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, mc);
                for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = 90; y <= 96; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(RAIZ.below(), Blocks.DIRT.defaultBlockState());
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(0.5, 90, -2.5);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.SEMENTE_UVA, 2));
            });
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> client.player.getMainHandItem().is(IntoxicantesMod.SEMENTE_UVA));
            mirar(context, Vec3.atCenterOf(RAIZ.below()).add(0, 0.5, 0));
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                    && hit.getBlockPos().equals(RAIZ.below()) && hit.getDirection() == Direction.UP);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            server.waitFor(mc -> mc.overworld().getBlockState(RAIZ).is(IntoxicantesMod.UVA_PLANT));
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (player.getInventory().countItem(IntoxicantesMod.SEMENTE_UVA) != 1) {
                    throw new AssertionError("O plantio no chão não consumiu exatamente uma semente");
                }
                if (!(mc.overworld().getBlockEntity(RAIZ) instanceof ParreiraBlockEntity)) {
                    throw new AssertionError("A muda plantada não criou sua BlockEntity");
                }
                player.getInventory().clearContent();
            });
            context.waitTicks(25);
            context.takeScreenshot("parreira-0-muda-no-chao");

            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int y = 0; y < 3; y++) {
                    level.setBlockAndUpdate(POSTE.above(y), Blocks.OAK_FENCE.defaultBlockState());
                }
                // O poste fica no canto, como na prévia aprovada: a cobertura
                // avança para dentro do pergolado, em vez de envolver a muda.
                for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
                    level.setBlockAndUpdate(POSTE.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
                }
                var parreira = (ParreiraBlockEntity) level.getBlockEntity(RAIZ);
                // Preparar o tempo do cenário exercita o ticker e a descoberta
                // reais. Não altera o renderer nem suprime a espera do jogo.
                int faltam = parreira.ticksRebrotaRestantes();
                for (int tick = 0; tick < faltam; tick++) {
                    ParreiraBlockEntity.tick(level, RAIZ, level.getBlockState(RAIZ), parreira);
                }
            });
            world.getConnection().waitForChunksRender();
            for (int idade = 1; idade <= 4; idade++) {
                int etapa = idade;
                server.runOnServer(mc -> {
                    mc.overworld().setBlockAndUpdate(RAIZ, IntoxicantesMod.UVA_PLANT.defaultBlockState()
                            .setValue(UvCropBlock.AGE, etapa).setValue(UvCropBlock.UV_AGE, etapa == 4 ? 3 : 0));
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    // A distância desta vista evita pisotear a muda e permite
                    // comparar as etapas sobre o mesmo pergolado.
                    player.teleportTo(-3.5, 90, -3.5);
                });
                context.waitTicks(30);
                mirar(context, new Vec3(2.4, 91.6, 1.5));
                context.takeScreenshot("parreira-" + etapa + "-crescimento");
            }
            server.waitFor(mc -> {
                var be = mc.overworld().getBlockEntity(RAIZ);
                return be instanceof ParreiraBlockEntity parreira
                        && parreira.lerEstrutura().cobertura().size() == 25 && parreira.frutosProntos();
            });
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(2.5, 90, -4.5));
            context.waitTicks(10);
            mirar(context, new Vec3(2.4, 91.6, 1.5));
            context.takeScreenshot("parreira-madura-frente");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90, -1.5));
            context.waitTicks(10);
            mirar(context, new Vec3(2.5, 92.4, 1.5));
            context.takeScreenshot("parreira-madura-sob-cobertura");

            // Remover só a cobertura mantém a planta antiga madura, mas deixa
            // de haver armação produtiva: cachos colhíveis não podem continuar
            // aparecendo em ramos que perderam o suporte horizontal.
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
                    if (x != 0 || z != 0) level.destroyBlock(POSTE.above(2).offset(x, 0, z), false);
                }
                var parreira = (ParreiraBlockEntity) level.getBlockEntity(RAIZ);
                if (parreira.lerEstrutura().produtiva() || parreira.lerEstrutura().cobertura().size() != 1
                        || level.getBlockState(RAIZ).getValue(UvCropBlock.AGE) != 4
                        || level.getBlockState(RAIZ).getValue(UvCropBlock.UV_AGE) != 3) {
                    throw new AssertionError("Poda parcial não preservou a maturação ou manteve suporte produtivo");
                }
                mc.getPlayerList().getPlayers().getFirst().teleportTo(0.5, 90, -2.5);
            });
            context.waitTicks(25);
            mirar(context, new Vec3(0.5, 90.25, 0.5));
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(RAIZ));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            server.runOnServer(mc -> {
                if (mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.UVA) != 0
                        || mc.overworld().getBlockState(RAIZ).getValue(UvCropBlock.AGE) != 4) {
                    throw new AssertionError("Uma coluna sem cobertura permitiu colher ou apagou a maturação antiga");
                }
                mc.getPlayerList().getPlayers().getFirst().teleportTo(-2.5, 90, -2.5);
            });
            context.waitTicks(10);
            mirar(context, new Vec3(1.1, 91.5, 0.5));
            context.takeScreenshot("parreira-madura-sem-cobertura-sem-cachos");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
                    level.setBlockAndUpdate(POSTE.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
                }
            });
            server.waitFor(mc -> ((ParreiraBlockEntity) mc.overworld().getBlockEntity(RAIZ))
                    .lerEstrutura().cobertura().size() == 25);
            context.waitTicks(25);

            // Mirar diretamente no cacho usa a hitbox nativa da entidade.
            // O cliente envia o clique normal; não simulamos a colheita no servidor.
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(1.5, 90, -2.5);
                player.getInventory().clearContent();
            });
            context.waitFor(client -> client.player.getMainHandItem().isEmpty());
            context.waitTicks(10);
            mirar(context, centroCacho(COLHEITA_CACHO));
            context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof CachoParreiraEntity cacho
                    && cacho.apoio().equals(COLHEITA_CACHO));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.UVA) == 1);
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                var be = (ParreiraBlockEntity) level.getBlockEntity(RAIZ);
                if (player.getInventory().countItem(IntoxicantesMod.SEMENTE_UVA) != 0
                        || !level.getBlockState(RAIZ).is(IntoxicantesMod.UVA_PLANT)
                        || level.getBlockState(RAIZ).getValue(UvCropBlock.AGE) != 4
                        || be.frutosRestantes() != 24 || be.cachoPronto(COLHEITA_CACHO)) {
                    throw new AssertionError("Clique no cacho não entregou uma uva preservando os vinte e quatro vizinhos");
                }
            });
            context.waitTicks(8);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(8);
            server.runOnServer(mc -> {
                if (mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.UVA) != 1) {
                    throw new AssertionError("Repetir o clique no cacho removido duplicou a colheita");
                }
                mc.getPlayerList().getPlayers().getFirst().teleportTo(-3.5, 90, -3.5);
            });
            context.waitTicks(10);
            mirar(context, new Vec3(2.4, 91.6, 1.5));
            context.takeScreenshot("parreira-um-cacho-colhido-vizinhos-preservados");

            // A UVA já na mão não deve interromper uma sequência de colheita.
            BlockPos segundoCacho = COLHEITA_CACHO.east();
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(1.5, 90, -2.5));
            context.waitTicks(10);
            mirar(context, centroCacho(segundoCacho));
            context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof CachoParreiraEntity cacho
                    && cacho.apoio().equals(segundoCacho));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.UVA) == 2);
            server.runOnServer(mc -> {
                var be = (ParreiraBlockEntity) mc.overworld().getBlockEntity(RAIZ);
                if (be.frutosRestantes() != 23 || be.estadoCacho(COLHEITA_CACHO).ticksRebrota() <= 0
                        || be.estadoCacho(segundoCacho).ticksRebrota() <= 0) {
                    throw new AssertionError("Colher outro cacho reiniciou os vizinhos ou não preservou a recuperação individual");
                }
                mc.getPlayerList().getPlayers().getFirst().teleportTo(-3.5, 90, -3.5);
            });
            context.waitTicks(10);
            mirar(context, new Vec3(2.4, 91.6, 1.5));
            context.takeScreenshot("parreira-dois-cachos-colhidos-folhagem-preservada");

            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(.5, 90, -2.5));
            context.waitTicks(10);
            mirar(context, new Vec3(.5, 90.25, .5));
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(RAIZ));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(8);
            server.runOnServer(mc -> {
                if (mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.UVA) != 2) {
                    throw new AssertionError("A raiz colheu cachos de outros lugares");
                }
                var level = mc.overworld();
                level.destroyBlock(POSTE, false);
                mc.getPlayerList().getPlayers().getFirst().teleportTo(-3.5, 90, -3.5);
            });
            server.waitFor(mc -> {
                var be = mc.overworld().getBlockEntity(RAIZ);
                return be instanceof ParreiraBlockEntity parreira && parreira.lerEstrutura().apoios().isEmpty();
            });
            context.waitTicks(25);
            mirar(context, new Vec3(2.4, 91.6, 1.5));
            context.takeScreenshot("parreira-poda-apos-quebrar-apoio");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                if (!level.getBlockState(RAIZ).is(IntoxicantesMod.UVA_PLANT)
                        || !level.getBlockState(POSTE.above(2).south()).is(Blocks.OAK_FENCE)) {
                    throw new AssertionError("Poda apagou a raiz ou cercas restantes do pergolado");
                }
            });

            // O guia é aberto pelo livro na mão, e a página é alcançada pelo
            // sumário real: a captura não fabrica outra interface para o teste.
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.GUIA_SNC));
            });
            context.waitFor(client -> client.player.getMainHandItem().is(IntoxicantesMod.GUIA_SNC));
            context.getInput().lookAt(0, -65);
            context.waitTicks(5);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(GuiaScreen.class);
            context.waitTicks(5);
            clicarNoGuia(context, 120, 164); // Botão "abrir" da capa.
            context.waitTicks(5);
            int categoriaCultivos = context.computeOnClient(client -> {
                var categorias = GuiaConteudo.categorias();
                for (int i = 0; i < categorias.size(); i++) {
                    if (categorias.get(i).id.equals("cultivos")) return i;
                }
                throw new AssertionError("O guia não apresenta a categoria Cultivos");
            });
            clicarNoGuia(context, 50, 34 + categoriaCultivos * 16);
            context.waitTicks(5);
            int entradaUva = context.computeOnClient(client -> {
                var categoria = GuiaConteudo.categorias().get(categoriaCultivos);
                for (int i = 0; i < categoria.entradas.size(); i++) {
                    if (categoria.entradas.get(i).id.equals("cultivos.uva")) return i;
                }
                throw new AssertionError("O guia não apresenta a entrada Parreira de uva");
            });
            clicarNoGuia(context, 50, 52 + entradaUva * 30);
            context.waitTicks(5);
            context.runOnClient(client -> validarPaginaParreira((GuiaScreen) client.gui.screen()));
            context.takeScreenshot("parreira-guia-plantio-e-armacao");
            for (int trecho = 1; trecho <= 3; trecho++) {
                for (int i = 0; i < 15; i++) context.getInput().scroll(-1);
                context.waitTicks(3);
                context.takeScreenshot("parreira-guia-conteudo-" + trecho);
            }
            // ESC volta entrada -> categoria -> índice -> capa -> mundo.
            for (int nivel = 0; nivel < 4; nivel++) {
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitTicks(2);
            }
            context.waitFor(client -> client.gui.screen() == null);
        } finally {
            alterarIdioma(context, idiomaAnterior);
        }
    }

    /** A recarga segue assíncrona; a thread de renderização nunca espera seu future. */
    private static void alterarIdioma(ClientGameTestContext context, String idioma) {
        if (context.computeOnClient(client -> client.getLanguageManager().getSelected().equals(idioma))) {
            return;
        }
        var recarga = context.computeOnClient(client -> {
            client.getLanguageManager().setSelected(idioma);
            client.options.languageCode = idioma;
            return client.reloadResourcePacks();
        });
        // O atlas HD completo demora mais que o timeout padrão de interação.
        context.waitFor(client -> recarga.isDone(), 1200);
        // getNow somente após isDone propaga falhas de recarga, sem bloquear.
        recarga.getNow(null);
        context.waitFor(client -> client.gui.overlay() == null);
        context.waitFor(client -> client.getLanguageManager().getSelected().equals(idioma));
    }

    /** Inspeção de leitura após os cliques, sem forçar a navegação da tela. */
    private static void validarPaginaParreira(GuiaScreen tela) {
        try {
            var modo = GuiaScreen.class.getDeclaredField("modo");
            modo.setAccessible(true);
            var entrada = GuiaScreen.class.getDeclaredMethod("entrada");
            entrada.setAccessible(true);
            if (!modo.get(tela).toString().equals("ENTRADA")
                    || !((GuiaConteudo.GuiaEntrada) entrada.invoke(tela)).id.equals("cultivos.uva")) {
                throw new AssertionError("Os cliques do sumário não abriram a página real Parreira de uva");
            }
        } catch (ReflectiveOperationException erro) {
            throw new AssertionError("Não foi possível verificar a página alcançada pelo jogador", erro);
        }
    }

    private static void clicarNoGuia(ClientGameTestContext context, int x, int y) {
        double[] posicao = context.computeOnClient(client -> {
            var tela = client.gui.screen();
            if (!(tela instanceof GuiaScreen)) throw new AssertionError("O livro fechou durante a navegação");
            int esquerda = (tela.width - 240) / 2;
            int topo = (tela.height - Math.min(230, tela.height - 10)) / 2;
            return new double[] {
                    (esquerda + x) * (double) client.getWindow().getWidth() / tela.width,
                    (topo + y) * (double) client.getWindow().getHeight() / tela.height };
        });
        context.getInput().setCursorPos(posicao[0], posicao[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static Vec3 centroCacho(BlockPos apoio) {
        return new Vec3(apoio.getX() + .73, apoio.getY() + .55, apoio.getZ() + .27);
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
