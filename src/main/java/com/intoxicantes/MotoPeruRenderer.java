package com.intoxicantes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Geometria canônica própria, em 16 unidades/bloco, +Y para cima e frente -Z. */
public final class MotoPeruRenderer extends EntityRenderer<MotoPeruEntity, MotoPeruRenderer.Estado> {
    private static final Identifier MODELO = Identifier.fromNamespaceAndPath("intoxicantes", "models/entity/moto_peru.json");
    private static final float RAD = (float) (Math.PI / 180.0);
    private final List<No> raizes;

    public MotoPeruRenderer(EntityRendererProvider.Context contexto) {
        super(contexto);
        // EntityRenderDispatcher26.3 recria este renderer a cada reload/F3+T.
        // Nenhum mesh/material fica guardado em cache estático de um resource pack antigo.
        raizes = carregar(contexto.getResourceManager());
        shadowRadius = .65F;
        shadowStrength = .8F;
    }
    @Override public Estado createRenderState() { return new Estado(); }
    @Override public void extractRenderState(MotoPeruEntity entidade, Estado estado, float parcial) {
        super.extractRenderState(entidade, estado, parcial);
        estado.yaw = Mth.rotLerp(parcial, entidade.yRotO, entidade.getYRot());
        estado.rodaGraus = (float) Math.toDegrees(entidade.anguloRoda(parcial)) % 360F;
        estado.andando = entidade.emMovimento();
    }
    @Override protected AABB getBoundingBoxForCulling(MotoPeruEntity entidade, float parcial) {
        return entidade.getBoundingBox().inflate(1.4, .8, 1.4);
    }
    @Override public void submit(Estado estado, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!estado.isInvisible) {
            poses.pushPose();
            poses.rotateDegrees(Axis.YP, 180F - estado.yaw);
            for (No no : raizes) no.submit(estado, poses, collector);
            poses.popPose();
        }
        super.submit(estado, poses, collector, camera);
    }
    public static final class Estado extends EntityRenderState {
        float yaw, rodaGraus;
        boolean andando;
    }

    private static List<No> carregar(ResourceManager recursos) {
        try (Reader leitura = recursos.openAsReader(MODELO)) {
            JsonObject modelo = JsonParser.parseReader(leitura).getAsJsonObject();
            if (modelo.get("units_per_block").getAsFloat() != 16F)
                throw new IllegalArgumentException("Moto deve usar 16 unidades por bloco");
            Map<String, No> nos = new LinkedHashMap<>();
            for (JsonElement entrada : modelo.getAsJsonArray("groups")) {
                JsonObject grupo = entrada.getAsJsonObject();
                String nome = grupo.get("name").getAsString();
                if (nos.putIfAbsent(nome, new No(nome, vetor(grupo, "origin"), vetor(grupo, "rotation"))) != null)
                    throw new IllegalArgumentException("Grupo duplicado: " + nome);
            }
            if (!nos.keySet().containsAll(Set.of("chassis", "direcao", "roda_frente", "roda_tras", "cavalete")))
                throw new IllegalArgumentException("Articulações obrigatórias ausentes na moto");
            List<No> raizes = new ArrayList<>();
            for (JsonElement entrada : modelo.getAsJsonArray("groups")) {
                JsonObject grupo = entrada.getAsJsonObject();
                No no = nos.get(grupo.get("name").getAsString());
                JsonElement pai = grupo.get("parent");
                if (pai == null || pai.isJsonNull()) {
                    raizes.add(no);
                    no.deslocamento.set(no.origem).div(16F);
                } else {
                    No superior = nos.get(pai.getAsString());
                    if (superior == null || superior == no) throw new IllegalArgumentException("Pai inválido: " + pai);
                    superior.filhos.add(no);
                    no.deslocamento.set(no.origem).sub(superior.origem).div(16F);
                }
            }
            Set<No> visitados = new HashSet<>();
            for (No raiz : raizes) conferirArvore(raiz, visitados);
            if (visitados.size() != nos.size()) throw new IllegalArgumentException("Ciclo ou grupo sem raiz na moto");
            Set<String> nomesCubos = new HashSet<>();
            for (JsonElement entrada : modelo.getAsJsonArray("cubes")) {
                JsonObject cubo = entrada.getAsJsonObject();
                if (!nomesCubos.add(cubo.get("name").getAsString())) throw new IllegalArgumentException("Cubo duplicado");
                No no = nos.get(cubo.get("group").getAsString());
                if (no == null) throw new IllegalArgumentException("Cubo aponta para grupo inexistente");
                String material = cubo.get("material").getAsString();
                if (!material.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Material inválido: " + material);
                modelarCubo(no.geometria.computeIfAbsent(material, ignorado -> new ArrayList<>()), cubo, no.origem);
            }
            if (nomesCubos.isEmpty()) throw new IllegalArgumentException("Moto sem geometria");
            for (No no : nos.values()) no.finalizar();
            return List.copyOf(raizes);
        } catch (IOException | RuntimeException erro) {
            throw new IllegalStateException("Não foi possível carregar o modelo canônico da moto do Peru: " + MODELO, erro);
        }
    }
    private static void conferirArvore(No no, Set<No> visitados) {
        if (!visitados.add(no)) throw new IllegalArgumentException("Ciclo na articulação: " + no.nome);
        for (No filho : no.filhos) conferirArvore(filho, visitados);
    }
    private static Vector3f vetor(JsonObject objeto, String chave) {
        JsonArray valores = objeto.getAsJsonArray(chave);
        if (valores == null) return new Vector3f();
        if (valores.size() != 3) throw new IllegalArgumentException("Vetor inválido: " + chave);
        Vector3f vetor = new Vector3f(valores.get(0).getAsFloat(), valores.get(1).getAsFloat(), valores.get(2).getAsFloat());
        if (!Float.isFinite(vetor.x) || !Float.isFinite(vetor.y) || !Float.isFinite(vetor.z))
            throw new IllegalArgumentException("Coordenada não finita: " + chave);
        return vetor;
    }
    private static Quaternionf rotacao(Vector3f graus) {
        return new Quaternionf().rotationZYX(graus.z * RAD, graus.y * RAD, graus.x * RAD);
    }
    private static void modelarCubo(List<Vertice> vertices, JsonObject cubo, Vector3f origemGrupo) {
        Vector3f inicio = vetor(cubo, "from"), fim = vetor(cubo, "to");
        if (inicio.x >= fim.x || inicio.y >= fim.y || inicio.z >= fim.z)
            throw new IllegalArgumentException("Cubo sem volume: " + cubo.get("name"));
        Vector3f pivo = cubo.has("origin") ? vetor(cubo, "origin") : new Vector3f(inicio).add(fim).mul(.5F);
        Quaternionf orientacao = rotacao(vetor(cubo, "rotation"));
        float x0 = inicio.x, y0 = inicio.y, z0 = inicio.z, x1 = fim.x, y1 = fim.y, z1 = fim.z;
        face(vertices, new float[][]{{x1,y1,z1},{x1,y1,z0},{x1,y0,z0},{x1,y0,z1}}, 1,0,0,pivo,origemGrupo,orientacao);
        face(vertices, new float[][]{{x0,y1,z0},{x0,y1,z1},{x0,y0,z1},{x0,y0,z0}}, -1,0,0,pivo,origemGrupo,orientacao);
        face(vertices, new float[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}}, 0,1,0,pivo,origemGrupo,orientacao);
        face(vertices, new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}}, 0,-1,0,pivo,origemGrupo,orientacao);
        face(vertices, new float[][]{{x0,y1,z1},{x1,y1,z1},{x1,y0,z1},{x0,y0,z1}}, 0,0,1,pivo,origemGrupo,orientacao);
        face(vertices, new float[][]{{x1,y1,z0},{x0,y1,z0},{x0,y0,z0},{x1,y0,z0}}, 0,0,-1,pivo,origemGrupo,orientacao);
    }
    private static void face(List<Vertice> vertices, float[][] cantos, float nx, float ny, float nz,
                             Vector3f pivo, Vector3f origemGrupo, Quaternionf orientacao) {
        Vector3f normal = new Vector3f(nx, ny, nz).rotate(orientacao);
        // 0,3,2,1 produz a face frontal anti-horária correspondente à normal externa.
        // UV0..1 mantém os mesmos materiais/rotações do estúdio, sem escala de atlas implícita.
        for (int i : new int[]{0, 3, 2, 1}) {
            float[] c = cantos[i];
            Vector3f p = new Vector3f(c[0], c[1], c[2]).sub(pivo).rotate(orientacao).add(pivo).sub(origemGrupo).div(16F);
            vertices.add(new Vertice(p.x, p.y, p.z, i == 1 || i == 2 ? 1F : 0F, i >= 2 ? 1F : 0F,
                    normal.x, normal.y, normal.z));
        }
    }
    private record Vertice(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        void emitir(PoseStack.Pose pose, VertexConsumer buffer, int luz, int cor) {
            buffer.addVertex(pose, x, y, z).setColor(cor).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(luz).setNormal(pose, nx, ny, nz);
        }
    }
    private record Malha(RenderType tipo, RenderType contorno, List<Vertice> vertices) {
        void submit(PoseStack poses, SubmitNodeCollector collector, int luz, int corContorno) {
            collector.submitCustomGeometry(poses, tipo, (pose, buffer) -> {
                for (Vertice v : vertices) v.emitir(pose, buffer, luz, -1);
            });
            if (corContorno != 0) collector.submitCustomGeometry(poses, contorno, (pose, buffer) -> {
                for (Vertice v : vertices) v.emitir(pose, buffer, luz, corContorno);
            });
        }
    }
    private static final class No {
        final String nome;
        final Vector3f origem, repouso, deslocamento = new Vector3f();
        final Quaternionf rotacaoRepouso;
        final List<No> filhos = new ArrayList<>();
        Map<String, List<Vertice>> geometria = new LinkedHashMap<>();
        List<Malha> malhas;
        No(String nome, Vector3f origem, Vector3f repouso) {
            this.nome = nome; this.origem = origem; this.repouso = repouso;
            rotacaoRepouso = rotacao(repouso);
        }
        void finalizar() {
            List<Malha> prontas = new ArrayList<>();
            geometria.forEach((material, vertices) -> {
                Identifier textura = Identifier.fromNamespaceAndPath("intoxicantes", "textures/entity/moto_peru/" + material + ".png");
                prontas.add(new Malha(RenderTypes.entitySolid(textura), RenderTypes.outline(textura), List.copyOf(vertices)));
            });
            malhas = List.copyOf(prontas);
            geometria = null;
        }
        void submit(Estado estado, PoseStack poses, SubmitNodeCollector collector) {
            poses.pushPose();
            poses.translate(deslocamento.x, deslocamento.y, deslocamento.z);
            float x = repouso.x;
            if (nome.equals("roda_frente") || nome.equals("roda_tras")) x -= estado.rodaGraus;
            if (nome.equals("cavalete") && estado.andando) x -= 82F;
            if (x == repouso.x) poses.rotate(rotacaoRepouso);
            else poses.rotate(new Quaternionf().rotationZYX(repouso.z * RAD, repouso.y * RAD, x * RAD));
            for (Malha malha : malhas) malha.submit(poses, collector, estado.lightCoords, estado.outlineColor);
            for (No filho : filhos) filho.submit(estado, poses, collector);
            poses.popPose();
        }
    }
}
