package com.intoxicantes.mixin;

import com.intoxicantes.ArmasClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.2.53 — CONTROLES DE FPS (padrão CoD/Battlefield) com as armas na mão:
 * - BOTÃO ESQUERDO = ATIRA (o vanilla trata clique esquerdo como quebrar/
 *   atacar — antes o tiro morava no direito, colidindo com o "usar");
 * - BOTÃO DIREITO = MIRAR (segurar = ADS/iron sight; soltar = quadril).
 *
 * O mixin intercepta onButton ANTES do jogo: com arma de fogo na mão, o
 * esquerdo vira o gatilho (manda atirar via ArmasClient) e o direito é
 * engolido (não coloca bloco / não usa item) e liga o ADS. Sem arma na mão
 * o vanilla segue intacto. A mira NÃO é mais o SHIFT (que encurvalava e
 * rouba o sneak — agora o shift volta a ser só agachar).
 *
 * v1.2.70 (HOTFIX): o 26.3 entrega o botão em códigos SDL pelo
 * InputConstants (esquerdo = 1, direito = 3) — o mixin comparava com
 * números GLFW (0/1), então o ESQUERDO caía na comparação do direito, era
 * engolido e a arma NUNCA atirava (e o direito passava reto e virava "usar"
 * vanilla). Comparação agora é pelas constantes do InputConstants (regra do
 * projeto: nada de número mágico de GLFW).
 *
 * v1.2.70 (ADS): como o evento do direito é engolido, o vanilla nunca roda
 * a linha que seta isRightPressed() — o ArmasClient.mirando() parou de ler
 * o mouseHandler e lê o ESTADO rastreado aqui (borda de descida/subida do
 * evento, set/clear pelo mesmo critério do vanilla: tela aberta limpa).
 */
@Mixin(MouseHandler.class)
public abstract class MouseBotaoMixin {

    /**
     * Ação do onButton: o 26.3 (SDL) mapeia 1 = PRESSIONAR e 0 = SOLTAR — o
     * vanilla usa exatamente este literal no onButton (não há constante
     * nomeada; InputConstants só nomeia o ÍNDICE do botão, não a ação).
     */
    private static final int ACAO_PRESSIONAR = 1;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void intoxicantes$armaNoGatilho(long janela, net.minecraft.client.input.MouseButtonInfo info,
            int acao, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || !ArmasClient.segurandoArma()) {
            // fora do jogo/sobre tela/sem arma: devolve o controle ao vanilla
            // e garante que o ADS não fique preso ligado
            ArmasClient.definirDireitoPressionado(false);
            return;
        }
        int botao = info.button();
        if (botao == InputConstants.MOUSE_BUTTON_LEFT) { // SDL: 1 (era 0 no GLFW)
            // pressionou (acao 1): puxa o gatilho; soltar (0) é inerte (a arma
            // é semi-auto — um clique, um tiro). Engolimos o evento inteiro:
            // nenhum bloco quebra, nenhuma entidade apanha de soco.
            if (acao == ACAO_PRESSIONAR) {
                ArmasClient.gatilhoPuxado();
            }
            ci.cancel();
            return;
        }
        if (botao == InputConstants.MOUSE_BUTTON_RIGHT) { // SDL: 3 (era 1 no GLFW)
            // EXCEÇÃO: a crosshair numa ENTIDADE (Gago, Traficante, Juça…) —
            // o direito passa intacto pra abrir o menu/interagir em vez de
            // mirar (mirar em NPC não faz sentido; perder o menu, sim).
            if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                ArmasClient.definirDireitoPressionado(false);
                return;
            }
            // o ADS é CONTÍNUO (segurar): como o evento é engolido, o vanilla
            // NÃO seta isRightPressed() — espelhamos o estado no ArmasClient e
            // o mirando() lê este espelho por frame. Engolir o evento impede o
            // "usar" vanilla (colocar bloco etc.).
            ArmasClient.definirDireitoPressionado(acao == ACAO_PRESSIONAR);
            ci.cancel();
        }
    }
}
