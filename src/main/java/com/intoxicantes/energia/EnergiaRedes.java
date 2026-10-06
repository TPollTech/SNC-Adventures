package com.intoxicantes.energia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.intoxicantes.LedPotenciaBlockEntity;
import com.intoxicantes.SoqueteTetoBlock.SoqueteBlockEntity;
import com.intoxicantes.TomadaBlock.TomadaBlockEntity;
import com.intoxicantes.QuadroEletricoBlock.QuadroEletricoBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.intoxicantes.CabosNetworking;

/** Redes passivas, isoladas por dimensão; reconstrução somente por eventos. */
public final class EnergiaRedes {
    public static final long INTERVALO_REDE = 20L;
    public static final int LIMITE_TOPOLOGIA = 4096;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> quadros = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> rebuilds = new HashMap<>();
    private EnergiaRedes() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> limpar());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            for (var be : new ArrayList<>(chunk.getBlockEntities().values())) {
                // O evento acontece antes de o chunk ficar disponível no cache.
                // Consultar level.getBlockEntity/setBlock aqui espera pelo próprio
                // carregamento e trava o servidor. Trabalhar no chunk recebido.
                BlockPos pos = be.getBlockPos();
                var estado = chunk.getBlockState(pos);
                var atualizado = estado;
                if (be instanceof LedPotenciaBlockEntity
                        && estado.hasProperty(com.intoxicantes.LedPotenciaBlock.LIT))
                    atualizado = estado.setValue(com.intoxicantes.LedPotenciaBlock.LIT, false);
                else if (be instanceof SoqueteBlockEntity
                        && estado.hasProperty(com.intoxicantes.SoqueteTetoBlock.ACESA))
                    atualizado = estado.setValue(com.intoxicantes.SoqueteTetoBlock.ACESA, false);
                else if (be instanceof TomadaBlockEntity tomada) {
                    if (estado.hasProperty(com.intoxicantes.TomadaBlock.ENERGIZADA))
                        atualizado = estado.setValue(com.intoxicantes.TomadaBlock.ENERGIZADA, false);
                    tomada.limparCircuitoCarregado(chunk);
                } else if (be instanceof QuadroEletricoBlockEntity quadro) quadro.registrar();
                if (atualizado != estado) {
                    chunk.setBlockState(pos, atualizado, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    level.sendBlockUpdated(pos, estado, atualizado, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
            marcarRebuildProximo(level, chunk.getPos().getMiddleBlockPosition(0), 0);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            // Na parada não existem cargas ativas e consultar outros chunks
            // pode tentar recarregar o que já está sendo descarregado.
            if (!level.getServer().isRunning()) return;
            for (var be : chunk.getBlockEntities().values()) {
                if (be instanceof QuadroEletricoBlockEntity quadro) {
                    quadro.desligarSaidas();
                    esquecerQuadro(level, quadro.getBlockPos());
                }
            }
            // Qualquer caminho pode atravessar este chunk; cortar antes da nova travessia.
            for (BlockPos pos : new ArrayList<>(quadros.getOrDefault(level.dimension(), Set.of()))) {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro)
                    quadro.desligarSaidas();
            }
            marcarRebuildProximo(level, chunk.getPos().getMiddleBlockPosition(0), 0);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                boolean medir = level.getGameTime() % INTERVALO_REDE == 0L;
                if (medir) CabosSuspensos.get(level).validar(level);
                processarFila(level);
                if (medir) {
                    medirQuadros(level);
                    CabosNetworking.sincronizar(level);
                }
            }
        });
    }

    public static void marcarRebuild(ServerLevel level, BlockPos quadro) {
        if (quadro == null) return;
        BlockPos pos = quadro.immutable();
        quadros.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(pos);
        rebuilds.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(pos);
    }

    public static void esquecerQuadro(ServerLevel level, BlockPos quadro) {
        var conhecidos = quadros.get(level.dimension());
        if (conhecidos != null) conhecidos.remove(quadro);
        var pendentes = rebuilds.get(level.dimension());
        if (pendentes != null) pendentes.remove(quadro);
    }

    public static void processarFila(ServerLevel level) {
        Set<BlockPos> fila = rebuilds.get(level.dimension());
        if (fila == null || fila.isEmpty()) return;
        var pendentes = new ArrayList<>(fila);
        fila.clear();
        for (BlockPos pos : pendentes) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro)
                quadro.reconstruirTopologia();
        }
    }

    private static void medirQuadros(ServerLevel level) {
        for (BlockPos pos : new ArrayList<>(quadros.getOrDefault(level.dimension(), Set.of()))) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro) quadro.tickRede(level);
            else esquecerQuadro(level, pos);
        }
    }

    /** Invalida quadros conhecidos sem varrer cubos do mundo ou perder fios longos. */
    public static void marcarRebuildProximo(ServerLevel level, BlockPos pos, int raio) {
        rebuilds.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>())
                .addAll(quadros.getOrDefault(level.dimension(), Set.of()));
    }

    public static QuadroEletricoBlockEntity quadroDaRede(ServerLevel level, BlockPos ponto) {
        for (BlockPos pos : quadros.getOrDefault(level.dimension(), Set.of())) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro
                    && quadro.circuitoDoPonto(ponto) >= 0) return quadro;
        }
        return null;
    }

    /** Impede cobrança dupla e realimentação por um ponto ligado a dois quadros. */
    public static void validarExclusividade(ServerLevel level, QuadroEletricoBlockEntity origem,
            BlockPos ponto, int circuito) {
        for (BlockPos pos : quadros.getOrDefault(level.dimension(), Set.of())) {
            if (pos.equals(origem.getBlockPos()) || !level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity outro)) continue;
            int indice = outro.circuitoDoPonto(ponto);
            if (indice < 0) continue;
            origem.circuitos().get(circuito).conflitoLigacao = true;
            outro.circuitos().get(indice).conflitoLigacao = true;
            outro.desligarSaidas();
        }
    }

    public static QuadroEletricoBlockEntity quadroMaisProximo(ServerLevel level, BlockPos ponto, double raio) {
        QuadroEletricoBlockEntity melhor = null;
        double distancia = raio * raio;
        for (BlockPos pos : quadros.getOrDefault(level.dimension(), Set.of())) {
            if (!level.isLoaded(pos)) continue;
            if (pos.distSqr(ponto) <= distancia
                    && level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro) {
                distancia = pos.distSqr(ponto);
                melhor = quadro;
            }
        }
        return melhor;
    }

    public static void limpar() { quadros.clear(); rebuilds.clear(); }
}
