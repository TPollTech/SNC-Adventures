package com.intoxicantes;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Ambulante do jogo do bicho. Não ocupa nenhum catálogo dos outros NPCs. */
public class PeruEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> FUMANDO =
            SynchedEntityData.defineId(PeruEntity.class, EntityDataSerializers.INT);
    private UUID moto;
    private boolean gerenciado;
    private int proximaAcao = 400;
    private int passeio;

    public PeruEntity(EntityType<? extends PeruEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.translatable("entity.intoxicantes.peru"));
        setCustomNameVisible(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.FOLLOW_RANGE, 16);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FUMANDO, 0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    public int fumandoTicks() { return entityData.get(FUMANDO); }
    public UUID motoUUID() { return moto; }
    public boolean gerenciado() { return gerenciado; }
    public void vincularMoto(MotoPeruEntity veiculo, boolean controlado) {
        moto = veiculo.getUUID(); gerenciado = controlado;
        veiculo.dono(getUUID());
    }
    public MotoPeruEntity moto() {
        return level() instanceof ServerLevel sl && moto != null
                && sl.getEntity(moto) instanceof MotoPeruEntity m && !m.isRemoved()
                && getUUID().equals(m.dono()) ? m : null;
    }
    public void estacionar() {
        MotoPeruEntity m = moto();
        if (m != null) m.parar();
        if (isPassenger()) stopRiding();
        getNavigation().stop();
        passeio = 0;
        proximaAcao = 300;
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.isAlive() || !isAlive()) return InteractionResult.FAIL;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && player.isWithinEntityInteractionRange(this, 0)
                && player.hasLineOfSight(this)) {
            estacionar();
            PeruNetworking.abrir(sp, this);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (gerenciado && tickCount % 100 == 0) PeruSystem.observar(this);
        int f = fumandoTicks();
        if (f > 0) {
            entityData.set(FUMANDO, f - 1);
            if (f == 8) {
                double yaw = Math.toRadians(yHeadRot);
                sl.sendParticles(ParticleTypes.SMOKE, getX() - Math.sin(yaw) * .31,
                        getEyeY() - .13, getZ() + Math.cos(yaw) * .31,
                        2, .025, .015, .025, .007);
            }
        } else if (tickCount % 100 == 0 && random.nextInt(3) == 0) entityData.set(FUMANDO, 28);
        if (PeruNetworking.negociando(getUUID())) {
            getNavigation().stop();
            if (isPassenger()) estacionar();
            return;
        }
        MotoPeruEntity m = moto();
        if (m == null) return; // Contraparte descarregada não cria uma moto substituta.
        if (isPassenger()) {
            yBodyRot = getYRot();
            if (--passeio <= 0 || !m.emMovimento()) estacionar();
            return;
        }
        if (--proximaAcao > 0) return;
        proximaAcao = 300 + random.nextInt(500);
        if (sl.getNearestPlayer(this, 8) != null) return;
        if (distanceToSqr(m) > 5) {
            getNavigation().moveTo(m.getX() + 1.5, m.getY(), m.getZ(), .6);
        } else if (random.nextInt(3) == 0) {
            Vec3 alvo = m.position().add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
            if (m.caminhoLivre(alvo) && startRiding(m)) {
                getNavigation().stop(); passeio = 100; m.passear(alvo);
            }
        } else {
            BlockPos p = BlockPos.containing(m.position().add(0, .1, 0))
                    .offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            var espaco = getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(position()));
            if (sl.hasChunksAt(BlockPos.containing(espaco.minX, espaco.minY, espaco.minZ),
                        BlockPos.containing(espaco.maxX, espaco.maxY, espaco.maxZ))
                    && MotoPeruEntity.apoioFirme(sl, p.below()) && !sl.containsAnyLiquid(espaco)
                    && sl.getWorldBorder().isWithinBounds(espaco) && sl.noCollision(this, espaco))
                getNavigation().moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, .55);
        }
    }
    @Override public void die(DamageSource source) {
        estacionar();
        if (gerenciado) PeruSystem.retiring(getUUID());
        super.die(source);
    }
    @Override public void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (gerenciado && level() instanceof ServerLevel) PeruSystem.observar(this);
        if (moto != null) out.putString("MotoPeru", moto.toString());
        out.putBoolean("PeruGerenciado", gerenciado);
    }
    @Override public void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        try { moto = UUID.fromString(in.getStringOr("MotoPeru", "")); }
        catch (IllegalArgumentException ignored) { moto = null; }
        gerenciado = in.getBooleanOr("PeruGerenciado", false);
        setCustomName(Component.translatable("entity.intoxicantes.peru"));
        setCustomNameVisible(true);
    }
    @Override public void remove(RemovalReason motivo) {
        if (gerenciado && level() instanceof ServerLevel) {
            PeruSystem.observar(this);
            if (motivo.shouldDestroy()) PeruSystem.retiring(getUUID());
        }
        super.remove(motivo);
    }
}
