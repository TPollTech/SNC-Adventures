package com.intoxicantes;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jspecify.annotations.Nullable;

/**
 * O Traficante: vendedor ambulante da esquina.
 * Vende na boa, mas se agredir ele te caça (e dropa o estoque kkkk).
 * Roleplay de mundo de GTA: planta, destila, gasta.
 *
 * v1.2.44 — O PONTO: tela PROPRIA dele (nada de UI de vilarejo), com:
 *  - estoque diário de 7 produtos (preço do dia, mais barato que o Gago);
 *  - DESCONTO de fila: quanto mais gente no atendimento, mais barato;
 *  - DESTRANCO: largou a fila, perde o desconto (a rua cobra a espera);
 *  - FIADO: 3 níveis — SEM LANÇAMENTO (10% de juros, perde estoque), na
 *    confiança (30% de juros) e na palavra (zero juros, até 10 compras);
 *  - FIDELIDADE DA RUA: 5% de desconto pra sempre a cada 5 compras (teto 15%);
 *  - COMPRA de CRU DE DROGA na porta (v1.2.66: ele recolhe seda/ópio/cocaína
 *    na porta do ponto, pagando ~90% do preço do dia — colheita de bebida é
 *    com o Gago, droga é com o camelo);
 *  - DIAMANTE: o "lançamento" exclusivo do dia (1 unidade, metade off);
 *  - COMPRAS DO DIA contam pros dois descontos (nada é de graça kkkk).
 */
public class TraficanteEntity extends AbstractVillager {
    /** Falas de apresentacao quando spawna perto do player. */
    static final String[] FRASES_CHEGADA = {
            "entity.intoxicantes.traficante.chegada.1",
            "entity.intoxicantes.traficante.chegada.2",
            "entity.intoxicantes.traficante.chegada.3",
            "entity.intoxicantes.traficante.chegada.4"
    };

    private boolean anunciouChegada;

    // ==================================================== O PONTO (v1.2.44)

    /** Estoque restante por produto do dia (chave = id do TradeCatalog). */
    private final Map<String, Integer> estoqueDia = new HashMap<>();
    /** Exclusivo do dia: -1 = sem lançamento hoje. */
    private int exclusiveN = -1;
    /** Quem já usou o exclusive hoje (1 compra por dia por freguês). */
    private final java.util.Set<UUID> exclusiveFeito = new java.util.HashSet<>();
    /** Fiado em curso por freguês (compras em aberto pro nível de juros). */
    private final Map<UUID, Integer> fiadoAberto = new HashMap<>();
    /** Quantos fiados o fregues JA QUITOU (sobe o nível de confiança). */
    private final Map<UUID, Integer> fiadoQuitados = new HashMap<>();
    /** Compras hoje por freguês (desconto de fila + fidelidade da rua). */
    private final Map<UUID, Integer> comprasHoje = new HashMap<>();

    /** Preenche o estoque do dia (chamado no refresh diário). */
    private void sortearEstoqueDoDia(net.minecraft.util.RandomSource rng, long seedDia) {
        estoqueDia.clear();
        for (TradeCatalog.Entry e : TradeCatalog.traficanteFixo()) {
            estoqueDia.put(e.id(), e.stock());
        }
        // o EXCLUSIVO do dia: 40% de chance de lançamento (metade do preço)
        exclusiveN = rng.nextInt(10) < 4 ? rng.nextInt(TradeCatalog.EXCLUSIVOS.length) : -1;
        exclusiveFeito.clear();
    }

    /** Estoque restante de um produto (por id; diamante = exclusive). */
    public int estoqueDe(String id) {
        if ("diamante".equals(id)) {
            if (exclusiveN < 0) return 0;
            Integer feito = estoqueDia.get("exclusivo_dia");
            return feito == null ? 1 : feito;
        }
        return estoqueDia.getOrDefault(id, 0);
    }

    /** 1 compra a menos do estoque do dia. */
    private void debitar(String id, int qtd) {
        estoqueDia.put(id, Math.max(0, estoqueDe(id) - qtd));
    }

    /** Desconto de fila ATIVO agora (0/5/10% — soma com a fidelidade da rua). */
    public int getDescontoAtivo() {
        int fila = getTradingPlayer() == null ? 0
                : (level() instanceof ServerLevel s
                        ? s.players().size() - 1 : 0);
        return Math.min(10, fila * 5);
    }

    /** Compras do fregues hoje (conta pra fidelidade da rua). */
    public int getComprasHoje(ServerPlayer player) {
        return comprasHoje.getOrDefault(player.getUUID(), 0);
    }

    /** Desconto de FIDELIDADE da rua: 5% a cada 5 compras, teto 15%. */
    private int fidelidadeRua(ServerPlayer player) {
        return Math.min(15, (getComprasHoje(player) / 5) * 5);
    }

    /** Preço de HOJE do produto (com todos os descontos do freguês).
     *  v1.2.71: público — a tela do Ponto pinta o MESMO número que a cobrança. */
    public int precoDe(ServerPlayer player, TradeCatalog.Entry e) {
        long seedDia = DailyTradeStock.tradingDay(
                MarketSystem.tempoTotal(level().getServer().overworld()));
        int preco = TradeCatalog.precoDoDia(seedDia, e.item().getDescriptionId(), e.price());
        int desconto = Math.min(20, getDescontoAtivo() + fidelidadeRua(player));
        return Math.max(1, Math.round(preco * (1.0F - desconto / 100.0F)));
    }

    /** Fiado em aberto do freguês (compras pendentes). */
    public int getFiadoPlayer(ServerPlayer player) {
        return fiadoAberto.getOrDefault(player.getUUID(), 0);
    }

    /** Nível de confiança do fiado: 0=sem lançamento, 1=na confiança, 2=na palavra. */
    public int getFiadoNivel(ServerPlayer player) {
        return Math.min(2, fiadoQuitados.getOrDefault(player.getUUID(), 0) / 2);
    }

    /** Estado do EXCLUSIVO (qual produto, estoque, preço). */
    public int getExclusiveN() { return exclusiveN; }

    public int getExclusiveEstoque() { return estoqueDe("diamante"); }

    /** Preço do exclusivo: metade do preço do dia. */
    public int getExclusivePreco(ServerPlayer player) {
        if (exclusiveN < 0) return 0;
        TradeCatalog.Entry e = TradeCatalog.traficanteFixo().get(exclusiveN);
        return Math.max(1, precoDe(player, e) / 2);
    }

    /** COMPRAR: produto do catalogo do ponto (0..6), 8=exclusivo. Só DROGA aqui. */
    public boolean comprar(ServerPlayer player, int indice) {
        if (!player.isAlive() || player.level() != this.level()) return false;
        // ---- o EXCLUSIVO (8)
        if (indice == 8) {
            if (exclusiveN < 0 || estoqueDe("diamante") <= 0) return false;
            if (exclusiveFeito.contains(player.getUUID())) {
                player.sendSystemMessage(Component.translatable(
                        "commerce.intoxicantes.ponto.exclusive_feito"));
                return false;
            }
            TradeCatalog.Entry e = TradeCatalog.traficanteFixo().get(exclusiveN);
            int preco = getExclusivePreco(player);
            if (!PlayerMoney.subtrair(player, preco)) {
                avisaSemSaldo(player, preco);
                return false;
            }
            entregar(player, e.item(), e.count());
            debitar("diamante", 1);
            exclusiveFeito.add(player.getUUID());
            registrarCompra(player, preco);
            return true;
        }
        // ---- produto normal (0..6)
        if (indice < 0 || indice >= TradeCatalog.traficanteFixo().size()) return false;
        TradeCatalog.Entry e = TradeCatalog.traficanteFixo().get(indice);
        if (estoqueDe(e.id()) <= 0) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.esgotado"));
            return false;
        }
        int preco = precoDe(player, e);
        if (PlayerMoney.get(player) >= preco) {
            if (!PlayerMoney.subtrair(player, preco)) return false;
            entregar(player, e.item(), e.count());
            debitar(e.id(), 1);
        } else if (getFiadoNivel(player) > 0) {
            // FIADO: nivel 1 = 30% de juros, nivel 2 = 0%
            int juros = getFiadoNivel(player) >= 2 ? 0 : 30;
            int total = preco + (preco * juros) / 100;
            PlayerMoney.addDivida(player, total);
            fiadoAberto.merge(player.getUUID(), 1, Integer::sum);
            entregar(player, e.item(), e.count());
            debitar(e.id(), 1);
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.fiado", total, PlayerMoney.getDivida(player)));
        } else {
            // SEM LANÇAMENTO: 10% de juros E consome estoque (a rua não é caridade)
            int total = preco + (preco * 10) / 100;
            PlayerMoney.addDivida(player, total);
            entregar(player, e.item(), e.count());
            debitar(e.id(), 1);
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.fiado_semlancamento", total,
                    PlayerMoney.getDivida(player)));
        }
        registrarCompra(player, preco);
        return true;
    }

    /** Pagar a divida inteira (o que tiver em carteira, na verdade). */
    public boolean pagarDivida(ServerPlayer player) {
        int devendo = PlayerMoney.getDivida(player);
        if (devendo <= 0) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.sem_divida"));
            return false;
        }
        int pago = PlayerMoney.pagarDivida(player, PlayerMoney.get(player));
        if (pago <= 0) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.sem_dinheiro"));
            return false;
        }
        if (PlayerMoney.getDivida(player) == 0) {
            fiadoAberto.remove(player.getUUID());
            int quitados = fiadoQuitados.merge(player.getUUID(), 1, Integer::sum);
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.divida_quitada", pago, quitados * 2));
            this.playSound(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.4F);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.divida_parcial", pago,
                    PlayerMoney.getDivida(player)));
        }
        return true;
    }

    /** EMPRESTAR (fiado direto, sem compra): R$ 50 no bolso, no nível atual. */
    public boolean emprestarFiado(ServerPlayer player) {
        if (PlayerMoney.getDivida(player) >= 200) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.fiado_cheio"));
            return false;
        }
        int juros = getFiadoNivel(player) >= 2 ? 0 : getFiadoNivel(player) == 1 ? 30 : 10;
        int total = 50 + (50 * juros) / 100;
        PlayerMoney.add(player, 50);
        PlayerMoney.addDivida(player, total);
        player.sendSystemMessage(Component.translatable(
                "commerce.intoxicantes.ponto.emprestou", 50, total,
                PlayerMoney.getDivida(player)));
        return true;
    }

    /**
     * v1.2.66 — COMPRA de CRU DE DROGA na porta do ponto (paga na hora).
     * O espelho da aba de venda do Gago: o player planta/colhe droga e vende
     * pro camelo; insumo de bebida é com o Gago do Esquinão.
     */
    public boolean venderCruDroga(ServerPlayer player, int indice) {
        var crus = TradeCatalog.cruDrogas();
        if (indice < 0 || indice >= crus.size()) return false;
        TradeCatalog.Entry e = crus.get(indice);
        int tenho = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(e.item())) tenho += st.getCount();
        }
        if (tenho < e.count()) return false;
        int preco = precoCruDe(player, e);
        int restante = e.count();
        for (int i = 0; i < player.getInventory().getContainerSize() && restante > 0; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.is(e.item())) continue;
            int tira = Math.min(st.getCount(), restante);
            st.shrink(tira);
            restante -= tira;
        }
        PlayerMoney.add(player, preco);
        player.sendSystemMessage(Component.translatable(
                "commerce.intoxicantes.ponto.comprou_cru", preco));
        return true;
    }

    /**
     * Preço de HOJE que a rua paga pelo cru (90% da cotação do dia).
     * Mesmo cálculo na tela e na cobrança — o que o fregues vê é o que entra.
     */
    public int precoCruDe(ServerPlayer player, TradeCatalog.Entry e) {
        long seedDia = DailyTradeStock.tradingDay(
                MarketSystem.tempoTotal(level().getServer().overworld()));
        return Math.max(1, Math.round(TradeCatalog.precoDoDia(
                seedDia, e.item().getDescriptionId(), e.price()) * 0.9F)); // a rua paga 90%
    }

    /** Saiu da fila (botão DESTRAVADO): perde desconto e a vaga. */
    public void destrancar(ServerPlayer player) {
        comprasHoje.merge(player.getUUID(), 0, Integer::sum); // mantém histórico
        player.sendSystemMessage(Component.translatable(
                "commerce.intoxicantes.ponto.destrancado"));
    }

    private void registrarCompra(ServerPlayer player, int precoPago) {
        comprasHoje.merge(player.getUUID(), 1, Integer::sum);
        this.playSound(IntoxicantesMod.CAIXA_REGISTRADORA, 0.8F, 1.1F);
        if (level() instanceof ServerLevel lvl) {
            lvl.sendParticles(Particulas.DINHEIRO,
                    this.getX(), this.getY() + 1.9, this.getZ(), 8, 0.3, 0.4, 0.3, 0.03);
        }
        // milestone da fidelidade da rua
        int compras = getComprasHoje(player);
        if (compras % 5 == 0) {
            player.sendSystemMessage(Component.translatable(
                    "commerce.intoxicantes.ponto.fidelidade", compras, fidelidadeRua(player) + 5));
        }
    }

    private void entregar(ServerPlayer player, net.minecraft.world.item.Item item, int qtd) {
        ItemStack stack = new ItemStack(item, qtd);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.PREDICTED);
        }
        player.getInventory().setChanged();
    }

    private void avisaSemSaldo(ServerPlayer player, int preco) {
        player.sendSystemMessage(Component.translatable(
                "money.intoxicantes.insuficiente", PlayerMoney.get(player)));
    }

    /** Larga o atendimento (fecha a fila dele). */
    public void largarAtendimento() {
        this.setTradingPlayer(null);
        if (!this.level().isClientSide()) {
            this.setNoAi(false);
        }
    }

    // ==================================================== ciclo vanilla

    public TraficanteEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    private final DailyTradeStock tradeStock = new DailyTradeStock();

    void refreshTradeStock() {
        if (this.level() instanceof ServerLevel server) {
            long ticks = MarketSystem.tempoTotal(server.getServer().overworld());
            long seedDia = DailyTradeStock.tradingDay(ticks);
            this.offers = tradeStock.refresh(this.offers, ticks, this.isTrading(),
                    () -> {
                        MerchantOffers ofertas = TradeCatalog.traficante(this.random, seedDia);
                        sortearEstoqueDoDia(this.random, seedDia);
                        return ofertas;
                    });
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % 20 == 0) refreshTradeStock();
        // EM ATENDIMENTO: estatueta (o nego nao sai andando no meio da negociação kkkk)
        if (!this.level().isClientSide() && this.getTradingPlayer() != null) {
            this.setNoAi(true);
            this.getNavigation().stop();
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    /** Fechou a tela: volta a andar (IA de novo). */
    @Override
    protected void stopTrading() {
        super.stopTrading();
        if (!this.level().isClientSide()) {
            this.setNoAi(false);
        }
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand mao) {
        // v1.2.44: botao direito abre o PONTO (tela propria, payload + sessao)
        ItemStack naMao = player.getItemInHand(mao);
        if (!naMao.is(Items.VILLAGER_SPAWN_EGG) && !naMao.is(IntoxicantesMod.OVO_TRAFICANTE)
                && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (mao == net.minecraft.world.InteractionHand.MAIN_HAND) {
                player.awardStat(net.minecraft.stats.Stats.TALKED_TO_VILLAGER);
            }
            if (!this.level().isClientSide() && player instanceof ServerPlayer sp) {
                refreshTradeStock();
                EsquinaoNetworking.ingressarTraf(sp, this);
                sp.sendSystemMessage(Component.translatable("commerce.intoxicantes.traficante.hint"));
                return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, mao);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData resultado = super.finalizeSpawn(accessor, difficulty, reason, data);
        refreshTradeStock();
        // v1.2.43: o .38 na mao direita — o corpo de player agora tem braco
        // animado (TraficanteRenderer.getArmPose) e o ItemInHandLayer desenha
        // a arma nela. Drop chance 0: a arma e dele, nao entra no loot (kkkk).
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(IntoxicantesMod.REVOLVER));
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        // invocado por ovo/comando: fica pra sempre (nao some longe)
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
            this.setPersistenceRequired();
        }
        return resultado;
    }

    /** Nao reproduz: o nego e solo kkkk */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
        return null;
    }

    /** Traficante e tipo andarilho: some se ninguem tiver perto (os invocados ficam). */
    @Override
    public boolean removeWhenFarAway(double distancia) {
        return distancia > 64.0 && !this.isPersistenceRequired() && !this.hasCustomName();
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        refreshTradeStock();
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        this.overrideXp(this.getVillagerXp() + offer.getXp());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.tradeStock.load(input);
        this.exclusiveN = input.getIntOr("ExclusiveDia", -1);
        // fiado/compras: mapas UUID->int serializados como JSON (persistem
        // junto da entidade; a API de mapa do ValueInput nao existe na 26.3)
        carregarMapa(input.getStringOr("FiadoAberto", ""), fiadoAberto);
        carregarMapa(input.getStringOr("FiadoQuitados", ""), fiadoQuitados);
        carregarMapa(input.getStringOr("ComprasHoje", ""), comprasHoje);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.tradeStock.save(output);
        output.putInt("ExclusiveDia", this.exclusiveN);
        salvarMapa(output, "FiadoAberto", fiadoAberto);
        salvarMapa(output, "FiadoQuitados", fiadoQuitados);
        salvarMapa(output, "ComprasHoje", comprasHoje);
    }

    /** Chamado pelo spawner do mod: avisa TODO MUNDO no chat (pura criacao de rumores kkkk). */
    public void anunciarChegada(ServerLevel level) {
        if (this.anunciouChegada) {
            return;
        }
        this.anunciouChegada = true;
        for (var p : level.players()) {
            p.sendSystemMessage(Component.translatable(
                    FRASES_CHEGADA[this.random.nextInt(FRASES_CHEGADA.length)],
                    this.getName(), p.getName()));
        }
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + 1.8, this.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            if (source.getEntity() instanceof Player matador) {
                matador.sendSystemMessage(Component.translatable(
                        this.random.nextBoolean()
                                ? "entity.intoxicantes.traficante.morte.1"
                                : "entity.intoxicantes.traficante.morte.2",
                        this.getName(), matador.getName()));
            }
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // "o cara deixou cair o estoque" kkkk
        this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.BASEADO));
        if (this.random.nextInt(3) == 0) {
            this.spawnAtLocation(level, new ItemStack(IntoxicantesMod.COCAINA));
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    // ==================================================== persistencia dos mapas (v1.2.44)

    private static final com.google.gson.Gson GSON_MAPA = new com.google.gson.Gson();

    /** Serializa UUID->int como JSON compacto ("uuid":valor,...). */
    private static void salvarMapa(net.minecraft.world.level.storage.ValueOutput output,
            String chave, Map<UUID, Integer> mapa) {
        if (mapa.isEmpty()) return;
        StringBuilder json = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<UUID, Integer> e : mapa.entrySet()) {
            if (!primeiro) json.append(',');
            json.append('"').append(e.getKey()).append("\" :").append(e.getValue());
            primeiro = false;
        }
        json.append('}');
        output.putString(chave, json.toString());
    }

    /** Rehidrata o JSON do salvarMapa (entrada corrompida = mapa vazio). */
    private static void carregarMapa(String json, Map<UUID, Integer> destino) {
        destino.clear();
        if (json == null || json.isBlank()) return;
        try {
            java.lang.reflect.Type tipo =
                    new com.google.gson.reflect.TypeToken<HashMap<UUID, Integer>>() {}.getType();
            HashMap<UUID, Integer> lido = GSON_MAPA.fromJson(json, tipo);
            if (lido != null) destino.putAll(lido);
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Caderninho corrompido ignorado: {}", e.getMessage());
        }
    }
}
