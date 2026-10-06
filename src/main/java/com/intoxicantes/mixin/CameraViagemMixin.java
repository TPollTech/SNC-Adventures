package com.intoxicantes.mixin;

import com.intoxicantes.SaudeClient;
import com.intoxicantes.SaudeVisionClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.2.54 — O FOV DAS VIAGENS: o OVERDRIVE pulsa com a batida do coração
 * (mundo aperta/solta ~200 bpm no pico), o VIAGEM respira lento, o SONHO
 * fecha a pálpebra 1,5%. Soma ao zoom do ADS das armas (a prioridade é do
 * ADS: o fator maior vence — não multiplicam juntos pra não enjooar).
 */
@Mixin(Camera.class)
public abstract class CameraViagemMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);

    /** Agachar + correr (Shift + Ctrl por padrão) abaixa a vista para as etiquetas. */
    @Inject(method = "update", at = @At("RETURN"))
    private void intoxicantes$espiarPrateleiras(DeltaTracker delta, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.gui.screen() == null
                && client.options.getCameraType().isFirstPerson()
                && client.options.keyShift.isDown() && client.options.keySprint.isDown()) {
            setPosition(((Camera) (Object) this).position().add(0, -.32, 0));
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void intoxicantes$fovViagem(float parcial, CallbackInfoReturnable<Float> cir) {
        float fatorViagem = SaudeVisionClient.fovViagem(parcial);
        if (fatorViagem != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * fatorViagem);
        }
    }
}
