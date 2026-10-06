package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Hitbox nativa de um cacho; desenho e dados permanentes pertencem à raiz. */
public final class CachoParreiraEntity extends Entity {
    public static final float LARGURA = .38F;
    public static final float ALTURA = .55F;
    private static final EntityDataAccessor<BlockPos> RAIZ = SynchedEntityData.defineId(
            CachoParreiraEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> APOIO = SynchedEntityData.defineId(
            CachoParreiraEntity.class, EntityDataSerializers.BLOCK_POS);

    public CachoParreiraEntity(EntityType<? extends CachoParreiraEntity> tipo, Level level) {
        super(tipo, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RAIZ, BlockPos.ZERO);
        builder.define(APOIO, BlockPos.ZERO);
    }

    public void configurar(BlockPos raiz, BlockPos apoio) {
        entityData.set(RAIZ, raiz.immutable());
        entityData.set(APOIO, apoio.immutable());
        // Mesmo centro do cacho no renderer: centro da cerca + (.23,.64,-.23).
        // A base cobre as quatro camadas de uvas e a haste, sem colidir com o jogador.
        setPos(apoio.getX() + .73, apoio.getY() + .27, apoio.getZ() + .27);
        setOldPosAndRot();
    }

    public BlockPos raiz() { return entityData.get(RAIZ); }
    public BlockPos apoio() { return entityData.get(APOIO); }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel) || tickCount % 20 != 0) return;
        // A hitbox temporária pode sumir num unload; o slot no NBT continua vivo.
        if (!level().hasChunkAt(raiz()) || !level().hasChunkAt(apoio())) {
            discard();
            return;
        }
        if (!(level().getBlockEntity(raiz()) instanceof ParreiraBlockEntity parreira)
                || !parreira.cachoPronto(apoio())) discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (isRemoved() || hand != InteractionHand.MAIN_HAND || player.isSpectator()
                || !UvaParreiraBlock.maoDeColheita(player)
                || !player.isWithinEntityInteractionRange(this, 0.0)
                || !level().hasChunkAt(raiz()) || !level().hasChunkAt(apoio())
                || !level().mayInteract(player, raiz()) || !level().mayInteract(player, apoio())) {
            return InteractionResult.PASS;
        }
        if (!(level().getBlockEntity(raiz()) instanceof ParreiraBlockEntity parreira)) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel servidor)) {
            return parreira.cachoPronto(apoio()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!parreira.colher(servidor, player, apoio())) return InteractionResult.PASS;
        discard();
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }

    @Override
    public boolean hurtClient(DamageSource source) { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean canUsePortal(boolean allowVehicles) { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public ItemStack getPickResult() { return new ItemStack(IntoxicantesMod.UVA); }

    @Override
    public void move(MoverType type, Vec3 movement) {}

    @Override
    public void push(double x, double y, double z) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}
}
