package com.intoxicantes;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;

/**
 * Parte client do mod: renderer dos NPCs.
 * v1.2.40: os tres (traficante, Gago e Juca) usam o CORPO DE PLAYER de
 * verdade — HumanoidModel/HumanoidMobRenderer, o mesmo caminho do zumbi
 * vanilla. O item na mao direita renderiza pelo ItemInHandLayer que o
 * proprio HumanoidMobRenderer adiciona. As skins sao steve 64x64
 * (tools/converte_skins_player.py converte o art antigo).
 * v1.2.44: o layer de bake e o ModelLayers.PLAYER (mesh steve 64x64,
 * bracos 4px com left_arm em texOffs(32,48) SEM mirror — o layout que as
 * skins pintam). NAO usar SKELETON: o mesh dele e 64x32 de braco fino 2px
 * com membro esquerdo espelhado, e o braco esquerdo fica invisivel.
 */
public class IntoxicantesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // partículas proprias do mod (atlas + providers, antes do primeiro render)
        ParticulasClient.init();

        // v1.2.33: as armas em nivel gun mod (a 12 e o .38) — kick de camera,
        // ADS (zoom) e HUD do mecanismo, tudo num lugar so
        ArmasClient.init();

        // v1.2.53: o relógio de sempre no HUD (canto superior direito, discreto)
        RelogioHud.init();
        CabosClient.init();

        // v1.2.54: a SAÚDE do fregues — HUD de sede/vício, overlays das viagens,
        // alucinações (Gago gigante) e a tecla H do prontuário
        SaudeVisionClient.init();
        SaudeClient.init();

        EntityRendererRegistry.register(IntoxicantesMod.BEBIDA_DECORATIVA, BebidaDecorativaRenderer::new);
        EntityRendererRegistry.register(IntoxicantesMod.CACHO_PARREIRA, NoopRenderer::new);
        EntityRendererRegistry.register(IntoxicantesMod.TRAFICANTE, TraficanteRenderer::new);
        EntityRendererRegistry.register(IntoxicantesMod.GAGO, GagoRenderer::new);
        // v1.2.39: o JUÇA — mesmo corpo de barrigão do Gago, skin própria
        // (camisa preta do Matanza, cabelo comprido, Camel na boca)
        EntityRendererRegistry.register(IntoxicantesMod.JUCA, JucaRenderer::new);
        EntityRendererRegistry.register(IntoxicantesMod.PERU, PeruRenderer::new);
        EntityRendererRegistry.register(IntoxicantesMod.MOTO_PERU, MotoPeruRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                PeruNetworking.Estado.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
                    if (ctx.player() == null) return;
                    if (ctx.client().gui.screen() instanceof PeruScreen tela
                            && tela.sessao().equals(payload.sessao())) tela.atualizar(payload);
                    else if (payload.abrir() && payload.valido()) ctx.client().gui.setScreen(new PeruScreen(payload));
                }));

        // v1.2.18: o LETREIRO DE LED do Esquinão (placa custom, texto por código)
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.PLACA_ESQUINAO_ENTITY, PlacaEsquinaoRenderer::new);

        // v1.2.36: o PAINEL DE LED craftável (mesma fonte, cor/brilho do NBT)
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.PAINEL_LED_ENTITY, PainelLedRenderer::new);

        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.PARREIRA_ENTITY, ParreiraRenderer::new);

        // v1.2.55: AS MÁQUINAS VIVAS — líquido subindo, serpentina esquentando,
        // rolo girando, espuma no barril. Máquina ociosa = custo zero (o
        // early-out é na primeira linha do submit).
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.DORNA_BEBIDA_ENTITY, MaquinasVivasRenderer.DornaRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.ALAMBIQUE_ENTITY, MaquinasVivasRenderer.AlambiqueRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.MOENDA_CANA_ENTITY, MaquinasVivasRenderer.PrimaRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.PRENSA_UVAS_ENTITY, MaquinasVivasRenderer.PrimaRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.CALDEIRAO_MOSTURA_ENTITY, MaquinasVivasRenderer.PrimaRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.BARRIL_BEBIDA_ENTITY, MaquinasVivasRenderer.BarrilRenderer::new);

        // v1.2.69: a PRATELEIRA DE MERCADO — os produtos da gôndola na frente
        // da estanteria (a receita do Campfire 26.3: resolver + submit FIXED)
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.PRATELEIRA_MERCADO_ENTITY, PrateleiraMercadoRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                IntoxicantesMod.CAIXA_MERCADO_ENTITY, CaixaMercadoRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                PrateleiraNetworking.CarrinhoResumoPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> CaixaMercadoRenderer.atualizarResumo(payload)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                PrateleiraNetworking.AbrirCompraPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (ctx.player() != null) {
                        if (ctx.client().gui.screen() instanceof PrateleiraCompraScreen caixa) caixa.atualizar(payload);
                        else ctx.client().gui.setScreen(new PrateleiraCompraScreen(payload));
                    }
                }));

        // v1.2.59: A GUI DAS MÁQUINAS — uma tela pros seis tipos (o classtweaker
        // do fabric-menu-api-v1 abre o registro vanilla do MenuScreens).
        // O payload (TipoMaquina) chega pelo ExtendedMenuType; slots/progresso
        // chegam pelo sync vanilla do menu.
        net.minecraft.client.gui.screens.MenuScreens.register(
                IntoxicantesMod.MENU_MAQUINA_SNC, TelaMaquinaBase::new);

        // ==================================================== PONTO DO TRAFICANTE (v1.2.44)
        // S2C: o traficante mandou o estado do ponto -> abre a tela dele.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .registerGlobalReceiver(EsquinaoNetworking.AbrirPontoPayload.TYPE,
                        (payload, ctx) -> ctx.client().execute(() -> {
                            if (ctx.player() != null) {
                                if (ctx.client().gui.screen() instanceof PontoTraficanteScreen atual) {
                                    atual.atualizar(payload);
                                } else if (payload.abrir()) {
                                    ctx.client().gui.setScreen(new PontoTraficanteScreen(payload));
                                }
                            }
                        }));

        // ==================================================== CARDAPIO DO ESQUINAO
        // S2C: o Gago mandou o cardapio -> abre a tela custom (em tick de client).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .registerGlobalReceiver(EsquinaoNetworking.AbrirCardapioPayload.TYPE,
                        (payload, ctx) -> ctx.client().execute(() -> {
                            if (ctx.player() != null) {
                                if (ctx.client().gui.screen() instanceof EsquinaoCardapioScreen current) {
                                    current.atualizar(payload);
                                } else if (payload.abrir()) {
                                    ctx.client().gui.setScreen(new EsquinaoCardapioScreen(payload));
                                }
                            }
                        }));
        // S2C: o servidor derrubou a sessao (fregues sumiu do balcao) -> fecha a tela.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .registerGlobalReceiver(EsquinaoNetworking.FecharCardapioPayload.TYPE,
                        (payload, ctx) -> ctx.client().execute(() -> {
                            if (ctx.client().gui.screen()
                                    instanceof EsquinaoCardapioScreen) {
                                ctx.client().gui.setScreen(null);
                            }
                        }));

        // v1.2.36: S2C da CENTRAL DE COMANDO -> abre a tela de edição do painel
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .registerGlobalReceiver(CentralComandoNetworking.AbrirCentralPayload.TYPE,
                        (payload, ctx) -> ctx.client().execute(() -> {
                            if (ctx.player() != null) {
                                ctx.client().gui.setScreen(new CentralComandoScreen(payload));
                            }
                        }));
                // v1.2.71: a GUI do QUADRO ELÉTRICO (o S2C manda o estado e abre)
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                        .registerGlobalReceiver(QuadroEletricoNetworking.AbrirQuadroPayload.TYPE,
                                (payload, ctx) -> ctx.client().execute(() -> {
                                    if (ctx.player() != null) {
                                        if (ctx.client().gui.screen()
                                                instanceof QuadroEletricoScreen atual) {
                                            atual.atualizar(payload);
                                        } else if (payload.abrirTela()) {
                                            ctx.client().gui.setScreen(
                                                    new QuadroEletricoScreen(payload));
                                        }
                                    }
                                }));

    }

    /** Traficante: hood cinza-escuro, oculos e corrente (kkkk) — corpo de player. */
    private static class TraficanteRenderer
            extends HumanoidMobRenderer<TraficanteEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
        TraficanteRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
            super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        }

        @Override
        public HumanoidRenderState createRenderState() {
            return new HumanoidRenderState();
        }

        /**
         * v1.2.43 — o .38 na mao ANIMADA: o renderer humanoide padrao devolve
         * pose EMPTY (so detecta lanca), entao o braco ficaria balançando solto
         * com a arma pendurada. Aqui o braco direito entra na pose ITEM (postura
         * de quem carrega um item, igual player vanilla) e, quando ele fica
         * agressivo, na pose de mira do arqueiro (os DOIS bracos apontam junto
         * com a cabeca — mira de two hands kkkk). O desenho do item na mao
         * continua por conta do ItemInHandLayer (o renderer adiciona sozinho).
         */
        @Override
        protected HumanoidModel.ArmPose getArmPose(TraficanteEntity traficante,
                HumanoidArm braco) {
            if (braco == HumanoidArm.RIGHT) {
                return traficante.isAggressive()
                        ? HumanoidModel.ArmPose.BOW_AND_ARROW // mira dois bracos
                        : HumanoidModel.ArmPose.ITEM;         // carregando o .38
            }
            return super.getArmPose(traficante, braco);
        }

        @Override
        public Identifier getTextureLocation(HumanoidRenderState state) {
            return Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "textures/entity/traficante.png");
        }
    }

    /**
     * v1.2.39 — O JUÇA: mesmo corpo de player do Gago (v1.2.40), skin
     * propria convertida: cabelo comprido preto, camisa do Matanza,
     * Camel pendurado na boca. Doidão = cara de raiva (cabeca treme).
     */
    private static class JucaRenderer
            extends HumanoidMobRenderer<JucelinoEntity, GagoRenderState, GagoHumanoidModel> {
        JucaRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
            super(ctx, new GagoHumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.6F);
        }

        @Override
        public GagoRenderState createRenderState() {
            return new GagoRenderState();
        }

        @Override
        public void extractRenderState(JucelinoEntity entidade, GagoRenderState state,
                float parcial) {
            super.extractRenderState(entidade, state, parcial);
            state.roupa = 0;
            state.puto = entidade.estaDoidao(); // doidão = cara de raiva kkkk
        }

        @Override
        public Identifier getTextureLocation(GagoRenderState state) {
            return Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "textures/entity/juca.png");
        }
    }

    /** Render state do Gago/Juca: fantasia (bioma) + modo PUTO + fantasma. */
    private static class GagoRenderState extends HumanoidRenderState {
        public int roupa;
        public boolean puto;
        /** v1.2.54: o Gago FANTASMA da viagem de LSD — desenha 4x. */
        public boolean fantasma;
    }

    /**
     * HumanoidModel com o toque do Esquinão: quando o PUTO esta ligado,
     * a cabeca treme de raiva (o mesmo canal do isUnhappy do villager).
     */
    private static class GagoHumanoidModel extends HumanoidModel<GagoRenderState> {
        GagoHumanoidModel(ModelPart raiz) {
            super(raiz);
        }

        @Override
        public void setupAnim(GagoRenderState state) {
            super.setupAnim(state);
            this.head.zRot = state.puto
                    ? Mth.sin(state.ageInTicks * 0.45F) * 0.3F
                    : 0.0F;
        }
    }

    /**
     * Gago: corpo de player (v1.2.40) + fantasia tematica do bioma do
     * mercado + escopeta na mao (kkkk). O indice da roupa chega sincronizado
     * (DATA_ROUPA no servidor) e escolhe a skin direto — as 7 variantes sao
     * PNGs steve 64x64 (convertidos por tools/converte_skins_player.py).
     */
    private static class GagoRenderer
            extends HumanoidMobRenderer<GagoEntity, GagoRenderState, GagoHumanoidModel> {
        private static final String[] NOME_ROUPA = {
                "gago", "gago_sertao", "gago_mata", "gago_cerrado",
                "gago_sul", "gago_serra", "gago_brejo"
        };

        GagoRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
            super(ctx, new GagoHumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.6F);
        }

        @Override
        public GagoRenderState createRenderState() {
            return new GagoRenderState();
        }

        @Override
        public void extractRenderState(GagoEntity entidade, GagoRenderState state,
                float parcial) {
            super.extractRenderState(entidade, state, parcial);
            state.roupa = Math.floorMod(entidade.getRoupa(), GagoEntity.ROUPAS);
            state.puto = entidade.isPuto();
            // v1.2.54: o Gago FANTASMA da viagem (SaudeVisionClient) desenha 4x
            state.fantasma = entidade.entityTags().contains(SaudeVisionClient.TAG_FANTASMA);
        }

        @Override
        protected void scale(GagoRenderState state, com.mojang.blaze3d.vertex.PoseStack pose) {
            if (state.fantasma) {
                // o mercadão no céu: 4x o corpo (quase o letreiro deitado kkkk)
                pose.scale(4.0F, 4.0F, 4.0F);
            }
        }

        @Override
        public Identifier getTextureLocation(GagoRenderState state) {
            String nome = NOME_ROUPA[state.roupa % NOME_ROUPA.length];
            return Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID,
                    "textures/entity/" + nome + ".png");
        }
    }
}
