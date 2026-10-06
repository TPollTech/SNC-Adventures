package com.intoxicantes;

import java.util.List;

import com.intoxicantes.QuadroEletricoNetworking.AbrirQuadroPayload;
import com.intoxicantes.QuadroEletricoNetworking.AbrirQuadroPayload.CircuitoSnapshot;
import com.intoxicantes.energia.CircuitoEletrico;
import com.intoxicantes.energia.Grandezas;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Quadro de distribuição: chapa, barramento de cobre e quatro módulos DIN. */
public class QuadroEletricoScreen extends Screen {
    private static final int CHAPA = 0xFF3E4247;
    private static final int CHAPA_CLARA = 0xFF565B61;
    private static final int MOLDURA = 0xFF23262A;
    private static final int PERIGO = 0xFFE8C319;
    private static final int COBRE = 0xFFB87333;
    private static final int ETIQUETA = 0xFFE9E2CE;
    private static final int TINTA = 0xFF2B2620;
    private static final int TEXTO = 0xFFF1EFE8;
    private static final int SECUNDARIO = 0xFFC1C5CA;
    private static final int VERDE = 0xFF83D697;
    private static final int VERMELHO = 0xFFFF9990;
    private static final int LARANJA = 0xFFFFC078;

    private static final int LARGURA = 640;
    private static final int ALTURA = 388;
    private static final int MODULO_X = 12;
    private static final int MODULO_Y = 122;
    private static final int MODULO_LARGURA = 150;
    private static final int MODULO_PASSO = 154;

    private AbrirQuadroPayload estado;
    private final BlockPos pos;
    private float escala = 1F;
    private int x0;
    private int y0;
    private int editandoNome = -1;
    private String nomeRascunho = "";
    private boolean fechamentoEnviado;

    public QuadroEletricoScreen(AbrirQuadroPayload payload) {
        super(Component.translatable("gui.intoxicantes.quadro.titulo"));
        estado = payload;
        pos = payload.pos();
    }

    public void atualizar(AbrirQuadroPayload payload) {
        if (pos.equals(payload.pos())) estado = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        escala = Math.min(1F, Math.min(Math.max(1, width - 12) / (float) LARGURA,
                Math.max(1, height - 12) / (float) ALTURA));
        x0 = Math.round((width - LARGURA * escala) / 2F);
        y0 = Math.round((height - ALTURA * escala) / 2F);
    }

    private int moduloX(int i) { return MODULO_X + i * MODULO_PASSO; }
    private int[] caixaNome(int i) { return new int[]{moduloX(i) + 6, 136, 138, 22}; }
    private int[] caixaAlavanca(int i) { return new int[]{moduloX(i) + 16, 177, 18, 30}; }
    private int[] caixaAmperagem(int i) { return new int[]{moduloX(i) + 6, 229, 65, 22}; }
    private int[] caixaTensao(int i) { return new int[]{moduloX(i) + 79, 229, 65, 22}; }
    private int[] caixaFechar() { return new int[]{540, 352, 88, 23}; }

    /** Coordenadas da mesma caixa usada no desenho/clique, para regressão no cliente. */
    double[] centroAlavanca(int i) {
        int[] r = caixaAlavanca(i);
        return new double[]{x0 + (r[0] + r[2] / 2.0) * escala,
                y0 + (r[1] + r[3] / 2.0) * escala};
    }

    boolean layoutCabeNaTela() {
        return x0 >= 0 && y0 >= 0
                && x0 + LARGURA * escala <= width
                && y0 + ALTURA * escala <= height;
    }

    private boolean dentro(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private String traducao(String chave, Object... args) {
        return Component.translatable(chave, args).getString();
    }

    /** Remover códigos de estilo também dos nomes editáveis; nunca desenhar sombra. */
    private String textoSimples(String texto) {
        String limpo = ChatFormatting.stripFormatting(texto);
        return limpo == null ? "" : limpo;
    }

    private String ajustarTexto(String texto, int largura) {
        String limpo = textoSimples(texto);
        if (font.width(limpo) <= largura) return limpo;
        return font.plainSubstrByWidth(limpo, Math.max(0, largura - font.width("..."))) + "...";
    }

    private void texto(GuiGraphicsExtractor g, String valor, int x, int y, int largura, int cor) {
        g.text(font, ajustarTexto(valor, largura), x, y, cor, false);
    }

    private void textoCentral(GuiGraphicsExtractor g, String valor, int[] r, int cor) {
        String visivel = ajustarTexto(valor, r[2] - 6);
        g.text(font, visivel, r[0] + (r[2] - font.width(visivel)) / 2,
                r[1] + (r[3] - font.lineHeight) / 2, cor, false);
    }

    private void caixa(GuiGraphicsExtractor g, int[] r, int cor, int borda) {
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], cor);
        g.outline(r[0], r[1], r[2], r[3], borda);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        double localX = (mx - x0) / escala;
        double localY = (my - y0) / escala;
        g.fill(0, 0, width, height, 0xA0000000);
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale(escala, escala);
        caixa(g, new int[]{0, 0, LARGURA, ALTURA}, CHAPA, MOLDURA);
        g.outline(1, 1, LARGURA - 2, ALTURA - 2, CHAPA_CLARA);
        for (int x = 6; x < LARGURA - 6; x += 16) {
            g.fill(x, 5, Math.min(x + 8, LARGURA - 6), 10, PERIGO);
            g.fill(x + 8, 5, Math.min(x + 16, LARGURA - 6), 10, MOLDURA);
        }
        texto(g, traducao("gui.intoxicantes.quadro.titulo"), 12, 18, 610, TEXTO);
        texto(g, estado.fontes() > 0
                ? traducao("gui.intoxicantes.quadro.entrada.ok", estado.fontes())
                : traducao("gui.intoxicantes.quadro.entrada.nada"),
                12, 34, 610, estado.fontes() > 0 ? VERDE : VERMELHO);

        medidor(g, 12, "disponivel", Grandezas.formatarE(estado.disponivelE()));
        medidor(g, 220, "consumo", Grandezas.formatarW(estado.consumoTotalW()));
        medidor(g, 428, "buffer", estado.bufferE() * 100L / Math.max(1, estado.capacidadeBufferE()) + "%");
        g.fill(12, 109, 628, 113, COBRE);
        g.fill(12, 109, 628, 110, 0xFFE39A5B);
        for (int i = 0; i < estado.circuitos().size(); i++)
            desenharDisjuntor(g, i, estado.circuitos().get(i), localX, localY);

        texto(g, traducao(editandoNome >= 0 ? "gui.intoxicantes.quadro.edicao"
                : "gui.intoxicantes.quadro.dica"), 12, 354, 510, SECUNDARIO);
        if (editandoNome < 0)
            texto(g, traducao("gui.intoxicantes.quadro.dica_ajustes"), 12, 370, 510, SECUNDARIO);
        int[] fechar = caixaFechar();
        caixa(g, fechar, dentro(localX, localY, fechar) ? CHAPA_CLARA : MOLDURA, CHAPA_CLARA);
        textoCentral(g, traducao("gui.intoxicantes.quadro.fechar"), fechar, TEXTO);
        g.pose().popMatrix();

        // Tooltips usam coordenadas da tela, não as coordenadas escaladas do quadro.
        for (int i = 0; i < estado.circuitos().size(); i++) {
            var c = estado.circuitos().get(i);
            if (dentro(localX, localY, caixaNome(i))
                    || dentro(localX, localY, new int[]{moduloX(i) + 6, 277, 138, 14})) {
                g.setTooltipForNextFrame(font, List.of(
                        Component.literal(textoSimples(c.nome())),
                        Component.literal(traducao("gui.intoxicantes.quadro.total", Grandezas.formatarE(c.totalE())))),
                        java.util.Optional.empty(), mx, my);
                break;
            }
        }
    }

    private void medidor(GuiGraphicsExtractor g, int x, String chave, String valor) {
        caixa(g, new int[]{x, 57, 200, 43}, MOLDURA, CHAPA_CLARA);
        texto(g, traducao("gui.intoxicantes.quadro." + chave), x + 8, 63, 184, SECUNDARIO);
        texto(g, valor, x + 8, 79, 184, TEXTO);
    }

    private void desenharDisjuntor(GuiGraphicsExtractor g, int i, CircuitoSnapshot c,
            double mx, double my) {
        int x = moduloX(i);
        caixa(g, new int[]{x, MODULO_Y, MODULO_LARGURA, 221}, 0xFF30343A, CHAPA_CLARA);
        texto(g, "C" + (i + 1), x + 6, 127, 138, SECUNDARIO);
        int[] nome = caixaNome(i);
        caixa(g, nome, ETIQUETA, editandoNome == i ? PERIGO : 0xFF8F887A);
        if (editandoNome == i) {
            // Mostrar a ponta do rascunho, incluindo o caractere que está sendo digitado.
            String visivel = textoSimples(nomeRascunho);
            while (!visivel.isEmpty() && font.width(visivel) > nome[2] - 12)
                visivel = visivel.substring(visivel.offsetByCodePoints(0, 1));
            texto(g, visivel, nome[0] + 4, nome[1] + 5, nome[2] - 12, TINTA);
            if ((Util.getMillis() / 400L) % 2L == 0L) {
                int caretX = nome[0] + 4 + font.width(visivel);
                g.fill(caretX, nome[1] + 4, caretX + 1, nome[1] + 13, TINTA);
            }
        } else texto(g, c.nome(), nome[0] + 4, nome[1] + 5, nome[2] - 8, TINTA);

        int cor = c.estado() == CircuitoEletrico.LIGADO ? VERDE
                : c.estado() == CircuitoEletrico.DESARMADO ? LARANJA : SECUNDARIO;
        caixa(g, new int[]{x + 6, 168, 38, 48}, ETIQUETA, 0xFF8F887A);
        int[] alavanca = caixaAlavanca(i);
        caixa(g, alavanca, MOLDURA, 0xFF111111);
        int ay = c.estado() == CircuitoEletrico.LIGADO ? alavanca[1] + 2 : alavanca[1] + 18;
        g.fill(alavanca[0] + 2, ay, alavanca[0] + alavanca[2] - 2, ay + 9,
                dentro(mx, my, alavanca) ? TEXTO : cor);
        texto(g, traducao(c.estado() == CircuitoEletrico.DESARMADO ? "gui.intoxicantes.quadro.rearme"
                : c.estado() == CircuitoEletrico.LIGADO ? "gui.intoxicantes.quadro.on"
                : "gui.intoxicantes.quadro.off"), x + 52, 173, 90, cor);
        texto(g, Grandezas.formatarW(c.consumoW()), x + 52, 194, 90,
                c.consumoW() > Grandezas.wattsDoDisjuntor(c.disjuntorA(), c.tensao()) ? VERMELHO : TEXTO);
        int[] a = caixaAmperagem(i);
        int[] v = caixaTensao(i);
        caixa(g, a, dentro(mx, my, a) ? 0xFFFFF7DF : ETIQUETA, 0xFF8F887A);
        caixa(g, v, dentro(mx, my, v) ? 0xFFFFF7DF : ETIQUETA, 0xFF8F887A);
        textoCentral(g, c.disjuntorA() + " A", a, TINTA);
        textoCentral(g, c.tensao() + " V", v, TINTA);
        texto(g, traducao("gui.intoxicantes.quadro.limite_fio",
                c.limiteBitolaA() > 0 ? c.limiteBitolaA() + " A" : "—"), x + 6, 261, 138, SECUNDARIO);
        texto(g, traducao("gui.intoxicantes.quadro.total", Grandezas.formatarE(c.totalE())),
                x + 6, 278, 138, SECUNDARIO);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean duplo) {
        if (ev.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(ev, duplo);
        double mx = (ev.x() - x0) / escala;
        double my = (ev.y() - y0) / escala;
        if (dentro(mx, my, caixaFechar())) {
            onClose();
            return true;
        }
        if (editandoNome >= 0 && !dentro(mx, my, caixaNome(editandoNome))) confirmarNome();
        for (int i = 0; i < estado.circuitos().size(); i++) {
            if (dentro(mx, my, caixaAlavanca(i))) {
                enviar(estado.circuitos().get(i).estado() == CircuitoEletrico.DESARMADO ? 1 : 0, i, "");
                return true;
            }
            if (dentro(mx, my, caixaAmperagem(i))) { enviar(2, i, ""); return true; }
            if (dentro(mx, my, caixaTensao(i))) { enviar(3, i, ""); return true; }
            if (dentro(mx, my, caixaNome(i))) {
                editandoNome = i;
                nomeRascunho = textoSimples(estado.circuitos().get(i).nome());
                return true;
            }
        }
        return super.mouseClicked(ev, duplo);
    }

    private void enviar(int acao, int indice, String texto) {
        ClientPlayNetworking.send(new QuadroEletricoNetworking.AcaoQuadroPayload(pos, acao, indice, texto));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ev) {
        int tecla = ev.key();
        if (editandoNome < 0) {
            if (tecla == InputConstants.KEY_ESCAPE) { onClose(); return true; }
            return super.keyPressed(ev);
        }
        if (tecla == InputConstants.KEY_ESCAPE) { editandoNome = -1; return true; }
        if (tecla == InputConstants.KEY_RETURN || tecla == InputConstants.KEY_NUMPADENTER) {
            confirmarNome();
            return true;
        }
        if (tecla == InputConstants.KEY_BACKSPACE && !nomeRascunho.isEmpty())
            nomeRascunho = nomeRascunho.substring(0, nomeRascunho.offsetByCodePoints(nomeRascunho.length(), -1));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent ev) {
        if (editandoNome < 0) return super.charTyped(ev);
        String caractere = ev.codepointAsString();
        if (ev.isAllowedChatCharacter()
                && nomeRascunho.length() + caractere.length() <= QuadroEletricoNetworking.MAX_NOME)
            nomeRascunho += caractere;
        return true;
    }

    private void confirmarNome() {
        if (editandoNome >= 0) enviar(4, editandoNome, nomeRascunho);
        editandoNome = -1;
    }

    @Override
    public void onClose() {
        if (editandoNome >= 0) confirmarNome();
        if (!fechamentoEnviado) {
            enviar(5, 0, "");
            fechamentoEnviado = true;
        }
        super.onClose();
    }
}
