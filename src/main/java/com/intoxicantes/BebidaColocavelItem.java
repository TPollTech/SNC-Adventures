package com.intoxicantes;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Expõe o próprio item na mesa, preservando seus componentes e uso normal. */
public class BebidaColocavelItem extends Item {
    /** Caixas que envolvem o modelo e sua base em qualquer rotação horizontal. */
    public record Exposicao(float largura, float altura, float larguraApoio) {
        public Exposicao {
            if (!Float.isFinite(largura) || !Float.isFinite(altura)
                    || !Float.isFinite(larguraApoio) || largura <= 0 || largura >= 1
                    || altura <= 0 || larguraApoio <= 0 || larguraApoio > largura) {
                throw new IllegalArgumentException("Dimensões de exposição inválidas");
            }
        }

        public EntityDimensions dimensoes() {
            return EntityDimensions.fixed(largura, altura);
        }
    }

    public static final Exposicao GARRAFA = new Exposicao(
            BebidaDecorativaEntity.LARGURA, BebidaDecorativaEntity.ALTURA,
            BebidaDecorativaEntity.LARGURA);

    private final Exposicao exposicao;
    private final String tooltipColocacao;
    private final SoundEvent somColocacao;

    public BebidaColocavelItem(Properties properties) {
        this(properties, GARRAFA, "item.intoxicantes.bebida_colocavel", SoundEvents.BOTTLE_FILL);
    }

    public BebidaColocavelItem(Properties properties, Exposicao exposicao, String tooltipColocacao) {
        this(properties, exposicao, tooltipColocacao, SoundEvents.ITEM_FRAME_ADD_ITEM);
    }

    private BebidaColocavelItem(Properties properties, Exposicao exposicao,
            String tooltipColocacao, SoundEvent somColocacao) {
        super(properties);
        this.exposicao = java.util.Objects.requireNonNull(exposicao);
        this.tooltipColocacao = java.util.Objects.requireNonNull(tooltipColocacao);
        this.somColocacao = somColocacao;
    }

    public Exposicao getExposicao() {
        return exposicao;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos apoio = context.getClickedPos();
        ItemStack bebida = context.getItemInHand();
        if (context.getClickedFace() != Direction.UP) {
            return recusar(level, player, "bebida.intoxicantes.sem_apoio");
        }
        if (player.isSpectator() || !player.mayBuild()
                || !player.mayUseItemAt(apoio, Direction.UP, bebida)
                || !level.mayInteract(player, apoio)
                || !player.isWithinBlockInteractionRange(apoio, 0.0)) {
            return InteractionResult.FAIL;
        }

        // A coordenada Y vem da face atingida: laje baixa, tampo e bloco
        // inteiro conservam suas alturas reais. A margem mantém a base
        // dentro do bloco mirado, inclusive quando o clique toca sua borda.
        Vec3 hit = context.getClickLocation();
        double margem = exposicao.largura() / 2.0 + 0.005;
        Vec3 base = new Vec3(
                Mth.clamp(hit.x, apoio.getX() + margem, apoio.getX() + 1.0 - margem),
                hit.y,
                Mth.clamp(hit.z, apoio.getZ() + margem, apoio.getZ() + 1.0 - margem));
        BebidaDecorativaEntity decoracao = new BebidaDecorativaEntity(
                IntoxicantesMod.BEBIDA_DECORATIVA, level);
        decoracao.configurar(bebida, apoio, base, player.getYRot());

        if (!decoracao.temApoio()) {
            return recusar(level, player, "bebida.intoxicantes.sem_apoio");
        }
        if (!decoracao.temEspaco()) {
            return recusar(level, player, "bebida.intoxicantes.sem_espaco");
        }
        if (!(level instanceof ServerLevel servidor)) {
            return InteractionResult.SUCCESS;
        }
        if (!servidor.addFreshEntity(decoracao)) {
            return InteractionResult.FAIL;
        }
        // Somente a colocação bem-sucedida retira uma unidade. consume
        // respeita o criativo, sem executar efeitos nem devolver recipiente.
        bebida.consume(1, player);
        servidor.playSound(null, base.x, base.y, base.z, somColocacao,
                SoundSource.BLOCKS, 0.35F, 1.35F);
        decoracao.gameEvent(GameEvent.ENTITY_PLACE, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult recusar(Level level, Player player, String chave) {
        if (!level.isClientSide()) {
            // displayClientMessage não existe neste mapeamento: overlay é a
            // variante de 2 argumentos do ServerPlayer (padrão do mod).
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable(chave), true);
            } else {
                player.sendSystemMessage(Component.translatable(chave));
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> output, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, output, flag);
        output.accept(Component.translatable(tooltipColocacao)
                .withStyle(ChatFormatting.GRAY));
    }
}
