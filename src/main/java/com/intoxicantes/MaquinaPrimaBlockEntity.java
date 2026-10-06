package com.intoxicantes;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
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
 * O CÉREBRO DA MÁQUINA DE PRIMA (v1.2.59 — GUI + motor de lotes): executa a
 * receita do {@link ProcessosBebida} com INVENTÁRIO DE VERDADE.
 *
 * ARQUITETURA DO REWORK: os itens moram num {@link Container} próprio
 * (slot 0 = insumo; caldeirão tem 1 = lúpulo; saída/extra nas posições do
 * {@link TipoMaquina}) e a lógica é o MOTOR DE LOTES:
 * <ul>
 *   <li>aceita pilha MAIOR que a dose ({@code >=}) — fim do "exatamente 6";</li>
 *   <li>consome EXATAMENTE a dose ao FECHAR cada lote (nunca a pilha toda);</li>
 *   <li>a sobra fica no slot e o lote seguinte começa sozinho — 13 uvas →
 *       2 lotes + 1 uva parada (produção contínua lote a lote);</li>
 *   <li>saída cheia NÃO perde produto: o lote fica parado
 *       ({@code producaoPendente}) até sobrar espaço;</li>
 *   <li>servidor é a autoridade; a GUI lê o progresso REAL por
 *       {@link ContainerData} e os itens pelos slots sincronizados.</li>
 * </ul>
 *
 * Modo DUAS DOSES (caldeirão): mostura (malte) → pede lúpulo no slot 1 →
 * fervura → serve. O caldeirão só roda com ÁGUA embaixo; a falta aparece na
 * GUI via estado {@link #FASE_SEM_AGUA}.
 */
public class MaquinaPrimaBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<Integer> {

    private final MaquinaPrimaBlock.Tipo tipo;
    private final NonNullList<ItemStack> itens;
    private int ticksRestantes;
    private int ticksTotal;
    /** Caldeirão: mostura fechou, esperando o lúpulo (slot da 2ª dose). */
    private boolean esperandoSegunda;
    /** Caldeirão: o lote em curso é a FERVURA (a mostura já passou). */
    private boolean fervuraEmCurso;
    /** Lote fechou mas a saída está cheia: espera espaço (nada se perde). */
    private boolean producaoPendente;
    /**
     * v1.2.59 — O produto principal ENTREGOU mas o EXTRA (bagaço) não coube.
     * Diferencia o retry: sem ela, o retry re-insere o principal e DUPLICAVA
     * produto (uma dose de cana virava 8 caldo quando o slot do bagaço lotava).
     */
    private boolean pendenciaExtra;

    /** Fases sincronizadas pra GUI (ContainerData índice 2). */
    public static final int FASE_VAZIA = 0;
    public static final int FASE_PROCESSANDO = 1;
    public static final int FASE_AGUARDANDO_SEGUNDA = 2;
    public static final int FASE_FERVURA = 3;
    public static final int FASE_PRONTA = 4;        // produto no slot de saída
    public static final int FASE_SAIDA_CHEIA = 5;   // lote fechou, sem espaço
    public static final int FASE_SEM_AGUA = 6;      // caldeirão sem água embaixo

    /** 1 verificação por segundo (o custo por tick é o mesmo de sempre). */
    private static final int INTERVALO = 20;

    /** Dados sincronizados com a GUI — a barra de progresso REAL do motor. */
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

    public MaquinaPrimaBlockEntity(BlockPos pos, BlockState state, MaquinaPrimaBlock.Tipo tipo) {
        super(switch (tipo) {
            case MOENDA -> IntoxicantesMod.MOENDA_CANA_ENTITY;
            case PRENSA -> IntoxicantesMod.PRENSA_UVAS_ENTITY;
            case CALDEIRAO -> IntoxicantesMod.CALDEIRAO_MOSTURA_ENTITY;
        }, pos, state);
        this.tipo = tipo;
        this.itens = NonNullList.withSize(tipoMaquina().totalSlots(), ItemStack.EMPTY);
    }

    /** A identidade da GUI/registro (derivada do tipo do bloco). */
    public TipoMaquina tipoMaquina() {
        return switch (tipo) {
            case MOENDA -> TipoMaquina.MOENDA;
            case PRENSA -> TipoMaquina.PRENSA;
            case CALDEIRAO -> TipoMaquina.CALDEIRAO;
        };
    }

    public MaquinaPrimaBlock.Tipo tipo() {
        return tipo;
    }

    /** A receita válida pro insumo ATUAL do slot 0 (pilha >= dose). */
    private Optional<ProcessosBebida.Prima> receitaDoInsumo() {
        ItemStack insumo = itens.get(0);
        if (insumo.isEmpty()) {
            return Optional.empty();
        }
        return ProcessosBebida.primaDoLote(tipo, insumo.getItem(), insumo.getCount());
    }

    // ==================================================== FASES (pra GUI)

    private int faseAtual() {
        if (tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO && esperandoSegunda) {
            return FASE_AGUARDANDO_SEGUNDA;
        }
        if (producaoPendente) {
            return FASE_SAIDA_CHEIA;
        }
        if (faseCaldeiraoFervura()) {
            return FASE_FERVURA;
        }
        if (ticksRestantes > 0) {
            return FASE_PROCESSANDO;
        }
        if (!itens.get(tipoMaquina().idxOut).isEmpty()) {
            return FASE_PRONTA;
        }
        if (tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO
                && getLevel() instanceof ServerLevel sl && !temAgua(sl)) {
            return FASE_SEM_AGUA; // insumo esperando e a água sumiu (GUI)
        }
        return FASE_VAZIA;
    }

    private boolean faseCaldeiraoFervura() {
        return tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO && fervuraEmCurso;
    }

    // ==================================================== INTERAÇÃO LEGADA
    // (cliques nos blocos e gametests: operam o INVENTÁRIO agora)

    /** Quantidade da dose desta stack segundo a receita (0 = não aceita). */
    public int qtdNecessaria(ItemStack stack) {
        Optional<ProcessosBebida.Prima> r = ProcessosBebida.primaDe(tipo,
                stack.getItem(), stack.getCount());
        return r.map(ProcessosBebida.Prima::qtdIn).orElse(0);
    }

    /** O caldeirão exige água (fonte ou caldeirão cheio) embaixo. */
    private boolean temAgua(ServerLevel level) {
        if (tipo != MaquinaPrimaBlock.Tipo.CALDEIRAO) {
            return true;
        }
        var estado = level.getBlockState(worldPosition.below());
        if (estado.is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        return estado.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON)
                && estado.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON) > 0;
    }

    /**
     * Clique com item (rota de inserção rápida): joga o insumo NO SLOT 0. A
     * mão manda até 4 doses de uma vez (o motor consome lote a lote); NÃO
     * consome nada aqui fora — quem gasta a dose é o motor, ao fechar o lote.
     */
    public boolean tentarCarregar(ServerLevel level, Player player, ItemStack stack) {
        // 2ª dose do caldeirão (o lúpulo da fervura) pela rota antiga
        if (esperandoSegunda && !itens.get(0).isEmpty()) {
            ItemStack insumo = itens.get(0);
            Optional<ProcessosBebida.Prima> r = ProcessosBebida.primaDoLote(
                    tipo, insumo.getItem(), insumo.getCount());
            if (r.isPresent() && r.get().secIn() != null
                    && stack.getItem() == r.get().secIn()
                    && stack.getCount() >= r.get().secQtd()) {
                ItemStack dose = stack.split(r.get().secQtd());
                itens.set(tipoMaquina().idxSec, dose);
                iniciarFervura();
                avisar(player, Component.translatable(
                        "block.intoxicantes.prima_segunda_dose",
                        ticksTotal / 20), true);
                level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                        SoundSource.BLOCKS, 0.8F, 1.1F);
                return true;
            }
            avisar(player, Component.translatable(
                    "block.intoxicantes.prima_falta_segunda"), true);
            return false;
        }

        if (processando() || esperandoSegunda || producaoPendente) {
            return false; // lote em curso é respeitado
        }
        if (!itens.get(tipoMaquina().idxOut).isEmpty()) {
            return false; // tem produto esperando recolha
        }
        Optional<ProcessosBebida.Prima> r = ProcessosBebida.primaQueAceitaLote(
                tipo, stack.getItem());
        if (r.isEmpty()) {
            return false;
        }
        if (tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO && !temAgua(level)) {
            avisar(player, Component.translatable(
                    "block.intoxicantes.caldeirao_sem_agua"), true);
            return false;
        }
        // entra só em DOSES INTEIRAS (6 em 6 p/ uva); a sobra FICA na mão do
        // jogador e dose parcial é recusada (2 uvas não fecham lote de 6)
        int dose = r.get().qtdIn();
        int doses = Math.min(stack.getCount() / dose, 4);
        if (doses == 0) {
            return false; // menos de uma dose inteira: não aceita nada
        }
        int entra = doses * dose;
        ItemStack sobraSlot = itens.get(0);
        if (!sobraSlot.isEmpty() && sobraSlot.getItem() != stack.getItem()) {
            return false; // já tem outro insumo no buffer
        }
        if (sobraSlot.isEmpty()) {
            itens.set(0, stack.split(entra));
        } else {
            int espaco = Math.min(entra, sobraSlot.getMaxStackSize() - sobraSlot.getCount());
            if (espaco <= 0) {
                return false;
            }
            sobraSlot.grow(stack.split(espaco).getCount());
        }
        setChanged();
        sincronizar();
        iniciarLote(r.get());
        avisar(player, Component.translatable(
                "block.intoxicantes.prima_carregada", ticksTotal / 20), true);
        return true;
    }

    /** Overlay (actionbar) pra ServerPlayer; chat pro resto. */
    private void avisar(Player player, Component msg, boolean overlay) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(msg, overlay);
        } else {
            player.sendSystemMessage(msg);
        }
    }

    /** Clique de mão vazia (rota antiga): recolhe o pronto ou mostra o progresso. */
    public void interagir(ServerLevel level, Player player) {
        if (coletarSaida(player)) {
            level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE,
                    SoundSource.BLOCKS, 0.8F, 1.1F);
            return;
        }
        if (esperandoSegunda) {
            avisar(player, Component.translatable(
                    "block.intoxicantes.prima_falta_segunda"), true);
            return;
        }
        if (processando()) {
            int seg = ticksRestantes / 20;
            avisar(player, Component.translatable(
                    "block.intoxicantes.prima_processando", seg,
                    Math.max(0, Math.min(100, 100 - ticksRestantes * 100 / Math.max(1, ticksTotal)))),
                    true);
        } else {
            avisar(player, Component.translatable(
                    "block.intoxicantes.prima_vazia"), true);
        }
    }

    /** Entrega o produto (+extra); true se recolheu algo. Libera lote parado. */
    private boolean coletarSaida(Player player) {
        boolean recolheu = false;
        ItemStack saida = itens.get(tipoMaquina().idxOut);
        if (!saida.isEmpty()) {
            entregar(player, saida);
            itens.set(tipoMaquina().idxOut, ItemStack.EMPTY);
            recolheu = true;
        }
        if (tipoMaquina().idxExtra >= 0 && !itens.get(tipoMaquina().idxExtra).isEmpty()) {
            ItemStack extra = itens.get(tipoMaquina().idxExtra);
            entregar(player, extra);
            itens.set(tipoMaquina().idxExtra, ItemStack.EMPTY);
            recolheu = true;
        }
        if (recolheu) {
            setChanged();
            sincronizar();
            avancar();
        }
        return recolheu;
    }

    private void entregar(Player player, ItemStack stack) {
        ItemStack copia = stack.copy();
        if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && sp.getInventory().add(copia)) {
            return;
        }
        if (getLevel() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.7,
                    worldPosition.getZ() + 0.5, copia));
        }
    }

    // ==================================================== MOTOR DE LOTES

    /** Começa UM lote (timer da receita); a dose é consumida ao FECHAR. */
    private void iniciarLote(ProcessosBebida.Prima rec) {
        this.ticksTotal = ProcessosBebida.tempoPrima(rec);
        this.ticksRestantes = ticksTotal;
        this.producaoPendente = false;
        this.pendenciaExtra = false;
        this.esperandoSegunda = false;
        if (tipo != MaquinaPrimaBlock.Tipo.CALDEIRAO) {
            this.fervuraEmCurso = false;
        }
        setChanged();
        sincronizar();
    }

    /** A fervura do caldeirão (30s fixos — a 2ª metade do ciclo da cerveja). */
    private void iniciarFervura() {
        this.esperandoSegunda = false;
        this.fervuraEmCurso = true;
        this.producaoPendente = false;
        this.ticksTotal = ModConfig.ticksDeSegundos(30F);
        this.ticksRestantes = ticksTotal;
        setChanged();
        sincronizar();
    }

    /**
     * O coração do motor: com timer zerado, decide o próximo passo — fechar
     * lote pendente, iniciar o seguinte, pedir a 2ª dose ou ficar ociosa.
     * Chamado ao carregar, recolher produto, mudar slot e fechar lote.
     */
    private void avancar() {
        if (esperandoSegunda || ticksRestantes > 0) {
            return; // rodando ou esperando lúpulo
        }
        ItemStack insumo = itens.get(0);
        if (insumo.isEmpty()) {
            return;
        }
        Optional<ProcessosBebida.Prima> rec = receitaDoInsumo();
        if (rec.isEmpty()) {
            return; // insumo solto sem receita (hint na GUI)
        }
        if (producaoPendente) {
            tentarProduzir(rec.get()); // saída abriu espaço: tenta de novo
            return;
        }
        if (tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO) {
            if (fervuraEmCurso) {
                tentarProduzir(rec.get()); // fervura fechou, sem espaço antes
            } else if (getLevel() instanceof ServerLevel sl && temAgua(sl)) {
                iniciarLote(rec.get()); // mostura
            }
            return;
        }
        iniciarLote(rec.get()); // moenda/prensa: próximo lote (produção contínua)
    }

    /**
     * Fecha o lote: produz (+extra), consome EXATAMENTE a dose e encadeia o
     * próximo passo. Se a saída não couber, marca {@code producaoPendente}
     * (nada se perde; tenta de novo quando sobrar espaço).
     */
    private void tentarProduzir(ProcessosBebida.Prima r) {
        boolean fervura = faseCaldeiraoFervura();
        // retry do EXTRA: o principal JÁ entregou e a dose JÁ foi gasta na
        // 1ª passada — produzir/consumir de novo DUPLICAVA produto
        boolean soExtra = pendenciaExtra;
        // pendência do EXTRA: o principal JÁ está no slot — nunca re-produz
        if (!soExtra
                && !inserirSaida(tipoMaquina().idxOut, new ItemStack(r.output(), r.qtdOut()))) {
            this.producaoPendente = true;
            this.ticksRestantes = 0;
            setChanged();
            sincronizar();
            return;
        }
        if (r.extraQtd() > 0 && tipoMaquina().idxExtra >= 0
                && !inserirSaida(tipoMaquina().idxExtra,
                        new ItemStack(r.extraOut(), r.extraQtd()))) {
            // extra sem espaço: o principal fica entregue e o extra sai no retry
            this.producaoPendente = true;
            this.pendenciaExtra = true;
            this.ticksRestantes = 0;
            setChanged();
            sincronizar();
            return;
        }
        this.pendenciaExtra = false;
        // consome EXATAMENTE a dose do insumo SÓ na passada que produziu (a
        // sobra vira o próximo lote; no retry do extra ela já foi gasta)
        if (!soExtra) {
            ItemStack insumo = itens.get(0);
            insumo.shrink(r.qtdIn());
            if (insumo.isEmpty()) {
                itens.set(0, ItemStack.EMPTY);
            }
        }
        if (fervura && tipoMaquina().idxSec >= 0) {
            // a fervura gasta o lúpulo da 2ª dose
            ItemStack sec = itens.get(tipoMaquina().idxSec);
            if (!sec.isEmpty()) {
                sec.shrink(r.secQtd());
                if (sec.isEmpty()) {
                    itens.set(tipoMaquina().idxSec, ItemStack.EMPTY);
                }
            }
            this.fervuraEmCurso = false;
            setChanged();
            sincronizar();
            if (getLevel() instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, SoundEvents.WOOD_BREAK,
                        SoundSource.BLOCKS, 0.9F, 0.8F);
            }
            // encadeia: malte de novo? mostura se tiver água; senão espera
            if (!itens.get(0).isEmpty() && getLevel() instanceof ServerLevel sl2) {
                if (temAgua(sl2)) {
                    iniciarLote(r);
                } else {
                    // água sumiu: fica ociosa com o insumo (hint FASE_SEM_AGUA
                    // só aparece processando; aqui simplesmente não roda)
                }
            }
            return;
        }
        if (tipo == MaquinaPrimaBlock.Tipo.CALDEIRAO) {
            // MOSTURA fechou: gasta o malte (feito acima) e pede o lúpulo
            if (tipoMaquina().idxSec >= 0 && !itens.get(tipoMaquina().idxSec).isEmpty()) {
                iniciarFervura(); // lúpulo já estava no slot: fervura direto
            } else {
                this.esperandoSegunda = true;
                setChanged();
                sincronizar();
                if (getLevel() instanceof ServerLevel sl) {
                    sl.playSound(null, worldPosition, SoundEvents.WOOD_HIT,
                            SoundSource.BLOCKS, 0.8F, 0.9F);
                }
            }
            return;
        }
        // moenda/prensa: fecha um lote e já abre o próximo (contínua) —
        // mas NUNCA com o buffer vazio (lote fantasma de timer órfão)
        if (!itens.get(0).isEmpty()) {
            iniciarLote(r);
        }
    }

    /** Empilha no slot de saída (mixa com pilha igual); false = não coube. */
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
            return;
        }
        ticksRestantes -= INTERVALO;
        if (ticksRestantes <= 0) {
            ticksRestantes = 0;
            Optional<ProcessosBebida.Prima> rec = receitaDoInsumo();
            if (rec.isPresent()) {
                tentarProduzir(rec.get());
            }
        }
        setChanged();
        sincronizar();
        // v1.2.60: efeitos por máquina, guiados pelo progresso REAL do lote
        // (mesmo gancho 1/s que o motor — nada por tick, nada client-side)
        efeitosDeProcesso(level);
    }

    /**
     * v1.2.60 — a assinatura sonora/particular de cada ofício: a MOENDA
     * "tritaca" (tranco curto, grave → agudo com o lote), a PRENSA "tranca"
     * (fuso forçando, grave → tenso), o CALDEIRÃO fervilha (chiado, alto →
     * ferver mais suave) e o vapor engrossa no fim do lote — tudo com
     * SoundEvents vanilla no mesmo padrão das outras máquinas (v1.2.55).
     */
    private void efeitosDeProcesso(ServerLevel level) {
        float progresso = ticksTotal > 0
                ? 1F - ticksRestantes / (float) ticksTotal : 0F;
        switch (tipoMaquina()) {
            case MOENDA -> {
                // tranco de moinho a cada ~7s, acelerando o tom com o lote
                if ((ticksRestantes / 20) % 7 == 0) {
                    level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE,
                            SoundSource.BLOCKS, 0.45F, 0.75F + 0.35F * progresso);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            worldPosition.getX() + 0.5, worldPosition.getY() + 0.65,
                            worldPosition.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.1);
                }
            }
            case PRENSA -> {
                // o fuso forçando: tranco grave a cada ~8s, tenso no fim
                if ((ticksRestantes / 20) % 8 == 0) {
                    level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND,
                            SoundSource.BLOCKS, 0.4F, 0.6F + 0.2F * progresso);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                            worldPosition.getX() + 0.5, worldPosition.getY() + 0.6,
                            worldPosition.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.0);
                }
            }
            case CALDEIRAO -> {
                // fervura: chiado de fervura, relaxando o pitch (ferve mais)
                if ((ticksRestantes / 20) % 9 == 0) {
                    level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH,
                            SoundSource.BLOCKS, 0.3F, 1.4F - 0.4F * progresso);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                            worldPosition.getZ() + 0.5, 1, 0.25, 0.05, 0.25, 0.004);
                }
            }
        }
    }

    /** Estado: timer rodando (mostura, fervura, moagem ou prensagem). */
    public boolean processando() {
        return ticksRestantes > 0 && !esperandoSegunda;
    }

    /** Estado: produto pronto no slot de saída (a GUI mostra "recolher"). */
    public boolean servido() {
        return !itens.get(tipoMaquina().idxOut).isEmpty() && !producaoPendente;
    }

    // ==================================================== CONTAINER (slots reais)

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
            if (slot == tipoMaquina().idxOut
                    || (tipoMaquina().idxExtra >= 0 && slot == tipoMaquina().idxExtra)) {
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
            avancar(); // insumo novo: motor decide (lote, espera, hint)
        }
        if (tipoMaquina().idxSec >= 0 && slot == tipoMaquina().idxSec
                && esperandoSegunda && !stack.isEmpty()) {
            iniciarFervura(); // o lúpulo chegou: fervura começa sozinha
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
        return Component.translatable("block.intoxicantes." + tipoMaquina().id);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MenuMaquinaSNC(id, inv, this, tipoMaquina(), dadosGui());
    }

    /**
     * v1.2.69 FIX: o menu "maquina_snc" é um ExtendedMenuType — o fabric-menu-api
     * EXIGE que o provedor diga qual TipoMaquina abrir no client (o payload da
     * abertura). Sem isto, openMenu lançava "Extended menu intoxicantes:maquina_snc
     * must be opened with an ExtendedMenuProvider!" e a GUI nunca abria.
     */
    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return tipoMaquina().indice();
    }

    public ContainerData dadosGui() {
        return dados;
    }

    // ==================================================== SOLTE O CONTEÚDO

    /** Quebrou a máquina: devolve TUDO (insumo, 2ª dose, produto, extra). */
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
        esperandoSegunda = false;
        fervuraEmCurso = false;
        producaoPendente = false;
        pendenciaExtra = false;
        setChanged();
    }

    private void dropar(ServerLevel level, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, stack));
    }

    // ==================================================== SYNC DO RENDERER
    // (v1.2.55 preservada: rolos/parafuso/rodopio continuam lendo isto aqui)

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

    /** Progresso do lote 0..1 (o client usa no parafuso/barra). */
    public float progressoClient() {
        if (ticksTotal <= 0) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, 1F - ticksRestantes / (float) ticksTotal));
    }

    /** O TIPO da máquina (o renderer escolhe a animação: rolo/parafuso/rodopio). */
    public MaquinaPrimaBlock.Tipo tipoClient() {
        return tipo;
    }

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("lote");
        ContainerHelper.loadAllItems(raiz, itens);
        this.ticksRestantes = raiz.getIntOr("ticksRestantes", 0);
        this.ticksTotal = raiz.getIntOr("ticksTotal", 0);
        this.esperandoSegunda = raiz.getBooleanOr("esperandoSegunda", false);
        this.fervuraEmCurso = raiz.getBooleanOr("fervuraEmCurso", false);
        this.producaoPendente = raiz.getBooleanOr("producaoPendente", false);
        this.pendenciaExtra = raiz.getBooleanOr("pendenciaExtra", false);
        this.pendenciaExtra = raiz.getBooleanOr("pendenciaExtra", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput raiz = output.child("lote");
        ContainerHelper.saveAllItems(raiz, itens, true);
        raiz.putInt("ticksRestantes", ticksRestantes);
        raiz.putInt("ticksTotal", ticksTotal);
        raiz.putBoolean("esperandoSegunda", esperandoSegunda);
        raiz.putBoolean("fervuraEmCurso", fervuraEmCurso);
        raiz.putBoolean("producaoPendente", producaoPendente);
        raiz.putBoolean("pendenciaExtra", pendenciaExtra);
        raiz.putBoolean("pendenciaExtra", pendenciaExtra);
    }
}
