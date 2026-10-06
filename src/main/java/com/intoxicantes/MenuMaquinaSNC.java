package com.intoxicantes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * O MENU DAS MÁQUINAS DO SNC (v1.2.59): UM só menu para as SEIS máquinas —
 * a identidade mora no {@link TipoMaquina} (slots, índices e paleta) e a
 * lógica viva mora na BlockEntity (autoridade do servidor).
 *
 * REGIÕES: os primeiros {@code tipo.totalSlots()} slots são da MÁQUINA (0 =
 * insumo; saída(s) e 2ª dose/garrafa nas posições do tipo); os 36 seguintes
 * são o inventário do jogador (27 mochila + 9 hotbar).
 *
 * Regras dos slots:
 * <ul>
 *   <li>INSUMO: só entra item que alguma receita da máquina usa — filtro
 *       ESTÁTICO derivado do {@link ProcessosBebida} (o catálogo é código
 *       comum: cliente e servidor filtram igual, sem sync extra);</li>
 *   <li>SAÍDA/EXTRA: só SAEM (mayPlace = false) — o produto é do motor;</li>
 *   <li>2ª dose (caldeirão) e garrafas (barril): entrada comum;</li>
 *   <li>shift-click funciona nos dois sentidos (quickMoveStack).</li>
 * </ul>
 *
 * O progresso viaja pelo {@link ContainerData} da BlockEntity (barra REAL,
 * server-authoritative — nada de animação inventada no client).
 */
public class MenuMaquinaSNC extends AbstractContainerMenu {

    /** Slots do jogador a partir daqui (mochila 27 + hotbar 9). */
    public static final int SLOTS_JOGADOR = 36;

    private final Container maquina;
    private final TipoMaquina tipo;
    /** Dados vivos (servidor) ou sincronizados (client) — a barra REAL. */
    private final ContainerData dados;

    /** Construtor do SERVIDOR (a BE passa o ContainerData vivo dela). */
    public MenuMaquinaSNC(int id, Inventory playerInv, Container maquina,
            TipoMaquina tipo, ContainerData dados) {
        super(IntoxicantesMod.MENU_MAQUINA_SNC, id);
        this.maquina = maquina;
        this.tipo = tipo;
        this.dados = dados;
        montarSlots(playerInv, dados);
    }

    /** Construtor do CLIENTE (mesma geometria; dados viram SimpleContainerData). */
    private MenuMaquinaSNC(int id, Inventory playerInv, Container dummy,
            TipoMaquina tipo, int tamanhoDados) {
        super(IntoxicantesMod.MENU_MAQUINA_SNC, id);
        this.maquina = dummy;
        this.tipo = tipo;
        this.dados = new SimpleContainerData(tamanhoDados);
        montarSlots(playerInv, this.dados);
    }

    private void montarSlots(Inventory playerInv, ContainerData dados) {
        // ------- slots DA MÁQUINA (posições do TipoMaquina)
        for (int i = 0; i < tipo.totalSlots(); i++) {
            int[] xy = tipo.slotsGui[i];
            if (i == tipo.idxInsumo()) {
                addSlot(new SlotInsumo(this, maquina, i, xy[0], xy[1], tipo));
            } else if (i == tipo.idxOut || i == tipo.idxExtra) {
                addSlot(new SlotSaida(maquina, i, xy[0], xy[1]));
            } else if (tipo == TipoMaquina.BARRIL && i == tipo.idxSec) {
                // v1.2.66: garrafas do barril com filtro (vidro OU a vazia certa)
                addSlot(new SlotGarrafas(this, maquina, i, xy[0], xy[1]));
            } else {
                // 2ª dose (caldeirão): entrada comum
                addSlot(new Slot(maquina, i, xy[0], xy[1]));
            }
        }

        // ------- inventário do jogador (27 mochila embaixo da máquina)
        for (int linha = 0; linha < 3; linha++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + linha * 9 + 9,
                        8 + col * 18, 148 + linha * 18));
            }
        }
        // ------- hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 206));
        }

        addDataSlots(dados);
    }

    /**
     * Fábrica do CLIENTE (payload de abertura: só o ordinal do tipo). O
     * conteúdo dos slots e o ContainerData chegam sincronizados pelo vanilla
     * (ClientboundContainerSetContent / ContainerSetData).
     */
    public static MenuMaquinaSNC reabrir(int id, Inventory playerInv, int tipoOrdinal) {
        TipoMaquina tipo = TipoMaquina.porIndice(tipoOrdinal);
        SimpleContainer dummy = new SimpleContainer(tipo.totalSlots());
        int tamanhoDados = tipo == TipoMaquina.BARRIL ? 4 : 3;
        return new MenuMaquinaSNC(id, playerInv, dummy, tipo, tamanhoDados);
    }

    public TipoMaquina tipo() {
        return tipo;
    }

    /** O primeiro índice do inventário do jogador (a tela usa nos destaques). */
    public int inicioJogador() {
        return tipo.totalSlots();
    }

    /** Dado sincronizado pelo ContainerData (progresso/fase — a barra REAL). */
    public int dado(int indice) {
        return this.dados.get(indice);
    }

    // ==================================================== FILTRO COMUM

    /**
     * O filtro de insumo de CADA máquina, derivado do catálogo — código
     * comum: o cliente pinta o slot "não aceita" igual ao servidor valida.
     */
    public static boolean aceitaInsumo(TipoMaquina tipo, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var item = stack.getItem();
        return switch (tipo) {
            case MOENDA -> ProcessosBebida.moendaDe(item).isPresent();
            case PRENSA -> ProcessosBebida.prensaDe(item).isPresent();
            case CALDEIRAO -> ProcessosBebida.caldeiraoDe(item).isPresent();
            case DORNA -> ProcessosBebida.dornaDe(item).isPresent();
            case ALAMBIQUE -> ProcessosBebida.alambiqueDe(item).isPresent();
            case BARRIL -> ProcessosBebida.barrilQueAceita(item).isPresent();
        };
    }

    // ==================================================== SHIFT-CLICK

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack pilha = slot.getItem();
        ItemStack original = pilha.copy();
        int n = tipo.totalSlots();

        if (index < n) {
            // máquina → jogador
            if (!this.moveItemStackTo(pilha, n, n + SLOTS_JOGADOR, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // jogador → máquina: insumo por DOSES INTEIRAS; o resto (2ª dose,
            // garrafas) segue pelo caminho cru (cada slot filtra o que aceita)
            if (!this.moverInsumoEmDoses(pilha)
                    && !this.moveItemStackTo(pilha, 0, n, false)) {
                // não coube na máquina: troca mochila ↔ hotbar
                if (index < n + 27) {
                    if (!this.moveItemStackTo(pilha, n + 27, n + 36, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(pilha, n, n + 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (pilha.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        // v1.2.60: devolve a SOBRA (não a cópia original — devolver a cópia
        // restaurava a pilha cheia no slot = dupe de shift-click).
        return pilha;
    }

    /**
     * v1.2.60: o shift-click segue a MESMA regra da mão — só entram DOSES
     * INTEIRAS no insumo (7 uvas → 6 no buffer, 1 volta pro slot). O
     * moveItemStackTo cru ignora a dose e estaciona resto que nunca fecha
     * lote. Itens de slot auxiliar (lúpulo, garrafas) seguem pelo caminho
     * cru, porque não são insumo de receita.
     */
    private boolean moverInsumoEmDoses(ItemStack pilha) {
        var dose = doseDe(this.tipo, pilha);
        if (dose.isEmpty()) {
            return false;
        }
        int qtdIn = dose.get();
        int idx = this.tipo.idxInsumo();
        ItemStack atual = this.maquina.getItem(idx);
        if (!atual.isEmpty()
                && !ItemStack.isSameItemSameComponents(atual, pilha)) {
            return false;
        }
        int existente = atual.isEmpty() ? 0 : atual.getCount();
        int inteiras = (existente + pilha.getCount()) / qtdIn * qtdIn;
        int add = inteiras - existente;
        if (add <= 0) {
            return false;
        }
        ItemStack parte = pilha.split(Math.min(add, pilha.getCount()));
        if (atual.isEmpty()) {
            this.maquina.setItem(idx, parte);
        } else {
            atual.grow(parte.getCount());
        }
        this.maquina.setChanged();
        return true;
    }

    /** A dose (qtdIn) que a receita desta máquina cobra do item, se houver. */
    private static java.util.Optional<Integer> doseDe(TipoMaquina tipo,
            ItemStack stack) {
        var item = stack.getItem();
        return switch (tipo) {
            case MOENDA -> ProcessosBebida
                    .primaPrincipal(MaquinaPrimaBlock.Tipo.MOENDA, item)
                    .map(ProcessosBebida.Prima::qtdIn);
            case PRENSA -> ProcessosBebida
                    .primaPrincipal(MaquinaPrimaBlock.Tipo.PRENSA, item)
                    .map(ProcessosBebida.Prima::qtdIn);
            case CALDEIRAO -> ProcessosBebida
                    .primaPrincipal(MaquinaPrimaBlock.Tipo.CALDEIRAO, item)
                    .map(ProcessosBebida.Prima::qtdIn);
            case DORNA -> ProcessosBebida.dornaDe(item)
                    .map(ProcessosBebida.Dorna::qtdIn);
            case ALAMBIQUE -> ProcessosBebida.alambiqueDe(item)
                    .map(ProcessosBebida.Alambique::qtdIn);
            case BARRIL -> ProcessosBebida.barrilQueAceita(item)
                    .map(ProcessosBebida.Barril::qtdIn);
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return this.maquina.stillValid(player);
    }

    // ==================================================== SLOTS ESPECIAIS

    /** Slot de ENTRADA: só aceita item que alguma receita da máquina usa. */
    private static class SlotInsumo extends Slot {
        private final TipoMaquina tipo;

        SlotInsumo(AbstractContainerMenu dono, Container maquina, int index,
                int x, int y, TipoMaquina tipo) {
            super(maquina, index, x, y);
            this.tipo = tipo;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return aceitaInsumo(this.tipo, stack);
        }
    }

    /**
     * v1.2.66 — Slot de GARRAFAS do barril: aceita a garrafa de vidro vanilla
     * OU a VAZIA da própria bebida. No SERVIDOR o rótulo é real (a BE é o
     * container) e a vazia de OUTRA bebida é recusada; no CLIENT o menu nasce
     * de um dummy sem rótulo, então aceita qualquer vazia e o servidor
     * revalida o clique (padrão vanilla: mayPlace é julgado nos dois lados).
     */
    private static class SlotGarrafas extends Slot {
        private final Container maquina;

        SlotGarrafas(AbstractContainerMenu dono, Container maquina, int index, int x, int y) {
            super(maquina, index, x, y);
            this.maquina = maquina;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (stack.getItem() == net.minecraft.world.item.Items.GLASS_BOTTLE) return true;
            if (!(stack.getItem() instanceof GarrafaVaziaItem)) return false;
            if (maquina instanceof BarrilBebidaBlockEntity barril
                    && !barril.getBebida().isEmpty()) {
                return ProcessosBebida.eVaziaDoBarril(stack.getItem(),
                        ProcessosBebida.barrilDe(barril.getBebida()));
            }
            return true; // client dummy: deixa tentar — o servidor decide com o rótulo
        }
    }

    /** Slot de SAÍDA: o produto só sai (o motor é quem põe coisa aqui). */
    private static class SlotSaida extends Slot {
        SlotSaida(Container maquina, int index, int x, int y) {
            super(maquina, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
