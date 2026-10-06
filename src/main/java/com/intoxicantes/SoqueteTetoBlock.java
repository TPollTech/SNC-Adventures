package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.intoxicantes.energia.EnergiaRedes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;


/**
 * v1.2.70 — O SOQUETE DE TETO (a "boca de luz"): separa SOQUETE de LÂMPADA.
 * Coloca o soquete, clica com a lâmpada LED pra instalar, clica de novo pra
 * trocar/remover. A lâmpada instalada é uma STACK COMPLETA (componentes
 * preservados) gravada na BE.
 *
 * ACESA: propriedade no BLOCKSTATE (a rede escreve quando o circuito do
 * soquete está energizado + interruptor a jusante ON). Luz vem da propriedade
 * lightLevel (mesmo caminho do PainelLed — reativo, sem BE pra luz).
 *
 * O CONSUMO (W da lâmpada → E/t da rede) é contabilizado pela REDE ao
 * percorrer os soquetes energizados; se a energia acabar, a rede apaga.
 */
public class SoqueteTetoBlock extends BaseEntityBlock {

    /** A lâmpada instalada está acesa (a rede decide; o estado acende). */
    public static final BooleanProperty ACESA = BlockStateProperties.LIT;
    /** Emissão da lâmpada instalada; 10 conserva o estado dos saves antigos. */
    public static final IntegerProperty BRILHO = IntegerProperty.create("brilho", 0, 15);

    private static final VoxelShape SHAPE =
            Block.box(5.0, 9.0, 5.0, 11.0, 16.0, 11.0);

    public SoqueteTetoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACESA, Boolean.FALSE)
                .setValue(BRILHO, 10));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACESA, BRILHO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(ACESA, Boolean.FALSE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoqueteBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /**
     * Clique com lâmpada LED: instala/troca. Clique vazio: remove a lâmpada
     * (devolve pro inventário). Clique com outra coisa: nada.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(stack.getItem() instanceof BlockItem)
                || !(Block.byItem(stack.getItem()) instanceof LedPotenciaBlock led)
                || LedPotenciaBlock.familiaDoBloco(led) != LedPotenciaBlock.Familia.BULBO) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof SoqueteBlockEntity soquete)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        ItemStack antiga = soquete.getLampada();
        ItemStack nova = stack.copyWithCount(1);
        soquete.setLampada(nova);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!antiga.isEmpty()) {
            if (!player.getInventory().add(antiga)) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, antiga));
            }
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS, 0.6F, 1.1F);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoqueteBlockEntity soquete)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.PASS;
        ItemStack lampada = soquete.getLampada();
        if (lampada.isEmpty()) {
            return InteractionResult.PASS;
        }
        soquete.setLampada(ItemStack.EMPTY);
        soquete.apagar();
        if (!player.getInventory().add(lampada)) {
            level.addFreshEntity(new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lampada));
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS, 0.6F, 0.9F);
        return InteractionResult.SUCCESS_SERVER;
    }

    // A LUZ vem da lambda lightLevel do registro (lê ACESA do estado) —
    // no 26.3 não existe getLight sobrescrevível (mesmo caminho da família LED).

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState antigo, boolean movido) {
        super.onPlace(state, level, pos, antigo, movido);
        if (state.getBlock() != antigo.getBlock() && level instanceof ServerLevel servidor) {
            EnergiaRedes.marcarRebuildProximo(servidor, pos,
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        EnergiaRedes.marcarRebuildProximo(level, pos,
                QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    /**
     * A BE do soquete: guarda a lâmpada instalada (stack completa) e pede o
     * rebuild da rede quando muda (consumo novo na rede).
     */
    public static class SoqueteBlockEntity extends BlockEntity {

        private ItemStack lampada = ItemStack.EMPTY;

        public SoqueteBlockEntity(BlockPos pos, BlockState state) {
            super(IntoxicantesMod.SOQUETE_TETO_ENTITY, pos, state);
        }

        public ItemStack getLampada() {
            return lampada.copy();
        }

        public void setLampada(ItemStack stack) {
            this.lampada = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            setChanged();
            atualizarBrilho();
            if (consumoW() == 0L) apagar();
            if (level instanceof ServerLevel servidor) {
                servidor.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                        Block.UPDATE_CLIENTS);
            }
            marcarRede();
        }

        private void marcarRede() {
            if (level instanceof ServerLevel servidor) {
                EnergiaRedes.marcarRebuildProximo(servidor, worldPosition,
                        QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
            }
        }

        /** A potência da lâmpada instalada em W (0 se vazia). */
        public long consumoW() {
            if (lampada.isEmpty()
                    || !(Block.byItem(lampada.getItem()) instanceof LedPotenciaBlock led)) {
                return 0L;
            }
            return LedPotenciaBlock.wattsDoBloco(led);
        }

        /** A rede pediu: acende/apaga (blockstate UPDATE_CLIENTS). */
        public void acender() {
            atualizarBrilho();
            if (level instanceof ServerLevel && level.getBlockEntity(worldPosition) == this
                    && consumoW() > 0 && level.getBlockState(worldPosition).hasProperty(ACESA)
                    && !level.getBlockState(worldPosition).getValue(ACESA)) {
                level.setBlock(worldPosition,
                        level.getBlockState(worldPosition).setValue(ACESA, Boolean.TRUE),
                        Block.UPDATE_CLIENTS);
            }
        }

        private void atualizarBrilho() {
            if (!(level instanceof ServerLevel servidor)
                    || servidor.getBlockEntity(worldPosition) != this) return;
            BlockState estado = servidor.getBlockState(worldPosition);
            if (!(estado.getBlock() instanceof SoqueteTetoBlock)) return;
            long watts = consumoW();
            int brilho = watts > 0L ? LedPotenciaBlock.luzPorWatts((int) watts) : 0;
            if (estado.getValue(BRILHO) != brilho) {
                servidor.setBlock(worldPosition, estado.setValue(BRILHO, brilho), Block.UPDATE_CLIENTS);
            }
        }

        public void apagar() {
            if (level instanceof ServerLevel && level.getBlockEntity(worldPosition) == this
                    && level.getBlockState(worldPosition).hasProperty(ACESA)
                    && level.getBlockState(worldPosition).getValue(ACESA)) {
                level.setBlock(worldPosition,
                        level.getBlockState(worldPosition).setValue(ACESA, Boolean.FALSE),
                        Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            lampada = input.read("lampada", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            marcarRede();
        }

        @Override
        public void setLevel(Level nivel) {
            super.setLevel(nivel);
            marcarRede();
        }

        @Override
        public void clearRemoved() {
            super.clearRemoved();
            marcarRede();
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            marcarRede();
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
                getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (!lampada.isEmpty()) {
                output.store("lampada", ItemStack.CODEC, lampada);
            }
        }

        /** Quebrou o soquete: a lâmpada cai (nunca some). */
        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            if (level instanceof ServerLevel servidor && !lampada.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lampada));
                lampada = ItemStack.EMPTY;
            }
            super.preRemoveSideEffects(pos, state);
        }
    }
}
