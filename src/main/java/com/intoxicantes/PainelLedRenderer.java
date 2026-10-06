package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * O RENDERER DO PAINEL DE LED CRAFTÁVEL (v1.2.36): mesma técnica do
 * letreiro da fachada — cada pixel da {@link LedFont} (5×7) vira um quad
 * no atlas LED com a cor gravada no block entity (a Central troca) e
 * brilho full-bright. 1..2 linhas, modo FIXO ou SCROLL (o texto anda).
 */
public class PainelLedRenderer
        implements BlockEntityRenderer<PainelLedBlockEntity, PainelLedRenderer.State> {

    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
            "intoxicantes", "textures/misc/led_atlas.png");
    private static final float U_ACESO = 0.5F;
    private static final float V_ACESO = 0.5F;
    private static final float UV_MEIO = 0.0625F;
    private static final int LUZ_LED = 15728880;
    private static final RenderType RENDER_TYPE = RenderTypes.textPolygonOffset(ATLAS);

    /** A TELA (v1.2.36, convenção da fornalha — prova com a matriz de
     * rotação): com rot = -toYRot o eixo +z local aponta PRO OBSERVADOR
     * (rua) e o plano da tela fica em z = -5px LOCAL (o corpo do painel
     * ocupa z13..16 do modelo = lado da parede). O texto nasce COLADO na
     * tela (1/128 à frente) e o verso fica no lado da parede (culled). */
    private static final float Z_FACE = -5.0F / 16.0F;
    private static final float Z_FLUTUA = 1.0F / 128.0F;

    public PainelLedRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PainelLedBlockEntity painel, State state,
            float parcial, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(painel, state, crumbling);
        state.linhas.clear();
        state.linhas.addAll(painel.getLinhas());
        state.cor = painel.getCor();
        state.brilho = painel.getBrilho();
        state.modo = painel.getModo();
        state.ledLigado = painel.getBlockState().hasProperty(PainelLedBlock.LIT)
                ? painel.getBlockState().getValue(PainelLedBlock.LIT)
                : true;
        state.facing = painel.getBlockState().hasProperty(PainelLedBlock.FACING)
                ? painel.getBlockState().getValue(PainelLedBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        // v1.2.40: extensão da linha (não desenha) + nº de telas da TV
        // (o texto estica pela linha inteira)
        state.extensao = painel.isExtensao();
        state.telas = painel.getBlockState().hasProperty(PainelLedBlock.TELAS)
                ? painel.getBlockState().getValue(PainelLedBlock.TELAS)
                : 1;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.linhas.isEmpty() || !state.ledLigado) return;

        // v1.2.40: bloco de EXTENSÃO da linha NÃO desenha texto — quem
        // desenha é o CABEÇA (a ponta sem irmão). O template 1.2.36–39
        // gravava NBT em todos → cada tela desenhava o texto inteiro dele,
        // 5 TVs gritando juntas (as letras sobrepostas do print).
        if (state.extensao) return;

        List<String> conteudo = state.linhas.stream()
                .filter(l -> l != null && !l.isBlank()).toList();
        if (conteudo.isEmpty()) return;

        // v1.2.40: a TV é a LINHA INTEIRA (1..3 telas de 16px) — o texto
        // estica por todas, como o letreiro atravessa a fachada. O valor
        // antigo (13px fixo) era a face de UM bloco só.
        final int telas = Math.max(1, state.telas);
        final float painelLarguraPx = telas * 16F - 3F;
        final float painelAlturaPx = 14F;
        int maxW = Math.max(1, LedFont.larguraMax(conteudo));
        int nLin = conteudo.size();
        int alturaFonte = nLin * LedFont.ALTURA_GLYPH + (nLin - 1) * LedFont.LINHA_VAZIO;
        float escala = Math.min(painelLarguraPx / maxW, painelAlturaPx / (float) alturaFonte);

        // pulso de alimentação (mesma vida do letreiro)
        float pulso = 0.9F + 0.1F * Mth.sin((Util.getMillis() % 4000L) / 4000F * 2F * Mth.PI);
        float fatorBrilho = 0.25F + 0.75F * state.brilho / 15.0F;
        int corLed = argb(pulso * fatorBrilho, state.cor);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // CONVENÇÃO DA FORNALHA (igual ao letreiro da fachada, prova vanilla):
        // facing=north → 0°, south → 180°... = -toYRot. Com isso o +z local
        // aponta PRO OBSERVADOR e a tela (plano +z13 do modelo) encara a rua
        // com o texto do jeito certo — nunca "atrás da caixa".
        pose.rotateDegrees(com.mojang.math.Axis.YP,
                -state.facing.toYRot());

        final float px = 1.0F / 16.0F;
        final float esc = escala;
        final List<String> linhas = conteudo;
        final int cor = corLed;
        // offset de scroll (modo ANDANDO): o texto entra pela direita e sai
        final float larguraTextoPx = maxW * esc;
        final boolean rolando = state.modo == PainelLedBlockEntity.MODO_SCROLL
                && larguraTextoPx > painelLarguraPx - 2F;
        final float periodo = larguraTextoPx + painelLarguraPx;
        final float deslocamento = rolando
                ? (Util.getMillis() / 40L) % (long) Math.max(1, (int) periodo)
                : 0F;

        collector.submitCustomGeometry(pose, RENDER_TYPE, (poseAtual, vc) -> {
            float yTopo = painelAlturaPx / 2F;
            for (int i = 0; i < linhas.size(); i++) {
                String linha = linhas.get(i);
                float largLinha = LedFont.largura(linha) * esc;
                float x0 = rolando ? painelLarguraPx - deslocamento
                        : -largLinha / 2F;
                for (int ci = 0; ci < linha.length(); ci++) {
                    byte[] glifo = LedFont.glifo(linha.charAt(ci));
                    float gx = x0 + ci * LedFont.GLIFO_ESPACO * esc;
                    for (int row = 0; row < LedFont.ALTURA_GLYPH; row++) {
                        int bits = glifo[row];
                        if (bits == 0) continue;
                        for (int col = 0; col < LedFont.LARGURA_GLYPH; col++) {
                            if ((bits & (1 << (LedFont.LARGURA_GLYPH - 1 - col))) == 0) continue;
                            float x = gx + col * esc;
                            float y1 = yTopo - (i * (LedFont.ALTURA_GLYPH + LedFont.LINHA_VAZIO)
                                    + row) * esc;
                            float y0 = y1 - esc;
                            pixel(vc, poseAtual, x, y0, y1, cor);
                        }
                    }
                }
            }
        });
        pose.popPose();
    }

    /** Pixel de LED: quad DUPLA-FACE (winding certo dos dois lados, igual
     * letreiro — à prova de cull e de câmera dentro do painel). */
    private static void pixel(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y0, float y1, int cor) {
        float x1 = x + 1F;
        quad(vc, pose, x, y0, x1, y1, Z_FACE + Z_FLUTUA, cor, false);
        quad(vc, pose, x, y0, x1, y1, Z_FACE + Z_FLUTUA, cor, true);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float y0, float x1, float y1, float z, int cor, boolean verso) {
        float zA = verso ? -z : z;
        if (verso) {
            vc.addVertex(pose, x1, y0, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y0, zA).setColor(cor)
                    .setUv(U_ACESO + UV_MEIO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y1, zA).setColor(cor)
                    .setUv(U_ACESO + UV_MEIO, V_ACESO + UV_MEIO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y1, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO + UV_MEIO).setLight(LUZ_LED);
        } else {
            vc.addVertex(pose, x0, y0, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y0, zA).setColor(cor)
                    .setUv(U_ACESO + UV_MEIO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y1, zA).setColor(cor)
                    .setUv(U_ACESO + UV_MEIO, V_ACESO + UV_MEIO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y1, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO + UV_MEIO).setLight(LUZ_LED);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0F, 1F) * 255F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Estado de render do painel. */
    public static class State extends BlockEntityRenderState {
        public final List<String> linhas = new ArrayList<>();
        public int cor = PlacaEsquinaoBlockEntity.COR_LED;
        public int brilho = 15;
        public int modo = PainelLedBlockEntity.MODO_FIXO;
        public boolean ledLigado = true;
        public net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        /** v1.2.40: extensão da linha (muda — o cabeça desenha). */
        public boolean extensao;
        /** v1.2.40: nº de telas da TV (o texto estica pela linha). */
        public int telas = 1;
    }
}
