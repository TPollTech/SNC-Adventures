package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

/** Skin Steve e o próprio cigarro do catálogo, preso à transformação da cabeça. */
public class PeruRenderer extends HumanoidMobRenderer<PeruEntity, PeruRenderer.State, PeruRenderer.Modelo> {
    private final ItemModelResolver itens;
    public PeruRenderer(EntityRendererProvider.Context context) {
        super(context, new Modelo(context.bakeLayer(ModelLayers.PLAYER)), .45F);
        itens = context.getItemModelResolver();
        addLayer(new RenderLayer<State, Modelo>(this) {
            @Override public void submit(PoseStack pose, SubmitNodeCollector collector,
                    int light, State state, float yRot, float xRot) {
                pose.pushPose();
                getParentModel().head.translateAndRotate(pose);
                pose.translate(.065, -.1125, -.46);
                pose.rotateDegrees(Axis.XP, -90);
                pose.scale(.4F, .4F, .4F);
                state.cigarro.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                pose.popPose();
            }
        });
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(PeruEntity peru, State state, float partialTick) {
        super.extractRenderState(peru, state, partialTick);
        state.fumando = peru.fumandoTicks();
        itens.updateForNonLiving(state.cigarro, new ItemStack(IntoxicantesMod.CIGARRO_CAMEL),
                ItemDisplayContext.NONE, peru);
    }
    @Override public Identifier getTextureLocation(State state) {
        return Identifier.fromNamespaceAndPath("intoxicantes", "textures/entity/peru.png");
    }
    public static class State extends HumanoidRenderState {
        public int fumando;
        public final ItemStackRenderState cigarro = new ItemStackRenderState();
    }
    public static class Modelo extends HumanoidModel<State> {
        public Modelo(ModelPart root) { super(root); }
        @Override public void setupAnim(State s) {
            super.setupAnim(s);
            if (s.isPassenger) {
                rightArm.xRot = leftArm.xRot = -1.1F;
            } else {
                body.xRot = .07F;
                head.xRot -= .035F;
            }
            if (s.fumando > 0) head.zRot += (float) Math.sin(s.fumando * .2) * .014F;
        }
    }
}
