package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Economia do servidor. Saldo e recibo de uma operação são gravados juntos. */
public final class PlayerMoney {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Livro livro;

    private PlayerMoney() {}

    public static void init(File worldDir) {
        reset(); // um mundo novo nunca herda os saldos/fiados do servidor anterior
        livro = new Livro(worldDir.toPath());
    }

    public static void reset() { livro = null; }

    public static int get(ServerPlayer player) { return get(player.getUUID()); }
    public static int get(UUID jogador) { return livro == null ? 0 : livro.get(jogador); }
    public static void set(ServerPlayer player, int valor) { atual().set(player.getUUID(), valor); }
    public static int add(ServerPlayer player, int quantia) {
        long novo = Math.max(0L, Math.min((long) get(player) + quantia, Integer.MAX_VALUE));
        set(player, (int) novo);
        return (int) novo;
    }
    public static boolean tem(ServerPlayer player, int quantia) {
        return quantia >= 0 && get(player) >= quantia;
    }
    public static boolean subtrair(ServerPlayer player, int quantia) {
        if (!tem(player, quantia)) return false;
        add(player, -quantia);
        return true;
    }

    public static int getDivida(ServerPlayer player) { return atual().dividas.getOrDefault(player.getUUID(), 0); }
    public static void addDivida(ServerPlayer player, int quantia) {
        int novo = (int) Math.max(0L, Math.min((long) getDivida(player) + quantia, Integer.MAX_VALUE));
        atual().setDivida(player.getUUID(), novo);
    }
    public static int pagarDivida(ServerPlayer player, int quantia) {
        int pago = Math.min(Math.max(0, quantia), getDivida(player));
        if (pago > 0) atual().setDivida(player.getUUID(), getDivida(player) - pago);
        return pago;
    }

    /** Operação imutável por UUID, inclusive para jogadores offline. */
    public static Movimento transacionar(UUID operacao, UUID jogador, long delta) {
        if (livro == null) return Movimento.ERRO_PERSISTENCIA;
        return livro.transacionar(operacao, jogador, delta);
    }

    static Livro atual() {
        if (livro == null) throw new IllegalStateException("Economia não inicializada");
        return livro;
    }

    public enum Movimento { APLICADO, REPETIDO, SEM_SALDO, SALDO_CHEIO, CONFLITO, ERRO_PERSISTENCIA }
    private record Recibo(UUID jogador, long delta, Movimento resultado) {}
    private record Documento(int formato, Map<UUID, Integer> saldos, Map<UUID, Recibo> operacoes) {}

    /** Instância por mundo; os testes podem usar diretórios isolados sem alterar a economia real. */
    static final class Livro {
        private final Path arquivo;
        private final Path fiado;
        private final Map<UUID, Integer> saldos = new HashMap<>();
        private final Map<UUID, Recibo> operacoes = new HashMap<>();
        private final Map<UUID, Integer> dividas = new HashMap<>();

        Livro(Path mundo) {
            arquivo = mundo.resolve("intoxicantes_money.json");
            fiado = mundo.resolve("intoxicantes_fiado.json");
            try {
                Files.createDirectories(mundo);
                if (Files.exists(arquivo)) {
                    JsonObject objeto = lerObjeto(arquivo);
                    if (objeto.has("formato")) {
                        Documento d = GSON.fromJson(objeto, Documento.class);
                        if (d.formato() != 2 || d.saldos() == null || d.operacoes() == null)
                            throw new IOException("Formato de dinheiro inválido");
                        saldos.putAll(d.saldos());
                        operacoes.putAll(d.operacoes());
                        for (var entrada : operacoes.entrySet()) {
                            Recibo r = entrada.getValue();
                            if (entrada.getKey() == null || r == null || r.jogador() == null
                                    || (r.resultado() != Movimento.APLICADO && r.resultado() != Movimento.SEM_SALDO))
                                throw new IOException("Recibo monetário inválido");
                        }
                    } else {
                        // Migração explícita do antigo mapa UUID -> saldo, mantendo o nome oficial.
                        saldos.putAll(lerMapa(objeto));
                        validarSaldos(saldos);
                        Path legado = arquivo.resolveSibling("intoxicantes_money.legacy-backup.json");
                        if (!Files.exists(legado)) Files.copy(arquivo, legado);
                        salvar();
                        IntoxicantesMod.LOGGER.info("[SNC Adventures] Economia migrada: {} saldos; original em {}", saldos.size(), legado);
                    }
                    validarSaldos(saldos);
                }
                if (Files.exists(fiado)) dividas.putAll(lerMapa(lerObjeto(fiado)));
                validarSaldos(dividas);
            } catch (Exception e) {
                IntoxicantesMod.LOGGER.error("[SNC Adventures] Não foi possível abrir a economia em {}. Arquivos preservados.", mundo, e);
                throw new IllegalStateException("Economia persistente inválida; não substituir por saldos vazios", e);
            }
        }

        int get(UUID jogador) { return saldos.getOrDefault(jogador, 0); }

        void set(UUID jogador, int valor) {
            Integer anterior = saldos.put(jogador, Math.max(0, valor));
            try { salvar(); }
            catch (IOException e) {
                restaurar(saldos, jogador, anterior);
                IntoxicantesMod.LOGGER.error("[SNC Adventures] Saldo não foi salvo; alteração cancelada", e);
                throw new IllegalStateException("Não foi possível salvar o dinheiro", e);
            }
        }

        void setDivida(UUID jogador, int valor) {
            Integer anterior = dividas.put(jogador, Math.max(0, valor));
            try { gravarAtomico(fiado, dividas); }
            catch (IOException e) {
                restaurar(dividas, jogador, anterior);
                IntoxicantesMod.LOGGER.error("[SNC Adventures] Fiado não foi salvo; alteração cancelada", e);
                throw new IllegalStateException("Não foi possível salvar o fiado", e);
            }
        }

        Movimento transacionar(UUID id, UUID jogador, long delta) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(jogador);
            if (delta == 0) return Movimento.CONFLITO;
            Recibo repetido = operacoes.get(id);
            if (repetido != null) {
                if (!repetido.jogador().equals(jogador) || repetido.delta() != delta) return Movimento.CONFLITO;
                return repetido.resultado() == Movimento.APLICADO ? Movimento.REPETIDO : repetido.resultado();
            }
            long novo;
            try { novo = Math.addExact((long) get(jogador), delta); }
            catch (ArithmeticException e) { return delta > 0 ? Movimento.SALDO_CHEIO : Movimento.SEM_SALDO; }
            // Prêmio que não cabe NÃO recebe recibo: permanece devido e pode ser pago depois.
            if (novo > Integer.MAX_VALUE) return Movimento.SALDO_CHEIO;
            Movimento resultado = novo < 0 ? Movimento.SEM_SALDO : Movimento.APLICADO;
            Integer anterior = saldos.get(jogador);
            if (resultado == Movimento.APLICADO) saldos.put(jogador, (int) novo);
            operacoes.put(id, new Recibo(jogador, delta, resultado));
            try { salvar(); return resultado; }
            catch (IOException e) {
                restaurar(saldos, jogador, anterior);
                operacoes.remove(id);
                IntoxicantesMod.LOGGER.error("[SNC Adventures] Operação {} não persistiu; saldo preservado", id, e);
                return Movimento.ERRO_PERSISTENCIA;
            }
        }

        private void salvar() throws IOException { gravarAtomico(arquivo, new Documento(2, saldos, operacoes)); }
    }

    private static JsonObject lerObjeto(Path arquivo) throws IOException {
        var valor = JsonParser.parseString(Files.readString(arquivo, StandardCharsets.UTF_8));
        if (!valor.isJsonObject()) throw new IOException("JSON não é um objeto: " + arquivo);
        return valor.getAsJsonObject();
    }

    private static Map<UUID, Integer> lerMapa(JsonObject objeto) throws IOException {
        Map<UUID, Integer> mapa = GSON.fromJson(objeto, new TypeToken<HashMap<UUID, Integer>>() {}.getType());
        if (mapa == null) throw new IOException("Mapa monetário ausente");
        return mapa;
    }

    private static void validarSaldos(Map<UUID, Integer> mapa) throws IOException {
        for (var e : mapa.entrySet())
            if (e.getKey() == null || e.getValue() == null || e.getValue() < 0)
                throw new IOException("Saldo ou dívida inválido");
    }

    private static <T> void restaurar(Map<UUID, T> mapa, UUID id, T anterior) {
        if (anterior == null) mapa.remove(id); else mapa.put(id, anterior);
    }

    /** Force antes da troca atômica: o arquivo oficial contém tudo antes ou tudo depois. */
    static synchronized void gravarAtomico(Path destino, Object dados) throws IOException {
        Path temporario = destino.resolveSibling(destino.getFileName() + ".pending");
        byte[] bytes = GSON.toJson(dados).getBytes(StandardCharsets.UTF_8);
        try (FileChannel canal = FileChannel.open(temporario, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) canal.write(buffer);
            canal.force(true);
        }
        // Sem fallback para cópia não atômica: uma falha precisa ser visível e reversível.
        // No Windows, scanners/indexadores podem segurar brevemente o destino após gravá-lo.
        for (int tentativa = 0; ; tentativa++) {
            try {
                Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (java.nio.file.AccessDeniedException bloqueio) {
                if (tentativa >= 5) throw bloqueio;
                try {
                    Thread.sleep(20L << tentativa);
                } catch (InterruptedException interrompido) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrompida a gravação atômica de " + destino, interrompido);
                }
            }
        }
    }
}
