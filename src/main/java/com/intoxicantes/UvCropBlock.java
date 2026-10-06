package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Cultura que cresce em 5 estagios (age 0..4) e ainda precisa AMADURECER
 * sob luz forte (sol direto ou lampada UV) em 4 niveis (uv_age 0..3).
 * A colheita com uv_age=3 rende o produto de melhor qualidade.
 */
public class UvCropBlock extends CropBlock {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 4);
    public static final IntegerProperty UV_AGE = IntegerProperty.create("uv_age", 0, 3);

    private final boolean aceitaSoloComum;

    public UvCropBlock(Properties properties, boolean aceitaSoloComum) {
        super(properties);
        this.aceitaSoloComum = aceitaSoloComum;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 4;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        // CropBlock stops random ticks at max age, but our crop must still ripen.
        return state.getValue(AGE) < getMaxAge() || state.getValue(UV_AGE) < 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, UV_AGE);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (aceitaSoloComum) {
            return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.FARMLAND);
        }
        return super.mayPlaceOn(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < getMaxAge()) {
            if (level.getRawBrightness(pos, 0) < 9) {
                return; // escuro demais: nao cresce
            }
            float speed = velocidadeCrescimento(level, pos);
            if (random.nextFloat() < speed) {
                growCrops(level, pos, state);
            }
        } else {
            int uv = state.getValue(UV_AGE);
            if (uv < 3 && IntoxicantesMod.isUvLit(level, pos)
                    && random.nextFloat() < ModConfig.get().uvChanceMaturacao) {
                level.setBlock(pos, state.setValue(UV_AGE, uv + 1), 2);
                if (uv + 1 == 3 && ModConfig.get().uvCueMaturacao) {
                    // CUE de maturacao: amadureceu AGORA — som suave + fagulhas
                    // douradas. Quem estiver perto da plantacao SABE que vale voltar
                    // (e quem estiver longe descobre pelo brilho do bloco).
                    level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 0.5F, 1.6F);
                    level.sendParticles(ParticleTypes.END_ROD,
                            pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                            6, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
    }

    /**
     * v1.2.15: velocidade de crescimento do randomTick — o BOOST ×2 é
     * EXCLUSIVO da lâmpada UV (sol é sol; a fazenda ao ar livre anda no ritmo
     * vanilla e a tecnologia do mod é que vale o investimento — report do
     * beta tester: "as plantas crescem bem rápido"). Package-private: o game
     * test verifica a conta exata.
     */
    float velocidadeCrescimento(ServerLevel level, BlockPos pos) {
        float speed = level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                ? getGrowthSpeed(this, level, pos)
                : 0.15F; // solo comum: cresce mais devagar
        if (IntoxicantesMod.temLampadaUvPerto(level, pos)) {
            speed *= 2.0F;
        }
        return speed;
    }

    /** Brilho ambiente DISCRETO na planta pronta: fagulhas ocasionais. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean madura = state.getValue(AGE) == getMaxAge() && state.getValue(UV_AGE) >= 3;
        if (madura && random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + 0.6 + random.nextDouble() * 0.4,
                    pos.getZ() + random.nextDouble(),
                    0.0, 0.01, 0.0);
        }
        // v1.2.7: folhinha caindo da planta no ponto — quem vê a folha sabe que
        // vale colher (particula propria do mod, física de folha no client)
        if (madura && random.nextInt(20) == 0) {
            level.addParticle(Particulas.FOLHA,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.7 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0.0, -0.02, 0.0);
        }
    }

    /**
     * Colheita na mao: se a planta saiu no ponto (AGE max + UV max), o fregues
     * capta a conquista Colheita Perfeita. Roda antes da quebra (o bloco ainda
     * existe, da pra ler o estado certo).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp
                && state.getValue(AGE) == getMaxAge() && state.getValue(UV_AGE) >= 3) {
            Progressoes.conceder(sp, Progressoes.COLHEITA_PERFEITA);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
