package com.intoxicantes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

/**
 * v1.2.54 — A VISAO DAS VIAGENS (client-only, 100% fantasma):
 *
 * - VIAGEM (LSD): hue-cycle do mundo inteiro via colormap trick de partícula
 *   e o GAGO GIGANTE atravessando o céu — uma entidade real criada NO CLIENT
 *   (nunca existe no servidor: os outros players nao veem NADA), sem IA, sem
 *   colisão, só o corpo do mercadão flutuando de lado kkkk. Aparece 1x por
 *   viagem (intensidade 2+: pode voltar), atravessa em ~20s e some sozinho.
 * - MORNO/SONHO: tintas quentes e "nod" (a cabeça pesa — pulso de pitch).
 * - OVERDRIVE: zoom pulsando com a batida (o CameraFovMixin pergunta aqui).
 * - ABSTINENCIA: o mundo escurece nas bordas (as sombras chegam antes).
 *
 * As entidades fantasmas são limpas no logout/troca de mundo (o tick confere).
 */
public final class SaudeVisionClient {
    private SaudeVisionClient() {}

    /** A tag que marca o Gago fantasma pro renderer (client-only). */
    public static final String TAG_FANTASMA = "gago_fantasma";

    /** O Gago fantasma atual (client-only; null = nenhum). */
    private static Entity gagoFantasma;
    /** Já desenhou o Gago nesta viagem? (intensidade 1: 1 aparecimento só) */
    private static boolean gagoDestaViagem;
    /** A viagem que estava ativa no último tick (pra detectar troca/fim). */
    private static String viagemAnterior = "";
    /** RNG client (só pra variar a entrada do Gago). */
    private static final RandomSource RNG = RandomSource.create();

    public static void init() {
        // o ciclo de vida do fantasma (tick de client)
        ClientTickEvents.END_CLIENT_TICK.register(SaudeVisionClient::fimDoTick);
        // o scale 4x do fantasma mora no IntoxicantesClient.GagoRenderer: ele
        // vê a tag "gago_fantasma" no extractRenderState e estica o corpo.
    }

    // ==================================================== CICLO DE VIDA DO FANTASMA

    private static void fimDoTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        String viagem = SaudeClient.viagemAtiva();

        // troca de mundo/logout: o fantasma não atravessa dimensão
        if (player == null || mc.level == null) {
            removerFantasma(mc);
            gagoDestaViagem = false;
            viagemAnterior = "";
            return;
        }

        // a viagem acabou ou mudou: limpa a marca da aparição
        if (!viagem.equals(viagemAnterior)) {
            gagoDestaViagem = false;
            viagemAnterior = viagem;
        }

        if (viagem.equals("VIAGEM")) {
            int intensidade = SaudeClient.intensidadeViagem();
            boolean podeAparecer = intensidade >= 2 || !gagoDestaViagem;
            if (podeAparecer && gagoFantasma == null
                    && mc.level.getGameTime() % 100 == 0 && RNG.nextInt(3) == 0) {
                aparecerGago(mc, player, intensidade);
            }
        }

        // o fantasma vive atravessando: quando sai do raio (ou o nivel trocou),
        // remove e libera a próxima aparição
        if (gagoFantasma != null) {
            if (!gagoFantasma.isAlive() || gagoFantasma.level() != mc.level
                    || gagoFantasma.distanceTo(player) > 96.0) {
                removerFantasma(mc);
                gagoDestaViagem = true; // já veio nesta viagem
            }
        }
    }

    /**
     * Cria o Gago GIGANTE: 8 blocos de altura (quase o letreiro kkkk), client
     * only. Nascimento: 40-70 blocos de distância, na direção do olhar, lá no
     * alto — atravessa na direção do jogador e continua (sem IA: só pose).
     */
    private static void aparecerGago(Minecraft mc, LocalPlayer player, int intensidade) {
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        double dist = 48 + RNG.nextInt(24);
        double x = player.getX() - Math.sin(yawRad) * dist;
        double z = player.getZ() + Math.cos(yawRad) * dist;
        double y = player.getY() + 14 + RNG.nextInt(8);

        Entity fantasma = IntoxicantesMod.GAGO.create(mc.level, EntitySpawnReason.EVENT);
        if (fantasma == null) {
            return;
        }
        fantasma.absSnapTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
        fantasma.setNoGravity(true);
        fantasma.setSilent(true);
        // a TAG que o renderer client usa pra desenhar 4x (só existe nesta
        // instância client: o servidor nunca viu esta entidade)
        fantasma.addTag(TAG_FANTASMA);
        mc.level.addEntity(fantasma);
        // a travessia: empurrão lento na direção do jogador (sem IA, só física)
        fantasma.push(
                (player.getX() - x) / dist * 0.35,
                -0.01,
                (player.getZ() - z) / dist * 0.35);
        gagoFantasma = fantasma;
    }

    private static void removerFantasma(Minecraft mc) {
        if (gagoFantasma != null && mc.level != null) {
            mc.level.removeEntity(gagoFantasma.getId(),
                    Entity.RemovalReason.DISCARDED);
        }
        gagoFantasma = null;
    }

    // ==================================================== O PULSO DAS TINTAS (o mixin de FOV pergunta)

    /**
     * Multiplicador de FOV do OVERDRIVE: o zoom pulsa com a "batida do
     * coração" (o mundo aperta e solta, ~200 bpm no pico). O CameraFovMixin
     * soma este fator ao do ADS (nunca os dois ao mesmo tempo na prática).
     */
    public static float fovViagem(float parcial) {
        String viagem = SaudeClient.viagemAtiva();
        if (viagem.isEmpty()) {
            return 1.0F;
        }
        long t = System.currentTimeMillis();
        return switch (viagem) {
            case "OVERDRIVE" -> 1.0F + SaudeClient.pulsoOverdrive(t) * 0.045F * SaudeClient.intensidadeViagem();
            case "VIAGEM" -> 1.0F + Mth.sin(t / 900.0F) * 0.02F * SaudeClient.intensidadeViagem(); // respira
            case "SONHO" -> 0.985F; // a pálpebra pesada
            default -> 1.0F;
        };
    }
}
