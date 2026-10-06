package com.intoxicantes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

/**
 * Partículas próprias do mod (item 2 do TODO): nota de R$ na venda, folha da
 * planta madura e fumaça do baseado. Todos SimpleParticleType (sem dados) —
 * sprites em assets/intoxicantes/textures/particle/, atlas em particles/*.json.
 */
public final class Particulas {
    /** A cédula voando quando a venda sai — a festa visual do comércio. */
    public static final SimpleParticleType DINHEIRO = register("dinheiro");
    /** Folhinha caindo da planta em ponto de colheita (uv_age=3). */
    public static final SimpleParticleType FOLHA = register("folha");
    /** Bafo verde da erva — a fumaça do baseado com cara de erva. */
    public static final SimpleParticleType FUMACA_ERVA = register("fumaca_erva");

    private Particulas() {}

    /** Força a inicialização da classe (registro no mod init, antes do freeze). */
    public static void init() {
    }

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(IntoxicantesMod.MOD_ID, name),
                FabricParticleTypes.simple(false));
        // overrideLimiter=false: respeita Particles "Minimal" como o vanilla
    }
}
