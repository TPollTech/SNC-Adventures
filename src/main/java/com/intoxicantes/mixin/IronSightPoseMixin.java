package com.intoxicantes.mixin;

import com.intoxicantes.ArmasClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.2.53 — A POSE DO IRON SIGHT: quando o freguês MIRA (botão direito), a
 * arma de fogo sobe até os olhos e centraliza — a massa de mira do modelo
 * alinha com o centro da tela, de verdade (CoD/BF). O quão perto dos olhos é
 * o fatorZoom() suavizado (0 = quadril, 1 = mira total), então a arma "sobe"
 * deslizando, sem teleportar.
 *
 * Intercepto o envio das mãos/itens de primeira pessoa e empurro um
 * transform extra antes do render (a arma sobe, avança e centraliza; os
 * desvios de animação de recarga/som continuam por cima do novo pose —
 * seguramos o pose stack, o resto do render é indiferente ao offset).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class IronSightPoseMixin {

    @Unique
    private static final float ADS_SUBIDA = 1.35F;   // sobe até a linha dos olhos
    @Unique
    private static final float ADS_ADUCAO = -1.15F;  // aduz pro centro (esq->dir, dir->esq)
    @Unique
    private static final float ADS_AVANCO = -1.4F;   // aproxima da face (Z negativo = pra frente)

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void intoxicantes$poseDeMira(float parcial, PoseStack pose, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState estado, CallbackInfo ci) {
        float nivel = ArmasClient.fatorZoom();
        if (nivel < 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean destro = player.avatarRenderState.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
        float lado = destro ? 1.0F : -1.0F;
        pose.pushPose();
        pose.translate(
                -lado * ADS_ADUCAO * nivel / 10.0,
                ADS_SUBIDA * nivel / 10.0,
                ADS_AVANCO * nivel / 10.0);
        adsEmpilhado = true; // o pop acontece no RETURN abaixo (render é single-thread)
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"))
    private void intoxicantes$fechaPoseDeMira(float parcial, PoseStack pose, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState estado, CallbackInfo ci) {
        if (adsEmpilhado) {
            adsEmpilhado = false;
            pose.popPose();
        }
    }

    @Unique
    private boolean adsEmpilhado;
}
