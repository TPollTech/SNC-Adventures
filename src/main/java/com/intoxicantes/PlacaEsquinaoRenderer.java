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
 * O LETREIRO DE LED DO ESQUINÃO (v1.2.23; DISPLAY DE FACHADA na v1.2.31) —
 * desenhado PIXEL A PIXEL por código: cada pixel da {@link LedFont} (bitmap
 * 5×7 próprio — NADA de fonte do Minecraft) vira um quad no atlas LED com
 * cor verde-esmeralda full-bright.
 *
 * v1.2.31 — O DISPLAY DE FACHADA (estilo Satisfactory): o letreiro é a
 * FAIXA LARGA montada na fachada (15 blocos no mercado), montada na parede
 * ACIMA da porta — nada de torres/pólos. O texto ESTICA até preencher a
 * largura da faixa (escala calculada pra preencher, não só pra caber):
 * "MERCADO ESQUINÃO" numa linha só atravessa o prédio; o status
 * ABERTO/FECHADO entra sozinho no ciclo, sempre grande.
 *
 * Efeitos vivos mantidos: brilho pulsando (alimentação), flicker de neon,
 * placa apaga o LED de dia (LIT do bloco), zumbido de proximidade.
 * v1.2.51: o status ABERTO/FECHADO saiu daqui (o mercado é 24h — NUNCA
 * mostra "FECHADO"); o anúncio de status agora é o MINI DISPLAY de LED
 * ao lado da porta ("ABERTO · 24H", ver _mini_display_nbt).
 *
 * Pipeline 26.3: submitCustomGeometry com RenderTypes.textPolygonOffset
 * (atlas de quadrados brancos do mod dá a cor; o vertex color pinta cada
 * pixel; os quads nascem 1/128 à frente da face — sem z-fighting).
 */
public class PlacaEsquinaoRenderer
        implements BlockEntityRenderer<PlacaEsquinaoBlockEntity, PlacaEsquinaoRenderer.State> {

    /** O atlas de quadrados brancos (1 cor por região — o vertex color pinta). */
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
            "intoxicantes", "textures/misc/led_atlas.png");
    /** Atlas 32×32: pixel aceso = quadrado 2×2 no quadrante inferior direito. */
    private static final float U_ACESO = 0.5F;
    private static final float V_ACESO = 0.5F;
    private static final float UV_ACESO_MEIO = 0.0625F;  // 2/32 do atlas

    /** Luz full-bright (o LED brilha no escuro; mesmo valor do vanilla). */
    private static final int LUZ_LED = 15728880;

    /**
     * Conversão px de fonte → px de PAINEL, na escala 1:1 (letra de 7px de
     * fonte = 7px de painel); o desenho converte pra blocos no scale(1/16).
     */
    private static final float PX_PAINEL = 1.0F;

    /**
     * Geometria da FAIXA DE LED (v1.2.31), em px do PAINEL (16px = 1 bloco):
     * o display ocupa a LARGURA INTEIRA da faixa (largura em blocos vem do
     * block entity — 15 na fachada do mercado) e 16px de altura (o bloco é
     * cheio). A escala preenche: min(largura/texto, altura/texto).
     */
    /** Z da face do texto, relativo ao centro do bloco (bloco CHEIO → face
     * a 8/16 do centro). */
    private static final float Z_FACE = 8.0F / 16.0F;
    /** Empurrãozinho anti z-fighting (1/128 de bloco à frente da face). */
    private static final float Z_FLUTUA = 1.0F / 128.0F;

    private static final RenderType RENDER_TYPE = RenderTypes.textPolygonOffset(ATLAS);

    /** Anti-repetição do zumbido: 1 hum a cada ~11s por letreiro. */
    private long ultimoZumbidoSeg = -1;

    public PlacaEsquinaoRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlacaEsquinaoBlockEntity placa, State state,
            float parcial, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(placa, state, crumbling);
        state.linhas.clear();
        state.linhas.addAll(placa.getLinhas());
        state.acesa = placa.isLinkMercado();
        // v1.2.19 → v1.2.51: o estado ABERTO/FECHADO mora no MINI DISPLAY
        // (painel de LED ao lado da porta). O letreiro é o NOME, sempre.
        state.ledLigado = placa.getBlockState().hasProperty(PlacaEsquinaoBlock.LIT)
                ? placa.getBlockState().getValue(PlacaEsquinaoBlock.LIT)
                : true;
        state.facing = placa.getBlockState().hasProperty(PlacaEsquinaoBlock.FACING)
                ? placa.getBlockState().getValue(PlacaEsquinaoBlock.FACING)
                : net.minecraft.core.Direction.SOUTH;
        // v1.2.31: a largura da faixa (o texto estica dentro dela)
        state.larguraBlocos = Math.max(1, placa.getLargura());

        // v1.2.40: bloco de EXTENSÃO da faixa velha (torre de save antigo,
        // com block entity mas sem vínculo) não desenha — o PAINEL da faixa
        // desenha por todos. Na anatomia nova a extensão nem tem BE; isto
        // cobre a janela de migração (sem "fileira de televisões").
        if (!placa.isNova() && !placa.isLinkMercado()) return;

        // v1.2.19: ZUMBIDO DE LED — perto do letreiro (8 blocos), a caixa de
        // som zumbe baixinho (pitch agudo, diferente da lâmpada e do poste).
        if (placa.isLinkMercado() && placa.getLevel() != null) {
            long seg = Util.getMillis() / 1000L;
            double perto = camera.distanceToSqr(
                    net.minecraft.world.phys.Vec3.atCenterOf(placa.getBlockPos()));
            if (perto < 64.0 && seg != this.ultimoZumbidoSeg && seg % 11L == 0L) {
                this.ultimoZumbidoSeg = seg;
                placa.getLevel().playLocalSound(
                        placa.getBlockPos().getX() + 0.5, placa.getBlockPos().getY() + 0.7,
                        placa.getBlockPos().getZ() + 0.5,
                        IntoxicantesMod.ZUMBIDO_UV, net.minecraft.sounds.SoundSource.BLOCKS,
                        0.18F, 1.8F, false);
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.linhas.isEmpty() || !state.ledLigado) return;

        // ---- v1.2.40: o NOME, sempre. O ciclo ABERTO/FECHADO saiu do
        // letreiro (vai ganhar display dedicado próprio) — o print do tester
        // ("ABE PTTO") era o status entrando no MEIO do nome a cada 4s.
        // Linhas em branco não contam (defesa extra contra fantasmas de NBT).
        List<String> conteudo = state.linhas.stream()
                .filter(l -> l != null && !l.isBlank()).toList();
        if (conteudo.isEmpty()) return;

        // ---- a FAIXA DE LED: larguraBlocos×16 px (o display atravessa a
        // fachada; painel de altura total). A escala PREENCHE a caixa (estilo
        // Satisfactory): o maior fator uniforme que cabe, SEM teto de 1.0 —
        // o nome estica até a largura da construção.
        final float faixaLarguraPx = state.larguraBlocos * 16F;
        final float faixaAlturaPx = 16F;
        int maxW = Math.max(1, LedFont.larguraMax(conteudo));
        int nLin = conteudo.size();
        int alturaFonte = nLin * LedFont.ALTURA_GLYPH + (nLin - 1) * LedFont.LINHA_VAZIO;
        float escala = Math.min(faixaLarguraPx / maxW, faixaAlturaPx / (float) alturaFonte);
        // altura do grid em px de PAINEL (o desenho centraliza por ela).
        // larguraMax() JÁ conta glifo+espaço por letra — só a escala multiplica.
        float gridAlt = nLin * LedFont.ALTURA_GLYPH * escala
                + (nLin - 1) * LedFont.LINHA_VAZIO * escala;

        // ---- flicker de neon: a cada ~7s uma linha pisca por 2 frames
        long fase = Util.getMillis() / 50L;
        boolean falha = state.acesa && fase % 350L >= 348L;
        int linhaFalha = (int) (fase % 350L) % nLin;

        // v1.2.23: o pulso da alimentação — brilho sobe/desce 10% devagar
        float pulso = 0.9F + 0.1F * Mth.sin((Util.getMillis() % 4000L) / 4000F * 2F * Mth.PI);

        // cor do nome: LED vivo — SEMPRE verde (o mercado é 24h; não existe
        // mais "esmaecido de fechado": esse ramo nunca disparava)
        int corNome = argb(pulso, 0x39FF6E);

        pose.pushPose();
        // centro do bloco: a faixa de LED ocupa o painel INTEIRO (0..16px),
        // então a origem do texto é o centro do bloco
        pose.translate(0.5, 0.5, 0.5);
        // v1.2.25: CONVENÇÃO DA FORNALHA (prova vanilla): facing=north → 0°
        // (frente no -Z), south → 180°, east → -90°... = -toYRot. Com o
        // +180 antigo o texto nascia ATRÁS da caixa — só o verso dupla-face
        // escapava, flutuando no ar (o print do tester).
        pose.rotateDegrees(com.mojang.math.Axis.YP,
                -state.facing.toYRot());
        // o texto desenha nos DOIS lados da caixa: frente (+Z local, na face
        // do "front") e verso (−Z local, na face do "back") — letreiro lê
        // dos dois lados da rua como um de verdade
        final float px = 1.0F / 16.0F;
        for (float lado : new float[]{Z_FACE + Z_FLUTUA, -(Z_FACE + Z_FLUTUA)}) {
            pose.pushPose();
            pose.translate(0.0, 0.0, lado);
            // px de painel → blocos (1px do modelo = 1/16 de bloco). NUNCA
            // escala negativa — winding intacto (o bug do "nada de frente")
            pose.scale(px, px, px);
            final float esc = escala;
            final List<String> linhas = conteudo;
            collector.submitCustomGeometry(pose, RENDER_TYPE, (poseAtual, vc) -> {
                // centro do grid na origem; linha 0 é a de CIMA (Y de painel
                // cresce pra CIMA no bloco, então a primeira linha nasce no topo)
                float yTopo = gridAlt / 2F;
                for (int i = 0; i < linhas.size(); i++) {
                    String linha = linhas.get(i);
                    // v1.2.25: largura() já inclui os gaps — só a escala multiplica
                    float largLinha = LedFont.largura(linha) * esc;
                    float x0 = -largLinha / 2F;
                    boolean apagada = falha && i == linhaFalha;

                    // cor da linha: o nome em verde vivo; flicker apaga 1 linha
                    int cor = apagada ? argb(0.06F, 0x39FF6E) : corNome;

                    for (int ci = 0; ci < linha.length(); ci++) {
                        byte[] glifo = LedFont.glifo(linha.charAt(ci));
                        float gx = x0 + ci * LedFont.GLIFO_ESPACO * esc;
                        for (int row = 0; row < LedFont.ALTURA_GLYPH; row++) {
                            int bits = glifo[row];
                            if (bits == 0) continue;
                            for (int col = 0; col < LedFont.LARGURA_GLYPH; col++) {
                                if ((bits & (1 << (LedFont.LARGURA_GLYPH - 1 - col))) == 0) continue;
                                float x = gx + col * PX_PAINEL * esc;
                                // row 0 = topo do glifo; desce = subtra do topo
                                float y1 = yTopo - (i * (LedFont.ALTURA_GLYPH + LedFont.LINHA_VAZIO)
                                        + row) * PX_PAINEL * esc;
                                float y0 = y1 - PX_PAINEL * esc;
                                pixel(vc, poseAtual, x, y0, y1, cor);
                            }
                        }
                    }
                }
            });
            pose.popPose();
        }
        pose.popPose();

    }

    /** ARGB com alpha aplicado sobre a cor RGB pura. */
    private static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0F, 1F) * 255F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Um pixel de LED (v1.2.24): quad DUPLA-FACE no plano z — a frente (Z+)
     * e o verso (Z−) com winding CERTO cada um, igual ao texto do vanilla.
     * Com isso o letreiro lê de frente E de trás da rua, em qualquer rotação,
     * sem depender de truque de cull no RenderType.
     */
    private static void pixel(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y0, float y1, int cor) {
        float x1 = x + PX_PAINEL;
        // ---- face frontal (vista de Z+): anti-horário visto de frente
        quad(vc, pose, x, y0, x1, y1, 0F, cor, false);
        // ---- face do verso (vista de Z−): winding espelhado
        quad(vc, pose, x, y0, x1, y1, 0F, cor, true);
    }

    /** Quad axis-aligned (XY) no plano z, com UV do pixel aceso do atlas. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float y0, float x1, float y1, float z, int cor, boolean verso) {
        float zA = verso ? -z : z;
        if (verso) {
            // verso: ordem espelhada (continua anti-horário visto de Z−)
            vc.addVertex(pose, x1, y0, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y0, zA).setColor(cor)
                    .setUv(U_ACESO + UV_ACESO_MEIO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y1, zA).setColor(cor)
                    .setUv(U_ACESO + UV_ACESO_MEIO, V_ACESO + UV_ACESO_MEIO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y1, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO + UV_ACESO_MEIO).setLight(LUZ_LED);
        } else {
            vc.addVertex(pose, x0, y0, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y0, zA).setColor(cor)
                    .setUv(U_ACESO + UV_ACESO_MEIO, V_ACESO).setLight(LUZ_LED);
            vc.addVertex(pose, x1, y1, zA).setColor(cor)
                    .setUv(U_ACESO + UV_ACESO_MEIO, V_ACESO + UV_ACESO_MEIO).setLight(LUZ_LED);
            vc.addVertex(pose, x0, y1, zA).setColor(cor)
                    .setUv(U_ACESO, V_ACESO + UV_ACESO_MEIO).setLight(LUZ_LED);
        }
    }

    /** Estado de render: linhas + orientação + LED on/off. */
    public static class State extends BlockEntityRenderState {
        public final List<String> linhas = new ArrayList<>();
        public boolean acesa;
        public boolean ledLigado = true;
        public long ticksDoDia;
        public net.minecraft.core.Direction facing = net.minecraft.core.Direction.SOUTH;
        /** v1.2.31: largura da faixa em blocos (15 na fachada do mercado). */
        public int larguraBlocos = PlacaEsquinaoBlockEntity.LARGURA_FACHADA;
    }
}
