package com.intoxicantes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * O chumbo da 12 (v1.2.11) — vale pros DOIS atiradores (item do player e o NPC).
 *
 * O CASO DO WITHER: no 26.3 os i-frames mudaram de lugar — o cooldown do dano
 * vive no campo PUBLICO damageCooldownTime e, com ele ativo, so entra dano
 * MAIOR que o ultimo hit (lastHurt). O setInvulnerableTime(0) legado NAO limpa
 * esse campo. Resultado: os 8 balins do mesmo tamanho viravam 1 hit, e o
 * volley seguinte do Gago (dano igual, cooldown re-ativado pelo 1o balim)
 * quicava inteiro — contra um chefe de 300 de vida, "o gago nao consegue dar
 * hit no wither". Chumbo.aplicar zera o cooldown REAL a cada balim.
 */
final class Chumbo {

    private Chumbo() {}

    /**
     * Aplica o dano do balim com a blindagem padrao da 12: escudo Invulnerable
     * de save antigo derrubado (o hurt engolia SEM LOG) e os i-frames do 26.3
     * zerados — o tiro inteiro e' UM hit e todo volley entra limpo.
     */
    static void aplicar(ServerLevel level, LivingEntity vitima, DamageSource fonte, float dano) {
        if (vitima.isInvulnerable()) {
            vitima.setPermanentlyInvulnerable(false);
            IntoxicantesMod.LOGGER.warn("[Escopeta] vitima {} estava Invulnerable — escudo derrubado",
                    vitima.getName().getString());
        }
        vitima.damageCooldownTime = 0; // o cooldown REAL do 26.3
        if (vitima.getInvulnerableTime() > 0) {
            vitima.setInvulnerableTime(0); // campo legado, por garantia
        }
        vitima.hurtServer(level, fonte, dano);
    }

    /**
     * v1.2.41 — O RASTRO do chumbo (tracer): partículas CRIT em linha reta da
     * boca da arma até o ponto de impacto, a cada 0.9 bloco — o tiro ganha
     * leitura instantânea (você VÊ onde o balim foi parar).
     */
    static void tracer(ServerLevel level, Vec3 origem, Vec3 alvo) {
        double distancia = origem.distanceTo(alvo);
        if (distancia < 1.5) {
            return; // tiro à queima-roupa não precisa de rastro
        }
        Vec3 direcao = alvo.subtract(origem).normalize();
        int passos = (int) (distancia / 0.9);
        for (int i = 1; i < passos; i++) {
            Vec3 ponto = origem.add(direcao.scale(i * 0.9));
            level.sendParticles(ParticleTypes.CRIT, ponto.x, ponto.y, ponto.z,
                    1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    /**
     * v1.2.41 — O CHUTE FÍSICO do chumbo: empurra a vítima na direção do tiro
     * com um empurrão de verdade (12 = escopeta empurra mais; .38 = bala pesada
     * e seca). Respeita resistência a knockback da criatura (lógica do vanilla:
     * quanto mais KB resistente, menos anda).
     */
    static void empurrar(LivingEntity vitima, Entity atirador, double forca) {
        Vec3 empurrao = vitima.position().subtract(atirador.position())
                .multiply(1.0, 0.0, 1.0);
        if (empurrao.lengthSqr() < 1.0e-4) {
            empurrao = atirador.getLookAngle().multiply(1.0, 0.0, 1.0);
        }
        empurrao = empurrao.normalize();
        double resistencia = Math.max(0.0, 1.0 - vitima.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        vitima.push(empurrao.x * forca * resistencia, 0.28 * resistencia,
                empurrao.z * forca * resistencia);
        vitima.syncVelocity = true; // o client vê o empurrão de verdade
    }
}
