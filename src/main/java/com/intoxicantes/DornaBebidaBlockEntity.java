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
 * O CÉREBRO DA DORNA (v1.2.59 — GUI + motor de lotes): fermentação com
 * INVENTÁRIO DE VERDADE — slot 0 = mosto, slot 1 = mosto fermentado.
 *
 * MOTOR DE LOTES (o mesmo das outras máquinas): pilha MAIOR que a dose é
 * aceita ({@code >=}); consome EXATAMENTE a dose ao fechar cada lote; a
 * sobra fica no slot e o lote seguinte começa sozinho (13 caldo → 3 lotes
 * de 4 + 1 caldo parado). Saida cheia NÃO perde produto: o lote fica
 * {@code producaoPendente} até sobrar espaço. Tudo persistido
 * (ValueInput/Output) — save/restart/chunk descarregado não perde o mosto.
 *
 * PERFORMANCE (regra 25): o tick conta em SEGUNDOS (20 ticks), igual sempre.
 */
public class DornaBebidaBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<Integer> {

    private final NonNullList<ItemStack> itens =
            NonNullList.withSize(TipoMaquina.DORNA.totalSlots(), ItemStack.EMPTY);
    private int ticksRestantes;
    private int ticksTotal;
    /** Lote fechou mas a saída está cheia: espera espaço (nada se perde). */
    private boolean producaoPendente;

    /** Fase sincronizada pra GUI (ContainerData índice 2). */
    public static final int FASE_VAZIA = 0;
    public static final int FASE_FERMENTANDO = 1;
    public static final int FASE_PRONTA = 2;
    public static final int FASE_SAIDA_CHEIA = 3;

    /** 1 verificação por segundo (20 ticks). */
    private static final int INTERVALO = 20;

    /** Dados sincronizados com a GUI — a barra REAL da fermentação. */
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

    public DornaBebidaBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.DORNA_BEBIDA_ENTITY, pos, state);
    }

    private int faseAtual() {
        if (producaoPendente) {
            return FASE_SAIDA_CHEIA;
        }
        if (ticksRestantes > 0) {
            return FASE_FERMENTANDO;
        }
        if (!itens.get(TipoMaquina.DORNA.idxOut).isEmpty()) {
            return FASE_PRONTA;
        }
        return FASE_VAZIA;
    }

    // ==================================================== INTERAÇÃO

    /** Quantidade da dose desta stack segundo a receita (0 = não aceita). */
    public int qtdNecessaria(ItemStack stack) {
        Optional<ProcessosBebida.Dorna> r = ProcessosBebida.dornaQueAceita(
                stack.getItem(), stack.getCount());
        return r.map(ProcessosBebida.Dorna::qtdIn).orElse(0);
    }

    /**
     * Clique com item (rota de inserção rápida): joga o mosto NO SLOT 0 —
     * até 4 doses por vez; o motor consome lote a lote e a sobra FICA na mão.
     */
    public boolean tentarCarregar(ServerLevel level, Player player, ItemStack stack) {
        if (fermentando() || producaoPendente) {
            return false; // lote em curso é respeitado
        }
        if (!itens.get(TipoMaquina.DORNA.idxOut).isEmpty()) {
            return false; // tem mosto fermentado esperando recolha
        }
        Optional<ProcessosBebida.Dorna> r = ProcessosBebida.dornaQueAceitaLote(
                stack.getItem());
        if (r.isEmpty()) {
            return false;
        }
        ItemStack buffer = itens.get(0);
        if (!buffer.isEmpty() && buffer.getItem() != stack.getItem()) {
            return false; // já tem outro insumo no buffer
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
        avisar(player, Component.translatable("block.intoxicantes.dorna_carregada",
                ticksTotal / 20 / 60, (ticksTotal / 20) % 60));
        return true;
    }

    /** Clique de mão vazia (rota antiga): recolhe o pronto ou mostra o progresso. */
    public void interagir(ServerLevel level, Player player) {
        if (coletarSaida(player)) {
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS, 0.8F, 1.2F);
            return;
        }
        if (fermentando()) {
            int seg = ticksRestantes / 20;
            avisar(player, Component.translatable("block.intoxicantes.dorna_fermentando",
                    seg / 60, seg % 60, porcentagem()));
        } else {
            avisar(player, Component.translatable("block.intoxicantes.dorna_vazia"));
        }
    }

    private boolean coletarSaida(Player player) {
        ItemStack saida = itens.get(TipoMaquina.DORNA.idxOut);
        if (saida.isEmpty()) {
            return false;
        }
        entregar(player, saida);
        itens.set(TipoMaquina.DORNA.idxOut, ItemStack.EMPTY);
        setChanged();
        sincronizar();
        avancar();
        return true;
    }

    private void entregar(Player player, ItemStack stack) {
        ItemStack copia = stack.copy();
        if (player instanceof ServerPlayer sp && sp.getInventory().add(copia)) {
            return;
        }
        if (getLevel() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.6,
                    worldPosition.getZ() + 0.5, copia));
        }
    }

    private void avisar(Player player, Component msg) {
        avisar(player, msg, true);
    }

    private void avisar(Player player, Component msg, boolean overlay) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(msg, overlay);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    // ==================================================== MOTOR DE LOTES

    /** Começa UM lote de fermentação (dose é consumida ao fechar). */
    private void iniciarLote(ProcessosBebida.Dorna rec) {
        this.ticksTotal = ProcessosBebida.tempoDorna();
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
        Optional<ProcessosBebida.Dorna> rec = receitaDoInsumo();
        if (rec.isEmpty()) {
            return;
        }
        if (producaoPendente) {
            tentarProduzir(rec.get());
            return;
        }
        iniciarLote(rec.get());
    }

    private Optional<ProcessosBebida.Dorna> receitaDoInsumo() {
        ItemStack insumo = itens.get(0);
        if (insumo.isEmpty()) {
            return Optional.empty();
        }
        return ProcessosBebida.dornaQueAceitaLote(insumo.getItem());
    }

    /**
     * Fecha o lote: produz o mosto fermentado, consome EXATAMENTE a dose e
     * encadeia o próximo. Saida sem espaço → {@code producaoPendente}.
     */
    private void tentarProduzir(ProcessosBebida.Dorna r) {
        if (!inserirSaida(TipoMaquina.DORNA.idxOut,
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

    public void tick(ServerLevel level) {
        if (ticksRestantes <= 0) {
            return;
        }
        if (level.getGameTime() % INTERVALO != 0L) {
            return; // 1 verificação por segundo
        }
        ticksRestantes -= INTERVALO;
        // som ocasional de borbulha (a cada ~13s de jogo, o lote "respira")
        if (ticksRestantes > 0 && (ticksRestantes / 20) % 13 == 0) {
            // v1.2.60: o borbulhar ACELERA com o lote — o pitch sobe com o
            // progresso REAL (lote fresco grave, quase pronto agitado)
            float progresso = ticksTotal > 0
                    ? 1F - ticksRestantes / (float) ticksTotal : 0F;
            level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS, 0.6F, 0.85F + 0.25F * progresso);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.85,
                    worldPosition.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.02);
        }
        if (ticksRestantes <= 0) {
            ticksRestantes = 0;
            Optional<ProcessosBebida.Dorna> rec = receitaDoInsumo();
            if (rec.isPresent()) {
                tentarProduzir(rec.get());
            } else {
                setChanged();
                sincronizar();
            }
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS, 0.9F, 0.8F);
        } else {
            setChanged();
            // v1.2.55/60: o VAPOR da fermentação engrossa no fim do lote
            // (lote fresco ~1 puff a cada 4s; quase pronto, todo segundo)
            int periodoVapor = 1 + Math.min(3, Math.round(
                    3 * ticksRestantes / (float) Math.max(1, ticksTotal)));
            if (level.getRandom().nextInt(periodoVapor) == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.75,
                        worldPosition.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.004);
            }
        }
    }

    public boolean fermentando() {
        return ticksRestantes > 0;
    }

    /** True se há lote em curso ou pendente de saída (travado por buffer cheio). */
    public boolean processando() {
        return fermentando() || producaoPendente;
    }

    /** Leitura dos dados sincronizados da GUI (0=timer, 1=total, 2=fase). */
    public ContainerData dadosGui() {
        return dados;
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
            if (slot == TipoMaquina.DORNA.idxOut) {
                avancar(); // saída abriu espaço: lote pendente segue
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
            avancar(); // insumo novo: motor decide (lote imediato)
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
        return Component.translatable("block.intoxicantes." + TipoMaquina.DORNA.id);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MenuMaquinaSNC(id, inv, this, TipoMaquina.DORNA, dados);
    }

    /** v1.2.69 FIX: payload do ExtendedMenuType (TipoMaquina) — a GUI não abria sem isto. */
    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return TipoMaquina.DORNA.indice();
    }

    // ==================================================== SOLTE O CONTEÚDO

    /** Quebrou a dorna: devolve o mosto do buffer E o fermentado pronto. */
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

    /** Progresso do lote 0..1 (o client usa pro nível do caldo). */
    public float progressoClient() {
        if (ticksTotal <= 0) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, 1F - ticksRestantes / (float) ticksTotal));
    }

    private int porcentagem() {
        if (ticksTotal <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, 100 - (ticksRestantes * 100 / ticksTotal)));
    }

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("lote");
        ContainerHelper.loadAllItems(raiz, itens);
        // MIGRAÇÃO v1.2.55 → v1.2.59: lotes antigos moravam em keys próprias
        if (itens.get(0).isEmpty()) {
            raiz.read("insumoStack", ItemStack.CODEC).ifPresent(this::migrarLegado);
        }
        this.ticksRestantes = raiz.getIntOr("ticksRestantes", 0);
        this.ticksTotal = raiz.getIntOr("ticksTotal", 0);
        this.producaoPendente = raiz.getBooleanOr("producaoPendente", false);
        if (producaoPendente) {
            this.producaoPendente = false;
            avancar(); // recomputa (o motor decide: produz ou espera espaço)
        }
    }

    /** Lote legado: entra no slot 0 (a pilha antiga era o lote inteiro). */
    private void migrarLegado(ItemStack antigo) {
        Optional<ProcessosBebida.Dorna> rec =
                ProcessosBebida.dornaQueAceitaLote(antigo.getItem());
        if (rec.isPresent()) {
            // velho comportamento guardava a pilha inteira: cap no buffer
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
