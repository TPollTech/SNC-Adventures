package com.intoxicantes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** Uva perene: preserva age/uv_age dos mundos existentes, sem modificar outras culturas. */
public final class UvaParreiraBlock extends UvCropBlock implements EntityBlock {
    public UvaParreiraBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ParreiraBlockEntity(pos, state);
    }

    /**
     * Migração de chunks anteriores à parreira: suas raízes ainda não têm NBT de
     * BlockEntity. O carregamento normal só registra BEs que já existiam, e uma
     * planta AGE4/UV3 não recebe random tick para se corrigir espontaneamente.
     * Executado pelo CHUNK_LOAD, antes do envio do chunk ao cliente; não muda
     * AGE/UV e não exige um clique para que a primeira folhagem seja desenhada.
     */
    public static void migrarChunk(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        if (chunk.getLevel() != level) {
            return;
        }
        chunk.findBlocks(state -> state.getBlock() instanceof UvaParreiraBlock, (pos, state) -> {
            BlockPos raiz = pos.immutable();
            if (chunk.getBlockEntities().containsKey(raiz)) {
                return;
            }
            // O evento pode ocorrer dentro da future de conversão para FULL.
            // Usar o próprio chunk evita pedir sua carga de novo através do Level.
            if (chunk.getBlockEntity(raiz, LevelChunk.EntityCreationType.IMMEDIATE)
                    instanceof ParreiraBlockEntity) {
                chunk.markUnsaved();
            }
        });
    }

    @Override
    protected boolean shouldChangedStateKeepBlockEntity(BlockState anterior) {
        // AGE/UV são atualizados no mesmo bloco. Preservar o timer e a folhagem
        // é explícito na API 26.3; trocar a raiz por outro bloco remove a BE.
        return anterior.is(this);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != IntoxicantesMod.PARREIRA_ENTITY) {
            return null;
        }
        return (mundo, pos, estado, entidade) -> {
            if (entidade instanceof ParreiraBlockEntity parreira) {
                ParreiraBlockEntity.tick(mundo, pos, estado, parreira);
            }
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // Apoio e luz controlam crescimento, nunca apagam uma planta já existente.
        return level.hasChunkAt(pos.below()) && mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return IntoxicantesMod.SEMENTE_UVA;
    }

    private int limiteCrescimento(LevelReader level, BlockPos pos) {
        var estrutura = ParreiraEstrutura.buscar(level, pos);
        int limite = estrutura.coluna().isEmpty() ? 1 : estrutura.produtiva() ? 4 : 2;
        if (level.getBlockEntity(pos) instanceof ParreiraBlockEntity be && be.ticksRebrotaRestantes() > 0) {
            limite = Math.min(limite, 3);
        }
        return limite;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        var estrutura = ParreiraEstrutura.buscar(level, pos);
        BlockPos luz = estrutura.coluna().isEmpty() ? pos : estrutura.topo().above();
        if (age < 4) {
            if (age < limiteCrescimento(level, pos) && level.getRawBrightness(luz, 0) >= 9
                    && random.nextFloat() < velocidadeCrescimento(level, pos)) {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (level.getBlockEntity(pos) instanceof ParreiraBlockEntity be) {
            be.amadurecerCachos(level, random);
        }
        int uv = state.getValue(UV_AGE);
        if (uv < 3 && estrutura.produtiva() && IntoxicantesMod.isUvLit(level, luz)
                && random.nextFloat() < ModConfig.get().uvChanceMaturacao) {
            level.setBlock(pos, state.setValue(UV_AGE, uv + 1), Block.UPDATE_CLIENTS);
            if (uv + 1 == 3 && ModConfig.get().uvCueMaturacao) {
                level.playSound(null, estrutura.topo(), SoundEvents.COMPOSTER_READY,
                        SoundSource.BLOCKS, 0.5F, 1.6F);
                level.sendParticles(ParticleTypes.END_ROD,
                        luz.getX() + 0.5, luz.getY() - 0.3, luz.getZ() + 0.5,
                        6, 0.2, 0.15, 0.2, 0.01);
            }
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        // Uma raiz UV3 ainda pode ter cachos novos com UV0. A maturação deles
        // é independente e não pode parar junto com a idade antiga da raiz.
        return true;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var legado = super.getDrops(state, params);
        var entidade = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(entidade instanceof ParreiraBlockEntity parreira) || !parreira.temCachosIndividuais()) {
            return legado;
        }
        List<ItemStack> resultado = new ArrayList<>();
        // A tabela histórica continua responsável pelas sementes. Uvas vêm
        // exclusivamente dos cachos ainda presentes, inclusive numa quebra parcial.
        for (ItemStack stack : legado) {
            if (!stack.is(IntoxicantesMod.UVA)) resultado.add(stack);
        }
        int restantes = parreira.frutosParaLoot();
        if (restantes > 0) resultado.add(new ItemStack(IntoxicantesMod.UVA, restantes));
        return resultado;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ParreiraBlockEntity parreira) {
            parreira.prepararRemocao();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(AGE) < limiteCrescimento(level, pos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        return isValidBonemealTarget(level, pos, state, source);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        growCrops(level, pos, state);
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState ignored) {
        if (!(level instanceof ServerLevel servidor) || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) {
            return;
        }
        int age = state.getValue(AGE);
        int novo = Math.min(age + getBonemealAgeIncrease(level), limiteCrescimento(level, pos));
        if (novo > age) {
            servidor.setBlock(pos, state.setValue(AGE, novo), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return interagir(player, level, InteractionHand.MAIN_HAND, hit);
    }

    /** Assinatura do UseBlockCallback; integra colheita nas cercas sem substituí-las. */
    public static boolean maoDeColheita(Player player) {
        return player.getMainHandItem().isEmpty() || player.getMainHandItem().is(IntoxicantesMod.UVA);
    }

    public static InteractionResult interagir(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
                || !maoDeColheita(player)
                || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 0.0)
                || !level.hasChunkAt(hit.getBlockPos())) {
            return InteractionResult.PASS;
        }
        BlockPos alvo = hit.getBlockPos();
        if (!ParreiraEstrutura.cercaCarregada(level, alvo)) {
            return InteractionResult.PASS;
        }
        // Uma única autoridade por apoio, compartilhada com o renderer.
        BlockPos raizDona = ParreiraEstrutura.donoDoApoio(level, alvo);
        if (raizDona == null || !level.hasChunkAt(raizDona)
                || !(level.getBlockEntity(raizDona) instanceof ParreiraBlockEntity dona)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return dona.cachoPronto(alvo)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return dona.colher((ServerLevel) level, player, alvo)
                ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
    }
}
