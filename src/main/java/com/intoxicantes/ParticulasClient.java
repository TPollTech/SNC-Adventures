package com.intoxicantes;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * As tres partículas do mod no client (sprites proprios no atlas):
 * - DinheiroParticle: a nota voando na venda (fisica de fumaça de fogueira:
 *   sobe serpenteando, sem gravidade — a grana não cai, kkkk)
 * - FolhaParticle: folhinha da planta madura, cai devagar balançando
 * - FumacaErvaParticle: bafo do baseado — subida lenta, fade out, verde-erva
 */
@Environment(EnvType.CLIENT)
public final class ParticulasClient {
    private ParticulasClient() {}

    /** Registra os providers (chamado do onInitializeClient, antes do engine bakes). */
    public static void init() {
        ParticleProviderRegistry.getInstance()
                .register(Particulas.DINHEIRO, DinheiroParticle.Provider::new);
        ParticleProviderRegistry.getInstance()
                .register(Particulas.FOLHA, FolhaParticle.Provider::new);
        ParticleProviderRegistry.getInstance()
                .register(Particulas.FUMACA_ERVA, FumacaErvaParticle.Provider::new);
    }

    // ==================================================== DINHEIRO
    /** A nota de R$ voando quando a venda sai — serpenteia pra cima, some lenta. */
    public static class DinheiroParticle extends SingleQuadParticle {
        private final SpriteSet sprites;
        private double fase;

        DinheiroParticle(ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites.get(RandomSource.create()));
            this.sprites = sprites;
            this.lifetime = 28 + this.random.nextInt(12); // ~1.5s voando
            this.gravity = 0.0F;      // sobe sozinho: grana pro alto kkkk
            this.friction = 0.92F;
            this.fase = this.random.nextDouble() * Math.PI * 2;
            this.roll = (float) (this.random.nextDouble() - 0.5) * 0.6F;
            this.quadSize = 0.18F;
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            // sobe sempre; serpenteia no plano horizontal (onda senoidal)
            this.yd += 0.028;
            this.xd += Math.cos(this.fase + this.age * 0.45) * 0.004;
            this.zd += Math.sin(this.fase + this.age * 0.45) * 0.004;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= this.friction;
            this.yd *= 0.94;
            this.zd *= this.friction;
            if (this.age > this.lifetime / 2) {
                this.alpha = 1.0F - (this.age - this.lifetime / 2F) / (this.lifetime / 2F);
            }
        }

        @Override
        public Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        public record Provider(SpriteSet sprites) implements
                ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                    double x, double y, double z, double vx, double vy, double vz,
                    RandomSource random) {
                return new DinheiroParticle(level, x, y, z, vx, vy, vz, this.sprites);
            }
        }
    }

    // ==================================================== FOLHA
    /** Folhinha da planta madura: cai devagar, balança, pousa e some. */
    public static class FolhaParticle extends SingleQuadParticle {
        private final SpriteSet sprites;
        private double fase;

        FolhaParticle(ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites.get(RandomSource.create()));
            this.sprites = sprites;
            this.lifetime = 40 + this.random.nextInt(20);
            this.gravity = 0.012F;    // queda de folha, nao de pedra
            this.friction = 0.96F;
            this.fase = this.random.nextDouble() * Math.PI * 2;
            this.roll = (float) (this.random.nextDouble() - 0.5) * 1.2F;
            this.quadSize = 0.11F;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            // vaivem horizontal de folha caindo (sempre descendo devagar)
            this.xd += Math.cos(this.fase + this.age * 0.35) * 0.0035;
            this.zd += Math.sin(this.fase + this.age * 0.35) * 0.0035;
            this.yd -= 0.002; // compensa o gravity baixo: queda constante
            this.move(this.xd, this.yd, this.zd);
            this.xd *= this.friction;
            this.zd *= this.friction;
            if (this.onGround) {
                // pousou: para de girar e se desfaz
                this.roll = 0.0F;
                this.alpha = 1.0F - (this.age % 10) / 10F;
            }
        }

        @Override
        public Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        public record Provider(SpriteSet sprites) implements
                ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                    double x, double y, double z, double vx, double vy, double vz,
                    RandomSource random) {
                return new FolhaParticle(level, x, y, z, vx, vy, vz, this.sprites);
            }
        }
    }

    // ==================================================== FUMACA DE ERVA
    /** Bafo do baseado: sobe devagar, expande e some — verde-erva no setColor. */
    public static class FumacaErvaParticle extends SingleQuadParticle {
        private final SpriteSet sprites;

        FumacaErvaParticle(ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites.get(RandomSource.create()));
            this.sprites = sprites;
            this.lifetime = 24 + this.random.nextInt(10);
            this.gravity = -0.012F;   // fumaça sobe (gravidade negativa)
            this.friction = 0.9F;
            this.roll = (float) (this.random.nextDouble() - 0.5) * 0.8F;
            this.quadSize = 0.12F;
            // tinta de erva: verde-acinzentado, varia um pouco por nuvem
            float tom = 0.85F + this.random.nextFloat() * 0.15F;
            this.setColor(0.62F * tom, 0.78F * tom, 0.55F * tom);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            // desacelera e expande conforme envelhece
            float t = (float) this.age / this.lifetime;
            this.quadSize = 0.12F + t * 0.22F;
            this.alpha = t < 0.15F ? t / 0.15F : 1.0F - (t - 0.15F) / 0.85F;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= this.friction;
            this.yd *= this.friction;
            this.zd *= this.friction;
        }

        @Override
        public Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        public record Provider(SpriteSet sprites) implements
                ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                    double x, double y, double z, double vx, double vy, double vz,
                    RandomSource random) {
                return new FumacaErvaParticle(level, x, y, z, vx, vy, vz, this.sprites);
            }
        }
    }
}
