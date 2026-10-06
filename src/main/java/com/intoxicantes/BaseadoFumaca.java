package com.intoxicantes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Fumaca PROPRIA do Baseado (item 2 do TODO): enquanto o fregues puxa (use
 * duration correndo), o Boca solta o bafo verde-erva (particula client) e uma
 * coluna de fumaça de fogueira no servidor — de longe se vê que tem baseado
 * na esquina. Server tick, custo zero quando ninguém está fumando.
 */
public final class BaseadoFumaca {
    private BaseadoFumaca() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ItemStack usando = player.getUseItem();
                if (!usando.is(IntoxicantesMod.BASEADO) || !player.isUsingItem()) {
                    continue;
                }
                ServerLevel level = (ServerLevel) player.level();
                Vec3 olhando = player.getViewVector(1.0F);
                // boca: um pouco abaixo da linha dos olhos (ninguem exala pela testa)
                Vec3 boca = player.getEyePosition().add(olhando.scale(0.35)).subtract(0, 0.12, 0);
                // bafo verde-erva (client, com tint): um por puxada a cada 3 ticks
                if (player.tickCount % 3 == 0) {
                    level.sendParticles(Particulas.FUMACA_ERVA,
                            boca.x, boca.y, boca.z,
                            1, 0.02, 0.01, 0.02, 0.006);
                }
                // coluna visivel de longe (server): a cada 5 ticks, um cosmico
                if (player.tickCount % 5 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            boca.x, boca.y, boca.z,
                            1, 0.02, 0.02, 0.02, 0.004);
                }
            }
        });
    }
}
