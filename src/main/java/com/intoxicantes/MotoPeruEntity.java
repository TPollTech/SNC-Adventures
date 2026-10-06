package com.intoxicantes;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Moto pessoal do Peru; pequenas rotas locais são decididas no servidor. */
public class MotoPeruEntity extends Entity {
    private static final EntityDataAccessor<Float> RODA =
            SynchedEntityData.defineId(MotoPeruEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ANDANDO =
            SynchedEntityData.defineId(MotoPeruEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID dono;
    private Vec3 destino;
    private int tempoRota;
    private float rodaAnterior;

    public MotoPeruEntity(EntityType<? extends MotoPeruEntity> type, Level level) {
        super(type, level); blocksBuilding = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(RODA, 0F); b.define(ANDANDO, false);
    }
    public void dono(UUID id) { dono = id; }
    public UUID dono() { return dono; }
    public boolean emMovimento() { return entityData.get(ANDANDO); }
    public float anguloRoda(float parcial) {
        return rodaAnterior + (entityData.get(RODA) - rodaAnterior) * parcial;
    }
    public void parar() { destino = null; entityData.set(ANDANDO, false); setDeltaMovement(Vec3.ZERO); }
    public void passear(Vec3 alvo) {
        if (alvo == null || !caminhoLivre(alvo)) { parar(); return; }
        destino = alvo; tempoRota = 120;
        // Evita o piloto desmontar antes do primeiro tick da moto, dependendo da ordem das entidades.
        entityData.set(ANDANDO, true);
    }
    @Override protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }
    @Override public void tick() {
        super.tick(); rodaAnterior = entityData.get(RODA);
        if (level().isClientSide()) { getInterpolation().interpolate(); return; }
        boolean rodando = destino != null && !getPassengers().isEmpty() && --tempoRota > 0;
        Vec3 passo = Vec3.ZERO;
        if (rodando) {
            Vec3 delta = destino.subtract(position()).multiply(1, 0, 1);
            if (delta.lengthSqr() < .2) rodando = false;
            else {
                passo = delta.normalize().scale(.075);
                if (!caminhoLivre(position().add(passo.scale(10)))) rodando = false;
            }
        }
        if (!rodando) { destino = null; passo = Vec3.ZERO; }
        entityData.set(ANDANDO, rodando);
        if (rodando) setYRot((float) Math.toDegrees(Math.atan2(-passo.x, passo.z)));
        setDeltaMovement(passo.x, Math.max(-.5, getDeltaMovement().y - .04), passo.z);
        Vec3 antes = position();
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
        double avancou = position().subtract(antes).horizontalDistance();
        if (horizontalCollision) parar();
        entityData.set(RODA, entityData.get(RODA) + (float) (avancou / .30));
    }
    /** Toda a pequena rota precisa de apoio, espaço para o piloto e chunks já carregados. */
    public boolean caminhoLivre(Vec3 alvo) {
        Vec3 delta = alvo.subtract(position());
        int passos = Math.max(1, (int) Math.ceil(delta.length() / .5));
        if (passos > 24) return false;
        for (int i = 1; i <= passos; i++) {
            Vec3 ponto = position().add(delta.scale((double) i / passos));
            double meiaLargura = getBbWidth() * .5 + .01;
            AABB espaco = new AABB(ponto.x - meiaLargura, ponto.y + .01, ponto.z - meiaLargura,
                    ponto.x + meiaLargura, ponto.y + 2.35, ponto.z + meiaLargura);
            if (!espacoLivre(espaco) || level().getEntities(this, espaco).stream().anyMatch(e ->
                    e.isAlive() && e.isPickable() && !e.isPassengerOfSameVehicle(this)
                            && !e.getUUID().equals(dono))) return false;
        }
        return true;
    }
    /** Verifica a caixa toda sem carregar chunks nem atravessar água ou deixar roda sobre um buraco. */
    private boolean espacoLivre(AABB espaco) {
        BlockPos minimo = BlockPos.containing(espaco.minX, espaco.minY, espaco.minZ);
        BlockPos maximo = BlockPos.containing(espaco.maxX - .0001, espaco.maxY, espaco.maxZ - .0001);
        if (!level().hasChunksAt(minimo, maximo) || !level().getWorldBorder().isWithinBounds(espaco)
                || level().containsAnyLiquid(espaco) || !level().noCollision(this, espaco)) return false;
        for (int x = minimo.getX(); x <= maximo.getX(); x++) for (int z = minimo.getZ(); z <= maximo.getZ(); z++) {
            BlockPos apoio = BlockPos.containing(x, espaco.minY - .06, z);
            if (!apoioFirme(level(), apoio)) return false;
        }
        return true;
    }
    static boolean apoioFirme(Level nivel, BlockPos pos) {
        var piso = nivel.getBlockState(pos);
        // Dirt path tem 15/16 de altura e não é solidRender, embora seja uma estrada utilizável.
        return nivel.getFluidState(pos).isEmpty() && (piso.isSolidRender() || piso.is(Blocks.DIRT_PATH));
    }
    @Override protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty() && e instanceof PeruEntity && e.getUUID().equals(dono);
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        double yaw = Math.toRadians(getYRot());
        move.accept(passenger, getX() + Math.sin(yaw) * .18, getY() + .01,
                getZ() - Math.cos(yaw) * .18);
        passenger.setYRot(getYRot());
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        for (Vec3 offset : new Vec3[]{new Vec3(1.5, 0, 0), new Vec3(-1.5, 0, 0),
                new Vec3(0, 0, 1.5), new Vec3(0, 0, -1.5),
                new Vec3(1.5, 0, 1.5), new Vec3(-1.5, 0, 1.5),
                new Vec3(1.5, 0, -1.5), new Vec3(-1.5, 0, -1.5)}) {
            Vec3 p = position().add(offset);
            AABB espaco = e.getBoundingBox().move(p.subtract(e.position()));
            if (level().hasChunksAt(BlockPos.containing(espaco.minX, espaco.minY, espaco.minZ),
                            BlockPos.containing(espaco.maxX, espaco.maxY, espaco.maxZ))
                    && level().getWorldBorder().isWithinBounds(espaco)
                    && apoioFirme(level(), BlockPos.containing(p.add(0, -.06, 0)))
                    && !level().containsAnyLiquid(espaco) && level().noCollision(e, espaco)) return p;
        }
        return super.getDismountLocationForPassenger(e);
    }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith(Entity outro) {
        return !isRemoved() && !isPassengerOfSameVehicle(outro);
    }
    @Override public InteractionResult interact(Player p, InteractionHand hand, Vec3 hit) {
        if (p.isSpectator() || !p.isAlive()) return InteractionResult.FAIL;
        if (level() instanceof ServerLevel sl && dono != null && sl.getEntity(dono) instanceof PeruEntity peru)
            return peru.mobInteract(p, hand);
        return InteractionResult.PASS;
    }
    @Override public boolean hurtServer(ServerLevel sl, DamageSource source, float amount) {
        // Destruir a moto não duplica NPC nem oferece um veículo público gratuito.
        if (amount <= 0 || isRemoved()) return false;
        if (dono != null && sl.getEntity(dono) instanceof PeruEntity peru) peru.estacionar();
        discard(); return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        if (level() instanceof ServerLevel) PeruSystem.observar(this);
        if (dono != null) out.putString("DonoPeru", dono.toString());
        out.putFloat("Roda", entityData.get(RODA));
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        try { dono = UUID.fromString(in.getStringOr("DonoPeru", "")); }
        catch (IllegalArgumentException ignored) { dono = null; }
        float roda = in.getFloatOr("Roda", 0);
        if (!Float.isFinite(roda)) roda = 0;
        entityData.set(RODA, roda);
        rodaAnterior = roda;
        parar();
    }
    @Override public void remove(RemovalReason motivo) {
        if (level() instanceof ServerLevel) {
            PeruSystem.observar(this);
            if (motivo.shouldDestroy() && dono != null) PeruSystem.retiring(dono);
        }
        super.remove(motivo);
    }
}
