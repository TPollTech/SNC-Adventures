package com.intoxicantes;

import java.text.Normalizer;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jspecify.annotations.Nullable;

/**
 * O Gago: dono do Mercado Esquinão, vendedor de bebidas e mercadorias da esquina.
 * Atende 24h! De 07:00~00:00 fica no balcão, de 00:00~07:00 vai pra porta.
 * Chama ele de "gago" no chat e ele FICA PUTO e te expulsa do mercado kkkk
 */
public class GagoEntity extends AbstractVillager {
    // ==================================================== FANTASIA POR BIOMA
    // 7 roupas tematicas (ordem = indice do dado sincronizado; mesmos arquivos
    // que o gen_npc_textures.py gera). Mesma cara bege, muda o tecido: identidade
    // do Rei do Bar em qualquer esquina do mundo.
    public static final int ROUPA_PLAINS = 0;   // camisa vermelha classica
    public static final int ROUPA_SERTAO = 1;   // deserto/badlands: couro e po
    public static final int ROUPA_MATA = 2;     // selva: verde-mata + flor
    public static final int ROUPA_CERRADO = 3;  // savanna: ocra do capim dourado
    public static final int ROUPA_SUL = 4;      // neve: casaco azul-petroleo (SEDE MATRIZ)
    public static final int ROUPA_SERRA = 5;    // taiga/montanha: verde-pinho + la
    public static final int ROUPA_BREJO = 6;    // swamp: marrom-pantano
    public static final int ROUPAS = 7;
    private static final EntityDataAccessor<Integer> DATA_ROUPA =
            SynchedEntityData.defineId(GagoEntity.class, EntityDataSerializers.INT);

    /**
     * v1.2.19: O MERCADO TÁ ABERTO? Synched pro client (a UI de negociação
     * vive no client e precisa saber se o portão tá fechado). O server decide
     * pela MESMA regra do letreiro: 07:00 ~ 00:00 aberto.
     */
    private static final EntityDataAccessor<Boolean> DATA_ABERTO =
            SynchedEntityData.defineId(GagoEntity.class, EntityDataSerializers.BOOLEAN);

    /** v1.2.25: o Gago COCHILA fora do expediente — frase embolada de sono. */
    static final String[] FRASES_COCHILO = {
            "entity.intoxicantes.gago.cochilo.1",
            "entity.intoxicantes.gago.cochilo.2",
            "entity.intoxicantes.gago.cochilo.3"
    };
    /** v1.2.25: o DONO DA ESQUINA (fidelidade tier 3) tem saudação própria. */
    static final String[] FRASES_VIP = {
            "entity.intoxicantes.gago.vip.1",
            "entity.intoxicantes.gago.vip.2",
            "entity.intoxicantes.gago.vip.3"
    };
    /** v1.2.25: cochilo fora do expediente — Zzz e frase de sono (não é fila). */
    private boolean cochilando;
    private int cooldownCochilo;
    private long proximoZzz;

    /** Primeiro tick depois do spawn: detecta a roupa pelo bioma (uma vez so). */
    private boolean roupaDefinida;

    static final String[] FRASES_CHEGADA = {
            "entity.intoxicantes.gago.chegada.1",
            "entity.intoxicantes.gago.chegada.2",
            "entity.intoxicantes.gago.chegada.3",
            "entity.intoxicantes.gago.chegada.4",
            "entity.intoxicantes.gago.chegada.5"
    };
    static final String[] FRASES_BRAVO = {
            "entity.intoxicantes.gago.bravo.1",
            "entity.intoxicantes.gago.bravo.2",
            "entity.intoxicantes.gago.bravo.3",
            "entity.intoxicantes.gago.bravo.4"
    };
    static final String[] FRASES_MORTE = {
            "entity.intoxicantes.gago.morte.1",
            "entity.intoxicantes.gago.morte.2"
    };
    static final String[] FRASES_AMIGAVEL = {
            "entity.intoxicantes.gago.amigavel.1",
            "entity.intoxicantes.gago.amigavel.2",
            "entity.intoxicantes.gago.amigavel.3"
    };
    /** Quem le o letreiro ganha um cumprimento do dono da esquina. */
    static final String[] FRASES_LETREIRO = {
            "entity.intoxicantes.gago.letreiro.1",
            "entity.intoxicantes.gago.letreiro.2",
            "entity.intoxicantes.gago.letreiro.3"
    };
    /** Primeiro aviso de vandalismo: fala, nao bala. */
    static final String[] FRASES_ADVERTENCIA = {
            "entity.intoxicantes.gago.advertencia.1",
            "entity.intoxicantes.gago.advertencia.2",
            "entity.intoxicantes.gago.advertencia.3"
    };
    /** Pedrada no mercado: raiva curta, mas com chumbo. */
    static final String[] FRASES_PEDRADA = {
            "entity.intoxicantes.gago.pedrada.1",
            "entity.intoxicantes.gago.pedrada.2",
            "entity.intoxicantes.gago.pedrada.3"
    };
    /** Fumaca de baseado perto do balcão: o dono da esquina DETESTA. */
    static final String[] FRASES_FUMACA = {
            "entity.intoxicantes.gago.fumaca.1",
            "entity.intoxicantes.gago.fumaca.2",
            "entity.intoxicantes.gago.fumaca.3"
    };
    /** Fregues cruzou a linha da borracharia: o Gago debocha (com carinho). */
    static final String[] FRASES_BORRACHARIA = {
            "entity.intoxicantes.gago.borracharia.1",
            "entity.intoxicantes.gago.borracharia.2",
            "entity.intoxicantes.gago.borracharia.3"
    };

    // v1.2.44 — MERCADO 24H: o dono da esquina não fecha mais. Chegou a hora
    // que antes era de dormir, ele arruma a cara e levanta a bandeira de novo
    // (meio dia a madrugada é quando a esquina mais ferve kkkk).

    private boolean anunciouChegada;
    private boolean putoDaCara;
    private boolean sociavel;
    private int raivaTicks;
    int cooldownTiro; // package-private: o game test calibra/config verifica
    /** Throttle do cumprimento do letreiro (1x a cada 8s por Gago). */
    private int cooldownLetreiro;
    /** Throttle da reacao a pedrada (1 msg a cada 5s enquanto ja ta puto). */
    private int cooldownPedrada;
    /** Throttle da reclamacao contra fumaca de baseado no balcão (6s entre falas). */
    int cooldownFumaca; // package-private: o game test simula a expiracao
    /** Quantas conversas de fumaca o mesmo fregues ja levou NESTE uso do baseado. */
    int fumacaConversas; // package-private: o game test verifica o escalonamento
    /** Throttle da zoeira pro fregues bêbado (30s entre deboches). */
    int cooldownZoacao; // package-private: o game test simula a expiracao

    /** Posto fixo no Mercado Esquinão: NoAI enquanto trabalha, solto quando fica puto. */
    private boolean emPostoMercado;
    private net.minecraft.core.BlockPos posPostoMercado;

    /**
     * O MarketSystem chama isso a cada passada de gerenciamento: trava o Gago no posto
     * (sem AI, nao anda pro nada) ou libera a IA se ele tiver raiva ativa.
     */
    public void setupPostoMercado(net.minecraft.core.BlockPos posto) {
        this.posPostoMercado = posto.immutable();
        if (this.raivaTicks > 0 || this.getTarget() != null) {
            this.emPostoMercado = false;
            this.setNoAi(false);
        } else {
            this.emPostoMercado = true;
            this.setNoAi(true);
        }
    }

    /**
     * v1.2.36 — O DETECTOR DE SUFOCAMENTO: true se o corpo do Gago está
     * INTERSECTANDO blocos sólidos (cabeça dentro de parede/barril). O
     * gerente do mercado (MarketSystem.gerenciarGago) consulta a cada
     * passada: Gago preso não é reposto no lugar — o POSTO é limpo antes,
     * e quebra o loop de morte que enchia a loja de drop.
     */
    public boolean estaPresoEmBloco() {
        if (this.noPhysics) return false;
        return !this.level().noCollision(this, this.getBoundingBox());
    }

    /**
     * v1.2.51 — ÂNCORA DUPLA: true se o corpo dele está parado no posto que
     * o gerenciador marca (o “plantão” — balcão de dia, guichê de madrugada).
     * O gerenciador usa pra saber se precisa reancorar: mesmo que o NoAI
     * tenha sido solto por um save velho, o corpo no lugar certo conta.
     */
    public boolean estaDePlantao() {
        return this.emPostoMercado && this.posPostoMercado != null
                && this.blockPosition().distSqr(this.posPostoMercado) < 4.0;
    }

    public GagoEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        // SEM arma por padrao: e o mercado, nao oFront. Ele SACA a 12 (enraivecer)
        // so quando ofendem ele, e guarda de volta quando a raiva passa (acalmar/tick)
        // v1.2.76 — O NOME NA CABEÇA: "Gago" em cima, igual o nome que os
        // jogadores veem nos outros (a chave de lang da entidade, traduzida).
        this.garantirNomeNaCabeca();
    }

    /**
     * v1.2.76: o nome do dono da esquina é PADRÃO — e precisa ser reposto ao
     * CARREGAR, porque o `Entity.load` do vanilla zera o CustomName quando a
     * tag não está no NBT (template, ovo, save). Nome de comando/jogador
     * (que vem no NBT) nunca é sobrescrito.
     */
    private void garantirNomeNaCabeca() {
        if (this.getCustomName() == null) {
            this.setCustomName(Component.translatable("entity.intoxicantes.gago"));
        }
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    private final MarketInventory tradeStock = new MarketInventory();

    void refreshTradeStock() {
        if (this.level() instanceof ServerLevel server) {
            tradeStock.refresh(MarketSystem.tempoTotal(server.getServer().overworld()), this.isTrading(), this.offers);
        }
    }

    MarketInventory marketInventory() { return tradeStock; }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand mao) {
        // PUTO nao atende cliente: ta la com a 12 na mao, nao vai abrir cardapio
        if (this.isPuto()) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        // v1.2.44 — MERCADO 24H: sem portão, sem recusa. O dono da esquina
        // não manda fregues embora nem de madrugada (era a fila do 1.2.19).
        // mesmo padrao do WanderingTrader: botao direito abre o cardapio (e nao e' negocinho de ovo)
        ItemStack naMao = player.getItemInHand(mao);
        if (!naMao.is(Items.VILLAGER_SPAWN_EGG) && !naMao.is(IntoxicantesMod.OVO_GAGO)
                && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (mao == net.minecraft.world.InteractionHand.MAIN_HAND) {
                player.awardStat(net.minecraft.stats.Stats.TALKED_TO_VILLAGER);
            }
            if (!this.level().isClientSide()) {
                // Cardapio PROPRIO do Esquinao (payload + tela custom), nao a UI de
                // vilarejo vanilla. A sessao congela o Gago no balcao e o watchdog
                // do servidor derruba se o fregues sumir.
                EsquinaoNetworking.iniciarSessao((ServerPlayer) player, this);
                return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, mao);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData resultado = super.finalizeSpawn(accessor, difficulty, reason, data);
        refreshTradeStock();
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
            this.setPersistenceRequired();
        }
        return resultado;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distancia) {
        // v1.2.76: o critério NÃO é mais "tem nome" (todo Gago tem o nome na
        // cabeça agora) e sim "está de plantão": o dono do mercado nunca some
        // com a chunk longe; um Gago solto longe do Esquinão continua sumindo.
        return distancia > 64.0 && !this.isPersistenceRequired() && !this.emPostoMercado;
    }

    /**
     * v1.2.30 — O GAGO NÃO SUFFOCA NO POSTO: o gerenciador o teleporta para
     * o posto do plantão; se um bloco nasceu lá (reforma de pele, plantio
     * velho, offset de estrutura girada), o vanilla o matava sufocado
     * (IN_WALL) — e o gerenciador nasce outro no MESMO lugar: ciclo
     * "o Gago some e o mercado fica cheio de cachaça/R$/cartucho no chão".
     * Em serviço, dano de parede/empilhamento é IGNORADO (isInvulnerableToBase
     * é final no 26.3 — a imunidade mora no hurtServer). v1.2.46: dano de
     * JOGADOR acorda ele (ver acima) — só o ambient continua blindado.
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source, float quantidade) {
        // em serviço: parede e empilhamento não machucam (o posto é seguro)
        if (this.emPostoMercado && this.isAlive()
                && (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))) {
            return false;
        }
        // v1.2.46 — O TAPA ACORDA O VENDEDOR: dano direto de JOGADOR em serviço
        // não pode quicar — com NoAI ele nem reagia via HurtByTargetGoal, e o
        // playtest leu "não to conseguindo bater no gago" (impossível de saber
        // se o hit valeu). O tapa tira ele do plantão, solta a IA e ele saca a
        // 12 pra cobrar a educação. Parede/sufocamento segue ignorado (acima).
        if (this.isAlive()
                && source.getEntity() instanceof Player autor
                && !autor.getAbilities().instabuild // criativo é obra, nao vandalismo
                && (this.emPostoMercado || this.raivaTicks < 0)) {
            this.emPostoMercado = false;
            this.getNavigation().stop();
            // mesmo canal do vandalismo (advertência -> perseguição com a 12,
            // SEM expulsar o fregues do mercado — senão ninguém consegue
            // medir forças com ele nunca)
            naPedrada(level, autor);
        }
        // defesa em profundidade: ferimento FATAL no posto (explosão, etc.) —
        // solta a IA em vez de morrer parado dentro do bloco: ele escapa andando
        if (this.emPostoMercado && this.getHealth() - quantidade <= 0.0F) {
            this.emPostoMercado = false;
            this.setNoAi(false);
            this.getNavigation().stop();
        }
        return super.hurtServer(level, source, quantidade);
    }

    /**
     * v1.2.30 — AUTOCURA DE SAVE VELHO: Gago de mundo 1.2.18~1.2.29 sem a
     * marca de persistência despawnava a 64+ blocos ("o Gago simplesmente
     * some"). No primeiro tick de server, vira permanente.
     */
    private boolean persistenciaGarantida;

    public boolean isPuto() {
        return this.isAggressive();
    }

    /** v1.2.19: o mercado tá no horário de atendimento (07:00 ~ 00:00)? */
    public boolean isMercadoAberto() {
        return this.entityData.get(DATA_ABERTO);
    }

    /** v1.2.19: server manda o estado do portão (synched — a UI lê no client). */
    public void setMercadoAberto(boolean aberto) {
        this.entityData.set(DATA_ABERTO, aberto);
    }

    @Override
    protected void updateTrades(ServerLevel level) { refreshTradeStock(); }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        this.overrideXp(this.getVillagerXp() + offer.getXp());
    }

    /** Chamado quando o mercado abre: Gago se apresenta pro player mais proximo. */
    public void anunciarChegada(ServerLevel level, Player player) {
        if (this.anunciouChegada) return;
        this.anunciouChegada = true;
        player.sendSystemMessage(Component.translatable(
                FRASES_CHEGADA[this.random.nextInt(FRASES_CHEGADA.length)],
                this.getName(), player.getName()));
    }

    /**
     * Chamado quando o Gago é resgatado de volta ao BALCÃO (v1.2.76: ele não
     * muda mais de posto com o relógio — o guichê da porta foi extinto).
     */
    public void anunciarRetornoAoBalcao(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(this.blockPosition()) < 32 * 32) {
                player.sendSystemMessage(Component.literal(
                        "§eO Gago arruma o balcão e volta pro plantão."));
            }
        }
    }

    // ==================================================== O SISTEMA DO "GAGO" kkkk

    public void ouvirChat(ServerPlayer speaker, String mensagem) {
        String normalizada = Normalizer.normalize(mensagem, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        if (normalizada.contains("gago")) {
            this.putoDaCara = true;
            this.sociavel = false;
            enraivecer(speaker);
        } else if (normalizada.contains("clovis")) {
            this.putoDaCara = false;
            this.sociavel = true;
            acalmar(speaker);
        }
    }

    /** Package-private: o game test enfurece direto. */
    void enraivecer(ServerPlayer alvo) {
        this.raivaTicks = 20 * 60 * 5;
        this.emPostoMercado = false;
        this.setNoAi(false); // solta a IA: ele vai atras do infeliz
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(IntoxicantesMod.ESCOPETA));
        this.setTarget(alvo);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    this.getX(), this.getY() + 2.4, this.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
            alvo.sendSystemMessage(Component.translatable(
                    FRASES_BRAVO[this.random.nextInt(FRASES_BRAVO.length)],
                    this.getName(), alvo.getName()));
            // Expulsa o infeliz do mercado kkkk (v1.2.20: a casa nova é
            // PÉ-NO-CHÃO de verdade — o offset fixo (+5,+1,+5) ignorava a
            // rotação da estrutura e o terreno: freguês nascia dentro da
            // fundação (v1.2.18) e ficava trancado nas pedras)
            BlockPos mercado = MarketSystem.getMarketPos();
            if (mercado != null && alvo.level().dimension().equals(Level.OVERWORLD)
                    && level instanceof ServerLevel serverLevel) {
                if (alvo.blockPosition().distSqr(mercado) < 16 * 16) {
                    BlockPos casa = MarketSystem.procurarCasaProExpulso(
                            serverLevel, mercado, alvo, this);
                    if (casa != null) {
                        // v26.3: teleportTo(ServerLevel, x, y, z, relativos, olhar,
                        // keepCamera=false) — tp normal, sem mexer na câmera
                        alvo.teleportTo(serverLevel, casa.getX() + 0.5, casa.getY(),
                                casa.getZ() + 0.5,
                                java.util.Set.of(), alvo.getYRot(), alvo.getXRot(), false);
                        alvo.setDeltaMovement(Vec3.ZERO);
                        alvo.fallDistance = 0.0F;
                        // v1.2.14: texto fixo ia pro lang (era pt cravado no código)
                        alvo.sendSystemMessage(Component.translatable(
                                "entity.intoxicantes.gago.expulso"));
                    }
                }
            }
        }
    }

    /** Package-private: o game test acalma direto. */
    void acalmar(ServerPlayer player) {
        this.raivaTicks = 0;
        this.setTarget(null);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    this.getX(), this.getY() + 2.4, this.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
            player.sendSystemMessage(Component.translatable(
                    FRASES_AMIGAVEL[this.random.nextInt(FRASES_AMIGAVEL.length)],
                    this.getName(), player.getName()));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROUPA, ROUPA_PLAINS);
        builder.define(DATA_ABERTO, Boolean.TRUE);
    }

    /** Indice da roupa (0..6) — sincronizado pro renderer trocar a textura. */
    public int getRoupa() {
        return this.entityData.get(DATA_ROUPA);
    }

    public void setRoupa(int roupa) {
        this.entityData.set(DATA_ROUPA, Math.floorMod(roupa, ROUPAS));
    }

    /** Mapa bioma->roupa (a chave e' o caminho do Identifier do bioma). */
    private static int roupaDoBioma(Holder<net.minecraft.world.level.biome.Biome> biome) {
        String b = biome.unwrapKey()
                .map(ResourceKey::identifier).map(Identifier::getPath).orElse("");
        if (b.contains("desert") || b.contains("badlands") || b.contains("beach")
                || b.contains("dune")) {
            return ROUPA_SERTAO;
        }
        if (b.contains("jungle") || b.contains("bamboo") || b.contains("pale")) {
            return ROUPA_MATA;
        }
        if (b.contains("savanna")) {
            return ROUPA_CERRADO;
        }
        if (b.contains("snow") || b.contains("ice") || b.contains("frozen")) {
            return ROUPA_SUL; // sede matriz kkkk
        }
        if (b.contains("taiga") || b.contains("grove") || b.contains("peaks")
                || b.contains("mountain") || b.contains("windswept") || b.contains("stony")) {
            return ROUPA_SERRA;
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return ROUPA_BREJO;
        }
        return ROUPA_PLAINS; // plains, forest, meadow, cherry... o classico
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        // v1.2.30: Gago de save velho nunca mais despawna (marca no 1o tick)
        if (!this.persistenciaGarantida) {
            this.persistenciaGarantida = true;
            this.setPersistenceRequired();
        }
        if (this.tickCount % 20 == 0) refreshTradeStock();
        // EM ATENDIMENTO: estatueta. IA congelada, navegacao parada, velocidade
        // zero — o fregues nao merece o vendedor passeando no meio da compra
        if (this.getTradingPlayer() != null && this.raivaTicks <= 0) {
            this.setNoAi(true);
            this.getNavigation().stop();
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        // roupa na primeira vez que o servidor pensa nele (spawn por template,
        // ovo, comando ou despertar de save antigo): o bioma ONDE ELE ESTA decide
        if (!this.roupaDefinida) {
            this.roupaDefinida = true;
            this.setRoupa(roupaDoBioma(
                    this.level().getBiome(this.blockPosition())));
        }
        if (this.cooldownTiro > 0) this.cooldownTiro--;
        if (this.cooldownLetreiro > 0) this.cooldownLetreiro--;
        if (this.cooldownPedrada > 0) this.cooldownPedrada--;
        if (this.cooldownFumaca > 0) this.cooldownFumaca--;
        if (this.cooldownZoacao > 0) this.cooldownZoacao--;
        // v1.2.8: fiscalizacao do balcao — alguem fumando baseado perto? 1x/s
        if (this.tickCount % 20 == 0) {
            fiscalizarFumaca();
            // v1.2.44 — MERCADO 24H: a checagem de horário virou no-op (o
            // portão não existe mais), mas o cochilo segue pro save antigo.
            if (this.level() instanceof ServerLevel nivelVirada) {
                this.atualizarCochilo(nivelVirada);
            }
        }
        if (this.raivaTicks < 0) {
            this.raivaTicks++; // advertencia expirando: volta a "calmo" (== 0)
        }
        if (this.raivaTicks > 0) {
            this.raivaTicks--;
            if (this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty()) {
                this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new ItemStack(IntoxicantesMod.ESCOPETA));
            }
            if (this.isAggressive() && this.random.nextInt(30) == 0
                    && this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        this.getX(), this.getY() + 2.4, this.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
            }
            if (this.getTarget() instanceof LivingEntity alvo && alvo.isAlive()) {
                this.atirarEscopeta(alvo);
            }
            if (this.raivaTicks == 0) {
                this.setTarget(null);
                // raiva passou: GUARDA a 12 e volta a trabalhar no posto (NoAI de novo)
                this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (this.posPostoMercado != null) {
                    this.emPostoMercado = true;
                    this.setNoAi(true);
                }
            }
        }
    }

    /**
     * v1.2.39 — A DUPLA: o Juça gritou "GAGO" no chat (cachaça nele); o dono
     * do Esquinão responde na altura (o Grito do Balcão). Package-private:
     * o Juça chama direto.
     */
    void responderJuca(ServerLevel level) {
        this.playSound(IntoxicantesMod.VOZ_GAGO, 2.0F, 0.9F);
        for (Player perto : level.players()) {
            if (perto.distanceToSqr(this) < 20.0 * 20.0) {
                perto.sendSystemMessage(Component.translatable(
                        "entity.intoxicantes.gago.resposta_juca", this.getName()));
            }
        }
    }

    /** Package-private: o game test dispara direto (sem esperar o tick). */
    void atirarEscopeta(LivingEntity alvo) {
        if (this.cooldownTiro > 0 || !(this.level() instanceof ServerLevel level)) return;
        int alcanceMax = Math.max(1, ModConfig.get().escopetaAlcanceMaximo); // mesmo alcance da 12 do player
        double distancia = this.distanceTo(alvo);
        if (distancia > alcanceMax || !this.hasLineOfSight(alvo)) return;
        this.cooldownTiro = Math.max(1, ModConfig.get().escopetaCooldownTicks); // mesmo cooldown do item do player
        Vec3 origem = this.getEyePosition();
        Vec3 mira = alvo.getEyePosition();
        // dano acumulado por vitima (os balins no mesmo tick casariam em i-frames
        // do vanilla — invulnerableTime — e so 1 balim contaria)
        float danoPorBalim = ModConfig.get().escopetaDanoPorBalim; // mesma mao de obra que a sua 12 (config)
        java.util.Map<LivingEntity, Double> feridos = new java.util.HashMap<>();
        // estouro proprietario do mod + bomba mecanica (mesmaIdentidade sonora da 12 do player)
        this.playSound(IntoxicantesMod.SOCO_D12, 2.0F, 1.0F);
        this.playSound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1.3F, 0.5F);
        Vec3 direcaoCano = mira.subtract(origem).normalize();
        Vec3 boca = origem.add(direcaoCano.scale(0.9));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boca.x, boca.y, boca.z, 8, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.FLAME, boca.x, boca.y, boca.z, 4, 0.05, 0.05, 0.05, 0.01);
        // mesmo detector de CONE do EscopetaItem (o sampler do ProjectileUtil varre
        // por celulas de chunk e deixava alvos finos escorrerem)
        java.util.List<LivingEntity> presentes = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(alcanceMax),
                e -> e != this && e.isAlive() && !e.isSpectator());
        for (int i = 0; i < ModConfig.get().escopetaBalins; i++) {
            double desvio = 0.045 + (distancia / (double) alcanceMax) * 0.085;
            Vec3 direcao = mira.add(
                    (this.random.nextDouble() - 0.5) * 2 * desvio * distancia,
                    (this.random.nextDouble() - 0.5) * 2 * desvio * distancia + distancia * 0.035,
                    (this.random.nextDouble() - 0.5) * 2 * desvio * distancia)
                    .subtract(origem).normalize();
            double alcanceTotal = Math.max(distancia, 3.0) + 1.5;
            Vec3 fim = origem.add(direcao.scale(alcanceTotal));
            // chumbo atravessa vidro (ele atira ATRAVES da vitrine de dentro do mercado);
            // vidro/pane NAO sao full-block -> nao travam o balim
            Vec3 inicio = origem;
            BlockHitResult bloco = level.clip(new ClipContext(inicio, fim,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            for (int p = 0; p < 4 && bloco.getType() != HitResult.Type.MISS
                    && !level.getBlockState(bloco.getBlockPos()).isCollisionShapeFullBlock(
                            level, bloco.getBlockPos()); p++) {
                inicio = bloco.getLocation().add(direcao.scale(0.1));
                bloco = level.clip(new ClipContext(inicio, fim,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            }
            double alcance = bloco.getType() == HitResult.Type.MISS
                    ? alcanceTotal
                    : bloco.getLocation().distanceTo(origem);
            LivingEntity vitima = null;
            double melhorDist = Double.MAX_VALUE;
            for (double d = 0.8; d <= alcance; d += (alcance - 0.4) / 12.0) {
                Vec3 ponto = origem.add(direcao.scale(d));
                for (LivingEntity a : presentes) {
                    double raio = a.getBbWidth() / 2.0 + 0.6 + d / 24.0;
                    if (a.getBoundingBox().inflate(raio).contains(ponto) && d < melhorDist) {
                        vitima = a;
                        melhorDist = d;
                    }
                }
            }
            if (vitima != null) {
                feridos.merge(vitima, (double) danoPorBalim, Double::sum);
            }
        }
        this.push(-direcaoCano.x * 0.35, 0.1, -direcaoCano.z * 0.35);
        // v1.2.12: o Gago NAO e' serrilha de boss — contra alvo de vida maxima alta
        // (wither, dragao, warden...) o volley dele capA no config (padrao 6.0).
        // Ele tem municao infinita e atira sozinho a cada segundo: sem cap, derretia
        // um wither em segundos. Contra fregues comum, dano pleno (e o PLAYER que
        // paga cartucho nao sofre cap nenhum).
        float capBoss = ModConfig.get().escopetaCapDanoBoss;
        for (var ferido : feridos.entrySet()) {
            LivingEntity v = ferido.getKey();
            float dano = ferido.getValue().floatValue();
            if (v.getMaxHealth() > 100.0F && dano > capBoss) {
                dano = capBoss;
            }
            Chumbo.aplicar(level, v, this.damageSources().mobAttack(this), dano);
        }
    }

    @Override
    public void setTarget(LivingEntity alvo) {
        super.setTarget(alvo);
        if (alvo != null && !this.level().isClientSide()) {
            this.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.55F);
        }
    }

    // ==================================================== SAVE / LOAD

    /**
     * v1.2.51 — CONGELA pro atendimento: parado onde está, sem IA, até a
     * tela fechar (o encerrarAtendimento devolve o comportamento normal).
     * Vale pro Gago de plantão (já NoAI) e pro de ovo/passeando, que saía
     * ANDANDO com a interface aberta até o watchdog fechar por distância.
     */
    public void congelarParaAtendimento() {
        if (!this.level().isClientSide()) {
            this.emPostoMercado = false;      // não é plantão: é sessão de venda
            this.setNoAi(true);
            this.getNavigation().stop();
        }
    }

    /** Atendimento encerrado pelo servidor (tela fechou / watchdog): solta o Gago. */
    public void encerrarAtendimento() {
        this.setTradingPlayer(null);
        if (!this.level().isClientSide()) {
            if (this.posPostoMercado != null && this.raivaTicks <= 0) {
                this.setupPostoMercado(this.posPostoMercado);
            } else if (this.raivaTicks <= 0) {
                // Gago de ovo/fora do mercado: volta a passear
                this.setNoAi(false);
            }
        }
    }

    /** Fechou a tela de comercio: restaura o estado (posto ou passeio livre). */
    @Override
    protected void stopTrading() {
        super.stopTrading();
        if (!this.level().isClientSide()) {
            if (this.posPostoMercado != null) {
                // volta pro posto (respeita raiva ativa — setupPostoMercado decide)
                this.setupPostoMercado(this.posPostoMercado);
            } else if (this.raivaTicks <= 0) {
                // Gago de ovo/fora do mercado: volta a passear
                this.setNoAi(false);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.tradeStock.load(input);
        // roupa gravada: mantem a QUE ELE JA TINHA (nao troca de fantasia no meio
        // da bronca se voce arrastar ele pra outro bioma kkkk)
        int salva = input.getIntOr("Roupa", -1);
        if (salva >= 0) {
            this.setRoupa(salva);
            this.roupaDefinida = true;
        }
        // sem "Roupa" no save = NPC de save antigo: o tick 1 define pelo bioma
        // v1.2.38 — PERSISTE O POSTO: sem isso, reload = Gago sem imunidade e sem
        // vínculo com o mercado (sufoca, morre dropando, gerente repõe = loop).
        var posto = input.getIntArray("PostoMercado").orElse(null);
        if (posto != null && posto.length == 3) {
            this.posPostoMercado = new net.minecraft.core.BlockPos(posto[0], posto[1], posto[2]);
            this.emPostoMercado = input.getBooleanOr("EmPosto", true);
            if (this.emPostoMercado && this.raivaTicks <= 0) {
                this.setNoAi(true);
            }
        }
        this.garantirNomeNaCabeca();
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.tradeStock.save(output);
        output.putInt("Roupa", this.getRoupa());
        // v1.2.38: posto persistido — a imunidade e o vínculo sobrevivem ao save/load
        if (this.posPostoMercado != null) {
            output.putIntArray("PostoMercado", new int[]{
                    this.posPostoMercado.getX(), this.posPostoMercado.getY(), this.posPostoMercado.getZ()});
            output.putBoolean("EmPosto", this.emPostoMercado);
        }
    }

    // ==================================================== BORRACHARIA

    /**
     * v1.2.10: o fregues cruzou o limiar da fala bêbada perto do balcão? O dono
     * da esquina DEBOCHA (voz + fala), com throttle — o Gago é zoeiro, não
     * babaca. Raiva total resolve na bala, sem deboche.
     */
    void zoFreguesBebado(ServerPlayer fregues) { // package-private: o game test chama direto
        if (this.raivaTicks != 0 || this.cooldownZoacao > 0) return;
        // so debocha de fregues REALMENTE bêbado (limiar da fala, do config)
        if (Embriaguez.nivel(fregues) < ModConfig.get().embriaguezLimiarFonar) return;
        this.cooldownZoacao = 600; // 30s entre deboches
        fregues.sendSystemMessage(Component.translatable(
                FRASES_BORRACHARIA[this.random.nextInt(FRASES_BORRACHARIA.length)],
                this.getName(), fregues.getName()));
        this.playSound(IntoxicantesMod.VOZ_GAGO, 1.0F, 1.05F);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY() + 2.2, this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
    }

    // ==================================================== LETREIRO + PEDRADAS

    /**
     * v1.2.8: o Gago DETESTA baseado no balcão — "aqui é bebida fria, essa
     * erva lá pra fora!". Escalonado: 1a vez reclama (voz + fala), 2a vez
     * reclama de novo, na 3a apaga na mão (interrompe o puxão + 10s de
     * cooldown) e fica bravo na cara. Passou o clima, o contador zera.
     * Roda 1x/s do tick; raiva total resolve na bala, sem conversa.
     */
    void fiscalizarFumaca() { // package-private: o game test chama direto
        if (this.raivaTicks != 0) return;
        ServerLevel level = (ServerLevel) this.level();
        java.util.List<ServerPlayer> fumantes = level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(4.0D),
                p -> p.isUsingItem() && p.getUseItem().is(IntoxicantesMod.BASEADO));
        if (fumantes.isEmpty()) {
            this.fumacaConversas = 0;
            return;
        }
        if (this.cooldownFumaca > 0) return;
        this.cooldownFumaca = 120; // 6s entre reclamacoes
        ServerPlayer fumante = fumantes.get(0);
        this.fumacaConversas++;
        String chave = this.fumacaConversas >= 3
                ? "entity.intoxicantes.gago.fumaca.3"
                : (this.fumacaConversas == 2
                        ? "entity.intoxicantes.gago.fumaca.2"
                        : "entity.intoxicantes.gago.fumaca.1");
        fumante.sendSystemMessage(Component.translatable(chave,
                this.getName(), fumante.getName()));
        // voz propia no lugar do grunt de villager (o ambient dele é a VOZ_GAGO)
        this.playSound(IntoxicantesMod.VOZ_GAGO, 1.0F, 1.0F);
        if (this.fumacaConversas >= 3) {
            // acabou a paciencia: APAGA NA MÃO (interrompe o uso e trava 10s)
            this.fumacaConversas = 0;
            fumante.releaseUsingItem();
            fumante.getCooldowns().addCooldown(new ItemStack(IntoxicantesMod.BASEADO), 200);
            level.sendParticles(ParticleTypes.SMOKE,
                    fumante.getX(), fumante.getY() + 1.2, fumante.getZ(),
                    8, 0.2, 0.2, 0.2, 0.02);
        }
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                this.getX(), this.getY() + 2.4, this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
    }

    /**
     * Alguem leu o letreiro "SUL DISTRIBUIDORA & MERCADO ESQUINAO": o Gago
     * cumprimenta o fregues (ou recomenda education, dependendo do humor).
     * Chamado do UseBlockCallback em qualquer placa perto do mercado.
     */
    public void cumprimentarLeitor(ServerLevel level, Player leitor) {
        if (this.cooldownLetreiro > 0) return;
        this.cooldownLetreiro = 160; // 8s entre cumprimentos
        // v1.2.19: o BLIP DE VOZ do Gago lendo o letreiro (o blip existe, a voz agora)
        this.playSound(IntoxicantesMod.VOZ_GAGO, 0.85F, 1.25F);
        // v1.2.25: o DONO DA ESQUINA (tier 3) tem o chamado respeitoso da casa
        if (leitor instanceof ServerPlayer fregues
                && FidelidadeData.getTier(fregues) >= FidelidadeData.TIER_DONO) {
            leitor.sendSystemMessage(Component.translatable(
                    FRASES_VIP[this.random.nextInt(FRASES_VIP.length)],
                    this.getName(), leitor.getName()));
            level.sendParticles(ParticleTypes.HEART,
                    this.getX(), this.getY() + 2.2, this.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
        } else {
            leitor.sendSystemMessage(Component.translatable(
                    FRASES_LETREIRO[this.random.nextInt(FRASES_LETREIRO.length)],
                    this.getName(), leitor.getName()));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 2.2, this.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * v1.2.25 — O COCHILO: fora do expediente (00:00 ~ 07:00) o dono da
     * esquina não é máquina — fecha, toma a última e dorme na cadeira do
     * balcão: Zzz em baforadas de nuvem (o SLEEP não existe no 26.3) e frase
     * embolada se o freguês tocar nele. Acorde e de pé no horário de abrir.
     */
    private void atualizarCochilo(ServerLevel level) {
        if (this.cooldownCochilo > 0) this.cooldownCochilo--;
        // v1.2.45 — MERCADO 24H, PARTE 2: o cochilo é EXTERMINADO. A condição
        // antiga era `!isMercadoAberto() && ...` — com a flag false de save
        // velho (o portão saiu do jogo), o dono da esquina ficava SENTADO
        // pra sempre com a boca embolada, "preso nos horários". Levanta,
        // arruma a pose e nunca mais senta.
        if (this.cochilando) {
            this.cochilando = false;
            this.setPose(net.minecraft.world.entity.Pose.STANDING);
        }
    }

    /**
     * Pedrada no mercado (soco/bomba em bloco na area): o Gago fica puto POR
     * UM TEMPO CURTO (30s), saca a 12 e vai atras do vandal — diferente do
     * "gago" no chat (5 min de raiva total). Passou a raiva, volta ao balcao.
     */
    public void naPedrada(ServerLevel level, Player vandal) {
        // ADVERTENCIA primeiro: acerto acidental na parede nao vira perseguicao.
        // Repetiu dentro dos 30s — ai sim saca a doze. Provocar de proposito
        // continua dando o que falar kkkk
        if (this.raivaTicks == 0) {
            this.raivaTicks = -20 * 30; // negativo = "em advertencia" (30s)
            this.setTarget(null);
            if (this.posPostoMercado != null) {
                this.setNoAi(true); // segue no posto, so avisou
            }
            vandal.sendSystemMessage(Component.translatable(
                    FRASES_ADVERTENCIA[this.random.nextInt(FRASES_ADVERTENCIA.length)],
                    this.getName(), vandal.getName()));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    this.getX(), this.getY() + 2.4, this.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
            return;
        }
        if (this.cooldownPedrada > 0) return; // ja respondeu esse periodo
        this.cooldownPedrada = 100; // 1 msg a cada 5s enquanto a pedrada continuar
        this.raivaTicks = Math.max(this.raivaTicks, 20 * 30);
        this.emPostoMercado = false;
        this.setNoAi(false);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(IntoxicantesMod.ESCOPETA));
        this.setTarget(vandal);
        vandal.sendSystemMessage(Component.translatable(
                FRASES_PEDRADA[this.random.nextInt(FRASES_PEDRADA.length)],
                this.getName(), vandal.getName()));
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                this.getX(), this.getY() + 2.4, this.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            Player matador = source.getEntity() instanceof Player p ? p : null;
            String chave = this.putoDaCara
                    ? FRASES_MORTE[this.random.nextInt(FRASES_MORTE.length)]
                    : "entity.intoxicantes.gago.morte.inocente";
            if (matador != null) {
                matador.sendSystemMessage(Component.translatable(chave, this.getName()));
            }
            // v1.2.66 — A RÁDIO PEÃO: matou o dono do bar devendo fiado? O
            // corpo de chocadeiro sabe de TUDO: onde o covarde marca encontro.
            if (matador instanceof ServerPlayer devor) {
                int divida = PlayerMoney.getDivida(devor);
                if (divida > 0) {
                    devor.sendSystemMessage(Component.translatable(
                            "commerce.intoxicantes.gago.morte_fiado", divida));
                }
            }
        }
        super.die(source);
    }

    /**
     * v1.2.66 — A OUTRA PONTA DA PONTE: o Gago COMPRA insumo de bebida NA
     * PORTA (mesma tabela da aba "Vender ingredientes", mesma cota diária),
     * sem abrir a tela — e lembra que droga é com o camelo do Ponto.
     * Espelho do venderCruDroga do traficante: a economia da esquina fecha
     * o circuito (planta bebida -> vende no Esquinão; planta droga -> Ponto).
     */
    public boolean venderInsumo(ServerPlayer player, int indice) {
        var insumos = TradeCatalog.harvests();
        if (!player.isAlive() || player.level() != this.level()
                || indice < 0 || indice >= insumos.size()) return false;
        TradeCatalog.Entry e = insumos.get(indice);
        var stock = marketInventory();
        if (stock.remaining(e) == 0) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.gago.cota_insumo"));
            return false;
        }
        int tenho = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(e.item())) tenho += st.getCount();
        }
        if (tenho < e.count()) return false;
        int restante = e.count();
        for (int i = 0; i < player.getInventory().getContainerSize() && restante > 0; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.is(e.item())) continue;
            int tira = Math.min(st.getCount(), restante);
            st.shrink(tira);
            restante -= tira;
        }
        PlayerMoney.add(player, e.price());
        stock.consume(e);
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.translatable(
                "commerce.intoxicantes.gago.comprou_insumo", e.count(), e.price()));
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // v1.2.30: GUARDA DO LOOT — se ele morreu EM SERVIÇO (no posto, que é
        // exatamente o bug do sufocamento), não vira dispensa de mercado:
        // morre sem drop (o loot é pra QUEM MATOU o dono do bar de propósito,
        // não pro sistema encher a loja de item). Defesa contra qualquer
        // futuro caminho de dano no posto.
        // v1.2.36 — A GUARDA DO ENTALADO: morreu PRESO num bloco (sufocamento
        // do sistema — ex. emPostoMercado transiente após restart) também não
        // dropa: é o loop que enchia o mercado de cachaça/R$/cartucho.
        if (this.emPostoMercado
                || this.posPostoMercado != null
                        && this.blockPosition().distSqr(this.posPostoMercado) < 4.0
                || this.estaPresoEmBloco()) {
            super.dropCustomDeathLoot(level, source, recentlyHit);
            return;
        }
        this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.CACHACA));
        this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.CERVEJA, 1 + this.random.nextInt(3)));
        // v1.2.60: o bolso do dono do bar tem NOTAS — troco certo (5..24 em valor)
        RealItem.trocar(5 + this.random.nextInt(20)).forEach(p -> this.spawnAtLocation(level, p));
        // cartuchos no bolso do dono do bar (ele carrega pro caso de voce chamar ele de gago)
        this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.CARTUCHO, 2 + this.random.nextInt(4)));
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        // voz propia: blip dobrado estilo fala de jogo (o bobo nunca cala a boca kkkk)
        return IntoxicantesMod.VOZ_GAGO;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    /**
     * v1.2.44 — O GAGO NUNCA MAIS LARGA O POSTO: o gerenciador conserta o
     * dono da esquina ONDE ELE ESTÁ (dentro do pátio) em vez de teleportar
     * a cada ciclo — era assim que ele ficava entalado em bloco, "falando
     * no chat e invisível". Desobstrui o corpo SÓ se estiver preso, trava
     * NoAI e marca o posto aí mesmo.
     */
    public void consolidarAqui(ServerLevel level) {
        if (this.estaPresoEmBloco()) {
            MarketSystem.garantirPostoLivre(level, this.blockPosition());
        }
        this.emPostoMercado = true;
        this.posPostoMercado = this.blockPosition();
        this.setNoAi(true);
        this.getNavigation().stop();
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
}
