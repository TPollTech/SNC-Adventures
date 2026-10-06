package com.intoxicantes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Cada apoio do teto mantém seu próprio cacho, colheita e recuperação. */
public final class ParreiraBlockEntity extends BlockEntity {
    /** Tempo mínimo de rebrota após cada colheita, em ticks efetivamente cultivados. */
    public static final int TEMPO_REBROTA = 60 * 20;
    public static final int COLHEITA_NORMAL = 1;
    public static final int COLHEITA_UV = 1;
    public static final int INTERVALO_SINCRONIZACAO = 20;

    private int etapaVisual;
    private int ticksRebrota;
    private int ticksManutencao;
    private boolean inicializouCachos;
    private int frutosAntesDaRemocao = -1;
    // Posições podadas continuam salvas: recolocar uma cerca não cria frutos de graça.
    private final Map<BlockPos, EstadoCacho> cachos = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> primeiroPassoRebrota = new HashMap<>();
    // Hitboxes não são inventários e não são salvas; o NBT desta raiz é a autoridade.
    private final Map<BlockPos, CachoParreiraEntity> hitboxes = new HashMap<>();

    public record EstadoCacho(int ticksRebrota, int maturacao) {
        public EstadoCacho {
            ticksRebrota = Math.clamp(ticksRebrota, 0, TEMPO_REBROTA);
            maturacao = Math.clamp(maturacao, 0, 3);
        }

        public boolean pronto() { return ticksRebrota == 0; }
    }

    public ParreiraBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.PARREIRA_ENTITY, pos, state);
        etapaVisual = state.getValue(UvCropBlock.AGE);
        // Replantar não contorna o intervalo com farinha de osso. Plantas antigas
        // já maduras mantêm sua primeira colheita, mesmo sem um NBT de parreira.
        ticksRebrota = etapaVisual < 4 ? TEMPO_REBROTA : 0;
    }

    public ParreiraEstrutura.Estrutura lerEstrutura() {
        return level == null ? ParreiraEstrutura.vazia(worldPosition)
                : ParreiraEstrutura.buscar(level, worldPosition);
    }

    /** Preserva tronco e folhas completos enquanto os frutos voltam a crescer. */
    public int etapaVisual() {
        return Math.max(etapaVisual, getBlockState().getValue(UvCropBlock.AGE));
    }

    public int maturacaoClient() {
        return getBlockState().getValue(UvCropBlock.UV_AGE);
    }

    public boolean frutosProntos() {
        return getBlockState().getValue(UvCropBlock.AGE) == 4
                && (inicializouCachos ? frutosRestantes() > 0 : ticksRebrota == 0);
    }

    public int ticksRebrotaRestantes() {
        return ticksRebrota;
    }

    /** Snapshot imutável usado pelo renderer; não altera o mundo do cliente. */
    public Map<BlockPos, EstadoCacho> cachosClient() {
        return Map.copyOf(cachos);
    }

    public EstadoCacho estadoCacho(BlockPos apoio) {
        return cachos.get(apoio);
    }

    public boolean temCachosIndividuais() {
        return inicializouCachos;
    }

    private Set<BlockPos> apoiosProdutivos(ParreiraEstrutura.Estrutura estrutura) {
        if (level == null || !estrutura.produtiva() || etapaVisual() < 4) return Set.of();
        var donos = ParreiraEstrutura.donosDosApoios(level, estrutura);
        return Set.copyOf(estrutura.cobertura().stream()
                .filter(apoio -> worldPosition.equals(donos.get(apoio))).toList());
    }

    private boolean descobrirCachos(Set<BlockPos> apoios) {
        boolean mudou = false;
        // O primeiro conjunto herda o timer antigo. Depois disso, toda expansão
        // precisa crescer por um intervalo completo, mesmo numa raiz madura.
        for (BlockPos apoio : apoios) {
            if (!cachos.containsKey(apoio)) {
                cachos.put(apoio.immutable(), new EstadoCacho(inicializouCachos ? TEMPO_REBROTA : ticksRebrota,
                        inicializouCachos ? 0 : maturacaoClient()));
                if (inicializouCachos) primeiroPassoRebrota.put(apoio.immutable(), 0);
                mudou = true;
            }
        }
        if (!apoios.isEmpty() && !inicializouCachos) {
            inicializouCachos = true;
            mudou = true;
        }
        return mudou;
    }

    /** Inclui propriedade, cobertura atual e idade: um slot salvo não basta. */
    public boolean cachoPronto(BlockPos apoio) {
        if (level == null || isRemoved() || !level.hasChunkAt(worldPosition)
                || !level.hasChunkAt(apoio) || getBlockState().getValue(UvCropBlock.AGE) != 4) return false;
        EstadoCacho estado = cachos.get(apoio);
        return estado != null && estado.pronto() && lerEstrutura().cobertura().contains(apoio)
                && worldPosition.equals(ParreiraEstrutura.donoDoApoio(level, apoio));
    }

    public int frutosRestantes() {
        return (int) apoiosProdutivos(lerEstrutura()).stream().filter(apoio -> {
            var estado = cachos.get(apoio);
            return getBlockState().getValue(UvCropBlock.AGE) == 4 && estado != null && estado.pronto();
        }).count();
    }

    public void prepararRemocao() {
        // ServerPlayerGameMode remove o bloco antes de calcular o loot. Capturar
        // agora considera a poda e a dona reais, enquanto a raiz ainda existe.
        frutosAntesDaRemocao = frutosRestantes();
    }

    public int frutosParaLoot() {
        return isRemoved() && frutosAntesDaRemocao >= 0 ? frutosAntesDaRemocao : frutosRestantes();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ParreiraBlockEntity be) {
        if (!(level instanceof ServerLevel) || be.isRemoved()
                || !(state.getBlock() instanceof UvaParreiraBlock)) {
            return;
        }
        be.ticksManutencao++;
        boolean sincroniza = false;
        int etapa = state.getValue(UvCropBlock.AGE);
        if (etapa > be.etapaVisual) {
            be.etapaVisual = etapa;
            sincroniza = true;
        }
        if (be.ticksManutencao % INTERVALO_SINCRONIZACAO == 0) {
            var estrutura = be.lerEstrutura();
            Set<BlockPos> apoios = be.apoiosProdutivos(estrutura);
            sincroniza |= be.descobrirCachos(apoios);
            // Timers pausam sem apoio/luz. Só cachos ainda ligados à raiz crescem.
            if (estrutura.produtiva() && level.getRawBrightness(estrutura.topo().above(), 0) >= 9) {
                if (be.ticksRebrota > 0) {
                    be.ticksRebrota = Math.max(0, be.ticksRebrota - INTERVALO_SINCRONIZACAO);
                    sincroniza |= be.ticksRebrota == 0;
                    be.setChanged();
                }
                for (BlockPos apoio : apoios) {
                    EstadoCacho cacho = be.cachos.get(apoio);
                    if (cacho != null && cacho.ticksRebrota() > 0
                            && level.getRawBrightness(apoio.above(), 0) >= 9) {
                        Integer primeiroPasso = be.primeiroPassoRebrota.remove(apoio);
                        int passo = primeiroPasso == null ? INTERVALO_SINCRONIZACAO : primeiroPasso;
                        int restante = Math.max(0, cacho.ticksRebrota() - passo);
                        be.cachos.put(apoio, new EstadoCacho(restante, cacho.maturacao()));
                        sincroniza |= restante == 0;
                        be.setChanged();
                    }
                }
            }
            be.manterHitboxes((ServerLevel) level, apoios);
        }
        if (sincroniza) {
            be.setChanged();
            be.sincronizar();
        }
    }

    /** UV muda a aparência e concede a conquista; cada cacho continua valendo uma uva. */
    public void amadurecerCachos(ServerLevel servidor, RandomSource random) {
        boolean mudou = false;
        for (BlockPos apoio : apoiosProdutivos(lerEstrutura())) {
            EstadoCacho estado = cachos.get(apoio);
            if (estado == null || !estado.pronto() || estado.maturacao() >= 3
                    || !IntoxicantesMod.isUvLit(servidor, apoio.above())
                    || random.nextFloat() >= ModConfig.get().uvChanceMaturacao) continue;
            cachos.put(apoio, new EstadoCacho(0, estado.maturacao() + 1));
            mudou = true;
        }
        if (mudou) {
            setChanged();
            sincronizar();
        }
    }

    private void manterHitboxes(ServerLevel servidor, Set<BlockPos> apoios) {
        hitboxes.entrySet().removeIf(entrada -> {
            var entity = entrada.getValue();
            var cacho = cachos.get(entrada.getKey());
            if (entity.isRemoved() || !apoios.contains(entrada.getKey()) || cacho == null || !cacho.pronto()
                    || getBlockState().getValue(UvCropBlock.AGE) != 4) {
                if (!entity.isRemoved()) entity.discard();
                return true;
            }
            return false;
        });
        for (BlockPos apoio : apoios) {
            var cacho = cachos.get(apoio);
            if (cacho == null || !cacho.pronto() || getBlockState().getValue(UvCropBlock.AGE) != 4
                    || hitboxes.containsKey(apoio)) continue;
            var existentes = servidor.getEntitiesOfClass(CachoParreiraEntity.class, new AABB(apoio),
                    entity -> !entity.isRemoved() && worldPosition.equals(entity.raiz())
                            && apoio.equals(entity.apoio()));
            CachoParreiraEntity entity;
            if (!existentes.isEmpty()) {
                entity = existentes.getFirst();
                existentes.stream().skip(1).forEach(CachoParreiraEntity::discard);
            } else {
                entity = new CachoParreiraEntity(IntoxicantesMod.CACHO_PARREIRA, servidor);
                entity.configurar(worldPosition, apoio);
                if (!servidor.addFreshEntity(entity)) continue;
            }
            hitboxes.put(apoio.immutable(), entity);
        }
    }

    /**
     * Única entrega autoritativa por apoio, usada pela hitbox e pela cerca exata.
     * O slot é zerado ANTES da entrega: um segundo pacote não colhe de novo.
     */
    public boolean colher(ServerLevel servidor, Player player, BlockPos alvo) {
        if (level != servidor || isRemoved() || player.isSpectator()
                || !UvaParreiraBlock.maoDeColheita(player)
                || !player.isWithinBlockInteractionRange(alvo, 0.0)
                || !servidor.mayInteract(player, alvo)
                || !servidor.mayInteract(player, worldPosition)
                || !servidor.hasChunkAt(worldPosition)
                || servidor.getBlockEntity(worldPosition) != this) {
            return false;
        }
        BlockState state = servidor.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof UvaParreiraBlock)
                || state.getValue(UvCropBlock.AGE) != 4) {
            return false;
        }
        var estrutura = lerEstrutura();
        if (!estrutura.produtiva() || !estrutura.cobertura().contains(alvo)
                || !worldPosition.equals(ParreiraEstrutura.donoDoApoio(servidor, alvo))) {
            return false;
        }
        if (descobrirCachos(apoiosProdutivos(estrutura))) {
            setChanged();
            sincronizar();
        }
        EstadoCacho cacho = cachos.get(alvo);
        if (cacho == null || !cacho.pronto()) return false;
        int maturacao = cacho.maturacao();
        etapaVisual = 4;
        cachos.put(alvo.immutable(), new EstadoCacho(TEMPO_REBROTA, 0));
        // O primeiro passo usa somente os ticks decorridos desde ESTE clique;
        // a fase dos outros cachos continua intacta, mesmo em colheitas rápidas.
        primeiroPassoRebrota.put(alvo.immutable(), INTERVALO_SINCRONIZACAO
                - ticksManutencao % INTERVALO_SINCRONIZACAO);
        CachoParreiraEntity entity = hitboxes.remove(alvo);
        if (entity != null && !entity.isRemoved()) entity.discard();
        setChanged();
        sincronizar();
        ItemStack frutos = new ItemStack(IntoxicantesMod.UVA, 1);
        player.getInventory().add(frutos);
        if (!frutos.isEmpty()) {
            Block.popResource(servidor, alvo, frutos);
        }
        if (maturacao == 3 && player instanceof ServerPlayer sp) {
            Progressoes.conceder(sp, Progressoes.COLHEITA_PERFEITA);
        }
        servidor.playSound(null, alvo, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 0.65F, 0.9F + servidor.getRandom().nextFloat() * 0.2F);
        return true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            hitboxes.values().forEach(entity -> { if (!entity.isRemoved()) entity.discard(); });
            hitboxes.clear();
        }
    }

    private void sincronizar() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput dados = input.childOrEmpty("parreira");
        etapaVisual = Math.clamp(dados.getIntOr("etapa_visual", getBlockState().getValue(UvCropBlock.AGE)), 0, 4);
        int inicial = getBlockState().getValue(UvCropBlock.AGE) < 4 ? TEMPO_REBROTA : 0;
        ticksRebrota = Math.clamp(dados.getIntOr("rebrota", inicial), 0, TEMPO_REBROTA);
        inicializouCachos = dados.getBooleanOr("cachos_individuais", false);
        cachos.clear();
        primeiroPassoRebrota.clear();
        for (ValueInput entrada : dados.childrenListOrEmpty("cachos")) {
            var pos = entrada.read("apoio", BlockPos.CODEC).orElse(null);
            // Toda cobertura possível cabe neste limite fixo; NBT adulterado
            // não cria slots arbitrários nem força consultas a chunks distantes.
            if (pos == null || Math.abs((long) pos.getX() - worldPosition.getX()) > ParreiraEstrutura.RAIO_BUSCA_RAIZ
                    || Math.abs((long) pos.getZ() - worldPosition.getZ()) > ParreiraEstrutura.RAIO_BUSCA_RAIZ
                    || pos.getY() < worldPosition.getY()
                    || pos.getY() >= worldPosition.getY() + ParreiraEstrutura.ALTURA_MAXIMA) continue;
            cachos.putIfAbsent(pos.immutable(), new EstadoCacho(
                    entrada.getIntOr("rebrota", TEMPO_REBROTA), entrada.getIntOr("uv", 0)));
            entrada.getInt("primeiro_passo").ifPresent(passo -> primeiroPassoRebrota.put(pos.immutable(),
                    Math.clamp(passo, 0, INTERVALO_SINCRONIZACAO)));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput dados = output.child("parreira");
        dados.putInt("etapa_visual", etapaVisual());
        dados.putInt("rebrota", ticksRebrota);
        dados.putBoolean("cachos_individuais", inicializouCachos);
        var lista = dados.childrenList("cachos");
        for (var entrada : cachos.entrySet()) {
            var cacho = lista.addChild();
            cacho.store("apoio", BlockPos.CODEC, entrada.getKey());
            cacho.putInt("rebrota", entrada.getValue().ticksRebrota());
            cacho.putInt("uv", entrada.getValue().maturacao());
            Integer primeiroPasso = primeiroPassoRebrota.get(entrada.getKey());
            if (primeiroPasso != null) cacho.putInt("primeiro_passo", primeiroPasso);
        }
    }
}
