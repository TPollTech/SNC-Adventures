package com.intoxicantes;

import net.fabricmc.api.ModInitializer;

/**
 * v1.2.51 — boot do mod de GAME TESTS.
 *
 * A escala de aceleração dos processos de bebida (x20) é definida UMA VEZ
 * aqui, no boot do servidor de testes. Antes cada teste de cadeia setava
 * {@code 0.05F} no começo e {@code null} no fim — mas os testes rodam em
 * PARALELO no mesmo servidor: o {@code null} de um teste zerava a escala
 * enquanto outra cadeia ainda carregava máquinas, que então calculavam a
 * duração com a escala errada e os 4 testes de cadeia falhavam.
 */
public class TestesBebidaBoot implements ModInitializer {

    /** Mesma escala que os testes de cadeia sempre usaram (420s → 21s). */
    public static final float ESCALA = 0.05F;

    @Override
    public void onInitialize() {
        ModConfig.setVelocidadeTeste(ESCALA);
    }
}
