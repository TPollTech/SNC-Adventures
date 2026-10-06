package com.intoxicantes;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Regressões do objeto colocado; execute somente após a aprovação visual. */
public class BebidasDecorativasGameTest {
    private static final BlockPos SUPORTE = new BlockPos(1, 1, 1);

    @GameTest
    public void oitoBebidasColocamUmaUnidadeERecolhemSemConsumir(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        Player player = jogador(helper, GameType.SURVIVAL);
        List<Item> bebidas = List.of(IntoxicantesMod.CERVEJA, IntoxicantesMod.VINHO,
                IntoxicantesMod.CACHACA, IntoxicantesMod.HIDROMEL, IntoxicantesMod.RUM,
                IntoxicantesMod.SUCO_DETOX, IntoxicantesMod.AGUA_DE_COCO, IntoxicantesMod.CHA_LUPULO);

        for (Item item : bebidas) {
            ItemStack original = personalizada(item, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 1.0, Direction.UP)
                    .consumesAction(), "Toda bebida pronta pode ser colocada");
            helper.assertTrue(player.getMainHandItem().getCount() == 2,
                    "Colocar retira exatamente uma unidade da mão");
            BebidaDecorativaEntity garrafa = unica(helper);
            helper.assertTrue(ItemStack.matches(garrafa.getBebida(), original.copyWithCount(1)),
                    "A garrafa conserva item, nome e componentes próprios");
            helper.assertTrue(player.interactOn(garrafa, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(),
                    "Mão secundária vazia recolhe a bebida");
            helper.assertTrue(ItemStack.matches(player.getOffhandItem(), original.copyWithCount(1)),
                    "O item recolhido é a mesma bebida completa");
            helper.assertTrue(garrafa.isRemoved(), "Recolhimento remove a decoração");

            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertFalse(player.interactOn(garrafa, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(),
                    "Uma segunda interação no mesmo objeto não entrega outra bebida");
            helper.assertTrue(player.getOffhandItem().isEmpty(), "Mão permanece vazia após interação repetida");
            helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                    "Colocar e recolher não geram garrafa vazia");
            helper.assertTrue(player.getActiveEffects().isEmpty(), "Decoração não aplica efeitos de consumo");
        }
        helper.succeed();
    }

    @GameTest
    public void alimentosExpostosRecolhemQuantidadeEComponentesIntactos(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        Player player = jogador(helper, GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(8);
        player.getFoodData().setSaturation(0.0F);
        for (Item item : alimentos()) {
            ItemStack original = personalizada(item, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP)
                    .consumesAction(), "Pão, coco e uva podem ser expostos sobre a laje");
            helper.assertTrue(ItemStack.matches(player.getMainHandItem(), original.copyWithCount(2)),
                    "Expor retira uma unidade e conserva os componentes da pilha restante");
            BebidaDecorativaEntity alimento = unica(helper);
            helper.assertTrue(ItemStack.matches(alimento.getBebida(), original.copyWithCount(1)),
                    "O objeto exposto conserva nome e componentes do alimento");
            helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP)
                    .consumesAction(), "Outro alimento não pode ocupar o objeto já exposto");
            helper.assertTrue(player.getMainHandItem().getCount() == 2, "Recusa não gasta outra unidade");
            helper.assertTrue(player.interactOn(alimento, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(),
                    "Mão secundária vazia recolhe o alimento");
            helper.assertTrue(ItemStack.matches(player.getOffhandItem(), original.copyWithCount(1)),
                    "Recolher devolve a unidade original, com todos os componentes");
            helper.assertTrue(player.getMainHandItem().getCount() + player.getOffhandItem().getCount() == 3,
                    "Colocação e recolhimento conservam as três unidades iniciais");
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertFalse(player.interactOn(alimento, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(),
                    "Interação repetida no objeto removido não duplica alimento");
            helper.assertTrue(player.getOffhandItem().isEmpty() && garrafas(helper).isEmpty()
                    && drops(helper).isEmpty(), "Não sobra uma segunda unidade no mundo nem na mão");
            helper.assertTrue(player.getFoodData().getFoodLevel() == 8
                    && player.getFoodData().getSaturationLevel() == 0.0F,
                    "Expor e recolher não alimentam o jogador");
            helper.assertTrue(player.getActiveEffects().isEmpty(), "Exposição não aplica efeitos de consumo");
            helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0
                    && player.getInventory().countItem(Items.BOWL) == 0,
                    "Expor comida não gera recipientes de bebida ou sopa");
        }
        helper.succeed();
    }

    @GameTest
    public void alimentosCabemSobTetoBaixoEMantemBaseNaSuperficie(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        helper.setBlock(SUPORTE.above(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, false));
        Player player = jogador(helper, GameType.SURVIVAL);
        player.setYRot(45.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.VINHO));
        helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                "O alçapão superior recusa a caixa alta da garrafa");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Teto preserva a bebida recusada");

        double[] larguras = new double[3];
        int indice = 0;
        for (Item item : alimentos()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                    "Alimento baixo cabe onde a caixa alta da garrafa não cabe");
            BebidaDecorativaEntity alimento = unica(helper);
            double topo = helper.absolutePos(SUPORTE).getY() + 1.0;
            helper.assertTrue(Math.abs(alimento.getY() - topo) < 0.00001
                    && Math.abs(alimento.getBoundingBox().minY - topo) < 0.00001,
                    "Base da entidade e da caixa coincide com a superfície real");
            helper.assertTrue(alimento.temApoio() && alimento.temEspaco(),
                    "Alimento tem apoio e espaço sob o teto, inclusive a 45 graus");
            larguras[indice++] = alimento.getBoundingBox().getXsize();
            helper.assertTrue(player.interactOn(alimento, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                    "Mesmo sob o teto é possível recolher o alimento");
        }
        helper.assertTrue(larguras[0] > larguras[1] && larguras[1] > larguras[2],
                "Pão, coco e cacho têm larguras próprias, coerentes com seus modelos");
        helper.succeed();
    }

    @GameTest
    public void apoioEstreitoAceitaCachoERecusaAlimentosMaiores(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.OAK_FENCE);
        Player player = jogador(helper, GameType.SURVIVAL);
        for (Item item : List.of(IntoxicantesMod.PAO_CEVADA, IntoxicantesMod.COCO_FRUTO)) {
            ItemStack original = personalizada(item, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 1.5, Direction.UP)
                    .consumesAction(), "Pão e coco precisam de uma base maior que o topo da cerca");
            helper.assertTrue(ItemStack.matches(player.getMainHandItem(), original),
                    "Falta de apoio preserva a pilha inteira e seus componentes");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, personalizada(IntoxicantesMod.UVA, 2));
        helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 1.5, Direction.UP).consumesAction(),
                "A base pequena do cacho cabe no topo estreito da cerca");
        BebidaDecorativaEntity cacho = unica(helper);
        helper.assertTrue(cacho.temApoio(), "Toda a base do cacho possui sustentação");
        helper.assertTrue(Math.abs(cacho.getY() - helper.absolutePos(SUPORTE).getY() - 1.5) < 0.00001,
                "Cacho acompanha a altura real da cerca");
        helper.succeed();
    }

    @GameTest
    public void uvaContinuaIngredienteSemConsumo(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        Player player = jogador(helper, GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.getFoodData().setFoodLevel(8);
        ItemStack original = personalizada(IntoxicantesMod.UVA, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
        helper.assertTrue(original.get(DataComponents.FOOD) == null
                && original.get(DataComponents.CONSUMABLE) == null,
                "Cacho exposto continua ingrediente, sem comida ou componente de consumo");
        helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 1.0, Direction.UP)
                == InteractionResult.PASS, "Uso normal da uva não coloca nem inicia consumo");
        helper.assertFalse(player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                .consumesAction(), "Usar uva com fome não passa a comer o ingrediente");
        helper.assertTrue(player.getUseItem().isEmpty()
                && ItemStack.matches(player.getMainHandItem(), original),
                "Uva não inicia animação nem consome unidade ao usar normalmente");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 8 && garrafas(helper).isEmpty(),
                "Uso normal mantém fome e não cria decoração");
        helper.succeed();
    }

    @GameTest
    public void comerPaoECocoPreservaFomeSaturacaoEHidratacao(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        try {
            for (Item item : List.of(IntoxicantesMod.PAO_CEVADA, IntoxicantesMod.COCO_FRUTO)) {
                SaudeSystem.limparSessaoTeste(player);
                SaudeData.alterarHidratacao(player, -SaudeData.HIDRATACAO_MAX);
                player.getFoodData().setFoodLevel(8);
                player.getFoodData().setSaturation(0.0F);
                ItemStack original = personalizada(item, 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
                helper.assertTrue(player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                        .consumesAction(), "Uso normal continua iniciando o consumo do alimento");
                player.stopUsingItem();
                helper.assertTrue(ItemStack.matches(player.getMainHandItem(), original)
                        && player.getFoodData().getFoodLevel() == 8 && SaudeData.hidratacao(player) == 0,
                        "Interromper a mordida não consome, alimenta ou hidrata");

                helper.assertTrue(player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                        .consumesAction(), "Após interromper ainda é possível consumir normalmente");
                ItemStack restante = player.getMainHandItem().finishUsingItem(helper.getLevel(), player);
                player.setItemInHand(InteractionHand.MAIN_HAND, restante);
                player.stopUsingItem();
                helper.assertTrue(ItemStack.matches(restante, original.copyWithCount(2)),
                        "Concluir o consumo retira uma unidade e preserva os componentes restantes");
                boolean coco = item == IntoxicantesMod.COCO_FRUTO;
                helper.assertTrue(player.getFoodData().getFoodLevel() == (coco ? 10 : 14),
                        "Pão mantém seis pontos de fome; coco mantém dois");
                helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - (coco ? 1.2F : 7.2F)) < 0.00001,
                        "Consumo mantém a saturação original dos dois alimentos");
                // Mesmo hook já usado em SaudeGameTest: prova a regra de saúde
                // após a mordida, sem fingir que este teste valida o detector de ticks.
                SaudeSystem.consumirFonteDeAguaTeste(player, original.copyWithCount(1), 0);
                helper.assertTrue(SaudeData.hidratacao(player) == (coco ? 15 : 0),
                        "Coco mantém quinze pontos de hidratação; pão não hidrata");
                helper.assertTrue(player.getActiveEffects().isEmpty()
                        && player.getInventory().countItem(Items.GLASS_BOTTLE) == 0
                        && player.getInventory().countItem(Items.BOWL) == 0,
                        "Comer não aplica efeitos de bebida nem cria recipientes");

                player.getFoodData().setFoodLevel(20);
                InteractionResult cheio = player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                helper.assertTrue(cheio.consumesAction() == coco,
                        "Coco continua utilizável com fome cheia; pão respeita a fome");
                player.stopUsingItem();
            }
        } finally {
            SaudeSystem.limparSessaoTeste(player);
        }
        helper.succeed();
    }

    @GameTest
    public void lajeMantemAlturaEColocacoesInvalidasNaoGastamItens(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        Player player = jogador(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.CERVEJA, 4));

        player.setShiftKeyDown(false);
        helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP)
                == InteractionResult.PASS, "Uso normal continua disponível para beber");
        helper.assertTrue(garrafas(helper).isEmpty(), "Uso sem agachar não coloca decoração");
        player.setShiftKeyDown(true);
        helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP).consumesAction(),
                "Laje baixa aceita bebida na altura do topo real");
        BebidaDecorativaEntity garrafa = unica(helper);
        double altura = helper.absolutePos(SUPORTE).getY() + 0.5;
        helper.assertTrue(Math.abs(garrafa.getY() - altura) < 0.00001,
                "A garrafa não flutua meio bloco sobre a laje");
        helper.assertTrue(garrafa.temApoio(), "Base inteira possui apoio na laje");
        helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP).consumesAction(),
                "Outra garrafa não pode ocupar o mesmo espaço");
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "Recusa não retira outro item");
        garrafa.discard();

        helper.setBlock(SUPORTE.above(), Blocks.STONE);
        helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP).consumesAction(),
                "Teto que atravessaria o vidro impede a colocação");
        helper.setBlock(SUPORTE.above(), Blocks.AIR);
        helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.NORTH).consumesAction(),
                "A face lateral não serve de suporte");
        helper.setBlock(SUPORTE, Blocks.AIR);
        helper.assertFalse(colocar(helper, player, InteractionHand.MAIN_HAND, 0.5, Direction.UP).consumesAction(),
                "Não é possível colocar no ar");
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "Todas as recusas preservam a pilha");
        helper.succeed();
    }

    @GameTest
    public void gravaERecarregaBebidaCompletaSemResetarApoio(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        Player player = jogador(helper, GameType.SURVIVAL);
        for (Item item : List.of(IntoxicantesMod.VINHO, IntoxicantesMod.PAO_CEVADA,
                IntoxicantesMod.COCO_FRUTO, IntoxicantesMod.UVA)) {
            ItemStack original = personalizada(item, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            helper.assertTrue(colocar(helper, player, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                    "Bebida ou alimento personalizado foi colocado");
            BebidaDecorativaEntity anterior = unica(helper);
            AABB caixaAnterior = anterior.getBoundingBox();
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            anterior.saveWithoutId(output);
            anterior.discard();

            BebidaDecorativaEntity recarregada = new BebidaDecorativaEntity(IntoxicantesMod.BEBIDA_DECORATIVA,
                    helper.getLevel());
            recarregada.load(TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), output.buildResult()));
            helper.assertTrue(helper.getLevel().addFreshEntity(recarregada), "Entidade recarregada entra no mundo");
            helper.assertTrue(ItemStack.matches(recarregada.getBebida(), original), "Save preserva todos os componentes");
            helper.assertTrue(recarregada.getApoio().equals(helper.absolutePos(SUPORTE)), "Save preserva o suporte");
            helper.assertTrue(recarregada.getBoundingBox().equals(caixaAnterior),
                    "Recarga conserva posição e dimensões próprias do objeto");
            helper.assertTrue(recarregada.temApoio(), "Objeto recarregado continua apoiado");
            helper.assertTrue(player.interactOn(recarregada, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                    "Após recarregar ainda é possível recolher");
            helper.assertTrue(ItemStack.matches(player.getMainHandItem(), original), "Recolhimento devolve o item salvo");
        }
        helper.succeed();
    }

    @GameTest
    public void perderApoioDevolveUmaVezMesmoComDanoRepetido(GameTestHelper helper) {
        Player player = jogador(helper, GameType.SURVIVAL);
        for (Item item : List.of(IntoxicantesMod.RUM, IntoxicantesMod.PAO_CEVADA,
                IntoxicantesMod.COCO_FRUTO, IntoxicantesMod.UVA)) {
            helper.setBlock(SUPORTE, Blocks.STONE);
            ItemStack original = personalizada(item, 1);
            player.setItemInHand(InteractionHand.OFF_HAND, original.copy());
            helper.assertTrue(colocar(helper, player, InteractionHand.OFF_HAND, 1.0, Direction.UP).consumesAction(),
                    "Mão secundária também coloca exatamente uma unidade");
            BebidaDecorativaEntity objeto = unica(helper);
            helper.setBlock(SUPORTE, Blocks.AIR);
            for (int i = 0; i < 5; i++) objeto.tick();
            helper.assertTrue(objeto.isRemoved(), "Perder apoio remove a entidade decorativa");
            objeto.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0F);
            objeto.kill(helper.getLevel());
            List<ItemEntity> drops = drops(helper);
            helper.assertTrue(drops.size() == 1, "Apoio, dano e kill repetidos deixam só um drop");
            helper.assertTrue(ItemStack.matches(drops.getFirst().getItem(), original),
                    "O drop conserva bebida ou alimento com todos os componentes");
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest
    public void explosaoEQuebraCriativaTemResultadosDefinidos(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        Player sobrevivente = jogador(helper, GameType.SURVIVAL);
        ItemStack original = personalizada(IntoxicantesMod.HIDROMEL, 1);
        sobrevivente.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
        helper.assertTrue(colocar(helper, sobrevivente, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                "Bebida colocada antes da explosão");
        BebidaDecorativaEntity garrafa = unica(helper);
        var explosao = helper.getLevel().damageSources().explosion(null, null);
        garrafa.hurtServer(helper.getLevel(), explosao, 20.0F);
        garrafa.hurtServer(helper.getLevel(), explosao, 20.0F);
        helper.assertTrue(drops(helper).size() == 1, "Dano repetido de explosão devolve uma única bebida");
        helper.assertTrue(ItemStack.matches(drops(helper).getFirst().getItem(), original),
                "Explosão conserva o item cheio, sem gerar recipiente extra");
        drops(helper).forEach(ItemEntity::discard);

        Player criativo = jogador(helper, GameType.CREATIVE);
        criativo.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.HIDROMEL, 3));
        helper.assertTrue(colocar(helper, criativo, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                "Criativo também permite decorar");
        helper.assertTrue(criativo.getMainHandItem().getCount() == 3, "Criativo mantém a pilha da mão");
        BebidaDecorativaEntity criativa = unica(helper);
        criativa.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(criativo), 1.0F);
        helper.assertTrue(criativa.isRemoved(), "Ataque do criativo quebra a decoração");
        helper.assertTrue(drops(helper).isEmpty(), "Quebra no criativo não produz drop extra");
        helper.succeed();
    }

    @GameTest
    public void espectadorEAventuraNaoAlteramBebidas(GameTestHelper helper) {
        helper.setBlock(SUPORTE, Blocks.STONE);
        Player sobrevivente = jogador(helper, GameType.SURVIVAL);
        sobrevivente.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.AGUA_DE_COCO));
        helper.assertTrue(colocar(helper, sobrevivente, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                "Sobrevivente colocou a bebida");
        BebidaDecorativaEntity garrafa = unica(helper);
        for (GameType modo : List.of(GameType.SPECTATOR, GameType.ADVENTURE)) {
            Player restrito = jogador(helper, modo);
            helper.assertFalse(garrafa.interact(restrito, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                    "Modo restrito não recolhe decoração");
            helper.assertFalse(garrafa.hurtServer(helper.getLevel(),
                    helper.getLevel().damageSources().playerAttack(restrito), 1.0F), "Modo restrito não quebra bebida");
            restrito.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IntoxicantesMod.CERVEJA, 2));
            helper.assertFalse(colocar(helper, restrito, InteractionHand.MAIN_HAND, 1.0, Direction.UP).consumesAction(),
                    "Modo restrito não coloca decoração");
            helper.assertTrue(restrito.getMainHandItem().getCount() == 2, "Recusa preserva inventário");
        }
        helper.assertFalse(garrafa.isRemoved(), "A bebida original continua presente");
        helper.succeed();
    }

    private static List<Item> alimentos() {
        return List.of(IntoxicantesMod.PAO_CEVADA, IntoxicantesMod.COCO_FRUTO, IntoxicantesMod.UVA);
    }

    private static Player jogador(GameTestHelper helper, GameType modo) {
        Player player = helper.makeMockPlayer(modo);
        modo.updatePlayerAbilities(player.getAbilities());
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 0.25)));
        player.setShiftKeyDown(true);
        return player;
    }

    private static InteractionResult colocar(GameTestHelper helper, Player player, InteractionHand hand,
            double alturaLocal, Direction face) {
        BlockPos apoio = helper.absolutePos(SUPORTE);
        Vec3 click = new Vec3(apoio.getX() + 0.5, apoio.getY() + alturaLocal, apoio.getZ() + 0.5);
        return player.getItemInHand(hand).useOn(new UseOnContext(player, hand,
                new BlockHitResult(click, face, apoio, false)));
    }

    private static ItemStack personalizada(Item item, int quantidade) {
        ItemStack stack = new ItemStack(item, quantidade);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Reserva da mesa"));
        CompoundTag dados = new CompoundTag();
        dados.putString("lote", "mesa-42");
        dados.putInt("qualidade", 73);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(dados));
        return stack;
    }

    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(SUPORTE)).inflate(2.0);
    }

    private static List<BebidaDecorativaEntity> garrafas(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BebidaDecorativaEntity.class, area(helper), e -> !e.isRemoved());
    }

    private static BebidaDecorativaEntity unica(GameTestHelper helper) {
        List<BebidaDecorativaEntity> garrafas = garrafas(helper);
        helper.assertTrue(garrafas.size() == 1, "Exatamente uma garrafa decorativa ocupa a superfície");
        return garrafas.getFirst();
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper), e -> !e.isRemoved());
    }
}
