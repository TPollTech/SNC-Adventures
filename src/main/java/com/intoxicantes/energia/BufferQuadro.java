package com.intoxicantes.energia;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * v1.2.70 — O "MEDIDOR DE ENTRADA" DO QUADRO: buffer mínimo (10.000 E) que
 * amortece a medição da rede. MESMA semântica do SimpleEnergyStorage do SNC
 * Energies (E, insert/extract com simulate) — é um ponto de medição da
 * instalação, NÃO geração nem armazenamento concorrente (regra do documento:
 * geração e armazenamento pertencem ao SNC Energies).
 *
 * Classe própria (não importamos a deles por reflexão pra escrever — o
 * adapter só LÊ/EXTRAI de storages deles; o nosso buffer é nosso).
 */
public final class BufferQuadro {

    private final long capacidade;
    private long energia;

    public BufferQuadro(long capacidade) {
        this.capacidade = capacidade;
    }

    public long getEnergy() {
        return energia;
    }

    public long getCapacity() {
        return capacidade;
    }

    public boolean isFull() {
        return energia >= capacidade;
    }

    public boolean isEmpty() {
        return energia <= 0;
    }

    public long insert(long quantia, boolean simulate) {
        if (quantia <= 0) {
            return 0L;
        }
        long aceito = Math.min(quantia, capacidade - energia);
        if (!simulate && aceito > 0) {
            energia += aceito;
        }
        return aceito;
    }

    public long extract(long quantia, boolean simulate) {
        if (quantia <= 0) {
            return 0L;
        }
        long removido = Math.min(quantia, energia);
        if (!simulate && removido > 0) {
            energia -= removido;
        }
        return removido;
    }

    public static void save(BufferQuadro buffer, ValueOutput output) {
        output.putLong("Energy", buffer.energia);
        output.putLong("Capacity", buffer.capacidade);
    }

    public static void load(BufferQuadro buffer, ValueInput input) {
        buffer.energia = Math.clamp(input.getLongOr("Energy", 0L), 0L, buffer.capacidade);
    }
}
