package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Dezoito expositores (v1.2.75): NOVE na FRENTE e NOVE ATRÁS da ilha de meio
 * bloco, apoiados proporcionais ao vão de cada lado. A etiqueta acompanha o
 * produto no corredor correspondente.
 */
public class PrateleiraMercadoRenderer
        implements BlockEntityRenderer<PrateleiraMercadoBlockEntity, PrateleiraMercadoRenderer.Estado> {
    private static final double PX = 1.0 / 16.0;
    /** Vão do expositor a partir da face aberta (a etiqueta fica na borda). */
    private static final float VAO = 3.8F;
    private final ItemModelResolver itens;
    private final Font fonte;

    public PrateleiraMercadoRenderer(BlockEntityRendererProvider.Context context) {
        itens = context.itemModelResolver();
        fonte = context.font();
    }

    @Override
    public Estado createRenderState() { return new Estado(); }

    @Override
    public void extractRenderState(PrateleiraMercadoBlockEntity prateleira, Estado state,
            float parcial, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(prateleira, state, crumbling);
        state.facing = prateleira.getBlockState().getValue(PrateleiraMercadoBlock.FACING);
        for (int slot = 0; slot < PrateleiraMercadoBlockEntity.TOTAL; slot++) {
            Expositor expositor = state.expositores[slot];
            expositor.item.clear();
            expositor.etiqueta = null;
            expositor.fundo = slot >= PrateleiraMercadoBlockEntity.SLOTS;
            ItemStack pilha = prateleira.getItem(slot);
            if (pilha.isEmpty()) continue;
            // NONE evita aplicar transforms de moldura/inventário ao objeto no móvel.
            itens.updateForTopItem(expositor.item, pilha.copyWithCount(1), ItemDisplayContext.NONE,
                    prateleira.getLevel(), null, 0);
            if (expositor.item.isEmpty()) continue;
            var volume = expositor.item.getModelBoundingBox();
            if (volume.hasNaN()) { expositor.item.clear(); continue; }
            // Um único fator preserva proporções; qualquer item cabe no seu vão.
            double escala = Math.min(1.0, Math.min(3.8 * PX / Math.max(volume.getXsize(), 1e-9),
                    Math.min(3.8 * PX / Math.max(volume.getYsize(), 1e-9),
                            7.0 * PX / Math.max(volume.getZsize(), 1e-9))));
            expositor.escala = (float) escala;
            int indice = expositor.fundo ? slot - PrateleiraMercadoBlockEntity.SLOTS : slot;
            expositor.centroX = (float) (escala * (volume.minX + volume.maxX) / 2);
            expositor.centroZ = (float) (escala * (volume.minZ + volume.maxZ) / 2);
            expositor.x = PrateleiraMercadoBlockEntity.COLUNAS[indice % 3] * PX
                    - escala * (volume.minX + volume.maxX) / 2;
            expositor.y = (PrateleiraMercadoBlockEntity.NIVEIS[indice / 3] + .01) * PX
                    - escala * volume.minY;
            // Frente encosta na face -Z local; fundo, na +Z (16 - 3.8).
            expositor.z = (expositor.fundo ? 16 - VAO : VAO) * PX
                    - escala * (volume.minZ + volume.maxZ) / 2;
            int dose = prateleira.doseDoSlot(slot);
            int quantidade = Math.min(dose, pilha.getCount());
            int preco = (int) Math.ceil(prateleira.precoDoSlot(slot) * (double) quantidade / dose);
            String etiqueta = "R$" + preco + " x" + quantidade;
            expositor.etiqueta = Component.literal(etiqueta).getVisualOrderText();
            expositor.larguraTexto = fonte.width(etiqueta);
        }
    }

    @Override
    public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        pose.pushPose();
        // Igual ao blockstate JSON: N0/E-90/S180/W90, girando pelo centro.
        pose.translate(.5, 0, .5);
        pose.rotateDegrees(Axis.YP, 180 - state.facing.toYRot());
        pose.translate(-.5, 0, -.5);
        for (int slot = 0; slot < state.expositores.length; slot++) {
            Expositor expositor = state.expositores[slot];
            if (expositor.item.isEmpty() && expositor.etiqueta == null) continue;
            int indice = expositor.fundo ? slot - PrateleiraMercadoBlockEntity.SLOTS : slot;
            if (!expositor.item.isEmpty()) {
                pose.pushPose();
                pose.translate(expositor.x, expositor.y, expositor.z);
                pose.scale(expositor.escala, expositor.escala, expositor.escala);
                if (expositor.fundo) {
                    // o produto do fundo encara o corredor de trás (giro no
                    // eixo do próprio item, sem sair do vão)
                    pose.translate(expositor.centroX, 0, expositor.centroZ);
                    pose.rotateDegrees(Axis.YP, 180);
                    pose.translate(-expositor.centroX, 0, -expositor.centroZ);
                }
                expositor.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            if (expositor.etiqueta != null) {
                pose.pushPose();
                pose.translate(PrateleiraMercadoBlockEntity.COLUNAS[indice % 3] * PX,
                        (PrateleiraMercadoBlockEntity.NIVEIS[indice / 3] - .22) * PX,
                        expositor.fundo ? 16.001 * PX : -.001);
                if (expositor.fundo) {
                    // A etiqueta do fundo olha o corredor oposto (mesma mecânica
                    // do verso das placas do vanilla: gira 180° no eixo Y).
                    pose.rotateDegrees(Axis.YP, 180);
                }
                float textoEscala = (float) Math.min(.065 * PX,
                        3.55 * PX / Math.max(1, expositor.larguraTexto));
                // Texto sobe com Y invertido, sem atravessar o móvel.
                pose.scale(-textoEscala, -textoEscala, textoEscala);
                collector.submitText(pose, -expositor.larguraTexto / 2F, 0,
                        expositor.etiqueta, false, Font.DisplayMode.NORMAL, state.lightCoords,
                        0xFF243B2C, 0, 0);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static class Expositor {
        final ItemStackRenderState item = new ItemStackRenderState();
        double x, y, z;
        float escala;
        float centroX, centroZ;
        boolean fundo;
        FormattedCharSequence etiqueta;
        int larguraTexto;
    }

    public static class Estado extends BlockEntityRenderState {
        private final Expositor[] expositores = new Expositor[PrateleiraMercadoBlockEntity.TOTAL];
        private Direction facing = Direction.NORTH;
        public Estado() {
            for (int i = 0; i < expositores.length; i++) expositores[i] = new Expositor();
        }
    }
}
