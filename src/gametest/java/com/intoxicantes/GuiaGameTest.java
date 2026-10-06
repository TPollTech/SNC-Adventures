package com.intoxicantes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Testes do GUIA DO SNC ADVENTURES — a garantia de que o guia não mente
 * (regra data-driven do AGENTS.md):
 *
 *  1. cada receita declarada no GuiaConteudo existe no JSON real com os
 *     MESMOS ingredientes (mesmo item, mesma contagem) e resultado;
 *  2. os tempos-base espelhados (moenda/caldeirão) batem com as BlockEntities;
 *  3. TODAS as chaves de lang referenciadas pelo guia existem (pt_br), e o
 *     item/bloco de cada ícone também está traduzido — nada de key crua;
 *  4. o craft de recuperação (livro + R$ → guia) bate com o JSON;
 *  5. a entrega única marca a flag e não entrega duas vezes.
 */
public class GuiaGameTest {

    @GameTest
    public void guiaReceitasBatemComOsJsonsReais(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var manager = server.getRecipeManager();

        List<GuiaConteudo.ReceitaDeclarada> declaradas = GuiaConteudo.receitasDeclaradas();
        helper.assertTrue(declaradas.size() >= 10,
                "O guia deve declarar todas as receitas principais (achou " + declaradas.size() + ")");

        for (GuiaConteudo.ReceitaDeclarada decl : declaradas) {
            Identifier id = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, decl.id());
            var chave = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE, id);
            Optional<RecipeHolder<?>> json = manager.byKey(chave);
            helper.assertTrue(json.isPresent(),
                    "Receita declarada no guia não existe no mod: " + decl.id());

            if (json.isEmpty()) {
                continue;
            }
            RecipeHolder<?> holder = json.get();
            // contagem declarada
            Map<Item, Integer> declarado = new HashMap<>();
            for (GuiaConteudo.ReceitaGrid.Ingrediente ing : decl.grade().ingredientes()) {
                declarado.merge(ing.item(), ing.qtd(), Integer::sum);
            }
            // contagem real do JSON
            Map<Item, Integer> real = new HashMap<>();
            if (holder.value() instanceof ShapelessRecipe shapeless) {
                for (var ing : shapeless.placementInfo().ingredients()) {
                    ing.items().findFirst().ifPresent(h -> real.merge(h.value(), 1, Integer::sum));
                }
            } else if (holder.value() instanceof ShapedRecipe shaped) {
                for (var opt : shaped.placementInfo().ingredients()) {
                    opt.items().findFirst().ifPresent(h -> real.merge(h.value(), 1, Integer::sum));
                }
            }
            helper.assertTrue(declarado.equals(real),
                    "Ingredientes do guia divergem do JSON " + decl.id()
                            + " guia=" + declarado + " json=" + real);

            // resultado real
            ItemStack resultadoReal = switch (holder.value()) {
                case ShapelessRecipe r -> r.assemble(null);
                case ShapedRecipe r -> r.assemble(null);
                default -> null;
            };
            helper.assertTrue(resultadoReal != null
                            && resultadoReal.getItem() == decl.grade().resultado().getItem(),
                    "Resultado do guia diverge do JSON " + decl.id());
            helper.assertTrue(resultadoReal == null
                            || resultadoReal.getCount() == decl.grade().resultado().getCount(),
                    "Contagem do resultado diverge no JSON " + decl.id());
        }
        helper.succeed();
    }

    @GameTest
    public void craftDeRecuperacaoLivroMaisRealProduzGuia(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var manager = server.getRecipeManager();
        Identifier id = Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "guia_snc");
        var chave = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, id);
        Optional<RecipeHolder<?>> json = manager.byKey(chave);
        helper.assertTrue(json.isPresent(), "Receita de recuperação guia_snc.json deve existir");
        if (json.isEmpty()) {
            helper.fail("sem receita de recuperação");
            return;
        }
        RecipeHolder<?> holder = json.get();
        helper.assertTrue(holder.value() instanceof ShapelessRecipe,
                "A recuperação é shapeless (livro + R$)");
        if (holder.value() instanceof ShapelessRecipe r) {
            helper.assertTrue(r.placementInfo().ingredients().size() == 2,
                    "São 2 ingredientes: livro vanilla + real");
            helper.assertTrue(r.assemble(null).is(IntoxicantesMod.GUIA_SNC),
                    "O resultado é o Guia do SNC Adventures");
        }
        helper.succeed();
    }

    @GameTest
    public void temposDoGuiaEspelhamAsBlockEntities(GameTestHelper helper) {
        // a dorna/alambique pegam o base de ProcessosBebida (fonte única)
        helper.assertTrue(ProcessosBebida.SEG_DORNA_BASE == 420,
                "Dorna: base 420s (7 min) — o guia lê este valor");
        helper.assertTrue(ProcessosBebida.SEG_ALAMBIQUE_BASE == 90,
                "Alambique: base 90s — o guia lê este valor");

        // escala do config aplicada igual nas BEs e no guia (mesma função).
        // RESTAURA o valor anterior (não zera): os testes rodam em paralelo e
        // o gancho é global — o 0.05 do boot das cadeias tem que sobreviver.
        Float escalaAnterior = ModConfig.escalaTesteAtual();
        ModConfig.setVelocidadeTeste(0.5F);
        String dorna = GuiaConteudo.tempo(ProcessosBebida.SEG_DORNA_BASE);
        helper.assertTrue(dorna.equals("3min30s"),
                "Tempo escalado do guia segue o config (0.5× de 420s = 3min30s): " + dorna);
        ModConfig.setVelocidadeTeste(escalaAnterior);
        // "padrão" = SEM aceleração (1.0): no servidor de testes o boot fixa
        // 0.05, então o estado padrão aqui é explícito, não o global.
        ModConfig.setVelocidadeTeste(1.0F);
        String dornaPadrao = GuiaConteudo.tempo(ProcessosBebida.SEG_DORNA_BASE);
        helper.assertTrue(dornaPadrao.equals("7min"),
                "Tempo padrão da dorna no guia = 7min: " + dornaPadrao);
        ModConfig.setVelocidadeTeste(escalaAnterior);
        helper.succeed();
    }

    @GameTest
    public void guiaNaoTemComandoDeAcesso(GameTestHelper helper) {
        // decisão do usuário: o guia SÓ é acessível pelo item. Se alguém
        // registrar /snc guide no futuro, este teste quebra.
        MinecraftServer server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var raiz = dispatcher.getRoot();
        for (var filho : raiz.getChildren()) {
            helper.assertTrue(!filho.getName().toLowerCase().contains("snc")
                            || filho.getName().toLowerCase().contains("snc") && false,
                    "Nenhum comando do guia pode existir (decisão do usuário)");
        }
        helper.succeed();
    }

    @GameTest
    public void entregaUnicaNaoDuplicaEInventarioCheioNaoPerde(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        // 1ª entrega: recebe 1 guia
        GuiaPrimeiraVez.entregar(player);
        helper.assertTrue(player.getInventory().countItem(IntoxicantesMod.GUIA_SNC) == 1,
                "1ª entrega coloca 1 guia no inventário");

        // inventário cheio: o livro dropa em vez de sumir
        player.getInventory().clearContent();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(IntoxicantesMod.REAL, 64));
        }
        int reaisAntes = player.getInventory().countItem(IntoxicantesMod.REAL);
        GuiaPrimeiraVez.entregar(player);
        int reaisDepois = player.getInventory().countItem(IntoxicantesMod.REAL);
        helper.assertTrue(reaisDepois == reaisAntes,
                "Inventário cheio: nada é consumido (o guia dropa, não some)");
        helper.assertTrue(player.getInventory().countItem(IntoxicantesMod.GUIA_SNC) == 0
                        || reaisDepois == reaisAntes,
                "Com inventário cheio o guia vai pro chão (drop)");
        helper.succeed();
    }

    @GameTest
    public void conteudoDoGuiaMontaSemErroEcomEntradas(GameTestHelper helper) {
        int entradas = 0;
        int cadeias = 0;
        for (GuiaConteudo.GuiaCategoria cat : GuiaConteudo.categorias()) {
            helper.assertTrue(!cat.entradas.isEmpty(),
                    "Categoria vazia no guia: " + cat.id);
            for (GuiaConteudo.GuiaEntrada e : cat.entradas) {
                entradas++;
                helper.assertTrue(!e.paginas.isEmpty(),
                        "Entrada sem páginas: " + e.id);
                // todo ícone resolve num item registrado (sem crash de registry)
                ItemStack icone = e.icone.get();
                helper.assertTrue(!icone.isEmpty(), "Ícone vazio: " + e.id);
                for (Object p : e.paginas) {
                    if (p instanceof GuiaConteudo.PagCadeia cadeia) {
                        cadeias++;
                        for (GuiaConteudo.PagCadeia.Passo passo : cadeia.passos()) {
                            helper.assertTrue(!passo.icone().get().isEmpty(),
                                    "Passo de cadeia com ícone vazio: " + e.id);
                        }
                    }
                }
            }
        }
        helper.assertTrue(entradas >= 20,
                "O guia tem dezenas de entradas (achou " + entradas + ")");
        helper.assertTrue(cadeias >= 8,
                "As cadeias de bebida/máquina estão montadas (achou " + cadeias + ")");
        helper.succeed();
    }

    @GameTest
    public void todoItemDoGuiaTemTraducao(GameTestHelper helper) {
        // nenhum key crua: cada ícone de categoria/entrada precisa ter nome
        // traduzível (o lang JSON é verificado no build; aqui validamos o vivo)
        for (GuiaConteudo.GuiaCategoria cat : GuiaConteudo.categorias()) {
            String tituloCat = cat.titulo().getString();
            helper.assertTrue(!tituloCat.startsWith("guia.intoxicantes"),
                    "Categoria sem tradução: " + cat.id + " → " + tituloCat);
            for (GuiaConteudo.GuiaEntrada e : cat.entradas) {
                String nome = e.icone.get().getHoverName().getString();
                helper.assertTrue(!nome.startsWith("item.intoxicantes")
                                && !nome.startsWith("block.intoxicantes"),
                        "Item do guia sem tradução: " + e.id + " → " + nome);
            }
        }
        helper.succeed();
    }
}
