package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A TELA BASE DAS MÁQUINAS DO SNC (v1.2.59): UMA classe, SEIS caras.
 *
 * Desenho 100% vetorial (fill/outline/text/item — zero textura de GUI), na
 * mesma escola do Esquinão: o painel usa a PALETA do {@link TipoMaquina} e
 * cada máquina tem seu ORNAMENTO pixel-art no letreiro. A GEOMETRIA (slots,
 * barra, painel de receitas) é compartilhada — consistente como pede o plano,
 * mas cada máquina é reconhecível de longe.
 *
 * A BARRA É REAL: lê o {@code ContainerData} da BlockEntity
 * ({@code progresso/total} — ticks que faltam sobre o total do lote). Nada
 * de animação inventada no client (regra 9/10 do plano).
 *
 * O painel "PRODUZ" lista as receitas direto do {@link ProcessosBebida} —
 * mesmo catálogo do Guia, nada escrito duas vezes (regra 17).
 *
 * Ponto de costura do vanilla 26.3: {@code AbstractContainerScreen} desenha
 * Screen-bg → translate → labels → slots → item carregado → tooltip. Este
 * método {@code extractLabels} roda DEPOIS do fundo e ANTES dos slots, então
 * o painel pintado aqui fica embaixo dos itens de graça.
 */
public class TelaMaquinaBase extends AbstractContainerScreen<MenuMaquinaSNC> {

    /** Geometria compartilhada (as coordenadas do TipoMaquina mandam nisto). */
    public static final int IMG_W = 208;
    /** v1.2.69: 226 — a hotbar (y 206 + 18 = 224) caber INTEIRA com 2px de folga. */
    public static final int IMG_H = 226;
    /** Linhas de receita por página do painel PRODUZ (2 cabem no 44px). */
    private static final int LINHAS_POR_PAGINA = 2;

    private final TipoMaquina tipo;
    /** Retângulos das linhas de receita desenhadas (tooltip no hover). */
    private final List<int[]> retangulosReceita = new ArrayList<>();
    /** Stacks por linha de receita (mesma ordem dos retângulos). */
    private final List<ItemStack[]> stacksReceita = new ArrayList<>();
    /**
     * v1.2.60: o painel PRODUZ cabe 2 linhas de 20px — máquinas com catálogo
     * maior (o barril tem 4 bebidas) PAGINAM: setas clicáveis + scroll na
     * área do painel, no mesmo idioma do Esquinão (hit = área desenhada).
     */
    private int paginaReceitas;
    /** Retângulo da seta ◀ (screen-space, por frame). */
    private int[] setaAnterior;
    /** Retângulo da seta ▶ (screen-space, por frame). */
    private int[] setaProxima;

    public TelaMaquinaBase(MenuMaquinaSNC menu, Inventory inv, Component titulo) {
        super(menu, inv, titulo, IMG_W, IMG_H);
        this.tipo = menu.tipo();
    }

    // ==================================================== LAYOUT

    /** Geometria do painel pra quem olha de fora (GameTests de layout). */
    public int painelX() {
        return leftPos;
    }

    /** Geometria do painel pra quem olha de fora (GameTests de layout). */
    public int painelY() {
        return topPos;
    }

    /**
     * v1.2.69 FIX DE LAYOUT: no 26.3 o {@code AbstractContainerScreen} aplica o
     * translate(leftPos, topPos) na pose ANTES de chamar os hooks extract*
     * (labels → slots → tooltip) e o mouse chega em coordenadas DA TELA. Então:
     * DESENHE em coordenadas do painel ({@code lx/ly} = identidade — o translate
     * do vanilla posiciona); para HIT-TEST de mouse, converta com {@link #fx}/
     * {@link #fy}. Somar leftPos/topPos aqui de novo era offset DUPLo: o painel
     * ancorava no canto inferior direito da tela (2·leftPos = largura − 208)
     * e os itens reais dos slots ficavam desconectados do desenho.
     */
    private int lx(int x) {
        return x;
    }

    private int ly(int y) {
        return y;
    }

    /** Mouse (coords de tela) → espaço do painel (o translate não vale pro mouse). */
    private double fx(double x) {
        return x - leftPos;
    }

    private double fy(double y) {
        return y - topPos;
    }

    // ==================================================== DESENHO

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        desenharPainel(g);
        desenharLetreiro(g);
        desenharCena(g);
        desenharAnimacao(g);
        desenharBarra(g);
        desenharReceitas(g, mx, my);
        g.text(this.font, Component.translatable("gui.intoxicantes.maquina.inventario"),
                lx(8), ly(134), tipo.corTextoFraco);
    }

    /** Corpo da máquina: sombra, fundo da paleta e moldura dupla. */
    private void desenharPainel(GuiGraphicsExtractor g) {
        g.fill(lx(3), ly(3), lx(IMG_W + 3), ly(IMG_H + 3), 0x66000000);
        g.fill(lx(0), ly(0), lx(IMG_W), ly(IMG_H), tipo.corFundo);
        g.outline(lx(0), ly(0), IMG_W, IMG_H, tipo.corMoldura);
        g.outline(lx(1), ly(1), IMG_W - 2, IMG_H - 2, tipo.corFaixa);
        g.outline(lx(3), ly(3), IMG_W - 6, IMG_H - 6,
                shade(tipo.corFaixa, 0.8F));
    }

    /** Faixa do título com rebites (a cara "industrial artesanal" da linha). */
    private void desenharLetreiro(GuiGraphicsExtractor g) {
        g.fill(lx(4), ly(4), lx(IMG_W - 4), ly(23), tipo.corFaixa);
        for (int px = lx(6); px < lx(IMG_W - 6); px += 7) {
            g.fill(px, ly(4), px + 3, ly(6), tipo.corAcento);
        }
        g.centeredText(this.font, this.title, lx(IMG_W / 2), ly(11), 0xFFF3E9CF);
    }

    /**
     * A CENA ÚNICA de cada máquina (vignetta do ofício): os retângulos vivem
     * no {@link TipoMaquina}; aqui só resolvemos os códigos simbólicos para a
     * paleta (nunca dessincroniza) e pintamos na ordem do array.
     */
    private void desenharCena(GuiGraphicsExtractor g) {
        for (int[] r : tipo.cena) {
            if (r[4] == -10) {
                g.outline(lx(r[0]), ly(r[1]), r[2] - r[0], r[3] - r[1],
                        tipo.corMoldura);
            } else {
                g.fill(lx(r[0]), ly(r[1]), lx(r[2]), ly(r[3]),
                        resolverCor(r[4]));
            }
        }
    }

    /** Códigos da cena: negativo = simbólico (paleta), senão ARGB literal. */
    private int resolverCor(int codigo) {
        return switch (codigo) {
            case -1 -> tipo.corAcento;
            case -2 -> tipo.corMoldura;
            case -3 -> tipo.corFaixa;
            case -4 -> tipo.corTexto;
            case -5 -> tipo.corTextoFraco;
            default -> codigo;
        };
    }

    // ==================================================== ANIMAÇÃO DA CENA (v1.2.60)

    /**
     * A cena GANHA VIDA com o lote REAL: TUDO que se move é função do
     * {@code ContainerData} — restante/total/fase mandam em QUANDO e QUANTO
     * (bolhas aceleram na dorna, vapor engrossa no caldeirão, a serpentina
     * goteja no alambique, o fuso desce na prensa). O relógio de frames só
     * escolhe o quadro da oscilação (o mesmo papel do bob de itens vanilla) —
     * NENHUM estado inventado no client: lote parado = cena imóvel.
     */
    private void desenharAnimacao(GuiGraphicsExtractor g) {
        if (!ativo()) {
            return;
        }
        int f = quadro();
        float p = progresso();
        switch (tipo) {
            case DORNA -> animarDorna(g, f, p);
            case CALDEIRAO -> animarCaldeirao(g, f, p);
            case ALAMBIQUE -> animarAlambique(g, f, p);
            case PRENSA -> animarPrensa(g, f, p);
            case MOENDA -> animarMoenda(g, f, p);
            case BARRIL -> animarBarril(g, f);
        }
    }

    /** O lote está rodando? (timer só anda quando a máquina processa) */
    private boolean ativo() {
        return menu.dado(0) > 0;
    }

    /** Progresso do lote REAL (mesma conta da barra). */
    private float progresso() {
        int restante = menu.dado(0);
        int total = menu.dado(1);
        return total > 0
                ? net.minecraft.util.Mth.clamp(1F - restante / (float) total, 0F, 1F)
                : 0F;
    }

    /** Quadro da oscilação (relógio de apresentação, não de estado). */
    private int quadro() {
        return (int) ((net.minecraft.util.Util.getMillis() / 50L) & 0xFFFF);
    }

    /** DORNA: bolhas sobem no visor (3→5, mais rápido no fim) + espuma e válvula vivas. */
    private void animarDorna(GuiGraphicsExtractor g, int f, float p) {
        int[][] bolhas = {{75, 0}, {89, 2}, {97, 1}, {107, 3}};
        int n = 3 + Math.round(2 * p);
        int periodo = Math.max(1, 8 - Math.round(5 * p));
        for (int i = 0; i < bolhas.length && i < n; i++) {
            int t = (f / periodo + bolhas[i][1]) % 6;
            int y = 42 - t;
            g.fill(lx(bolhas[i][0]), ly(y), lx(bolhas[i][0] + 2), ly(y + 2),
                    TipoMaquina.BRILHO);
        }
        if ((f / 10) % 2 == 0) {
            g.fill(lx(66), ly(37), lx(118), ly(38), TipoMaquina.VAPOR);
        }
        int pv = Math.max(1, 16 - Math.round(8 * p));
        if ((f / pv) % 4 < 2) {
            g.fill(lx(96), ly(17), lx(102), ly(21), TipoMaquina.VAPOR);
            g.fill(lx(98), ly(15), lx(100), ly(17), TipoMaquina.VAPOR);
        }
    }

    /** CALDEIRÃO: vapor engrossa (1→4 colunas) e o fogo fervilha. */
    private void animarCaldeirao(GuiGraphicsExtractor g, int f, float p) {
        int n = 1 + Math.round(3 * p);
        int[] colunas = {70, 80, 96, 106};
        for (int i = 0; i < colunas.length && i < n; i++) {
            if ((f / 3 + i) % 3 != 0) {
                g.fill(lx(colunas[i]), ly(19), lx(colunas[i] + 3),
                        ly(21 + ((f / 5 + i) % 3)), TipoMaquina.VAPOR);
            }
        }
        int[][] chamas = {{72, 45, 76, 49}, {80, 44, 84, 49}, {90, 45, 94, 49},
                {100, 44, 104, 49}, {106, 46, 110, 49}};
        int vivo = (f / 3) % 2;
        for (int i = 0; i < chamas.length; i++) {
            if ((i + vivo) % 2 == 0) {
                g.fill(lx(chamas[i][0]), ly(chamas[i][1]), lx(chamas[i][2]),
                        ly(chamas[i][3]), shade(TipoMaquina.FOGO, 1.3F));
            }
        }
    }

    /** ALAMBIQUE: a serpentina GOTEJA (mais rápido no fim) + fogo e água vivos. */
    private void animarAlambique(GuiGraphicsExtractor g, int f, float p) {
        int periodo = Math.max(1, 14 - Math.round(8 * p));
        int t = f % periodo;
        if (t < 6) {
            int y = 42 + t / 2;
            g.fill(lx(115), ly(y), lx(117), ly(y + 2), tipo.corAcento);
        }
        if ((f / 6) % 2 == 0) {
            g.fill(lx(94), ly(37), lx(104), ly(38), TipoMaquina.BRILHO);
        }
        int[][] chamas = {{46, 46, 50, 50}, {54, 46, 58, 50}, {62, 46, 66, 50}};
        int vivo = (f / 3) % 2;
        for (int i = 0; i < chamas.length; i++) {
            if ((i + vivo) % 2 == 0) {
                g.fill(lx(chamas[i][0]), ly(chamas[i][1]), lx(chamas[i][2]),
                        ly(chamas[i][3]), shade(TipoMaquina.FOGO, 1.3F));
            }
        }
    }

    /** PRENSA: o fuso desce com o progresso, o mosto sangra nas laterais. */
    private void animarPrensa(GuiGraphicsExtractor g, int f, float p) {
        int profundidade = Math.round(3 * p);
        g.fill(lx(86), ly(42 - profundidade), lx(94), ly(42),
                shade(tipo.corAcento, 0.9F));
        int larg = 2 + Math.round(4 * p);
        g.fill(lx(72), ly(52), lx(72 + larg), ly(54), tipo.corAcento);
        g.fill(lx(108 - larg), ly(52), lx(108), ly(54), tipo.corAcento);
        if ((f / 5) % 4 == 0) {
            g.fill(lx(62), ly(27), lx(118), ly(28), shade(tipo.corMoldura, 0.7F));
        }
    }

    /** MOENDA: a cana avança pros rolos, o brilho gira, o caldo escorre em degraus. */
    private void animarMoenda(GuiGraphicsExtractor g, int f, float p) {
        int ciclo = 12;
        int t = f % ciclo;
        int x = 46 + t * (78 - 46) / ciclo;
        g.fill(lx(x), ly(32), lx(x + 4), ly(36), TipoMaquina.CANA_VERDE);
        if ((f / 4) % 2 == 0) {
            g.fill(lx(86), ly(26), lx(89), ly(34), TipoMaquina.BRILHO);
        } else {
            g.fill(lx(86), ly(34), lx(89), ly(44), TipoMaquina.BRILHO);
        }
        int passo = (int) ((f / 3) % 3);
        int[][] calha = {{118, 42, 128, 46}, {128, 38, 138, 42}, {138, 34, 146, 38}};
        g.fill(lx(calha[passo][0]), ly(calha[passo][1]), lx(calha[passo][2]),
                ly(calha[passo][3]), shade(tipo.corAcento, 1.25F));
    }

    /** BARRIL: a torneira goteja no copo; com o lote pronto, a rolha acende. */
    private void animarBarril(GuiGraphicsExtractor g, int f) {
        float p = progresso();
        int periodo = Math.max(1, 12 - Math.round(6 * p));
        int t = f % periodo;
        if (t < 5) {
            int y = 44 + t / 2;
            g.fill(lx(57), ly(y), lx(59), ly(y + 2), tipo.corAcento);
        }
        if (menu.dado(2) == fasePronta() && (f / 6) % 2 == 0) {
            g.fill(lx(92), ly(25), lx(96), ly(27), tipo.corAcento);
        }
    }

    /**
     * A BARRA REAL do lote (ContainerData da BlockEntity), na posição que a
     * CENA de cada máquina define, com o status da fase por baixo.
     */
    private void desenharBarra(GuiGraphicsExtractor g) {
        int restante = menu.dado(0);
        int total = menu.dado(1);
        int fase = menu.dado(2);
        int bx = lx(tipo.barraGui[0]);
        int by = ly(tipo.barraGui[1]);
        int bw = tipo.barraGui[2];
        int bh = 6;
        // trilho + preenchimento (a barra do SNC: metal escuro, conteúdo aceso)
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, tipo.corMoldura);
        g.fill(bx, by, bx + bw, by + bh, shade(tipo.corFundo, 0.55F));
        boolean pronta = fase == fasePronta();
        float frac = total > 0 ? 1F - restante / (float) total : (pronta ? 1F : 0F);
        frac = net.minecraft.util.Mth.clamp(frac, 0F, 1F);
        int cheio = Math.round(bw * frac);
        if (cheio > 0) {
            g.fill(bx, by, bx + cheio, by + bh, pronta ? 0xFF67C24A : tipo.corAcento);
        }
        // sem rótulo solto: o percentual já mora na frase de fase abaixo
        // status da fase (a dica de "por que não começou" mora aqui)
        Component status = statusDaFase(fase);
        g.centeredText(this.font, status, lx(IMG_W / 2), ly(66),
                pronta ? 0xFF67C24A : tipo.corTexto);
    }

    /** Total de páginas do painel PRODUZ (2 linhas por página). */
    private int totalPaginas() {
        int linhas = receitasDoCatalogo().size();
        return Math.max(1, (linhas + LINHAS_POR_PAGINA - 1) / LINHAS_POR_PAGINA);
    }

    /** Cabeçalho do painel paginado: PRODUZ + ◀ página/total ▶ (se > 1). */
    private void desenharPaginacao(GuiGraphicsExtractor g) {
        int paginas = totalPaginas();
        this.setaAnterior = null;
        this.setaProxima = null;
        if (paginas <= 1) {
            return;
        }
        if (this.paginaReceitas >= paginas) {
            this.paginaReceitas = paginas - 1;
        }
        // setas: caixas de 12px na linha do rótulo, moldura da máquina
        int sy = ly(75);
        this.setaAnterior = new int[]{lx(IMG_W - 52), sy, 12, 12};
        this.setaProxima = new int[]{lx(IMG_W - 16), sy, 12, 12};
        desenharSeta(g, this.setaAnterior, "<", this.paginaReceitas > 0);
        desenharSeta(g, this.setaProxima, ">", this.paginaReceitas < paginas - 1);
        g.centeredText(this.font,
                Component.literal((this.paginaReceitas + 1) + "/" + paginas),
                lx(IMG_W - 28), sy + 2, tipo.corTextoFraco);
    }

    /** Caixa da seta: fundo faixa, contorno moldura, glifo aceso quando ativo. */
    private void desenharSeta(GuiGraphicsExtractor g, int[] r, String glifo,
            boolean ativa) {
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], tipo.corFaixa);
        g.outline(r[0], r[1], r[2], r[3], tipo.corMoldura);
        g.centeredText(this.font, Component.literal(glifo), r[0] + r[2] / 2,
                r[1] + 2, ativa ? 0xFFF3E9CF : shade(tipo.corFaixa, 0.7F));
    }

    /** Clique caiu numa das setas? (consome o clique e vira a página) */
    private boolean clicouSeta(double mx, double my) {
        for (int[] r : new int[][]{this.setaAnterior, this.setaProxima}) {
            if (r == null || mx < r[0] || mx >= r[0] + r[2]
                    || my < r[1] || my >= r[1] + r[3]) {
                continue;
            }
            int paginas = totalPaginas();
            if (r == this.setaAnterior && this.paginaReceitas > 0) {
                this.paginaReceitas--;
            } else if (r == this.setaProxima && this.paginaReceitas < paginas - 1) {
                this.paginaReceitas++;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev,
            boolean duplo) {
        // o mouse chega em coords de TELA; as setas vivem em coords de PAINEL
        if (clicouSeta(fx(ev.x()), fy(ev.y()))) {
            return true;
        }
        return super.mouseClicked(ev, duplo);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // rolando sobre o painel PRODUZ com catálogo paginado = vira a página
        if (totalPaginas() > 1 && dy != 0 && fx(mx) >= lx(6) && fx(mx) < lx(IMG_W - 6)
                && fy(my) >= ly(72) && fy(my) < ly(128)) {
            int paginas = totalPaginas();
            this.paginaReceitas = net.minecraft.util.Mth.clamp(
                    this.paginaReceitas + (dy < 0 ? 1 : -1), 0, paginas - 1);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    /** Painel "PRODUZ": receitas direto do catálogo (mesma fonte do Guia). */
    private void desenharReceitas(GuiGraphicsExtractor g, int mx, int my) {
        retangulosReceita.clear();
        stacksReceita.clear();
        g.text(this.font, Component.translatable("gui.intoxicantes.maquina.produz"),
                lx(8), ly(78), tipo.corTextoFraco);
        desenharPaginacao(g);
        g.fill(lx(6), ly(84), lx(IMG_W - 6), ly(128), shade(tipo.corFundo, 0.88F));
        List<ItemStack[]> linhas = receitasDoCatalogo();
        int inicio = this.paginaReceitas * LINHAS_POR_PAGINA;
        int fim = Math.min(linhas.size(), inicio + LINHAS_POR_PAGINA);
        int y = 90;
        for (int i = inicio; i < fim; i++) {
            ItemStack[] linha = linhas.get(i);
            int x = 14;
            for (int cel = 0; cel < linha.length; cel++) {
                ItemStack st = linha[cel];
                if (st == null) {
                    // separador visual (a seta do processo)
                    g.text(this.font, "→", lx(x), ly(y + 4), tipo.corTextoFraco);
                    x += 14;
                    continue;
                }
                if (st.getCount() < 0) {
                    // sinal "+" entre os dois insumos do caldeirão
                    g.text(this.font, "+", lx(x), ly(y + 4), tipo.corTextoFraco);
                    x += 12;
                    continue;
                }
                g.item(st, lx(x), ly(y));
                g.itemDecorations(this.font, st, lx(x), ly(y));
                x += 20;
            }
            int[] rect = {lx(8), ly(y - 2), IMG_W - 16, 22};
            retangulosReceita.add(rect);
            stacksReceita.add(linha);
            y += 20;
        }
    }

    /** Linhas do catálogo desta máquina: [in, (sinal), (sec), seta, out]. */
    private List<ItemStack[]> receitasDoCatalogo() {
        List<ItemStack[]> linhas = new ArrayList<>();
        switch (tipo) {
            case MOENDA -> {
                for (ProcessosBebida.Prima r : ProcessosBebida.receitasPrima(
                        MaquinaPrimaBlock.Tipo.MOENDA)) {
                    List<ItemStack> linha = new ArrayList<>();
                    linha.add(new ItemStack(r.input(), r.qtdIn()));
                    linha.add(null); // seta
                    linha.add(new ItemStack(r.output(), r.qtdOut()));
                    if (r.extraQtd() > 0) {
                        linha.add(sinalPositivo());
                        linha.add(new ItemStack(r.extraOut(), r.extraQtd()));
                    }
                    linhas.add(linha.toArray(new ItemStack[0]));
                }
            }
            case PRENSA -> {
                for (ProcessosBebida.Prima r : ProcessosBebida.receitasPrima(
                        MaquinaPrimaBlock.Tipo.PRENSA)) {
                    linhas.add(new ItemStack[]{
                            new ItemStack(r.input(), r.qtdIn()), null,
                            new ItemStack(r.output(), r.qtdOut())});
                }
            }
            case CALDEIRAO -> {
                for (ProcessosBebida.Prima r : ProcessosBebida.receitasPrima(
                        MaquinaPrimaBlock.Tipo.CALDEIRAO)) {
                    if (r.secIn() != null) {
                        linhas.add(new ItemStack[]{
                                new ItemStack(r.input(), r.qtdIn()),
                                sinalPositivo(), new ItemStack(r.secIn(), r.secQtd()),
                                null, new ItemStack(r.output(), r.qtdOut())});
                    } else {
                        linhas.add(new ItemStack[]{
                                new ItemStack(r.input(), r.qtdIn()), null,
                                new ItemStack(r.output(), r.qtdOut())});
                    }
                }
            }
            case DORNA -> {
                for (ProcessosBebida.Dorna r : ProcessosBebida.receitasDorna()) {
                    linhas.add(new ItemStack[]{
                            new ItemStack(r.input(), r.qtdIn()), null,
                            new ItemStack(r.output(), r.qtdOut())});
                }
            }
            case ALAMBIQUE -> {
                for (ProcessosBebida.Alambique r : ProcessosBebida.receitasAlambique()) {
                    linhas.add(new ItemStack[]{
                            new ItemStack(r.input(), r.qtdIn()), null,
                            new ItemStack(r.output(), r.qtdOut())});
                }
            }
            case BARRIL -> {
                for (ProcessosBebida.Barril r : ProcessosBebida.receitasBarril()) {
                    linhas.add(new ItemStack[]{
                            new ItemStack(r.input(), r.qtdIn()), null,
                            new ItemStack(r.bebidaFinal(),
                                    ProcessosBebida.garrafasPorLote(r))});
                }
            }
        }
        return linhas;
    }

    /** Marcador do "+" entre os dois insumos (count negativo = sinal). */
    private ItemStack sinalPositivo() {
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.PAPER);
        s.setCount(-1);
        return s;
    }

    // ==================================================== STATUS / FASES

    /**
     * A fase vem do ContainerData (índice 2). Cada BlockEntity numera as
     * próprias fases — a chave de lang é data-driven por máquina
     * ({@code gui.intoxicantes.maquina.<id>.fase.<n>}), sem colisão.
     */
    private Component statusDaFase(int fase) {
        if (fase == fasePronta()) {
            return Component.translatable(
                    "gui.intoxicantes.maquina." + tipo.id + ".fase." + fase);
        }
        int restante = menu.dado(0);
        int total = menu.dado(1);
        int pct = total > 0
                ? Math.max(0, Math.min(100, (total - restante) * 100 / total)) : 0;
        return Component.translatable(
                "gui.intoxicantes.maquina." + tipo.id + ".fase." + fase, pct);
    }

    /** A fase "produto pronto" de cada BE (2 na dorna/alambique, 3 barril, 4 prima). */
    private int fasePronta() {
        return switch (tipo) {
            case MOENDA, PRENSA, CALDEIRAO -> MaquinaPrimaBlockEntity.FASE_PRONTA;
            case DORNA -> DornaBebidaBlockEntity.FASE_PRONTA;
            case ALAMBIQUE -> AlambiqueBlockEntity.FASE_PRONTA;
            case BARRIL -> BarrilBebidaBlockEntity.FASE_PRONTA;
        };
    }

    // ==================================================== SLOTS / TOOLTIP

    /** Quadro do slot na cor da máquina (o vanilla desenha o fundo+item). */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mx, int my) {
        super.extractSlot(g, slot, mx, my);
        int cor = (slot.index < tipo.totalSlots())
                ? tipo.corAcento : shade(tipo.corFaixa, 0.85F);
        g.outline(lx(slot.x) - 1, ly(slot.y) - 1, 18, 18, cor);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mx, int my) {
        // mx/my chegam em coords de TELA; os retângulos foram guardados em
        // coords de PAINEL (desenho) — converte o mouse pro espaço do painel
        double px = fx(mx);
        double py = fy(my);
        super.extractTooltip(g, mx, my);
        for (int i = 0; i < retangulosReceita.size(); i++) {
            int[] r = retangulosReceita.get(i);
            if (px >= r[0] && px < r[0] + r[2] && py >= r[1] && py < r[1] + r[3]) {
                List<Component> tip = new ArrayList<>();
                ItemStack[] linha = stacksReceita.get(i);
                for (ItemStack st : linha) {
                    if (st == null || st.isEmpty() || st.getCount() < 0) {
                        continue;
                    }
                    tip.add(Component.translatable(
                            "gui.intoxicantes.maquina.receita_linha",
                            st.getCount(), st.getHoverName()));
                }
                g.setComponentTooltipForNextFrame(this.font, tip, mx, my);
                return;
            }
        }
    }

    // ==================================================== GERAL

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Escurece/clareia uma cor ARGB (molduras internas sem paleta extra). */
    private static int shade(int argb, float fator) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * fator));
        int gr = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * fator));
        int b = Math.min(255, Math.round((argb & 0xFF) * fator));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** Fonte exposta pras subclasses de ornamento (nada por enquanto). */
    protected Font fonte() {
        return this.font;
    }
}
