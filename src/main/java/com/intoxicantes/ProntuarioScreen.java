package com.intoxicantes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * v1.2.57 — "O QUÃO FUDIDO TU ESTÁ": a ficha médica da esquina (tecla H),
 * redesenhada pra ficar FIEL ao preview aprovado (previa-saude-completa.html):
 * painel CINZA estilo GUI vanilla com bevel (branco em cima/esquerda, cinza
 * embaixo/direita — o clássico), um bloco por órgão (nome + estágio na linha,
 * BARRA SUNKEN destrutível embaixo e a consequência de gameplay na terceira
 * linha), histórico em GRADE de 2 colunas, dias limpos, caixa de vício com
 * PIPS (|||||·····) e o veredito do Dr. Gago numa caixa sunken no rodapé.
 *
 * A consequência exibida é VERDADE: fígado ruim multiplica a ressaca no
 * Embriaguez, pulmão ruim tosse pós-baseado e estômago ruim alonga a náusea
 * da queda (os 3 ganchos vivem no SaudeSystem/Embriaguez).
 *
 * Zero emoji (a fonte vanilla não tem) e zero textura: GUI desenhada vetorial
 * no GuiGraphicsExtractor, no padrão da casa.
 */
public class ProntuarioScreen extends Screen {
    // ==================================================== PALETA (GUI vanilla)
    private static final int FUNDO = 0xFFC6C6C6;
    private static final int BEVEL_CLARO = 0xFFFFFFFF;
    private static final int BEVEL_ESCURO = 0xFF555555;
    private static final int BEVEL_FUNDO = 0xFF373737;
    private static final int TINTA = 0xFF3F3F3F;
    private static final int TINTA_FORTE = 0xFF2B2B2B;
    private static final int TINTA_FRACA = 0xFF5C5C5C;
    private static final int BARRA_FUNDO = 0xFF565656;
    private static final int VERDE = 0xFF5FD35F;
    private static final int AMARELO = 0xFFE0C040;
    private static final int VERMELHO = 0xFFE05548;
    private static final int VICIO_FUNDO = 0xFFD8C8C8;
    private static final int VICIO_BEVEL = 0xFFAA8888;
    private static final int VEREDITO_FUNDO = 0xFF8B8B8B;
    private static final int LINHA_SEP = 0xFFA5A5A5;

    /** Dados que chegam do SaudeSyncPayload (server-authoritative). */
    public record Dados(int hidratacao, int figado, int pulmao, int estomago,
                        int vicio, String drogaVicio, int alcool, int erva,
                        int po, int pilula, int diasLimpos, int abstinencia) {}

    private final Dados d;
    private int x0;
    private int y0;
    private static final int LARGURA = 240;
    private int altura;

    public ProntuarioScreen(Dados dados) {
        super(Component.translatable("gui.intoxicantes.prontuario.titulo"));
        this.d = dados;
    }

    // ==================================================== GEOMETRIA

    @Override
    protected void init() {
        // cabeçalho 24 + 3 órgãos (27 cada) + histórico 44 + vício 24 (cond.)
        // + abstinência 12 (cond.) + veredito 30 + respiros
        this.altura = 24 + 3 * 27 + 44 + (d.vicio() > 0 ? 24 : 0)
                + (d.abstinencia() > 0 ? 12 : 0) + 34;
        this.altura = Math.min(this.altura + 10, this.height - 16);
        this.x0 = (this.width - LARGURA) / 2;
        this.y0 = (this.height - altura) / 2;
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        if (this.minecraft.options.keyInventory.matches(ev)
                || SaudeClient.teclaProntuarioMatches(ev)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Bevel RAISED estilo vanilla: topo/esquerda claros, baixo/direita escuros. */
    private static void painel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fundo) {
        g.fill(x, y, x + w, y + h, fundo);
        g.fill(x, y, x + w, y + 1, BEVEL_CLARO);
        g.fill(x, y, x + 1, y + h, BEVEL_CLARO);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_ESCURO);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_ESCURO);
    }

    /** Bevel SUNKEN (a barra e o veredito afundam no painel). */
    private static void recesso(GuiGraphicsExtractor g, int x, int y, int w, int h, int fundo) {
        g.fill(x, y, x + w, y + h, fundo);
        g.fill(x, y, x + w, y + 1, BEVEL_FUNDO);
        g.fill(x, y, x + 1, y + h, BEVEL_FUNDO);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_CLARO);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_CLARO);
    }

    // ==================================================== DESENHO

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        g.fill(0, 0, this.width, this.height, 0x88000000);
        painel(g, x0, y0, LARGURA, altura, FUNDO);

        desenharCabecalho(g);
        int y = desenharOrgaos(g);
        y = desenharHistorico(g, y);
        y = desenharVicio(g, y);
        desenharVeredito(g, y);
    }

    private void desenharCabecalho(GuiGraphicsExtractor g) {
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.prontuario.titulo"),
                x0 + LARGURA / 2, y0 + 7, TINTA_FORTE);
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.prontuario.subtitulo"),
                x0 + LARGURA / 2, y0 + 16, TINTA_FRACA);
    }

    /** Os 3 órgãos: nome+estágio na linha, barra sunken, consequência real. */
    private int desenharOrgaos(GuiGraphicsExtractor g) {
        int y = y0 + 27;
        SaudeData.Orgao[] orgaos = {SaudeData.Orgao.FIGADO, SaudeData.Orgao.PULMAO, SaudeData.Orgao.ESTOMAGO};
        int[] danos = {d.figado(), d.pulmao(), d.estomago()};
        int barraX = x0 + 10;
        int barraW = LARGURA - 20;
        for (int i = 0; i < orgaos.length; i++) {
            int dano = danos[i];
            int saudePct = Math.max(0, 100 - dano * 100 / SaudeData.ORGAO_MAX);
            int cor = corDe(saudePct);

            // linha 1: nome (esquerda, forte) + estágio (direita, cor do estágio)
            g.text(this.font, Component.translatable("gui.intoxicantes.prontuario.orgao." + orgaos[i].name()),
                    x0 + 10, y, TINTA_FORTE);
            Component estagio = Component.translatable(estagioDe(orgaos[i], saudePct));
            g.text(this.font, estagio,
                    x0 + LARGURA - 10 - this.font.width(estagio), y, cor);

            // linha 2: barra SUNKEN destrutível (verde → amarelo → vermelho)
            recesso(g, barraX, y + 10, barraW, 6, BARRA_FUNDO);
            int cheio = Math.max(1, (int) (barraW * saudePct / 100F)) - 2;
            g.fill(barraX + 1, y + 11, barraX + 1 + cheio, y + 15, cor);

            // linha 3: a consequência de gameplay VERDADEIRA (ganchos reais)
            g.text(this.font, Component.translatable(efeitoDe(orgaos[i], dano)),
                    x0 + 10, y + 19, dano >= 200 ? cor : TINTA_FRACA);

            y += 27;
        }
        // separador antes do histórico (o .hist do preview)
        g.fill(x0 + 8, y - 3, x0 + LARGURA - 8, y - 2, LINHA_SEP);
        return y + 3;
    }

    private static int corDe(int saudePct) {
        return saudePct > 60 ? VERDE : (saudePct > 25 ? AMARELO : VERMELHO);
    }

    /** A chave de lang do estágio (server/client compartilham o critério). */
    static String estagioDe(SaudeData.Orgao orgao, int saudePct) {
        String orgaoId = orgao.name().toLowerCase();
        if (saudePct > 80) return "gui.intoxicantes.prontuario.estagio.0." + orgaoId;
        if (saudePct > 60) return "gui.intoxicantes.prontuario.estagio.1." + orgaoId;
        if (saudePct > 40) return "gui.intoxicantes.prontuario.estagio.2." + orgaoId;
        if (saudePct > 20) return "gui.intoxicantes.prontuario.estagio.3." + orgaoId;
        return "gui.intoxicantes.prontuario.estagio.4." + orgaoId;
    }

    /**
     * v1.2.57: a consequência de gameplay de cada órgão — o que a tela promete
     * acontece de verdade (ressaca multiplicada, tosse, náusea alongada).
     */
    static String efeitoDe(SaudeData.Orgao orgao, int dano) {
        String orgaoId = orgao.name().toLowerCase();
        return "gui.intoxicantes.prontuario.efeito." + orgaoId + (dano >= 200 ? ".ruim" : ".limpo");
    }

    /** Histórico em GRADE 2×2 (o .itens do preview) + dias limpos. */
    private int desenharHistorico(GuiGraphicsExtractor g, int y) {
        g.text(this.font, Component.translatable("gui.intoxicantes.prontuario.historico"),
                x0 + 10, y, TINTA);
        y += 11;
        String[][] celulas = {
                {"gui.intoxicantes.prontuario.h.alcool", String.valueOf(d.alcool())},
                {"gui.intoxicantes.prontuario.h.erva", String.valueOf(d.erva())},
                {"gui.intoxicantes.prontuario.h.po", String.valueOf(d.po())},
                {"gui.intoxicantes.prontuario.h.pilula", String.valueOf(d.pilula())},
        };
        int meia = (LARGURA - 20) / 2;
        for (int i = 0; i < celulas.length; i++) {
            int cx = x0 + 10 + (i % 2) * meia;
            int cy = y + (i / 2) * 11;
            Component rotulo = Component.translatable(celulas[i][0]);
            g.text(this.font, rotulo, cx, cy, TINTA);
            String valor = celulas[i][1];
            g.text(this.font, Component.literal(valor), cx + meia - 8 - this.font.width(valor),
                    cy, TINTA_FRACA);
        }
        y += 23;
        Component limpos = Component.translatable("gui.intoxicantes.prontuario.diaslimpos",
                d.diasLimpos() > 0 ? String.valueOf(d.diasLimpos()) : "-");
        g.text(this.font, limpos, x0 + 10, y, TINTA_FRACA);
        return y + 13;
    }

    /** A caixa de vício (rosada, com pips |||||·····) e o aviso de abstinência. */
    private int desenharVicio(GuiGraphicsExtractor g, int y) {
        if (d.vicio() <= 0) {
            return y;
        }
        int caixaH = d.abstinencia() > 0 ? 22 : 12;
        g.fill(x0 + 8, y, x0 + LARGURA - 8, y + caixaH, VICIO_FUNDO);
        g.fill(x0 + 8, y, x0 + LARGURA - 8, y + 1, VICIO_BEVEL);
        g.fill(x0 + 8, y + caixaH - 1, x0 + LARGURA - 8, y + caixaH, BEVEL_CLARO);
        g.fill(x0 + 8, y, x0 + 9, y + caixaH, VICIO_BEVEL);
        g.fill(x0 + LARGURA - 9, y, x0 + LARGURA - 8, y + caixaH, BEVEL_CLARO);

        Component rotulo = Component.translatable("gui.intoxicantes.prontuario.vicio.label",
                Component.translatable(drogaLang(d.drogaVicio())));
        g.text(this.font, rotulo, x0 + 12, y + 2, 0xFF5A2020);
        Component nivel = Component.translatable("gui.intoxicantes.prontuario.vicio.nivel", d.vicio());
        boolean dependente = d.vicio() >= 30; // espelha o saudeVicioLimiar padrão
        g.text(this.font, nivel,
                x0 + LARGURA - 12 - this.font.width(nivel), y + 2,
                dependente ? VERMELHO : VERDE);
        // os pips do preview: cheio = |, vazio = ·
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            pips.append((i + 1) * 20 <= d.vicio() ? "|" : "·");
        }
        g.text(this.font, Component.literal(pips.toString()), x0 + 12, y + 11, 0xFF5A2020);
        Component status = Component.translatable(dependente
                ? "gui.intoxicantes.prontuario.vicio.dependente"
                : "gui.intoxicantes.prontuario.vicio.controlado");
        g.text(this.font, status,
                x0 + LARGURA - 12 - this.font.width(status), y + 11,
                dependente ? VERMELHO : VERDE);

        if (d.abstinencia() > 0) {
            Component sindrome = Component.translatable(
                    "gui.intoxicantes.prontuario.abstinencia", d.abstinencia());
            g.text(this.font, sindrome,
                    x0 + LARGURA / 2 - this.font.width(sindrome) / 2, y + caixaH + 1, VERMELHO);
        }
        return y + caixaH + (d.abstinencia() > 0 ? 13 : 2);
    }

    /** Nome de lang da droga do vício (mesmo critério do catálogo). */
    private static String drogaLang(String droga) {
        return droga == null || droga.isEmpty()
                ? "gui.intoxicantes.prontuario.vicio.desconhecida"
                : "item.intoxicantes." + droga;
    }

    /** O veredito do Dr. Gago na caixa sunken do rodapé. */
    private void desenharVeredito(GuiGraphicsExtractor g, int y) {
        int caixaH = 24;
        recesso(g, x0 + 8, y, LARGURA - 16, caixaH, VEREDITO_FUNDO);
        SaudeData.Orgao pior = piorOrgao();
        int danoPior = saudeDoPior(pior);
        int saudePct = Math.max(0, 100 - danoPior * 100 / SaudeData.ORGAO_MAX);
        g.centeredText(this.font, Component.translatable(vereditoDe(saudePct)),
                x0 + LARGURA / 2, y + 3, 0xFF1E1E1E);
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.prontuario.medico"),
                x0 + LARGURA / 2, y + 13, 0xFF3A3A3A);
    }

    private SaudeData.Orgao piorOrgao() {
        if (d.figado() >= d.pulmao() && d.figado() >= d.estomago()) return SaudeData.Orgao.FIGADO;
        if (d.pulmao() >= d.estomago()) return SaudeData.Orgao.PULMAO;
        return SaudeData.Orgao.ESTOMAGO;
    }

    private int saudeDoPior(SaudeData.Orgao orgao) {
        return switch (orgao) {
            case FIGADO -> d.figado();
            case PULMAO -> d.pulmao();
            case ESTOMAGO -> d.estomago();
        };
    }

    static String vereditoDe(int saudePct) {
        if (saudePct > 80) return "gui.intoxicantes.prontuario.veredito.0";
        if (saudePct > 60) return "gui.intoxicantes.prontuario.veredito.1";
        if (saudePct > 40) return "gui.intoxicantes.prontuario.veredito.2";
        if (saudePct > 20) return "gui.intoxicantes.prontuario.veredito.3";
        return "gui.intoxicantes.prontuario.veredito.4";
    }
}
