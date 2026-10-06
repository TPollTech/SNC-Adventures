package com.intoxicantes;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * v1.2.58 — O RELÓGIO SIMPLIFICADO: canto superior esquerdo, SÓ a hora do
 * mundo (o "Dia N" saiu — quem quer o dia vê o debug ou a lua kkkk).
 *
 * A hora segue o clock oficial do overworld (os markers da timeline: noon =
 * tick 6000, midnight = tick 18000), com o offset +6h do amanhecer vanilla.
 * v1.2.69 — O DIA DA SEMANA voltou (imersão do mercadinho): a entrega da
 * prateleira é QUARTA (CalendarioEsquinao) e o freguês precisa saber —
 * "qua 14:32" ao lado do sol. Semana do AMANHECER (06:00): a data que o
 * jogador acorda vendo é a que o HUD mostra.
 */
public final class RelogioHud {

    // paleta discreta: fundo escuro translúcido, texto claro, âmbar pra hora
    private static final int COR_FUNDO = 0x5A0E0C0A;
    private static final int COR_BORDA = 0x5A3A342C;
    private static final int COR_HORA = 0xFFE8D8B0;
    private static final int COR_DIA = 0xFFC8D0E0;   // v1.2.69: o dia da semana de madrugada
    private static final int COR_SOL = 0xFFE8C84A;
    private static final int COR_LUA = 0xFFC8CCD8;

    private RelogioHud() {}

    public static void init() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "relogio_hud"),
                RelogioHud::desenhar);
    }

    private static void desenhar(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.keyToggleGui.isDown()) {
            return;
        }
        // esconde com qualquer tela aberta (chat, inventário, guia) — discreto
        if (mc.gui.screen() != null) {
            return;
        }

        long tempo = mc.level.getOverworldClockTime();
        long ticks = ((tempo % 24000L) + 24000L) % 24000L;
        int horas = (int) ((ticks / 1000L + 6L) % 24L);
        int minutos = (int) ((ticks % 1000L) * 60L / 1000L);
        boolean diaLuz = ticks < 12500L; // sol até ~18:30, depois lua

        String hora = String.format("%02d:%02d", horas, minutos);

        Font fonte = mc.font;
        // v1.2.69: o dia da semana (3 letras, "qua") — a semana do amanhecer;
        // a MESMA fonte de tempo da hora acima (o clock do overworld)
        long dayTime = mc.level.getOverworldClockTime();
        int diaSemana = CalendarioEsquinao.diaDaSemanaAmanhecer(dayTime);
        String abreviado = Component.translatable(
                "calendario.intoxicantes." + CalendarioEsquinao.nome(diaSemana) + ".curto")
                .getString();
        String rotulo = abreviado + " " + hora;

        int larguraHora = fonte.width(rotulo);
        int largura = 10 + larguraHora + 5;
        int altura = 12;
        int x = 4;
        int y = 4;

        g.fill(x, y, x + largura, y + altura, COR_FUNDO);
        g.fill(x, y, x + largura, y + 1, COR_BORDA);           // borda fina
        g.fill(x, y + altura - 1, x + largura, y + altura, COR_BORDA);

        desenharCorpo(g, x + 2, y + 6, diaLuz);

        int tx = x + 10;
        g.text(fonte, rotulo, tx, y + 2, diaLuz ? COR_HORA : COR_DIA, false);
    }

    /**
     * O corpo celeste de 7px: SOL = núcleo + 4 raios; LUA = crescente
     * (quadrado claro com recorte escuro no canto superior direito).
     */
    private static void desenharCorpo(GuiGraphicsExtractor g, int cx, int cy, boolean sol) {
        if (sol) {
            g.fill(cx + 2, cy - 2, cx + 4, cy + 2, COR_SOL);   // núcleo
            g.fill(cx, cy - 1, cx + 1, cy + 1, COR_SOL);        // raios
            g.fill(cx + 5, cy - 1, cx + 6, cy + 1, COR_SOL);
            g.fill(cx + 2, cy - 4, cx + 4, cy - 3, COR_SOL);
            g.fill(cx + 2, cy + 3, cx + 4, cy + 4, COR_SOL);
        } else {
            g.fill(cx + 1, cy - 2, cx + 5, cy + 2, COR_LUA);    // disco
            g.fill(cx + 3, cy - 3, cx + 6, cy, COR_FUNDO);      // recorte = crescente
        }
    }
}
