package com.intoxicantes;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * v1.2.58 — O COQUEIRO (feature de worldgen): tronco curvado de 4-6 blocos
 * (cada um acima pende 1 pro lado, como palmeira de praia de verdade) +
 * coroa de folhas no topo com 1-2 cocos pendurados. Nasce em areia.
 *
 * No 26.3 a Feature é INTERFACE (sem NoneFeatureConfiguration): o JSON
 * {"type": "intoxicantes:coqueiro", "config": {}} resolve pelo MapCodec
 * registrado em FEATURE_TYPE e chama place() abaixo.
 */
public final class CoqueiroFeature implements Feature {

    public static final MapCodec<CoqueiroFeature> CODEC = MapCodec.unit(CoqueiroFeature::new);

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator gerador,
            RandomSource rng, BlockPos base) {
        // só nasce em areia (a praia é o endereço do coqueiro)
        if (!level.getBlockState(base.below()).is(net.minecraft.world.level.block.Blocks.SAND)) {
            return false;
        }
        int altura = 4 + rng.nextInt(3); // 4-6 de tronco

        // espaço livre: coluna do tronco
        for (int i = 0; i < altura + 1; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) {
                return false;
            }
        }

        // tronco curvado: cada bloco acima pende 1 no eixo sorteado
        Direction inclinacao = Direction.Plane.HORIZONTAL.getRandomDirection(rng);
        BlockPos topo = base;
        for (int i = 0; i < altura; i++) {
            topo = base.above(i);
            if (i > 0 && rng.nextInt(2) == 0) {
                topo = topo.relative(inclinacao);
            }
            level.setBlock(topo, IntoxicantesMod.COQUEIRO_TRONCO.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, inclinacao.getAxis()), 3);
        }

        // a coroa: anel 3x3 de folhas no topo + 1 acima
        BlockPos coroa = topo.above();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos folha = coroa.offset(dx, 0, dz);
                if (level.getBlockState(folha).isAir()) {
                    level.setBlock(folha, IntoxicantesMod.COQUEIRO_FOLHAS.defaultBlockState(), 3);
                }
            }
        }
        if (level.getBlockState(coroa.above()).isAir()) {
            level.setBlock(coroa.above(), IntoxicantesMod.COQUEIRO_FOLHAS.defaultBlockState(), 3);
        }

        // os cocos: 2-4 pendurados sob a coroa (média 3; o pé é generoso,
        // a garrafa ainda vence no total — v1.2.58 balanceado)
        int cocos = 2 + rng.nextInt(3);
        for (int i = 0; i < cocos; i++) {
            Direction lado = Direction.Plane.HORIZONTAL.getRandomDirection(rng);
            BlockPos coco = coroa.below().relative(lado);
            if (level.getBlockState(coco).isAir()) {
                level.setBlock(coco, IntoxicantesMod.COCO_BLOCO.defaultBlockState(), 3);
            }
        }
        return true;
    }
}
