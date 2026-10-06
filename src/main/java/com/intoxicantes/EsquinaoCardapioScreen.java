package com.intoxicantes;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Cardapio do Mercado Esquinao: tela PROPRIA (nada de UI de vilarejo), com
 * a cara da loja — letreiro verde tipo a fachada, papel-moeda bege, e o
 * CARTAO FIDELIDADE no topo (nivel, compras, quanto falta pro proximo).
 *
 * Desenho 100% vetorial pelo GuiGraphicsExtractor (fill/outline/text/item):
 * zero textura, zero widget vanilla — hit-testing manual nas mesmas caixas
 * que sao desenhadas, entao o que parece clicavel E clicavel.
 * Toda regra de negocio (saldo, preco, estoque de indice) e do servidor;
 * aqui e so pintura e envio do indice clicado.
 */
public class EsquinaoCardapioScreen extends Screen {
    // ==================================================== PALETA (a cara da loja)
    private static final int VERDE_BORDA = 0xFF083D1E;   // moldura do letreiro
    private static final int VERDE_LETREIRO = 0xFF0E5A2E; // faixa verde da fachada
    private static final int VERDE_CLARO = 0xFF1E6B38;   // dinheiro / botoes
    private static final int VERDE_HOVER = 0xFF2E8A4A;
    private static final int PAPEL = 0xFFF1E4C3;         // notinha de R$
    private static final int PAPEL_SOMBRA = 0xFFD8C79A;
    private static final int TINTA = 0xFF2B2417;         // texto no papel
    // v1.2.72 (bug 17): o bege antigo sumia no papel claro — castanho de tinta
    // velha, ~5:1 de contraste sobre o bege.
    private static final int TINTA_FRACA = 0xFF6B5B3A;
    private static final int OURO = 0xFFC8971E;          // fidelidade / exclusivos
    private static final int OURO_CLARO = 0xFFF2D06B;
    private static final int VERMELHO = 0xFFB03030;      // preco rasurado / sem saldo
    private static final int LINHA_ZEBRA = 0x28D9C89A;   // linha par da lista
    private static final int LINHA_EXCLUSIVA = 0x30C8971E;

    // ==================================================== LAYOUT
    private static final int LARGURA = 310;
    private static final int LINHA_ALT = 32;

    private EsquinaoNetworking.AbrirCardapioPayload p;
    /** v1.2.44: 0=compra 1=venda 2=exclusivos (a lista de compra fica limpa). */
    private int aba;
    private boolean vendendo;
    private boolean aguardando;
    private int x0;
    private int y0;
    private int altPainel;
    private int listaY;
    private int linhasVisiveis;
    private int scrollLinha;
    private boolean arrastandoBarra;
    private double arrastoOffset;
    /** v1.2.51: 3 posições — a aba EXCLUSIVOS (2) da v1.2.44 estourava o array
     *  antigo de 2 e travava a tela (não dava mais pra voltar pra Buy/Sell). */
    private final int[] scrollAbas = new int[3];

    public EsquinaoCardapioScreen(EsquinaoNetworking.AbrirCardapioPayload payload) {
        super(Component.translatable("gui.intoxicantes.cardapio.titulo"));
        this.p = payload;
    }

    // ==================================================== GEOMETRIA

    @Override
    protected void init() {
        this.altPainel = Math.min(380, this.height - 12);
        this.x0 = (this.width - LARGURA) / 2;
        this.y0 = (this.height - altPainel) / 2;
        this.listaY = this.y0 + 96;
        // linhas que cabem: espaco entre listaY e o rodape (rodape = 26px)
        this.linhasVisiveis = Math.max(1, (this.y0 + altPainel - 36 - this.listaY) / LINHA_ALT);
        this.scrollLinha = Mth.clamp(this.scrollLinha, 0, maxScroll());
    }

    private int totalLinhas() {
        if (aba == 2) {
            int n = p.produtos().size() - p.primeiroExclusivo();
            return Math.max(0, n);
        }
        return vendendo ? p.colheitas().size() : p.produtos().size();
    }

    private int maxScroll() {
        return Math.max(0, totalLinhas() - linhasVisiveis);
    }

    private int barraX() {
        return this.x0 + LARGURA - 8;
    }

    /** Retangulo do botao R$ da linha (indice VISIVEL 0..linhasVisiveis-1). */
    private int[] botaoDaLinha(int linhaVisivel) {
        int y = this.listaY + linhaVisivel * LINHA_ALT + 2;
        return new int[]{this.x0 + LARGURA - 68, y + 3, 54, 16};
    }

    private int[] retanguloFechar() {
        return new int[]{this.x0 + LARGURA - 52, this.y0 + altPainel - 30, 46, 14};
    }

    private boolean dentro(int mx, int my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    public void atualizar(EsquinaoNetworking.AbrirCardapioPayload payload) {
        this.p = payload;
        this.aguardando = false;
        this.scrollLinha = Mth.clamp(scrollLinha, 0, maxScroll());
    }

    private int[] aba(int indice) {
        int larguraAba = 100;
        return new int[]{x0 + 6 + indice * (larguraAba + 3), y0 + 76, larguraAba, 16};
    }

    private boolean disponivel(int index) {
        if (aguardando) return false;
        int indice = aba == 2 ? p.primeiroExclusivo() + index : index;
        boolean venda = aba == 1;
        return venda ? p.cotas().get(indice) > 0 && p.disponiveis().get(indice) >= p.colheitas().get(indice).getCount()
                : p.estoques().get(indice) > 0 && p.saldo() >= p.precos().get(indice);
    }

    // ==================================================== ENTRADA

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean duplo) {
        if (ev.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(ev, duplo);
        double mx = ev.x();
        double my = ev.y();
        for (int i = 0; i < 3; i++) {
            if (dentro((int) mx, (int) my, aba(i))) {
                if (aba != i) {
                    scrollAbas[aba] = scrollLinha;
                    aba = i;
                    vendendo = (aba == 1);
                    scrollLinha = Mth.clamp(scrollAbas[aba], 0, maxScroll());
                    arrastandoBarra = false;
                }
                return true;
            }
        }
        // Fechar
        if (dentro((int) mx, (int) my, retanguloFechar())) {
            this.onClose();
            return true;
        }
        // Botoes R$ das linhas visiveis
        for (int v = 0; v < linhasVisiveis; v++) {
            int indice = scrollLinha + v;
            if (indice >= totalLinhas()) {
                break;
            }
            if (dentro((int) mx, (int) my, botaoDaLinha(v))) {
                if (!disponivel(indice)) return true;
                aguardando = true;
                // O servidor compra pelo índice global do catálogo completo.
                int clique = aba == 2 ? indice + p.primeiroExclusivo() : indice;
                if (vendendo) ClientPlayNetworking.send(new EsquinaoNetworking.VenderPayload(clique));
                else ClientPlayNetworking.send(new EsquinaoNetworking.ComprarPayload(clique));
                return true;
            }
        }
        // Scrollbar: comecar a arrastar
        if (maxScroll() > 0 && (int) mx >= barraX() - 1 && (int) mx <= barraX() + 6
                && (int) my >= listaY && (int) my < listaY + linhasVisiveis * LINHA_ALT) {
            this.arrastandoBarra = true;
            int thumb = thumbHeight();
            double thumbTop = listaY + (linhasVisiveis * LINHA_ALT - thumb) * (double) scrollLinha / maxScroll();
            arrastoOffset = my >= thumbTop && my < thumbTop + thumb ? my - thumbTop : thumb / 2.0;
            atualizarArrasto(my);
            return true;
        }
        return super.mouseClicked(ev, duplo);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        this.arrastandoBarra = false;
        return super.mouseReleased(ev);
    }

    private int thumbHeight() {
        return Math.max(10, linhasVisiveis * LINHA_ALT * linhasVisiveis / totalLinhas());
    }

    private void atualizarArrasto(double my) {
        int travel = linhasVisiveis * LINHA_ALT - thumbHeight();
        scrollLinha = Mth.clamp((int) Math.round((my - listaY - arrastoOffset) * maxScroll() / Math.max(1, travel)), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll() > 0 && mx >= x0 && mx < x0 + LARGURA
                && my >= listaY && my < listaY + linhasVisiveis * LINHA_ALT && dy != 0) {
            scrollLinha = Mth.clamp(scrollLinha - (int) Math.signum(dy), 0, maxScroll());
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ev) {
        // E com o cardapio aberto: fecha o atendimento limpo (o vanilla so abre
        // o inventario quando NAO ha tela; com tela, a tecla vem pra ca primeiro)
        if (this.minecraft.options.keyInventory.matches(ev)) {
            this.onClose();
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
        // avisa o servidor (o Gago sai do atendimento e volta ao posto)
        ClientPlayNetworking.send(new EsquinaoNetworking.FecharCardapioPayload());
        super.onClose();
    }

    // ==================================================== DESENHO

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        desenharDim(g);
        desenharPainel(g, mx, my);
        desenharCabecalho(g);
        desenharFidelidade(g);
        // v1.2.44: abas COMPRA / VENDA / EXCLUSIVOS (a seção ouro saiu da lista)
        for (int i = 0; i < 3; i++) {
            int[] tab = aba(i);
            boolean ativa = this.aba == i;
            g.fill(tab[0], tab[1], tab[0] + tab[2], tab[1] + tab[3], ativa ? VERDE_LETREIRO : PAPEL_SOMBRA);
            textoCentrado(g, Component.translatable(NOME_ABA[i]),
                    tab[0] + tab[2] / 2, tab[1] + 4, ativa ? 0xFFFFFFFF : TINTA);
        }
        desenharLista(g, mx, my);
        desenharRodape(g, mx, my);
        // SEM super: nenhum widget vanilla nesta tela; tudo aqui e desenho proprio
    }

    private static final String[] NOME_ABA = {
            "commerce.intoxicantes.buy", "commerce.intoxicantes.sell", "gui.intoxicantes.cardapio.exclusivos"
    };

    /** Escurece o mundo atras do cardapio (como as telas do vanilla). */
    private void desenharDim(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
    }

    /** Papel-moeda bege com moldura verde dupla. */
    private void desenharPainel(GuiGraphicsExtractor g, int mx, int my) {
        // sombra + papel
        g.fill(x0 + 3, y0 + 3, x0 + LARGURA + 3, y0 + altPainel + 3, 0x66000000);
        g.fill(x0, y0, x0 + LARGURA, y0 + altPainel, PAPEL);
        // moldura dupla: faixa verde externa + filete escuro
        g.outline(x0, y0, LARGURA, altPainel, VERDE_BORDA);
        g.outline(x0 + 1, y0 + 1, LARGURA - 2, altPainel - 2, VERDE_LETREIRO);
        g.outline(x0 + 3, y0 + 3, LARGURA - 6, altPainel - 6, PAPEL_SOMBRA);
    }

    /** Letreiro: "SUL DISTRIBUIDORA / & MERCADO ESQUINAO", igual a fachada. */
    private void desenharCabecalho(GuiGraphicsExtractor g) {
        int alt = 34;
        g.fill(x0 + 4, y0 + 4, x0 + LARGURA - 4, y0 + 4 + alt, VERDE_LETREIRO);
        g.outline(x0 + 4, y0 + 4, LARGURA - 8, alt, VERDE_BORDA);
        // borda "picotada" do letreiro (mesma vibe da fachada de blocos)
        for (int px = x0 + 6; px < x0 + LARGURA - 6; px += 6) {
            g.fill(px, y0 + 4, px + 3, y0 + 6, OURO);
        }
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.cardapio.letreiro1"),
                this.x0 + LARGURA / 2, this.y0 + 9, 0xFFF3E9CF);
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.cardapio.letreiro2"),
                this.x0 + LARGURA / 2, this.y0 + 19, 0xFFFFFFFF);
        g.centeredText(this.font,
                Component.translatable("gui.intoxicantes.cardapio.subtitulo"),
                this.x0 + LARGURA / 2, this.y0 + 30, OURO_CLARO);
    }

    /** Cartao fidelidade: nivel, contador e progresso pro proximo nivel. */
    private void desenharFidelidade(GuiGraphicsExtractor g) {
        int y = this.y0 + 40;
        int alt = 32;
        g.fill(x0 + 4, y, x0 + LARGURA - 4, y + alt, 0xFF0B4A25);
        // "carimbo" ouro a esquerda
        g.fill(x0 + 7, y + 3, x0 + 19, y + alt - 3, OURO);
        g.fill(x0 + 9, y + 5, x0 + 17, y + alt - 5, OURO_CLARO);
        g.text(this.font, "\u2605", x0 + 10, y + 5, VERDE_BORDA);
        // nome do nivel + compras
        Component nomeNivel = Component.translatable("gui.intoxicantes.cardapio.tier."
                + p.nivel());
        g.text(this.font, nomeNivel, x0 + 23, y + 3, OURO_CLARO);
        String contador = Component.translatable("gui.intoxicantes.cardapio.compras",
                p.compras()).getString();
        g.text(this.font, contador, x0 + 23, y + 14, 0xFFD9C89A);
        // desconto a direita
        if (p.nivel() > 0) {
            String desc = Component.translatable("gui.intoxicantes.cardapio.desconto",
                    FidelidadeData.DESCONTO_TIER[p.nivel()]).getString();
            g.text(this.font, desc, x0 + LARGURA - 10 - this.font.width(desc), y + 3, OURO_CLARO);
        }
        // barra de progresso pro proximo nivel
        if (p.comprasProximoNivel() > 0) {
            int anterior = FidelidadeData.META_TIER[Math.min(p.nivel(), FidelidadeData.META_TIER.length - 1)];
            int meta = p.comprasProximoNivel();
            int span = Math.max(1, meta - anterior);
            float frac = Mth.clamp((p.compras() - anterior) / (float) span, 0f, 1f);
            int bx = x0 + 23;
            int bw = 200;
            int by = y + alt - 5;
            g.fill(bx, by, bx + bw, by + 2, 0xFF06301A);
            g.fill(bx, by, bx + Math.max(1, (int) (bw * frac)), by + 2, OURO);
            String falta = Component.translatable("gui.intoxicantes.cardapio.faltam",
                    meta - p.compras()).getString();
            g.text(this.font, falta, bx + bw + 4, by - 3, 0xFFD9C89A);
        } else {
            String topo = Component.translatable("gui.intoxicantes.cardapio.topo").getString();
            g.text(this.font, topo, x0 + LARGURA - 10 - this.font.width(topo), y + 11, OURO_CLARO);
        }
    }

    /** A lista do cardapio: icones, nomes, precos (rasurado + com desconto) e botoes R$. */
    private void desenharLista(GuiGraphicsExtractor g, int mx, int my) {
        int altLista = linhasVisiveis * LINHA_ALT;
        // fundo da area da lista (papel escurecido) pra marcar a regiao scrollavel
        g.fill(x0 + 4, listaY, x0 + LARGURA - 4, listaY + altLista, 0x18D9C89A);
        g.enableScissor(x0 + 4, listaY, x0 + LARGURA - 4, listaY + altLista);

        // v1.2.53: aba EXCLUSIVOS vazia no Gago (os exclusivos são do ponto do
        // traficante) — mensagem em vez de deserto, e nada clicável
        if (aba == 2 && totalLinhas() == 0) {
            g.disableScissor();
            String msg = net.minecraft.client.resources.language.I18n.get(
                    "gui.intoxicantes.cardapio.exclusivos_vazio");
            g.text(this.font, msg, x0 + (LARGURA - this.font.width(msg)) / 2,
                    listaY + altLista / 2 - 4, TINTA_FRACA);
            return;
        }

        for (int v = 0; v < linhasVisiveis; v++) {
            int indice = scrollLinha + v;
            if (indice >= totalLinhas()) {
                break;
            }
            // v1.2.44: na aba EXCLUSIVOS o índice VISÍVEL vira global (+ offset)
            if (aba == 2) indice += p.primeiroExclusivo();
            int ry = listaY + v * LINHA_ALT;
            boolean exclusivo = aba == 2 || (!vendendo && indice >= p.primeiroExclusivo());
            boolean hover = (int) mx >= x0 + 4 && (int) mx < x0 + LARGURA - 10
                    && (int) my >= ry && (int) my < ry + LINHA_ALT;

            // zebra / exclusivo / hover
            if (exclusivo) {
                g.fill(x0 + 4, ry, x0 + LARGURA - 10, ry + LINHA_ALT, LINHA_EXCLUSIVA);
            } else if (indice % 2 == 0) {
                g.fill(x0 + 4, ry, x0 + LARGURA - 10, ry + LINHA_ALT, LINHA_ZEBRA);
            }
            if (hover) {
                g.fill(x0 + 4, ry, x0 + LARGURA - 10, ry + LINHA_ALT, 0x20FFFFFF);
            }

            ItemStack produto = (vendendo ? p.colheitas() : p.produtos()).get(indice);
            int precoCheio = (vendendo ? p.pagamentos() : p.precoCheio()).get(indice);
            int preco = (vendendo ? p.pagamentos() : p.precos()).get(indice);
            boolean comDesconto = preco != precoCheio;
            boolean semSaldo = !disponivel(scrollLinha + v);
            int stock = (vendendo ? p.cotas() : p.estoques()).get(indice);

            // icone do produto + contagem (x4 etc.)
            g.item(produto, x0 + 6, ry);
            g.itemDecorations(this.font, produto, x0 + 6, ry);

            // nome (truncado) — exclusivos em ouro com estrela
            String nome = produto.getHoverName().getString();
            int nomeLargura = 154;
            String visivel = (exclusivo ? "\u2605 " : "") + nome;
            visivel = this.font.plainSubstrByWidth(visivel, nomeLargura);
            g.text(this.font, visivel, x0 + 24, ry + 4, exclusivo ? 0xFF8A6A10 : TINTA);

            String detalhe = Component.translatable(vendendo ? "commerce.intoxicantes.quota" : "commerce.intoxicantes.stock", stock).getString();
            g.text(this.font, this.font.plainSubstrByWidth(detalhe, 154), x0 + 24, ry + 14, stock == 0 ? VERMELHO : TINTA_FRACA);
            if (vendendo) {
                String lote = Component.translatable("commerce.intoxicantes.batch", produto.getCount(), p.disponiveis().get(indice)).getString();
                g.text(this.font, this.font.plainSubstrByWidth(lote, 210), x0 + 24, ry + 24, TINTA_FRACA);
            }

            // preco: cheio rasurado (vermelho) + com desconto (verde)
            int px = x0 + 185;
            if (comDesconto) {
                String velho = "R$" + precoCheio;
                g.text(this.font, velho, px, ry + 4, VERMELHO);
                int w = this.font.width(velho);
                g.horizontalLine(px - 1, px + w + 1, ry + 8, VERMELHO);
                String novo = "R$" + preco;
                g.text(this.font, novo, px, ry + 15, semSaldo ? VERMELHO : VERDE_CLARO);
            } else {
                String etiqueta = "R$" + preco;
                g.text(this.font, etiqueta, px, ry + 4, semSaldo ? VERMELHO : TINTA);
            }

            // botao R$
            int[] b = botaoDaLinha(v);
            boolean botaoHover = dentro(mx, my, b) && !semSaldo;
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3],
                    semSaldo ? 0xFF7A7A6E : botaoHover ? VERDE_HOVER : VERDE_CLARO);
            g.outline(b[0], b[1], b[2], b[3], VERDE_BORDA);
            String rotulo = aguardando ? "…" : Component.translatable(stock == 0 ? "commerce.intoxicantes.sold_out"
                    : semSaldo ? vendendo ? "commerce.intoxicantes.missing" : "commerce.intoxicantes.no_balance"
                    : vendendo ? "commerce.intoxicantes.sell_short" : "gui.intoxicantes.cardapio.comprar_curtinho").getString();
            g.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(rotulo, b[2] - 4)),
                    b[0] + b[2] / 2, b[1] + 4, 0xFFF3E9CF);
            if (hover) {
                java.util.ArrayList<Component> tip = new java.util.ArrayList<>();
                tip.add(produto.getHoverName());
                if (vendendo) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(produto.getItem()).getPath();
                    tip.add(Component.translatable("commerce.intoxicantes.use." + id));
                    tip.add(Component.translatable("commerce.intoxicantes.payment", preco, produto.getCount()));
                }
                if (aguardando) tip.add(Component.translatable("commerce.intoxicantes.pending"));
                else if (stock == 0) tip.add(Component.translatable("commerce.intoxicantes.result.out_of_stock"));
                else if (semSaldo) tip.add(vendendo
                        ? Component.translatable("commerce.intoxicantes.need", produto.getCount() - p.disponiveis().get(indice))
                        : Component.translatable("commerce.intoxicantes.result.no_money"));
                g.setComponentTooltipForNextFrame(this.font, tip, mx, my);
            }
        }
        g.disableScissor();

        // scrollbar (so quando precisa)
        if (maxScroll() > 0) {
            int altLista2 = linhasVisiveis * LINHA_ALT;
            g.fill(barraX(), listaY, barraX() + 4, listaY + altLista2, 0xFFC9B888);
            float frac = (float) scrollLinha / maxScroll();
            int altAlmofada = thumbHeight();
            int py = listaY + (int) ((altLista2 - altAlmofada) * frac);
            g.fill(barraX(), py, barraX() + 4, py + altAlmofada, VERDE_LETREIRO);
            g.outline(barraX(), py, 4, altAlmofada, VERDE_BORDA);
        }
    }


    /** v1.2.44: texto CENTRADO sem sombra (a flag do centeredText nao existe). */
    private void textoCentrado(GuiGraphicsExtractor g, Component texto, int cx, int y, int cor) {
        g.text(this.font, texto, cx - this.font.width(texto) / 2, y, cor, false);
    }

    /** Rodape: saldo do fregues + botao FECHAR. */
    private void desenharRodape(GuiGraphicsExtractor g, int mx, int my) {
        int y = this.y0 + altPainel - 34;
        g.horizontalLine(x0 + 6, x0 + LARGURA - 6, y - 2, PAPEL_SOMBRA);
        String saldo = Component.translatable("gui.intoxicantes.cardapio.saldo",
                p.saldo()).getString();
        g.text(this.font, saldo, x0 + 8, y + 4, TINTA);

        g.text(this.font, Component.translatable("commerce.intoxicantes.restock"), x0 + 8, y + 17, TINTA_FRACA);
        int[] f = retanguloFechar();
        boolean hover = dentro(mx, my, f);
        g.fill(f[0], f[1], f[0] + f[2], f[1] + f[3], hover ? VERDE_HOVER : VERDE_LETREIRO);
        g.outline(f[0], f[1], f[2], f[3], VERDE_BORDA);
        String fechar = Component.translatable("gui.intoxicantes.cardapio.fechar").getString();
        g.centeredText(this.font, Component.literal(fechar), f[0] + f[2] / 2, f[1] + 3, 0xFFF3E9CF);
    }
}
