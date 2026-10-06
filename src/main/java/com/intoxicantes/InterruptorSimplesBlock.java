package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v1.2.70 — O INTERRUPTOR SIMPLES: a tecla da parede que corta o trecho do
 * circuito a jusante. Estado 100% no BLOCKSTATE (LIGADO + POS/POS_HORIZ) —
 * sem BE, sincroniza de graça, salva de graça, multiplayer de graça.
 *
 * A lógica elétrica (quem está a jusante dele) é decidida na REDE: um
 * interruptor DESLIGADO não deixa a travessia do quadro continuar por ele
 * (o BFS do quadro o trata como parede elétrica).
 */
public class InterruptorSimplesBlock extends Block {

    public static final BooleanProperty LIGADO = BlockStateProperties.POWERED;
    public static final EnumProperty<Direction> POS = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTE = Block.box(3.0, 5.0, 14.0, 13.0, 13.0, 16.0);

    public InterruptorSimplesBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIGADO, Boolean.FALSE)
                .setValue(POS, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGADO, POS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return this.defaultBlockState()
                .setValue(POS, face.getAxis().isHorizontal() ? face
                        : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(POS, rot.rotate(state.getValue(POS)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(POS)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(POS)) {
            case NORTH -> NORTE;
            case SOUTH -> Block.box(3, 5, 0, 13, 13, 2);
            case EAST -> Block.box(0, 5, 3, 2, 13, 13);
            case WEST -> Block.box(14, 5, 3, 16, 13, 13);
            default -> NORTE;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState antigo, boolean movido) {
        super.onPlace(state, level, pos, antigo, movido);
        if (state.getBlock() != antigo.getBlock() && level instanceof ServerLevel servidor) {
            com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(servidor, pos,
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(level, pos,
                QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    /** Clique alterna com som de "click" mecânico. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (!player.isAlive() || player.isSpectator()) return InteractionResult.PASS;
            boolean novo = !state.getValue(LIGADO);
            level.setBlock(pos, state.setValue(LIGADO, novo), Block.UPDATE_CLIENTS);
            level.playSound(null, pos,
                    novo ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.5F, novo ? 1.4F : 0.9F);
            if (level instanceof ServerLevel servidor) {
                com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(
                        servidor, pos, QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
