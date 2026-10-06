package com.intoxicantes;

import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Regressões da muda, da estrutura de cercas e da colheita autoritativa. */
public class ParreiraGameTest {
    private static final BlockPos RAIZ = new BlockPos(3, 1, 3);

    private static BlockState planta(int idade, int uv) {
        return IntoxicantesMod.UVA_PLANT.defaultBlockState()
                .setValue(UvCropBlock.AGE, idade).setValue(UvCropBlock.UV_AGE, uv);
    }

    private static BlockPos preparar(GameTestHelper helper, int idade, int uv) {
        var level = helper.getLevel();
        // A área fica no mundo descartável do GameTest. O padding separa as
        // coberturas dos outros testes, inclusive quando há execução em lote.
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) {
            helper.setBlock(x, 0, z, Blocks.DIRT);
            for (int y = 1; y <= 5; y++) helper.setBlock(x, y, z, Blocks.AIR);
        }
        BlockPos raiz = helper.absolutePos(RAIZ);
        level.setBlockAndUpdate(raiz, planta(idade, uv));
        return raiz;
    }

    private static void estruturaCompleta(GameTestHelper helper, BlockPos raiz, Block cerca) {
        var level = helper.getLevel();
        BlockPos poste = raiz.east();
        for (int y = 0; y < 3; y++) level.setBlockAndUpdate(poste.above(y), cerca.defaultBlockState());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlockAndUpdate(poste.above(2).offset(x, 0, z), cerca.defaultBlockState());
        }
        manter(helper, raiz, 20);
    }

    private static ParreiraBlockEntity entidade(GameTestHelper helper, BlockPos raiz) {
        var be = helper.getLevel().getBlockEntity(raiz);
        helper.assertTrue(be instanceof ParreiraBlockEntity, "A muda possui sua própria BlockEntity");
        return (ParreiraBlockEntity) be;
    }

    private static void manter(GameTestHelper helper, BlockPos raiz, int ticks) {
        ParreiraBlockEntity be = entidade(helper, raiz);
        for (int i = 0; i < ticks; i++) {
            ParreiraBlockEntity.tick(helper.getLevel(), raiz, helper.getLevel().getBlockState(raiz), be);
        }
    }

    private static ServerPlayer jogador(GameTestHelper helper, BlockPos alvo) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.absSnapTo(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        return player;
    }

    private static void clicar(GameTestHelper helper, ServerPlayer player, BlockPos alvo) {
        UvaParreiraBlock.interagir(player, helper.getLevel(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(alvo), Direction.NORTH, alvo, false));
    }

    private static int quantidade(GameTestHelper helper, ServerPlayer player, BlockPos raiz, Item item) {
        int soltos = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(raiz).inflate(5)).stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        return player.getInventory().countItem(item) + soltos;
    }

    private static void guardarMao(ServerPlayer player) {
        ItemStack mao = player.getMainHandItem();
        if (!mao.isEmpty()) {
            player.getInventory().setItem(9, mao);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    @GameTest(padding = 8)
    public void sementePlantaNaTerraENaTerraAradaMasNaoNaPedra(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 0, 0);
        helper.getLevel().setBlockAndUpdate(raiz, Blocks.AIR.defaultBlockState());
        ServerPlayer player = jogador(helper, raiz);
        for (Block solo : new Block[] { Blocks.DIRT, Blocks.FARMLAND, Blocks.STONE }) {
            helper.getLevel().setBlockAndUpdate(raiz, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(raiz.below(), solo.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.SEMENTE_UVA));
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(raiz.below()).add(0, 0.5, 0),
                            Direction.UP, raiz.below(), false)));
            helper.assertTrue(helper.getLevel().getBlockState(raiz).is(IntoxicantesMod.UVA_PLANT)
                            == (solo != Blocks.STONE),
                    "A semente deve respeitar o solo ao ser usada: " + solo);
        }
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void ramosSobemESeEspalhamSemSubstituirCercas(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaCompleta(helper, raiz, Blocks.SPRUCE_FENCE);
        BlockPos molhada = raiz.east().above();
        helper.getLevel().setBlock(molhada, helper.getLevel().getBlockState(molhada)
                .setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_CLIENTS);
        Map<BlockPos, BlockState> antes = new LinkedHashMap<>();
        var estrutura = ParreiraEstrutura.buscar(helper.getLevel(), raiz);
        for (BlockPos pos : estrutura.apoios()) antes.put(pos, helper.getLevel().getBlockState(pos));
        manter(helper, raiz, 40);
        var visivel = entidade(helper, raiz).lerEstrutura();
        helper.assertTrue(visivel.coluna().size() == 3, "A parreira sobe os três postes conectados");
        helper.assertTrue(visivel.cobertura().size() == 25, "A parreira cobre o teto conectado de 5 por 5");
        helper.assertTrue(visivel.cobertura().contains(raiz.east().above(2).north().west()),
                "Os ramos podem dobrar e preencher a lateral da cobertura");
        for (var apoio : antes.entrySet()) {
            helper.assertTrue(helper.getLevel().getBlockState(apoio.getKey()).equals(apoio.getValue()),
                    "O ramo preserva material, conexões e água da cerca em " + apoio.getKey());
        }
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void crescimentoEAduboEsperamPosteECobertura(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 0, 0);
        ItemStack adubo = new ItemStack(Items.BONE_MEAL, 64);
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) == 1,
                "Sem cerca a muda cresce até o primeiro estágio e espera um apoio");
        helper.getLevel().setBlockAndUpdate(raiz.east(), Blocks.OAK_FENCE.defaultBlockState());
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) <= 2,
                "Um poste curto não faz a parreira produzir frutos");
        helper.getLevel().setBlockAndUpdate(raiz.east().above(), Blocks.OAK_FENCE.defaultBlockState());
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) <= 2,
                "Uma coluna sem cobertura ainda não sustenta a produção");
        helper.getLevel().setBlockAndUpdate(raiz.east().above().north(), Blocks.OAK_FENCE.defaultBlockState());
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) == 3,
                "A planta nova constrói seus ramos, mas o adubo respeita a recuperação do primeiro lote");
        manter(helper, raiz, entidade(helper, raiz).ticksRebrotaRestantes());
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) == 4,
                "Com coluna, cobertura e recuperação completa, o adubo termina o crescimento");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void dezCachosEntregamDezUvasUmaPorClique(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        var be = entidade(helper, raiz);
        helper.assertTrue(be.frutosRestantes() == 10, "Cada um dos dez apoios mostra um cacho real");
        ServerPlayer player = jogador(helper, raiz);
        var apoios = be.lerEstrutura().cobertura();
        for (int i = 0; i < apoios.size(); i++) {
            BlockPos apoio = apoios.get(i);
            player.absSnapTo(apoio.getX() + .5, raiz.getY(), apoio.getZ() - 1.5);
            clicar(helper, player, apoio);
            helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == i + 1,
                    "Cada clique entrega exatamente uma uva, inclusive segurando a colheita anterior");
            helper.assertTrue(!be.cachoPronto(apoio) && be.frutosRestantes() == 9 - i,
                    "Só o cacho clicado desaparece; os outros conservam sua vida");
            clicar(helper, player, apoio);
            helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == i + 1,
                    "Repetir o clique no mesmo cacho não duplica itens");
        }
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.SEMENTE_UVA) == 0,
                "Colher cachos não cria sementes");
        helper.assertTrue(helper.getLevel().getBlockState(raiz).getValue(UvCropBlock.AGE) == 4
                        && be.etapaVisual() == 4,
                "A raiz e a folhagem inteira permanecem estabelecidas após colher dez cachos");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void cachoComESemUvEntregamExatamenteUmaUva(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 0);
        estruturaDezCachos(helper, raiz);
        ServerPlayer player = jogador(helper, raiz);
        clicar(helper, player, raiz.east().above(2));
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "Um cacho sem maturação extra rende uma uva");
        clicar(helper, player, raiz);
        clicar(helper, player, raiz.east());
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "Raiz e coluna não recolhem frutos de outros apoios");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void removerPostePodaRamosSemApagarRaizNemCercasRestantes(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaCompleta(helper, raiz, Blocks.DARK_OAK_FENCE);
        BlockPos teto = raiz.east().above(2).north();
        helper.getLevel().destroyBlock(raiz.east(), false);
        manter(helper, raiz, 40);
        helper.assertTrue(entidade(helper, raiz).lerEstrutura().apoios().isEmpty(),
                "Perder o poste de base retira todos os ramos que dependiam dele");
        helper.assertTrue(helper.getLevel().getBlockState(raiz).is(IntoxicantesMod.UVA_PLANT),
                "A poda de um apoio não arranca a muda do solo");
        helper.assertTrue(helper.getLevel().getBlockState(teto).is(Blocks.DARK_OAK_FENCE),
                "Cercas que perderam o ramo continuam sendo cercas do jogador");
        ServerPlayer player = jogador(helper, raiz);
        clicar(helper, player, teto);
        clicar(helper, player, raiz);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Um teto desligado da raiz não pode entregar a colheita");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void cachosParcialmenteColhidosPersistemSemAtalhoDeAdubo(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        ServerPlayer player = jogador(helper, raiz);
        BlockPos apoio = raiz.east().above(2);
        clicar(helper, player, apoio);
        manter(helper, raiz, 47);
        var original = entidade(helper, raiz);
        var snapshot = original.cachosClient();
        int faltam = original.estadoCacho(apoio).ticksRebrota();
        var saida = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.saveWithoutMetadata(saida);
        var estado = helper.getLevel().getBlockState(raiz);
        var nova = ((EntityBlock) IntoxicantesMod.UVA_PLANT).newBlockEntity(raiz, estado);
        helper.getLevel().removeBlockEntity(raiz);
        nova.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saida.buildResult()));
        helper.getLevel().setBlockEntity(nova);
        var recarregada = entidade(helper, raiz);
        helper.assertTrue(recarregada.cachosClient().equals(snapshot) && recarregada.frutosRestantes() == 9,
                "Recarga preserva cada cacho colhido e cada cacho ainda pendurado");
        ItemStack adubo = new ItemStack(Items.BONE_MEAL, 64);
        for (int i = 0; i < 8; i++) BoneMealItem.growCrop(adubo, helper.getLevel(), raiz);
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1
                        && recarregada.estadoCacho(apoio).ticksRebrota() == faltam,
                "Adubo e recarga não produzem uma segunda uva no slot em recuperação");
        manter(helper, raiz, faltam + ParreiraBlockEntity.INTERVALO_SINCRONIZACAO);
        helper.assertTrue(recarregada.cachoPronto(apoio) && recarregada.frutosRestantes() == 10,
                "Só o cacho antes colhido volta quando termina seu tempo");
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 2,
                "A rebrota entrega exatamente uma nova uva, sem herdar bônus de quantidade UV");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void podaERecolocacaoNaoReiniciamCachoColhido(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos apoio = raiz.east().above(2).east(4);
        ServerPlayer player = jogador(helper, apoio);
        clicar(helper, player, apoio);
        int antes = entidade(helper, raiz).estadoCacho(apoio).ticksRebrota();
        helper.getLevel().destroyBlock(apoio, false);
        manter(helper, raiz, 80);
        helper.assertTrue(entidade(helper, raiz).estadoCacho(apoio).ticksRebrota() == antes,
                "Uma posição podada conserva seu cooldown em vez de criar frutos invisíveis");
        helper.getLevel().setBlockAndUpdate(apoio, Blocks.OAK_FENCE.defaultBlockState());
        manter(helper, raiz, 20);
        helper.assertFalse(entidade(helper, raiz).cachoPronto(apoio),
                "Recolocar a mesma cerca não repõe o fruto recém-colhido");
        helper.assertTrue(entidade(helper, raiz).estadoCacho(apoio).ticksRebrota() < antes,
                "A reconexão retoma o mesmo crescimento pausado");
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "Podar, reconectar e clicar não duplica a primeira uva");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void plantaMaduraAntigaMantemIdadeUvELootAoMigrar(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        manter(helper, raiz, 40);
        var estado = helper.getLevel().getBlockState(raiz);
        helper.assertTrue(estado.getValue(UvCropBlock.AGE) == 4 && estado.getValue(UvCropBlock.UV_AGE) == 3,
                "Uma planta de um save anterior não perde maturação por não possuir o pergolado novo");
        var drops = Block.getDrops(estado, helper.getLevel(), raiz, entidade(helper, raiz));
        helper.assertTrue(drops.stream().filter(stack -> stack.is(IntoxicantesMod.UVA))
                        .mapToInt(ItemStack::getCount).sum() == 3,
                "Quebrar uma planta antiga madura preserva sua colheita histórica");
        helper.assertTrue(drops.stream().filter(stack -> stack.is(IntoxicantesMod.SEMENTE_UVA))
                        .mapToInt(ItemStack::getCount).sum() == 2,
                "A raiz antiga madura preserva a semente plantada e a semente extra da maturação UV");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void quebrarRaizDepoisDeColheitaParcialSoltaSomenteCachosRestantes(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        ServerPlayer player = jogador(helper, raiz);
        clicar(helper, player, raiz.east().above(2));
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(player.gameMode.destroyBlock(raiz), "O jogador consegue arrancar a raiz no sobrevivência");
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 10,
                "Uma uva colhida mais nove cachos restantes totalizam dez, sem lote extra na raiz");
        helper.assertTrue(helper.getLevel().getBlockState(raiz.east()).is(Blocks.OAK_FENCE),
                "Arrancar a raiz não remove o pergolado");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(CachoParreiraEntity.class,
                new AABB(raiz).inflate(7), entity -> entity.raiz().equals(raiz)).isEmpty(),
                "Arrancar a raiz remove as hitboxes temporárias de seus cachos");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void estruturaIgnoraApoioSoltoELimitaAlturaEExtensao(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaCompleta(helper, raiz, Blocks.OAK_FENCE);
        BlockPos poste = raiz.east();
        helper.getLevel().setBlockAndUpdate(poste.above(3), Blocks.OAK_FENCE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(poste.above(2).east(3), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos solta = poste.above().west(2);
        helper.getLevel().setBlockAndUpdate(solta, Blocks.OAK_FENCE.defaultBlockState());
        var estrutura = ParreiraEstrutura.buscar(helper.getLevel(), raiz);
        helper.assertTrue(estrutura.altura() == 3 && estrutura.coluna().size() == 3,
                "A parreira não escala um quarto bloco de poste");
        helper.assertTrue(estrutura.cobertura().size() == 25,
                "A área produtiva permanece limitada a vinte e cinco posições de cobertura");
        helper.assertFalse(estrutura.apoios().contains(poste.above(3)), "O poste alto demais fica fora dos ramos");
        helper.assertFalse(estrutura.apoios().contains(poste.above(2).east(3)), "A extensão fora do raio fica sem ramo");
        helper.assertFalse(estrutura.apoios().contains(solta), "Uma cerca lateral isolada não recebe ramos");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void servidorRejeitaColheitaDistanteComItemOuAlvoAlheio(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos apoio = raiz.east().above(2);
        ServerPlayer player = jogador(helper, apoio);
        player.absSnapTo(raiz.getX() + 20, raiz.getY(), raiz.getZ());
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Um pacote forjado à distância não colhe cachos");
        player.absSnapTo(apoio.getX() + .5, raiz.getY(), apoio.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Itens de construção não são interceptados pela colheita");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos alheia = raiz.west();
        helper.getLevel().setBlockAndUpdate(alheia, Blocks.OAK_FENCE.defaultBlockState());
        entidade(helper, raiz).colher(helper.getLevel(), player, alheia);
        clicar(helper, player, raiz);
        UvaParreiraBlock.interagir(player, helper.getLevel(), InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(apoio), Direction.NORTH, apoio, false));
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Apoio alheio, raiz e segunda mão não autorizam colher frutos de outro lugar");
        clicar(helper, player, apoio);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "Depois dos pacotes inválidos, o cacho válido ainda está intacto");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void consultaDaEstruturaNaoCarregaChunkAusente(GameTestHelper helper) {
        BlockPos distante = new BlockPos(2_000_000, 90, 2_000_000);
        helper.assertFalse(helper.getLevel().hasChunkAt(distante), "O cenário remoto começa sem chunk carregado");
        helper.assertTrue(ParreiraEstrutura.buscar(helper.getLevel(), distante).apoios().isEmpty(),
                "Uma raiz em chunk ausente não tem estrutura disponível");
        helper.assertFalse(helper.getLevel().hasChunkAt(distante),
                "Consultar uma parreira não pode gerar nem carregar um chunk distante");
        helper.succeed();
    }

    @GameTest(padding = 12)
    public void posteNoCantoAlimentaTodaCoberturaCincoPorCinco(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        BlockPos poste = raiz.east();
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(poste.above(y), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
            helper.getLevel().setBlockAndUpdate(poste.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
        }
        var estrutura = ParreiraEstrutura.buscar(helper.getLevel(), raiz);
        BlockPos extremo = poste.above(2).offset(4, 0, 4);
        helper.assertTrue(estrutura.cobertura().size() == 25 && estrutura.cobertura().contains(extremo),
                "Um poste no canto deve alimentar todo o pergolado aprovado, inclusive o canto oposto");
        ServerPlayer player = jogador(helper, extremo);
        clicar(helper, player, extremo);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "Clicar no canto distante encontra a raiz dona e colhe apenas aquele cacho");
        guardarMao(player);
        clicar(helper, player, extremo);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "O cacho distante não pode ser colhido duas vezes seguidas");
        // Uma extensão maior permanece intacta, mas não aumenta a área de uma raiz.
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) {
            helper.getLevel().setBlockAndUpdate(poste.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
        }
        estrutura = ParreiraEstrutura.buscar(helper.getLevel(), raiz);
        helper.assertTrue(estrutura.cobertura().size() <= 25,
                "Ampliar o pergolado para seis por seis não amplia o limite de uma raiz");
        int minX = estrutura.cobertura().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = estrutura.cobertura().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = estrutura.cobertura().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = estrutura.cobertura().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        helper.assertTrue(maxX - minX <= 4 && maxZ - minZ <= 4,
                "A área de uma única parreira cabe em cinco posições por eixo");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void chunkAntigoCriaEntidadeDaParreiraSemAlterarPlantaNemApoios(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaCompleta(helper, raiz, Blocks.SPRUCE_FENCE);
        var level = helper.getLevel();
        var chunk = level.getChunkAt(raiz);
        BlockState antiga = level.getBlockState(raiz);
        Map<BlockPos, BlockState> apoios = new LinkedHashMap<>();
        for (BlockPos pos : ParreiraEstrutura.buscar(level, raiz).apoios()) {
            apoios.put(pos, level.getBlockState(pos));
        }
        // Um chunk salvo antes da parreira tem o bloco e age/uv_age, mas não
        // possui NBT nem ticker de BlockEntity. Consultar o mapa não a recria.
        level.removeBlockEntity(raiz);
        helper.assertFalse(chunk.getBlockEntities().containsKey(raiz),
                "O cenário antigo começa com sua planta madura e sem BlockEntity");
        UvaParreiraBlock.migrarChunk(level, chunk, false);
        helper.assertTrue(chunk.getBlockEntities().get(raiz) instanceof ParreiraBlockEntity,
                "Carregar um chunk antigo cria a BlockEntity antes do primeiro clique");
        ParreiraBlockEntity migrada = (ParreiraBlockEntity) chunk.getBlockEntities().get(raiz);
        helper.assertTrue(migrada.ticksRebrotaRestantes() == 0 && migrada.frutosProntos(),
                "A migração respeita a primeira colheita de uma planta já madura");
        helper.assertTrue(level.getBlockState(raiz).equals(antiga),
                "Migrar a entidade não altera o ID, idade nem maturação UV da planta");
        for (var apoio : apoios.entrySet()) {
            helper.assertTrue(level.getBlockState(apoio.getKey()).equals(apoio.getValue()),
                    "A migração preserva cada cerca já construída pelo jogador");
        }
        UvaParreiraBlock.migrarChunk(level, chunk, false);
        helper.assertTrue(chunk.getBlockEntities().get(raiz) == migrada,
                "Repetir o evento de carga reutiliza a entidade e não reinicia nem duplica a raiz");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void duasRaizesCompartilhamDonoDoCachoSemDuplicarOFruto(GameTestHelper helper) {
        BlockPos raizA = preparar(helper, 4, 3);
        estruturaCompleta(helper, raizA, Blocks.OAK_FENCE);
        BlockPos raizB = raizA.east(2);
        helper.getLevel().setBlockAndUpdate(raizB, planta(4, 3));
        manter(helper, raizA, 20);
        manter(helper, raizB, 20);
        BlockPos comum = raizA.east().above(2);
        BlockPos dona = raizA.asLong() < raizB.asLong() ? raizA : raizB;
        BlockPos outra = dona.equals(raizA) ? raizB : raizA;
        helper.assertTrue(dona.equals(ParreiraEstrutura.donoDoApoio(helper.getLevel(), comum)),
                "O apoio igualmente distante escolhe a mesma dona antes e depois da colheita");
        ServerPlayer player = jogador(helper, comum);
        clicar(helper, player, comum);
        helper.assertTrue(quantidade(helper, player, raizA, IntoxicantesMod.UVA) == 1,
                "Duas raízes não desenham nem entregam dois frutos no mesmo apoio");
        helper.assertTrue(!entidade(helper, dona).cachoPronto(comum)
                        && !entidade(helper, outra).cachoPronto(comum),
                "Só a dona guarda a recuperação, sem trocar a propriedade para a outra raiz");
        clicar(helper, player, comum);
        entidade(helper, outra).colher(helper.getLevel(), player, comum);
        helper.assertTrue(quantidade(helper, player, raizA, IntoxicantesMod.UVA) == 1
                        && dona.equals(ParreiraEstrutura.donoDoApoio(helper.getLevel(), comum)),
                "Nem clique duplicado nem chamada pela outra raiz cria a segunda uva");
        helper.assertTrue(helper.getLevel().getBlockState(dona).getValue(UvCropBlock.AGE) == 4
                        && helper.getLevel().getBlockState(outra).getValue(UvCropBlock.AGE) == 4,
                "Colher um cacho preserva a folhagem estabelecida das duas raízes");
        helper.succeed();
    }

    private static void estruturaDezCachos(GameTestHelper helper, BlockPos raiz) {
        var level = helper.getLevel();
        BlockPos poste = raiz.east();
        for (int y = 0; y < 3; y++) level.setBlockAndUpdate(poste.above(y), Blocks.OAK_FENCE.defaultBlockState());
        for (int x = 0; x < 5; x++) for (int z = 0; z < 2; z++) {
            level.setBlockAndUpdate(poste.above(2).offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
        }
        manter(helper, raiz, 20);
    }

    @GameTest(padding = 8)
    public void temposDeDoisCachosNaoReiniciamUmAoColherOutro(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        ServerPlayer player = jogador(helper, raiz);
        BlockPos primeiro = raiz.east().above(2);
        BlockPos segundo = primeiro.east();
        clicar(helper, player, primeiro);
        manter(helper, raiz, 40);
        int antes = entidade(helper, raiz).estadoCacho(primeiro).ticksRebrota();
        clicar(helper, player, segundo);
        helper.assertTrue(entidade(helper, raiz).estadoCacho(primeiro).ticksRebrota() == antes,
                "Colher um segundo cacho não reinicia a recuperação do primeiro");
        manter(helper, raiz, antes);
        helper.assertTrue(entidade(helper, raiz).cachoPronto(primeiro)
                        && !entidade(helper, raiz).cachoPronto(segundo),
                "O primeiro cacho volta antes do segundo conforme a hora de cada colheita");
        manter(helper, raiz, 40);
        helper.assertTrue(entidade(helper, raiz).cachoPronto(segundo),
                "O segundo cacho completa seu próprio intervalo");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void novoApoioPrecisaCrescerSemReposicaoInstantanea(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos novo = raiz.east().above(2).south(2);
        helper.getLevel().setBlockAndUpdate(novo, Blocks.OAK_FENCE.defaultBlockState());
        manter(helper, raiz, 20);
        var be = entidade(helper, raiz);
        helper.assertTrue(be.estadoCacho(novo) != null
                        && be.estadoCacho(novo).ticksRebrota() == ParreiraBlockEntity.TEMPO_REBROTA,
                "Uma expansão recebe um slot novo, com os sessenta segundos completos de crescimento");
        ServerPlayer player = jogador(helper, novo);
        clicar(helper, player, novo);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Expandir a cobertura de uma raiz madura não entrega uvas instantâneas");
        manter(helper, raiz, ParreiraBlockEntity.TEMPO_REBROTA - 20);
        helper.assertFalse(be.cachoPronto(novo), "O novo cacho respeita o último segundo de crescimento");
        manter(helper, raiz, 20);
        clicar(helper, player, novo);
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "A nova posição produz sua primeira uva depois do crescimento real");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void hitboxNativaColheUmCachoSemRecolherOsVizinhos(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos apoio = raiz.east().above(2);
        var entidades = helper.getLevel().getEntitiesOfClass(CachoParreiraEntity.class,
                new AABB(raiz).inflate(7), entity -> entity.raiz().equals(raiz));
        helper.assertTrue(entidades.size() == 10, "Os dez cachos visíveis têm dez hitboxes reais");
        var alvo = entidades.stream().filter(entity -> entity.apoio().equals(apoio)).findFirst().orElseThrow();
        helper.assertTrue(alvo.isPickable() && !alvo.shouldBeSaved() && !alvo.isPushable(),
                "A hitbox pode ser clicada, não empurra o jogador e não duplica o save do fruto");
        ServerPlayer player = jogador(helper, apoio);
        alvo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        alvo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(alvo.isRemoved() && quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1
                        && entidade(helper, raiz).frutosRestantes() == 9,
                "A interação normal da entidade remove só um cacho e entrega uma única uva");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void migracaoDoTimerGlobalAntigoNaoLiberaCachosAntesDaHora(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 2, 0);
        estruturaDezCachos(helper, raiz);
        var dados = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var legado = dados.child("parreira");
        legado.putInt("etapa_visual", 4);
        legado.putInt("rebrota", 600);
        var original = entidade(helper, raiz);
        original.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), dados.buildResult()));
        manter(helper, raiz, 20);
        helper.assertTrue(original.cachosClient().size() == 10
                        && original.cachosClient().values().stream().allMatch(cacho -> cacho.ticksRebrota() == 580),
                "Save antigo reparte os seiscentos ticks de espera, preservando o tempo já decorrido");
        helper.getLevel().setBlock(raiz, planta(4, 0), Block.UPDATE_CLIENTS);
        ServerPlayer player = jogador(helper, raiz);
        clicar(helper, player, raiz.east().above(2));
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 0,
                "Nem idade madura forçada libera os cachos migrados antes do timer antigo");
        manter(helper, raiz, 580);
        clicar(helper, player, raiz.east().above(2));
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 1,
                "O cacho migrado completa a espera anterior e passa a colher individualmente");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void inventarioCheioSoltaUmaUnidadeSemDuplicarCacho(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos apoio = raiz.east().above(2);
        ServerPlayer player = jogador(helper, apoio);
        // O mock vanilla nasce criativo. Inventory.add apaga sobras no criativo
        // cheio; esta regressão verifica a entrega de itens no sobrevivência.
        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(player.hasInfiniteMaterials(), "O inventário cheio do teste está no sobrevivência");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STICK, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.UVA, 64));
        clicar(helper, player, apoio);
        clicar(helper, player, apoio);
        int inventario = player.getInventory().countItem(IntoxicantesMod.UVA);
        int total = quantidade(helper, player, raiz, IntoxicantesMod.UVA);
        helper.assertTrue(inventario == 64 && total == 65,
                "Inventário cheio preserva 64 uvas e solta uma nova: inventário=" + inventario + ", total=" + total);
        helper.assertTrue(entidade(helper, raiz).frutosRestantes() == 9,
                "Um cacho recolhido com inventário cheio continua colhido e não duplica seu drop");
        helper.succeed();
    }

    @GameTest(padding = 8)
    public void raizArrancadaNaoIncluiCachosDeApoiosPodados(GameTestHelper helper) {
        BlockPos raiz = preparar(helper, 4, 3);
        estruturaDezCachos(helper, raiz);
        BlockPos podado = raiz.east().above(2).east(4).south();
        helper.getLevel().destroyBlock(podado, false);
        // Arrancar antes da próxima manutenção também precisa respeitar a poda.
        ServerPlayer player = jogador(helper, raiz);
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(player.gameMode.destroyBlock(raiz), "O jogador arranca a raiz parcialmente podada");
        helper.assertTrue(quantidade(helper, player, raiz, IntoxicantesMod.UVA) == 9,
                "O loot real considera só os nove cachos ainda conectados, sem tombstones podados");
        helper.succeed();
    }
}
