package com.intoxicantes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Parte client das ARMAS em nível gun mod (v1.2.32 a 12, v1.2.33 o .38):
 *
 * - KICK DE CÂMERA: recebe o RecuoPayload do servidor e chuta pitch+yaw na
 *   hora; 55% do chute volta suavemente nos ticks seguintes (constituição de
 *   atirador: a mira re-assenta sozinha no alvo, não fica torta pra cima)
 * - ADS: segurar o BOTÃO DIREITO com uma das armas na mão liga o zoom (v1.2.53:
 *   padrão CoD/BF — esquerdo atira, direito mira; o estado segue pro servidor
 *   via MiraPayload na borda) — o multiplicador
 *   de FOV entra pelo mixin no Camera.calculateFov (o getFov do GameRenderer
 *   morreu no 26.3); a dispersão menor e o alcance maior acontecem no servidor
 * - HUD (v1.2.48): painel de munição estilo CoD/Battlefield no CANTO INFERIOR
 *   DIREITO — nome da arma, pips do mecanismo, número GRANDE do que está
 *   carregado, reserva do inventário e status (recarregando/pump/fecho/[R]).
 *   Nada mais no meio da tela além da retícula do ADS
 */
public final class ArmasClient {

    /** Chute que chegou do servidor, a aplicar no próximo tick. */
    private static float kickPitchPendente, kickYawPendente;
    /** Parte do chute que ainda falta devolver (retorno suave). */
    private static float retornoPitch, retornoYaw;
    /** 0 = fora do ADS .. 1 = ADS total (suavizado por frame pro zoom não "teleportar"). */
    private static float nivelAds;

    /** v1.2.41: estado do keybind R (pra mandar press/soltar pro servidor). */
    private static boolean teclaRAtiva;
    /** v1.2.41: o keybind R (categoria GAMEPLAY, padrão R). */
    private static net.minecraft.client.KeyMapping teclaRecarregar;
    /** v1.2.41: status S2C da recarga por tecla (pra cor do HUD). */
    private static boolean recargaViaTecla;
    /** v1.2.41: nome de lang da arma recarregando via tecla ("escopeta"/"revolver"). */
    private static String recargaNomeArma = "escopeta";
    /** v1.2.53: último estado de mira enviado ao servidor (borda, não por tick). */
    private static boolean miraSincronizada;

    /**
     * v1.2.70: espelho do BOTÃO DIREITO com arma na mão. O MouseBotaoMixin
     * engole o evento do direito (pra não "usar" item) e o vanilla NUNCA seta
     * isRightPressed() — o mixin grava a borda aqui e o mirando() lê.
     */
    private static boolean direitoPressionado;

    // cores do painel de munição (paleta CoD/BF: âmbar sobre fundo escuro)
    private static final int COR_NOME = 0xFFB8B0A0;
    private static final int COR_PIP_CHEIO = 0xFFE8B23A;
    private static final int COR_PIP_VAGO = 0x90302C24;
    private static final int COR_PIP_CHEGANDO = 0xFF5A5248;
    private static final int COR_NUMERO = 0xFFF0E8D8;
    private static final int COR_RESERVA = 0xFF9A9284;
    private static final int COR_RECARREGANDO = 0xFFE0B060;
    private static final int COR_TECLA = 0xFF9AD89A;
    private static final int COR_SEM_MUNICAO = 0xFFE06050;
    private static final int COR_FONTE = 0xFF9A9484;
    private static final int COR_FUNDO = 0x5A10100E;

    private ArmasClient() {}

    public static void init() {
        // S2C: o recuo do tiro (payload compartilhado das duas armas)
        ClientPlayNetworking.registerGlobalReceiver(RecuoPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    kickPitchPendente += payload.pitch();
                    kickYawPendente += payload.yaw();
                }));

        ClientTickEvents.END_CLIENT_TICK.register(ArmasClient::fimDoTick);

        // v1.2.41: a TECLA R — recarrega a 12 ou o .38 sem travar a pose.
        // O client só AVISA (payload C2S); o servidor decide tudo.
        teclaRecarregar = KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping(
                "key.intoxicantes.recarregar",
                InputConstants.KEY_R,
                net.minecraft.client.KeyMapping.Category.GAMEPLAY));

        // S2C/C2S: status da recarga por tecla (pra cor do HUD) e o aviso C2S
        ClientPlayNetworking.registerGlobalReceiver(RecargaPayload.TipoStatus.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    recargaViaTecla = payload.ativa();
                    recargaNomeArma = payload.arma();
                }));

        // o painel de munição, por cima do HUD vanilla
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "armas_hud"),
                ArmasClient::desenharHud);
    }

    // ==================================================== KICK DE CÂMERA

    private static void fimDoTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            retornoPitch = retornoYaw = kickPitchPendente = kickYawPendente = 0;
            teclaRAtiva = false;
            recargaViaTecla = false;
            direitoPressionado = false;
            return;
        }
        // v1.2.70: tela aberta (menu, chat, inventário) solta o ADS — o
        // evento de soltar pode ter sido consumido pela tela e o espelho
        // não pode prender a mira ligada.
        if (mc.gui.screen() != null) {
            direitoPressionado = false;
        }
        // v1.2.41: a TECLA R — borda de subida/descida manda pro servidor.
        // v1.2.48: o servidor ignora o soltar (R de 1 aperto), o aviso segue
        // indo pra manter o contrato do payload (e server antigo ainda funciona).
        boolean down = teclaRecarregar.isDown();
        if (down && !teclaRAtiva) {
            ClientPlayNetworking.send(new RecargaPayload(true));
        } else if (!down && teclaRAtiva) {
            ClientPlayNetworking.send(new RecargaPayload(false));
        }
        teclaRAtiva = down;
        // v1.2.53: mira (ADS) sincronizada na BORDA do estado — o servidor lê
        // MiraPayload.estaMirando() na hora do disparo (dispersão/alcance).
        boolean miraAgora = mirando();
        if (miraAgora != miraSincronizada) {
            ClientPlayNetworking.send(new MiraPayload(miraAgora));
            miraSincronizada = miraAgora;
        }
        // aplica o chute bruto (45% na hora, 55% vira retorno suave)
        if (kickPitchPendente != 0 || kickYawPendente != 0) {
            player.turn(kickYawPendente, kickPitchPendente);
            retornoPitch += kickPitchPendente * 0.55F;
            retornoYaw += kickYawPendente * 0.55F;
            kickPitchPendente = 0;
            kickYawPendente = 0;
        }
        // devolve suavemente (exponencial — mira re-assenta em ~12 ticks)
        if (retornoPitch != 0 || retornoYaw != 0) {
            float devolvePitch = retornoPitch * 0.12F;
            float devolveYaw = retornoYaw * 0.12F;
            player.turn(-devolveYaw, -devolvePitch);
            retornoPitch -= devolvePitch;
            retornoYaw -= devolveYaw;
            if (Math.abs(retornoPitch) < 0.005F) retornoPitch = 0;
            if (Math.abs(retornoYaw) < 0.005F) retornoYaw = 0;
        }
    }

    // ==================================================== ADS (o mixin pergunta aqui)

    /** Segura alguma das armas de fogo? (escopeta ou revólver) */
    public static boolean segurandoArma() {
        return armaNaMao() != null;
    }

    /** A stack da arma de fogo na mão (qualquer das duas), ou null. */
    private static ItemStack armaNaMao() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack na = player.getItemInHand(hand);
            if (na.is(IntoxicantesMod.ESCOPETA) || na.is(IntoxicantesMod.REVOLVER)) {
                return na;
            }
        }
        return null;
    }

    /**
     * Mira ativada: arma de fogo na mão + BOTÃO DIREITO segurado (v1.2.53: padrão CoD/BF — o SHIFT voltou a ser só agachar).
     * v1.2.70: com arma na mão o evento do direito é engolido pelo
     * MouseBotaoMixin e o vanilla NUNCA seta isRightPressed() — o ADS lê o
     * espelho rastreado pelo mixin (códigos SDL do 26.3, esquerdo = 1).
     */
    public static boolean mirando() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        return segurandoArma() && direitoPressionado;
    }

    /** v1.2.70: o MouseBotaoMixin grava a borda do botão direito engolido. */
    public static void definirDireitoPressionado(boolean pressionado) {
        direitoPressionado = pressionado;
    }

    /**
     * v1.2.53 — GATILHO: chamado pelo MouseBotaoMixin quando o BOTÃO ESQUERDO
     * desce com arma de fogo na mão (padrão CoD/BF: esquerdo atira, direito
     * mira).
     * v1.2.57 — agora manda o GatilhoPayload C2S: o item.use() do botão
     * direito ficou INERTE (PASS), então o disparo viaja pelo canal próprio e
     * o servidor executa o caminho de tiro real. 1 clique = 1 tentativa.
     */
    public static void gatilhoPuxado() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen() != null) {
            return;
        }
        ItemStack principal = player.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionHand mao = principal.is(IntoxicantesMod.ESCOPETA)
                || principal.is(IntoxicantesMod.REVOLVER)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ClientPlayNetworking.send(new GatilhoPayload());
        player.swing(mao, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
    }

    /** 0..1 com transição suave; chamado por frame pelo mixin de FOV. */
    public static float fatorZoom() {
        float alvo = mirando() ? 1.0F : 0.0F;
        nivelAds += (alvo - nivelAds) * 0.3F;
        if (Math.abs(alvo - nivelAds) < 0.002F) {
            nivelAds = alvo;
        }
        return nivelAds;
    }

    /** Multiplicador de FOV do ADS da arma na mão (a 12 fecha mais que o .38). */
    public static float fovAds() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && player.getMainHandItem().is(IntoxicantesMod.REVOLVER)) {
            return ModConfig.get().revolverAdsFov;
        }
        return ModConfig.get().escopetaAdsFov;
    }

    // ==================================================== HUD DA MUNIÇÃO (CoD/BF)

    private static void desenharHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.keyToggleGui.isDown()) {
            return;
        }
        ItemStack stack = armaNaMao();
        if (stack == null) {
            return;
        }

        desenharPainelMunicao(g, mc, stack);

        // ADS: retícula tática verde (a dispersão cai — o jogador merece ver)
        if (ArmasClient.fatorZoom() > 0.5F) {
            int centro = g.guiWidth() / 2;
            int cy = g.guiHeight() / 2;
            int r = 14;
            int verde = 0xD070C070;
            g.fill(centro - r, cy, centro - r + 6, cy + 1, verde);
            g.fill(centro + r - 6, cy, centro + r, cy + 1, verde);
            g.fill(centro, cy - r, centro + 1, cy - r + 6, verde);
            g.fill(centro, cy + r - 6, centro + 1, cy + r, verde);
            g.fill(centro, cy, centro + 1, cy + 1, 0xF0A0E0A0);
        }
    }

    /** Um carregado/vago do painel. */
    private record Pip(int x, int largura, int altura) {}

    /**
     * v1.2.48 — Painel estilo CoD/Battlefield no canto inferior direito:
     * nome da arma / pips do mecanismo / NÚMERO GRANDE carregado + reserva /
     * status. Só desaparece quando a arma sai da mão.
     */
    private static void desenharPainelMunicao(GuiGraphicsExtractor g, Minecraft mc,
            ItemStack stack) {
        boolean escopeta = stack.is(IntoxicantesMod.ESCOPETA);
        int carregadas;
        int capacidade;
        int reserva;
        String fase;
        String arma;

        if (escopeta) {
            EscopetaEstado estado = EscopetaItem.estado(stack);
            carregadas = estado.noTubo() + (estado.camara() ? 1 : 0);
            capacidade = ModConfig.get().escopetaCapacidadeTubo + 1;
            reserva = EscopetaItem.contarCartuchos(mc.player);
            arma = "escopeta";
            fase = switch (estado.fase()) {
                case EscopetaEstado.FASE_RECARREGANDO, EscopetaEstado.FASE_TECLA -> "recarregando";
                case EscopetaEstado.FASE_BOMBA -> "pump";
                default -> "pronta";
            };
        } else {
            RevolverEstado estado = RevolverItem.estado(stack);
            carregadas = estado.balas();
            capacidade = RevolverEstado.CAPACIDADE;
            reserva = RevolverItem.contarCartucho38(mc.player);
            arma = "revolver";
            fase = switch (estado.fase()) {
                case RevolverEstado.FASE_RECARREGANDO, RevolverEstado.FASE_TECLA -> "recarregando";
                case RevolverEstado.FASE_FERRAMENTA -> "fecho";
                default -> "pronta";
            };
        }

        int larguraTela = g.guiWidth();
        int alturaTela = g.guiHeight();
        int xDir = larguraTela - 10; // margem direita
        int y0 = alturaTela - 50;    // bloco do painel (acima da hotbar)

        // ---------- linha 1: nome da arma (o nome localizado do item)
        Component nome = stack.getHoverName();
        g.pose().pushMatrix();
        g.pose().scale(0.85F, 0.85F);
        g.text(mc.font, nome,
                (int) ((xDir - mc.font.width(nome)) / 0.85F), (int) (y0 / 0.85F),
                COR_NOME, true);
        g.pose().popMatrix();

        // ---------- linha 2: pips do mecanismo (câmara destacada na 12)
        int pipL = 5, pipH = 6, gap = 2;
        int totalPips = escopeta ? capacidade : RevolverEstado.CAPACIDADE;
        int larguraPips = totalPips * (pipL + gap) - gap;
        int xPip = xDir - larguraPips;
        int yPip = y0 + 11;
        // fundo sutil atrás do bloco todo (legibilidade sobre qualquer cena)
        int larguraBloco = Math.max(larguraPips, mc.font.width(nome)) + 8;
        g.fill(xDir - larguraBloco + 4, y0 - 2, xDir + 4, alturaTela - 8, COR_FUNDO);

        for (int i = 0; i < totalPips; i++) {
            int cor;
            boolean cheio;
            boolean chegando = "recarregando".equals(fase);
            if (escopeta) {
                EscopetaEstado estado = EscopetaItem.estado(stack);
                cheio = i == 0 ? estado.camara() : i - 1 < estado.noTubo();
            } else {
                cheio = RevolverItem.estado(stack).cheio(i);
            }
            if (cheio) {
                cor = COR_PIP_CHEIO;
            } else if (chegando) {
                cor = COR_PIP_CHEGANDO;
            } else {
                cor = COR_PIP_VAGO;
            }
            int altura = (escopeta && i == 0) ? pipH + 2 : pipH; // câmara mais alta
            g.fill(xPip, yPip + (pipH - altura), xPip + pipL, yPip + pipH + (pipH - altura), cor);
            xPip += pipL + gap;
        }

        // ---------- linha 3: número GRANDE carregado + / reserva
        String grande = String.valueOf(carregadas);
        String resto = " / " + (reserva < 0 ? "\u221E" : String.valueOf(reserva));
        int larguraGrande = Math.round(mc.font.width(grande) * 1.45F);
        int larguraBarra = mc.font.width(resto);
        int xNum = xDir - larguraGrande - larguraBarra;
        int yNum = y0 + 21;

        g.pose().pushMatrix();
        g.pose().scale(1.45F, 1.45F);
        g.text(mc.font, Component.literal(grande),
                (int) (xNum / 1.45F), (int) (yNum / 1.45F), COR_NUMERO, true);
        g.pose().popMatrix();
        g.text(mc.font, Component.literal(resto), xNum + larguraGrande,
                yNum + 6, COR_RESERVA, true);

        // ---------- linha 4: status (recarregando / pump / fecho / [R] / seco)
        Component status = null;
        int corStatus = COR_FONTE;
        boolean teclaDestaArma = recargaViaTecla && recargaNomeArma.equals(arma);
        if ("recarregando".equals(fase)) {
            status = Component.translatable("item.intoxicantes." + arma + ".hudRecarga");
            corStatus = teclaDestaArma ? COR_TECLA : COR_RECARREGANDO;
        } else if ("pump".equals(fase)) {
            status = Component.translatable("item.intoxicantes.escopeta.hudPump");
        } else if ("fecho".equals(fase)) {
            status = Component.translatable("item.intoxicantes.revolver.hudFecho");
        } else if (carregadas < capacidade) {
            boolean temReserva = reserva != 0;
            if (temReserva) {
                status = Component.translatable("item.intoxicantes.hudTeclaR");
                corStatus = COR_TECLA;
            } else if (carregadas == 0) {
                status = Component.translatable("item.intoxicantes." + arma + ".semmunicao");
                corStatus = COR_SEM_MUNICAO;
            }
        }
        if (status != null) {
            g.text(mc.font, status, xDir - mc.font.width(status), y0 + 37, corStatus, true);
        }
    }
}
