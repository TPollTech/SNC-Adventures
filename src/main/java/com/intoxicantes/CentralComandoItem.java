package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * O CONTROLE (v1.2.38) — a Central de Comando virou ITEM de verdade:
 * um controle remoto que você segura, APONTA pro painel de LED (ou pro
 * letreiro do mercado) e clica pra abrir a tela de edição. Nada de clicar
 * no bloco — quem edita é o controle, igual TV de verdade.
 *
 * Regras:
 *  - painel de LED: clique com o controle mirando no painel (ou linha) →
 *    abre a Central do painel-cabeça;
 *  - letreiro do mercado: mesmo esquema; se estiver TRANCADO, o controle
 *    só abre com a CHAVE (comparador) na MÃO SECUNDÁRIA — a principal
 *    está ocupada segurando o controle;
 *  - nada mirado: avisa que tem que apontar pra um display.
 *
 * O alcance é 8 blocos (o mesmo do payload; o servidor revalida tudo —
 * o client é só pintura, regra de negócio é dele).
 */
public class CentralComandoItem extends Item {

    /** Alcance de edição (sincronizado com o guard do payload). */
    public static final double ALCANCE = 8.0;

    public CentralComandoItem(Properties properties) {
        super(properties.rarity(Rarity.EPIC));
    }

    /** Clique no bloco com o controle na mão. */
    @Override
    public InteractionResult useOn(UseOnContext contexto) {
        Level level = contexto.getLevel();
        BlockPos pos = contexto.getClickedPos();
        Player player = contexto.getPlayer();
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) != null) {
            if (abrirNoAlvo(sp, level, pos)) {
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return InteractionResult.PASS;
    }

    /** Clique no ar: tenta o raytrace estendido (8 blocos) atrás do mira. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand mao) {
        if (player instanceof ServerPlayer sp) {
            BlockHitResult hit = getPlayerPOVHitResult(level, sp, ClipContext.Fluid.NONE);
            if (abrirNoAlvo(sp, level, hit.getBlockPos())) {
                return InteractionResult.SUCCESS_SERVER;
            }
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "item.intoxicantes.central_comando.sem_alvo"));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Abre a Central pro display mirado (painel de LED OU letreiro), com as
     * regras de trava. Retorna true se abriu (pra segurar o SUCCESS).
     */
    private boolean abrirNoAlvo(ServerPlayer sp, Level level, BlockPos pos) {
        // PAINEL DE LED: abre sempre pela cabeça da linha
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PainelLedBlockEntity painel) {
            BlockPos cabeca = PainelLedBlock.acharCabeca(level, pos,
                    level.getBlockState(pos));
            if (level.getBlockEntity(cabeca) instanceof PainelLedBlockEntity dono) {
                tocarClique(level, pos);
                CentralComandoNetworking.abrir(sp, dono);
                return true;
            }
            // painel sem BE de linha (raro): o próprio serve
            tocarClique(level, pos);
            CentralComandoNetworking.abrir(sp, painel);
            return true;
        }
        // LETREIRO DO MERCADO: a trava vale aqui também
        if (be instanceof PlacaEsquinaoBlockEntity placa) {
            if (placa.isLinkMercado() && placa.isTrancada()
                    && !sp.getOffhandItem().is(net.minecraft.world.item.Items.COMPARATOR)) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "block.intoxicantes.placa_esquinao.trancada"));
                return false;
            }
            tocarClique(level, pos);
            CentralComandoNetworking.abrir(sp, placa);
            return true;
        }
        return false;
    }

    /** O clique do botão do controle (a "tv click" do botãozinho). */
    private static void tocarClique(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.PLAYERS, 0.4F, 1.6F);
    }
}
