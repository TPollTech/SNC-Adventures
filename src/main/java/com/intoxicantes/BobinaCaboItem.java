package com.intoxicantes;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.intoxicantes.energia.CabosSuspensos;
import com.intoxicantes.energia.EloCabo;
import com.intoxicantes.energia.EnergiaRedes;

/**
 * v1.2.80 — A BOBINA DE CABO (o "wire spool" do Immersive Engineering).
 *
 * Uso de um conector para o outro, com a bobina da bitola do trecho:
 *   1º clique  — fixa a ORIGEM no conector;
 *   2º clique  — estende o cabo até o outro conector (até 32 blocos de vão);
 *   agachado  — recolhe o cabo e devolve a bobina.
 *
 * Uma bobina monta UM trecho inteiro de qualquer comprimento dentro do
 * alcance (o cobre é medido em metros de jogo só no cabo de bloco, que
 * gasta 1 unidade por bloco); cortar devolve a bobina, então errar a
 * bitola não destrói cobre. A energia trafega pelo elo com o mesmo limite
 * de corrente da bitola — o quadro sabe que o vão existe.
 */
public class BobinaCaboItem extends Item {

    /** A bitola desta bobina (mm²). */
    private final double bitola;

    public BobinaCaboItem(Properties properties, double bitola) {
        super(properties);
        this.bitola = bitola;
    }

    public double bitola() {
        return bitola;
    }

    /** A bobina de uma bitola (registrada com a família elétrica). */
    public static Item da(double bitola) {
        if (bitola <= 1.5) return IntoxicantesMod.BOBINA_COBRE_1_5MM;
        if (bitola <= 2.5) return IntoxicantesMod.BOBINA_COBRE_2_5MM;
        if (bitola <= 4.0) return IntoxicantesMod.BOBINA_COBRE_4MM;
        if (bitola <= 6.0) return IntoxicantesMod.BOBINA_COBRE_6MM;
        return IntoxicantesMod.BOBINA_COBRE_10MM;
    }

    @Override
    public InteractionResult useOn(UseOnContext contexto) {
        Level level = contexto.getLevel();
        Player jogador = contexto.getPlayer();
        if (jogador == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel servidor)) return InteractionResult.SUCCESS;
        BlockPos pos = contexto.getClickedPos();
        BlockState estado = level.getBlockState(pos);
        if (!(estado.getBlock() instanceof ConectorEletricoBlock)) {
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.sem_conector",
                    estado.getBlock().getName()));
            return InteractionResult.SUCCESS;
        }
        Direction face = estado.getValue(ConectorEletricoBlock.FACING);
        ItemStack mao = contexto.getItemInHand();
        if (jogador.isSpectator() || !jogador.mayBuild()
                || !jogador.mayUseItemAt(pos, face, mao) || !level.mayInteract(jogador, pos))
            return InteractionResult.FAIL;
        CabosSuspensos cabos = CabosSuspensos.get(servidor);

        // agachado: recolhe o cabo e devolve a bobina
        if (jogador.isCrouching()) {
            EloCabo elo = cabos.removerEm(pos);
            if (elo == null) {
                jogador.sendSystemMessage(Component.translatable(
                        "eletricidade.intoxicantes.bobina.nada_a_recolher"));
                return InteractionResult.SUCCESS;
            }
            level.playSound(null, pos, SoundEvents.COPPER_HIT, SoundSource.BLOCKS, 0.6F, 1.4F);
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.recolhido", elo.bitola()));
            mao.remove(IntoxicantesMod.TIPO_ORIGEM_ELO);
            if (!jogador.hasInfiniteMaterials()) {
                ItemStack devolvida = new ItemStack(da(elo.bitola()));
                jogador.getInventory().placeItemBackInInventory(devolvida, false,
                        net.minecraft.util.Prediction.SERVER_ONLY);
            }
            marcarRede(servidor, elo);
            return InteractionResult.SUCCESS;
        }

        EloCabo.Extremo origem = mao.get(IntoxicantesMod.TIPO_ORIGEM_ELO);
        if (origem == null) {
            mao.set(IntoxicantesMod.TIPO_ORIGEM_ELO,
                    new EloCabo.Extremo(pos.immutable(), face, level.dimension()));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.5F, 1.6F);
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.origem", bitola,
                    CabosSuspensos.ALCANCE_MAX));
            return InteractionResult.SUCCESS;
        }
        if (!origem.dimensao().equals(level.dimension()) || !servidor.isLoaded(origem.pos())
                || !CabosSuspensos.conectorVivo(servidor, origem.pos(), origem.face())) {
            mao.remove(IntoxicantesMod.TIPO_ORIGEM_ELO);
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.origem_invalida"));
            return InteractionResult.SUCCESS;
        }
        if (!level.mayInteract(jogador, origem.pos())
                || !jogador.mayUseItemAt(origem.pos(), origem.face(), mao)) return InteractionResult.FAIL;
        if (origem.pos().equals(pos)) {
            mao.remove(IntoxicantesMod.TIPO_ORIGEM_ELO);
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.cancelada"));
            return InteractionResult.SUCCESS;
        }
        EloCabo elo = new EloCabo(origem.pos(), origem.face(), pos.immutable(), face, bitola);
        if (elo.comprimento() > CabosSuspensos.ALCANCE_MAX) {
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.longe", elo.comprimento(),
                    CabosSuspensos.ALCANCE_MAX));
            return InteractionResult.SUCCESS;
        }
        if (!cabos.adicionar(elo)) {
            jogador.sendSystemMessage(Component.translatable(
                    "eletricidade.intoxicantes.bobina.ja_existe"));
            return InteractionResult.SUCCESS;
        }
        mao.remove(IntoxicantesMod.TIPO_ORIGEM_ELO);
        mao.consume(1, jogador);
        level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.7F, 0.9F);
        jogador.sendSystemMessage(Component.translatable(
                "eletricidade.intoxicantes.bobina.ligado", bitola, elo.amperagem(),
                elo.comprimento()));
        marcarRede(servidor, elo);
        return InteractionResult.SUCCESS;
    }

    /** Qualquer elo toca a rede: quem reconstrói é o quadro (BFS). */
    private static void marcarRede(ServerLevel level, EloCabo elo) {
        EnergiaRedes.marcarRebuildProximo(level, elo.a(),
                QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        // o cabo precisa APARECER no mesmo clique: sem esperar a cadência
        CabosNetworking.sincronizarAgora(level);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext contexto,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> dica, TooltipFlag flag) {
        dica.accept(Component.translatable("eletricidade.intoxicantes.bobina.bitola",
                bitola, CaboEletricoBlock.amperagemDaBitola(bitola)).withStyle(ChatFormatting.GRAY));
        EloCabo.Extremo origem = stack.get(IntoxicantesMod.TIPO_ORIGEM_ELO);
        if (origem != null) {
            dica.accept(Component.translatable("eletricidade.intoxicantes.bobina.pendente",
                    origem.pos().getX(), origem.pos().getY(), origem.pos().getZ())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}