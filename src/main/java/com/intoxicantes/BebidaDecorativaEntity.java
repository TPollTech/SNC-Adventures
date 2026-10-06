package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Uma bebida ou alimento apoiado, sem rotação automática, despawn ou coleta por proximidade.
 * O ItemStack completo é a fonte da verdade: colocar e recolher não consomem
 * nada e não repetem os efeitos de saúde, embriaguez ou os recipientes.
 */
public class BebidaDecorativaEntity extends Entity {
    // Envolve a diagonal do maior modelo (5,6 × 5,6) em QUALQUER rotação,
    // inclusive 45 graus; a caixa não pode deixar o vidro entrar na parede.
    public static final float LARGURA = 0.51F;
    public static final float ALTURA = 0.82F;
    private static final EntityDataAccessor<ItemStack> BEBIDA =
            SynchedEntityData.defineId(BebidaDecorativaEntity.class, EntityDataSerializers.ITEM_STACK);

    private BlockPos apoio = BlockPos.ZERO;

    public BebidaDecorativaEntity(EntityType<? extends BebidaDecorativaEntity> tipo, Level level) {
        super(tipo, level);
        setNoGravity(true);
        noPhysics = true;
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEBIDA, ItemStack.EMPTY);
    }

    /** Configura antes do spawn, copiando uma unidade com todos os componentes. */
    public void configurar(ItemStack stack, BlockPos suporte, Vec3 base, float rotacao) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BebidaColocavelItem)) {
            throw new IllegalArgumentException("A decoração precisa de um item colocável");
        }
        entityData.set(BEBIDA, stack.copyWithCount(1));
        apoio = suporte.immutable();
        setPos(base);
        setYRot(rotacao);
        setOldPosAndRot();
    }

    public ItemStack getBebida() {
        return entityData.get(BEBIDA).copy();
    }

    /** Derivada do item: saves antigos de garrafas conservam as mesmas medidas. */
    public BebidaColocavelItem.Exposicao getExposicao() {
        if (entityData != null && entityData.get(BEBIDA).getItem() instanceof BebidaColocavelItem item) {
            return item.getExposicao();
        }
        return BebidaColocavelItem.GARRAFA;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return getExposicao().dimensoes();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (BEBIDA.equals(accessor)) {
            // Spawn, recarga e sincronização do cliente recalculam a caixa.
            // noPhysics conserva a posição exata sobre o apoio ao redimensionar.
            refreshDimensions();
        }
    }

    public BlockPos getApoio() {
        return apoio;
    }

    /** Toda a base deve ter sustentação, não apenas o pixel central. */
    public boolean temApoio() {
        if (!level().hasChunkAt(apoio)) {
            return false;
        }
        double meio = getExposicao().larguraApoio() / 2.0 - 0.005;
        VoxelShape base = Shapes.create(new AABB(getX() - meio, getY() - 0.004,
                getZ() - meio, getX() + meio, getY() - 0.001, getZ() + meio));
        VoxelShape superficie = level().getBlockState(apoio)
                .getCollisionShape(level(), apoio, CollisionContext.empty()).move(apoio);
        return !Shapes.joinIsNotEmpty(base, superficie, BooleanOp.ONLY_FIRST);
    }

    /** Sem blocos atravessando o modelo, borda do mundo ou outro item no lugar. */
    public boolean temEspaco() {
        AABB caixa = getBoundingBox().deflate(0.001);
        return level().isInWorldBounds(blockPosition())
                && level().isInWorldBounds(BlockPos.containing(getX(), caixa.maxY, getZ()))
                && level().getWorldBorder().isWithinBounds(caixa)
                && level().noBlockCollision(this, caixa)
                && level().getEntitiesOfClass(BebidaDecorativaEntity.class,
                        caixa.inflate(0.015), outra -> outra != this && !outra.isRemoved()).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel servidor && tickCount % 5 == 0) {
            // Não interpretar um chunk descarregado como suporte destruído.
            if (!level().hasChunkAt(apoio)) return;
            if (entityData.get(BEBIDA).isEmpty()) {
                discard();
            } else if (!temApoio() || !level().noBlockCollision(this, getBoundingBox().deflate(0.001))) {
                soltarBebida(servidor, true);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (isRemoved() || player.isSpectator() || !player.mayBuild()
                || !level().mayInteract(player, apoio)
                || !player.isWithinEntityInteractionRange(this, 0.0)) {
            return InteractionResult.FAIL;
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel servidor)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack retirada = retirarBebida();
        if (retirada.isEmpty()) return InteractionResult.FAIL;
        // A mão que acabou de interagir está vazia, inclusive se o resto do
        // inventário está cheio; não existe sobra a perder nem coleta dupla.
        player.setItemInHand(hand, retirada);
        servidor.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.3F, 1.25F);
        gameEvent(GameEvent.ENTITY_INTERACT, player);
        discard();
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableToBase(source)) return false;
        if (source.getEntity() instanceof Player player) {
            if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, apoio)) {
                return false;
            }
            if (source.getDirectEntity() == player
                    && !player.isWithinAttackRange(player.getWeaponItem(), getBoundingBox(), 0.0)) {
                return false;
            }
            return soltarBebida(level, !player.hasInfiniteMaterials());
        }
        return soltarBebida(level, true);
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return !isRemoved();
    }

    /** /kill também devolve o conteúdo; unload de chunk nunca é uma quebra. */
    @Override
    public void kill(ServerLevel level) {
        soltarBebida(level, true);
    }

    private ItemStack retirarBebida() {
        ItemStack retirada = entityData.get(BEBIDA);
        entityData.set(BEBIDA, ItemStack.EMPTY);
        return retirada;
    }

    private boolean soltarBebida(ServerLevel level, boolean devolver) {
        if (isRemoved()) return false;
        ItemStack retirada = retirarBebida();
        if (devolver && !retirada.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.05, getZ(), retirada);
            drop.setDefaultPickUpDelay();
            if (!level.addFreshEntity(drop)) {
                entityData.set(BEBIDA, retirada);
                return false;
            }
        }
        gameEvent(GameEvent.ENTITY_DIE);
        discard();
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Bebida", ItemStack.OPTIONAL_CODEC, entityData.get(BEBIDA));
        output.store("Apoio", BlockPos.CODEC, apoio);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        ItemStack stack = input.read("Bebida", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        // A entidade tem um único lugar de exposição. Um save modificado não
        // deve criar pilhas inteiras a partir de uma garrafa decorativa.
        entityData.set(BEBIDA, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        apoio = input.read("Apoio", BlockPos.CODEC)
                .orElseGet(() -> BlockPos.containing(getX(), getY() - 0.01, getZ()));
        setNoGravity(true);
    }

    @Override
    public ItemStack getPickResult() {
        return getBebida();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return false;
    }

    /** Pistões removem o apoio; não deslocam o vidro para dentro da parede. */
    @Override
    public void move(MoverType type, Vec3 movement) {
    }

    @Override
    public void push(double x, double y, double z) {
    }
}
