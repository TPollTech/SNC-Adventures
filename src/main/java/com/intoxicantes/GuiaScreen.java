package com.intoxicantes;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Stacks do guia (resolvidos de vivo — o item existe no registro comum).
 */
final class GuiaItemStacks {
    private GuiaItemStacks() {}

    static ItemStack guia() {
        return new ItemStack(IntoxicantesMod.GUIA_SNC);
    }
}

/**
 * A TELA DO GUIA DO SNC ADVENTURES — identidade própria (regra do AGENTS.md):
 * LIVRO DE COURO da esquina, NÃO a fachada verde do comércio.
 *
 * Estrutura: CAPA (livro fechado, costura + rebite) → SUMÁRIO (índice com
 * pontilhado) → CATEGORIA (lista de fichas) → ENTRADA (carimbo de tinta,
 * cadeia de processo, receita 3x3, fichas pautadas). Navegação por clique,
 * scroll e ESC/Backspace sempre voltam um nível.
 *
 * Desenho 100% vetorial pelo GuiGraphicsExtractor (padrão do projeto):
 * zero textura de GUI, hit-testing manual nas caixas desenhadas. Ícones são
 * ItemStack reais (os mesmos que o jogador vê no inventário).
 */
public class GuiaScreen extends Screen {

    // ==================================================== PALETA (couro + papel envelhecido)
    private static final int COURO = 0xFF4A3623;        // capa de couro verde-escuro
    private static final int COURO_CLARO = 0xFF5D4730;  // relevo do couro
    private static final int COURO_SOMBRA = 0xFF332415; // vinco da capa
    private static final int VERDE_CAPA = 0xFF0E5A2E;   // o verde da marca SÓ na capa
    private static final int VERDE_CAPA_ESC = 0xFF083D1E;
    private static final int OURO = 0xFFC8971E;         // rebite / fecho
    private static final int OURO_CLARO = 0xFFF2D06B;
    private static final int PAPEL = 0xFFEFE3C0;        // página envelhecida
    private static final int PAPEL_SOMBRA = 0xFFD9C79A; // mancha / dobra
    private static final int PAPEL_MANCHA = 0x20C9B27E; // mancha de umidade
    private static final int LOMBADA = 0xFF2E2113;      // lombada escura
    private static final int TINTA = 0xFF3A2E1C;        // texto no papel
    private static final int TINTA_FRACA = 0xFF8A7B5A;  // texto secundário
    private static final int TINTA_VERMELHA = 0xFF9C3B25; // carimbo / números de tinta
    private static final int OLIVA = 0xFF6B6B34;        // bordas tracejadas de máquina
    private static final int LINHA_PONTILHADA = 0x608A7B5A;
    private static final int LINHA_PAUTA = 0x38A09060;

    // ==================================================== LAYOUT
    private static final int LARGURA = 240;
    private static final int ALTURA = 230;

    private enum Modo { CAPA, SUMARIO, CATEGORIA, ENTRADA }

    private Modo modo = Modo.CAPA;
    private int categoriaSelecionada;
    private int entradaSelecionada;
    private int x0;
    private int y0;
    private int altPainel;
    private int scroll;
    private boolean arrastandoBarra;
    private double arrastoOffset;
    /** Entradas visíveis na página atual (para hit-testing). */
    private final java.util.List<int[]> areasClicaveis = new java.util.ArrayList<>();
    private final java.util.List<Runnable> acoesClicaveis = new java.util.ArrayList<>();

    public GuiaScreen(Component quemAbriu) {
        super(Component.translatable("guia.intoxicantes.titulo"));
    }

    // ==================================================== GEOMETRIA

    @Override
    protected void init() {
        this.altPainel = Math.min(ALTURA, this.height - 10);
        this.x0 = (this.width - LARGURA) / 2;
        this.y0 = (this.height - altPainel) / 2;
        this.scroll = 0;
    }

    /** Área do papel (dentro da capa, à direita da lombada). */
    private int papelX() {
        return x0 + 14;
    }

    private int papelY() {
        return y0 + 8;
    }

    private int papelL() {
        return LARGURA - 14 - 8;
    }

    private int papelA() {
        return altPainel - 16 - 24; // desconta o rodapé
    }

    private boolean dentro(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private int maxScroll() {
        int conteudo = switch (modo) {
            case SUMARIO -> totalAlturaSumario();
            case CATEGORIA -> totalAlturaCategoria();
            case ENTRADA -> totalAlturaEntrada();
            default -> 0;
        };
        return Math.max(0, conteudo - papelA());
    }

    private int totalAlturaSumario() {
        int h = 26;
        List<GuiaConteudo.GuiaCategoria> cats = GuiaConteudo.categorias();
        h += cats.size() * 16;
        h += 10;
        return h;
    }

    private int totalAlturaCategoria() {
        int h = 26;
        for (GuiaConteudo.GuiaEntrada e : categoria().entradas) {
            h += 30; // nome + carimbo
        }
        h += 10;
        return h;
    }

    private int totalAlturaEntrada() {
        int h = 24;
        for (Object p : entrada().paginas) {
            h += alturaPagina(p);
        }
        h += 8;
        return h;
    }

    private int alturaPagina(Object p) {
        if (p instanceof GuiaConteudo.PagTexto || p instanceof GuiaConteudo.PagTitulo) {
            return p instanceof GuiaConteudo.PagTitulo ? 15 : wrapLinhas(textoDe(p)) * 10 + 2;
        }
        if (p instanceof GuiaConteudo.PagDica d) {
            String texto = "\u2727 " + Component.translatable("guia.intoxicantes." + d.chave()).getString();
            return Math.max(24, wrapLinhas(texto, papelL() - 24) * 10 + 12);
        }
        if (p instanceof GuiaConteudo.PagItem item) {
            String texto = Component.translatable("guia.intoxicantes." + item.chave()).getString();
            return Math.max(18, wrapLinhas(texto, papelL() - 36) * 10 + 6);
        }
        if (p instanceof GuiaConteudo.PagItemDinamica item) {
            String texto = Component.translatable("guia.intoxicantes." + item.chave(),
                    item.valor1().get(), item.valor2().get()).getString();
            return Math.max(18, wrapLinhas(texto, papelL() - 36) * 10 + 6);
        }
        if (p instanceof GuiaConteudo.PagLinha) {
            return 18;
        }
        if (p instanceof GuiaConteudo.PagCadeia cadeia) {
            return cadeia.passos().size() * 20 + 6;
        }
        if (p instanceof GuiaConteudo.ReceitaGrid) {
            return 58;
        }
        return 10;
    }

    /** Quantas linhas o texto ocupa no papel (quebra manual pelo width da fonte). */
    private int wrapLinhas(String texto) {
        return wrapLinhas(texto, papelL() - 16);
    }

    private int wrapLinhas(String texto, int largura) {
        if (texto == null || texto.isEmpty()) {
            return 1;
        }
        String[] palavras = texto.split(" ");
        int linhas = 1;
        int atual = 0;
        for (String palavra : palavras) {
            int w = this.font.width(palavra) + (atual > 0 ? this.font.width(" ") : 0);
            if (atual + w > largura && atual > 0) {
                linhas++;
                atual = this.font.width(palavra);
            } else {
                atual += w;
            }
        }
        return linhas;
    }

    /** Resolve o texto de uma página (lang) — usado pra medir. */
    private String textoDe(Object p) {
        if (p instanceof GuiaConteudo.PagTexto t) {
            return Component.translatable("guia.intoxicantes." + t.chave()).getString();
        }
        return "";
    }

    private GuiaConteudo.GuiaCategoria categoria() {
        return GuiaConteudo.categorias().get(categoriaSelecionada);
    }

    private GuiaConteudo.GuiaEntrada entrada() {
        return categoria().entradas.get(entradaSelecionada);
    }

    // ==================================================== NAVEGAÇÃO

    private void ir(Modo novo) {
        modo = novo;
        scroll = 0;
    }

    private void abrirEntrada(int cat, int ent) {
        categoriaSelecionada = cat;
        entradaSelecionada = ent;
        ir(Modo.ENTRADA);
    }

    /** Volta um nível (ESC/Backspace). */
    private void voltar() {
        switch (modo) {
            case SUMARIO -> ir(Modo.CAPA);
            case CATEGORIA -> ir(Modo.SUMARIO);
            case ENTRADA -> ir(Modo.CATEGORIA);
            default -> onClose();
        }
    }

    // ==================================================== ENTRADA (mouse/teclado)

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean duplo) {
        double mx = ev.x();
        double my = ev.y();
        if (ev.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(ev, duplo);
        }
        // áreas clicáveis do conteúdo (preenchidas no extractRenderState)
        for (int i = 0; i < areasClicaveis.size(); i++) {
            if (dentro(mx, my, areasClicaveis.get(i))) {
                acoesClicaveis.get(i).run();
                return true;
            }
        }
        // scrollbar
        if (maxScroll() > 0 && mx >= x0 + LARGURA - 12 && mx < x0 + LARGURA - 6
                && my >= papelY() && my < papelY() + papelA()) {
            arrastandoBarra = true;
            arrastoOffset = 6;
            atualizarArrasto(my);
            return true;
        }
        return super.mouseClicked(ev, duplo);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        arrastandoBarra = false;
        return super.mouseReleased(ev);
    }

    private void atualizarArrasto(double my) {
        int travel = Math.max(1, papelA() - thumbAltura());
        scroll = Mth.clamp(
                (int) Math.round((my - papelY() - arrastoOffset) * maxScroll() / travel),
                0, maxScroll());
    }

    private int thumbAltura() {
        return Math.max(12, papelA() * papelA() / Math.max(1, papelA() + maxScroll()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll() > 0 && dy != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy) * 12, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (arrastandoBarra && maxScroll() > 0) {
            atualizarArrasto(ev.y());
            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int tecla = ev.key();
        if (tecla == InputConstants.KEY_ESCAPE
                || tecla == InputConstants.KEY_BACKSPACE
                || this.minecraft.options.keyInventory.matches(ev)) {
            voltar();
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    // ==================================================== DESENHO

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        areasClicaveis.clear();
        acoesClicaveis.clear();
        desenharDim(g);
        desenharCapaDeCouro(g);
        desenharLombada(g);
        switch (modo) {
            case CAPA -> desenharCapa(g, parcial);
            case SUMARIO -> desenharSumario(g, mx, my);
            case CATEGORIA -> desenharCategoria(g, mx, my);
            case ENTRADA -> desenharEntrada(g, mx, my);
        }
        desenharRodape(g, mx, my);
        desenharScrollbar(g);
    }

    /** Escurece o mundo atrás do livro. */
    private void desenharDim(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, 0x88000000);
    }

    /** A capa de couro com moldura dupla + sombra. */
    private void desenharCapaDeCouro(GuiGraphicsExtractor g) {
        g.fill(x0 + 4, y0 + 4, x0 + LARGURA + 4, y0 + altPainel + 4, 0x66000000); // sombra
        g.fill(x0, y0, x0 + LARGURA, y0 + altPainel, COURO);
        g.outline(x0, y0, LARGURA, altPainel, COURO_SOMBRA);
        g.outline(x0 + 2, y0 + 2, LARGURA - 4, altPainel - 4, COURO_CLARO);
        g.outline(x0 + 3, y0 + 3, LARGURA - 6, altPainel - 6, COURO_SOMBRA);
    }

    /** Lombada costurada à esquerda (a "capa" envolve o papel). */
    private void desenharLombada(GuiGraphicsExtractor g) {
        g.fill(x0 + 3, y0 + 6, x0 + 11, y0 + altPainel - 6, LOMBADA);
        // costura pontilhada dupla
        for (int y = y0 + 10; y < y0 + altPainel - 8; y += 6) {
            g.fill(x0 + 5, y, x0 + 7, y + 3, 0xFF8A7455);
        }
    }

    /** Página de papel com manchas + vinheta da dobra. */
    private void desenharPapel(GuiGraphicsExtractor g) {
        g.fill(papelX(), papelY(), papelX() + papelL(), papelY() + papelA(), PAPEL);
        g.outline(papelX(), papelY(), papelL(), papelA(), PAPEL_SOMBRA);
        long seed = 42;
        for (int i = 0; i < 7; i++) {
            seed = seed * 31 + i;
            int mx2 = papelX() + (int) ((seed >> 8) % Math.max(1, papelL() - 20));
            int my2 = papelY() + (int) ((seed >> 16) % Math.max(1, papelA() - 14));
            g.fill(mx2, my2, mx2 + 5 + (int) ((seed >> 3) % 6), my2 + 3 + (int) ((seed >> 6) % 4),
                    PAPEL_MANCHA);
        }
        // sombra do vinco (lombada)
        g.fill(papelX(), papelY(), papelX() + 3, papelY() + papelA(), PAPEL_SOMBRA);
    }

    /** CAPA fechada: título da marca + botão ABRIR. */
    private void desenharCapa(GuiGraphicsExtractor g, float parcial) {
        int cx = x0 + LARGURA / 2;
        int cy = y0 + altPainel / 2 - 26;
        int larguraCapa = LARGURA - 44;
        // painel verde da marca (a capa inteira é de couro; o verde é a etiqueta)
        g.fill(cx - larguraCapa / 2, cy - 34, cx + larguraCapa / 2, cy + 34, VERDE_CAPA);
        g.outline(cx - larguraCapa / 2, cy - 34, larguraCapa, 68, VERDE_CAPA_ESC);
        g.outline(cx - larguraCapa / 2 + 1, cy - 33, larguraCapa - 2, 66, OURO);
        // costura pontilhada ao redor
        for (int px = cx - larguraCapa / 2 + 3; px < cx + larguraCapa / 2 - 3; px += 6) {
            g.fill(px, cy - 31, px + 3, cy - 30, 0x60F2D06B);
            g.fill(px, cy + 30, px + 3, cy + 31, 0x60F2D06B);
        }
        // ícone do livro (ItemStack real do guia)
        ItemStack guia = GuiaItemStacks.guia();
        g.item(guia, cx - 8, cy - 26);
        // título
        Component titulo = Component.translatable("guia.intoxicantes.capa.titulo");
        g.centeredText(this.font, titulo, cx, cy - 6, OURO_CLARO);
        Component sub = Component.translatable("guia.intoxicantes.capa.sub");
        g.centeredText(this.font, sub, cx, cy + 6, 0xFFEFE3C0);
        // pulso do botão ABRIR
        int pulso = (int) (Math.sin(parcial * 0.1F) * 0.5F + 0.5F);
        int corBotao = pulso > 0.5 ? OURO : OURO_CLARO;
        int[] botao = new int[]{cx - 44, y0 + altPainel - 74, 88, 16};
        g.fill(botao[0], botao[1], botao[0] + botao[2], botao[1] + botao[3], corBotao);
        g.outline(botao[0], botao[1], botao[2], botao[3], COURO_SOMBRA);
        g.centeredText(this.font, Component.translatable("guia.intoxicantes.capa.abrir"),
                cx, botao[1] + 4, VERDE_CAPA_ESC);
        areasClicaveis.add(botao);
        acoesClicaveis.add(() -> ir(Modo.SUMARIO));
        // rebite
        g.fill(x0 + LARGURA - 14, y0 + 12, x0 + LARGURA - 10, y0 + 16, OURO);
    }

    /** SUMÁRIO: índice de livro — nome … pontilhado … "cap. N". */
    private void desenharSumario(GuiGraphicsExtractor g, int mx, int my) {
        desenharPapel(g);
        g.enableScissor(papelX(), papelY(), papelX() + papelL(), papelY() + papelA());
        int y = papelY() + 4 - scroll;
        Component titulo = Component.translatable("guia.intoxicantes.sumario");
        g.text(this.font, titulo, papelX() + 8, y, TINTA, false);
        g.verticalLine(papelX() + 6, y + 10, y + 11, TINTA);
        y += 18;
        List<GuiaConteudo.GuiaCategoria> cats = GuiaConteudo.categorias();
        for (int i = 0; i < cats.size(); i++) {
            GuiaConteudo.GuiaCategoria c = cats.get(i);
            boolean hover = dentro(mx, my, new int[]{papelX() + 4, y - 2, papelL() - 10, 14});
            if (hover) {
                g.fill(papelX() + 4, y - 2, papelX() + papelL() - 6, y + 12, 0x28FFFFFF);
            }
            String nome = c.titulo().getString();
            String cap = "cap. " + (i + 1);
            int wNome = this.font.width(nome);
            int wCap = this.font.width(cap);
            g.text(this.font, nome, papelX() + 8, y, hover ? TINTA_VERMELHA : TINTA, false);
            g.text(this.font, cap, papelX() + papelL() - 8 - wCap, y, TINTA_FRACA, false);
            // pontilhado de índice
            for (int px = papelX() + 8 + wNome + 4; px < papelX() + papelL() - 12 - wCap; px += 4) {
                g.fill(px, y + 7, px + 2, y + 8, LINHA_PONTILHADA);
            }
            int catIdx = i;
            areasClicaveis.add(new int[]{papelX() + 4, y - 2, papelL() - 10, 14});
            acoesClicaveis.add(() -> {
                categoriaSelecionada = catIdx;
                ir(Modo.CATEGORIA);
            });
            y += 16;
        }
        g.disableScissor();
    }

    /** CATEGORIA: fichas das entradas com carimbo da cadeia. */
    private void desenharCategoria(GuiGraphicsExtractor g, int mx, int my) {
        desenharPapel(g);
        // carimbo de cabeçalho
        desenharCarimbo(g, categoria().titulo(), papelX() + papelL() / 2, papelY() + 10);
        g.enableScissor(papelX(), papelY() + 22, papelX() + papelL(), papelY() + papelA());
        int y = papelY() + 30 - scroll;
        List<GuiaConteudo.GuiaEntrada> entradas = categoria().entradas;
        for (int i = 0; i < entradas.size(); i++) {
            GuiaConteudo.GuiaEntrada e = entradas.get(i);
            boolean hover = dentro(mx, my, new int[]{papelX() + 4, y, papelL() - 10, 28});
            if (hover) {
                g.fill(papelX() + 4, y, papelX() + papelL() - 6, y + 28, 0x28FFFFFF);
            }
            // ícone + nome
            g.item(e.icone.get(), papelX() + 8, y + 2);
            String nome = e.titulo().getString();
            g.text(this.font, nome, papelX() + 28, y + 2, hover ? TINTA_VERMELHA : TINTA, false);
            // carimbo da cadeia (mini, linha de baixo)
            String carimbo = Component.translatable(
                    "guia.intoxicantes." + e.id + ".carimbo").getString();
            g.text(this.font, carimbo, papelX() + 28, y + 12, TINTA_FRACA, false);
            // linha pontilhada de ficha
            if (i < entradas.size() - 1) {
                for (int px = papelX() + 6; px < papelX() + papelL() - 8; px += 5) {
                    g.fill(px, y + 27, px + 2, y + 28, LINHA_PONTILHADA);
                }
            }
            int entIdx = i;
            areasClicaveis.add(new int[]{papelX() + 4, y, papelL() - 10, 28});
            acoesClicaveis.add(() -> abrirEntrada(categoriaSelecionada, entIdx));
            y += 30;
        }
        g.disableScissor();
    }

    /** ENTRADA: carimbo + páginas (cadeia, receita, fichas). */
    private void desenharEntrada(GuiGraphicsExtractor g, int mx, int my) {
        desenharPapel(g);
        GuiaConteudo.GuiaEntrada e = entrada();
        // carimbo do capítulo
        Component catTitulo = categoria().titulo();
        g.enableScissor(papelX(), papelY(), papelX() + papelL(), papelY() + papelA());
        desenharCarimbo(g, catTitulo, papelX() + papelL() - 34, papelY() + 10 - scroll);
        int y = papelY() + 4 - scroll;
        // título da entrada com ícone
        g.item(e.icone.get(), papelX() + 8, y + 2);
        String titulo = e.titulo().getString();
        g.text(this.font, titulo, papelX() + 28, y + 4, TINTA, false);
        y += 22;
        for (Object p : e.paginas) {
            y = desenharPagina(g, p, y, mx, my);
        }
        g.disableScissor();
    }

    /** Desenha uma página na altura y; devolve a nova altura. */
    private int desenharPagina(GuiGraphicsExtractor g, Object p, int y, int mx, int my) {
        int px = papelX() + 8;
        int largura = papelL() - 16;
        if (p instanceof GuiaConteudo.PagTitulo t) {
            String s = Component.translatable("guia.intoxicantes." + t.chave()).getString();
            g.text(this.font, s, px, y, TINTA_VERMELHA, false);
        g.outline(px, y + 9, largura, 1, PAPEL_SOMBRA);
        g.outline(px, y + 10, largura, 1, 0x40D9C79A);
        return y + 15;
        }
        if (p instanceof GuiaConteudo.PagTexto t2) {
            String texto = Component.translatable("guia.intoxicantes." + t2.chave()).getString();
            return desenharTextoQuebrado(g, texto, px, y, largura, TINTA);
        }
        if (p instanceof GuiaConteudo.PagDica d) {
            String s = Component.translatable("guia.intoxicantes." + d.chave()).getString();
            int altura = alturaPagina(d);
            int alt = altura - 2;
            g.fill(px, y, px + largura, y + alt, 0x18C8971E);
            // borda "carimbo de rabisco" (arredondada irregular)
            g.outline(px, y, largura, alt, OURO);
            g.outline(px + 1, y + 1, largura - 2, alt - 2, 0x60C8971E);
            String comPrefixo = "\u2727 " + s;
            desenharTextoQuebrado(g, comPrefixo, px + 4, y + 4, largura - 8, OURO);
            return y + altura;
        }
        if (p instanceof GuiaConteudo.PagItem it) {
            g.item(it.icone().get(), px, y + 1);
            String s = Component.translatable("guia.intoxicantes." + it.chave()).getString();
            desenharTextoQuebrado(g, s, px + 20, y + 2, largura - 20, TINTA);
            // pauta
            int altura = alturaPagina(it);
            g.fill(px + 20, y + altura - 3, px + largura, y + altura - 2, LINHA_PAUTA);
            return y + altura;
        }
        if (p instanceof GuiaConteudo.PagItemDinamica din) {
            g.item(din.icone().get(), px, y + 1);
            String s = Component.translatable(
                    "guia.intoxicantes." + din.chave(),
                    din.valor1().get(), din.valor2().get()).getString();
            desenharTextoQuebrado(g, s, px + 20, y + 2, largura - 20, TINTA);
            int altura = alturaPagina(din);
            g.fill(px + 20, y + altura - 3, px + largura, y + altura - 2, LINHA_PAUTA);
            return y + altura;
        }
        if (p instanceof GuiaConteudo.PagLinha l) {
            String s = Component.translatable("guia.intoxicantes." + l.chave()).getString();
            g.fill(px, y + 4, px + 3, y + 8, TINTA_VERMELHA); // marco de tinta
            desenharTextoQuebrado(g, s, px + 8, y + 1, largura - 8, TINTA);
            return y + 17;
        }
        if (p instanceof GuiaConteudo.PagCadeia cadeia) {
            for (GuiaConteudo.PagCadeia.Passo passo : cadeia.passos()) {
                y = desenharPasso(g, passo, y, px, largura);
            }
            return y + 4;
        }
        if (p instanceof GuiaConteudo.ReceitaGrid receita) {
            return desenharReceita(g, receita, y, px, largura, mx, my);
        }
        return y + 8;
    }

    /** Passo da cadeia: [seta] ícone xN [caixa de tempo] [legenda]. */
    private int desenharPasso(GuiGraphicsExtractor g, GuiaConteudo.PagCadeia.Passo passo,
            int y, int px, int largura) {
        int cx = px + 8;
        if (passo.setaAntes()) {
            g.text(this.font, "\u25BC", cx - 2, y + 2, TINTA_VERMELHA, false);
        }
        ItemStack stack = passo.icone().get();
        g.item(stack, cx + 8, y + 1);
        g.itemDecorations(this.font, stack, cx + 8, y + 1);
        int tx = cx + 28;
        if (passo.tempo() != null) {
            String t = passo.tempo();
            int w = this.font.width(t) + 8;
            g.fill(tx, y + 1, tx + w, y + 13, 0x186B6B34);
            g.outline(tx, y + 1, w, 12, OLIVA);
            g.text(this.font, t, tx + 4, y + 3, OLIVA, false);
            tx += w + 6;
        }
        if (passo.chaveLegenda() != null) {
            String legenda = Component.translatable(
                    "guia.intoxicantes." + passo.chaveLegenda()).getString();
            g.text(this.font, legenda, tx, y + 3, TINTA_FRACA, false);
        }
        g.fill(px + 20, y + 17, px + largura, y + 18, LINHA_PAUTA);
        return y + 20;
    }

    /** Receita 3x3: grade de slots + seta + resultado (tooltip de dica). */
    private int desenharReceita(GuiGraphicsExtractor g, GuiaConteudo.ReceitaGrid receita,
            int y, int px, int largura, int mx, int my) {
        int slot = 18;
        int grade = slot * 3;
        // moldura da grade
        g.fill(px, y, px + grade + 4, y + grade + 4, 0xFFD9C79A);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = px + 2 + c * slot;
                int sy = y + 2 + r * slot;
                g.fill(sx, sy, sx + slot, sy + slot, PAPEL_SOMBRA);
            }
        }
        // ingredientes na ordem (shapeless: linha a linha)
        int idx = 0;
        for (GuiaConteudo.ReceitaGrid.Ingrediente ing : receita.ingredientes()) {
            if (idx >= 9) {
                break;
            }
            int c = idx % 3;
            int r = idx / 3;
            int sx = px + 2 + c * slot;
            int sy = y + 2 + r * slot;
            ItemStack stack = new ItemStack(ing.item(), Math.max(1, ing.qtd()));
            g.item(stack, sx + 1, sy + 1);
            g.itemDecorations(this.font, stack, sx + 1, sy + 1);
            idx++;
        }
        // seta
        int setaX = px + grade + 8;
        g.text(this.font, "\u27A4", setaX, y + grade / 2 - 4, TINTA_VERMELHA, false);
        // resultado
        int rx = setaX + 14;
        ItemStack res = receita.resultado();
        g.fill(rx, y + grade / 2 - 10, rx + 22, y + grade / 2 + 12, 0xFFC8971E);
        g.item(res, rx + 3, y + grade / 2 - 7);
        g.itemDecorations(this.font, res, rx + 3, y + grade / 2 - 7);
        // dica de tooltip
        if (mx >= px && mx < rx + 22 && my >= y && my < y + grade + 4) {
            List<Component> tip = List.of(
                    Component.translatable("guia.intoxicantes.receita.dica"));
            g.setComponentTooltipForNextFrame(this.font, tip, mx, my);
        }
        return y + grade + 8;
    }

    /** Carimbo inclinado com moldura dupla (o cabeçalho "de tinta"). */
    private void desenharCarimbo(GuiGraphicsExtractor g, Component texto, int cx, int cy) {
        String s = texto.getString();
        int w = this.font.width(s) + 12;
        int h = 14;
        int left = cx - w / 2;
        int top = cy - h / 2;
        g.fill(left, top, left + w, top + h, 0x309C3B25);
        g.outline(left, top, w, h, TINTA_VERMELHA);
        g.outline(left + 2, top + 2, w - 4, h - 4, 0x809C3B25);
        // v1.2.52: SEM sombra (a sombra preta sobre o papel claro lia como negrito forte)
        g.text(this.font, texto, cx - this.font.width(texto.getString()) / 2, top + 3, TINTA_VERMELHA, false);
    }

    /** Texto com quebra de linha manual (o guia não usa componentes multi-linha). */
    private int desenharTextoQuebrado(GuiGraphicsExtractor g, String texto, int x, int y,
            int largura, int cor) {
        String[] palavras = texto.split(" ");
        int linha = 0;
        StringBuilder atual = new StringBuilder();
        for (String palavra : palavras) {
            String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (this.font.width(tentativa) > largura && !atual.isEmpty()) {
                g.text(this.font, atual.toString(), x, y + linha * 10, cor, false);
                linha++;
                atual = new StringBuilder(palavra);
            } else {
                atual = new StringBuilder(tentativa);
            }
        }
        if (!atual.isEmpty()) {
            g.text(this.font, atual.toString(), x, y + linha * 10, cor, false);
            linha++;
        }
        return y + linha * 10 + 2;
    }

    /** Rodapé: botão Voltar + Índice + dica de navegação. */
    private void desenharRodape(GuiGraphicsExtractor g, int mx, int my) {
        int y = y0 + altPainel - 24;
        g.horizontalLine(x0 + 14, x0 + LARGURA - 8, y - 3, PAPEL_SOMBRA);
        int[] voltar = new int[]{x0 + 14, y + 2, 58, 16};
        boolean hoverV = dentro(mx, my, voltar);
        g.fill(voltar[0], voltar[1], voltar[0] + voltar[2], voltar[1] + voltar[3],
                hoverV ? COURO_CLARO : LOMBADA);
        g.outline(voltar[0], voltar[1], voltar[2], voltar[3], COURO_SOMBRA);
        g.centeredText(this.font, Component.translatable("guia.intoxicantes.nav.voltar"),
                voltar[0] + voltar[2] / 2, voltar[1] + 4, 0xFFEFE3C0);
        areasClicaveis.add(voltar);
        acoesClicaveis.add(this::voltar);
        if (modo == Modo.CATEGORIA || modo == Modo.ENTRADA) {
            int[] indice = new int[]{voltar[0] + voltar[2] + 4, y + 2, 52, 16};
            boolean hoverI = dentro(mx, my, indice);
            g.fill(indice[0], indice[1], indice[0] + indice[2], indice[1] + indice[3],
                    hoverI ? COURO_CLARO : LOMBADA);
            g.outline(indice[0], indice[1], indice[2], indice[3], COURO_SOMBRA);
            g.centeredText(this.font, Component.translatable("guia.intoxicantes.nav.indice"),
                    indice[0] + indice[2] / 2, indice[1] + 4, 0xFFEFE3C0);
            areasClicaveis.add(indice);
            acoesClicaveis.add(() -> ir(Modo.SUMARIO));
        }
        String dica = Component.translatable("guia.intoxicantes.nav.esc").getString();
        g.text(this.font, dica, x0 + LARGURA - 8 - this.font.width(dica), y + 6, 0xFFD9C79A);
    }

    /** Scrollbar de couro com puxador dourado. */
    private void desenharScrollbar(GuiGraphicsExtractor g) {
        if (maxScroll() <= 0) {
            return;
        }
        int barraX = x0 + LARGURA - 11;
        g.fill(barraX, papelY(), barraX + 4, papelY() + papelA(), 0xFF332415);
        int altAlmofada = thumbAltura();
        int py = papelY() + (papelA() - altAlmofada) * scroll / maxScroll();
        g.fill(barraX - 1, py, barraX + 5, py + altAlmofada, 0xFF7A5C38);
        g.outline(barraX - 1, py, 6, altAlmofada, OURO);
        g.fill(barraX + 1, py + 3, barraX + 3, py + altAlmofada - 3, OURO_CLARO);
    }
}
