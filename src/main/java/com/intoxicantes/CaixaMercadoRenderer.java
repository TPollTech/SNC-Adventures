package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Visor de caixa: mostra somente o carrinho do jogador local, enviado pelo servidor. */
public final class CaixaMercadoRenderer implements BlockEntityRenderer<CaixaMercadoBlockEntity, CaixaMercadoRenderer.Estado> {
    private static Object ultimoNivel;
    private static BlockPos caixaAtual;
    private static int totalAtual;
    private final Font fonte;

    public CaixaMercadoRenderer(BlockEntityRendererProvider.Context context) { fonte = context.font(); }

    public static void atualizarResumo(PrateleiraNetworking.CarrinhoResumoPayload resumo) {
        ultimoNivel = Minecraft.getInstance().level;
        caixaAtual = resumo.caixa().immutable();
        totalAtual = resumo.total();
    }

    /** Gametest (CarrinhoClientTest): o texto que o visor pintaria agora. */
    public static String textoDoVisorDeTeste() {
        int total = Minecraft.getInstance().level == ultimoNivel ? totalAtual : 0;
        return "R$ " + total;
    }

    @Override public Estado createRenderState() { return new Estado(); }
    @Override public void extractRenderState(CaixaMercadoBlockEntity be, Estado state, float parcial,
            net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.facing = be.getBlockState().getValue(CaixaMercadoBlock.FACING);
        int total = be.getLevel() == ultimoNivel && be.getBlockPos().below().equals(caixaAtual) ? totalAtual : 0;
        String texto = "R$ " + total;
        state.texto = Component.literal(texto).getVisualOrderText();
        state.largura = fonte.width(texto);
    }
    @Override public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.rotateDegrees(Axis.YP, 180-state.facing.toYRot());
        pose.translate(-.5, 0, -.5);
        // Visor elevado do cliente (-Z), alinhado ao VISOR da fonte
        // tools/gen_caixa_mercado.py. Texto é camada dinâmica, nunca atlas.
        // Centro y8.38; fonte de 9px com altura máxima 1.08 unidades.
        // Plano 0.02 unidades à frente do vidro (z4.255), sem z-fighting.
        float escala = (float)Math.min(.12/16.0, 5.9/16.0/Math.max(1,state.largura));
        pose.translate(10.5/16.0, 8.38/16.0 + 4.5*escala, 4.235/16.0);
        pose.scale(-escala, -escala, escala);
        collector.submitText(pose, -state.largura/2F, 0, state.texto, false,
                Font.DisplayMode.NORMAL, 0xF000F0, 0xFFD6EBC4, 0, 0);
        pose.popPose();
    }
    public static final class Estado extends BlockEntityRenderState {
        Direction facing;
        FormattedCharSequence texto;
        int largura;
    }
}
