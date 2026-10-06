package com.intoxicantes;

import java.util.Locale;
import java.util.UUID;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

/** Talão de papel e tinta azul: somente o servidor confirma cobrança e prêmio. */
public class PeruScreen extends Screen {
    private static final int W = 420, H = 320;
    private static final int PAPEL = 0xFFEEE6CF, TINTA = 0xFF243C63,
            LINHA = 0xFFBCAD91, CLARO = 0xFFF8F0D9, SELO = 0xFFB76D3B;
    private PeruNetworking.Estado estado;
    private int aba, grupo = 1, dezena = 1, tipo, pagina, editando = -1, ticks, ticksPedido;
    private int valor;
    private String rascunho = "", mensagem = "";
    private float escala = 1;
    private int x0, y0;
    private boolean confirmar, fechou;
    private PeruNetworking.Acao pedido;

    public PeruScreen(PeruNetworking.Estado estado) {
        super(Component.translatable("gui.intoxicantes.peru.titulo"));
        this.estado = estado;
        valor = Math.max(estado.dados().minimo(), Math.min(10, estado.dados().maximo()));
    }
    public void atualizar(PeruNetworking.Estado novo) {
        if (!estado.sessao().equals(novo.sessao())) return;
        if (!novo.valido()) { mensagem = "fora_alcance"; pedido = null; confirmar = false; }
        if (novo.dados().proximoDia() != estado.dados().proximoDia()
                || novo.dados().horarioSorteio() != estado.dados().horarioSorteio()
                || novo.dados().multiplicadorGrupo() != estado.dados().multiplicadorGrupo()
                || novo.dados().multiplicadorDezena() != estado.dados().multiplicadorDezena()) confirmar = false;
        if (!novo.mensagem().isEmpty()) {
            mensagem = novo.mensagem();
            if (!mensagem.equals("erro_persistencia")) pedido = null;
        } else if (pedido != null) {
            var bilhete = novo.dados().apostas().stream().filter(a -> a.id().equals(pedido.pedido())).findFirst();
            if (bilhete.isPresent() && bilhete.get().estado() != JogoDoBicho.Estado.PREPARADA) {
                mensagem = bilhete.get().estado() == JogoDoBicho.Estado.REJEITADA ? "sem_saldo" : "aceita";
                pedido = null;
            }
        }
        estado = novo;
    }
    public UUID sessao() { return estado.sessao(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        escala = Math.min(1F, Math.min(Math.max(1, width - 12) / (float) W,
                Math.max(1, height - 12) / (float) H));
        x0 = Math.round((width - W * escala) / 2); y0 = Math.round((height - H * escala) / 2);
    }
    private String tr(String id, Object... args) { return Component.translatable("gui.intoxicantes.peru." + id, args).getString(); }
    private String bicho(int n) { return Component.translatable("bicho.intoxicantes." + JogoDoBicho.grupos().get(n - 1).id()).getString(); }
    private String reais(long n) { return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,d", n); }
    private String numero(int n) { return String.format(Locale.ROOT, "%02d", n % 100); }
    private String horario(int ticks) { return String.format(Locale.ROOT, "%02d:%02d", (ticks / 1000 + 6) % 24, ticks % 1000 * 60 / 1000); }
    private int multiplicador() { return tipo == 0 ? estado.dados().multiplicadorGrupo() : estado.dados().multiplicadorDezena(); }
    private void texto(GuiGraphicsExtractor g, String s, int x, int y, int w, int cor) {
        if (font.width(s) > w) s = font.plainSubstrByWidth(s, Math.max(0, w - font.width("..."))) + "...";
        g.text(font, s, x, y, cor, false);
    }
    private void caixa(GuiGraphicsExtractor g, int x, int y, int w, int h, int cor) {
        g.fill(x, y, x + w, y + h, cor); g.outline(x, y, w, h, LINHA);
    }
    private void botao(GuiGraphicsExtractor g, String s, int x, int y, int w, int h, boolean ativo) {
        caixa(g, x, y, w, h, ativo ? TINTA : 0xFFDDD4BD);
        texto(g, s, x + Math.max(2, (w - font.width(s)) / 2), y + (h - 8) / 2, w - 4, ativo ? PAPEL : TINTA);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float parcial) {
        g.fill(0, 0, width, height, 0xA0000000);
        g.pose().pushMatrix(); g.pose().translate(x0, y0); g.pose().scale(escala, escala);
        caixa(g, 0, 0, W, H, PAPEL); g.outline(2, 2, W - 4, H - 4, TINTA);
        g.pose().pushMatrix(); g.pose().translate(12, 12); g.pose().scale(1.5F, 1.5F);
        texto(g, tr("titulo"), 0, 0, 180, TINTA); g.pose().popMatrix();
        texto(g, tr("subtitulo"), 13, 34, 245, 0xFF706755);
        texto(g, reais(estado.dados().saldo()), 288, 20, 111, TINTA);
        botao(g, "×", 400, 6, 14, 14, false);
        g.fill(12, 50, 403, 51, LINHA); g.fill(12, 77, 403, 78, LINHA);
        texto(g, tr("proximo", horario(estado.dados().horarioSorteio())), 12, 59, 237, TINTA);
        texto(g, tr("dia", estado.dados().proximoDia()), 290, 59, 110, TINTA);
        for (int i = 0; i < 3; i++) botao(g, tr(new String[]{"apostar", "minhas", "resultados"}[i]), 12 + i * 130, 84, 126, 18, aba == i);
        if (aba == 0) apostar(g); else if (aba == 1) minhas(g); else resultados(g);
        if (confirmar) confirmacao(g);
        g.pose().popMatrix();
    }
    private void apostar(GuiGraphicsExtractor g) {
        botao(g, tr("grupo", estado.dados().multiplicadorGrupo()), 12, 111, 122, 19, tipo == 0);
        botao(g, tr("dezena", estado.dados().multiplicadorDezena()), 138, 111, 122, 19, tipo == 1);
        for (int i = 0; i < 25; i++) {
            int x = 12 + i % 5 * 51, y = 137 + i / 5 * 25;
            caixa(g, x, y, 48, 21, grupo == i + 1 ? TINTA : 0xFFDDD4BD);
            texto(g, bicho(i + 1), x + 2, y + 3, 44, grupo == i + 1 ? PAPEL : TINTA);
            g.pose().pushMatrix(); g.pose().translate(x + 3, y + 13); g.pose().scale(.65F, .65F);
            texto(g, numero(i * 4 + 1) + "–" + numero(i * 4 + 4), 0, 0, 66, grupo == i + 1 ? PAPEL : TINTA);
            g.pose().popMatrix();
        }
        caixa(g, 275, 111, 127, 166, CLARO);
        texto(g, tr("escolhido"), 283, 121, 110, 0xFF706755);
        texto(g, bicho(grupo), 283, 136, 111, TINTA);
        texto(g, tr(tipo == 0 ? "quatro" : "numero"), 283, 156, 111, 0xFF706755);
        botao(g, editando == 0 ? rascunho + "_" : numero(dezena), 283, 169, 111, 19, editando == 0);
        texto(g, tr("valor"), 283, 195, 111, 0xFF706755);
        botao(g, "−", 283, 209, 20, 20, false);
        botao(g, editando == 1 ? rascunho + "_" : "" + valor, 306, 209, 64, 20, editando == 1);
        botao(g, "+", 374, 209, 20, 20, false);
        texto(g, tr("premio"), 283, 240, 111, 0xFF706755);
        texto(g, reais((long) valor * multiplicador()), 283, 256, 111, TINTA);
        texto(g, tr("limites", reais(estado.dados().minimo()), reais(estado.dados().maximo()), estado.dados().limite()), 12, 267, 252, 0xFF706755);
        String msg = mensagem.isEmpty() ? tr("escolha") : tr("status." + mensagem);
        texto(g, pedido != null ? tr("aguardando") : msg, 12, 287, 250, TINTA);
        caixa(g, 275, 283, 127, 21, estado.valido() ? SELO : LINHA);
        texto(g, tr(pedido != null && ticks - ticksPedido > 100 ? "reenviar" : "conferir"), 280, 290, 117, CLARO);
    }
    private void minhas(GuiGraphicsExtractor g) {
        var apostas = estado.dados().apostas(); int inicio = pagina * 5;
        if (apostas.isEmpty()) texto(g, tr("sem_apostas"), 16, 123, 380, TINTA);
        for (int i = inicio; i < Math.min(apostas.size(), inicio + 5); i++) {
            var a = apostas.get(i); int y = 112 + (i - inicio) * 28;
            texto(g, bicho(a.grupo()) + (a.tipo() == JogoDoBicho.Tipo.DEZENA ? " • " + numero(a.dezena()) : " • " + tr("grupo_nome")), 16, y, 214, TINTA);
            texto(g, tr("dia", a.dia()) + " • " + reais(a.valor()) + (a.premio() > 0 ? " → " + reais(a.premio()) : ""), 16, y + 12, 248, 0xFF706755);
            texto(g, tr("bilhete." + a.estado().name().toLowerCase(Locale.ROOT)), 277, y + 5, 121, SELO);
            g.fill(12, y + 25, 402, y + 26, LINHA);
        }
        botao(g, tr("anterior"), 12, 266, 95, 20, false);
        botao(g, tr("seguinte"), 307, 266, 95, 20, false);
        texto(g, tr("pagamentos"), 12, 299, 389, TINTA);
    }
    private void resultados(GuiGraphicsExtractor g) {
        var resultados = estado.dados().resultados();
        if (resultados.isEmpty()) texto(g, tr("sem_resultados"), 16, 123, 380, TINTA);
        for (int i = 0; i < resultados.size(); i++) {
            var r = resultados.get(i); int y = 115 + i * 23;
            texto(g, tr("dia", r.dia()) + " • " + bicho(r.grupo()), 16, y, 245, TINTA);
            texto(g, horario(r.horarioSorteio()), 283, y, 60, TINTA);
            texto(g, numero(r.dezena()), 374, y, 25, SELO);
            g.fill(12, y + 17, 402, y + 18, LINHA);
        }
        texto(g, tr("mesmo_resultado"), 12, 299, 389, TINTA);
    }
    private void confirmacao(GuiGraphicsExtractor g) {
        g.fill(3, 3, W - 3, H - 3, 0x99252B33); caixa(g, 63, 100, 290, 160, CLARO);
        texto(g, tr("conferir"), 78, 115, 261, TINTA);
        texto(g, bicho(grupo) + (tipo == 1 ? " • " + numero(dezena) : " • " + tr("grupo_nome")), 78, 140, 260, TINTA);
        texto(g, tr("debito", reais(valor)), 78, 159, 260, TINTA);
        texto(g, tr("ganho", reais((long) valor * multiplicador())), 78, 178, 260, TINTA);
        texto(g, tr("dia", estado.dados().proximoDia()) + " • " + horario(estado.dados().horarioSorteio()), 78, 197, 260, TINTA);
        botao(g, tr("voltar"), 78, 220, 120, 22, false); botao(g, tr("confirmar"), 207, 220, 120, 22, true);
    }
    private boolean hit(double x, double y, int bx, int by, int w, int h) { return x >= bx && x < bx + w && y >= by && y < by + h; }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean duplo) {
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(e, duplo);
        double x = (e.x() - x0) / escala, y = (e.y() - y0) / escala;
        if (hit(x, y, 400, 6, 14, 14)) { onClose(); return true; }
        if (confirmar) {
            if (hit(x, y, 78, 220, 120, 22)) confirmar = false;
            else if (hit(x, y, 207, 220, 120, 22)) {
                confirmar = false;
                pedido = new PeruNetworking.Acao(estado.sessao(), UUID.randomUUID(), 1, tipo, grupo,
                        tipo == 1 ? dezena : 0, valor, estado.dados().proximoDia(), estado.dados().horarioSorteio(), multiplicador());
                ticksPedido = ticks; ClientPlayNetworking.send(pedido);
            }
            return true;
        }
        terminarEdicao();
        for (int i = 0; i < 3; i++) if (hit(x, y, 12 + i * 130, 84, 126, 18)) { aba = i; pagina = 0; return true; }
        if (aba == 1) {
            if (hit(x, y, 12, 266, 95, 20)) pagina = Math.max(0, pagina - 1);
            if (hit(x, y, 307, 266, 95, 20)) pagina = Math.min(Math.max(0, (estado.dados().apostas().size() - 1) / 5), pagina + 1);
        }
        if (aba != 0 || !estado.valido()) return true;
        if (pedido != null) {
            if (hit(x, y, 275, 283, 127, 21) && ticks - ticksPedido > 100) {
                ticksPedido = ticks; ClientPlayNetworking.send(pedido);
            }
            return true;
        }
        if (hit(x, y, 12, 111, 122, 19)) tipo = 0;
        if (hit(x, y, 138, 111, 122, 19)) tipo = 1;
        for (int i = 0; i < 25; i++) if (hit(x, y, 12 + i % 5 * 51, 137 + i / 5 * 25, 48, 21)) {
            grupo = i + 1; dezena = (i * 4 + 1) % 100;
        }
        if (tipo == 1 && hit(x, y, 283, 169, 111, 19)) { editando = 0; rascunho = ""; }
        if (hit(x, y, 306, 209, 64, 20)) { editando = 1; rascunho = ""; }
        if (hit(x, y, 283, 209, 20, 20)) valor = Math.max(estado.dados().minimo(), valor - 10);
        if (hit(x, y, 374, 209, 20, 20)) valor = Math.min(estado.dados().maximo(), valor + 10);
        if (hit(x, y, 275, 283, 127, 21)) {
            if (valor > estado.dados().saldo()) mensagem = "sem_saldo";
            else if (valor < estado.dados().minimo() || valor > estado.dados().maximo()) mensagem = "invalida";
            else confirmar = true;
        }
        return true;
    }
    private void terminarEdicao() {
        if (editando >= 0 && !rascunho.isEmpty()) {
            try {
                int n = Integer.parseInt(rascunho);
                if (editando == 0 && n >= 0 && n <= 99) { dezena = n; grupo = JogoDoBicho.grupoDaDezena(n); }
                else if (editando == 1) valor = Math.max(estado.dados().minimo(), Math.min(estado.dados().maximo(), n));
            } catch (NumberFormatException ignored) { mensagem = "invalida"; }
        }
        editando = -1;
    }
    @Override public boolean charTyped(CharacterEvent e) {
        if (editando < 0) return super.charTyped(e);
        String c = e.codepointAsString();
        if (c.length() == 1 && c.charAt(0) >= '0' && c.charAt(0) <= '9'
                && rascunho.length() < (editando == 0 ? 2 : 6)) rascunho += c;
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            if (confirmar) confirmar = false; else if (editando >= 0) editando = -1; else onClose();
            return true;
        }
        if (editando >= 0) {
            if (e.key() == InputConstants.KEY_BACKSPACE && !rascunho.isEmpty()) rascunho = rascunho.substring(0, rascunho.length() - 1);
            if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) terminarEdicao();
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void tick() {
        if (++ticks % 20 == 0 && estado.valido()) ClientPlayNetworking.send(new PeruNetworking.Acao(
                estado.sessao(), new UUID(0, 0), 0, 0, 0, 0, 0, 0, 0, 0));
    }
    private void fecharSessao() {
        if (!fechou) {
            ClientPlayNetworking.send(new PeruNetworking.Acao(estado.sessao(), new UUID(0, 0), 2, 0, 0, 0, 0, 0, 0, 0));
            fechou = true;
        }
    }
    @Override public void onClose() { fecharSessao(); super.onClose(); }
    @Override public void removed() { fecharSessao(); super.removed(); }
}
