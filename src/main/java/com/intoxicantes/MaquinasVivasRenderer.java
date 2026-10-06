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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * v1.2.55 — AS MÁQUINAS VIVAS ("a versão chique"): 4 renderers que desenham
 * por cima dos modelos 3D quando a máquina está PROCESSANDO — e custam ZERO
 * quando ociosa (o early-out é na primeira linha do submit).
 *
 *   DORNA     : caldo translúcido subindo na boca + ondulação na superfície
 *   ALAMBIQUE : tacho esquentando (brilho pulsante) + calor andando elipse
 *               por elipse na serpentina até o bico pingar no vidro
 *   PRIMA     : rolos da moenda girando / parafuso da prensa descendo /
 *               mosto rodando no caldeirão (um renderer pros 3 tipos)
 *   BARRIL    : espuma subindo no buraco da rolha SÓ na FERMENTAÇÃO
 *               (maturação é silenciosa, como no código real)
 *
 * O líquido usa RenderTypes.entityTranslucent com o atlas LED da casa
 * (v1.2.69 FIX: o translucentMovingBlock do 26.3 exige o formato completo
 * UV2/UV3/Normal/LineWidth que só o quad machinery do vanilla alimenta —
 * vértices soltos crashavam o client quando a máquina começava a processar).
 * O brilho quente usa textPolygonOffset com o mesmo atlas (cor pura
 * full-bright). Toda a geometria é submitCustomGeometry — zero textura nova.
 *
 * As orientações vêm do FACING do bloco (mesma convenção da fornalha: o
 * renderer rotaciona por -toYRot como o letreiro v1.2.25 provou).
 */
public final class MaquinasVivasRenderer {

    private MaquinasVivasRenderer() {}

    // ==================================================== CAMADAS
    /** O líquido (v1.2.69): entityTranslucent — formato completo de entity,
     * translúcido e sem depender do quad machinery do vanilla (vê ParreiraRenderer). */
    private static final RenderType RT_LIQUIDO = RenderTypes.entityTranslucent(
            Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "textures/misc/led_atlas.png"));
    /** O brilho quente (tacho/serpentina): atlas branco da casa + cor de vértice. */
    private static final RenderType RT_BRILHO = RenderTypes.textPolygonOffset(
            Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "textures/misc/led_atlas.png"));

    /** Luz full-bright (o brilho quente não depende de luz do mundo). */
    private static final int LUZ_CHEIA = 15728880;
    /** UV do pixel aceso do atlas LED (32×32, quadrante inferior direito). */
    private static final float U_ACESO = 0.5F;
    private static final float V_ACESO = 0.5F;
    private static final float UV_MEIO = 0.0625F;

    /** px do modelo → blocos (a origem do BE é o CANTO do bloco). */
    private static final float PX = 1.0F / 16.0F;

    // cores vivas (ARGB)
    private static final int CALDO_VERDE = 0x9A6B8F2E;      // caldo de cana (alpha 60%)
    private static final int MOSTO_ROXO = 0x8A5A2A6E;       // mosto de uva
    private static final int MOSTO_AMBAR = 0x8AB8782E;      // mosto de cerveja
    private static final int TACHO_QUENTE = 0x60FF7A30;     // brilho laranja do cobre
    private static final int SERPENTINA_VIVA = 0xC0FF9A40;  // o calor andando
    private static final int ESPUMA = 0xA8F0E8D8;           // espuma da fermentação
    private static final int PARAFUSO_BRILHO = 0x50FFB060;  // o parafuso "apertando"

    // ==================================================== ESTADO COMPARTILHADO

    /** O estado mínimo que os 4 renderers carregam (cada BE só preenche o seu). */
    public static class EstadoVivo extends BlockEntityRenderState {
        public boolean ativa;
        public float progresso;          // 0..1
        public Direction facing = Direction.SOUTH;
        public int fase;                 // barril: FASE_FERMENTANDO etc.
        public MaquinaPrimaBlock.Tipo tipo; // prima: MOENDA/PRENSA/CALDEIRAO
        public boolean comCalor;         // alambique: fogo embaixo
    }

    // ==================================================== DORNA

    public static class DornaRenderer
            implements BlockEntityRenderer<DornaBebidaBlockEntity, DornaRenderer.Estado> {

        public DornaRenderer(BlockEntityRendererProvider.Context ctx) {}

        public static class Estado extends EstadoVivo {}

        @Override
        public Estado createRenderState() {
            return new Estado();
        }

        @Override
        public void extractRenderState(DornaBebidaBlockEntity dorna, Estado state,
                float parcial, net.minecraft.world.phys.Vec3 camera,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(dorna, state, crumbling);
            state.ativa = dorna.fermentando();
            state.progresso = dorna.progressoClient();
            // (as máquinas são simétricas — sem FACING; geometria ao mundo)
        }

        @Override
        public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
                CameraRenderState camera) {
            if (!state.ativa) return; // ociosa = custo zero

            // o nível do caldo: a boca da dorna vai de y7.4 (fundo) a y9.0 (borda)
            float frac = 0.15F + 0.65F * state.progresso;
            float topo = Mth.lerp(frac, 6.9F, 8.9F);
            long t = Util.getMillis();

            collector.submitCustomGeometry(pose, RT_LIQUIDO, (p, vc) -> {
                // o CALDO: caixa 3..13 (borda interna 2.4 do modelo + folga),
                // ondulando no topo
                faceLiquido(vc, p, 3.0F, 13.0F, 3.0F, 13.0F, 7.0F, topo,
                        ondulacao(t, 0), CALDO_VERDE, state.lightCoords);
            });
            // vapor levinho por cima (o shader translúcido com cor mais clara)
            float v = ondulacao(t, 3) * 0.15F;
            if (v > 0.02F) {
                int corVapor = ((int) (v * 255) << 24) | 0xE8E8E0;
                collector.submitCustomGeometry(pose, RT_LIQUIDO, (p, vc) ->
                        faceLiquido(vc, p, 4.5F, 11.5F, 4.5F, 11.5F, topo, topo + 1.6F + v, 0F, corVapor,
                                state.lightCoords));
            }
        }
    }

    // ==================================================== ALAMBIQUE

    public static class AlambiqueRenderer
            implements BlockEntityRenderer<AlambiqueBlockEntity, AlambiqueRenderer.Estado> {

        public AlambiqueRenderer(BlockEntityRendererProvider.Context ctx) {}

        public static class Estado extends EstadoVivo {}

        @Override
        public Estado createRenderState() {
            return new Estado();
        }

        @Override
        public void extractRenderState(AlambiqueBlockEntity alambique, Estado state,
                float parcial, net.minecraft.world.phys.Vec3 camera,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(alambique, state, crumbling);
            state.ativa = alambique.destilando();
            state.progresso = alambique.progressoClient();
            state.comCalor = alambique.comCalorClient();
        }

        @Override
        public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
                CameraRenderState camera) {
            if (!state.ativa) return; // ociosa = custo zero

            long t = Util.getMillis();
            // o tacho pulsa quente (o cobre vivo) — só se tem fogo embaixo
            if (state.comCalor) {
                float pulso = 0.55F + 0.45F * (float) Math.abs(Math.sin(t / 700.0));
                int cor = ((int) (pulso * 0x60) << 24) | 0xFF7A30;
                pose.pushPose();
                collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) ->
                        brilhoBox(vc, p, 1.7F, 2.1F, 1.7F, 14.3F, 7.9F, 14.3F, cor));
                pose.popPose();
            }
            // o CALOR ANDANDO na serpentina: 3 elipses (8.8/10.4/12.0 de altura),
            // a onda anda por elas em sequência; a fração = progresso
            float onda = (t % 2400L) / 2400F; // o ciclo da onda (2.4s)
            collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) -> {
                for (int i = 0; i < 3; i++) {
                    float fase = onda * 3F - i * 0.5F;
                    float brilho = Mth.clamp(1F - Math.abs(fase - Mth.floor(fase + 0.5F)) * 2F, 0F, 1F);
                    if (brilho <= 0.05F) continue;
                    int cor = ((int) (brilho * 0xC0) << 24) | 0xFF9A40;
                    float y = 8.8F + i * 1.6F;
                    // o anel quente na serpentina (tubo 14.3..15.5 de raio)
                    brilhoBox(vc, p, 14.2F, y - 0.15F, 10.7F, 15.6F, y + 0.45F, 12.1F, cor);
                }
                // o bico PINGA no fim: brilho no vidro quando o progresso > 0.85
                if (state.progresso > 0.85F) {
                    int cor = SERPENTINA_VIVA;
                    brilhoBox(vc, p, 14.9F, 11.8F, 10.9F, 15.4F, 12.7F, 11.6F, cor);
                }
            });
        }
    }

    // ==================================================== MÁQUINA DE PRIMA (moenda/prensa/caldeirão)

    public static class PrimaRenderer
            implements BlockEntityRenderer<MaquinaPrimaBlockEntity, PrimaRenderer.Estado> {

        public PrimaRenderer(BlockEntityRendererProvider.Context ctx) {}

        public static class Estado extends EstadoVivo {}

        @Override
        public Estado createRenderState() {
            return new Estado();
        }

        @Override
        public void extractRenderState(MaquinaPrimaBlockEntity maquina, Estado state,
                float parcial, net.minecraft.world.phys.Vec3 camera,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(maquina, state, crumbling);
            state.ativa = maquina.processando();
            state.progresso = maquina.progressoClient();
            state.tipo = maquina.tipoClient();
        }

        @Override
        public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
                CameraRenderState camera) {
            if (!state.ativa) return; // ociosa = custo zero

            long t = Util.getMillis();
            switch (state.tipo == null ? MaquinaPrimaBlock.Tipo.MOENDA : state.tipo) {
                case MOENDA -> {
                    // o ROLO: listras claras passando pelos 2 rolos (girando)
                    float fase = (t % 1000L) / 1000F;
                    int cor = ((int) (0x55 + 0x30 * Math.sin(t / 120.0)) << 24) | 0xFFC878;
                    collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) -> {
                        for (int rolo = 0; rolo < 2; rolo++) {
                            float x0 = rolo == 0 ? 3.2F : 8.8F;
                            // 4 "aletas" girando: 2 acesas por vez (o desenho da rotação)
                            for (int a = 0; a < 2; a++) {
                                float faseA = fase + a * 0.5F + rolo * 0.25F;
                                if ((faseA % 1.0F) > 0.5F) continue;
                                brilhoBox(vc, p, x0, 3.2F + faseA % 0.5F * 8F, 5.2F,
                                        x0 + 3.6F, 4.4F + faseA % 0.5F * 8F, 10.8F, cor);
                            }
                        }
                    });
                }
                case PRENSA -> {
                    // o PARAFUSO desce com o progresso (apertando as uvas)
                    float descida = 9.0F + state.progresso * 3.2F;
                    int cor = ((int) (0x40 + 0x20 * Math.abs(Math.sin(t / 300.0))) << 24) | 0xFFB060;
                    collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) ->
                            brilhoBox(vc, p, 7.4F, descida, 7.4F, 8.6F, descida + 1.4F, 8.6F, cor));
                    // o suco correndo na calha (translúcido roxo no fundo)
                    float suco = 0.3F + 0.4F * state.progresso;
                    int corSuco = ((int) (suco * 255) << 24) | 0x5A2A6E;
                    collector.submitCustomGeometry(pose, RT_LIQUIDO, (p, vc) ->
                            faceLiquido(vc, p, 3.6F, 12.4F, 3.6F, 12.4F, 2.2F, 3.0F + suco, 0F, corSuco,
                                    state.lightCoords));
                }
                case CALDEIRAO -> {
                    // o MOSTO roda na boca (fervura): onda girando + vapor alto
                    float rot = (t % 3000L) / 3000F;
                    collector.submitCustomGeometry(pose, RT_LIQUIDO, (p, vc) -> {
                        faceLiquido(vc, p, 2.8F, 13.2F, 2.8F, 13.2F, 7.5F, 8.1F,
                                ondulacao(t, 0), MOSTO_AMBAR, state.lightCoords);
                    });
                    // a "pata" quente girando (a corrente da fervura)
                    int cor = ((int) (0x50 + 0x35 * Math.abs(Math.sin(t / 250.0))) << 24) | 0xFF8A30;
                    float ax = Mth.cos(rot * 2F * Mth.PI) * 4.2F;
                    float az = Mth.sin(rot * 2F * Mth.PI) * 4.2F;
                    collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) ->
                            brilhoBox(vc, p, 8.0F + ax, 7.3F, 8.0F + az,
                                    8.7F + ax, 8.1F, 8.7F + az, cor));
                }
            }
        }
    }

    // ==================================================== BARRIL

    public static class BarrilRenderer
            implements BlockEntityRenderer<BarrilBebidaBlockEntity, BarrilRenderer.Estado> {

        public BarrilRenderer(BlockEntityRendererProvider.Context ctx) {}

        public static class Estado extends EstadoVivo {}

        @Override
        public Estado createRenderState() {
            return new Estado();
        }

        @Override
        public void extractRenderState(BarrilBebidaBlockEntity barril, Estado state,
                float parcial, net.minecraft.world.phys.Vec3 camera,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(barril, state, crumbling);
            // SÓ a FERMENTAÇÃO anima (a maturação é silenciosa — como no código)
            state.ativa = barril.fermentando();
            state.fase = barril.faseClient();
        }

        @Override
        public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
                CameraRenderState camera) {
            if (!state.ativa) return; // ociosa/maturando = custo zero

            long t = Util.getMillis();
            // a espuma sobe no buraco da rolha (o topo do barril tem o bocal
            // central ~6.4..9.6px de raio; a espuma vem de y14.2 a y15.8)
            float pulso = (t % 1600L) / 1600F;
            float altura = 0.6F + 0.9F * pulso;
            int alpha = (int) (0x60 + 0x48 * Math.abs(Math.sin(t / 350.0))) << 24;
            int cor = alpha | 0xF0E8D8;
            float y1 = 14.4F + altura;
            collector.submitCustomGeometry(pose, RT_LIQUIDO, (p, vc) ->
                    faceLiquido(vc, p, 6.6F, 9.4F, 6.6F, 9.4F, 14.2F, y1, ondulacao(t, 1), cor,
                            state.lightCoords));
            // a bolha estourando (o "glup" visual, 1x por ciclo)
            if (pulso > 0.9F) {
                collector.submitCustomGeometry(pose, RT_BRILHO, (p, vc) ->
                        brilhoBox(vc, p, 7.4F, y1, 7.4F, 8.6F, y1 + 0.9F, 8.6F, 0x90F8F0E0));
            }
        }
    }

    // ==================================================== GEOMETRIA COMPARTILHADA

    // (as máquinas são simétricas: sem rotação — a geometria alinha ao mundo)

    /** Onda de superfície (o "fervilhar" do líquido). */
    private static float ondulacao(long t, int semente) {
        return 0.06F * (float) Math.sin(t / 380.0 + semente * 2.1);
    }

    /**
     * v1.2.69 FIX: vértice do líquido com o formato ENTITY completo (o padrão
     * comprovado do ParreiraRenderer): cor + UV + overlay + luz + normal.
     * O translucentMovingBlock antigo exigia UV2/UV3/Normal/LineWidth que o
     * vanilla só alimenta via quad machinery — vértice solto crashava.
     */
    private static void verticeLiquido(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y, float z, int cor, float nx, float ny, float nz, int luz) {
        vc.addVertex(pose, x * PX, y * PX, z * PX)
                .setColor(cor)
                .setUv(U_ACESO, V_ACESO)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(luz)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * A caixa do líquido: TOP + BOTTOM + os 4 lados (cada face é um quad com
     * winding e NORMAL certos). O "ondulação" entra no topo (superfície viva).
     */
    private static void faceLiquido(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float x1, float z0, float z1, float y0, float y1,
            float onda, int cor, int luz) {
        float top = y1 + onda;
        // topo (superfície, anti-horário visto de cima)
        verticeLiquido(vc, pose, x0, top, z0, cor, 0, 1, 0, luz);
        verticeLiquido(vc, pose, x0, top, z1, cor, 0, 1, 0, luz);
        verticeLiquido(vc, pose, x1, top, z1, cor, 0, 1, 0, luz);
        verticeLiquido(vc, pose, x1, top, z0, cor, 0, 1, 0, luz);
        // fundo (horário visto de cima)
        verticeLiquido(vc, pose, x0, y0, z0, cor, 0, -1, 0, luz);
        verticeLiquido(vc, pose, x1, y0, z0, cor, 0, -1, 0, luz);
        verticeLiquido(vc, pose, x1, y0, z1, cor, 0, -1, 0, luz);
        verticeLiquido(vc, pose, x0, y0, z1, cor, 0, -1, 0, luz);
        // norte (z0) e sul (z1)
        lateralLiquido(vc, pose, x0, x1, z0, y0, top, cor, false, luz);
        lateralLiquido(vc, pose, x0, x1, z1, y0, top, cor, true, luz);
        // oeste (x0) e leste (x1)
        lateralLiquidoX(vc, pose, z0, z1, x0, y0, top, cor, true, luz);
        lateralLiquidoX(vc, pose, z0, z1, x1, y0, top, cor, false, luz);
    }

    private static void lateralLiquido(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float x1, float z, float y0, float y1, int cor, boolean inverte,
            int luz) {
        float nz = inverte ? 1.0F : -1.0F;
        if (!inverte) {
            verticeLiquido(vc, pose, x0, y0, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x1, y0, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x1, y1, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x0, y1, z, cor, 0, 0, nz, luz);
        } else {
            verticeLiquido(vc, pose, x1, y0, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x0, y0, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x0, y1, z, cor, 0, 0, nz, luz);
            verticeLiquido(vc, pose, x1, y1, z, cor, 0, 0, nz, luz);
        }
    }

    private static void lateralLiquidoX(VertexConsumer vc, PoseStack.Pose pose,
            float z0, float z1, float x, float y0, float y1, int cor, boolean inverte,
            int luz) {
        float nx = inverte ? -1.0F : 1.0F;
        if (!inverte) {
            verticeLiquido(vc, pose, x, y0, z0, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y0, z1, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y1, z1, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y1, z0, cor, nx, 0, 0, luz);
        } else {
            verticeLiquido(vc, pose, x, y0, z1, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y0, z0, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y1, z0, cor, nx, 0, 0, luz);
            verticeLiquido(vc, pose, x, y1, z1, cor, nx, 0, 0, luz);
        }
    }

    /**
     * Um quad horizontal do brilho quente (frame de um anel/box): a face de
     * CIMA de uma caixa fina — dupla-face pra ser vista de cima e de baixo.
     */
    private static void brilhoBox(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1, int cor) {
        // face de cima
        vc.addVertex(pose, x0 * PX, y1 * PX, z0 * PX).setColor(cor)
                .setUv(U_ACESO, V_ACESO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x0 * PX, y1 * PX, z1 * PX).setColor(cor)
                .setUv(U_ACESO, V_ACESO + UV_MEIO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x1 * PX, y1 * PX, z1 * PX).setColor(cor)
                .setUv(U_ACESO + UV_MEIO, V_ACESO + UV_MEIO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x1 * PX, y1 * PX, z0 * PX).setColor(cor)
                .setUv(U_ACESO + UV_MEIO, V_ACESO).setLight(LUZ_CHEIA);
        // face de baixo (verso)
        vc.addVertex(pose, x0 * PX, y0 * PX, z0 * PX).setColor(cor)
                .setUv(U_ACESO, V_ACESO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x1 * PX, y0 * PX, z0 * PX).setColor(cor)
                .setUv(U_ACESO + UV_MEIO, V_ACESO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x1 * PX, y0 * PX, z1 * PX).setColor(cor)
                .setUv(U_ACESO + UV_MEIO, V_ACESO + UV_MEIO).setLight(LUZ_CHEIA);
        vc.addVertex(pose, x0 * PX, y0 * PX, z1 * PX).setColor(cor)
                .setUv(U_ACESO, V_ACESO + UV_MEIO).setLight(LUZ_CHEIA);
    }
}
