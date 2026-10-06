package com.intoxicantes;

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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * O CÉREBRO DA PRATELEIRA DE MERCADO (v1.2.69): 9 slots GENÉRICOS de verdade
 * (qualquer item entra — garrafa, semente, o que o freguês ou o dono puser),
 * venda R$ pelo preço de tabela do Gago (a etiqueta amarela) e o REESTOQUE
 * SEMANAL estilo My Summer Car: o estoque de PARTIDA dos slots é reposto
 * QUARTA-FEIRA (o dia de entrega do {@link CalendarioEsquinao}) e só lá.
 *
 * Compatibilidade "coloca qualquer coisa" (o pedido do playtest):
 * - REPOR: clique com o item na mão (insere do slot que a mira acertar) ou
 *   shift-clique (sobe a pilha inteira pela agenda do Gago). Qualquer item
 *   entra: o que vier da hora da entrega preenche os slots vazios, e o que
 *   o freguês colocar fica de venda também.
 * - COMPRAR: mão vazia tira 1 do slot mirado e cobra o preço de tabela (o
 *   mesmo do balcão do Gago); shift+clique vazio compra TODOS os 9 slots.
 *   Produtos do catálogo usam preço/dose do Gago, mesmo quando repostos
 *   pela mão. Itens fora da tabela mantêm o comportamento gratuito.
 * - TIRAR de volta: shift+clique COM item igual ao do slot: modo "gerente"
 *   (recua a pilha pra mão; o estoque de partida continua do lado de fora).
 */
public class PrateleiraMercadoBlockEntity extends BlockEntity
        implements Container, MenuProvider {

    /**
     * 9 slots POR LADO (v1.2.75): a gôndola virou ilha de MEIO BLOCO com
     * expositor na FRENTE e no FUNDO — o corredor dos dois lados aproveita
     * o mesmo estoque (a fileira oposta vê o outro lado).
     */
    public static final int SLOTS = 9;
    /** A ilha expõe dos dois lados: frente (-Z local) e fundo (+Z local). */
    public static final int LADOS = 2;
    /** Total de slots gerenciados (expostos + reservados na parte de trás). */
    public static final int TOTAL = SLOTS * LADOS;
    /** Colunas da gôndola (posição X dos 3 expositores de cada fileira). */
    static final float[] COLUNAS = {3.5F, 8.0F, 12.5F};
    /** Superfícies do modelo em px; estoque/NBT continuam nos mesmos nove slots. */
    static final float[] NIVEIS = {2.0F, 7.0F, 12.0F};

    private final NonNullList<ItemStack> itens =
            NonNullList.withSize(TOTAL, ItemStack.EMPTY);
    /** Preço por slot (R$) — sincroniza pro tooltip do client (18: frente+fundo). */
    private final int[] precos = new int[TOTAL];
    /** Dose de venda por slot (1 garrafa; 4 sementes por R$ 4 — a unidade do balcão). */
    private final int[] doses = new int[TOTAL];
    /** Produto de reposição por slot; nomes estáveis do catálogo. */
    private final String[] reposicao = new String[TOTAL];
    /** Seção do mercado (a agenda da FRENTE); o fundo usa a seção alternada. */
    private int secao;

    /** Ticks do dayTime já processados: a virada de dia/entrega roda 1x. */
    private long ultimoDayTime = Long.MIN_VALUE;
    /** Dia comercial da última entrega (sem isso 4:59 da sexta repõe de novo). */
    private long ultimoDiaEntrega = Long.MIN_VALUE;
    /** true = a reposição de QUARTA já aplicou ao menos uma vez (persistido). */
    private boolean primeiraEntregaFeita;

    public PrateleiraMercadoBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.PRATELEIRA_MERCADO_ENTITY, pos, state);
        secao = Math.floorMod(pos.getX() + 3 * pos.getY() + pos.getZ(),
                TradeCatalog.secoesPrateleira());
        java.util.Arrays.fill(reposicao, "");
    }

    // ==================================================== AGENDA DA ENTREGA

    /** A agenda padrão: o dia comercial da ENTREGA (quarta). */
    public static long diaDeEntregaPadrao() {
        return CalendarioEsquinao.DIA_ENTREGA;
    }

    public void carregarAgenda(int diaEntrega) {
        // v1.2.69: a agenda é GLOBAL (CalendarioEsquinao.DIA_ENTREGA); o gancho
        // existe pra o gametest calibrar (não persiste: o teste é efêmero)
    }

    public int diaEntregaAgendado() {
        return CalendarioEsquinao.DIA_ENTREGA;
    }

    public int diasAteEntrega(long dayTime) {
        return CalendarioEsquinao.diasAteEntrega(CalendarioEsquinao.diaDaSemana(dayTime));
    }

    /**
     * O TICK (server, 1×/s): detecta a virada de dia comercial (às 07:00) e,
     * se o dia novo é o da entrega e esta prateleira ainda não repôs nessa
     * semana, repõe os PARTIDOS dos slots (o que o freguês não comprou fica;
     * o que sumiu volta). Mundinhos de teste: dayTime parado nunca dispara.
     */
    public void tick(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        long dayTime = CalendarioEsquinao.tempoTotal(level);
        if (!CalendarioEsquinao.virouDeDia(ultimoDayTime, dayTime)) {
            return;
        }
        long diaNovo = DailyTradeStock.tradingDay(dayTime);
        int semana = CalendarioEsquinao.diaDaSemana(dayTime);
        if (semana == diaEntregaAgendado() && portaDaEntrega(diaNovo)) {
            reabastecer(level);
            ultimoDiaEntrega = diaNovo;
            primeiraEntregaFeita = true;
        }
        ultimoDayTime = dayTime;
        setChanged();
    }

    /** O portão: a entrega só entra se este dia comercial é NOVO (nunca 2x no dia). */
    private boolean portaDaEntrega(long diaComercial) {
        return ultimoDiaEntrega == Long.MIN_VALUE || diaComercial > ultimoDiaEntrega;
    }

    /**
     * A entrega completa a agenda de cada seção, com preço e dose do Gago.
     * STOCK conta compras: converte para unidades sem exceder a pilha máxima.
     * Pilha vazia
     * recebe o produto; pilha abastecida parcialmente é completada; o que o
     * DONO pôs por conta própria (produto diferente da agenda) NUNCA é
     * tocado.
     */
    public void reabastecer(ServerLevel level) {
        var agenda = TradeCatalog.prateleira(secao);
        boolean tocou = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            TradeCatalog.Entry e = agenda.get(slot);
            if (!reposicao[slot].isEmpty()) {
                var salvo = entrada(reposicao[slot]);
                if (salvo != null) e = salvo;
            }
            ItemStack atual = itens.get(slot);
            int estoque = Math.min(e.stack().getMaxStackSize(), e.stock() * e.count());
            if (atual.isEmpty()) {
                itens.set(slot, new ItemStack(e.item(), estoque));
                precos[slot] = e.price();
                doses[slot] = e.count();
                reposicao[slot] = e.id();
                tocou = true;
            } else if (atual.getItem() == e.item()
                    && atual.getCount() < estoque) {
                atual.setCount(estoque);
                precos[slot] = e.price();
                doses[slot] = e.count();
                reposicao[slot] = e.id();
                tocou = true;
            }
        }
        // A PARTE DE TRÁS (slots 9..17) é o segundo corredor da ilha: a
        // entrega usa a SEÇÃO ALTERNADA (a fileira dupla do corredor central
        // não repete a mercadoria) — a MESMA conta do template (gen_mercado).
        var agendaFundo = TradeCatalog.prateleira(secao + 1);
        for (int slot = 0; slot < SLOTS; slot++) {
            int indice = SLOTS + slot;
            TradeCatalog.Entry e = agendaFundo.get(slot);
            ItemStack atual = itens.get(indice);
            int estoque = Math.min(e.stack().getMaxStackSize(), e.stock() * e.count());
            if (atual.isEmpty()) {
                itens.set(indice, new ItemStack(e.item(), estoque));
                precos[indice] = e.price();
                doses[indice] = e.count();
                reposicao[indice] = e.id();
                tocou = true;
            } else if (atual.getItem() == e.item()
                    && atual.getCount() < estoque) {
                atual.setCount(estoque);
                precos[indice] = e.price();
                doses[indice] = e.count();
                reposicao[indice] = e.id();
                tocou = true;
            }
        }
        if (tocou) {
            setChanged();
            sincronizarTudo();
            level.playSound(null, worldPosition, SoundEvents.VILLAGER_WORK_LIBRARIAN,
                    SoundSource.BLOCKS, 0.6F, 0.85F);
        }
    }

    private static TradeCatalog.Entry entrada(String id) {
        for (var e : TradeCatalog.gago(0)) if (e.id().equals(id)) return e;
        return null;
    }

    private static TradeCatalog.Entry entrada(ItemStack item) {
        for (var e : TradeCatalog.gago(0)) if (item.is(e.item())) return e;
        return null;
    }

    private void precificar(int slot, ItemStack item) {
        var e = entrada(item);
        precos[slot] = e == null ? 0 : e.price();
        doses[slot] = e == null ? 1 : e.count();
        reposicao[slot] = e == null ? "" : e.id();
    }

    // ==================================================== INTERAÇÃO (cliques)

    /**
     * Clique com o item na mão: REPÕE a prateleira (qualquer item — o pedido
     * central do playtest). A pilha inteira entra (gôndola guarda caixa); o
     * que não coube volta pra mão. Devolve true se ENTROU algo.
     */
    public boolean reabastecerDaMao(ServerLevel level, Player player,
            ItemStack mao, int slot, boolean fundo) {
        if (mao.isEmpty()) {
            return false;
        }
        slot += fundo ? SLOTS : 0;
        ItemStack alvo = itens.get(slot);
        if (alvo.isEmpty() || ItemStack.isSameItemSameComponents(alvo, mao)) {
            int espaco = alvo.isEmpty() ? mao.getMaxStackSize()
                    : alvo.getMaxStackSize() - alvo.getCount();
            if (espaco <= 0) {
                return false;
            }
            int entra = Math.min(espaco, mao.getCount());
            if (alvo.isEmpty()) {
                itens.set(slot, mao.split(entra));
                precificar(slot, itens.get(slot));
            } else {
                alvo.grow(entra);
                mao.shrink(entra);
            }
            // Repor também corrige etiquetas gratuitas de pilhas antigas
            // quando o produto agora faz parte do catálogo do mercado.
            if (entrada(itens.get(slot)) != null) precificar(slot, itens.get(slot));
            setChanged();
            sincronizar();
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP,
                    SoundSource.BLOCKS, 0.5F, 1.0F);
            return true;
        }
        return false;
    }

    /**
     * Clique vazio compra a dose da etiqueta; itens do catálogo são cobrados.
     * Uma dose incompleta cobra a fração entregue. Shift+clique vazio: leva o
     * CARRINHO inteiro (todos os 9 slots do lado clicado). O LADO (frente ou
     * fundo da ilha) vem da face clicada. Devolve true se saiu algo.
     */
    public boolean comprar(ServerLevel level, ServerPlayer player,
            int slot, boolean carrinho, boolean ladoFundo) {
        boolean comprou = false;
        int inicio = carrinho ? 0 : slot;
        int fim = carrinho ? SLOTS : slot + 1;
        for (int i = inicio; i < fim; i++) {
            ItemStack pilha = itens.get((ladoFundo ? SLOTS : 0) + i);
            if (pilha.isEmpty()) {
                continue;
            }
            // a DOSE da etiqueta (1 garrafa; 4 sementes por R$ 4 — o mesmo
            // pacote do balcão); produto do dono vende 1 por clique
            int leva = Math.min(Math.max(1, doses[i]), pilha.getCount());
            // Fração final de um pacote cobra somente a parte entregue.
            int preco = (int) Math.ceil(precos[i] * (double) leva / Math.max(1, doses[i]));
            if (preco > 0 && !PlayerMoney.subtrair(player, preco)) {
                if (!comprou && !carrinho) {
                    avisar(player, Component.translatable(
                            "block.intoxicantes.prateleira_sem_dinheiro", preco), true);
                }
                continue; // carrinho segue pro próximo (leva o que der)
            }
            ItemStack saida = pilha.copyWithCount(leva);
            pilha.shrink(leva);
            if (pilha.isEmpty()) {
                itens.set(i, ItemStack.EMPTY);
            }
            entregar(level, player, saida);
            if (!carrinho) {
                avisar(player, preco > 0
                        ? Component.translatable("prateleira.intoxicantes.compra",
                                saida.getHoverName(), preco)
                        : Component.translatable("prateleira.intoxicantes.compra_gratis",
                                saida.getHoverName()), true);
            }
            comprou = true;
        }
        if (comprou) {
            sincronizarVenda();
            level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return comprou;
    }

    /**
     * Modo GERENTE (shift+clique com item): recolhe a pilha do slot pra mão —
     * o dono reorganiza ou esvazia a gôndola. O estoque de PARTIDA (quarta)
     * recomeça quando o slot esvaziar.
     */
    public boolean recolherParaMao(ServerLevel level, Player player, int slot, boolean fundo) {
        slot += fundo ? SLOTS : 0;
        ItemStack pilha = itens.get(slot);
        if (pilha.isEmpty()) {
            return false;
        }
        entregar(level, player, pilha);
        itens.set(slot, ItemStack.EMPTY);
        precos[slot] = 0;
        doses[slot] = 1;
        reposicao[slot] = "";
        setChanged();
        sincronizar();
        return true;
    }

    private void entregar(ServerLevel level, Player player, ItemStack stack) {
        if (player instanceof ServerPlayer sp && sp.getInventory().add(stack)) {
            return;
        }
        level.addFreshEntity(new ItemEntity(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, stack));
    }

    /** Overlay (actionbar) pro ServerPlayer; chat pro resto. */
    private void avisar(Player player, Component msg, boolean overlay) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(msg, overlay);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    // ==================================================== CONTAINER

    @Override
    public int getContainerSize() {
        return itens.size();
    }

    @Override
    public boolean isEmpty() {
        return itens.stream().allMatch(ItemStack::isEmpty);
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
        boolean mudouProduto = !ItemStack.isSameItemSameComponents(itens.get(slot), stack);
        itens.set(slot, stack);
        if (stack.getCount() > stack.getMaxStackSize()) {
            stack.setCount(stack.getMaxStackSize());
        }
        if (mudouProduto && !stack.isEmpty() && slot < SLOTS * LADOS) precificar(slot, stack);
        setChanged();
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
    // v1.2.69: a gôndola é VENDA DIRETA (clique), sem GUI — a etiqueta é a
    // tela. MenuProvider fica pra (1.2.70?): o menu de prateleira com preço.

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.intoxicantes.prateleira_mercado");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return null;
    }

    // ==================================================== SOLTE O CONTEÚDO

    /** Quebrou a prateleira: os produtos caem no chão (nunca somem). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel servidor) {
            soltarConteudo(servidor);
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void soltarConteudo(ServerLevel level) {
        for (int i = 0; i < itens.size(); i++) {
            ItemStack s = itens.get(i);
            if (!s.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, s));
                itens.set(i, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    // ==================================================== SYNC DO RENDERER

    private void sincronizar() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * O freguês anotou um pedido no carrinho: a gôndola só persiste — o
     * estoque não mudou ainda e o cliente não tem nada novo pra desenhar.
     */
    public void marcarComercial() {
        setChanged();
    }

    /** A venda mexeu no estoque (ou foi desfeita): o renderer vê na hora. */
    public void sincronizarVenda() {
        setChanged();
        sincronizar();
    }

    /** Entrega do caminhão: os DOIS lados voltam ao client. */
    public void sincronizarTudo() {
        setChanged();
        sincronizar();
    }

    /** Índice REAL (18 slots) de um pedido do carrinho: fonte + lado + slot. */
    public static int indiceLivre(Iterable<net.minecraft.world.item.ItemStack> fontes,
            int slot, int lado) {
        int indice = lado * SLOTS + slot;
        for (var fonte : fontes) {
            if (indice >= TOTAL) return -1;
            if (!fonte.isEmpty()) indice += SLOTS;
        }
        return indice >= TOTAL ? -1 : indice;
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

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("prateleira");
        boolean legado = raiz.getIntOr("secao", -1) < 0;
        secao = Math.floorMod(raiz.getIntOr("secao", secao), TradeCatalog.secoesPrateleira());
        this.ultimoDayTime = raiz.getLongOr("ultimoDayTime", Long.MIN_VALUE);
        this.ultimoDiaEntrega = raiz.getLongOr("ultimoDiaEntrega", Long.MIN_VALUE);
        this.primeiraEntregaFeita = raiz.getBooleanOr("primeiraEntrega", false);
        ContainerHelper.loadAllItems(raiz.childOrEmpty("itens"), itens);
        var pc = raiz.childOrEmpty("precos");
        var ds = raiz.childOrEmpty("doses");
        var ag = raiz.childOrEmpty("reposicao");
        for (int i = 0; i < TOTAL; i++) {
            precos[i] = pc.getIntOr(String.valueOf(i), 0);
            doses[i] = ds.getIntOr(String.valueOf(i), 0);
            reposicao[i] = ag.getStringOr(String.valueOf(i), "");
            if (i >= SLOTS) {
                // Lado do FUNDO (v1.2.75): sem lógica de migração legada (a
                // seção alternada é recalculada na entrega de quarta).
                if (!reposicao[i].isEmpty() && doses[i] <= 0) {
                    var e = entrada(reposicao[i]);
                    if (e != null) doses[i] = e.count();
                }
                continue;
            }
            // Saves antigos mantêm produtos, quantidades e preços. A agenda
            // reconhece os itens pagos sem substituir estoque existente.
            if (reposicao[i].isEmpty() && precos[i] > 0) {
                var e = entrada(itens.get(i));
                // Um slot legado vendido até zero ainda guarda a etiqueta.
                // Reconstitui a agenda antiga sem substituir produtos vivos.
                if (e == null && legado && itens.get(i).isEmpty()) {
                    String[] agendaAntiga = {"cerveja", "vinho", "hidromel", "cachaca",
                            "suco_detox", "agua_de_coco", "cha_lupulo", "semente_loupulo", "semente_uva"};
                    e = entrada(agendaAntiga[i]);
                }
                if (e != null) {
                    reposicao[i] = e.id();
                    if (doses[i] <= 0) doses[i] = e.count();
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput raiz = output.child("prateleira");
        raiz.putInt("secao", secao);
        raiz.putLong("ultimoDayTime", ultimoDayTime);
        raiz.putLong("ultimoDiaEntrega", ultimoDiaEntrega);
        raiz.putBoolean("primeiraEntrega", primeiraEntregaFeita);
        ContainerHelper.saveAllItems(raiz.child("itens"), itens, true);
        var pc = raiz.child("precos");
        var ds = raiz.child("doses");
        var ag = raiz.child("reposicao");
        for (int i = 0; i < TOTAL; i++) {
            pc.putInt(String.valueOf(i), precos[i]);
            ds.putInt(String.valueOf(i), doses[i]);
            ag.putString(String.valueOf(i), reposicao[i]);
        }
    }

    // ==================================================== GANCHOS DE TESTE

    /** Gametest: simula a virada pro dia `diaSemana` do dia comercial
     *  `diaComercial` — o MESMO portão do tick (dia errado não repõe nada;
     *  o mesmo dia comercial não repõe de novo; a semana seguinte repõe). */
    void testeForcarEntrega(ServerLevel level, int diaSemana, long diaComercial) {
        ultimoDayTime = diaComercial * 24000L + 1000L;
        if (diaSemana != CalendarioEsquinao.DIA_ENTREGA || !portaDaEntrega(diaComercial)) {
            setChanged();
            return;
        }
        reabastecer(level);
        ultimoDiaEntrega = diaComercial;
        primeiraEntregaFeita = true;
        setChanged();
    }

    /** Gametest: a 1ª entrega (as prateleiras nascem VAZIAS, estilo MSC). */
    boolean primeiraEntregaFeita() {
        return primeiraEntregaFeita;
    }

    /** Preço do slot (a etiqueta; 0 = produto do dono, sai de graça). */
    public int precoDoSlot(int slot) {
        return precos[slot];
    }

    public int doseDoSlot(int slot) {
        return Math.max(1, doses[slot]);
    }

    public void definirPreco(int slot, int preco) {
        precos[slot] = Math.max(0, preco);
        setChanged();
    }
}
