package com.intoxicantes;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A ETIQUETA DO MERCADO (v1.2.75): o CARRINHO inteiro numa tela — cada linha
 * aceita +/- de dose e remoção, o total e o saldo acompanham, e o pagamento
 * só sai no confirmar. Toda regra é do servidor; aqui é pintura e ação.
 */
public final class PrateleiraCompraScreen extends Screen {
    private static final int LARGURA = 280;
    private static final int ALTURA = 228;
    private static final int LINHA_ALT = 22;
    private static final int LINHAS_VISIVEIS = 6;

    private PrateleiraNetworking.AbrirCompraPayload proposta;
    /** Confirmou/cancelou: a tela não envia nada de novo. */
    private boolean enviada;
    /** Esperando a recotação do servidor: os controles ficam travados. */
    private boolean aguardando;
    private int scrollLinha;
    private int x0, y0, listaY;

    public PrateleiraCompraScreen(PrateleiraNetworking.AbrirCompraPayload proposta) {
        super(Component.translatable("prateleira.intoxicantes.confirmacao"));
        this.proposta = proposta;
    }

    // Ganchos de gametest (package-private): o orçamento vivo da tela e a
    // origem do painel — o client test valida +/- e recotação de verdade.
    PrateleiraNetworking.AbrirCompraPayload proposta() { return proposta; }
    boolean aguardandoRecotacao() { return aguardando; }
    int[] botaoMaisParaTeste(int v) { return botaoMais(v); }
    int[] botaoMenosParaTeste(int v) { return botaoMenos(v); }
    /** O centro do botão CONFIRMAR (o caixa cobra). */
    int[] botaoConfirmarParaTeste() { return new int[] {width / 2 - 55, y0 + ALTURA - 16}; }
    /** O centro do botão CANCELAR (o freguês desiste). */
    int[] botaoCancelarParaTeste() { return new int[] {width / 2 + 55, y0 + ALTURA - 16}; }
    int x0() { return x0; }
    int y0() { return y0; }
    int listaY() { return listaY; }

    /** O servidor recotou o carrinho: a tela adota a versão nova. */
    public void atualizar(PrateleiraNetworking.AbrirCompraPayload payload) {
        this.proposta = payload;
        this.aguardando = false;
        this.scrollLinha = Mth.clamp(scrollLinha, 0, maxScroll());
    }

    // ==================================================== GEOMETRIA

    @Override
    protected void init() {
        this.x0 = (this.width - LARGURA) / 2;
        this.y0 = (this.height - ALTURA) / 2;
        this.listaY = this.y0 + 32;
        this.scrollLinha = Mth.clamp(this.scrollLinha, 0, maxScroll());
    }

    private int totalLinhas() {
        return proposta.produtos().size();
    }

    private int maxScroll() {
        return Math.max(0, totalLinhas() - LINHAS_VISIVEIS);
    }

    private int[] botaoMenos(int v) {
        return new int[] {x0 + LARGURA - 96, listaY + v * LINHA_ALT + 3, 14, 16};
    }

    private int[] botaoMais(int v) {
        return new int[] {x0 + LARGURA - 40, listaY + v * LINHA_ALT + 3, 14, 16};
    }

    private int[] botaoRemover(int v) {
        return new int[] {x0 + LARGURA - 22, listaY + v * LINHA_ALT + 3, 14, 16};
    }

    private boolean dentro(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private boolean dentroDaLinha(double mx, double my, int v) {
        return mx >= x0 + 6 && mx < x0 + LARGURA - 6
                && my >= listaY + v * LINHA_ALT && my < listaY + (v + 1) * LINHA_ALT;
    }

    /** Preço da linha inteira (a mesma fração arredondada do servidor). */
    private int subtotal(int linha) {
        int dose = Math.max(1, proposta.produtos().get(linha).getCount());
        long preco = (long) proposta.precos().get(linha) * proposta.quantidades().get(linha);
        return (int) ((preco + dose - 1) / dose);
    }

    private boolean podeMexer() {
        return !enviada && !aguardando;
    }

    // ==================================================== ENTRADA

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean duplo) {
        if (ev.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(ev, duplo);
        }
        double mx = ev.x(), my = ev.y();
        int cx = width / 2, botoesY = y0 + ALTURA - 26;
        if (dentro(mx, my, new int[] {cx - 105, botoesY, 100, 20})) {
            if (podeMexer() && !proposta.produtos().isEmpty()
                    && proposta.saldo() >= proposta.total()) enviar(true);
            return true;
        }
        if (dentro(mx, my, new int[] {cx + 5, botoesY, 100, 20})) {
            enviar(false);
            return true;
        }
        if (!podeMexer()) {
            return super.mouseClicked(ev, duplo);
        }
        for (int v = 0; v < LINHAS_VISIVEIS; v++) {
            int linha = scrollLinha + v;
            if (linha >= totalLinhas()) break;
            if (!dentroDaLinha(mx, my, v)) continue;
            int dose = Math.max(1, proposta.produtos().get(linha).getCount());
            int quantidade = proposta.quantidades().get(linha);
            int limite = proposta.limites().get(linha);
            if (dentro(mx, my, botaoMenos(v))) {
                enviarQuantidade(linha, Math.max(0, quantidade - dose));
                return true;
            }
            if (dentro(mx, my, botaoMais(v))) {
                enviarQuantidade(linha, Math.min(limite, quantidade + dose));
                return true;
            }
            if (dentro(mx, my, botaoRemover(v))) {
                enviarQuantidade(linha, 0);
                return true;
            }
            return true;
        }
        return super.mouseClicked(ev, duplo);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll() > 0 && mx >= x0 && mx < x0 + LARGURA
                && my >= listaY && my < listaY + LINHAS_VISIVEIS * LINHA_ALT && dy != 0) {
            scrollLinha = Mth.clamp(scrollLinha - (int) Math.signum(dy), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ev) {
        if (this.minecraft.options.keyInventory.matches(ev)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(ev);
    }

    // ==================================================== AÇÕES

    private void enviarQuantidade(int linha, int quantidade) {
        if (!podeMexer() || quantidade == proposta.quantidades().get(linha)) return;
        aguardando = true;
        ClientPlayNetworking.send(new PrateleiraNetworking.AtualizarCarrinhoPayload(
                proposta.token(), linha, quantidade));
    }

    private void enviar(boolean confirmar) {
        if (enviada) return;
        enviada = true;
        ClientPlayNetworking.send(new PrateleiraNetworking.ConfirmarCompraPayload(
                proposta.token(), confirmar));
        super.onClose();
    }

    @Override
    public void onClose() {
        enviar(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================================================== DESENHO

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        int cx = width / 2;
        g.fill(0, 0, width, height, 0xA0000000);
        g.fill(x0, y0, x0 + LARGURA, y0 + ALTURA, 0xFFE8DDB7);
        g.outline(x0, y0, LARGURA, ALTURA, 0xFF3C6846);
        g.fill(x0 + 1, y0 + 1, x0 + LARGURA - 1, y0 + 24, 0xFF315C3E);
        g.centeredText(font, title, cx, y0 + 8, 0xFFF6ECCF);

        if (totalLinhas() == 0) {
            g.centeredText(font, Component.translatable("prateleira.intoxicantes.vazia"),
                    cx, listaY + 46, 0xFF596447);
        }
        for (int v = 0; v < LINHAS_VISIVEIS; v++) {
            int linha = scrollLinha + v;
            if (linha >= totalLinhas()) break;
            desenharLinha(g, mx, my, v, linha);
        }
        if (maxScroll() > 0) desenharBarra(g);

        int rodape = listaY + LINHAS_VISIVEIS * LINHA_ALT + 8;
        g.centeredText(font, Component.translatable("prateleira.intoxicantes.total",
                proposta.total()), cx, rodape, 0xFF254C34);
        g.centeredText(font, Component.translatable("prateleira.intoxicantes.saldo",
                proposta.saldo()), cx, rodape + 12, 0xFF596447);
        botao(g, cx - 105, y0 + ALTURA - 26, "prateleira.intoxicantes.confirmar", mx, my,
                proposta.saldo() >= proposta.total() && !proposta.produtos().isEmpty()
                        ? 0xFF386B45 : 0xFF77766A);
        botao(g, cx + 5, y0 + ALTURA - 26, "prateleira.intoxicantes.cancelar", mx, my, 0xFF766B4D);
    }

    private void desenharLinha(GuiGraphicsExtractor g, int mx, int my, int v, int linha) {
        int y = listaY + v * LINHA_ALT;
        boolean hover = dentroDaLinha(mx, my, v);
        if (hover) g.fill(x0 + 6, y, x0 + LARGURA - 6, y + LINHA_ALT - 1, 0x18DAB969);
        g.fill(x0 + 6, y + LINHA_ALT - 1, x0 + LARGURA - 6, y + LINHA_ALT, 0x22000000);
        var item = proposta.produtos().get(linha);
        int quantidade = proposta.quantidades().get(linha);
        int dose = Math.max(1, item.getCount());
        int limite = proposta.limites().get(linha);
        g.item(item, x0 + 10, y + 3);
        g.itemDecorations(font, item, x0 + 10, y + 3);
        g.text(font, font.plainSubstrByWidth(item.getHoverName().getString(), 138),
                x0 + 30, y + 7, 0xFF283D2C);
        String preco = "R$" + subtotal(linha);
        g.text(font, preco, x0 + LARGURA - 108 - font.width(preco), y + 7, 0xFF596447);
        desenharControle(g, botaoMenos(v), "-", podeMexer() && quantidade - dose >= 0, mx, my,
                "prateleira.intoxicantes.menos");
        g.centeredText(font, Component.literal("x" + quantidade),
                x0 + LARGURA - 63, y + 7, 0xFF254C34);
        desenharControle(g, botaoMais(v), "+", podeMexer() && quantidade + dose <= limite, mx, my,
                "prateleira.intoxicantes.mais");
        desenharControle(g, botaoRemover(v), "x", podeMexer(), mx, my,
                "prateleira.intoxicantes.remover");
        if (hover && !dentro(mx, my, botaoMenos(v)) && !dentro(mx, my, botaoMais(v))
                && !dentro(mx, my, botaoRemover(v))) {
            g.setComponentTooltipForNextFrame(font, List.of(item.getHoverName()), mx, my);
        }
    }

    private void desenharControle(GuiGraphicsExtractor g, int[] r, String simbolo,
            boolean ativo, int mx, int my, String chave) {
        boolean hover = dentro(mx, my, r);
        int fundo = !ativo ? 0xFFB9B29E : hover ? 0xFFDAB969 : 0xFFCFC3A0;
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], fundo);
        g.outline(r[0], r[1], r[2], r[3], 0xFF273B2C);
        g.centeredText(font, Component.literal(simbolo), r[0] + r[2] / 2, r[1] + 4,
                ativo ? 0xFF273B2C : 0xFF77766A);
        if (hover) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable(chave)), mx, my);
        }
    }

    private void desenharBarra(GuiGraphicsExtractor g) {
        int altura = LINHAS_VISIVEIS * LINHA_ALT;
        g.fill(x0 + 3, listaY, x0 + 5, listaY + altura, 0x33000000);
        int thumb = Math.max(12, altura * LINHAS_VISIVEIS / totalLinhas());
        int topo = listaY + (altura - thumb) * scrollLinha / maxScroll();
        g.fill(x0 + 3, topo, x0 + 5, topo + thumb, 0xFF596447);
    }

    private void botao(GuiGraphicsExtractor g, int x, int y, String chave, int mx, int my, int cor) {
        g.fill(x, y, x + 100, y + 20, cor);
        g.outline(x, y, 100, 20, mx >= x && mx < x + 100 && my >= y && my < y + 20 ? 0xFFDAB969 : 0xFF273B2C);
        g.centeredText(font, Component.translatable(chave), x + 50, y + 6, 0xFFF8EDCF);
    }
}
