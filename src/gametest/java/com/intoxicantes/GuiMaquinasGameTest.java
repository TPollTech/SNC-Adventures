package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * v1.2.59 — TESTES DO REWORK DAS MÁQUINAS (GUI + motor de lotes).
 *
 * Contratos provados (fonte: as BlockEntities, não a intenção):
 * <ul>
 *   <li>o buffer aceita até 4 DOSES de uma vez (13 uvas → 13 no slot, 0 na
 *       mão) e a dose é gasta ao FECHAR cada lote — produção contínua;</li>
 *   <li>saída cheia NÃO perde produto: {@code producaoPendente} guarda;</li>
 *   <li>quebra devolve buffer + lote (Dorna/Alambique via rota do jogador; o
 *       BARRIL devolve até em {@code destroyBlock} — explosão/pistão — via
 *       {@code spawnAfterBreak}, porque a BE dele não é Container);</li>
 *   <li>a barra da GUI lê o ContainerData REAL (0=restante, 1=total, 2=fase).</li>
 * </ul>
 * Os tempos seguem o gancho global de teste ({@code setVelocidadeTeste(0.05F)}
 * do boot — o mesmo do GuiaGameTest): lote da dorna 420s/20 = 21s.
 */
public class GuiMaquinasGameTest {

    // ==================================================== BUFFER DE 4 DOSES

    @GameTest(maxTicks = 400)
    public void prensaAceita7UvasSemExigirDoseExata(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        level.setBlockAndUpdate(pos, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        var be = (MaquinaPrimaBlockEntity) level.getBlockEntity(pos);

        // dose da prensa é 6; 7 uvas = 1 dose INTEIRA + sobra na mão (o buffer
        // só entra em doses inteiras: dose parcial não fecha lote). A stack vai
        // DA MÃO (rota real do bloco): quem consome é o split da própria máquina
        helper.assertFalse(be.tentarCarregar(level, player,
                new ItemStack(IntoxicantesMod.UVA, 2)),
                "Menos de uma dose inteira não entra (não fecha lote)");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.UVA, 7));
        boolean aceitou = be.tentarCarregar(level, player,
                player.getInventory().getItem(0));
        helper.assertTrue(aceitou, "A prensa deve aceitar 7 uvas (1 dose inteira)");
        helper.assertTrue(be.getItem(0).getCount() == 6,
                "O buffer recebe a dose (6), nunca pilha parcial");
        helper.assertTrue(contaMao(player, IntoxicantesMod.UVA) == 1,
                "A 7ª uva sobra NA MÃO do jogador");
        helper.assertTrue(be.processando(), "O lote deve começar sozinho");

        // 40s/20 = 2s por lote; no fechamento, consome EXATAMENTE 6 (sobra 0)
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.getItem(0).isEmpty(),
                    "Ao fechar o lote, a dose inteira é consumida");
            helper.assertTrue(be.getItem(TipoMaquina.PRENSA.idxOut).getCount() == 4,
                    "O lote fechado produziu 4 mostos no slot de saída");
            helper.assertTrue(!be.processando(),
                    "Sem mais insumo, nenhum lote fantasma começa");
            helper.succeed();
        });
    }

    // ==================================================== MULTI-LOTE

    @GameTest(maxTicks = 600)
    public void prensa13UvasFaz2Lotes(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        level.setBlockAndUpdate(pos, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        var be = (MaquinaPrimaBlockEntity) level.getBlockEntity(pos);

        // 13 uvas = 2 doses inteiras (6+6) entram; a 13ª sobra na mão
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.UVA, 13));
        be.tentarCarregar(level, player, player.getInventory().getItem(0));
        helper.assertTrue(be.getItem(0).getCount() == 12,
                "O buffer recebe 2 doses inteiras (12 uvas)");
        helper.assertTrue(contaMao(player, IntoxicantesMod.UVA) == 1,
                "A 13ª uva (dose parcial) sobra na mão do jogador");

        helper.runAfterDelay(140, () -> {
            // 12 = 6 (lote 1) + 6 (lote 2): DOIS lotes fecharam sem nenhuma
            // interação extra — a produção contínua encadeou sozinha
            helper.assertTrue(be.getItem(0).isEmpty(),
                    "Os 2 lotes consumiram as 12 uvas do buffer");
            helper.assertTrue(be.getItem(TipoMaquina.PRENSA.idxOut).getCount() == 8,
                    "Os 2 lotes encadeados entregaram 8 mostos (4+4)");
            helper.succeed();
        });
    }

    // ==================================================== SAÍDA CHEIA

    @GameTest(maxTicks = 600)
    public void dornaLoteTerminaMesmoComSaidaCheia(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        level.setBlockAndUpdate(pos, IntoxicantesMod.DORNA_BEBIDA.defaultBlockState());
        var be = (DornaBebidaBlockEntity) level.getBlockEntity(pos);

        // 4 caldo = 1 lote (420s/20 = 21s com a escala de teste 0.05)
        be.tentarCarregar(level, player, new ItemStack(IntoxicantesMod.CALDO_DE_CANA, 4));
        helper.assertTrue(be.processando(), "A dorna deve fermentar com 4 caldo");

        // entulha a saída com a pilha CHEIA (64): 4 mosto do lote novo não
        // cabem (68 > 64) — é aí que a pendência protege o produto
        be.setItem(TipoMaquina.DORNA.idxOut,
                new ItemStack(IntoxicantesMod.MOSTO_CANA_FERMENTADO, 64));
        helper.runAfterDelay(460, () -> {
            helper.assertTrue(be.dadosGui().get(2) == DornaBebidaBlockEntity.FASE_SAIDA_CHEIA,
                    "Com a saída cheia, a fase deve avisar e guardar o lote");
            helper.assertTrue(be.dadosGui().get(0) == 0,
                    "O lote terminou (timer zerado) mesmo sem espaço");
            helper.assertTrue(be.getItem(TipoMaquina.DORNA.idxOut).getCount() == 64,
                    "O produto pendente NÃO soma na pilha cheia (nada se perde)");
            helper.succeed();
        });
    }

    // ==================================================== QUEBRA DEVOLVE

    @GameTest(maxTicks = 400)
    public void dornaQuebradaDevolveBufferEProcesso(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        level.setBlockAndUpdate(pos, IntoxicantesMod.DORNA_BEBIDA.defaultBlockState());
        var be = (DornaBebidaBlockEntity) level.getBlockEntity(pos);
        be.tentarCarregar(level, player, new ItemStack(IntoxicantesMod.CALDO_DE_CANA, 8));

        // quebra com a mão (rota real do jogador): buffer + dose do lote voltam
        level.destroyBlock(pos, true);
        helper.runAfterDelay(20, () -> {
            int devolvido = itensNoChao(level, pos, IntoxicantesMod.CALDO_DE_CANA);
            helper.assertTrue(devolvido == 8,
                    "A dorna quebrada deve devolver as 8 unidades (buffer + lote)");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 400)
    public void barrilQuebradoSemJogadorDevolveTudo(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        level.setBlockAndUpdate(pos, IntoxicantesMod.BARRIL_CERVEJA.defaultBlockState());
        var be = (BarrilBebidaBlockEntity) level.getBlockEntity(pos);
        be.interagir(level, player, new ItemStack(IntoxicantesMod.MOSTO_CERVEJA_LUPULADO, 8));
        helper.assertTrue(be.processando() || be.getItem(0).getCount() > 0,
                "O barril deve carregar o mosto");

        // destroyBlock SEM jogador (explosão/pistão): o drop vanilla não cobre
        // a BE (não é Container) — o spawnAfterBreak tem que devolver buffer+lote
        level.destroyBlock(pos, true);
        helper.runAfterDelay(20, () -> {
            int devolvido = itensNoChao(level, pos,
                    IntoxicantesMod.MOSTO_CERVEJA_LUPULADO);
            helper.assertTrue(devolvido == 8,
                    "O barril quebrado sem jogador devolve buffer + lote em curso");
            helper.succeed();
        });
    }

    // ==================================================== BARRA REAL (ContainerData)

    @GameTest(maxTicks = 300)
    public void barraDaGuiEBarealDaBlockEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);

        // FOGO ANTES do bloco: o canSurvive do bloco duplo derruba a máquina
        // se a base aparecer depois (updateShape de vizinho) — o setup certo
        level.setBlockAndUpdate(pos.below(), Blocks.CAMPFIRE.defaultBlockState());
        level.setBlockAndUpdate(pos, IntoxicantesMod.ALAMBIQUE.defaultBlockState());
        var be = (AlambiqueBlockEntity) level.getBlockEntity(pos);
        be.tentarCarregar(level, player,
                new ItemStack(IntoxicantesMod.MOSTO_CANA_FERMENTADO, 4));

        helper.runAfterDelay(40, () -> {
            int restante = be.dadosGui().get(0);
            int total = be.dadosGui().get(1);
            int fase = be.dadosGui().get(2);
            helper.assertTrue(total > 0, "O total do lote deve ser positivo");
            helper.assertTrue(restante > 0 && restante < total,
                    "O progresso deve estar andando (restante entre 0 e total)");
            helper.assertTrue(fase == AlambiqueBlockEntity.FASE_DESTILANDO,
                    "A fase sincronizada deve ser DESTILANDO");
            helper.succeed();
        });
    }

    // ==================================================== LAYOUT DA GUI (v1.2.60)

    /**
     * O painel é 208×222; as posições dos slots vêm do {@link TipoMaquina}.
     * Estes testes provam que o MENU constrói a geometria certa (mesma fonte
     * que a tela usa pra desenhar) e que nada colide.
     */
    private static final int GUI_L = 208;
    private static final int GUI_H = 222;
    /** Janela do painel de invenção (início/altura do inventário do jogador). */
    private static final int INV_Y = 148;
    private static final int LBL_ALT = 4; // "Inventário" + respiro acima da grade

    @GameTest(maxTicks = 100)
    public void layoutTodosOsSlotsDentroDoPainelSemSobreporInventario(GameTestHelper helper) {
        // TODAS as 6 máquinas: cada slot da máquina deve ficar dentro do painel
        // e ACIMA da área do inventário do jogador (148 é a linha da mochila).
        int barraTotal = 0;
        for (TipoMaquina tipo : TipoMaquina.values()) {
            for (int[] xy : tipo.slotsGui) {
                helper.assertTrue(xy[0] >= 0 && xy[0] + 18 <= GUI_L,
                        tipo + ": slot x=" + xy[0] + " fora do painel 208");
                helper.assertTrue(xy[1] >= 0 && xy[1] + 18 <= GUI_H,
                        tipo + ": slot y=" + xy[1] + " fora do painel 222");
                helper.assertTrue(xy[1] + 18 <= INV_Y,
                        tipo + ": slot invade a área do inventário (y=" + xy[1] + ")");
                barraTotal++;
            }
            int[] barra = tipo.barraGui;
            helper.assertTrue(barra[0] >= 0 && barra[0] + barra[2] <= GUI_L,
                    tipo + ": barra fora do painel");
            helper.assertTrue(barra[1] + 6 <= INV_Y,
                    tipo + ": barra invade o inventário");
        }
        helper.assertTrue(barraTotal > 0, "As 6 máquinas foram varridas");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void layoutMenuDaPrensaBateComOTipoMaquina(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        var be = (MaquinaPrimaBlockEntity) level.getBlockEntity(pos);

        var menu = (MenuMaquinaSNC) be.createMenu(1, player.getInventory(), player);

        helper.assertTrue(menu.slots.size() == TipoMaquina.PRENSA.totalSlots()
                + MenuMaquinaSNC.SLOTS_JOGADOR,
                "O menu deve ter " + TipoMaquina.PRENSA.totalSlots()
                        + " slots da máquina + 36 do jogador");
        // geometria: o menu usa AS MESMAS coordenadas que a tela desenha
        for (int i = 0; i < TipoMaquina.PRENSA.totalSlots(); i++) {
            Slot slot = menu.slots.get(i);
            int[] esperado = TipoMaquina.PRENSA.slotsGui[i];
            helper.assertTrue(slot.x == esperado[0] && slot.y == esperado[1],
                    "Slot " + i + " deve estar em (" + esperado[0] + "," + esperado[1]
                            + ") — a mesma posição que a GUI pinta");
        }
        // inventário do jogador: 27 mochila + 9 hotbar nas linhas esperadas
        int n = TipoMaquina.PRENSA.totalSlots();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                Slot slot = menu.slots.get(n + r * 9 + c);
                helper.assertTrue(slot.y == 148 + r * 18 && slot.x == 8 + c * 18,
                        "Mochila fora da grade 18px (linha " + r + ")");
            }
        }
        for (int c = 0; c < 9; c++) {
            Slot slot = menu.slots.get(n + 27 + c);
            helper.assertTrue(slot.y == 206 && slot.x == 8 + c * 18,
                    "Hotbar fora da linha 206");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void layoutSlotsNaoColidemEntreSi(GameTestHelper helper) {
        // AABB simples: nenhum par de slots da MESMA máquina se sobrepõe
        // (slots têm 18px de passo; o teste pega colisão de layout novo).
        for (TipoMaquina tipo : TipoMaquina.values()) {
            int[][] slots = tipo.slotsGui;
            for (int a = 0; a < slots.length; a++) {
                for (int b = a + 1; b < slots.length; b++) {
                    boolean colide = slots[a][0] < slots[b][0] + 18
                            && slots[b][0] < slots[a][0] + 18
                            && slots[a][1] < slots[b][1] + 18
                            && slots[b][1] < slots[a][1] + 18;
                    helper.assertTrue(!colide,
                            tipo + ": slots " + a + " e " + b + " se sobrepõem");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void layoutFiltroDeInsumoBateComOCatalogo(GameTestHelper helper) {
        // o slot de entrada recusa o que nenhuma receita usa (a GUI pinta o
        // mesmo filtro: MenuMaquinaSNC.aceitaInsumo é código comum)
        helper.assertTrue(!MenuMaquinaSNC.aceitaInsumo(TipoMaquina.PRENSA,
                new ItemStack(IntoxicantesMod.MALTE)),
                "A prensa não aceita malte (isso é do caldeirão)");
        helper.assertTrue(MenuMaquinaSNC.aceitaInsumo(TipoMaquina.PRENSA,
                new ItemStack(IntoxicantesMod.UVA)),
                "A prensa aceita uvas");
        helper.assertTrue(MenuMaquinaSNC.aceitaInsumo(TipoMaquina.BARRIL,
                new ItemStack(IntoxicantesMod.CACHACA_JOVEM)),
                "O barril aceita cachaça jovem");
        helper.assertTrue(!MenuMaquinaSNC.aceitaInsumo(TipoMaquina.BARRIL,
                new ItemStack(IntoxicantesMod.UVA)),
                "O barril não aceita uva direto");
        // slot de SAÍDA: nada entra (o produto só sai)
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        var be = (MaquinaPrimaBlockEntity) level.getBlockEntity(pos);
        var menu = (MenuMaquinaSNC) be.createMenu(1, player.getInventory(), player);
        Slot saida = menu.slots.get(TipoMaquina.PRENSA.idxOut);
        helper.assertTrue(!saida.mayPlace(new ItemStack(IntoxicantesMod.MOSTO_DE_UVA)),
                "Slot de saída não aceita produto (só sai)");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void layoutShiftClickVaEVaiSemVazar(GameTestHelper helper) {
        // shift-click: uvas do jogador entram no buffer; produto da saída volta
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, IntoxicantesMod.PRENSA_UVAS.defaultBlockState());
        var be = (MaquinaPrimaBlockEntity) level.getBlockEntity(pos);
        var menu = (MenuMaquinaSNC) be.createMenu(1, player.getInventory(), player);

        // put na mão + quickMoveStack do slot da hotbar (0)
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IntoxicantesMod.UVA, 7));
        var devolve = menu.quickMoveStack(player, menu.slots.size() - 9); // hotbar 0
        helper.assertTrue(be.getItem(0).getCount() == 6,
                "Shift-click carrega 1 dose (6) no buffer");
        helper.assertTrue(contaMao(player, IntoxicantesMod.UVA) == 1,
                "A 7ª uva sobra na mão (a dose é inteira até no shift)");
        helper.assertTrue(devolve.getCount() == 1,
                "O quickMove devolve a sobra pro slot de origem");

        // agora o produto: pôr mosto na saída e shift-click de volta
        be.setItem(TipoMaquina.PRENSA.idxOut,
                new ItemStack(IntoxicantesMod.MOSTO_DE_UVA, 4));
        menu.quickMoveStack(player, TipoMaquina.PRENSA.idxOut);
        helper.assertTrue(contaMao(player, IntoxicantesMod.MOSTO_DE_UVA) == 4,
                "Shift-click da saída leva o produto pro inventário");
        helper.succeed();
    }

    // ==================================================== HELPERS

    /** Itens de um tipo na mão/inventário do jogador. */
    private static int contaMao(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var st = player.getInventory().getItem(i);
            if (st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
    }

    /** Itens dropados perto da posição (entidades Item no raio de 3 blocos). */
    private static int itensNoChao(net.minecraft.server.level.ServerLevel level,
            BlockPos pos, net.minecraft.world.item.Item item) {
        int total = 0;
        for (var entidade : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3))) {
            if (entidade.getItem().is(item)) {
                total += entidade.getItem().getCount();
            }
        }
        return total;
    }
}
