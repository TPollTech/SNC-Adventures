package com.intoxicantes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * JUCELINO, O JUÇA (v1.2.39): o parça do Gago. Fuma Camel amarelo sem
 * parar (fumaça própria), veste a camisa do Matanza e tem TEMA DE
 * ENTRADA — o riff cowpunk dele toca quando alguém se aproxima.
 *
 * Fuma só o dele, não vende nada; mas se você JOGAR UMA CACHAÇA no
 * chão perto dele, ele cai dentro, fica DOIDÃO e grita GAGO NO CHAT
 * (idéia do Discord kkkk) — e o Gago responde.
 */
public class JucelinoEntity extends AbstractVillager {
    /** Falas da troca (joga o item e "admira" — igual piglin). */
    static final String[] FRASES_TROCA = {
            "entity.intoxicantes.juca.troca.1",
            "entity.intoxicantes.juca.troca.2",
            "entity.intoxicantes.juca.troca.3"
    };
    /** O tema dele: INTERVALO mínimo entre riffs (não vira trilha chata). */
    public static final int INTERVALO_RIFF_TICKS = 900; // 45s entre riffs no maximo
    /** Quanto tempo o doidão dura. */
    private static final int DOIDA_TICKS_TOTAL = 600;   // 30s de porre

    static final String[] FRASES_DOIDA = {
            "entity.intoxicantes.juca.doida.1", // GAGO!!!
            "entity.intoxicantes.juca.doida.2",
            "entity.intoxicantes.juca.doida.3",
            "entity.intoxicantes.juca.doida.4"
    };
    static final String[] FRASES_OUVIR = {
            "entity.intoxicantes.juca.chat.1",
            "entity.intoxicantes.juca.chat.2",
            "entity.intoxicantes.juca.chat.3"
    };

    /** Tempo de riff restante (não toca de novo enquanto rola). */
    private int cooldownRiff = 100;
    /** v1.2.40: alguém estava DENTRO do raio na passada anterior? O riff é
     * tema de ENTRADA (edge-trigger) — com este flag ele toca 1× na chegada e
     * SÓ de novo depois do cooldown, mesmo com a galera parada ali. O bug do
     * print: tocava a cada 45s enquanto tivesse gente perto ("tocando
     * aleatório, vai dar acidente"). */
    private boolean alguemPerto;
    /** O PO dao corpo: >0 = doidão, correndo e gritando GAGO. */
    private int doidaTicks;
    /** Timer da fumaça do Camel (ele fuma SEMPRE). */
    private int fumacaCamel;
    /** Grito de "GAGO" restante enquanto dura a bebedeira. */
    private int gritoTicks;

    public JucelinoEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        // pacifista de balada: leva pedrada e CORRE (não tem targetSelector)
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ==================================================== O RIFF DO MATANZA

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.cooldownRiff > 0) {
            this.cooldownRiff--;
        }

        // ---- O TEMA: alguém ACABOU de chegar a 10 blocos? (na passada
        // anterior não tinha ninguém) → toca o riff do Juça. Cooldown manda
        // no re-trigger; com gente parada ali perto ele NÃO repete sozinho.
        Level level = this.level();
        Player chegando = level.getNearestPlayer(this, 10.0);
        boolean pertoAgora = chegando != null;
        if (pertoAgora && !this.alguemPerto && this.cooldownRiff == 0 && !this.isSilent()) {
            this.cooldownRiff = INTERVALO_RIFF_TICKS;
            ((ServerLevel) level).playSound(null, this.getX(), this.getY(), this.getZ(),
                    IntoxicantesMod.JUCA_RIFF, this.getSoundSource(), 2.0F, 1.0F);
        }
        this.alguemPerto = pertoAgora;

        // ---- O CAMEL: fumaça saindo da boca dele a cada ~3s (ele nunca para)
        if (--this.fumacaCamel <= 0) {
            this.fumacaCamel = 60 + this.random.nextInt(40);
            double dx = -Math.cos(Math.toRadians(this.getYRot() + 90));
            double dz = -Math.sin(Math.toRadians(this.getYRot() + 90));
            ((ServerLevel) level).sendParticles(ParticleTypes.SMOKE,
                    this.getX() + dx * 0.4, this.getEyeY() - 0.1, this.getZ() + dz * 0.4,
                    1, 0.02, 0.02, 0.02, 0.004);
        }

        this.tickDoida((ServerLevel) level);
    }

    // ==================================================== O DOIDÃO (cachaça no Juça)

    /**
     * A idéia do Discord: jogar cachaça pro Juça. Uma cachaça caindo a 3
     * blocos dele é "a rodada": ele engole a garrafa, fica doidão (30s
     * correndo em zigue-zague) e grita GAGO NO CHAT pra todo mundo perto.
     */
    private void tickDoida(ServerLevel level) {
        // bebe qualquer cachaça que pousar perto (a rodada dele)
        for (ItemEntity garrafa : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(this.position(), this.position()).inflate(3.0),
                item -> item.isAlive() && item.getItem().is(IntoxicantesMod.CACHACA))) {
            garrafa.getItem().shrink(garrafa.getItem().getCount()); // engole tudo
            garrafa.discard();
            this.encherACara(level);
            break; // uma rodada por passada
        }

        // ---- A TROCA DO CAMEL (estilo piglin): jogou cigarro PRA ELE?
        // ele bolsar (agacha), admira o cigarro e devolve a troca na cara.
        for (ItemEntity cigarro : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(this.position(), this.position()).inflate(3.0),
                item -> item.isAlive() && item.getItem().is(IntoxicantesMod.CIGARRO_CAMEL))) {
            int total = cigarro.getItem().getCount();
            var dono = cigarro.getOwner();
            cigarro.discard();
            this.trocarCamel(level, total, dono);
            break; // uma troca por passada
        }

        if (this.doidaTicks <= 0) {
            return;
        }
        this.doidaTicks--;
        this.gritoTicks--;

        // zigue-zague de bebum: troca de direcao rapido (sem dano, sem AI de raiva)
        if (this.doidaTicks % 20 == 0 && this.getNavigation().isDone()) {
            double ang = this.random.nextDouble() * Math.PI * 2;
            this.getNavigation().moveTo(
                    this.getX() + Math.cos(ang) * 5, this.getY(), this.getZ() + Math.sin(ang) * 5, 1.6);
        }
        if (this.doidaTicks % 4 == 0) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 2.1, this.getZ(), 2, 0.25, 0.2, 0.25, 0.0);
        }
        // O GRITO: "GAGO!!!" a cada 3s enquanto dura o porre (idéia do Discord)
        if (this.gritoTicks <= 0) {
            this.gritoTicks = 60;
            for (Player perto : level.players()) {
                if (perto.distanceToSqr(this) < 20.0 * 20.0) {
                    perto.sendSystemMessage(Component.translatable(
                            FRASES_DOIDA[this.random.nextInt(FRASES_DOIDA.length)],
                            this.getName()));
                }
            }
            this.playSound(IntoxicantesMod.JUCA_VOZ, 2.0F, 1.25F);
            // e o Gago responde se tiver um por perto (a dupla kkkk)
            for (GagoEntity gago : level.getEntitiesOfClass(GagoEntity.class,
                    this.getBoundingBox().inflate(32.0))) {
                gago.responderJuca(level);
                break;
            }
        }
    }

    /**
     * A TROCA DO CAMEL: o Juça pega o cigarro jogado, "admmira" (o bolso do
     * piglin) e devolve o prêmio como ItemEntity na direção de quem jogou.
     * 1 Camel rola na tabela (cerveja/R$/baseados); 2 de uma vez = a CAMISA.
     * Package-private: o game test chama direto.
     */
    void trocarCamel(ServerLevel level, int quantidade, @Nullable Entity quemJogou) {
        ItemStack premio;
        if (quantidade >= 2) {
            premio = new ItemStack(IntoxicantesMod.CAMISA_MATANZA); // o carão
        } else {
            // a tabela da troca (1 Camel): os amigos trocam figurinha
            premio = switch (this.random.nextInt(3)) {
                case 0 -> new ItemStack(IntoxicantesMod.CERVEJA);        // a gelada
                case 1 -> RealItem.trocar(4).get(0);                     // a nota do bolso (troco certo)
                default -> new ItemStack(IntoxicantesMod.BASEADO, 2);    // troca de fumo
            };
        }
        // o "bolso do piglin": agacha e admira antes de devolver
        this.playSound(IntoxicantesMod.JUCA_VOZ, 1.5F, 0.85F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getEyeY() + 0.2, this.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        for (Player perto : level.players()) {
            if (perto.distanceToSqr(this) < 16.0 * 16.0) {
                perto.sendSystemMessage(Component.translatable(
                        FRASES_TROCA[this.random.nextInt(FRASES_TROCA.length)],
                        this.getName()));
            }
        }
        // devolve o prêmio na direção de quem jogou (igual o piglin bolsar)
        double dx = 0, dz = 0;
        if (quemJogou != null) {
            dx = quemJogou.getX() - this.getX();
            dz = quemJogou.getZ() - this.getZ();
            double norm = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            dx /= norm;
            dz /= norm;
        }
        ItemEntity presente = new ItemEntity(level,
                this.getX() + dx * 0.8, this.getEyeY() - 0.2, this.getZ() + dz * 0.8, premio);
        presente.setDeltaMovement(dx * 0.25, 0.15, dz * 0.25);
        level.addFreshEntity(presente);
    }

    /** A rodada caiu: o Juça fica doidão (30s). Package-private: o teste chama. */
    void encherACara(ServerLevel level) {
        this.doidaTicks = DOIDA_TICKS_TOTAL;
        this.gritoTicks = 1; // o primeiro grito sai na hora
        this.playSound(IntoxicantesMod.GLUP, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.SPLASH,
                this.getX(), this.getEyeY(), this.getZ(), 6, 0.2, 0.1, 0.2, 0.0);
    }

    /** Estado pro renderer/teste: ele tá de porre agora? */
    public boolean estaDoidao() {
        return this.doidaTicks > 0;
    }

    /**
     * Alguém escreveu "juça" no chat: ele responde com o "hé hé" e uma
     * puxada no Camel (package-private: o handler de chat chama direto).
     */
    void ouvirChat(Player alvo, ServerLevel level) {
        this.playSound(IntoxicantesMod.JUCA_VOZ, 1.5F, 1.0F);
        alvo.sendSystemMessage(Component.translatable(
                FRASES_OUVIR[this.random.nextInt(FRASES_OUVIR.length)],
                this.getName()));
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getEyeY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
    }

    // ==================================================== O RESTO DO NPC

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distancia) {
        // ele é do mercado: não despawna (mesma regra do Gago)
        return false;
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        // o Juça não vende nada — ele é o clima da esquina (o Camel dele é
        // consumo próprio). O abstract da classe pede o método: fica vazio.
    }

    @Override
    protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer oferta) {
        // sem vendas, sem xp de venda
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance dificuldade,
            EntitySpawnReason motivo, @Nullable SpawnGroupData dados) {
        SpawnGroupData resultado = super.finalizeSpawn(level, dificuldade, motivo, dados);
        this.setPersistenceRequired();
        return resultado;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // "o Juça deixou cair o maço" kkkk
        this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.CIGARRO_CAMEL,
                2 + this.random.nextInt(4)));
        if (this.random.nextInt(5) == 0) {
            this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.CAMISA_MATANZA));
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return IntoxicantesMod.JUCA_VOZ;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}
