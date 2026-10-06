package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.intoxicantes.energia.EnergiaRedes;

/**
 * v1.2.71 — A TOMADA DE PAREDE (módulo 4): não consome energia sozinha —
 * é o PONTO ENERGIZADO onde máquinas e ferramentas elétricas futuras
 * engatam (o "plug" da instalação). A topologia do quadro a alcança como
 * alcança o soquete: cabos até ela, interruptor a jusante corta.
 *
 * ENERGIZADA é o espelho do ACESA do soquete (a rede escreve a cada medição:
 * 1×/s). O multímetro lê o estado + o circuito que a alimenta.
 */
public class TomadaBlock extends BaseEntityBlock {

    /** A tomada está recebendo energia da rede (a rede escreve). */
    public static final BooleanProperty ENERGIZADA = BlockStateProperties.LIT;
    /** Encostada na parede (qual face do bloco de trás). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTE = Block.box(3.0, 2.0, 12.0, 13.0, 14.0, 16.0);
    private static final VoxelShape SUL = Block.box(3.0, 2.0, 0.0, 13.0, 14.0, 4.0);
    private static final VoxelShape OESTE = Block.box(12.0, 2.0, 3.0, 16.0, 14.0, 13.0);
    private static final VoxelShape LESTE = Block.box(0.0, 2.0, 3.0, 4.0, 14.0, 13.0);

    public TomadaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENERGIZADA, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENERGIZADA);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction frente = clickedFace.getAxis().isHorizontal()
                ? clickedFace : context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, frente);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTE;
            case SOUTH -> SUL;
            case WEST -> LESTE;
            case EAST -> OESTE;
            default -> NORTE;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TomadaBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        EnergiaRedes.marcarRebuildProximo(level, pos,
                QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity colocador, ItemStack stack) {
        super.setPlacedBy(level, pos, state, colocador, stack);
        if (level instanceof ServerLevel servidor) {
            EnergiaRedes.marcarRebuildProximo(servidor, pos,
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    /**
     * A BE DA TOMADA: só guarda o circuito que a alcançou (escrito pela rede
     * na travessia) pra leitura honesta no multímetro.
     */
    public static class TomadaBlockEntity extends BlockEntity {

        /** O circuito do quadro que alcançou esta tomada (-1 = nenhum). */
        private int circuito = -1;

        public TomadaBlockEntity(BlockPos pos, BlockState state) {
            super(IntoxicantesMod.TOMADA_ENTITY, pos, state);
        }

        public int circuito() {
            return circuito;
        }

        public void setCircuito(int circuito) {
            if (this.circuito != circuito) {
                this.circuito = circuito;
                setChanged();
            }
        }

        /** Durante CHUNK_LOAD o chunk ainda não pode ser buscado via level. */
        public void limparCircuitoCarregado(net.minecraft.world.level.chunk.LevelChunk chunk) {
            if (circuito != -1) {
                circuito = -1;
                chunk.markUnsaved();
            }
        }

        /** O nome do circuito que alimenta (ou "—"). */
        public String nomeCircuito() {
            if (!(level instanceof ServerLevel servidor)) {
                return "—";
            }
            var quadro = EnergiaRedes.quadroDaRede(servidor, worldPosition);
            int indice = quadro == null ? -1 : quadro.circuitoDoPonto(worldPosition);
            if (indice >= 0 && indice < quadro.circuitos().size()) {
                return quadro.circuitos().get(indice).nome;
            }
            return "—";
        }

        /** Recebendo energia agora? (estado do bloco) */
        public boolean energizada() {
            return level != null && level.getBlockState(worldPosition).hasProperty(ENERGIZADA)
                    && level.getBlockState(worldPosition).getValue(ENERGIZADA);
        }

        /** A rede pediu: energiza/desenergiza (blockstate UPDATE_CLIENTS). */
        public void energizar(boolean ligada) {
            if (level instanceof ServerLevel && level.getBlockEntity(worldPosition) == this
                    && level.getBlockState(worldPosition).hasProperty(ENERGIZADA)
                    && level.getBlockState(worldPosition).getValue(ENERGIZADA) != ligada) {
                level.setBlock(worldPosition,
                        level.getBlockState(worldPosition).setValue(ENERGIZADA, ligada),
                        Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("circuito", circuito);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            circuito = input.getIntOr("circuito", -1);
            marcarRede();
        }

        private void marcarRede() {
            if (level instanceof ServerLevel servidor) {
                EnergiaRedes.marcarRebuildProximo(servidor, worldPosition,
                        QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
            }
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
    }
}
