package com.intoxicantes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * v1.2.54 — CLIENT DA SAUDE. Tres pecas:
 *
 * 1. HUD CONTEXTUAL (o "HUDzinho"): SEDE colada em cima da barra de FOME
 *    (lado direito do hotbar) e VICIO em cima da barra de VIDA (lado esquerdo).
 *    So aparecem quando algo esta acontecendo (sede < 100, vicio > 0) e deslizam
 *    pra cima se a armadura/bolhas de ar ocuparem a fileira. Abstinencia com
 *    TREMOR (as barras literalmente treme com o fregues). Nada na tela quando saudavel.
 * 2. OVERLAY DAS VIAGENS: tintas de tela e efeitos por viagem ativa — o
 *    MORNO tinta ambar, o SONHO dessatura, o OVERDRIVE pulsa o zoom com a
 *    "batida", o VIAGEM cicla hue, e a ABSTINENCIA escurece as bordas.
 *    (O psicodelico pesado do VIAGEM fica no SaudeVisionClient.)
 * 3. A TECLA H: manda o payload C2S; o servidor responde com o sync +
 *    abrirProntuario=true e a tela abre (receiver do payload, abaixo).
 */
public final class SaudeClient {
    private SaudeClient() {}

    // ==================================================== ESTADO RECEBIDO (S2C)

    private static volatile int hidratacao = SaudeData.HIDRATACAO_MAX;
    private static volatile int vicio;
    private static volatile String drogaVicio = "";
    private static volatile int estagioAbstinencia;
    private static volatile int alcool, erva, po, pilula; // histórico pro prontuário
    /** Última viagem ativa (nome do enum) + fim estimado (ms do client). */
    private static volatile String viagemAtiva = "";
    private static volatile int intensidadeViagem = 1;
    private static long fimDaViagem;

    // ==================================================== TECLA H

    private static net.minecraft.client.KeyMapping teclaProntuario;

    // paleta do HUD (a mesma vibe discreta do RelogioHud)
    private static final int COR_BORDA = 0x5A3A342C;
    private static final int COR_AGUA = 0xFF4AA8E8;
    private static final int COR_AGUA_FRACA = 0xFF2A5A80;
    private static final int COR_VICIO = 0xFFC050C8;
    private static final int COR_VICIO_FRACA = 0xFF5A2A60;
    private static final int COR_ABSTINENCIA = 0xFFD8B048;
    private static final int COR_CRITICA = 0xFFE06050;

    public static void init() {
        // S2C: o sync periodico + o pedido de abrir o prontuario
        ClientPlayNetworking.registerGlobalReceiver(SaudeNetworking.SaudeSyncPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    hidratacao = payload.hidratacao();
                    vicio = payload.vicio();
                    drogaVicio = payload.drogaVicio();
                    estagioAbstinencia = payload.estagioAbstinencia();
                    alcool = payload.alcool();
                    erva = payload.erva();
                    po = payload.po();
                    pilula = payload.pilula();
                    if (payload.abrirProntuario()) {
                        Minecraft mc = Minecraft.getInstance();
                        mc.gui.setScreen(new ProntuarioScreen(new ProntuarioScreen.Dados(
                                payload.hidratacao(), payload.figado(), payload.pulmao(),
                                payload.estomago(), payload.vicio(), payload.drogaVicio(),
                                payload.alcool(), payload.erva(), payload.po(),
                                payload.pilula(), payload.curasSeguidas(),
                                payload.estagioAbstinencia())));
                    }
                }));
        // S2C: a viagem ligou
        ClientPlayNetworking.registerGlobalReceiver(SaudeNetworking.ViagemPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    viagemAtiva = payload.viagem();
                    intensidadeViagem = payload.intensidade();
                    fimDaViagem = System.currentTimeMillis() + payload.segundos() * 1000L;
                }));

        ClientTickEventsFimDeTick.registrar();

        // a TECLA H do prontuario (GAMEPLAY, na fileira das armas)
        teclaProntuario = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
                new net.minecraft.client.KeyMapping(
                        "key.intoxicantes.prontuario",
                        InputConstants.KEY_H,
                        net.minecraft.client.KeyMapping.Category.GAMEPLAY));

        // o HUDzinho (depois das armas, antes do relogio na ordem de pintura)
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "saude_hud"),
                SaudeClient::desenharHud);
    }

    /** Ponte pro ClientTickEvents (classe separada pra manter o init limpo). */
    private static final class ClientTickEventsFimDeTick {
        static void registrar() {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                    .END_CLIENT_TICK.register(mc -> {
                        while (teclaProntuario.consumeClick()) {
                            if (mc.player != null && mc.gui.screen() == null) {
                                ClientPlayNetworking.send(new SaudeNetworking.AbrirProntuarioPayload());
                            }
                        }
                        // viagem vencida: limpa (o servidor reenvia na proxima dose)
                        if (!viagemAtiva.isEmpty() && System.currentTimeMillis() > fimDaViagem) {
                            viagemAtiva = "";
                        }
                    });
        }
    }

    /** A tela H fecha com H também (o KeyMapping compara a KeyEvent). */
    static boolean teclaProntuarioMatches(net.minecraft.client.input.KeyEvent ev) {
        return teclaProntuario != null && teclaProntuario.matches(ev);
    }

    // ==================================================== HUD

    private static void desenharHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.keyToggleGui.isDown()) {
            return;
        }
        if (mc.gui.screen() != null) {
            return;
        }
        // nada pra mostrar = nada desenhado (o HUDzinho invisivel da casa saudavel)
        if (hidratacao >= SaudeData.HIDRATACAO_MAX && vicio <= 0) {
            return;
        }

        int w = g.guiWidth();
        int h = g.guiHeight();

        // TREMOR da abstinencia: as barras seguem a mao do fregues
        int tremerX = 0;
        int tremerY = 0;
        if (estagioAbstinencia > 0) {
            long t = System.currentTimeMillis();
            int forca = Math.min(2, estagioAbstinencia);
            tremerX = (int) ((t / 60) % 2 == 0 ? forca : -forca);
            tremerY = (int) ((t / 90) % 2 == 0 ? -1 : 1);
        }

        // a fileira vanilla inteira: 10 icones de 9px (vida a esquerda, fome a direita)
        final int larguraBarra = 81;
        final int alturaBarra = 5;
        final int fileiraBase = h - 49; // a fileira DE CIMA de vida/fome (que ficam em h-39)

        // --- SEDE: colada EM CIMA da barra de fome (lado direito do hotbar)
        if (hidratacao < SaudeData.HIDRATACAO_MAX) {
            int y = fileiraBase;
            // as bolhas de ar ocupam essa fileira quando aparecem: desliza pra cima
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                y -= 10;
            }
            int x = w / 2 + 91 - larguraBarra + tremerX;
            desenharBarra(g, x, y + tremerY, larguraBarra, alturaBarra,
                    larguraBarra * hidratacao / SaudeData.HIDRATACAO_MAX,
                    hidratacao <= 25 ? COR_CRITICA : COR_AGUA, COR_AGUA_FRACA);
        }

        // --- VICIO: colado EM CIMA da barra de vida (lado esquerdo do hotbar)
        if (vicio > 0) {
            int y = fileiraBase;
            // armadura (ou a segunda fileira de coracoes de absorcao) ocupa a fileira
            if (player.getArmorValue() > 0
                    || player.getHealth() + player.getAbsorptionAmount() > 20.0F) {
                y -= 10;
            }
            int x = w / 2 - 91 + tremerX;
            desenharBarra(g, x, y + tremerY, larguraBarra, alturaBarra,
                    larguraBarra * vicio / SaudeData.VICIO_MAX, COR_VICIO, COR_VICIO_FRACA);

            if (estagioAbstinencia > 0) {
                String rotulo = Component.translatable(
                        "hud.intoxicantes.saude.abstinencia", estagioAbstinencia).getString();
                g.text(mc.font, rotulo, w / 2 - 91, y + tremerY - 11, COR_ABSTINENCIA, false);
            }
        }
    }

    /** A barra slim da casa: contorno escuro, fundo fraco e preenchimento forte. */
    private static void desenharBarra(GuiGraphicsExtractor g, int x, int y,
            int largura, int altura, int preenchido, int cor, int corFundo) {
        g.fill(x - 1, y - 1, x + largura + 1, y + altura + 1, COR_BORDA);
        g.fill(x, y, x + largura, y + altura, corFundo);
        g.fill(x, y, x + preenchido, y + altura, cor);
    }

    // ==================================================== LEITURAS (pra visao das viagens)

    /** Nome da viagem ativa ("" = nenhuma). O SaudeVisionClient le daqui. */
    public static String viagemAtiva() {
        return viagemAtiva;
    }

    public static int intensidadeViagem() {
        return intensidadeViagem;
    }

    /** A intensidade 0..1 da tinta do OVERDRIVE (pulso com a batida). */
    public static float pulsoOverdrive(long tempoMs) {
        double batida = Math.abs(Math.sin(tempoMs / 300.0)); // ~200 bpm
        return (float) batida;
    }

    /** Util: efeito ativo no jogador local (pra overlays que checam o HUD). */
    public static boolean temEfeito(LocalPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> efeito) {
        MobEffectInstance inst = player.getEffect(efeito);
        return inst != null;
    }

    /** Clamp util da casa. */
    static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }
}
