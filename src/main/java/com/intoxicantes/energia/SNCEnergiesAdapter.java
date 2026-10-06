package com.intoxicantes.energia;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import com.intoxicantes.IntoxicantesMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v1.2.70 — O PONTO ÚNICO DE CONTATO COM O SNC ENERGIES (contrato do
 * PROGRESSAO.md deles: integração OPCIONAL, sem referência direta às classes
 * opcionais, ponte por reflexão — o mesmo padrão do BeverageMotor deles, que
 * consome o nosso ProcessosBebida por reflexão sobre métodos públicos).
 *
 * Toda a eletricidade do SNC Adventures (quadro, cabos, interruptores,
 * soquetes, lâmpadas) chama SÓ este adapter. Nenhuma outra classe importa
 * (por nome) qualquer coisa do snc_energies — e nem aqui há import estático:
 * os MethodHandles são resolvidos por nome quando o mod está presente e
 * ficam em cache. Sem o mod: disponivel() == false e toda chamada devolve
 * 0/false — a instalação elétrica fica "sem energia na entrada", sem crash.
 *
 * API real deles (auditada na fonte 0.8.0):
 *   interface EnergyStorage { long getEnergy(); long getCapacity();
 *       long insert(long,boolean simulate); long extract(long,boolean); }
 *   interface EnergyProvider { EnergyStorage getEnergyStorage(Direction); }
 * Unidade: E (SNC energy units), taxas em E/tick. Fogão a lenha = 80 E/t.
 */
public final class SNCEnergiesAdapter {

    private static volatile boolean resolvido;
    private static boolean presente;

    // com.snc.energies.blockentity.EnergyProvider#getEnergyStorage(Direction)
    private static MethodHandle mhGetEnergyStorage;
    // com.snc.energies.energy.EnergyStorage#getEnergy() / extract(long,boolean)
    private static MethodHandle mhGetEnergy;
    private static MethodHandle mhGetCapacity;
    private static MethodHandle mhExtract;
    // a interface EnergyProvider (pra detectar fontes sem instanceof)
    private static Class<?> clsEnergyProvider;

    private SNCEnergiesAdapter() {}

    /** O SNC Energies está no classpath? (1 verificação de loader, cache) */
    public static boolean disponivel() {
        if (!resolvido) {
            resolver();
        }
        return presente;
    }

    /** Resolve os MethodHandles 1× (lock duplo; falha = indisponível, nunca crash). */
    private static void resolver() {
        synchronized (SNCEnergiesAdapter.class) {
            if (resolvido) {
                return;
            }
            try {
                ClassLoader cl = SNCEnergiesAdapter.class.getClassLoader();
                Class<?> clsStorage = Class.forName(
                        "com.snc.energies.energy.EnergyStorage", false, cl);
                clsEnergyProvider = Class.forName(
                        "com.snc.energies.blockentity.EnergyProvider", false, cl);
                Method mGet = clsStorage.getMethod("getEnergy");
                Method mCap = clsStorage.getMethod("getCapacity");
                Method mExt = clsStorage.getMethod("extract", long.class, boolean.class);
                Class<?> clsProvider = clsEnergyProvider;
                Method mProv = clsProvider.getMethod("getEnergyStorage", Direction.class);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                mhGetEnergy = lookup.unreflect(mGet);
                mhGetCapacity = lookup.unreflect(mCap);
                mhExtract = lookup.unreflect(mExt);
                mhGetEnergyStorage = lookup.unreflect(mProv);
                presente = true;
                IntoxicantesMod.LOGGER.info(
                        "[Eletricidade] SNC Energies detectado: integração elétrica habilitada (fonte oficial de energia)");
            } catch (Throwable t) {
                presente = false;
                IntoxicantesMod.LOGGER.info(
                        "[Eletricidade] SNC Energies ausente: instalação elétrica sem fonte de energia (integração opcional)");
            }
            resolvido = true;
        }
    }

    // ==================================================== DETECÇÃO DE FONTE

    /**
     * A BE é uma fonte de energia do SNC Energies? (gerador, fogão a lenha,
     * Energy Cube, cabo deles — qualquer EnergyProvider.) Sem o mod: false.
     */
    public static boolean eFonteValida(@org.jspecify.annotations.Nullable BlockEntity be) {
        if (be == null || !disponivel() || clsEnergyProvider == null) {
            return false;
        }
        return clsEnergyProvider.isInstance(be);
    }

    /**
     * O storage de energia da BE na direção dada (lado OPOSTO é como o
     * EnergyTransfer deles consulta; null se não expuser naquele lado).
     * Retorna Object (a interface só existe se o mod estiver carregado).
     */
    @org.jspecify.annotations.Nullable
    public static Object storageDe(@org.jspecify.annotations.Nullable BlockEntity be, Direction lado) {
        if (!eFonteValida(be) || mhGetEnergyStorage == null) {
            return null;
        }
        try {
            return mhGetEnergyStorage.invoke(be, lado);
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================================================== LEITURAS

    /** Energia armazenada no storage (E). Storage estranho = 0. */
    public static long energia(@org.jspecify.annotations.Nullable Object storage) {
        if (storage == null || mhGetEnergy == null) {
            return 0L;
        }
        try {
            return (long) mhGetEnergy.invoke(storage);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** Capacidade do storage (E). */
    public static long capacidade(@org.jspecify.annotations.Nullable Object storage) {
        if (storage == null || mhGetCapacity == null) {
            return 0L;
        }
        try {
            return (long) mhGetCapacity.invoke(storage);
        } catch (Throwable t) {
            return 0L;
        }
    }

    // ==================================================== EXTRAÇÃO (o consumo REAL)

    /**
     * Tira energia DE VERDADE do storage (extract(amount, simulate=false)).
     * Devolve o que saiu (E). O quadro chama isto a cada tick de rede — o
     * consumo da instalação bate no gerador do SNC Energies de verdade.
     */
    public static long extrair(@org.jspecify.annotations.Nullable Object storage, long max) {
        if (storage == null || max <= 0 || mhExtract == null) {
            return 0L;
        }
        try {
            return (long) mhExtract.invoke(storage, max, false);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** Extração simulada (extract(amount, simulate=true)) — medição sem consumo. */
    public static long extrairSimulado(@org.jspecify.annotations.Nullable Object storage, long max) {
        if (storage == null || max <= 0 || mhExtract == null) {
            return 0L;
        }
        try {
            return (long) mhExtract.invoke(storage, max, true);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * A fonte ADJACENTE mais útil pro quadro (BE central do fogão/gerador/
     * cabo com energia). Varre as 6 direções e devolve o primeiro storage
     * com energia; null se ninguém alimentar.
     */
    @org.jspecify.annotations.Nullable
    public static Object fonteAdjacente(Level level, BlockPos pos) {
        if (!disponivel()) {
            return null;
        }
        for (Direction lado : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(lado));
            Object storage = storageDe(be, lado.getOpposite());
            if (storage != null && energia(storage) > 0) {
                return storage;
            }
        }
        return null;
    }
}
