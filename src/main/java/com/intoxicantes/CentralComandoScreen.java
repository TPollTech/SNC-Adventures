package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * A CENTRAL DE COMANDO DO PAINEL DE LED (v1.2.36) — o controle remoto:
 * edita as 2 linhas de texto (teclado de verdade, caret piscando), escolhe
 * a COR do LED numa paleta, ajusta o BRILHO e o MODO (fixo/scroll) — painel
 * craftável; pro letreiro do mercado só texto+cor (e a trava de chave é
 * respeitada no servidor).
 *
 * Mesma disciplina das telas do mod: desenho 100% vetorial pelo
 * GuiGraphicsExtractor, zero widget vanilla; o PREVIEW no topo usa a
 * FONTE DE LED 5×7 REAL (LedFont) ampliada — o que você vê é o que o
 * painel mostra. APLICAR manda o payload; o servidor revalida tudo.
 */
public class CentralComandoScreen extends Screen {
    // ==================================================== PALETA (a cara do display)
    private static final int FUNDO = 0xFF101418;
    private static final int FUNDO_CLARO = 0xFF1A2026;
    private static final int MOLDURA = 0xFF2E3842;
    private static final int TELA_DESLIGADA = 0xFF060A0D;
    private static final int TEXTO = 0xFFD8E2EA;
    private static final int TEXTO_FRACO = 0xFF6E7E8C;
    private static final int ACENTO = 0xFF39FF6E;
    private static final int CAMPO = 0xFF060A0D;
    private static final int CAMPO_BORDA = 0xFF39FF6E;
    private static final int CAMPO_BORDA_FRACA = 0xFF3A4650;
    private static final int PERIGO = 0xFFFF5A48;

    /** A paleta do LED: 10 cores que o painel aceita (clique pra escolher). */
    private static final int[] CORES = {
            0x39FF6E, 0xA8FF3E, 0xFFD24A, 0xFF8C1A, 0xFF5A48,
            0xFF6EC7, 0xB266FF, 0x3EA6FF, 0x40E0D0, 0xFFFFFF};

    private static final int LARGURA = 240;
    private static final int ALTURA = 232;

    private final boolean letreiro;
    private final net.minecraft.core.BlockPos pos;
    private final List<String> linhas = new ArrayList<>(List.of("", ""));
    private final int maxCaracteres;
    private int cor;
    private int brilho;
    private int modo;
    private boolean vinculado;
    private boolean trancado;

    /** Linha em edição (0/1) e posição do caret. */
    private int linhaAtiva;
    private int caret;
    private boolean aplicou = true; // sem aviso de pendente no 1º frame

    public CentralComandoScreen(CentralComandoNetworking.AbrirCentralPayload p) {
        super(Component.translatable("gui.intoxicantes.central.titulo"));
        this.letreiro = p.letreiro();
        this.pos = p.pos();
        this.maxCaracteres = letreiro ? 24 : 16;
        List<String> recebidas = p.linhas();
        for (int i = 0; i < 2; i++) {
            if (i < recebidas.size()) {
                linhas.set(i, recebidas.get(i).substring(0,
                        Math.min(recebidas.get(i).length(), maxCaracteres)));
            }
        }
        if (linhas.get(0).isEmpty() && linhas.get(1).isEmpty()) {
            linhas.set(0, letreiro ? "MERCADO ESQUINÃO" : "PAINEL LED");
        }
        this.cor = p.cor();
        this.brilho = p.brilho();
        this.modo = p.modo();
        this.vinculado = p.vinculado();
        this.trancado = p.trancado();
        this.caret = linhas.get(0).length();
    }

    // ==================================================== GEOMETRIA

    private int x0;
    private int y0;

    @Override
    protected void init() {
        this.x0 = (this.width - LARGURA) / 2;
        this.y0 = (this.height - ALTURA) / 2;
    }

    private int[] campoLinha(int i) {
        int y = this.y0 + 66 + i * 22;
        return new int[]{this.x0 + 56, y, 158, 16};
    }

    private int[] paletaBox(int i) {
        int px = this.x0 + 12 + i * 22;
        int py = this.y0 + 122;
        return new int[]{px, py, 16, 16};
    }

    private int[] barraBrilho() {
        return new int[]{this.x0 + 56, this.y0 + 148, 120, 10};
    }

    private int[] abaModo(boolean scroll) {
        return new int[]{this.x0 + (scroll ? 124 : 56), this.y0 + 168, 66, 16};
    }

    private int[] botaoAplicar() {
        return new int[]{this.x0 + LARGURA - 96, this.y0 + ALTURA - 26, 88, 18};
    }

    private int[] botaoFechar() {
        return new int[]{this.x0 + 8, this.y0 + ALTURA - 26, 56, 18};
    }

    private boolean dentro(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================================================== ENTRADA

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean duplo) {
        double mx = ev.x();
        double my = ev.y();
        // campos de texto: foca a linha e posiciona o caret pelo clique
        for (int i = 0; i < 2; i++) {
            if (dentro(mx, my, campoLinha(i))) {
                linhaAtiva = i;
                caret = caretPorX(linhas.get(i), mx - campoLinha(i)[0] - 4);
                return true;
            }
        }
        // paleta
        for (int i = 0; i < CORES.length; i++) {
            if (dentro(mx, my, paletaBox(i))) {
                cor = CORES[i];
                return true;
            }
        }
        // brilho (só painel)
        if (!letreiro && dentro(mx, my, barraBrilho())) {
            int[] b = barraBrilho();
            brilho = Mth.clamp((int) Math.round((mx - b[0]) / (double) b[2] * 15.0), 0, 15);
            return true;
        }
        // modo (só painel)
        if (!letreiro) {
            if (dentro(mx, my, abaModo(false))) {
                modo = PainelLedBlockEntity.MODO_FIXO;
                return true;
            }
            if (dentro(mx, my, abaModo(true))) {
                modo = PainelLedBlockEntity.MODO_SCROLL;
                return true;
            }
        }
        if (dentro(mx, my, botaoAplicar())) {
            aplicar();
            return true;
        }
        if (dentro(mx, my, botaoFechar())) {
            onClose();
            return true;
        }
        return super.mouseClicked(ev, duplo);
    }

    /** Índice do caret a partir do X do clique (largura da fonte vanilla). */
    private int caretPorX(String texto, double dx) {
        int melhor = texto.length();
        for (int i = 0; i <= texto.length(); i++) {
            if (this.font.width(texto.substring(0, i)) >= dx) {
                melhor = i;
                break;
            }
        }
        return melhor;
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int tecla = ev.key();
        String linha = linhas.get(linhaAtiva);
        if (tecla == 256) { // ESC
            onClose();
            return true;
        }
        if (tecla == 257 || tecla == 335) { // ENTER / numpad
            aplicar();
            return true;
        }
        if (tecla == 258) { // TAB: alterna o campo
            linhaAtiva = (linhaAtiva + 1) % 2;
            caret = linhas.get(linhaAtiva).length();
            return true;
        }
        if (tecla == 259) { // BACKSPACE
            if (caret > 0) {
                linhas.set(linhaAtiva, linha.substring(0, caret - 1) + linha.substring(caret));
                caret--;
            }
            return true;
        }
        if (tecla == 261) { // DELETE
            if (caret < linha.length()) {
                linhas.set(linhaAtiva, linha.substring(0, caret) + linha.substring(caret + 1));
            }
            return true;
        }
        if (tecla == 263) { // LEFT
            caret = Math.max(0, caret - 1);
            return true;
        }
        if (tecla == 262) { // RIGHT
            caret = Math.min(linha.length(), caret + 1);
            return true;
        }
        if (tecla == 268) { // HOME
            caret = 0;
            return true;
        }
        if (tecla == 269) { // END
            caret = linha.length();
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent ev) {
        String linha = linhas.get(linhaAtiva);
        if (ev.isAllowedChatCharacter() && linha.length() < maxCaracteres) {
            linhas.set(linhaAtiva, linha.substring(0, caret) + ev.codepointAsString()
                    + linha.substring(caret));
            caret++;
            return true;
        }
        return super.charTyped(ev);
    }

    /** APLICAR: manda o estado pro servidor (que revalida travas/alcance). */
    private void aplicar() {
        ClientPlayNetworking.send(new CentralComandoNetworking.AplicarCentralPayload(
                pos, new ArrayList<>(linhas), cor, brilho, modo));
        aplicou = true;
    }

    @Override
    public void onClose() {
        this.minecraft.player.closeContainer();
        super.onClose();
    }

    // ==================================================== DESENHO

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
        // painel + moldura dupla
        g.fill(x0 + 3, y0 + 3, x0 + LARGURA + 3, y0 + ALTURA + 3, 0x66000000);
        g.fill(x0, y0, x0 + LARGURA, y0 + ALTURA, FUNDO);
        g.outline(x0, y0, LARGURA, ALTURA, MOLDURA);
        g.outline(x0 + 1, y0 + 1, LARGURA - 2, ALTURA - 2, FUNDO_CLARO);

        // cabeçalho
        g.text(this.font, Component.translatable("gui.intoxicantes.central.titulo"),
                x0 + 8, y0 + 8, ACENTO);
        String sub = letreiro
                ? Component.translatable("gui.intoxicantes.central.subtitulo.letreiro").getString()
                : Component.translatable("gui.intoxicantes.central.subtitulo.painel").getString();
        g.text(this.font, sub, x0 + 8, y0 + 18, TEXTO_FRACO);
        if (trancado) {
            g.text(this.font, Component.translatable("gui.intoxicantes.central.trancado"),
                    x0 + LARGURA - 8 - this.font.width("\uD83D\uDD12 trancado"), y0 + 8, PERIGO);
        }

        // ---- PREVIEW com a FONTE DE LED REAL (5×7 ampliada ×2)
        desenharPreview(g);

        // ---- campos de texto
        for (int i = 0; i < 2; i++) {
            int[] c = campoLinha(i);
            boolean ativo = linhaAtiva == i;
            g.fill(c[0] - 1, c[1] - 1, c[0] + c[2] + 1, c[1] + c[3] + 1,
                    ativo ? CAMPO_BORDA : CAMPO_BORDA_FRACA);
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], CAMPO);
            String rotulo = Component.translatable("gui.intoxicantes.central.linha",
                    i + 1).getString();
            g.text(this.font, rotulo, x0 + 8, c[1] + 4, ativo ? TEXTO : TEXTO_FRACO);
            String texto = linhas.get(i);
            String visivel = this.font.plainSubstrByWidth(
                    this.font.plainSubstrByWidth(texto, c[2] - 8) == texto ? texto
                            : this.font.plainSubstrByWidth(texto, c[2] - 8), c[2] - 8);
            g.text(this.font, visivel.isEmpty() && !texto.isEmpty()
                    ? "" : visivel, c[0] + 4, c[1] + 4, TEXTO);
            // caret piscando na linha ativa
            if (ativo && (Util.getMillis() / 500L) % 2L == 0L) {
                int cx = c[0] + 4 + Math.min(this.font.width(texto.substring(0, caret)),
                        c[2] - 8);
                g.fill(cx, c[1] + 3, cx + 1, c[1] + c[3] - 3, ACENTO);
            }
            // contador de caracteres da linha ativa
            if (ativo) {
                String cont = texto.length() + "/" + maxCaracteres;
                g.text(this.font, cont, x0 + LARGURA - 8 - this.font.width(cont),
                        c[1] + 18, TEXTO_FRACO);
            }
        }

        // ---- paleta de cores
        g.text(this.font, Component.translatable("gui.intoxicantes.central.cor"),
                x0 + 8, y0 + 112, TEXTO);
        for (int i = 0; i < CORES.length; i++) {
            int[] b = paletaBox(i);
            g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1,
                    cor == CORES[i] ? 0xFFFFFFFF : MOLDURA);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xFF000000 | CORES[i]);
        }

        // ---- brilho + modo (só painel craftável)
        if (!letreiro) {
            g.text(this.font, Component.translatable("gui.intoxicantes.central.brilho"),
                    x0 + 8, y0 + 150, TEXTO);
            int[] bb = barraBrilho();
            g.fill(bb[0] - 1, bb[1] - 1, bb[0] + bb[2] + 1, bb[1] + bb[3] + 1, MOLDURA);
            g.fill(bb[0], bb[1], bb[0] + bb[2], bb[1] + bb[3], CAMPO);
            g.fill(bb[0], bb[1], bb[0] + (int) (bb[2] * brilho / 15.0), bb[1] + bb[3],
                    corBrilho());
            // marcas de passo
            for (int s = 1; s < 15; s++) {
                int px = bb[0] + bb[2] * s / 15;
                g.fill(px, bb[1] + 2, px + 1, bb[1] + bb[3] - 2, 0x66000000);
            }

            desenharAba(g, abaModo(false), "gui.intoxicantes.central.fixo",
                    modo == PainelLedBlockEntity.MODO_FIXO, mx, my);
            desenharAba(g, abaModo(true), "gui.intoxicantes.central.scroll",
                    modo == PainelLedBlockEntity.MODO_SCROLL, mx, my);
        }

        // ---- rodapé: APLICAR / FECHAR
        int[] f = botaoFechar();
        boolean hF = dentro(mx, my, f);
        g.fill(f[0], f[1], f[0] + f[2], f[1] + f[3], hF ? FUNDO_CLARO : FUNDO);
        g.outline(f[0], f[1], f[2], f[3], MOLDURA);
        g.centeredText(this.font, Component.translatable("gui.intoxicantes.central.fechar"),
                f[0] + f[2] / 2, f[1] + 5, TEXTO);

        int[] a = botaoAplicar();
        boolean hA = dentro(mx, my, a);
        g.fill(a[0], a[1], a[0] + a[2], a[1] + a[3], hA ? ACENTO : 0xFF14522A);
        g.outline(a[0], a[1], a[2], a[3], 0xFF0C3A1E);
        g.centeredText(this.font, Component.translatable("gui.intoxicantes.central.aplicar"),
                a[0] + a[2] / 2, a[1] + 5, hA ? 0xFF062513 : ACENTO);
    }

    /** Cor da barra de brilho: do apagado ao aceso na COR escolhida. */
    private int corBrilho() {
        int r = (cor >> 16) & 0xFF;
        int gg = (cor >> 8) & 0xFF;
        int b = cor & 0xFF;
        float fator = 0.25F + 0.75F * brilho / 15.0F;
        return 0xFF000000 | ((int) (r * fator) << 16) | ((int) (gg * fator) << 8) | (int) (b * fator);
    }

    private void desenharAba(GuiGraphicsExtractor g, int[] r, String chave, boolean ativa,
            double mx, double my) {
        boolean hover = dentro(mx, my, r);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                ativa ? 0xFF14522A : hover ? FUNDO_CLARO : FUNDO);
        g.outline(r[0], r[1], r[2], r[3], ativa ? ACENTO : CAMPO_BORDA_FRACA);
        g.centeredText(this.font, Component.translatable(chave),
                r[0] + r[2] / 2, r[1] + 4, ativa ? ACENTO : TEXTO_FRACO);
    }

    /**
     * O PREVIEW: uma tela preta com o texto desenhado na FONTE DE LED 5×7
     * REAL (ampliada ×2, pixel a pixel) na COR escolhida — exatamente o que
     * o painel mostra (o modo scroll aparece andando).
     */
    private void desenharPreview(GuiGraphicsExtractor g) {
        int pw = LARGURA - 16;
        int ph = 38;
        int px = x0 + 8;
        int py = y0 + 28;
        g.fill(px, py, px + pw, py + ph, TELA_DESLIGADA);
        g.outline(px, py, pw, ph, MOLDURA);
        // a faixa "apagando de dia" (brilho 0 = apagado)
        if (letreiro ? false : brilho == 0) {
            return;
        }
        float alpha = letreiro ? 1.0F : 0.3F + 0.7F * brilho / 15.0F;
        int corLed = 0xFF000000 | cor;
        int escala = 2;

        // o texto (1..2 linhas), com offset de scroll quando MODO_SCROLL
        List<String> conteudo = new ArrayList<>();
        for (String l : linhas) {
            if (!l.isBlank()) conteudo.add(l);
        }
        if (conteudo.isEmpty()) return;
        int larguraMax = 0;
        for (String l : conteudo) larguraMax = Math.max(larguraMax, LedFont.largura(l));
        int larguraTela = pw - 8;
        int deslocamento = 0;
        if (modo == PainelLedBlockEntity.MODO_SCROLL && larguraMax * escala > larguraTela) {
            int periodo = larguraMax * escala + larguraTela;
            deslocamento = (int) ((Util.getMillis() / 40L) % periodo);
        }
        g.enableScissor(px + 2, py + 2, px + pw - 2, py + ph - 2);
        int nLin = Math.min(conteudo.size(), 2);
        int alturaBloco = nLin * LedFont.ALTURA_GLYPH * escala + (nLin - 1) * 4;
        int yTexto = py + (ph - alturaBloco) / 2;
        for (int i = 0; i < nLin; i++) {
            String linha = conteudo.get(i);
            int larguraLinha = LedFont.largura(linha) * escala;
            int xTexto;
            if (deslocamento > 0) {
                xTexto = px + pw - deslocamento;
            } else {
                xTexto = px + (pw - larguraLinha) / 2;
            }
            for (int ci = 0; ci < linha.length(); ci++) {
                byte[] glifo = LedFont.glifo(linha.charAt(ci));
                for (int row = 0; row < LedFont.ALTURA_GLYPH; row++) {
                    int bits = glifo[row];
                    if (bits == 0) continue;
                    for (int col = 0; col < LedFont.LARGURA_GLYPH; col++) {
                        if ((bits & (1 << (LedFont.LARGURA_GLYPH - 1 - col))) == 0) continue;
                        int gx = xTexto + (ci * LedFont.GLIFO_ESPACO + col) * escala;
                        int gy = yTexto + i * (LedFont.ALTURA_GLYPH * escala + 4) + row * escala;
                        g.fill(gx, gy, gx + escala, gy + escala,
                                (alphaToInt(alpha) << 24) | (corLed & 0xFFFFFF));
                    }
                }
            }
        }
        g.disableScissor();
    }

    private int alphaToInt(float alpha) {
        return (int) (Mth.clamp(alpha, 0F, 1F) * 255F);
    }
}
