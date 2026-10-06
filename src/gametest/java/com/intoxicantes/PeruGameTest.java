package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Pares particulares (gerenciado=false), sem modificar reservas ou a economia global. */
public class PeruGameTest {
    @GameTest
    public void parConservaUuidEVinculoAoSalvarERecarregar(GameTestHelper helper) {
        piso(helper);
        var nivel = helper.getLevel();
        var peru = new PeruEntity(IntoxicantesMod.PERU, nivel);
        var moto = new MotoPeruEntity(IntoxicantesMod.MOTO_PERU, nivel);
        posicionar(moto, helper.absolutePos(new BlockPos(5, 2, 5)));
        posicionar(peru, helper.absolutePos(new BlockPos(7, 2, 5)));
        peru.vincularMoto(moto, false);
        var peruId = peru.getUUID();
        var motoId = moto.getUUID();
        var peruSaida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, nivel.registryAccess());
        var motoSaida = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, nivel.registryAccess());
        peru.saveWithoutId(peruSaida);
        moto.saveWithoutId(motoSaida);
        var peruReload = new PeruEntity(IntoxicantesMod.PERU, nivel);
        var motoReload = new MotoPeruEntity(IntoxicantesMod.MOTO_PERU, nivel);
        peruReload.load(TagValueInput.create(ProblemReporter.DISCARDING, nivel.registryAccess(), peruSaida.buildResult()));
        motoReload.load(TagValueInput.create(ProblemReporter.DISCARDING, nivel.registryAccess(), motoSaida.buildResult()));
        helper.assertTrue(peruId.equals(peruReload.getUUID()) && motoId.equals(motoReload.getUUID()),
                "Identidades permanecem iguais depois do save/load");
        helper.assertTrue(motoId.equals(peruReload.motoUUID()) && peruId.equals(motoReload.dono())
                        && !peruReload.gerenciado(), "Vínculo bilateral e origem particular sobrevivem ao save");
        helper.assertTrue(nivel.addFreshEntity(motoReload) && nivel.addFreshEntity(peruReload),
                "Par recarregado entra no mundo uma única vez");
        helper.assertTrue(peruReload.moto() == motoReload, "UUID persistido resolve a contraparte real carregada");
        helper.succeed();
    }

    @GameTest
    public void motoRecusaJogadorEPeruAlheioEConservaSeuUnicoPiloto(GameTestHelper helper) {
        piso(helper);
        var nivel = helper.getLevel();
        var dono = new PeruEntity(IntoxicantesMod.PERU, nivel);
        var estranho = new PeruEntity(IntoxicantesMod.PERU, nivel);
        var moto = new MotoPeruEntity(IntoxicantesMod.MOTO_PERU, nivel);
        posicionar(moto, helper.absolutePos(new BlockPos(5, 2, 5)));
        posicionar(dono, helper.absolutePos(new BlockPos(7, 2, 5)));
        posicionar(estranho, helper.absolutePos(new BlockPos(9, 2, 5)));
        dono.vincularMoto(moto, false);
        helper.assertFalse(estranho.startRiding(moto), "Peru de outro par não pode tomar a moto");
        var jogador = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(jogador.startRiding(moto), "Jogador não ganha veículo público pela interação");
        helper.assertTrue(dono.startRiding(moto) && moto.getPassengers().size() == 1
                        && moto.getFirstPassenger() == dono, "Dono verdadeiro é o único piloto");
        helper.assertFalse(estranho.startRiding(moto), "Moto ocupada não aceita um segundo passageiro");
        dono.stopRiding();
        helper.assertTrue(moto.getPassengers().isEmpty(), "Desmontagem libera o assento");
        helper.succeed();
    }

    @GameTest
    public void percursoRecusaAguaParedeEBuracoEAceitaEstradaDeTerra(GameTestHelper helper) {
        piso(helper);
        var moto = new MotoPeruEntity(IntoxicantesMod.MOTO_PERU, helper.getLevel());
        BlockPos origem = helper.absolutePos(new BlockPos(5, 2, 5));
        posicionar(moto, origem);
        Vec3 alvo = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 5)));
        helper.assertTrue(moto.caminhoLivre(alvo), "Rota ampla e apoiada em piso sólido é utilizável");
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.STONE);
        helper.assertFalse(moto.caminhoLivre(alvo), "Obstáculo no volume da moto/piloto bloqueia rota");
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.WATER);
        helper.assertFalse(moto.caminhoLivre(alvo), "Água bloqueia mesmo sem colisão de bloco sólido");
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.AIR);
        helper.setBlock(new BlockPos(7, 1, 4), Blocks.AIR);
        helper.assertFalse(moto.caminhoLivre(alvo), "Buraco ao lado do centro também falta apoio na caixa inteira");
        helper.setBlock(new BlockPos(7, 1, 4), Blocks.STONE);
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
        // Dirt path mede 15/16 de altura: conservar contato real da roda com a superfície.
        moto.setPos(origem.getX() + .5, origem.getY() - .0625, origem.getZ() + .5);
        helper.assertTrue(moto.caminhoLivre(alvo.add(0, -.0625, 0)),
                "Estrada de terra rebaixada é suporte real, embora solidRender seja falso");
        helper.succeed();
    }

    private static void posicionar(net.minecraft.world.entity.Entity entidade, BlockPos pos) {
        entidade.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }
    private static void piso(GameTestHelper helper) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }
}
