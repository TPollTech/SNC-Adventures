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
 * O CÉREBRO DO BARRIL (v1.2.59 — GUI + motor de lotes): rótulo, duas fases
 * (FERMENTANDO/MATURANDO) e ENGARRAFAMENTO com inventário de verdade.
 *
 * SLOTS: 0 = insumo (buffer de até 4 doses), 1 = garrafas de vidro,
 * 2 = bebida engarrafada.
 *
 * MOTOR (regra unificada v1.2.59 — a dose é gasta no INÍCIO do lote):
 * buffer ≥ dose → consome a dose e começa a fermentar (ou maturar direto);
 * fases correm no timer persistido; PRONTO → cada garrafa de vidro (slot 1
 * ou clique com garrafa na mão) vira 1 bebida no slot 2/na mão; lote zerado
 * → o barril volta a VAZIO e o PRÓXIMO lote começa sozinho com o que sobrou
 * no buffer (produção contínua). Quebrou antes de esvaziar? Devolve a dose
 * do lote em curso, o buffer, as garrafas e o produto (nada se perde).
 *
 * Estados persistidos (spec 9): sobrevive a save/restart/chunk unload.
 */
public class BarrilBebidaBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<Integer> {

    /** Fases do barril (spec 9) — valores preservados pro renderer. */
    public static final int FASE_VAZIA = 0;
    public static final int FASE_FERMENTANDO = 1;
    public static final int FASE_MATURANDO = 2;
    public static final int FASE_PRONTA = 3;

    /** Rótulo: "cachaca", "cerveja", "rum", "vinho" (grava no NBT). */
    private String bebida = "";
    /** Fase atual. */
    private int fase = FASE_VAZIA;
    /** Ticks restantes da fase corrente. */
    private int ticksRestantes;
    private int ticksTotalFase;
    /** Garrafas que ainda dá pra tirar do lote pronto. */
    private int garrafasRestantes;
    /** Dose do lote EM CURSO (devolve se quebrar antes de esvaziar). */
    private int loteQtd;

    /** Slots: insumo, garrafas de vidro, bebida engarrafada. */
    private final NonNullList<ItemStack> itens =
            NonNullList.withSize(TipoMaquina.BARRIL.totalSlots(), ItemStack.EMPTY);

    private static final int INTERVALO = 20;

    /** Dados sincronizados com a GUI (fase, timer e garrafas restantes). */
    private final ContainerData dados = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> ticksRestantes;
                case 1 -> ticksTotalFase;
                case 2 -> fase;
                case 3 -> garrafasRestantes;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int valor) {
            // somente leitura: o servidor é a autoridade
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BarrilBebidaBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.BARRIL_BEBIDA_ENTITY, pos, state);
    }

    private Optional<ProcessosBebida.Barril> receita() {
        return getBebida().isEmpty()
                ? Optional.empty() : ProcessosBebida.barrilDe(getBebida());
    }

    // ==================================================== INTERAÇÃO (cliques)

    /**
     * Clique no barril. Com garrafa de vidro: engarrafa 1 (PRONTO). Com o
     * insumo da receita (barril VAZIO): enche o buffer (até 4 doses; a dose
     * do lote é gasta na largada). Mão vazia: recolhe o produto ou status.
     * Devolve true se CONSUMIU algo da mão (o clique não encolhe de fora).
     */
    public boolean interagir(ServerLevel level, Player player, ItemStack mao) {
        Optional<ProcessosBebida.Barril> receita = receita();
        // 1) PRONTO + garrafa na mão = engarrafa 1 direto na mão do jogador
        //    (v1.2.66: aceita a garrafa de vidro OU a VAZIA da própria bebida —
        //    o recipiente que volta ao beber volta a encher no barril)
        if (fase == FASE_PRONTA && ProcessosBebida.eGarrafaDoBarril(mao.getItem(), receita)) {
            if (receita.isEmpty() || garrafasRestantes <= 0) {
                return false;
            }
            mao.shrink(1);
            entregar(level, player, new ItemStack(receita.get().bebidaFinal(), 1));
            garrafasRestantes--;
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            if (garrafasRestantes <= 0) {
                esvaziarLote(level);
            } else {
                setChanged();
            }
            return true;
        }

        // 2) VAZIO + insumo da receita na mão = enche o buffer (até 4 doses)
        if (fase == FASE_VAZIA && receita.isPresent()
                && mao.getItem() == receita.get().input() && !mao.isEmpty()) {
            int dose = receita.get().qtdIn();
            ItemStack buffer = itens.get(0);
            if (!buffer.isEmpty() && buffer.getItem() != mao.getItem()) {
                mostrarStatus(level, player, receita);
                return false;
            }
            int entra = Math.min(mao.getCount(), dose * 4);
            if (entra <= 0) {
                return false;
            }
            if (buffer.isEmpty()) {
                itens.set(0, mao.split(entra));
            } else {
                int espaco = Math.min(entra, buffer.getMaxStackSize() - buffer.getCount());
                if (espaco <= 0) {
                    mostrarStatus(level, player, receita);
                    return false;
                }
                buffer.grow(mao.split(espaco).getCount());
            }
            setChanged();
            sincronizar();
            avancar(level); // buffer cheio → o lote começa sozinho
            level.playSound(null, worldPosition, SoundEvents.BARREL_OPEN,
                    SoundSource.BLOCKS, 0.7F, 0.9F);
            return true;
        }

        // 3) mão vazia: recolhe o produto pronto do slot de saída
        if (mao.isEmpty()) {
            ItemStack saida = itens.get(TipoMaquina.BARRIL.idxOut);
            if (!saida.isEmpty()) {
                entregar(level, player, saida);
                itens.set(TipoMaquina.BARRIL.idxOut, ItemStack.EMPTY);
                setChanged();
                sincronizar();
                return true;
            }
        }

        // 4) status (o jogador entende o porquê)
        mostrarStatus(level, player, receita);
        return false;
    }

    /** Esvaziou o lote (última garrafa): volta a VAZIO e encadeia o próximo. */
    private void esvaziarLote(ServerLevel level) {
        fase = FASE_VAZIA;
        loteQtd = 0;
        ticksRestantes = 0;
        ticksTotalFase = 0;
        setChanged();
        sincronizar();
        avancar(level); // produção contínua: buffer ≥ dose → lote novo
    }

    private void mostrarStatus(ServerLevel level, Player player,
            Optional<ProcessosBebida.Barril> receita) {
        switch (fase) {
            case FASE_FERMENTANDO -> {
                int seg = ticksRestantes / 20;
                avisar(player, Component.translatable(
                        "block.intoxicantes.barril_fermentando",
                        nomeBebida(), seg / 60, seg % 60,
                        Math.max(0, Math.min(100, 100 - ticksRestantes * 100 / Math.max(1, ticksTotalFase)))),
                        true);
            }
            case FASE_MATURANDO -> {
                int seg = ticksRestantes / 20;
                avisar(player, Component.translatable(
                        "block.intoxicantes.barril_maturando",
                        nomeBebida(), seg / 60, seg % 60,
                        Math.max(0, Math.min(100, 100 - ticksRestantes * 100 / Math.max(1, ticksTotalFase)))),
                        true);
            }
            case FASE_PRONTA -> avisar(player, Component.translatable(
                    "block.intoxicantes.barril_pronto", nomeBebida(), garrafasRestantes), true);
            default -> {
                if (receita.isPresent()) {
                    avisar(player, Component.translatable(
                            "block.intoxicantes.barril_vazio_receita",
                            new ItemStack(receita.get().input(), receita.get().qtdIn())
                                    .getHoverName()), true);
                } else {
                    avisar(player, Component.translatable(
                            "block.intoxicantes.barril_vazio"), true);
                }
            }
        }
    }

    /** Overlay (actionbar) pra ServerPlayer; chat pro resto. */
    private void avisar(Player player, Component msg, boolean overlay) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(msg, overlay);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    private Component nomeBebida() {
        return Component.translatable("block.intoxicantes.barril_" + getBebida());
    }

    // ==================================================== MOTOR (server, 1x/s)

    /** A dose é gasta NA LARGADA: consome o insumo e começa a 1ª fase. */
    private void iniciarLote(ProcessosBebida.Barril receita, ServerLevel level) {
        ItemStack buffer = itens.get(0);
        buffer.shrink(receita.qtdIn());
        if (buffer.isEmpty()) {
            itens.set(0, ItemStack.EMPTY);
        }
        this.loteQtd = receita.qtdIn();
        if (ProcessosBebida.tempoFermentacao(receita) > 0) {
            this.fase = FASE_FERMENTANDO;
            this.ticksTotalFase = ProcessosBebida.tempoFermentacao(receita);
        } else {
            this.fase = FASE_MATURANDO;
            this.ticksTotalFase = ProcessosBebida.tempoMaturacao(receita);
        }
        this.ticksRestantes = ticksTotalFase;
        setChanged();
        sincronizar(); // o client acorda o renderer (espuma no airlock)
    }

    /** Lote novo se: fase VAZIA, receita com rótulo e buffer ≥ dose. */
    private void avancar(ServerLevel level) {
        if (fase != FASE_VAZIA) {
            return;
        }
        Optional<ProcessosBebida.Barril> rec = receita();
        if (rec.isEmpty()) {
            return;
        }
        ItemStack buffer = itens.get(0);
        if (buffer.isEmpty() || buffer.getItem() != rec.get().input()
                || buffer.getCount() < rec.get().qtdIn()) {
            return;
        }
        iniciarLote(rec.get(), level);
    }

    public void tick(ServerLevel level) {
        if (fase != FASE_FERMENTANDO && fase != FASE_MATURANDO) {
            return;
        }
        if (level.getGameTime() % INTERVALO != 0L) {
            return;
        }
        ticksRestantes -= INTERVALO;
        // fim da fermentação: pula pra maturação (se existir) ou fica pronto
        if (ticksRestantes <= 0 && fase == FASE_FERMENTANDO) {
            Optional<ProcessosBebida.Barril> rec = receita();
            int maturacao = rec.map(ProcessosBebida::tempoMaturacao).orElse(0);
            if (maturacao > 0) {
                fase = FASE_MATURANDO;
                ticksTotalFase = maturacao;
                ticksRestantes = maturacao;
                level.playSound(null, worldPosition, SoundEvents.BARREL_CLOSE,
                        SoundSource.BLOCKS, 0.7F, 0.8F);
            } else {
                concluir(level);
            }
        } else if (ticksRestantes <= 0) {
            concluir(level);
        } else {
            // borbulha ocasional (1 a cada ~17s; v1.2.60: pitch sobe com o
            // progresso REAL da fase e solta a partícula de bolha estourando)
            if ((ticksRestantes / 20) % 17 == 0) {
                float progresso = ticksTotalFase > 0
                        ? 1F - ticksRestantes / (float) ticksTotalFase : 0F;
                level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                        SoundSource.BLOCKS, 0.5F, 0.8F + 0.2F * progresso);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.6,
                        worldPosition.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.02);
            }
        }
        setChanged();
        // v1.2.55: sincroniza no FIM de cada fase (a maturação é silenciosa;
        // o sync por segundo do progresso não vale o tráfego aqui)
        if ((fase == FASE_MATURANDO || fase == FASE_PRONTA) && ticksRestantes == 0) {
            sincronizar();
        }
    }

    /** Fase terminou: PRONTO com as garrafas do lote. */
    private void concluir(ServerLevel level) {
        fase = FASE_PRONTA;
        ticksRestantes = 0;
        garrafasRestantes = receita().map(ProcessosBebida::garrafasPorLote).orElse(4);
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.BARREL_CLOSE,
                SoundSource.BLOCKS, 0.8F, 1.15F);
    }

    /**
     * Engarrafa do SLOT (a GUI): cada garrafa de vidro (ou VAZIA da própria
     * bebida) no slot 1 vira 1 bebida no slot de saída enquanto houver lote
     * e espaço.
     */
    private void engarrafarDoSlot(ServerLevel level) {
        if (fase != FASE_PRONTA) {
            return;
        }
        Optional<ProcessosBebida.Barril> rec = receita();
        if (rec.isEmpty()) {
            return;
        }
        ItemStack garrafas = itens.get(TipoMaquina.BARRIL.idxSec);
        // v1.2.66: vazia de OUTRA bebida no slot não engarrafa (o barril de
        // cachaca recusa a garrafa do vinho) — espera o fregues tirar.
        if (!garrafas.isEmpty() && !ProcessosBebida.eGarrafaDoBarril(garrafas.getItem(), rec)) {
            return;
        }
        while (!garrafas.isEmpty() && garrafasRestantes > 0
                && inserirSaida(new ItemStack(rec.get().bebidaFinal(), 1))) {
            garrafas.shrink(1);
            if (garrafas.isEmpty()) {
                itens.set(TipoMaquina.BARRIL.idxSec, ItemStack.EMPTY);
                garrafas = itens.get(TipoMaquina.BARRIL.idxSec);
            }
            garrafasRestantes--;
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        if (garrafasRestantes <= 0) {
            esvaziarLote(level);
        } else {
            setChanged();
            sincronizar();
        }
    }

    /** Empilha na saída (mixa com pilha igual); false = não coube. */
    private boolean inserirSaida(ItemStack nova) {
        int idx = TipoMaquina.BARRIL.idxOut;
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

    public boolean fermentando() {
        return fase == FASE_FERMENTANDO;
    }

    /**
     * v1.2.66: gancho de TESTE — pula o timer até PRONTA (as fases reais
     * correm no tick; nos gametests a escala de tempo já é outra). Mesmo
     * caminho do tick: fermentação → maturação (se existir) → concluir.
     */
    void testeAvancarFaseAtePronta(ServerLevel level) {
        if (fase == FASE_FERMENTANDO) {
            Optional<ProcessosBebida.Barril> rec = receita();
            int maturacao = rec.map(ProcessosBebida::tempoMaturacao).orElse(0);
            if (maturacao > 0) {
                fase = FASE_MATURANDO;
                ticksTotalFase = maturacao;
            }
        }
        if (fase == FASE_FERMENTANDO || fase == FASE_MATURANDO) {
            concluir(level);
        }
    }

    /** True se há fermentação/maturação em curso ou dose pendente de saída. */
    public boolean processando() {
        return fermentando() || garrafasRestantes > 0 || loteQtd > 0;
    }

    /** Leitura dos dados sincronizados da GUI (fase, timer, garrafas restantes). */
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
        return itens.stream().allMatch(ItemStack::isEmpty) && garrafasRestantes <= 0;
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
        if (slot == 0 && getLevel() instanceof ServerLevel sl) {
            avancar(sl); // insumo novo: o lote começa sozinho
        }
        if (slot == TipoMaquina.BARRIL.idxSec && getLevel() instanceof ServerLevel sl2) {
            engarrafarDoSlot(sl2); // garrafas chegaram: engarrafa o que der
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
        return Component.translatable("block.intoxicantes.barril_" + getBebida());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MenuMaquinaSNC(id, inv, this, TipoMaquina.BARRIL, dados);
    }

    /** v1.2.69 FIX: payload do ExtendedMenuType (TipoMaquina) — a GUI não abria sem isto. */
    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return TipoMaquina.BARRIL.indice();
    }

    // ==================================================== SOLTE O CONTEÚDO

    /**
     * Quebrou o barril: devolve TUDO — a dose do lote em curso (antes de
     * pronto), o buffer, as garrafas do slot e o produto. Pronto: quem quebra
     * leva o barril; as garrafas restantes caem junto.
     */
    /**
     * v1.2.59 — LOTE INVISÍVEL NO VAZIO (26.3): a dose do lote em curso é um
     * int (não é Container), então {@code destroyBlock} (explosão, pistão,
     * /fill ar) não devolvia NADA — o {@code spawnAfterBreak} do bloco roda
     * DEPOIS da BE sair do chunk. Este hook roda ANTES (LevelChunk.setBlockState
     * chama {@code preRemoveSideEffects} antes de remover a BE): devolve o
     * lote em qualquer remoção. Idempotente com {@code playerWillDestroy}:
     * {@code soltarConteudo} zera o lote, nunca dropa duas vezes.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel servidor) {
            soltarConteudo(servidor);
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void soltarConteudo(ServerLevel level) {
        Optional<ProcessosBebida.Barril> rec = receita();
        if (fase == FASE_PRONTA && garrafasRestantes > 0) {
            rec.ifPresent(r -> dropar(level,
                    new ItemStack(r.bebidaFinal(), garrafasRestantes)));
        } else if (loteQtd > 0 && rec.isPresent()) {
            dropar(level, new ItemStack(rec.get().input(), loteQtd));
        }
        for (int i = 0; i < itens.size(); i++) {
            ItemStack s = itens.get(i);
            if (!s.isEmpty()) {
                dropar(level, s);
                itens.set(i, ItemStack.EMPTY);
            }
        }
        limpar();
    }

    private void dropar(ServerLevel level, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, stack));
    }

    private void entregar(ServerLevel level, Player player, ItemStack stack) {
        if (player instanceof ServerPlayer sp && sp.getInventory().add(stack)) {
            return;
        }
        dropar(level, stack);
    }

    private void limpar() {
        bebida = "";
        fase = FASE_VAZIA;
        ticksRestantes = 0;
        ticksTotalFase = 0;
        garrafasRestantes = 0;
        loteQtd = 0;
        setChanged();
    }

    /** Grava o rótulo no placement (o item sabe qual bebida carrega). */
    public void carregarRotulo(String novaBebida) {
        this.bebida = novaBebida == null ? "" : novaBebida;
        setChanged();
    }

    public String getBebida() {
        // Rótulo preguiçoso: blocos colocados por setBlock/estrutura/teste não
        // passam pelo setPlacedBy — deriva do próprio registro (barril_rum ->
        // "rum"). Blocos normais já chegam com rótulo pelo placement.
        if ((bebida == null || bebida.isEmpty())
                && getBlockState().getBlock() instanceof BarrilBebidaBlock barril) {
            bebida = barril.rotuloDoBlocoPublico();
            setChanged();
        }
        return bebida;
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

    /** A fase atual pro client (só a FERMENTAÇÃO anima; maturação silenciosa). */
    public int faseClient() {
        return fase;
    }

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("barril");
        this.bebida = raiz.getStringOr("bebida", "");
        this.fase = raiz.getIntOr("fase", FASE_VAZIA);
        this.ticksRestantes = raiz.getIntOr("ticksRestantes", 0);
        this.ticksTotalFase = raiz.getIntOr("ticksTotalFase", 0);
        this.garrafasRestantes = raiz.getIntOr("garrafas", 0);
        this.loteQtd = raiz.getIntOr("insumoQtd", 0);
        ContainerHelper.loadAllItems(raiz.childOrEmpty("itens"), itens);
        // MIGRAÇÃO v1.2.55 → v1.2.59: o lote antigo morava em "insumo" (stack)
        if (itens.get(0).isEmpty()) {
            raiz.read("insumo", ItemStack.CODEC).ifPresent(antigo -> {
                Optional<ProcessosBebida.Barril> rec = receita();
                if (rec.isPresent() && antigo.getItem() == rec.get().input()) {
                    int cap = Math.min(Math.max(antigo.getCount(), loteQtd),
                            rec.get().qtdIn() * 4);
                    itens.set(0, antigo.copyWithCount(cap));
                }
            });
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput raiz = output.child("barril");
        raiz.putString("bebida", bebida);
        raiz.putInt("fase", fase);
        raiz.putInt("ticksRestantes", ticksRestantes);
        raiz.putInt("ticksTotalFase", ticksTotalFase);
        raiz.putInt("garrafas", garrafasRestantes);
        raiz.putInt("insumoQtd", loteQtd);
        ContainerHelper.saveAllItems(raiz.child("itens"), itens, true);
    }
}
