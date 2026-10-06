package com.intoxicantes;

/**
 * v1.2.59 — AS SEIS MÁQUINAS DO SNC (identidade + layout em UM lugar só).
 *
 * Cada constante carrega:
 * <ul>
 *   <li>{@link #id} — chave de lang e id de registro do menu;</li>
 *   <li>{@link #slotsGui} — coordenadas {x,y} dos slots DA MÁQUINA na GUI
 *       (painel 208×222), na posição que a CENA de cada uma pede. Índice 0 é
 *       sempre o INSUMO; os demais seguem {@link #idxSec}/{@link #idxOut}/
 *       {@link #idxExtra};</li>
 *   <li>{@link #barraGui} — {x,y,largura} da barra de progresso REAL, onde a
 *       composição da máquina pede (altura fixa 6);</li>
 *   <li>{@link #cena} — a VIGNETTA pixel-art única de cada máquina (rolos,
 *       fuso, tacho, tanque borbulhando, serpentina, barril deitado), como
 *       lista de retângulos {x1,y1,x2,y2,código}. Código &gt;= 0 é cor ARGB
 *       literal; negativo é simbólico: -1 fill acento, -2 fill moldura,
 *       -3 fill faixa, -4 fill texto, -5 fill texto fraco, -10 OUTLINE
 *       moldura — resolvido pela {@code TelaMaquinaBase} (paleta nunca
 *       dessincroniza da cena);</li>
 *   <li>paleta própria (fundo/moldura/faixa/acento/texto).</li>
 * </ul>
 *
 * Papel dos slots (convenção do {@link MenuMaquinaSNC}):
 * <pre>
 *   MOENDA     0=insumo(cana)      1=saída(caldo)   2=extra(bagaço)
 *   PRENSA     0=insumo(uvas)      1=saída(mosto)
 *   CALDEIRAO  0=insumo(malte)     1=2ª dose(lúpulo) 2=saída
 *   DORNA      0=insumo            1=saída
 *   ALAMBIQUE  0=insumo            1=saída
 *   BARRIL     0=insumo            1=garrafa de vidro 2=saída(garrafas)
 * </pre>
 */
public enum TipoMaquina {

    //                       id                 idxSec idxOut idxExtra  slots{in, sec/out, extra}   barra{x,y,w}
    MOENDA    ("moenda_cana",       -1,     1,     2,
            new int[][]{{26, 28}, {148, 26}, {148, 44}},
            new int[]{60, 52, 56},
            cenaMoenda(),
            0xFFF2E8CE, 0xFF5C4023, 0xFF3F6F35, 0xFF8FBF6B, 0xFF33270F, 0xFF7A6A4A),
    PRENSA    ("prensa_uvas",       -1,     1,    -1,
            new int[][]{{26, 28}, {148, 28}},
            new int[]{120, 50, 56},
            cenaPrensa(),
            0xFFF3E9DB, 0xFF4A2530, 0xFF6E2F3D, 0xFFA04455, 0xFF2E1B20, 0xFF83655F),
    CALDEIRAO ("caldeirao_mostura",  1,     2,    -1,
            new int[][]{{22, 26}, {22, 44}, {152, 28}},
            new int[]{76, 52, 56},
            cenaCaldeirao(),
            0xFFF1E7D0, 0xFF7A4A1E, 0xFF9A6428, 0xFFC99044, 0xFF352510, 0xFF84704E),
    DORNA     ("dorna_bebida",      -1,     1,    -1,
            new int[][]{{24, 28}, {150, 28}},
            new int[]{76, 52, 56},
            cenaDorna(),
            0xFFEEE9D8, 0xFF5F5442, 0xFF77694A, 0xFF9FA871, 0xFF2E2A1C, 0xFF7C7561),
    ALAMBIQUE ("alambique",         -1,     1,    -1,
            new int[][]{{22, 28}, {152, 28}},
            new int[]{76, 52, 56},
            cenaAlambique(),
            0xFFF0E5CE, 0xFF8C4A16, 0xFFB2621E, 0xFFE08A2E, 0xFF3A2410, 0xFF8A6C48),
    BARRIL    ("barril_bebida",     -1,     2,    -1,
            new int[][]{{24, 26}, {24, 44}, {150, 28}},
            new int[]{76, 52, 56},
            cenaBarril(),
            0xFFEBDFC4, 0xFF4E3A24, 0xFF6B4E2E, 0xFFB08D57, 0xFF2C2114, 0xFF7A6A50);

    // Cores fixas de cena (não dependem da paleta: vapor, fogo, matéria-prima).
    static final int VAPOR = 0x66FFFFFF;      // fumaça/espuma translúcida
    static final int BRILHO = 0x88FFFFFF;     // reflexo/bolhas
    static final int FOGO = 0xFFE07020;       // chama sob caldeirão/alambique
    static final int CANA_VERDE = 0xFF6E8F3C; // colmo entrando na moenda
    static final int VINHO_MASSA = 0xFF5C2A38;// uvas sob o fuso da prensa
    static final int MOSTO = 0xFFC98F3F;      // superfície da mostura
    static final int MURK = 0xFF8A8A4C;       // líquido de fermentação
    static final int ROTULO = 0xFFC9A866;     // rótulo do barril
    static final int BEBIDA = 0xFF6E2440;     // mancha da bebida no rótulo
    static final int AGUA = 0x2600AACC;       // água de resfriamento da serpentina

    /** Chave de lang/id de registro (moenda_cana, prensa_uvas...). */
    public final String id;
    /** Índice do slot da 2ª dose (caldeirão) ou garrafa (barril); -1 = não tem. */
    public final int idxSec;
    /** Índice do slot de SAÍDA principal. */
    public final int idxOut;
    /** Índice do slot de saída EXTRA (bagaço da moenda); -1 = não tem. */
    public final int idxExtra;
    /** Coordenadas {x,y} de cada slot da máquina na GUI (208×222). */
    public final int[][] slotsGui;
    /** {x,y,largura} da barra de progresso (altura fixa 6). */
    public final int[] barraGui;
    /** A cena pixel-art desta máquina (ver contrato no javadoc da classe). */
    public final int[][] cena;

    // ==================================================== PALETA DA MÁQUINA
    public final int corFundo;
    public final int corMoldura;
    public final int corFaixa;
    public final int corAcento;
    public final int corTexto;
    public final int corTextoFraco;

    TipoMaquina(String id, int idxSec, int idxOut, int idxExtra, int[][] slotsGui,
            int[] barraGui, int[][] cena, int corFundo, int corMoldura,
            int corFaixa, int corAcento, int corTexto, int corTextoFraco) {
        this.id = id;
        this.idxSec = idxSec;
        this.idxOut = idxOut;
        this.idxExtra = idxExtra;
        this.slotsGui = slotsGui;
        this.barraGui = barraGui;
        this.cena = cena;
        this.corFundo = corFundo;
        this.corMoldura = corMoldura;
        this.corFaixa = corFaixa;
        this.corAcento = corAcento;
        this.corTexto = corTexto;
        this.corTextoFraco = corTextoFraco;
    }

    // ==================================================== CENAS (x2,y2 exclusivos)

    private static int[][] cenaMoenda() {
        return new int[][] {
                // rolos dentados (a esmagadora em si)
                {84, 24, 96, 46, -1}, {84, 24, 96, 46, -10}, {86, 26, 89, 44, BRILHO},
                {100, 24, 112, 46, -1}, {100, 24, 112, 46, -10}, {102, 26, 105, 44, BRILHO},
                {92, 28, 96, 31, -2}, {92, 36, 96, 39, -2},       // dentes L
                {100, 24, 104, 27, -2}, {100, 41, 104, 44, -2},   // dentes R (defasados)
                {88, 24, 92, 26, -2}, {104, 24, 108, 26, -2},     // eixos
                // colmo entrando do slot de insumo até os rolos
                {46, 32, 64, 36, CANA_VERDE}, {62, 35, 78, 39, CANA_VERDE},
                // calha de caldo sob os rolos
                {78, 46, 118, 51, -3}, {80, 47, 116, 50, -1},
                // caldo subindo em direção ao slot de saída
                {118, 42, 128, 46, -1}, {128, 38, 138, 42, -1}, {138, 34, 146, 38, -1},
                // calha de bagaço caindo pro slot extra
                {112, 47, 124, 50, -4}, {124, 50, 136, 53, -4}, {136, 53, 146, 54, -4}};
    }

    private static int[][] cenaPrensa() {
        return new int[][] {
                // barra de pressão + porcas
                {62, 24, 118, 28, -2}, {62, 24, 68, 28, -1}, {112, 24, 118, 28, -1},
                // fuso rosqueado descendo
                {86, 28, 94, 42, -1},
                {86, 30, 94, 31, -2}, {86, 34, 94, 35, -2}, {86, 38, 94, 39, -2},
                // prato de madeira pressionando
                {68, 42, 112, 46, -4}, {70, 43, 110, 44, -2},
                // massa de uvas espremida + poças de mosto
                {72, 46, 108, 52, VINHO_MASSA},
                {78, 48, 81, 51, -1}, {90, 47, 93, 50, -1}, {100, 49, 103, 52, -1},
                {76, 52, 104, 54, -1},
                // mosto escorrendo pro slot de saída
                {112, 42, 122, 46, -1}, {122, 38, 132, 42, -1}, {132, 34, 142, 38, -1}};
    }

    private static int[][] cenaCaldeirao() {
        return new int[][] {
                // vapor subindo do tacho
                {72, 22, 74, 27, VAPOR}, {82, 21, 84, 26, VAPOR},
                {92, 22, 94, 27, VAPOR}, {102, 23, 104, 28, VAPOR},
                // tacho de cobre com mostura
                {66, 31, 112, 46, -1}, {66, 31, 112, 46, -10}, {68, 33, 110, 36, MOSTO},
                {64, 28, 114, 31, -2},
                // concha descansada na borda
                {103, 18, 106, 32, -2}, {99, 30, 107, 34, -2},
                // fogo embaixo
                {72, 45, 76, 49, FOGO}, {80, 44, 84, 49, FOGO}, {90, 45, 94, 49, FOGO},
                {100, 44, 104, 49, FOGO}, {106, 46, 110, 49, FOGO},
                {68, 49, 110, 51, -2},
                // tubo de mosto lupulado até o slot de saída
                {112, 36, 126, 40, -2}, {126, 32, 138, 36, -2}, {138, 34, 150, 38, -1}};
    }

    private static int[][] cenaDorna() {
        return new int[][] {
                // tanque de madeira
                {60, 26, 124, 49, -3}, {60, 26, 124, 49, -10},
                {70, 28, 72, 47, -5}, {84, 28, 86, 47, -5},
                {98, 28, 100, 47, -5}, {112, 28, 114, 47, -5},
                // visor de nível: líquido, espuma e bolhas subindo
                {66, 35, 118, 44, MURK}, {66, 35, 118, 38, VAPOR},
                {74, 39, 76, 41, BRILHO}, {88, 41, 90, 43, BRILHO},
                {96, 38, 98, 40, BRILHO}, {106, 40, 108, 42, BRILHO},
                // aros de metal
                {60, 30, 124, 32, -2}, {60, 43, 124, 45, -2},
                // válvula de alívio no topo
                {88, 21, 96, 25, -2}, {90, 20, 94, 21, -2}, {97, 20, 101, 23, VAPOR},
                // torneira escorrendo pro slot de saída
                {124, 38, 132, 42, -2}, {126, 36, 130, 38, -2},
                {132, 36, 142, 40, -1}, {142, 34, 148, 38, -1}};
    }

    private static int[][] cenaAlambique() {
        return new int[][] {
                // domo de cobre (formato de cebola)
                {55, 23, 61, 26, -2},
                {48, 26, 68, 30, -1}, {42, 30, 74, 34, -1},
                {40, 34, 76, 38, -1}, {40, 38, 76, 46, -1}, {40, 38, 76, 46, -10},
                {44, 32, 47, 44, BRILHO},
                // pescoço de cisne descendo pra direita
                {62, 26, 72, 30, -1}, {70, 30, 80, 34, -1}, {78, 34, 88, 38, -1},
                // serpentina em espiral dentro do tonel d'água
                {90, 32, 110, 46, AGUA}, {88, 30, 112, 48, -10},
                {92, 34, 108, 36, -2}, {92, 44, 108, 46, -2},
                {92, 36, 94, 44, -2}, {106, 36, 108, 44, -2}, {98, 38, 102, 42, -1},
                // fogo sob o domo
                {46, 46, 50, 50, FOGO}, {54, 46, 58, 50, FOGO}, {62, 46, 66, 50, FOGO},
                {42, 50, 74, 52, -2},
                // destilado gotejando pro slot de saída
                {112, 42, 124, 46, -1}, {124, 38, 136, 42, -1}, {136, 34, 148, 38, -1}};
    }

    private static int[][] cenaBarril() {
        return new int[][] {
                // barril deitado (aduelas + aros)
                {62, 26, 126, 48, -3}, {62, 26, 126, 48, -10},
                {74, 28, 76, 46, -5}, {86, 28, 88, 46, -5},
                {102, 28, 104, 46, -5}, {114, 28, 116, 46, -5},
                // rótulo com a cor da bebida
                {78, 34, 112, 40, ROTULO}, {92, 35, 98, 39, BEBIDA},
                // aros de metal por cima do rótulo
                {70, 26, 74, 48, -2}, {96, 26, 100, 48, -2}, {118, 26, 122, 48, -2},
                // rolha no topo
                {90, 24, 98, 28, -2}, {92, 25, 96, 27, -1},
                // torneira gotejando no copo (perto do slot de garrafas)
                {58, 40, 62, 44, -2}, {56, 44, 60, 47, -1},
                {46, 45, 58, 51, -10}, {48, 47, 56, 49, -1},
                // pipa do lote pro slot de saída
                {126, 30, 140, 34, -2}, {140, 32, 148, 36, -1}};
    }

    /** Quantidade de slots QUE A MÁQUINA TEM (sem os do jogador). */
    public int totalSlots() {
        return slotsGui.length;
    }

    /** O slot de insumo é sempre o 0 (convenção do motor de lotes). */
    public int idxInsumo() {
        return 0;
    }

    /** Índice na rede (o payload do menu viaja como ordinal). */
    public int indice() {
        return ordinal();
    }

    /** Desserialização do payload (índice com clamp defensivo). */
    public static TipoMaquina porIndice(int indice) {
        TipoMaquina[] valores = values();
        if (indice < 0 || indice >= valores.length) {
            return MOENDA;
        }
        return valores[indice];
    }
}
