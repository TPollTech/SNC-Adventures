package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * v1.2.60 — o catálogo de receitas é DATAPACK (JSON em
 * {@code data/intoxicantes/processo_bebida/<maquina>/}) e o servidor de teste
 * carrega os JSON do próprio mod no reload de dados.
 *
 * O que os testes provam (fonte: a FACHADA {@link ProcessosBebida}, exatamente
 * o que o Guia, as GUIs e as máquinas leem):
 * <ul>
 *   <li>o reload do datapack rodou (flag {@code recarregado} — não é o
 *       fallback de fábrica que alimentou o catálogo);</li>
 *   <li>cada máquina enxerga sua receita com os números do JSON (doses,
 *       extras, tempos) — fonte única, nada escrito em dois lugares.</li>
 * </ul>
 * Os defaults de fábrica são iguais aos JSONs de fábrica POR DESIGN (o
 * datapack vazio nunca muda o jogo); este teste garante o caminho inteiro.
 */
public class CatalogoGameTest {

    @GameTest(maxTicks = 200)
    public void datapackCarregaEFonteUnica(GameTestHelper helper) {
        // A recarga de dados acontece no boot do servidor; succeedWhen re-tenta
        // até os listeners aplicarem (ou o teste estourar maxTicks).
        helper.succeedWhen(() -> {
            helper.assertTrue(CatalogoBebidas.recarregado(),
                    "O reload do datapack aplicou (JSON carregado)");

            // ---- DORNA (fermentação) — o que a GUI do Guia mostra
            helper.assertTrue(ProcessosBebida.receitasDorna().size() == 2,
                    "2 fermentações de dorna no catálogo");
            var dCana = ProcessosBebida.dornaDe(IntoxicantesMod.CALDO_DE_CANA).orElseThrow();
            helper.assertTrue(dCana.qtdIn() == 4 && dCana.qtdOut() == 4
                    && dCana.output() == IntoxicantesMod.MOSTO_CANA_FERMENTADO,
                    "dorna: 4 caldo → 4 mosto fermentado");

            // ---- ALAMBIQUE (destilação)
            var aCana = ProcessosBebida.alambiqueDe(IntoxicantesMod.MOSTO_CANA_FERMENTADO).orElseThrow();
            helper.assertTrue(aCana.qtdIn() == 4 && aCana.qtdOut() == 2
                    && aCana.output() == IntoxicantesMod.CACHACA_JOVEM,
                    "alambique: 4 mosto → 2 cachaça jovem");

            // ---- BARRIL (a cadeia do vinho que o Guia desenha)
            var v = ProcessosBebida.barrilDe("vinho").orElseThrow();
            helper.assertTrue(v.input() == IntoxicantesMod.MOSTO_DE_UVA && v.qtdIn() == 4,
                    "barril do vinho: 4 mostos de uva");
            helper.assertTrue(v.tempoFermentacaoSeg() == 300 && v.tempoMaturacaoSeg() == 300,
                    "barril do vinho: 300s + 300s");
            helper.assertTrue(v.bebidaFinal() == IntoxicantesMod.VINHO,
                    "barril do vinho → VINHO engarrafado");

            // ---- MÁQUINAS DE PRIMA (moenda/prensa/caldeirão)
            var moenda = ProcessosBebida.moendaDe(IntoxicantesMod.CANA_DE_ACUCAR).orElseThrow();
            helper.assertTrue(moenda.qtdIn() == 4 && moenda.qtdOut() == 4,
                    "moenda: 4 canas → 4 caldos");
            helper.assertTrue(moenda.extraOut() == IntoxicantesMod.BAGACO_DE_CANA
                    && moenda.extraQtd() == 1, "moenda: +1 bagaço no slot extra");
            helper.assertTrue(moenda.tempoSeg() == 40, "moenda: lote de 40s");

            var prensa = ProcessosBebida.prensaDe(IntoxicantesMod.UVA).orElseThrow();
            helper.assertTrue(prensa.qtdIn() == 6 && prensa.qtdOut() == 4
                    && prensa.secIn() == null,
                    "prensa: 6 uvas → 4 mostos, ingrediente único");

            var caldeirao = ProcessosBebida.caldeiraoDe(IntoxicantesMod.MALTE).orElseThrow();
            helper.assertTrue(caldeirao.secIn() == IntoxicantesMod.LOUPULO_FRESCO
                    && caldeirao.secQtd() == 1, "caldeirão: segunda dose é 1 lúpulo");
            helper.assertTrue(caldeirao.output() == IntoxicantesMod.MOSTO_CERVEJA_LUPULADO,
                    "caldeirão → mosto lupulado");
        });
    }
}
