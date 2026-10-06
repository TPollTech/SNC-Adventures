package com.intoxicantes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** As 46 artes aprovadas no cliente real: recursos ativos, perspectivas e objetos no mundo. */
public class IdentidadeClientTest implements FabricClientGameTest {
    private static final String[] IDS = {
            "uva", "lampada_uv", "poste_luz", "coco", "guia_snc", "central_comando",
            "camisa_matanza", "ovo_gago", "ovo_traficante", "ovo_juca", "cafe_verde",
            "lupulo", "cana_de_acucar", "cevada", "malte", "bagaco_de_cana", "maconha_seda",
            "baseado", "cigarro_camel", "cocaina", "opio", "heroina", "lsd", "po_estelar",
            "cogumelo_xamanico", "nevoa_do_deserto", "raiz_de_sombra", "cristal_de_euforia",
            "extrato_cafeina", "porta_grade", "prateleira_mercado", "lampada_led",
            "lampada_led_5w", "lampada_led_9w", "lampada_led_12w", "lampada_led_15w",
            "lampada_led_20w", "lampada_led_30w", "lampada_led_50w", "lampada_led_100w",
            "lampada_led_150w", "lampada_led_200w", "cabo_cobre_2_5mm", "soquete_teto",
            "interruptor_simples", "quadro_eletrico"
    };
    private static final String[] DESTAQUES = {
            "uva", "coco", "guia_snc", "central_comando", "poste_luz",
            "lampada_led_20w", "lampada_led_50w", "lampada_led_200w"
    };
    private static final BlockPos RAIZ = new BlockPos(-7, 90, 2);
    private static final BlockPos MESA_ALIMENTOS = new BlockPos(0, 90, 6);
    private static final AABB AREA_ALIMENTOS = new AABB(-1, 90, 5, 2, 93, 8);
    private static final Direction[] FACES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final BlockPos[] PRATELEIRAS = {new BlockPos(-9, 90, -5), new BlockPos(-3, 90, -5),
            new BlockPos(3, 90, -5), new BlockPos(9, 90, -5)};
    private record Cena(String nome, Vec3 camera, Vec3 alvo) {}
    private static final Cena[] CENAS = {
            new Cena("uva-cachos-no-mundo", new Vec3(-7.5, 90, -1.5), new Vec3(-5.2, 91.5, 3.2)),
            new Cena("coco-pendurado", new Vec3(1.5, 90, -.5), new Vec3(1.5, 91.7, 3.5)),
            new Cena("uv-tubos", new Vec3(5.5, 90, 1.0), new Vec3(5.5, 91.55, 4.5)),
            new Cena("leds-bulbos", new Vec3(-3.5, 90, 3.5), new Vec3(-3.5, 91.5, 10.5)),
            new Cena("leds-refletores", new Vec3(3.5, 90, 6.5), new Vec3(3.5, 91.5, 10.5)),
            new Cena("leds-industriais", new Vec3(8.5, 90, 5), new Vec3(8.5, 91.6, 10.5)),
            new Cena("porta-prateleira-eletrica", new Vec3(13.5, 90, -3.5), new Vec3(13.5, 91.2, 3.5)),
            new Cena("poste-completo", new Vec3(15.5, 90, 2), new Vec3(15.5, 91.8, 7.5))
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        int escalaAnterior = context.computeOnClient(client -> client.options.guiScale().get());
        int fovAnterior = context.computeOnClient(client -> client.options.fov().get());
        boolean balancoAnterior = context.computeOnClient(client -> client.options.bobView().get());
        CameraType cameraAnterior = context.computeOnClient(client -> client.options.getCameraType());
        int[] tamanhoAnterior = context.computeOnClient(client -> new int[] {
                client.getWindow().getWidth(), client.getWindow().getHeight() });
        var packsAnteriores = context.computeOnClient(client -> List.copyOf(client.getResourcePackRepository().getSelectedIds()));
        var opcoesPacksAnteriores = context.computeOnClient(client -> List.copyOf(client.options.resourcePacks));
        String idiomaAnterior = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        String opcaoIdiomaAnterior = context.computeOnClient(client -> client.options.languageCode);
        configurar(context, 1280, 800);
        try (var world = context.worldBuilder().create()) {
            try {
            var server = world.getServer();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, mc);
                level.getGameRules().set(GameRules.SPAWN_MOBS, false, mc);
                level.getGameRules().set(GameRules.ADVANCE_TIME, false, mc);
                level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, mc);
                prepararMundo(level);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(.5, 90, -7.5);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
            });
            world.getConnection().waitForChunksRender();
            String packAtivo = ativarPack(context);
            context.runOnClient(client -> {
                Set<Identifier> materiais = new HashSet<>();
                for (String id : IDS) conferirItem(client, id, materiais);
                // Coco colocado é um ID técnico separado do fruto coletado.
                conferirBloco(client, IntoxicantesMod.COCO_BLOCO);
                conferirBloco(client, IntoxicantesMod.UVA_PLANT);
                conferirItem(client, "pao_cevada", materiais);
                var tronco = IntoxicantesMod.COQUEIRO_TRONCO.asItem();
                String chaveTronco = tronco.getDescriptionId();
                String nomeTronco = new ItemStack(tronco).getHoverName().getString();
                if (!"block.intoxicantes.coqueiro_tronco".equals(chaveTronco)
                        || !"Tronco de Coqueiro".equals(nomeTronco)) {
                    throw new AssertionError("Tooltip do tronco não resolveu no cliente: "
                            + chaveTronco + " → " + nomeTronco);
                }
                IntoxicantesMod.LOGGER.info("[Identidade] Tooltip do tronco resolvido: {}", nomeTronco);
                IntoxicantesMod.LOGGER.info("[Identidade]46IDs e{}materiais ativos carregados", materiais.size());
                IntoxicantesMod.LOGGER.info("[Identidade]Packs ativos:{}", client.options.resourcePacks);
            });
            boolean focoPrateleiras = Boolean.getBoolean("snc.prateleirasTeste");
            IntoxicantesMod.LOGGER.info("[Identidade] Capturas: {}",
                    focoPrateleiras ? "gôndolas/compras/alimentos/câmera/cardápio/recarga" : "suíte integral");
            if (!focoPrateleiras) {
            for (int[] tamanho : new int[][] { {1280, 800}, {640, 480} }) {
                configurar(context, tamanho[0], tamanho[1]);
                for (int pagina = 0; pagina < 2; pagina++) {
                    int inicio = pagina * 36;
                    server.runOnServer(mc -> {
                        var player = mc.getPlayerList().getPlayers().getFirst();
                        player.getInventory().clearContent();
                        player.getInventory().setSelectedSlot(0);
                        for (int i = inicio; i < Math.min(IDS.length, inicio + 36); i++) {
                            player.getInventory().setItem(i - inicio, new ItemStack(item(IDS[i])));
                        }
                    });
                    context.waitFor(client -> client.player.getMainHandItem().is(item(IDS[inicio])));
                    context.getInput().pressKey(options -> options.keyInventory);
                    context.waitForScreen(InventoryScreen.class);
                    context.getInput().setCursorPos(3, 3);
                    context.waitTicks(6);
                    context.runOnClient(client -> {
                        for (int i = inicio; i < Math.min(IDS.length, inicio + 36); i++) {
                            if (!client.player.getInventory().getItem(i - inicio).is(item(IDS[i]))) {
                                throw new AssertionError(IDS[i] + ": item não sincronizou para inventário");
                            }
                        }
                        if (client.getWindow().getGuiScaledWidth() < 176
                                || client.getWindow().getGuiScaledHeight() < 166) {
                            throw new AssertionError("Inventário não cabe na resolução " + tamanho[0] + "x" + tamanho[1]);
                        }
                    });
                    context.takeScreenshot("identidade-inventario-" + tamanho[0] + "x" + tamanho[1] + "-pagina-" + (pagina + 1));
                    context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                }
                String[] capturados = tamanho[0] == 1280 ? IDS : DESTAQUES;
                for (String id : capturados) {
                    server.runOnServer(mc -> {
                        var player = mc.getPlayerList().getPlayers().getFirst();
                        player.teleportTo(.5, 90, -7.5);
                        player.getInventory().clearContent();
                        player.getInventory().setSelectedSlot(0);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(id)));
                        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item(id)));
                    });
                    context.waitFor(client -> client.player.getMainHandItem().is(item(id))
                            && client.player.getOffhandItem().is(item(id)));
                    context.getInput().lookAt(0, -20);
                    context.waitTicks(8);
                    context.takeScreenshot("identidade-maos-" + tamanho[0] + "x" + tamanho[1] + "-" + id);
                    if (tamanho[0] == 640) {
                        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                        context.waitTicks(5);
                        context.takeScreenshot("identidade-terceira-640x480-" + id);
                        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                    }
                }
            }
            }
            configurar(context, 1280, 800);
            server.waitFor(mc -> mc.overworld().getBlockEntity(RAIZ) instanceof ParreiraBlockEntity be
                    && be.lerEstrutura().cobertura().size() == 9 && be.frutosProntos());
            if (!focoPrateleiras) {
            for (Cena cena : CENAS) {
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    player.getInventory().clearContent();
                    player.teleportTo(cena.camera().x, cena.camera().y, cena.camera().z);
                });
                context.waitFor(client -> client.player.distanceToSqr(cena.camera()) < .05
                        && client.player.getMainHandItem().isEmpty());
                mirar(context, cena.alvo());
                context.waitTicks(8);
                context.takeScreenshot("identidade-mundo-" + cena.nome());
            }
            }
            validarPrateleiras(context, server);
            validarCameraPrateleira(context);
            validarAlimentos(context, server);
            validarCardapio(context, server);
            // Recarrega com o mundo e a parreira ainda abertos. Nunca join/get
            // na thread de render: o contexto continua bombeando frames/ticks.
            Cena recargaCena = CENAS[0];
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(
                    recargaCena.camera().x, recargaCena.camera().y, recargaCena.camera().z));
            context.waitFor(client -> client.player.distanceToSqr(recargaCena.camera()) < .05);
            mirar(context, recargaCena.alvo());
            context.waitTicks(8);
            context.takeScreenshot("identidade-recarga-mundo-antes");
            var recarga = context.computeOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(client -> recarga.isDone(), 1200);
            recarga.getNow(null);
            context.waitFor(client -> client.gui.overlay() == null);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                conferirPack(client, packAtivo);
                Set<Identifier> materiais = new HashSet<>();
                for (String id : IDS) conferirItem(client, id, materiais);
                conferirItem(client, "pao_cevada", materiais);
                for (int i = 0; i < PRATELEIRAS.length; i++) conferirPrateleira(client, PRATELEIRAS[i], FACES[i]);
                if (!(client.level.getBlockEntity(RAIZ) instanceof ParreiraBlockEntity be)
                        || !be.frutosProntos() || be.lerEstrutura().cobertura().size() != 9) {
                    throw new AssertionError("Recarga perdeu a parreira/frutos ou sua estrutura no cliente");
                }
            });
            context.waitTicks(12);
            context.takeScreenshot("identidade-recarga-mundo-depois");
            } catch (Throwable falha) {
                // O teardown da suíte pode bloquear o servidor integrado. Registra
                // a causa original antes de o try-with-resources chamar close().
                IntoxicantesMod.LOGGER.error("[Identidade] Falha original antes do fechamento do mundo de teste", falha);
                throw falha;
            }
        } finally {
            context.getInput().resizeWindow(tamanhoAnterior[0], tamanhoAnterior[1]);
            context.runOnClient(client -> {
                client.options.guiScale().set(escalaAnterior);
                client.options.fov().set(fovAnterior);
                client.options.bobView().set(balancoAnterior);
                client.options.setCameraType(cameraAnterior);
                client.getResourcePackRepository().setSelected(packsAnteriores);
                client.options.resourcePacks.clear();
                client.options.resourcePacks.addAll(opcoesPacksAnteriores);
                client.getLanguageManager().setSelected(idiomaAnterior);
                client.options.languageCode = opcaoIdiomaAnterior;
                client.resizeGui();
            });
            var restaurarPacks = context.computeOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(client -> restaurarPacks.isDone(), 1200);
            restaurarPacks.getNow(null);
            context.waitFor(client -> client.gui.overlay() == null);
        }
    }

    private static void configurar(ClientGameTestContext context, int width, int height) {
        context.getInput().resizeWindow(width, height);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.fov().set(70);
            client.options.bobView().set(false);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.resizeGui();
        });
    }

    private static String ativarPack(ClientGameTestContext context) {
        String id = context.computeOnClient(client -> {
            var repo = client.getResourcePackRepository();
            repo.reload();
            String pack = repo.getAvailableIds().stream().filter(nome -> nome.equals("file/minhas-texturas")
                    || nome.equals("file/minhas-texturas.zip")).findFirst()
                    .orElseThrow(() -> new AssertionError("Copie o pacote aprovado minhas-texturas para resourcepacks do cliente de teste"));
            var selecionados = new ArrayList<>(repo.getSelectedIds());
            selecionados.remove(pack);
            selecionados.add(pack);
            repo.setSelected(selecionados);
            if (!client.options.resourcePacks.contains(pack)) client.options.resourcePacks.add(pack);
            var idiomas = client.getLanguageManager();
            if (!idiomas.getLanguages().containsKey("pt_br")) {
                throw new AssertionError("Idioma português brasileiro ausente no cliente de teste");
            }
            idiomas.setSelected("pt_br");
            client.options.languageCode = "pt_br";
            return pack;
        });
        var recarga = context.computeOnClient(Minecraft::reloadResourcePacks);
        context.waitFor(client -> recarga.isDone(), 1200);
        recarga.getNow(null);
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(client -> {
            conferirPack(client, id);
            if (!client.getLanguageManager().getSelected().equals("pt_br")
                    || !client.options.languageCode.equals("pt_br")) {
                throw new AssertionError("Português brasileiro não foi ativado durante a recarga de recursos");
            }
        });
        return id;
    }

    private static void conferirPack(Minecraft client, String id) {
        var recurso = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath("intoxicantes",
                "textures/block/poste_luz.png")).orElseThrow(() -> new AssertionError("Textura do poste ausente"));
        if (!client.getResourcePackRepository().getSelectedIds().contains(id)
                || !client.options.resourcePacks.contains(id) || !recurso.sourcePackId().equals(id)) {
            throw new AssertionError("Pack aprovado não está ativo ou não sobrescreve o poste: " + recurso.sourcePackId());
        }
        IntoxicantesMod.LOGGER.info("[Identidade] Pack aprovado ativo: {}", recurso.sourcePackId());
    }

    private static Object campo(Object objeto, String nome) {
        try {
            var field = objeto.getClass().getDeclaredField(nome);
            field.setAccessible(true);
            return field.get(objeto);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Estado real do renderer/tela não expõe " + nome, ex);
        }
    }

    private static double numero(Object objeto, String nome) {
        return ((Number) campo(objeto, nome)).doubleValue();
    }

    private static AABB girarCaixa(AABB caixa, Direction frente) {
        return switch (frente) {
            case EAST -> new AABB(1 - caixa.maxZ, caixa.minY, caixa.minX, 1 - caixa.minZ, caixa.maxY, caixa.maxX);
            case SOUTH -> new AABB(1 - caixa.maxX, caixa.minY, 1 - caixa.maxZ, 1 - caixa.minX, caixa.maxY, 1 - caixa.minZ);
            case WEST -> new AABB(caixa.minZ, caixa.minY, 1 - caixa.maxX, caixa.maxZ, caixa.maxY, 1 - caixa.minX);
            default -> caixa;
        };
    }

    /** Inspeciona o snapshot do renderer real: nove objetos, base apoiada e vão livre. */
    private static void conferirPrateleira(Minecraft client, BlockPos pos, Direction frente) {
        if (!(client.level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be)
                || be.getBlockState().getValue(PrateleiraMercadoBlock.FACING) != frente) {
            throw new AssertionError("Prateleira/rotação não sincronizou: " + pos);
        }
        Object registrado = client.getBlockEntityRenderDispatcher().getRenderer(be);
        if (!(registrado instanceof PrateleiraMercadoRenderer renderer)) {
            throw new AssertionError("Renderer real da prateleira ausente");
        }
        var estado = renderer.createRenderState();
        renderer.extractRenderState(be, estado, 0, Vec3.ZERO, null);
        var expositores = (Object[]) campo(estado, "expositores");
        var agenda = TradeCatalog.prateleira(0);
        for (int slot = 0; slot < 9; slot++) {
            var oferta = agenda.get(slot);
            if (!be.getItem(slot).is(oferta.item()) || be.getItem(slot).getCount()
                    != Math.min(oferta.stack().getMaxStackSize(), oferta.stock() * oferta.count())) {
                throw new AssertionError("Prateleira " + frente + " slot " + slot + ": estoque real incompleto");
            }
            Object expositor = expositores[slot];
            var modelo = (ItemStackRenderState) campo(expositor, "item");
            double escala = numero(expositor, "escala");
            var volume = modelo.getModelBoundingBox();
            if (modelo.isEmpty() || volume.hasNaN() || !Double.isFinite(escala) || escala <= 0) {
                throw new AssertionError("Prateleira " + frente + " slot " + slot + ": modelo/escala vazios");
            }
            conferirSprite(modelo.pickParticleMaterial(RandomSource.create(0)).sprite(), "prateleira/" + slot);
            var etiqueta = (net.minecraft.util.FormattedCharSequence) campo(expositor, "etiqueta");
            var texto = new StringBuilder();
            if (etiqueta == null) throw new AssertionError("Etiqueta ausente no slot " + slot);
            etiqueta.accept((indice, estilo, ponto) -> { texto.appendCodePoint(ponto); return true; });
            if (!texto.toString().equals("R$" + oferta.price() + " x" + oferta.count())) {
                throw new AssertionError("Etiqueta diverge da cobrança: " + frente + "/" + slot + " " + texto);
            }
            double x = numero(expositor, "x"), y = numero(expositor, "y"), z = numero(expositor, "z");
            var caixa = new AABB(x + escala * volume.minX, y + escala * volume.minY, z + escala * volume.minZ,
                    x + escala * volume.maxX, y + escala * volume.maxY, z + escala * volume.maxZ);
            double base = (PrateleiraMercadoBlockEntity.NIVEIS[slot / 3] + .01) / 16;
            if (Math.abs(caixa.minY - base) > .00001 || caixa.getXsize() > 3.8001 / 16
                    || caixa.getYsize() > 3.8001 / 16 || caixa.getZsize() > 7.0001 / 16
                    || caixa.minX < 1.0 / 16 || caixa.maxX > 15.0 / 16
                    || caixa.minZ < 0 || caixa.maxZ > 15.5 / 16) {
                throw new AssertionError("Produto fora do vão ou flutuando: " + frente + "/" + slot + " " + caixa);
            }
            var orientada = girarCaixa(caixa, frente);
            var colisao = be.getBlockState().getCollisionShape(client.level, pos);
            if (Shapes.joinIsNotEmpty(Shapes.create(orientada), colisao, BooleanOp.AND)) {
                throw new AssertionError("Produto atravessa madeira/colisão em " + frente + "/" + slot);
            }
        }
        if (Shapes.joinIsNotEmpty(be.getBlockState().getShape(client.level, pos), Shapes.block(), BooleanOp.NOT_SAME)) {
            throw new AssertionError("Seleção da gôndola não ocupa exatamente um bloco em " + frente);
        }
        BlockPos costas = pos.relative(frente.getOpposite());
        if (!(client.level.getBlockEntity(costas) instanceof PrateleiraMercadoBlockEntity par)
                || par.getBlockState().getValue(PrateleiraMercadoBlock.FACING) != frente.getOpposite()) {
            throw new AssertionError("Par costas com costas ausente na face " + frente);
        }
        // Os dois fundos estruturais alcançam a mesma divisa dos blocos adjacentes.
        var fundo = girarCaixa(new AABB(1.0 / 16, 1.0 / 16, 15.5 / 16, 15.0 / 16, 1, 1), frente)
                .move(pos.getX(), pos.getY(), pos.getZ());
        var fundoPar = girarCaixa(new AABB(1.0 / 16, 1.0 / 16, 15.5 / 16, 15.0 / 16, 1, 1), frente.getOpposite())
                .move(costas.getX(), costas.getY(), costas.getZ());
        if (Shapes.joinIsNotEmpty(Shapes.create(fundo.move(-pos.getX(), -pos.getY(), -pos.getZ())),
                be.getBlockState().getCollisionShape(client.level, pos), BooleanOp.ONLY_FIRST)
                || Shapes.joinIsNotEmpty(Shapes.create(fundoPar.move(-costas.getX(), -costas.getY(), -costas.getZ())),
                par.getBlockState().getCollisionShape(client.level, costas), BooleanOp.ONLY_FIRST)) {
            throw new AssertionError("Fundo estrutural não alcança a divisa em " + frente);
        }
        double encontro = switch (frente) {
            case EAST -> fundo.minX - fundoPar.maxX;
            case WEST -> fundoPar.minX - fundo.maxX;
            case SOUTH -> fundo.minZ - fundoPar.maxZ;
            default -> fundoPar.minZ - fundo.maxZ;
        };
        if (Math.abs(encontro) > .000001) throw new AssertionError("Vão entre as costas em " + frente + ": " + encontro);
    }

    private static Vec3 frenteDaPrateleira(BlockPos pos, Direction frente, int slot) {
        double across = PrateleiraMercadoBlockEntity.COLUNAS[slot % 3] / 16.0 - .5;
        var direita = frente.getClockWise();
        double forward = .5;
        return new Vec3(pos.getX() + .5 + direita.getStepX() * across + frente.getStepX() * forward,
                pos.getY() + (PrateleiraMercadoBlockEntity.NIVEIS[slot / 3] + .5) / 16,
                pos.getZ() + .5 + direita.getStepZ() * across + frente.getStepZ() * forward);
    }

    private static PrateleiraNetworking.AbrirCompraPayload propostaPrateleira(Minecraft client) {
        if (!(client.gui.screen() instanceof PrateleiraCompraScreen tela)) {
            throw new AssertionError("Confirmação da prateleira fechou inesperadamente");
        }
        return (PrateleiraNetworking.AbrirCompraPayload) campo(tela, "proposta");
    }

    /** O clique DIREITO no balcão (caixa) abre a tela do carrinho. */
    private static void abrirCaixaDaPrateleira(ClientGameTestContext context, BlockPos balcao) {
        mirar(context, Vec3.atLowerCornerOf(balcao).add(.5, 1.0, .5));
        // o balcão são DUAS metades: a mira pode cair na de baixo ou na de cima
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                && (hit.getBlockPos().equals(balcao) || hit.getBlockPos().equals(balcao.above())));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(PrateleiraCompraScreen.class);
    }

    private static void responderPrateleira(ClientGameTestContext context, boolean confirmar) {
        // o centro vem da PRÓPRIA tela (o painel mudou de altura na v1.2.75:
        // nada de offset chutado aqui, ou o clique erra o botão)
        double[] botao = context.computeOnClient(client -> {
            var tela = (PrateleiraCompraScreen) client.gui.screen();
            int[] centro = confirmar ? tela.botaoConfirmarParaTeste() : tela.botaoCancelarParaTeste();
            return new double[] {centro[0], centro[1]};
        });
        moverNaTela(context, botao[0], botao[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void conferirCompraIntacta(TestServerContext server, BlockPos pos, int slot,
            Item item, int[] antes) {
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            var be = (PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(pos);
            if (be.getItem(slot).getCount() != antes[0] || PlayerMoney.get(player) != antes[1]
                    || player.getInventory().countItem(item) != antes[2]) {
                throw new AssertionError("Abrir/cancelar a confirmação alterou carteira ou produtos");
            }
        });
    }

    private static void validarPrateleiras(ClientGameTestContext context, TestServerContext server) {
        for (int i = 0; i < PRATELEIRAS.length; i++) {
            BlockPos pos = PRATELEIRAS[i];
            Direction frente = FACES[i];
            Vec3 camera = new Vec3(pos.getX() + .5 + frente.getStepX() * 2,
                    90, pos.getZ() + .5 + frente.getStepZ() * 2);
            // v1.2.75: o gesto de compra é o TAPA na gôndola e a TELA abre no
            // CAIXA (direito) — cada balcão fica ao lado do freguês, com o
            // Gago no posto, exatamente como na praça do Esquinão.
            BlockPos balcao = pos.relative(frente, 2).relative(frente.getClockWise());
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(camera.x, camera.y, camera.z);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                PlayerMoney.set(player, 1000);
                var balcaoEstado = IntoxicantesMod.CAIXA_MERCADO.defaultBlockState()
                        .setValue(CaixaMercadoBlock.FACING, frente.getOpposite())
                        .setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.LOWER);
                level.setBlockAndUpdate(balcao, balcaoEstado);
                level.setBlockAndUpdate(balcao.above(),
                        balcaoEstado.setValue(CaixaMercadoBlock.HALF, DoubleBlockHalf.UPPER));
                var posto = CaixaMercadoBlock.posAtendente(balcao, frente.getOpposite());
                var gago = IntoxicantesMod.GAGO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                gago.absSnapTo(posto.getX() + .5, posto.getY(), posto.getZ() + .5,
                        frente.getOpposite().toYRot(), 0F);
                gago.finalizeSpawn(level, level.getCurrentDifficultyAt(posto),
                        net.minecraft.world.entity.EntitySpawnReason.EVENT, null);
                gago.setNoAi(true);
                gago.setPersistenceRequired();
                gago.refreshTradeStock();
                level.addFreshEntity(gago);
            });
            context.waitFor(client -> client.player.distanceToSqr(camera) < .05 && client.player.getMainHandItem().isEmpty()
                    && client.level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be && !be.getItem(8).isEmpty());
            mirar(context, Vec3.atLowerCornerOf(pos).add(.5, .55, .5));
            context.runOnClient(client -> conferirPrateleira(client, pos, frente));
            context.waitTicks(8);
            context.takeScreenshot("prateleira-abastecida-9slots-" + frente.getName() + "-frente");
            var direita = frente.getClockWise();
            Vec3 iso = camera.add(direita.getStepX() * 1.15, 0, direita.getStepZ() * 1.15);
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(iso.x, iso.y, iso.z));
            context.waitFor(client -> client.player.distanceToSqr(iso) < .05);
            mirar(context, Vec3.atLowerCornerOf(pos).add(.5, .55, .5));
            context.waitTicks(8);
            context.takeScreenshot("prateleira-abastecida-9slots-" + frente.getName() + "-isometrica");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(camera.x, camera.y, camera.z));
            context.waitFor(client -> client.player.distanceToSqr(camera) < .05);
            for (int slot = 0; slot < 9; slot++) {
                int alvo = slot;
                var oferta = TradeCatalog.prateleira(0).get(slot);
                int[] antes = server.computeOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    var be = (PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(pos);
                    return new int[] {be.getItem(alvo).getCount(), PlayerMoney.get(player), player.getInventory().countItem(oferta.item())};
                });
                mirar(context, frenteDaPrateleira(pos, frente, slot));
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)
                        && PrateleiraMercadoBlock.slotDaMira(hit, client.level.getBlockState(pos)) == alvo);
                // o TAPA (botão ESQUERDO) anota a dose na gôndola…
                context.getInput().pressKey(options -> options.keyAttack);
                server.waitFor(mc -> PrateleiraNetworking.unidadesEmCarrinhoDeTeste(
                        mc.getPlayerList().getPlayers().getFirst()) > 0);
                // …e o clique DIREITO no CAIXA abre a tela da registradora
                abrirCaixaDaPrateleira(context, balcao);
                var proposta = context.computeOnClient(IdentidadeClientTest::propostaPrateleira);
                IntoxicantesMod.LOGGER.info("[Identidade] Confirmação aberta: face={} slot={} token={} total={} saldo={}",
                        frente, alvo, proposta.token(), proposta.total(), proposta.saldo());
                if (proposta.produtos().size() != 1
                        || !ItemStack.matches(proposta.produtos().getFirst(), oferta.stack())
                        || proposta.total() != oferta.price() || proposta.saldo() != antes[1]) {
                    throw new AssertionError("Confirmação não corresponde à etiqueta real em " + frente + "/" + alvo);
                }
                conferirCompraIntacta(server, pos, alvo, oferta.item(), antes);
                if (alvo == 0) {
                    context.waitTicks(5);
                    context.takeScreenshot("prateleira-confirmacao-" + frente.getName() + "-1280x800");
                    if (i == 0) {
                        configurar(context, 640, 480);
                        context.waitTicks(5);
                        context.takeScreenshot("prateleira-confirmacao-640x480");
                    }
                    responderPrateleira(context, false);
                    context.waitTicks(5);
                    conferirCompraIntacta(server, pos, alvo, oferta.item(), antes);
                    if (i == 0) configurar(context, 1280, 800);
                    abrirCaixaDaPrateleira(context, balcao);
                    long cancelado = proposta.token();
                    proposta = context.computeOnClient(IdentidadeClientTest::propostaPrateleira);
                    if (proposta.token() == cancelado) throw new AssertionError("Novo orçamento reutilizou o token cancelado");
                }
                long token = proposta.token();
                responderPrateleira(context, true);
                server.waitFor(mc -> ((PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(pos))
                        .getItem(alvo).getCount() == antes[0] - oferta.count());
                if (alvo == 0) {
                    context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new PrateleiraNetworking.ConfirmarCompraPayload(token, true)));
                    context.waitTicks(5);
                }
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    var be = (PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(pos);
                    if (be.getItem(alvo).getCount() != antes[0] - oferta.count()
                            || PlayerMoney.get(player) != antes[1] - oferta.price()
                            || player.getInventory().countItem(oferta.item()) != antes[2] + oferta.count()) {
                        throw new AssertionError("Clique real " + frente + "/" + alvo + " não conservou dinheiro/produto");
                    }
                    player.getInventory().setItem(9 + alvo, player.getMainHandItem().copy());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
                context.waitFor(client -> client.player.getMainHandItem().isEmpty());
                context.waitTicks(5);
            }
            server.runOnServer(mc -> ((PrateleiraMercadoBlockEntity) mc.overworld().getBlockEntity(pos)).reabastecer(mc.overworld()));
            context.waitFor(client -> {
                if (!(client.level.getBlockEntity(pos) instanceof PrateleiraMercadoBlockEntity be)) return false;
                var agenda = TradeCatalog.prateleira(0);
                for (int slot = 0; slot < 9; slot++) if (be.getItem(slot).getCount() != Math.min(
                        agenda.get(slot).stack().getMaxStackSize(), agenda.get(slot).stock() * agenda.get(slot).count())) return false;
                return true;
            });
        }
        BlockPos par = PRATELEIRAS[0];
        Vec3 cameraLateral = Vec3.atLowerCornerOf(par).add(2.5, 0, 1);
        server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(
                cameraLateral.x, cameraLateral.y, cameraLateral.z));
        context.waitFor(client -> client.player.distanceToSqr(cameraLateral) < .05);
        mirar(context, Vec3.atLowerCornerOf(par).add(.5, .5, 1));
        context.waitTicks(8);
        context.takeScreenshot("prateleira-costas-juntas-lateral");
        Vec3 cameraSuperior = Vec3.atLowerCornerOf(par).add(.5, 93.5 - par.getY(), 1);
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(cameraSuperior.x, cameraSuperior.y, cameraSuperior.z);
        });
        context.waitFor(client -> client.player.isSpectator() && client.player.distanceToSqr(cameraSuperior) < .05);
        mirar(context, Vec3.atLowerCornerOf(par).add(.5, .5, 1));
        context.waitTicks(8);
        context.takeScreenshot("prateleira-costas-juntas-superior");
        Vec3 cameraPequena = Vec3.atLowerCornerOf(PRATELEIRAS[3]).add(-1.5, 0, .5);
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(cameraPequena.x, cameraPequena.y, cameraPequena.z);
        });
        context.waitFor(client -> !client.player.isSpectator() && client.player.distanceToSqr(cameraPequena) < .05);
        configurar(context, 640, 480);
        context.runOnClient(client -> conferirPrateleira(client, PRATELEIRAS[3], FACES[3]));
        mirar(context, Vec3.atLowerCornerOf(PRATELEIRAS[3]).add(.5, .55, .5));
        context.waitTicks(8);
        context.takeScreenshot("prateleira-abastecida-9slots-west-640x480");
        configurar(context, 1280, 800);
    }

    private static void validarCameraPrateleira(ClientGameTestContext context) {
        context.waitTicks(15);
        double emPe = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y - client.player.getY());
        context.getInput().holdKey(opcoes -> opcoes.keyShift);
        try {
            context.waitTicks(15);
            double[] agachado = context.computeOnClient(client -> new double[] {
                    client.gameRenderer.mainCamera().position().y - client.player.getY(),
                    client.player.getBoundingBox().getYsize()});
            if (emPe - agachado[0] < .15) throw new AssertionError("Agachar normal não abaixou a câmera vanilla");
            context.takeScreenshot("prateleira-camera-agachar-normal");
            context.getInput().holdKey(opcoes -> opcoes.keySprint);
            try {
                context.waitTicks(15);
                double[] espiar = context.computeOnClient(client -> new double[] {
                        client.gameRenderer.mainCamera().position().y - client.player.getY(),
                        client.player.getBoundingBox().getYsize()});
                if (Math.abs(agachado[0] - espiar[0] - .32) > .025
                        || Math.abs(agachado[1] - espiar[1]) > .0001) {
                    throw new AssertionError("Agachar + correr não abaixou só a câmera em 0.32: "
                            + agachado[0] + " → " + espiar[0]);
                }
                context.takeScreenshot("prateleira-camera-espiar-agachar-correr");
            } finally {
                context.getInput().releaseKey(opcoes -> opcoes.keySprint);
            }
        } finally {
            context.getInput().releaseKey(opcoes -> opcoes.keyShift);
        }
        context.waitTicks(15);
        double restaurada = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y - client.player.getY());
        if (Math.abs(restaurada - emPe) > .025) throw new AssertionError("Câmera não restaurou a altura após soltar as teclas");
    }

    private static void validarAlimentos(ClientGameTestContext context, TestServerContext server) {
        for (String id : new String[] {"pao_cevada", "coco", "uva"}) {
            Item alimento = item(id);
            var original = new ItemStack(alimento, 2);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Alimento de teste " + id));
            if (id.equals("uva")) original.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(3F), List.of(), List.of(), List.of()));
            Vec3 camera = new Vec3(.5, 90, 3.5);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(camera.x, camera.y, camera.z);
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.removeAllEffects();
                player.getFoodData().setFoodLevel(8);
                player.getFoodData().setSaturation(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            });
            context.waitFor(client -> client.player.distanceToSqr(camera) < .05
                    && ItemStack.matches(client.player.getMainHandItem(), original));
            context.getInput().holdShift();
            try {
                context.waitTicks(3);
                mirar(context, Vec3.atLowerCornerOf(MESA_ALIMENTOS).add(.5, .5, .5));
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                        && hit.getBlockPos().equals(MESA_ALIMENTOS) && hit.getDirection() == Direction.UP);
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            } finally {
                context.getInput().releaseShift();
            }
            server.waitFor(mc -> mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA_ALIMENTOS).size() == 1);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var objeto = mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA_ALIMENTOS).getFirst();
                if (!ItemStack.matches(player.getMainHandItem(), original.copyWithCount(1))
                        || !ItemStack.matches(objeto.getBebida(), original.copyWithCount(1))
                        || Math.abs(objeto.getY() - 90.5) > .0001 || !objeto.temApoio() || !objeto.temEspaco()) {
                    throw new AssertionError(id + ": colocar não preservou uma unidade e apoio real da laje");
                }
                player.getInventory().setItem(9, player.getMainHandItem().copy());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            context.waitFor(client -> client.player.getMainHandItem().isEmpty()
                    && client.level.getEntitiesOfClass(BebidaDecorativaEntity.class, AREA_ALIMENTOS).size() == 1);
            mirar(context, Vec3.atLowerCornerOf(MESA_ALIMENTOS).add(.5, .68, .5));
            context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof BebidaDecorativaEntity objeto && objeto.getBebida().is(alimento));
            context.runOnClient(client -> {
                var objeto = client.level.getEntitiesOfClass(BebidaDecorativaEntity.class, AREA_ALIMENTOS).getFirst();
                if (!ItemStack.matches(objeto.getBebida(), original.copyWithCount(1))) {
                    throw new AssertionError(id + ": componentes não sincronizaram no alimento exposto");
                }
                Object registrado = client.getEntityRenderDispatcher().getRenderer(objeto);
                if (!(registrado instanceof BebidaDecorativaRenderer renderer)) throw new AssertionError("Renderer de alimentos ausente");
                var estado = renderer.createRenderState();
                renderer.extractRenderState(objeto, estado, 0);
                var volume = estado.item.getModelBoundingBox();
                var caixa = objeto.getBoundingBox();
                if (estado.item.isEmpty() || volume.hasNaN() || Math.abs(volume.minY + estado.baseY) > .00001
                        || volume.maxY + estado.baseY > caixa.getYsize() + .0001
                        || volume.minX < -caixa.getXsize() / 2 - .0001 || volume.maxX > caixa.getXsize() / 2 + .0001
                        || volume.minZ < -caixa.getZsize() / 2 - .0001 || volume.maxZ > caixa.getZsize() / 2 + .0001
                        || Math.abs(caixa.minY - 90.5) > .0001 || !objeto.temApoio() || !objeto.temEspaco()) {
                    throw new AssertionError(id + ": modelo real flutuando ou fora da caixa física: " + volume + "/" + caixa);
                }
                conferirSprite(estado.item.pickParticleMaterial(RandomSource.create(0)).sprite(), "alimento/" + id);
            });
            context.waitTicks(8);
            context.takeScreenshot("alimento-" + id + "-apoiado-na-laje");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            server.waitFor(mc -> mc.overworld().getEntitiesOfClass(BebidaDecorativaEntity.class, AREA_ALIMENTOS).isEmpty());
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                if (!ItemStack.matches(player.getMainHandItem(), original.copyWithCount(1))
                        || player.getInventory().countItem(alimento) != 2 || player.getFoodData().getFoodLevel() != 8
                        || player.getFoodData().getSaturationLevel() != 0 || !player.getActiveEffects().isEmpty()
                        || player.getInventory().countItem(Items.GLASS_BOTTLE) != 0) {
                    throw new AssertionError(id + ": recolher consumiu, duplicou ou perdeu componentes/alimento");
                }
            });
            context.waitFor(client -> ItemStack.matches(client.player.getMainHandItem(), original.copyWithCount(1)));
            context.getInput().lookAt(0, -20);
            context.waitTicks(6);
            context.takeScreenshot("alimento-" + id + "-recolhido-na-mao");
        }
    }

    private static EsquinaoNetworking.AbrirCardapioPayload cardapio(Minecraft client) {
        if (!(client.gui.screen() instanceof EsquinaoCardapioScreen tela)) throw new AssertionError("Cardápio fechou inesperadamente");
        return (EsquinaoNetworking.AbrirCardapioPayload) campo(tela, "p");
    }

    private static void moverNaTela(ClientGameTestContext context, double x, double y) {
        double[] ponto = context.computeOnClient(client -> new double[] {
                x * client.getWindow().getWidth() / client.gui.screen().width,
                y * client.getWindow().getHeight() / client.gui.screen().height});
        context.getInput().setCursorPos(ponto[0], ponto[1]);
    }

    private static void rolarCardapio(ClientGameTestContext context, int linha) {
        double[] origem = context.computeOnClient(client -> new double[] {
                numero(client.gui.screen(), "x0") + 120, numero(client.gui.screen(), "listaY") + 10});
        moverNaTela(context, origem[0], origem[1]);
        int atual = context.computeOnClient(client -> (int) numero(client.gui.screen(), "scrollLinha"));
        for (int i = atual; i != linha; i += atual < linha ? 1 : -1) {
            context.getInput().scroll(atual < linha ? -1 : 1);
            context.waitTick();
        }
        context.runOnClient(client -> {
            if ((int) numero(client.gui.screen(), "scrollLinha") != linha) throw new AssertionError("Scroll não alcançou produto " + linha);
        });
    }

    private static void clicarPrimeiraOferta(ClientGameTestContext context) {
        double[] botao = context.computeOnClient(client -> new double[] {
                numero(client.gui.screen(), "x0") + 269, numero(client.gui.screen(), "listaY") + 13});
        moverNaTela(context, botao[0], botao[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static void validarCardapio(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.teleportTo(.5, 90, -3.5);
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            PlayerMoney.set(player, 1000);
            if (FidelidadeData.getTier(player) != 0) throw new AssertionError("Mundo de teste precisa começar sem fidelidade");
            var gago = IntoxicantesMod.GAGO.create(mc.overworld(), EntitySpawnReason.COMMAND);
            if (gago == null) throw new AssertionError("Gago não pôde ser criado");
            gago.absSnapTo(.5, 90, -1.5, 180, 0);
            gago.setNoAi(true);
            gago.setPersistenceRequired();
            gago.refreshTradeStock();
            mc.overworld().addFreshEntity(gago);
        });
        context.waitFor(client -> client.player.distanceToSqr(new Vec3(.5, 90, -3.5)) < .05
                && client.player.getMainHandItem().isEmpty());
        mirar(context, new Vec3(.5, 91.5, -1.5));
        context.waitFor(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof GagoEntity);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(EsquinaoCardapioScreen.class);
        context.runOnClient(client -> conferirCardapio(client, 0, 71));
        context.waitTicks(6);
        context.takeScreenshot("gago-cardapio-71produtos-inicio");
        rolarCardapio(context, 21);
        context.takeScreenshot("gago-cardapio-pao-cevada-scroll");
        clicarPrimeiraOferta(context);
        server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(IntoxicantesMod.PAO_CEVADA) == 1);
        server.runOnServer(mc -> {
            if (PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) != 994) throw new AssertionError("Compra real do pão de cevada não cobrou R$6");
        });
        context.waitFor(client -> cardapio(client).saldo() == 994);
        rolarCardapio(context, 28);
        clicarPrimeiraOferta(context);
        server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.BREAD) == 1);
        server.runOnServer(mc -> {
            if (PlayerMoney.get(mc.getPlayerList().getPlayers().getFirst()) != 989) throw new AssertionError("Compra real do pão vanilla não cobrou R$5");
        });
        context.waitFor(client -> cardapio(client).saldo() == 989);
        configurar(context, 640, 480);
        rolarCardapio(context, 28);
        context.takeScreenshot("gago-cardapio-pao-640x480");
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            while (FidelidadeData.getTier(player) < 3) FidelidadeData.registrarCompra(player);
            var npc = mc.overworld().getEntitiesOfClass(GagoEntity.class, new AABB(-2, 89, -3, 3, 94, 1)).getFirst();
            EsquinaoNetworking.enviarCardapio(player, npc, false);
        });
        context.waitFor(client -> cardapio(client).produtos().size() == 76);
        context.runOnClient(client -> conferirCardapio(client, 3, 76));
        double[] aba = context.computeOnClient(client -> new double[] {
                numero(client.gui.screen(), "x0") + 255, numero(client.gui.screen(), "y0") + 84});
        moverNaTela(context, aba[0], aba[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
        context.takeScreenshot("gago-cardapio-76produtos-exclusivos-640x480");
        clicarPrimeiraOferta(context);
        server.waitFor(mc -> mc.getPlayerList().getPlayers().getFirst().getInventory().countItem(item("cha_lupulo")) == 4);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        server.waitFor(mc -> mc.overworld().getEntitiesOfClass(GagoEntity.class, new AABB(-2, 89, -3, 3, 94, 1))
                .stream().noneMatch(GagoEntity::isTrading));
        configurar(context, 1280, 800);
    }

    private static void conferirCardapio(Minecraft client, int tier, int total) {
        var payload = cardapio(client);
        var agenda = TradeCatalog.gago(tier);
        if (payload.produtos().size() != total || payload.primeiroExclusivo() != 71 || payload.nivel() != tier) {
            throw new AssertionError("Cardápio/fronteira de fidelidade incorretos: " + payload.produtos().size() + "/" + payload.primeiroExclusivo());
        }
        for (int i = 0; i < agenda.size(); i++) if (!ItemStack.matches(payload.produtos().get(i), agenda.get(i).stack())) {
            throw new AssertionError("Cardápio perdeu item/dose no índice " + i);
        }
    }

    private static Item item(String id) {
        Identifier key = Identifier.fromNamespaceAndPath("intoxicantes", id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new AssertionError("ID aprovado ausente: " + id);
        return BuiltInRegistries.ITEM.getValue(key);
    }

    private static Block bloco(String id) {
        if (!(item(id) instanceof BlockItem blockItem)) throw new AssertionError(id + ": não é BlockItem");
        return blockItem.getBlock();
    }

    private static void conferirItem(Minecraft client, String id, Set<Identifier> conferidos) {
        Item item = item(id);
        var stack = new ItemStack(item);
        var variantes = new ArrayList<ItemStack>();
        variantes.add(stack);
        if (id.equals("uva")) {
            var madura = stack.copy();
            madura.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(3F), List.of(), List.of(), List.of()));
            variantes.add(madura);
        }
        var modelo = new ItemStackRenderState();
        for (ItemStack variante : variantes) for (ItemDisplayContext perspectiva : new ItemDisplayContext[] {ItemDisplayContext.NONE,
                ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED}) {
            modelo.clear();
            client.getItemModelResolver().updateForLiving(modelo, variante, perspectiva, client.player);
            var volume = modelo.getModelBoundingBox();
            if (modelo.isEmpty() || volume.hasNaN()
                    || volume.getXsize() + volume.getYsize() + volume.getZsize() <= .001) {
                throw new AssertionError(id + ": modelo inválido em " + perspectiva);
            }
            var material = modelo.pickParticleMaterial(RandomSource.create(0));
            if (material == null) throw new AssertionError(id + ": material ausente em " + perspectiva);
            conferirSprite(material.sprite(), id + "/" + perspectiva);
        }
        try {
            JsonObject definition = lerJson(client, "items/" + id + ".json");
            Set<Identifier> referencias = new HashSet<>();
            modelosDaDefinition(definition.get("model"), referencias);
            if (referencias.isEmpty()) throw new AssertionError(id + ": definition sem modelo");
            for (Identifier referencia : referencias) {
                Map<String, String> textures = texturasModelo(client, referencia, new HashSet<>());
                if (textures.isEmpty()) throw new AssertionError(id + ": modelo sem materiais: " + referencia);
                for (String value : textures.values()) {
                    Set<String> visitados = new HashSet<>();
                    while (value.startsWith("#")) {
                        if (!visitados.add(value) || !textures.containsKey(value.substring(1))) {
                            throw new AssertionError(id + ": referência de material inválida: " + value);
                        }
                        value = textures.get(value.substring(1));
                    }
                    Identifier texture = Identifier.parse(value);
                    if ("intoxicantes".equals(texture.getNamespace()) && conferidos.add(texture)) {
                        conferirTexturaAtiva(client, texture);
                    }
                }
            }
        } catch (IOException ex) {
            throw new AssertionError(id + ": recurso ativo ausente", ex);
        }
        if (item instanceof BlockItem blockItem) conferirBloco(client, blockItem.getBlock());
    }

    private static void modelosDaDefinition(JsonElement elemento, Set<Identifier> referencias) {
        if (elemento == null || elemento.isJsonNull()) return;
        if (elemento.isJsonArray()) {
            elemento.getAsJsonArray().forEach(filho -> modelosDaDefinition(filho, referencias));
        } else if (elemento.isJsonObject()) {
            var objeto = elemento.getAsJsonObject();
            if (objeto.has("type") && objeto.get("type").getAsString().equals("minecraft:model")) {
                if (!objeto.has("model") || !objeto.get("model").isJsonPrimitive()
                        || !objeto.get("model").getAsJsonPrimitive().isString()) {
                    throw new AssertionError("Definition minecraft:model sem referência válida: " + objeto);
                }
                referencias.add(Identifier.parse(objeto.get("model").getAsString()));
            }
            objeto.entrySet().forEach(entry -> modelosDaDefinition(entry.getValue(), referencias));
        }
    }

    private static JsonObject lerJson(Minecraft client, String path) throws IOException {
        return lerJson(client, Identifier.fromNamespaceAndPath("intoxicantes", path));
    }

    private static JsonObject lerJson(Minecraft client, Identifier path) throws IOException {
        try (var reader = client.getResourceManager().openAsReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, String> texturasModelo(Minecraft client, Identifier model,
                                                      Set<Identifier> visitados) throws IOException {
        if (!visitados.add(model)) throw new AssertionError("Ciclo nos modelos: " + model);
        var json = lerJson(client, Identifier.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json"));
        Map<String, String> textures = new LinkedHashMap<>();
        if (json.has("parent")) {
            Identifier parent = Identifier.parse(json.get("parent").getAsString());
            if (!parent.getPath().startsWith("builtin/")) textures.putAll(texturasModelo(client, parent, visitados));
        }
        if (json.has("textures")) json.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        return textures;
    }

    private static void conferirTexturaAtiva(Minecraft client, Identifier texture) throws IOException {
        Identifier path = Identifier.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
        try (var stream = client.getResourceManager().open(path)) {
            byte[] header = stream.readNBytes(24);
            if (header.length != 24 || header[0] != (byte) 137 || header[1] != 80
                    || header[2] != 78 || header[3] != 71) throw new AssertionError(texture + ": PNG inválido");
            var size = ByteBuffer.wrap(header, 16, 8);
            conferirResolucao(texture, size.getInt(), size.getInt());
        }
        boolean[] carregada = {false};
        client.getAtlasManager().forEach((atlasId, atlas) -> {
            var sprite = atlas.getSprite(texture);
            if (sprite != null && sprite.contents().name().equals(texture)) {
                conferirSprite(sprite, texture.toString());
                carregada[0] = true;
            }
        });
        if (!carregada[0]) throw new AssertionError(texture + ": não carregou em nenhum atlas do cliente");
    }

    private static void conferirSprite(TextureAtlasSprite sprite, String origem) {
        var contents = sprite.contents();
        if (contents.name().equals(MissingTextureAtlasSprite.getLocation())
                || !"intoxicantes".equals(contents.name().getNamespace())) {
            throw new AssertionError(origem + ": material genérico/ausente: " + contents.name());
        }
        conferirResolucao(contents.name(), contents.width(), contents.height());
    }

    private static void conferirResolucao(Identifier name, int width, int height) {
        // As quatro artes1254 do poste foram preservadas no pacote aprovado;
        // gen_garrafas.py recorta SOMENTE estas partículas oficiais do tile
        // cap32. Os atlas e as demais pinturas continuam exigindo128.
        boolean poste = Set.of("block/poste_ped", "block/poste_luz", "block/poste_luz_on", "block/poste_luz_off")
                .contains(name.getPath());
        boolean particula = Set.of("block/coco_particula",
                "item/garrafas/pao_cevada_particula", "item/garrafas/cerveja_particula",
                "item/garrafas/vinho_particula", "item/garrafas/hidromel_particula",
                "item/garrafas/cachaca_particula", "item/garrafas/rum_particula",
                "item/garrafas/suco_detox_particula", "item/garrafas/agua_de_coco_particula",
                "item/garrafas/cha_lupulo_particula", "item/garrafas/caldo_de_cana_particula",
                "item/garrafas/mosto_cana_fermentado_particula", "item/garrafas/cachaca_jovem_particula",
                "item/garrafas/melaco_particula", "item/garrafas/mosto_rum_fermentado_particula",
                "item/garrafas/rum_jovem_particula", "item/garrafas/mosto_de_uva_particula",
                "item/garrafas/mosto_cerveja_lupulado_particula").contains(name.getPath());
        if (width != height || !(particula ? width == 32 : width == 128 || poste && width == 1254)) {
            throw new AssertionError(name + ": resolução inesperada " + width + "x" + height);
        }
    }

    private static void conferirBloco(Minecraft client, Block block) {
        var models = client.getModelManager().getBlockStateModelSet();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            var model = models.get(state);
            if (model == models.missingModel()) throw new AssertionError("Blockstate sem modelo: " + state);
            conferirSprite(model.particleMaterial().sprite(), state.toString());
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(RandomSource.create(0), parts);
            int faces = 0;
            for (var part : parts) {
                for (Direction direction : Direction.values()) for (var quad : part.getQuads(direction)) {
                    conferirSprite(quad.materialInfo().sprite(), state.toString());
                    faces++;
                }
                for (var quad : part.getQuads(null)) {
                    conferirSprite(quad.materialInfo().sprite(), state.toString());
                    faces++;
                }
            }
            if (faces == 0) throw new AssertionError("Bloco renderizado sem faces: " + state);
        }
    }

    private static void prepararMundo(ServerLevel level) {
        for (int x = -12; x <= 19; x++) for (int z = -9; z <= 12; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 89, z), Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 90; y <= 96; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(RAIZ.below(), Blocks.DIRT.defaultBlockState());
        BlockPos poste = RAIZ.east();
        for (int y = 0; y < 3; y++) level.setBlockAndUpdate(poste.above(y), Blocks.OAK_FENCE.defaultBlockState());
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            level.setBlockAndUpdate(poste.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
        }
        level.setBlockAndUpdate(RAIZ, IntoxicantesMod.UVA_PLANT.defaultBlockState()
                .setValue(UvCropBlock.AGE, 4).setValue(UvCropBlock.UV_AGE, 3));
        BlockPos coco = new BlockPos(1, 91, 3);
        level.setBlockAndUpdate(coco.above(), Blocks.JUNGLE_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(coco, IntoxicantesMod.COCO_BLOCO.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(5, 91, 4), IntoxicantesMod.LAMPADA_UV.defaultBlockState());
        for (int i = 0; i < IntoxicantesMod.LAMPADAS_POTENCIA.size(); i++) {
            BlockPos pos = new BlockPos(i * 2 - 8, 91, 10);
            level.setBlockAndUpdate(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADAS_POTENCIA.get(i).defaultBlockState());
        }
        BlockPos door = new BlockPos(10, 90, 3);
        BlockState state = IntoxicantesMod.PORTA_GRADE.defaultBlockState().setValue(PortaGradeBlock.FACING, Direction.NORTH)
                .setValue(PortaGradeBlock.FECHADA, true);
        level.setBlock(door, state.setValue(PortaGradeBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(door.above(), state.setValue(PortaGradeBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        level.setBlockAndUpdate(new BlockPos(13, 90, 3), bloco("prateleira_mercado").defaultBlockState());
        ((PrateleiraMercadoBlockEntity) level.getBlockEntity(new BlockPos(13, 90, 3))).reabastecer(level);
        for (int i = 0; i < PRATELEIRAS.length; i++) {
            BlockPos pos = PRATELEIRAS[i];
            level.setBlockAndUpdate(pos, IntoxicantesMod.PRATELEIRA_MERCADO.defaultBlockState()
                    .setValue(PrateleiraMercadoBlock.FACING, FACES[i]));
            var be = (PrateleiraMercadoBlockEntity) level.getBlockEntity(pos);
            var dados = new CompoundTag();
            var raiz = new CompoundTag();
            raiz.putInt("secao", 0);
            dados.put("prateleira", raiz);
            be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), dados));
            be.reabastecer(level);
            BlockPos costas = pos.relative(FACES[i].getOpposite());
            level.setBlockAndUpdate(costas, IntoxicantesMod.PRATELEIRA_MERCADO.defaultBlockState()
                    .setValue(PrateleiraMercadoBlock.FACING, FACES[i].getOpposite()));
            var par = (PrateleiraMercadoBlockEntity) level.getBlockEntity(costas);
            par.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), dados));
            par.reabastecer(level);
            for (int slot = 0; slot < 9; slot++) if (be.getItem(slot).isEmpty()) {
                throw new AssertionError("Cenário não abasteceu o slot " + slot + " da face " + FACES[i]);
            }
        }
        level.setBlockAndUpdate(MESA_ALIMENTOS, Blocks.OAK_SLAB.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(13, 92, 3), IntoxicantesMod.LAMPADA_LED.defaultBlockState());
        for (int x = 16; x <= 18; x++) for (int y = 90; y <= 93; y++) {
            level.setBlockAndUpdate(new BlockPos(x, y, 4), Blocks.SMOOTH_STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(16, 91, 3), bloco("quadro_eletrico").defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(17, 91, 3), bloco("interruptor_simples").defaultBlockState());
        for (int y = 90; y <= 93; y++) level.setBlockAndUpdate(new BlockPos(18, y, 3), bloco("cabo_cobre_2_5mm").defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(17, 94, 5), Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(17, 93, 5), bloco("soquete_teto").defaultBlockState());
        BlockPos basePoste = new BlockPos(15, 90, 7);
        level.setBlockAndUpdate(basePoste, IntoxicantesMod.POSTE_LUZ.defaultBlockState());
        // onPlace ergue corpo e topo; o cenário exige o mesmo poste completo do jogo.
        PosteLuzBlock.Parte[] partes = {PosteLuzBlock.Parte.BASE, PosteLuzBlock.Parte.CORPO,
                PosteLuzBlock.Parte.TOPO};
        for (int i = 0; i < partes.length; i++) {
            var parte = level.getBlockState(basePoste.above(i));
            if (!parte.is(IntoxicantesMod.POSTE_LUZ) || parte.getValue(PosteLuzBlock.PARTE) != partes[i]) {
                throw new AssertionError("Poste do cenário não construiu a parte " + partes[i]);
            }
        }
    }

    private static void mirar(ClientGameTestContext context, Vec3 alvo) {
        float[] angulos = context.computeOnClient(client -> {
            Vec3 delta = alvo.subtract(client.player.getEyePosition());
            return new float[] {(float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))};
        });
        context.getInput().lookAt(angulos[0], angulos[1]);
        context.waitTicks(2);
    }
}
