package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * v1.2.70 — A FAMÍLIA DE LÂMPADAS LED POR POTÊNCIA (5W → 200W), igual à
 * vida real: {@code lampada_led_5w}, {@code _9w}, {@code _12w}, {@code _15w},
 * {@code _20w}, {@code _30w}, {@code _50w}, {@code _100w}, {@code _150w} e
 * {@code _200w}. UMA classe, DEZ IDs (padrão dos barris v1.2.50): a potência
 * mora no REGISTRO do bloco ({@code lampada_led_50w} → 50).
 *
 * Três famílias visuais (o modelo cresce com a potência):
 * - 5..20W: BULBO residencial (a "lampadinha" comum do teto da casa);
 * - 30..50W: REFLETOR (o parabolico que varre o quintal);
 * - 100..200W: HIGH-BAY industrial (a rampa do galpão/oficina).
 *
 * LUZ = WATT: a escala real comprimida nos 15 níveis do Minecraft — 5W
 * ilumina 7 (comércio de rua à noite), 15W ilumina 9 (o quarto), 20W o 10
 * (a cozinha), 50W o 13 (a oficina), 200W ilumina 15 (o galpão inteiro).
 * zeroGlow: a torch do vanilla tem glow 2 "de graça" na luz 7; LED de
 * verdade não vaza luz pelo bloco (todas as outras fontes do mod têm glow
 * 15, onde o glow nem aparece).
 *
 * Clique vazio controla a chave local persistida na BE. A alimentação e
 * o consumo são medidos pelo quadro: sem rede energizada, LIT permanece
 * falso, inclusive quando a chave local está ligada. Não há ticker por lâmpada.
 */
public class LedPotenciaBlock extends BaseEntityBlock {

    /** A escala: watts → luz (7..15; abaixo de 7 a torch ganha). */
    public static final int[] WATTS = {5, 9, 12, 15, 20, 30, 50, 100, 150, 200};
    public static final int[] LUZ_POR_WATT = {7, 8, 8, 9, 10, 11, 13, 14, 15, 15};

    /** A família visual do modelo (bulbo / refletor / high-bay). */
    public enum Familia {
        BULBO("intoxicantes:block/lampada_led_bulbo", "residencial"),
        REFLETOR("intoxicantes:block/lampada_led_refletor", "refletor"),
        HIGH_BAY("intoxicantes:block/lampada_led_high_bay", "industrial");

        public final String modelo;
        public final String chave;

        Familia(String modelo, String chave) {
            this.modelo = modelo;
            this.chave = chave;
        }
    }

    /** A potência (watts) a partir do ID do registro (lampada_led_50w → 50). */
    public static int wattsDoBloco(Block bloco) {
        String nome = BuiltInRegistries.BLOCK.getKey(bloco).getPath();
        if (!nome.startsWith("lampada_led_") || !nome.endsWith("w")) {
            return 0;
        }
        try {
            return Integer.parseInt(nome.substring("lampada_led_".length(),
                    nome.length() - 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Luz (0..15) pela potência — a fonte única (registro e receitas). */
    public static int luzPorWatts(int watts) {
        for (int i = 0; i < WATTS.length; i++) {
            if (WATTS[i] == watts) {
                return LUZ_POR_WATT[i];
            }
        }
        return 15;
    }

    /** A família visual pela potência (a 100W não é bulbo, é high-bay). */
    public static Familia familiaDoBloco(Block bloco) {
        int watts = wattsDoBloco(bloco);
        if (watts >= 100) {
            return Familia.HIGH_BAY;
        }
        if (watts >= 30) {
            return Familia.REFLETOR;
        }
        return Familia.BULBO;
    }

    /** A state property de potência (o mesmo int pra os 10 blocos). */
    public static final IntegerProperty WATTAGE =
            IntegerProperty.create("wattage", 0, 9);

    /** A lâmpada sem alimentação não emite luz. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Limites de cada luminária nativa: refletor/campânula têm contorno próprio. */
    private static final java.util.Map<Integer, VoxelShape> FORMAS = java.util.Map.ofEntries(
            java.util.Map.entry(5, Block.box(5.8, 0, 5.8, 10.2, 7.6, 10.2)),
            java.util.Map.entry(9, Block.box(5.6, 0, 5.6, 10.4, 7.95, 10.4)),
            java.util.Map.entry(12, Block.box(5.4, 0, 5.4, 10.6, 8.3, 10.6)),
            java.util.Map.entry(15, Block.box(5.2, 0, 5.2, 10.8, 8.65, 10.8)),
            java.util.Map.entry(20, Block.box(5, 0, 5, 11, 9, 11)),
            java.util.Map.entry(30, Block.box(2.95, 0, 6.32, 13.05, 8.5, 10.2)),
            java.util.Map.entry(50, Block.box(.95, 0, 6.32, 15.05, 10.4, 10.2)),
            java.util.Map.entry(100, Block.box(3, 4, 3, 13, 15.5, 13)),
            java.util.Map.entry(150, Block.box(2.3, 4, 2.3, 13.7, 15.5, 13.7)),
            java.util.Map.entry(200, Block.box(1.6, 4, 1.6, 14.4, 15.5, 14.4)));

    /** Os watts deste membro da família (o registro sabe o ID na hora). */
    private final int wattsDoConstrutor;

    public LedPotenciaBlock(Properties properties, int watts) {
        super(properties);
        this.wattsDoConstrutor = watts;
        // o default JÁ NASCE com o wattage certo: mesmo que o colador de
        // propriedades copie o estado de outro membro, o default corrige
        int idx = indiceDosWatts(watts);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(WATTAGE, Math.max(0, idx))
                .setValue(LIT, Boolean.FALSE));
    }

    private static int indiceDosWatts(int watts) {
        for (int i = 0; i < WATTS.length; i++) {
            if (WATTS[i] == watts) return i;
        }
        return -1;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATTAGE, LIT);
    }

    /**
     * A lâmpada do ITEM certo nasce com a potência certa (o colador herdou a
     * propriedade do defaultBlockState de OUTRO membro da família — cada um
     * se conserta sozinho no placement, igual o rótulo do barril v1.2.50).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity colocador, ItemStack stack) {
        super.setPlacedBy(level, pos, state, colocador, stack);
        if (!level.isClientSide()) {
            int esperado = indiceDosWatts(wattsDoConstrutor);
            if (esperado >= 0 && state.getValue(WATTAGE) != esperado) {
                // defesa: estado herdado por colisão de propriedade corrige
                level.setBlock(pos, state.setValue(WATTAGE, esperado),
                        Block.UPDATE_ALL);
            }
            marcarRede(level, pos);
        }
    }

    private static void marcarRede(Level level, BlockPos pos) {
        if (level instanceof ServerLevel servidor) {
            com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(servidor, pos,
                    QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState antigo, boolean movido) {
        super.onPlace(state, level, pos, antigo, movido);
        if (state.getBlock() != antigo.getBlock()) marcarRede(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movido) {
        marcarRede(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movido);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return FORMAS.getOrDefault(wattsDoConstrutor, FORMAS.get(20));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LedPotenciaBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (!player.isAlive() || player.isSpectator()
                    || !(level.getBlockEntity(pos) instanceof LedPotenciaBlockEntity lampada)) {
                return InteractionResult.PASS;
            }
            boolean novo = !lampada.habilitada();
            lampada.definirHabilitada(novo);
            level.playSound(null, pos,
                    novo ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.4F, novo ? 1.3F : 0.8F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) {
            int wattsNaMao = wattsDoBloco(Block.byItem(stack.getItem()));
            return wattsNaMao > 0 && wattsNaMao != wattsDoBloco(state.getBlock())
                    ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
        }
        if (!player.isAlive() || player.isSpectator() || stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        // a troca rápida: segurar OUTRA lâmpada LED e clicar troca no lugar
        int wattsNaMao = wattsDoBloco(Block.byItem(stack.getItem()));
        int wattsAqui = wattsDoBloco(state.getBlock());
        if (wattsNaMao > 0 && wattsNaMao != wattsAqui) {
            Block novaLampada = Block.byItem(stack.getItem());
            boolean habilitada = !(level.getBlockEntity(pos)
                    instanceof LedPotenciaBlockEntity atual) || atual.habilitada();
            if (!level.setBlock(pos, novaLampada.defaultBlockState(), Block.UPDATE_ALL)) {
                return InteractionResult.PASS;
            }
            if (level.getBlockEntity(pos) instanceof LedPotenciaBlockEntity nova) {
                nova.definirHabilitada(habilitada);
            }
            // a lâmpada atual volta pro inventário (ou cai no chão se lotado)
            ItemStack antiga = new ItemStack(state.getBlock());
            if (!player.getInventory().add(antiga)) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        antiga));
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            marcarRede(level, pos);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.6F, 1.0F);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /** A rede do quadro centraliza o consumo; a luminária é uma carga passiva. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        return null; // O quadro mede e alimenta todas as cargas; não há ticker por lâmpada.
    }

    /** O ITEM da família: tooltip "LED 15W · luz 9 · clique liga/desliga". */
    public static class ItemLedPotencia extends BlockItem {
        public ItemLedPotencia(Block bloco, Item.Properties props) {
            super(bloco, props.useBlockDescriptionPrefix());
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                net.minecraft.world.item.component.TooltipDisplay display,
                java.util.function.Consumer<Component> linhas, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, linhas, flag);
            int watts = wattsDoBloco(Block.byItem(stack.getItem()));
            if (watts > 0) {
                linhas.accept(Component.translatable(
                        "block.intoxicantes.lampada_led_potencia",
                        watts, luzPorWatts(watts)));
            }
        }
    }
}
