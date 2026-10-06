package com.intoxicantes;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * ENTREGA DO GUIA NA PRIMEIRA ENTRADA (decisão do usuário: o jogador começa
 * o save já com o Guia do SNC Adventures na mão).
 *
 * Regras:
 *  - UMA VEZ POR JOGADOR, por mundo: flag persistente em
 *    intoxicantes_guia.json no diretório do mundo (mesmo padrão do
 *    PlayerMoney) — relogar, morrer ou reiniciar o servidor não duplica;
 *  - inventário cheio: o livro DROPA nos pés (nunca desaparece);
 *  - config guiaNaPrimeiraEntrada = false desliga a entrega automática
 *    (servidores). O livro continua obtível pelo CRAFT (única via — o guia
 *    não tem comando, decisão do usuário);
 *  - tudo server-side: funciona igual em singleplayer e dedicado.
 */
public final class GuiaPrimeiraVez {

    private GuiaPrimeiraVez() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Boolean> recebido = new HashMap<>();
    private static File arquivo;

    /** Chamado no SERVER_STARTING ao lado do PlayerMoney.init. */
    public static void init(File worldDir) {
        arquivo = new File(worldDir, "intoxicantes_guia.json");
        carregar();
    }

    /** Flag do jogador (para testes e consulta). */
    public static boolean jaRecebeu(ServerPlayer player) {
        return recebido.getOrDefault(player.getUUID(), false);
    }

    /** Marca como recebido e salva (usado pela entrega e por testes). */
    public static void marcarRecebido(ServerPlayer player) {
        recebido.put(player.getUUID(), Boolean.TRUE);
        salvar();
    }

    public static void registrar() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!ModConfig.get().guiaNaPrimeiraEntrada) {
                return; // servidor desligou a entrega automática
            }
            if (jaRecebeu(player)) {
                return; // uma vez por jogador, por mundo
            }
            marcarRecebido(player);
            entregar(player);
        });
    }

    /** Entrega o livro: inventário primeiro; sem espaço, dropa nos pés. */
    public static void entregar(ServerPlayer player) {
        ItemStack livro = new ItemStack(IntoxicantesMod.GUIA_SNC);
        if (!player.getInventory().add(livro)) {
            player.drop(livro, false, net.minecraft.util.Prediction.PREDICTED);
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "guia.intoxicantes.entregue"));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    // ==================================================== PERSISTÊNCIA

    private static void carregar() {
        if (arquivo == null || !arquivo.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(arquivo.toPath())) {
            Type tipo = new TypeToken<HashMap<UUID, Boolean>>() {}.getType();
            HashMap<UUID, Boolean> lido = GSON.fromJson(reader, tipo);
            if (lido != null) {
                recebido.clear();
                recebido.putAll(lido);
            }
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao carregar guia: {}",
                    e.getMessage());
        }
    }

    private static void salvar() {
        if (arquivo == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(arquivo.toPath())) {
            GSON.toJson(recebido, writer);
        } catch (Exception e) {
            IntoxicantesMod.LOGGER.warn("[Intoxicantes] Erro ao salvar guia: {}",
                    e.getMessage());
        }
    }
}
