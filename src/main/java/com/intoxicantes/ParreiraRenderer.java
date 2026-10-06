package com.intoxicantes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import com.mojang.math.Axis;

/** A raiz desenha a sua videira ao redor das cercas originais, sem trocar blocos. */
public final class ParreiraRenderer
        implements BlockEntityRenderer<ParreiraBlockEntity, ParreiraRenderer.Estado> {
    private static final RenderType RAMOS = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(
            IntoxicantesMod.MOD_ID, "textures/block/parreira_atlas.png"));
    private static final RenderType FOLHAS = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(
            IntoxicantesMod.MOD_ID, "textures/block/parreira_folha.png"));
    private static final UV CASCA = new UV(1F / 128, 1F / 128, 63F / 128, 63F / 128);
    private static final UV FOLHA = new UV(0, 0, 1, 1);
    private static final Malha VAZIA = new Malha(List.of(), List.of());
    /** Só a extração toca este cache. O callback de render recebe uma malha imutável. */
    private final Map<ParreiraBlockEntity, Cache> cache = new WeakHashMap<>();
    /** Descoberta das raízes concorrentes: no máximo uma vez a cada dez ticks por BE. */
    private final Map<ParreiraBlockEntity, CacheDonos> cacheDonos = new WeakHashMap<>();
    private final ItemModelResolver itens;
    private ItemStack uva;
    private ItemStack uvaMadura;

    public ParreiraRenderer(BlockEntityRendererProvider.Context context) {
        itens = context.itemModelResolver();
    }

    public static final class Estado extends BlockEntityRenderState {
        private Malha malha = VAZIA;
        private List<CachoVisual> cachos = List.of();
        private final ItemStackRenderState uva = new ItemStackRenderState();
        private final ItemStackRenderState uvaMadura = new ItemStackRenderState();
    }

    @Override
    public Estado createRenderState() { return new Estado(); }

    @Override
    public void extractRenderState(ParreiraBlockEntity parreira, Estado state, float parcial,
            Vec3 camera, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(parreira, state, crumbling);
        state.cachos = List.of();
        var estrutura = parreira.lerEstrutura();
        if (estrutura == null || estrutura.coluna().isEmpty() || parreira.etapaVisual() == 0
                || parreira.getLevel() == null) {
            state.malha = VAZIA;
            return;
        }
        // Posições/luzes são extraídas no cliente; submit nunca consulta mundo ou block entity.
        List<BlockPos> coluna = estrutura.coluna().stream().map(BlockPos::immutable).toList();
        List<BlockPos> cobertura = estrutura.cobertura().stream().map(BlockPos::immutable).toList();
        // Uma descoberta de raízes/armações por amostra, compartilhada com a colheita.
        // Poda/mudança da estrutura invalida imediatamente; raiz vizinha em até 0,5s.
        // A dona de cada apoio não muda conforme UV, frutos ou ordem de renderização.
        long amostra = parreira.getLevel().getGameTime() / 10;
        CacheDonos propriedade = cacheDonos.get(parreira);
        if (propriedade == null || propriedade.amostra != amostra || !propriedade.estrutura.equals(estrutura)) {
            propriedade = new CacheDonos(estrutura, amostra,
                    Map.copyOf(ParreiraEstrutura.donosDosApoios(parreira.getLevel(), estrutura)));
            cacheDonos.put(parreira, propriedade);
        }
        Map<BlockPos, BlockPos> donos = propriedade.donos;
        Set<BlockPos> proprios = Set.copyOf(estrutura.apoios().stream()
                .filter(pos -> parreira.getBlockPos().equals(donos.get(pos)))
                .map(BlockPos::immutable).toList());
        if (proprios.isEmpty()) {
            state.malha = VAZIA;
            return;
        }
        Map<BlockPos, Integer> luzes = new HashMap<>();
        for (BlockPos pos : proprios) {
            luzes.put(pos.immutable(), LightCoordsUtil.getLightCoords(parreira.getLevel(), pos.above()));
        }
        Chave chave = new Chave(parreira.getBlockPos().immutable(), coluna, cobertura, proprios,
                parreira.etapaVisual(), estrutura.produtiva() ? parreira.cachosClient() : Map.of(),
                state.lightCoords, Map.copyOf(luzes));
        Cache anterior = cache.get(parreira);
        if (anterior == null || !anterior.chave.equals(chave)) {
            anterior = new Cache(chave, construir(chave));
            cache.put(parreira, anterior);
        }
        state.malha = anterior.malha;
        List<CachoVisual> frutos = new ArrayList<>();
        for (int i = 0; i < cobertura.size() && chave.etapa >= 4; i++) {
            BlockPos pos = cobertura.get(i);
            ParreiraBlockEntity.EstadoCacho cacho = chave.cachos.get(pos);
            if (!proprios.contains(pos) || cacho == null || !cacho.pronto()) continue;
            frutos.add(new CachoVisual(centro(chave, pos).add(.23, .64, -.23),
                    (float) Math.toDegrees(i * .31), cacho.maturacao() >= 3, luz(chave, pos)));
        }
        state.cachos = List.copyOf(frutos);
        if (!frutos.isEmpty()) {
            // No26.3, componentes dos itens só estão vinculados depois de abrir
            // o mundo. O renderer também nasce na recarga inicial do menu.
            if (uva == null) {
                uva = new ItemStack(IntoxicantesMod.UVA);
                uvaMadura = new ItemStack(IntoxicantesMod.UVA);
                // Componente só de render: não muda colheita nem itens salvos.
                uvaMadura.set(DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(List.of(3F), List.of(), List.of(), List.of()));
            }
            // Mesmo item-model nativo do inventário, recarregado pelo Minecraft com F3+T.
            itens.updateForTopItem(state.uva, uva, ItemDisplayContext.NONE,
                    parreira.getLevel(), null, 0);
            itens.updateForTopItem(state.uvaMadura, uvaMadura, ItemDisplayContext.NONE,
                    parreira.getLevel(), null, 0);
        }
    }

    @Override
    public void submit(Estado state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        Malha malha = state.malha;
        if (malha == VAZIA) return;
        if (!malha.ramos.isEmpty()) collector.submitCustomGeometry(pose, RAMOS,
                (p, vc) -> desenhar(malha.ramos, p, vc));
        if (!malha.folhas.isEmpty()) collector.submitCustomGeometry(pose, FOLHAS,
                (p, vc) -> desenhar(malha.folhas, p, vc));
        for (CachoVisual cacho : state.cachos) {
            pose.pushPose();
            pose.translate(cacho.centro.x, cacho.centro.y, cacho.centro.z);
            pose.rotateDegrees(Axis.YP, cacho.giro);
            (cacho.maduro ? state.uvaMadura : state.uva).submit(pose, collector,
                    cacho.luz, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    /** A cobertura ultrapassa a caixa da raiz; não pode sumir quando a raiz sai da tela. */
    @Override
    public boolean shouldRenderOffScreen() { return true; }

    @Override
    public int getViewDistance() { return 64; }

    private record UV(float u0, float v0, float u1, float v1) {}
    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, UV uv, int cor, int luz) {}
    private record Malha(List<Quad> ramos, List<Quad> folhas) {}
    private record Cache(Chave chave, Malha malha) {}
    private record CacheDonos(ParreiraEstrutura.Estrutura estrutura, long amostra, Map<BlockPos, BlockPos> donos) {}
    private record CachoVisual(Vec3 centro, float giro, boolean maduro, int luz) {}
    private record Chave(BlockPos raiz, List<BlockPos> coluna, List<BlockPos> cobertura, Set<BlockPos> proprios,
            int etapa, Map<BlockPos, ParreiraBlockEntity.EstadoCacho> cachos,
            int luzRaiz, Map<BlockPos, Integer> luzes) {}

    private static Vec3 centro(Chave chave, BlockPos pos) {
        return new Vec3(pos.getX() - chave.raiz.getX() + .5,
                pos.getY() - chave.raiz.getY(), pos.getZ() - chave.raiz.getZ() + .5);
    }

    private static int luz(Chave chave, BlockPos pos) {
        return chave.luzes.getOrDefault(pos, chave.luzRaiz);
    }

    private static Malha construir(Chave chave) {
        Geometria geo = new Geometria();
        Vec3 base = centro(chave, chave.coluna.getFirst());
        Vec3 raiz = new Vec3(.5, .25, .5);
        Vec3 frente = raiz.subtract(base).multiply(1, 0, 1).normalize();
        Vec3 encontro = base.add(frente.scale(.225)).add(0, .48, 0);
        if (chave.proprios.contains(chave.coluna.getFirst())) {
            geo.segmento(raiz, raiz.lerp(encontro, .45).add(0, .05, 0), .033, chave.luzRaiz);
            geo.segmento(raiz.lerp(encontro, .45).add(0, .05, 0), encontro, .038, chave.luzRaiz);
        }
        float altura = chave.coluna.size() - .13F;
        if (chave.etapa == 1) altura = Math.min(.9F, altura);
        if (chave.etapa == 2) altura = Math.min(1.85F, altura);
        double angulo = Math.atan2(frente.z, frente.x);
        Vec3 anterior = encontro;
        int segmentos = Math.max(4, (int) (altura * 8));
        for (int n = 1; n <= segmentos; n++) {
            double y = .48 + (altura - .48) * n / segmentos;
            double a = angulo + n * Math.PI / 2;
            Vec3 atual = new Vec3(base.x + Math.cos(a) * .225, base.y + y,
                    base.z + Math.sin(a) * .225);
            BlockPos apoio = chave.coluna.get(Math.min(chave.coluna.size() - 1, (int) y));
            int luz = luz(chave, apoio);
            boolean proprio = chave.proprios.contains(apoio);
            if (proprio) geo.segmento(anterior, atual, .042, luz);
            if (proprio && (n & 1) == 0) {
                Vec3 ponta = atual.add(Math.cos(a) * .16, .035, Math.sin(a) * .16);
                geo.segmento(atual, ponta, .014, luz);
                geo.folha(ponta, .32 + ruido(n) * .08, a, -.35, n, luz);
            }
            anterior = atual;
        }
        if (chave.etapa < 3) return geo.finalizar();

        // Cada cerca da cobertura recebe ramos perto das duas travessas superiores.
        int limite = chave.etapa == 3 ? Math.max(1, (chave.cobertura.size() + 1) / 2)
                : chave.cobertura.size();
        List<BlockPos> usados = chave.cobertura.subList(0, limite);
        for (int i = 0; i < usados.size(); i++) {
            BlockPos pos = usados.get(i);
            if (!chave.proprios.contains(pos)) continue;
            Vec3 c = centro(chave, pos);
            int luz = luz(chave, pos);
            Vec3 centroRamo = c.add(.17, .83, .16);
            // Cruz de ramos contornando o poste. A cerca original continua à vista.
            geo.segmento(c.add(-.25, .84, .15), c.add(.25, .84, .15), .027, luz);
            geo.segmento(c.add(.15, .84, -.25), c.add(.15, .84, .25), .027, luz);
            for (BlockPos vizinho : List.of(pos.east(), pos.south())) {
                if (usados.contains(vizinho)) {
                    // A ponte pertence ao apoio inicial. O outro extremo pode ter
                    // outra raiz dona, mas ela não desenha esta mesma ponte de volta.
                    Vec3 v = centro(chave, vizinho);
                    Vec3 delta = v.subtract(c);
                    Vec3 inicio = centroRamo.add(delta.scale(.1));
                    Vec3 fim = v.add(.17, .83, .16).subtract(delta.scale(.1));
                    geo.segmento(inicio, inicio.lerp(fim, .5).add(0, .035, 0), .029, luz);
                    geo.segmento(inicio.lerp(fim, .5).add(0, .035, 0), fim, .029, luz);
                }
            }
            for (int n = 0; n < 7; n++) {
                double a = n * Math.PI * 2 / 7 + ruido(i + 11) * .7;
                double raio = .18 + ruido(i * 13 + n + 4) * .2;
                Vec3 folha = c.add(Math.cos(a) * raio, .91 + ruido(i * 7 + n) * .11,
                        Math.sin(a) * raio);
                geo.segmento(centroRamo, folha, .013, luz);
                geo.folha(folha, .37 + ruido(i * 17 + n) * .13, a,
                        Math.PI / 2 + (ruido(i + n + 14) - .5) * .35, i * 7 + n, luz);
            }
            if ((i % 3) == 0) geo.folha(c.add(.30, .68, -.20), .34, i, -.2, i + 53, luz);
        }
        return geo.finalizar();
    }

    private static double ruido(int n) {
        double v = Math.sin(n * 127.1 + 311.7) * 43758.5453123;
        return v - Math.floor(v);
    }

    private static final class Geometria {
        private final List<Quad> ramos = new ArrayList<>();
        private final List<Quad> folhas = new ArrayList<>();

        private void quad(List<Quad> destino, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                UV uv, int cor, int luz) {
            Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
            destino.add(new Quad(a, b, c, d, normal, uv, cor, luz));
        }

        private void segmento(Vec3 a, Vec3 b, double espessura, int luz) {
            Vec3 frente = b.subtract(a).normalize();
            if (frente.lengthSqr() < .001) return;
            Vec3 lado = frente.cross(Math.abs(frente.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0))
                    .normalize().scale(espessura / 2);
            Vec3 cima = frente.cross(lado).normalize().scale(espessura / 2);
            Vec3[] v = {a.add(lado).add(cima), a.subtract(lado).add(cima),
                    a.subtract(lado).subtract(cima), a.add(lado).subtract(cima),
                    b.add(lado).add(cima), b.subtract(lado).add(cima),
                    b.subtract(lado).subtract(cima), b.add(lado).subtract(cima)};
            quad(ramos, v[0], v[3], v[2], v[1], CASCA, 0xFFFFFFFF, luz);
            quad(ramos, v[4], v[5], v[6], v[7], CASCA, 0xFFFFFFFF, luz);
            for (int n = 0; n < 4; n++) {
                int j = (n + 1) % 4;
                quad(ramos, v[n], v[j], v[j + 4], v[n + 4], CASCA, 0xFFFFFFFF, luz);
            }
        }

        private void folha(Vec3 centro, double tamanho, double giro, double inclinacao, int indice, int luz) {
            Vec3 lado = new Vec3(Math.cos(giro), 0, Math.sin(giro)).scale(tamanho / 2);
            Vec3 cima = new Vec3(-Math.sin(giro) * Math.sin(inclinacao), Math.cos(inclinacao),
                    Math.cos(giro) * Math.sin(inclinacao)).scale(tamanho * .46);
            int cor = new int[] {0xFFFFFFFF, 0xFFE3F0D5, 0xFFEDF5DA, 0xFFCADDBB}[Math.floorMod(indice, 4)];
            quad(folhas, centro.subtract(lado).add(cima), centro.subtract(lado).subtract(cima),
                    centro.add(lado).subtract(cima), centro.add(lado).add(cima), FOLHA, cor, luz);
        }

        private Malha finalizar() { return new Malha(List.copyOf(ramos), List.copyOf(folhas)); }
    }

    private static void desenhar(List<Quad> quads, PoseStack.Pose pose, VertexConsumer vc) {
        for (Quad q : quads) {
            vertice(vc, pose, q.a, q.uv.u0, q.uv.v0, q);
            vertice(vc, pose, q.b, q.uv.u0, q.uv.v1, q);
            vertice(vc, pose, q.c, q.uv.u1, q.uv.v1, q);
            vertice(vc, pose, q.d, q.uv.u1, q.uv.v0, q);
        }
    }

    private static void vertice(VertexConsumer vc, PoseStack.Pose pose, Vec3 v, float u, float t, Quad q) {
        vc.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(q.cor).setUv(u, t)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.luz)
                .setNormal(pose, (float) q.normal.x, (float) q.normal.y, (float) q.normal.z);
    }
}
