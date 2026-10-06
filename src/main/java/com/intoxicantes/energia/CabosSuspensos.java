package com.intoxicantes.energia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.intoxicantes.ConectorEletricoBlock;

/**
 * v1.2.80 — OS CABOS SUSPENSOS DA DIMENSÃO: a lista de elos
 * (energia/EloCabo) que atravessa os vãos da instalação elétrica.
 *
 * Por que SavedData e não blocos: o cabo do Immersive Engineering cruza o ar
 * e o vão inteiro custa UM elo. Um bloco por trecho voltaria a ser "só blocos
 * adjacentes" e ainda custaria chunk carregado. Aqui o elo é dado puro —
 * persiste com a dimensão, some quando o conector some.
 *
 * discipline igual à da rede: NADA de varrer o mundo. As operações são
 * indexadas por conector e o índice é invalidado só quando a lista muda.
 */
public final class CabosSuspensos extends SavedData {

    /** Alcance máximo do vão entre dois conectores (passos de bloco). */
    public static final int ALCANCE_MAX = 32;

    public static final Codec<CabosSuspensos> CODEC = RecordCodecBuilder.create(instancia ->
            instancia.group(EloCabo.LIST_CODEC.fieldOf("elos").forGetter(CabosSuspensos::elos))
                    .apply(instancia, CabosSuspensos::com));

    public static final SavedDataType<CabosSuspensos> TIPO = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("intoxicantes", "cabos_suspensos"),
            CabosSuspensos::new, CODEC, DataFixTypes.LEVEL);

    private final List<EloCabo> elos = new ArrayList<>();
    /** Índice conector → elos que saem dele (reconstruído só na mudança). */
    private Map<BlockPos, List<EloCabo>> indice;

    public CabosSuspensos() {
    }

    public CabosSuspensos(List<EloCabo> elos) {
        this.elos.addAll(elos);
    }

    private static CabosSuspensos com(List<EloCabo> elos) {
        return new CabosSuspensos(elos);
    }

    /** Os elos da dimensão (visão somente leitura). */
    public static CabosSuspensos get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TIPO);
    }

    public List<EloCabo> elos() {
        return Collections.unmodifiableList(elos);
    }

    public int total() {
        return elos.size();
    }

    /** Os elos que saem de um conector (a ponta local do cabo). */
    public List<EloCabo> elosEm(BlockPos conector) {
        if (indice == null) {
            indice = new HashMap<>();
            for (EloCabo elo : elos) {
                indice.computeIfAbsent(elo.a(), p -> new ArrayList<>()).add(elo);
                indice.computeIfAbsent(elo.b(), p -> new ArrayList<>()).add(elo);
            }
        }
        return indice.getOrDefault(conector, List.of());
    }

    /** Cria o elo; devolve false se já existe cabo entre esses conectores. */
    public boolean adicionar(EloCabo elo) {
        if (contem(elo)) return false;
        elos.add(elo);
        indice = null;
        setDirty();
        return true;
    }

    private boolean contem(EloCabo elo) {
        for (EloCabo outro : elos) {
            if (outro.mesmoPar(elo)) return true;
        }
        return false;
    }

    public boolean contemPar(BlockPos a, BlockPos b) {
        for (EloCabo elo : elos) {
            if ((elo.a().equals(a) && elo.b().equals(b))
                    || (elo.a().equals(b) && elo.b().equals(a))) return true;
        }
        return false;
    }

    /**
     * Recolhe o elo que sai de um conector (a bobina volta para a mão).
     * Devolve o elo cortado ou null.
     */
    public EloCabo removerEm(BlockPos conector) {
        for (int i = 0; i < elos.size(); i++) {
            if (elos.get(i).toca(conector)) {
                EloCabo elo = elos.remove(i);
                indice = null;
                setDirty();
                return elo;
            }
        }
        return null;
    }

    /** Corta tudo que está preso a um conector (o terminal caiu). */
    public List<EloCabo> removerTodosEm(BlockPos conector) {
        List<EloCabo> removidos = new ArrayList<>();
        for (int i = elos.size() - 1; i >= 0; i--) {
            if (elos.get(i).toca(conector)) {
                removidos.add(elos.remove(i));
            }
        }
        if (!removidos.isEmpty()) {
            indice = null;
            setDirty();
        }
        return removidos;
    }

    /** Os elos visíveis de um jogador (o cliente desenha só estes). */
    public List<EloCabo> elosVisiveis(BlockPos centro, double raio) {
        List<EloCabo> visiveis = new ArrayList<>();
        double limite = raio * raio;
        for (EloCabo elo : elos) {
            if (elo.a().distSqr(centro) <= limite || elo.b().distSqr(centro) <= limite) {
                visiveis.add(elo);
            }
        }
        return visiveis;
    }

    /**
     * Descarta o elo cujo conector caiu (terminal quebrado, bloco de suporte
     * removido). Devolve quantos foram limpos — a rede reconstrói depois.
     */
    public int validar(ServerLevel level) {
        int removidos = 0;
        for (int i = elos.size() - 1; i >= 0; i--) {
            EloCabo elo = elos.get(i);
            if (!conectorVivo(level, elo.a(), elo.faceA())
                    || !conectorVivo(level, elo.b(), elo.faceB())) {
                elos.remove(i);
                removidos++;
            }
        }
        if (removidos > 0) {
            indice = null;
            setDirty();
            EnergiaRedes.marcarRebuildProximo(level, null, 0);
        }
        return removidos;
    }

    /** O conector existe e aponta para a face com que o elo foi feito? */
    public static boolean conectorVivo(ServerLevel level, BlockPos pos, net.minecraft.core.Direction face) {
        if (!level.isLoaded(pos)) return true;   // chunk fora: não é motivo para cortar
        if (!(level.getBlockState(pos).getBlock() instanceof ConectorEletricoBlock)) return false;
        return level.getBlockState(pos)
                .getValue(ConectorEletricoBlock.FACING) == face;
    }
}