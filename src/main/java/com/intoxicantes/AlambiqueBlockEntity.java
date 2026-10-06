package com.intoxicantes;

import java.util.Optional;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * O CÉREBRO DO ALAMBIQUE (v1.2.59 — GUI + motor de lotes): destilação com
 * INVENTÁRIO DE VERDADE — slot 0 = mosto fermentado, slot 1 = destilado jovem.
 *
 * REGRA DO CALOR (preservada): sem fogo/campfire/magma embaixo a destilação
 * PAUSA (o mosto não estraga, o cobre só esfria).
 *
 * MOTOR DE LOTES: pilha MAIOR que a dose é aceita; consome EXATAMENTE a dose
 * ao fechar cada lote; a sobra fica no slot e o lote seguinte começa sozinho.
 * Saída cheia → {@code producaoPendente} (nada se perde).
 */
public class AlambiqueBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<Integer> {

    private final NonNullList<ItemStack> itens =
            NonNullList.withSize(TipoMaquina.ALAMBIQUE.totalSlots(), ItemStack.EMPTY);
    private int ticksRestantes;
    private int ticksTotal;
    /** Lote fechou mas a saída está cheia: espera espaço (nada se perde). */
    private boolean producaoPendente;

    /** Fase sincronizada pra GUI (ContainerData índice 2). */
    public static final int FASE_VAZIA = 0;
    public static final int FASE_DESTILANDO = 1;
    public static final int FASE_PRONTA = 2;
    public static final int FASE_SAIDA_CHEIA = 3;
    public static final int FASE_SEM_FOGO = 4;

    /** 1 verificação por segundo (20 ticks). */
    private static final int INTERVALO = 20;

    /** Dados sincronizados com a GUI — a barra REAL da destilação. */
    private final ContainerData dados = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> ticksRestantes;
                case 1 -> ticksTotal;
                case 2 -> faseAtual();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int valor) {
            // somente leitura: o servidor é a autoridade
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public AlambiqueBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.ALAMBIQUE_ENTITY, pos, state);
    }

    private int faseAtual() {
        if (producaoPendente) {
            return FASE_SAIDA_CHEIA;
        }
        if (ticksRestantes > 0) {
            // o servidor conhece o calor: sem fogo a GUI acende a dica
            // "fogo apagado" (o lote espera — mosto não estraga)
            if (level != null && !temCalor(level)) {
                return FASE_SEM_FOGO;
            }
            return FASE_DESTILANDO;
        }
        if (!itens.get(TipoMaquina.ALAMBIQUE.idxOut).isEmpty()) {
            return FASE_PRONTA;
        }
        return FASE_VAZIA;
    }

    // ==================================================== INTERAÇÃO

    /** Quantidade da dose desta stack segundo a receita (0 = não aceita). */
    public int qtdNecessaria(ItemStack stack) {
        Optional<ProcessosBebida.Alambique> r = ProcessosBebida.alambiqueQueAceita(
                stack.getItem(), stack.getCount());
        return r.map(ProcessosBebida.Alambique::qtdIn).orElse(0);
    }

    /** Fogo embaixo: campfire, fogo ou magma (o queimador da destilaria). */
    private boolean temCalor(net.minecraft.world.level.Level level) {
        BlockState abaixo = level.getBlockState(worldPosition.below());
        return abaixo.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || abaixo.is(net.minecraft.world.level.block.Blocks.FIRE)
                || abaixo.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }

    /**
     * Clique com item (rota de inserção rápida): joga o mosto NO SLOT 0 —
     * até 4 doses por vez; o motor consome lote a lote.
     */
    public boolean tentarCarregar(ServerLevel level, Player player, ItemStack stack) {
        if (destilando() || producaoPendente) {
            return false; // lote em curso é respeitado
        }
        if (!itens.get(TipoMaquina.ALAMBIQUE.idxOut).isEmpty()) {
            return false; // tem destilado esperando recolha
        }
        Optional<ProcessosBebida.Alambique> r = ProcessosBebida
                .alambiqueQueAceitaLote(stack.getItem());
        if (r.isEmpty()) {
            return false;
        }
        ItemStack buffer = itens.get(0);
        if (!buffer.isEmpty() && buffer.getItem() != stack.getItem()) {
            return false;
        }
        int dose = r.get().qtdIn();
        int entra = Math.min(stack.getCount(), dose * 4);
        if (buffer.isEmpty()) {
            itens.set(0, stack.split(entra));
        } else {
            int espaco = Math.min(entra, buffer.getMaxStackSize() - buffer.getCount());
            if (espaco <= 0) {
                return false;
            }
            buffer.grow(stack.split(espaco).getCount());
        }
        setChanged();
        sincronizar();
        avancar();
        avisar(player, Component.translatable(
                "block.intoxicantes.alambique_carregado",
                ticksTotal / 20 / 60, (ticksTotal / 20) % 60), true);
        return true;
    }

    /** Clique de mão vazia (rota antiga): recolhe o pronto ou mostra o progresso. */
    public void interagir(ServerLevel level, Player player) {
        if (coletarSaida(player)) {
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
            return;
        }
        if (destilando()) {
            int seg = ticksRestantes / 20;
            boolean calor = temCalor(level);
            avisar(player, Component.translatable(
                    calor ? "block.intoxicantes.alambique_destilando"
                          : "block.intoxicantes.alambique_sem_fogo",
                    seg / 60, seg % 60,
                    Math.max(0, Math.min(100, 100 - ticksRestantes * 100 / Math.max(1, ticksTotal)))),
                    true);
        } else {
            avisar(player, Component.translatable(
                    "block.intoxicantes.alambique_vazio"), true);
        }
    }

    private boolean coletarSaida(Player player) {
        ItemStack saida = itens.get(TipoMaquina.ALAMBIQUE.idxOut);
        if (saida.isEmpty()) {
            return false;
        }
        entregar(player, saida);
        itens.set(TipoMaquina.ALAMBIQUE.idxOut, ItemStack.EMPTY);
        setChanged();
        sincronizar();
        avancar();
        return true;
    }

    private void entregar(Player player, ItemStack stack) {
        ItemStack copia = stack.copy();
        if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && sp.getInventory().add(copia)) {
            return;
        }
        if (getLevel() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                    worldPosition.getZ() + 0.5, copia));
        }
    }

    /** Overlay (actionbar) pra ServerPlayer; chat pro resto. */
    private void avisar(Player player, Component msg, boolean overlay) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(msg, overlay);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    // ==================================================== MOTOR DE LOTES

    /** Começa UM lote de destilação (dose é consumida ao fechar). */
    private void iniciarLote(ProcessosBebida.Alambique rec) {
        this.ticksTotal = ProcessosBebida.tempoAlambique();
        this.ticksRestantes = ticksTotal;
        this.producaoPendente = false;
        setChanged();
        sincronizar();
    }

    /** Com timer zerado: fecha lote pendente ou abre o próximo (contínua). */
    private void avancar() {
        if (ticksRestantes > 0) {
            return;
        }
        ItemStack insumo = itens.get(0);
        if (insumo.isEmpty()) {
            return;
        }
        Optional<ProcessosBebida.Alambique> rec = receitaDoInsumo();
        if (rec.isEmpty()) {
            return;
        }
        if (producaoPendente) {
            tentarProduzir(rec.get());
            return;
        }
        iniciarLote(rec.get());
    }

    private Optional<ProcessosBebida.Alambique> receitaDoInsumo() {
        ItemStack insumo = itens.get(0);
        if (insumo.isEmpty()) {
            return Optional.empty();
        }
        return ProcessosBebida.alambiqueQueAceitaLote(insumo.getItem());
    }

    /**
     * Fecha o lote: produz o destilado jovem, consome EXATAMENTE a dose e
     * encadeia o próximo. Saída sem espaço → {@code producaoPendente}.
     */
    private void tentarProduzir(ProcessosBebida.Alambique r) {
        if (!inserirSaida(TipoMaquina.ALAMBIQUE.idxOut,
                new ItemStack(r.output(), r.qtdOut()))) {
            this.producaoPendente = true;
            this.ticksRestantes = 0;
            setChanged();
            sincronizar();
            return;
        }
        ItemStack insumo = itens.get(0);
        insumo.shrink(r.qtdIn());
        if (insumo.isEmpty()) {
            itens.set(0, ItemStack.EMPTY);
        }
        setChanged();
        sincronizar();
        iniciarLote(r); // produção contínua
    }

    private boolean inserirSaida(int idx, ItemStack nova) {
        ItemStack atual = itens.get(idx);
        if (atual.isEmpty()) {
            itens.set(idx, nova);
            setChanged();
            return true;
        }
        if (atual.getItem() == nova.getItem()
                && atual.getCount() + nova.getCount() <= atual.getMaxStackSize()) {
            atual.grow(nova.getCount());
            setChanged();
            return true;
        }
        return false;
    }

    /** Leitura dos dados sincronizados da GUI (0=timer, 1=total, 2=fase). */
    public ContainerData dadosGui() {
        return dados;
    }

    public void tick(ServerLevel level) {
        if (ticksRestantes <= 0) {
            return;
        }
        if (level.getGameTime() % INTERVALO != 0L) {
            return;
        }
        // REGRA DO CALOR: sem fogo embaixo o lote ESPERA (não consome nada).
        if (!temCalor(level)) {
            return;
        }
        ticksRestantes -= INTERVALO;
        if (ticksRestantes <= 0) {
            ticksRestantes = 0;
            Optional<ProcessosBebida.Alambique> rec = receitaDoInsumo();
            if (rec.isPresent()) {
                tentarProduzir(rec.get());
            } else {
                setChanged();
                sincronizar();
            }
            level.playSound(null, worldPosition, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 0.7F);
        } else {
            setChanged();
            // v1.2.60: o CHIADO do vapor na serpentina (a cada ~11s de jogo)
            if ((ticksRestantes / 20) % 11 == 0) {
                level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.25F, 1.5F);
            }
            // v1.2.55/60: o VAPOR do destilado engrossa no fim do lote
            int periodoVapor = 1 + Math.min(2, Math.round(
                    2 * ticksRestantes / (float) Math.max(1, ticksTotal)));
            if (level.getRandom().nextInt(periodoVapor) == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        worldPosition.getX() + 0.85, worldPosition.getY() + 0.78,
                        worldPosition.getZ() + 0.73, 1, 0.03, 0.02, 0.03, 0.002);
            }
        }
    }

    public boolean destilando() {
        return ticksRestantes > 0;
    }

    // ==================================================== CONTAINER

    @Override
    public int getContainerSize() {
        return itens.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : itens) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return itens.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int qtd) {
        ItemStack removido = ContainerHelper.removeItem(itens, slot, qtd);
        if (!removido.isEmpty()) {
            setChanged();
            if (slot == TipoMaquina.ALAMBIQUE.idxOut) {
                avancar();
            }
        }
        return removido;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(itens, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        itens.set(slot, stack);
        if (stack.getCount() > stack.getMaxStackSize()) {
            stack.setCount(stack.getMaxStackSize());
        }
        setChanged();
        if (slot == 0) {
            avancar();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        itens.clear();
        setChanged();
    }

    // ==================================================== MENU PROVIDER

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.intoxicantes." + TipoMaquina.ALAMBIQUE.id);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MenuMaquinaSNC(id, inv, this, TipoMaquina.ALAMBIQUE, dados);
    }

    /** v1.2.69 FIX: payload do ExtendedMenuType (TipoMaquina) — a GUI não abria sem isto. */
    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return TipoMaquina.ALAMBIQUE.indice();
    }

    // ==================================================== SOLTE O CONTEÚDO

    /** Quebrou o alambique: devolve o mosto E o destilado pronto. */
    public void soltarConteudo(ServerLevel level) {
        for (int i = 0; i < itens.size(); i++) {
            ItemStack s = itens.get(i);
            if (!s.isEmpty()) {
                dropar(level, s);
                itens.set(i, ItemStack.EMPTY);
            }
        }
        ticksRestantes = 0;
        ticksTotal = 0;
        producaoPendente = false;
        setChanged();
    }

    private void dropar(ServerLevel level, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, stack));
    }

    // ==================================================== SYNC DO RENDERER

    private void sincronizar() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** O estado vivo pro renderer (mesma convenção do PainelLed). */
    @Override
    public Object getRenderData() {
        return this;
    }

    /** Progresso da destilação 0..1 (o client usa pro brilho da serpentina). */
    public float progressoClient() {
        if (ticksTotal <= 0) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, 1F - ticksRestantes / (float) ticksTotal));
    }

    /** O calor liga o brilho da serpentina no client (leitura do próprio nível). */
    public boolean comCalorClient() {
        return level != null && temCalor(level);
    }

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("lote");
        ContainerHelper.loadAllItems(raiz, itens);
        // MIGRAÇÃO v1.2.55 → v1.2.59: lotes antigos moravam em keys próprias
        if (itens.get(0).isEmpty()) {
            raiz.read("insumoStack", ItemStack.CODEC).ifPresent(this::migrarInsumoLegado);
        }
        if (itens.get(TipoMaquina.ALAMBIQUE.idxOut).isEmpty()) {
            raiz.read("resultadoStack", ItemStack.CODEC)
                    .ifPresent(v -> itens.set(TipoMaquina.ALAMBIQUE.idxOut, v));
        }
        this.ticksRestantes = raiz.getIntOr("ticksRestantes", 0);
        this.ticksTotal = raiz.getIntOr("ticksTotal", 0);
        this.producaoPendente = raiz.getBooleanOr("producaoPendente", false);
        if (producaoPendente) {
            this.producaoPendente = false;
            avancar(); // recomputa: produz agora ou volta a pendente
        }
    }

    /** Lote legado: entra no slot 0 com cap de 4 doses (pilha antiga = lote). */
    private void migrarInsumoLegado(ItemStack antigo) {
        Optional<ProcessosBebida.Alambique> rec =
                ProcessosBebida.alambiqueQueAceitaLote(antigo.getItem());
        if (rec.isPresent()) {
            int cap = Math.min(antigo.getCount(), rec.get().qtdIn() * 4);
            itens.set(0, antigo.copyWithCount(cap));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput raiz = output.child("lote");
        ContainerHelper.saveAllItems(raiz, itens, true);
        raiz.putInt("ticksRestantes", ticksRestantes);
        raiz.putInt("ticksTotal", ticksTotal);
        raiz.putBoolean("producaoPendente", producaoPendente);
    }
}
