package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Reutiliza o modelo do próprio item, apoiado e sem bobbing de item no chão. */
public class BebidaDecorativaRenderer
        extends EntityRenderer<BebidaDecorativaEntity, BebidaDecorativaRenderer.State> {
    private final ItemModelResolver itens;

    public BebidaDecorativaRenderer(EntityRendererProvider.Context context) {
        super(context);
        itens = context.getItemModelResolver();
        shadowRadius = 0.12F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BebidaDecorativaEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.rotacao = entity.getYRot();
        itens.updateForNonLiving(state.item, entity.getBebida(), ItemDisplayContext.NONE, entity);
        state.baseY = state.item.isEmpty() ? 0.0 : -state.item.getModelBoundingBox().minY;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, -state.rotacao);
        // Modelos centrados em X/Z=8, com 16 unidades por bloco. A base
        // real já transformada apoia tanto as garrafas/pão/coco (Y=0)
        // quanto o cacho da parreira, cuja geometria começa em Y=2,672.
        pose.translate(0.0, state.baseY, 0.0);
        state.item.submit(pose, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float rotacao;
        public double baseY;
    }
}
