package com.intoxicantes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.intoxicantes.energia.EloCabo;

/**
 * v1.2.80 — O DESENHO DO CABO SUSPENSO (client): o elo chega por pacote
 * (CabosNetworking) e vira aqui um TUBO de cobre em curva pendente entre os
 * dois conectores — a barriga do cabo é o que dá leitura de "cabo no ar", não
 * uma linha reta de um bloco ao outro.
 *
 * Sem blockstate, sem modelo e sem chunk: o vão é geometria do nível, então o
 * cabo entra e sai exatamente na boca dos dois conectores.
 */
@Environment(EnvType.CLIENT)
public final class CabosClient {

    /** Elos recebidos por dimensão (o servidor manda só a fatia visível). */
    private static final Map<ResourceKey<Level>, List<EloCabo>> RECEBIDOS = new HashMap<>();

    /** Cobre: translúcido de entidade (o formato completo de vértice). */
    private static final RenderType RT_CABO = RenderTypes.entityTranslucent(
            Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, "textures/misc/led_atlas.png"));

    private static final float UV_CENTRO = 0.5F;

    private CabosClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CabosNetworking.CabosPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> receber(payload)));
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(CabosClient::desenhar);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> RECEBIDOS.clear());
    }

    private static void receber(CabosNetworking.CabosPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(payload.dimensao())) return;
        RECEBIDOS.keySet().retainAll(Set.of(level.dimension()));
        RECEBIDOS.put(level.dimension(), payload.elos());
    }

    /**
     * Quantos elos o cliente TEM desenhado agora (o gametest de cliente usa
     * para esperar o cabo chegar pelo pacote antes de fotografar).
     */
    public static int elosRecebidos() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0;
        List<EloCabo> elos = RECEBIDOS.get(level.dimension());
        return elos == null ? 0 : elos.size();
    }

    private static void desenhar(LevelRenderContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ctx.levelState().cameraRenderState.initialized) return;
        List<EloCabo> elos = RECEBIDOS.get(level.dimension());
        if (elos == null || elos.isEmpty()) return;
        Vec3 camera = ctx.levelState().cameraRenderState.pos;
        for (EloCabo elo : elos) {
            double perto = Math.min(camera.distanceToSqr(Vec3.atCenterOf(elo.a())),
                    camera.distanceToSqr(Vec3.atCenterOf(elo.b())));
            if (perto > CabosNetworking.RAIO_CLIENTE * CabosNetworking.RAIO_CLIENTE) continue;
            PoseStack pose = new PoseStack();
            pose.mulPose(ctx.poseStack().last().pose());
            pose.translate(-camera.x, -camera.y, -camera.z);
            int luz = luzDoCabo(level, elo);
            ctx.submitNodeCollector().submitCustomGeometry(pose, RT_CABO,
                    (p, vc) -> tubo(vc, p, elo, luz));
        }
    }

    /** Luz do conector (o cobre some no escuro como qualquer bloco). */
    private static int luzDoCabo(ClientLevel level, EloCabo elo) {
        int b = Math.clamp(level.getMaxLocalRawBrightness(elo.a()), 0, 15);
        return 0xF00000 | (b << 4) | b;
    }

    // ==================================================== GEOMETRIA

    /** O raio do tubo por bitola (mm² → espessura na tela). */
    private static float raio(double bitola) {
        if (bitola <= 1.5) return 0.038F;
        if (bitola <= 2.5) return 0.048F;
        if (bitola <= 4.0) return 0.058F;
        if (bitola <= 6.0) return 0.068F;
        return 0.082F;
    }

    /** O cobre de cada bitola (o grosso é mais avermelhado e menos lustroso). */
    private static int cor(double bitola) {
        if (bitola <= 1.5) return 0xFFB4783C;
        if (bitola <= 2.5) return 0xFFC07A38;
        if (bitola <= 4.0) return 0xFFCE8034;
        if (bitola <= 6.0) return 0xFFD98A31;
        return 0xFFE29430;
    }

    private static final int SEGMENTOS = 18;
    private static final int LADOS = 6;

    /**
     * O tubo: Bézier quadrática de A a B com o controle abaixo do meio (a
     * barriga do cabo). Sem colisão com blocos — o cobre passa por cima.
     */
    private static void tubo(VertexConsumer vc, PoseStack.Pose pose, EloCabo elo, int luz) {
        Vec3 a = ponta(elo.a(), elo.faceA());
        Vec3 b = ponta(elo.b(), elo.faceB());
        double vao = a.distanceTo(b);
        if (vao < 0.01) return;
        // quanto maior o vão, mais a barriga (mas nunca uma corda reta)
        float flex = (float) Math.min(4.0, 0.10 + vao * 0.22);
        Vec3 controle = new Vec3((a.x + b.x) / 2.0, (a.y + b.y) / 2.0 - flex, (a.z + b.z) / 2.0);
        float r = raio(elo.bitola());
        int corBase = cor(elo.bitola());

        Vec3[] pontos = new Vec3[SEGMENTOS + 1];
        for (int i = 0; i <= SEGMENTOS; i++) {
            pontos[i] = bezier(a, controle, b, i / (float) SEGMENTOS);
        }
        // O referencial de cada anel é PROPAGADO do anterior (Gram-Schmidt no
        // plano normal à tangente) e o anel é montado UMA VEZ: se os dois
        // segmentos que se encontram montassem o mesmo anel com tangentes
        // diferentes, o tubo torcia na junção e abria fenda (via-se o céu).
        Vec3[] tangentes = new Vec3[SEGMENTOS];
        for (int i = 0; i < SEGMENTOS; i++) {
            Vec3 delta = pontos[i + 1].subtract(pontos[i]);
            tangentes[i] = delta.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : delta.normalize();
        }
        Vec3[] laterais = new Vec3[SEGMENTOS + 1];
        laterais[0] = projetar(perpendicular(tangentes[0]), tangentes[0]);
        for (int i = 1; i <= SEGMENTOS; i++) {
            laterais[i] = projetar(laterais[i - 1], tangentes[Math.min(i, SEGMENTOS - 1)]);
        }

        Vec3[][] anel = new Vec3[SEGMENTOS + 1][LADOS];
        Vec3[][] normais = new Vec3[SEGMENTOS + 1][LADOS];
        for (int i = 0; i <= SEGMENTOS; i++) {
            Vec3 tangente = tangentes[Math.min(i, SEGMENTOS - 1)];
            Vec3 eixoY = tangente.cross(laterais[i]).normalize();
            for (int lado = 0; lado < LADOS; lado++) {
                double ang = Math.PI * 2.0 * lado / LADOS;
                Vec3 d = laterais[i].scale((float) Math.cos(ang)).add(eixoY.scale((float) Math.sin(ang)));
                normais[i][lado] = d;
                anel[i][lado] = pontos[i].add(d.scale(r));
            }
        }

        for (int i = 0; i < SEGMENTOS; i++) {
            for (int lado = 0; lado < LADOS; lado++) {
                int proximo = (lado + 1) % LADOS;
                Vec3 normal = normais[i][lado].add(normais[i][proximo]).normalize();
                int cor = sombrear(corBase, normal);
                // QUATRO vértices por face: o RenderType do tubo é QUADS, então
                // dois triângulos por face viravam um quad torto atravessando a
                // lateral vizinha — o cabo saía com faixas de céu no meio.
                vertice(vc, pose, anel[i][lado], cor, normal, luz);
                vertice(vc, pose, anel[i][proximo], cor, normal, luz);
                vertice(vc, pose, anel[i + 1][proximo], cor, normal, luz);
                vertice(vc, pose, anel[i + 1][lado], cor, normal, luz);
            }
        }
    }

    /** O componente de {@code vetor} perpendicular a {@code normal}. */
    private static Vec3 projetar(Vec3 vetor, Vec3 normal) {
        Vec3 saida = vetor.subtract(normal.scale((float) vetor.dot(normal)));
        return saida.lengthSqr() < 1.0E-8 ? perpendicular(normal) : saida.normalize();
    }

    private static Vec3 bezier(Vec3 a, Vec3 c, Vec3 b, float t) {
        float u = 1.0F - t;
        return new Vec3(
                u * u * a.x + 2 * u * t * c.x + t * t * b.x,
                u * u * a.y + 2 * u * t * c.y + t * t * b.y,
                u * u * a.z + 2 * u * t * c.z + t * t * b.z);
    }

    /**
     * O vetor perpendicular ao eixo do cabo, sem virar do nada no meio do vão:
     * um cabo pendente é quase horizontal, então cruzar com o Y do mundo dá
     * sempre o mesmo "lado". Só um trecho vertical usa o X como referência.
     */
    private static Vec3 perpendicular(Vec3 eixo) {
        Vec3 base = Math.abs(eixo.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 lateral = base.cross(eixo);
        return lateral.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : lateral.normalize();
    }

    /** O brilho do lado do tubo que encara a luz do mundo (y para cima). */
    private static int sombrear(int corBase, Vec3 normal) {
        double luz = 0.55 + 0.45 * (normal.y * 0.6 + Math.max(0.0, -normal.x * 0.3));
        float fator = (float) Math.clamp(luz, 0.45, 1.15);
        int a = (corBase >>> 24) & 0xFF;
        int r = Math.clamp((int) (((corBase >> 16) & 0xFF) * fator), 0, 255);
        int g = Math.clamp((int) (((corBase >> 8) & 0xFF) * fator), 0, 255);
        int b = Math.clamp((int) ((corBase & 0xFF) * fator), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void vertice(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int cor,
            Vec3 normal, int luz) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(cor)
                .setUv(UV_CENTRO, UV_CENTRO)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(luz)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** A boca do conector: a ponta do cabo na face que aponta para o vão. */
    private static Vec3 ponta(BlockPos pos, Direction face) {
        return new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5,
                pos.getY() + 0.5 + face.getStepY() * 0.5,
                pos.getZ() + 0.5 + face.getStepZ() * 0.5);
    }
}