package com.intoxicantes;

import java.util.List;
import java.util.Map;

import com.intoxicantes.SaudeData.Orgao;

/**
 * v1.2.54 — O CATÁLOGO DAS VIAGENS: cada substância do mod com o seu efeito
 * assinatura e o "peso" dela (tier). Uma tabela só, usada pelos DOIS lados:
 * o servidor aplica o efeito e conta o dano de órgão; o client desenha o
 * overlay e as alucinações conforme o efeito ativo no jogador.
 *
 * As doses por item vêm daqui — o Embriaguez continua DONO do álcool (só
 * bebida emborracha), e este catálogo só referencia o que já existe.
 */
public final class CatalogoSubstancias {
    private CatalogoSubstancias() {}

    /** O efeito assinatura (registrado no Efeitos). */
    public enum Viagem {
        TRANQUILO,  // T1 baseado/seda
        MORNO,      // T2 ópio
        SONHO,      // T2.5 heroína
        OVERDRIVE,  // T3 cocaína
        VIAGEM      // T4 LSD
    }

    /** Ficha completa de uma substância. */
    public record Ficha(
            String id,
            net.minecraft.world.item.Item item,
            Viagem viagem,
            int tier,                 // 1..4 (escala dano de órgão e vício)
            int duracaoSegundos,      // duração da viagem
            int            vicioPorDose,         // pontos de dependência por dose
            SaudeData.Orgao orgao,    // qual órgão a droga cobra
            int danoOrgaoPorDose,     // quanto de dano cumulativo por dose
            String categoriaHistorico // álcool/erva/pó/pílula (prontuário)
    ) {}

    private static final Map<String, Ficha> POR_ID = new java.util.HashMap<>();
    private static final Map<net.minecraft.world.item.Item, Ficha> POR_ITEM = new java.util.HashMap<>();

    private static void registrar(Ficha f) {
        POR_ID.put(f.id(), f);
        POR_ITEM.put(f.item(), f);
    }

    static {
        // v1.2.56: números calibrados pra PRIMEIRA partida (ver CHANGELOG).
        // Duracao: leves longas e amigas, pesadas curtas e intensas — mas a
        // cocaína (a mais viciante) não pode ser a mais CURTA (era 120s, o
        // risco não compensava); LSD é a viagem-mãe, dura.
        // Vicio: escala pelo risco — leves quase não prendem (2/1), pesadas
        // cobram caro (10/12/6).
        // T1 — a erva: fuma, ri, come. Pesa no pulmão, vicia de leve.
        registrar(new Ficha("baseado", IntoxicantesMod.BASEADO,
                Viagem.TRANQUILO, 1, 240, 2, Orgao.PULMAO, 12, "erva"));
        registrar(new Ficha("maconha_seda", IntoxicantesMod.MACONHA_SEDA,
                Viagem.TRANQUILO, 1, 180, 1, Orgao.PULMAO, 8, "erva"));
        // T2 — o ópio: o casaco quente. Vício médio.
        registrar(new Ficha("opio", IntoxicantesMod.OPIO,
                Viagem.MORNO, 2, 300, 6, Orgao.ESTOMAGO, 25, "pó"));
        // T2.5 — a heroína: o nod. Vicia muito.
        registrar(new Ficha("heroina", IntoxicantesMod.HEROINA,
                Viagem.SONHO, 3, 240, 10, Orgao.ESTOMAGO, 35, "pó"));
        // T3 — a cocaína: o overdrive. Vicia rápido e cobra o coração (estômago
        // no prontuário: "coração/estômago" — a barriga de pó).
        registrar(new Ficha("cocaina", IntoxicantesMod.COCAINA,
                Viagem.OVERDRIVE, 3, 180, 12, Orgao.ESTOMAGO, 30, "pó"));
        // T4 — o LSD: a viagem. Menos vício físico, mais alma.
        registrar(new Ficha("lsd", IntoxicantesMod.LSD,
                Viagem.VIAGEM, 4, 420, 3, Orgao.ESTOMAGO, 20, "pílula"));
    }

    /** Ficha pelo id ("" -> null). */
    public static Ficha porId(String id) {
        return POR_ID.get(id);
    }

    /** Ficha pelo item consumido (null se não é substância catalogada). */
    public static Ficha porItem(net.minecraft.world.item.Item item) {
        return POR_ITEM.get(item);
    }

    /** Todas as fichas (pra busca por viagem na queda). */
    public static java.util.Collection<Ficha> todas() {
        return java.util.Collections.unmodifiableCollection(POR_ID.values());
    }

    /** Todos os itens que contam como substância (o filtro do hook de consumo). */
    public static List<net.minecraft.world.item.Item> itensCatalogados() {
        return List.copyOf(POR_ITEM.keySet());
    }

    /** Nome de lang da droga do vício ("item.intoxicantes.baseado"). */
    public static String nomeLang(Ficha ficha) {
        return "item.intoxicantes." + ficha.id();
    }
}
