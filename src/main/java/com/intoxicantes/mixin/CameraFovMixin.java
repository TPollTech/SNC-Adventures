package com.intoxicantes.mixin;

import com.intoxicantes.ArmasClient;
import com.intoxicantes.ModConfig;

import net.minecraft.client.Camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ADS das armas de fogo (v1.2.32 a 12, v1.2.33 o .38): o FOV da camera no 26.3
 * e' calculado no Camera.calculateFov (o getFov do GameRenderer morreu nessa
 * versao). Intercepto o resultado e aplico o multiplicador de zoom do ADS com o
 * nivel suavizado do client (0 = fora do ADS .. 1 = ADS total) — o zoom
 * desliza, nao "teleporta". Cada arma tem o proprio zoom (o .38 mira menos
 * fechado que a 12: e' revólver de cintura, nao luneta).
 */
@Mixin(Camera.class)
public abstract class CameraFovMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void intoxicantes$adsZoom(float parcial, CallbackInfoReturnable<Float> cir) {
        float nivel = ArmasClient.fatorZoom();
        if (nivel > 0.005F) {
            float fator = 1.0F - (1.0F - ArmasClient.fovAds()) * nivel;
            cir.setReturnValue(cir.getReturnValueF() * fator);
        }
    }
}
