package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * O BARRIL DE BEBIDA (v1.2.50) — UM bloco, QUATRO identidades (cachaça,
 * cerveja, rum, vinho) escolhidas na hora do CRAFT (receita carimba o
 * "rótulo" no item) e gravadas no BlockEntity ao ser colocado.
 *
 * Ciclo (spec 9/13): vazio → FERMENTANDO (bolhas, som, 1º tempo) →
 * MATURANDO (silencioso, madeira trabalhando) → PRONTO (rolha "respirando",
 * tooltip convida). Engarrafar: garrafa de vidro em mãos, clique-direito —
 * cada clique tira 1 garrafa do lote; o lote inteiro rende várias (spec 14).
 * Quebrar o barril antes do fim DEVOLVE o insumo (o lote não é perdido).
 */
public class BarrilBebidaBlock extends BaseEntityBlock {

    /** Barril cheio de dignidade: levemente mais estreito que o bloco. */
    private static final VoxelShape SHAPE = Block.box(1.5, 0.0, 1.5, 14.5, 14.0, 14.5);

    public BarrilBebidaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarrilBebidaBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Colocação: o BE recebe o rótulo do ITEM (DataComponent ROTULO_BARRIL,
     * gravado pela receita de craft de cada barril).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity colocador, ItemStack stack) {
        super.setPlacedBy(level, pos, state, colocador, stack);
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be) {
            // o rótulo vem do PRÓPRIO bloco (barril_cachaca -> "cachaca"); o
            // DataComponent continua sendo gravado pelo craft, mas o bloco
            // colocado nunca fica sem identidade
            String rotulo = stack.get(IntoxicantesMod.ROTULO_BARRIL);
            if (rotulo == null || rotulo.isEmpty()) {
                rotulo = rotuloDoBloco();
            }
            be.carregarRotulo(rotulo == null ? "" : rotulo);
        }
    }

    /** Rótulo a partir do registro do bloco (barril_cachaca -> cachaca). */
    /** Versao publica pra o BlockEntity derivar o rotulo (blocos sem placement). */
    public String rotuloDoBlocoPublico() {
        return rotuloDoBloco();
    }

    private String rotuloDoBloco() {
        String nome = BuiltInRegistries.BLOCK.getKey(this).getPath();
        return nome.startsWith("barril_") ? nome.substring("barril_".length()) : "";
    }

    /**
     * Quebrou o barril: o lote é devolvido (insumo antes de pronto, garrafas
     * depois) e o DROP é o barril certo — pela LOOT TABLE do bloco colocado
     * (barril_cachaca dropa barril_cachaca), que é a fonte única do drop.
     *
     * v1.2.71 — FIM DO DROP DUPLICADO (bug do playtest: "estações dropam 2
     * delas mesmas"): este método dropava o barril À MÃO (ItemEntity) E a
     * loot table do bloco dropava DE NOVO — 2 barris por quebra. O drop
     * manual saiu; a identidade continua garantida (o bloco colocado é o
     * do rótulo: o craft carimba e o setPlacedBy grava no BE).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (level instanceof ServerLevel servidor
                && level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be) {
            be.soltarConteudo(servidor);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * v1.2.59 — O LOTE INVISÍVEL NO VAZIO: a BE do barril NÃO é Container (o
     * lote em curso é um int), então {@code destroyBlock} (explosão, pistão,
     * "/fill ar") não derrubava nada do lote — só {@code playerWillDestroy}
     * devolvia. Idempotente com a rota de cima: {@code soltarConteudo}
     * esvazia os slots e zera o lote, nunca dropa duas vezes.
     */
    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
            ItemStack tool, boolean dropContents) {
        if (level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be) {
            be.soltarConteudo(level);
        }
        super.spawnAfterBreak(state, level, pos, tool, dropContents);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                // shift + mão vazia: recolhe garrafas prontas / status
                be.interagir((ServerLevel) level, player, ItemStack.EMPTY);
            } else {
                // v1.2.59: botão direito abre a GUI do barril
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // SHIFT + item: inserção/engarrafamento rápido (a BE mesma consome —
        // garrafa, insumo do lote; sem shrink aqui fora pra não morder 2×)
        if (player.isShiftKeyDown()) {
            be.interagir((ServerLevel) level, player, stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        // clique normal: abre a GUI (insere/garrafa pelos slots)
        player.openMenu(be);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Partículas de fermentação: bolhas escapando pela rolha (client). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof BarrilBebidaBlockEntity be)) {
            return;
        }
        if (!be.fermentando()) {
            return; // maturação é silenciosa (spec 17)
        }
        if (random.nextInt(25) != 0) {
            return;
        }
        double x = pos.getX() + 0.35 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.35 + random.nextDouble() * 0.3;
        level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 1.02, z, 0, 0.03, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.01, 0);
        }
    }

    /** Ticker do servidor: relógio das duas fases (1 checagem/s). */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (nivel, pos, estado, be) -> {
            if (be instanceof BarrilBebidaBlockEntity barril) {
                barril.tick((ServerLevel) nivel);
            }
        };
    }
}
