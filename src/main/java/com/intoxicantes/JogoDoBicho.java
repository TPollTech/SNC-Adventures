package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Uma loteria por mundo, relógio do overworld e operações monetárias recuperáveis. */
public final class JogoDoBicho {
    private static Loteria loteria;
    private static final List<Grupo> GRUPOS;
    static {
        String[] nomes = {"avestruz", "aguia", "burro", "borboleta", "cachorro", "cabra", "carneiro",
                "camelo", "cobra", "coelho", "cavalo", "elefante", "galo", "gato", "jacare", "leao",
                "macaco", "porco", "pavao", "peru", "touro", "tigre", "urso", "veado", "vaca"};
        List<Grupo> grupos = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) grupos.add(new Grupo(i + 1, nomes[i], i * 4 + 1, i * 4 + 4));
        GRUPOS = List.copyOf(grupos);
    }
    private JogoDoBicho() {}
    public enum Tipo { GRUPO, DEZENA }
    public enum Estado { PREPARADA, ACEITA, REJEITADA, PERDEU, PREMIO_PENDENTE, PAGA }
    public enum Status { ACEITA, REPETIDA, INVALIDA, SEM_SALDO, LIMITE, ERRO_PERSISTENCIA, CONFLITO }
    public record Grupo(int numero, String id, int primeiraDezena, int ultimaDezena) {}
    public record Aposta(UUID id, UUID jogador, long dia, int horarioSorteio, Tipo tipo, int grupo,
                         int dezena, int valor, int multiplicador, Estado estado, long premio) {
        Aposta comEstado(Estado novo, long pagamento) {
            return new Aposta(id, jogador, dia, horarioSorteio, tipo, grupo, dezena, valor, multiplicador, novo, pagamento);
        }
    }
    public record Resultado(long dia, int horarioSorteio, int grupo, int dezena) {}
    public record Resposta(Status status, Aposta aposta) {}
    public record Snapshot(long proximoDia, int horarioSorteio, int saldo, int minimo, int maximo,
                           int multiplicadorGrupo, int multiplicadorDezena, int limite,
                           List<Aposta> apostas, List<Resultado> resultados) {}

    public static List<Grupo> grupos() { return GRUPOS; }
    public static int normalizarDezena(int dezena) { return dezena == 0 ? 100 : dezena; }
    public static int grupoDaDezena(int dezena) {
        int n = normalizarDezena(dezena);
        if (n < 1 || n > 100) throw new IllegalArgumentException("Dezena fora de 00..99");
        return (n + 3) / 4;
    }
    public static void init(File worldDir) {
        reset();
        SecureRandom sorteador = new SecureRandom(); // independente da seed do mundo/cliente
        loteria = new Loteria(worldDir.toPath(), PlayerMoney.atual(), () -> sorteador.nextInt(100) + 1);
    }
    public static void reset() { loteria = null; }
    public static void tick(MinecraftServer servidor) {
        if (loteria != null) loteria.tick(CalendarioEsquinao.tempoTotal(servidor.overworld()), Regras.config());
    }
    public static Snapshot snapshot(MinecraftServer servidor, UUID jogador) {
        return atual().snapshot(CalendarioEsquinao.tempoTotal(servidor.overworld()), jogador, Regras.config());
    }
    public static Resposta apostar(MinecraftServer servidor, UUID requestId, UUID jogador, Tipo tipo,
                                   int grupo, int dezena, int valor) {
        if (loteria == null) return new Resposta(Status.ERRO_PERSISTENCIA, null);
        return loteria.apostar(CalendarioEsquinao.tempoTotal(servidor.overworld()), requestId,
                jogador, tipo, grupo, dezena, valor, Regras.config());
    }
    private static Loteria atual() {
        if (loteria == null) throw new IllegalStateException("Loteria não inicializada");
        return loteria;
    }

    /** Parâmetros efetivos também ficam congelados no bilhete/rodada, sem mudar apostas antigas. */
    record Regras(int horario, int minimo, int maximo, int grupo, int dezena, int limite) {
        static Regras config() {
            ModConfig c = ModConfig.get();
            return new Regras(c.bichoHorarioSorteioTicks, c.bichoApostaMinima, c.bichoApostaMaxima,
                    c.bichoMultiplicadorGrupo, c.bichoMultiplicadorDezena, c.bichoMaxApostasPorSorteio);
        }
        Regras {
            if (horario < 0 || horario >= 24000 || minimo < 1 || maximo < minimo
                    || grupo < 1 || dezena < 1 || limite < 1) throw new IllegalArgumentException("Regras inválidas");
        }
    }
    private record Rodada(long dia, int horario, Resultado resultado) {}
    private record Documento(int formato, long maiorTempo, long ultimoDiaSorteado,
                             Map<UUID, Aposta> apostas, Map<Long, Rodada> rodadas) {}

    static final class Loteria {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Path arquivo;
        private final PlayerMoney.Livro dinheiro;
        private final IntSupplier sorteador;
        private Documento dados = new Documento(1, 0, -1, Map.of(), Map.of());
        private long ultimoErroLog = Long.MIN_VALUE;

        Loteria(Path mundo, PlayerMoney.Livro dinheiro, IntSupplier sorteador) {
            this.arquivo = mundo.resolve("intoxicantes_bicho.json");
            this.dinheiro = dinheiro;
            this.sorteador = sorteador;
            if (Files.exists(arquivo)) {
                try {
                    Documento carregado = GSON.fromJson(Files.readString(arquivo, StandardCharsets.UTF_8), Documento.class);
                    validar(carregado);
                    dados = carregado;
                } catch (Exception e) {
                    IntoxicantesMod.LOGGER.error("[SNC Adventures] Loteria não pôde ser recuperada; dados preservados em {}", arquivo, e);
                    throw new IllegalStateException("Loteria persistente inválida", e);
                }
            }
        }

        Resposta apostar(long tempo, UUID id, UUID jogador, Tipo tipo, int grupo, int dezena, int valor, Regras regras) {
            if (id == null || jogador == null || tipo == null) return new Resposta(Status.INVALIDA, null);
            Aposta existente = dados.apostas().get(id);
            int numero = tipo == Tipo.DEZENA ? normalizarDezena(dezena) : 0;
            if (existente != null) {
                if (!existente.jogador().equals(jogador) || existente.tipo() != tipo || existente.grupo() != grupo
                        || existente.dezena() != numero || existente.valor() != valor)
                    return new Resposta(Status.CONFLITO, null);
                if (existente.estado() == Estado.PREPARADA && !confirmar(existente))
                    return new Resposta(Status.ERRO_PERSISTENCIA, existente);
                existente = dados.apostas().get(id);
                return new Resposta(existente.estado() == Estado.REJEITADA ? Status.SEM_SALDO : Status.REPETIDA, existente);
            }
            // Um retry antigo conserva suas regras; só bilhetes novos usam os limites atuais.
            if (grupo < 1 || grupo > 25 || valor < regras.minimo() || valor > regras.maximo()
                    || (tipo == Tipo.DEZENA && (numero < 1 || numero > 100 || grupoDaDezena(numero) != grupo)))
                return new Resposta(Status.INVALIDA, null);
            // Fechar o sorteio vencido ANTES de admitir qualquer bilhete novo.
            if (!tick(tempo, regras)) return new Resposta(Status.ERRO_PERSISTENCIA, null);
            long dia = proximoDia(tempo, regras);
            long quantidade = dados.apostas().values().stream().filter(a -> a.jogador().equals(jogador)
                    && a.dia() == dia && a.estado() != Estado.REJEITADA).count();
            if (quantidade >= regras.limite()) return new Resposta(Status.LIMITE, null);
            Rodada rodada = dados.rodadas().get(dia);
            int horario = rodada == null ? regras.horario() : rodada.horario();
            Aposta preparada = new Aposta(id, jogador, dia, horario, tipo, grupo, numero, valor,
                    tipo == Tipo.GRUPO ? regras.grupo() : regras.dezena(), Estado.PREPARADA, 0);
            Map<UUID, Aposta> apostas = new HashMap<>(dados.apostas());
            apostas.put(id, preparada);
            Map<Long, Rodada> rodadas = new HashMap<>(dados.rodadas());
            rodadas.putIfAbsent(dia, new Rodada(dia, horario, null));
            if (!persistir(new Documento(1, Math.max(dados.maiorTempo(), tempo), dados.ultimoDiaSorteado(), apostas, rodadas)))
                return new Resposta(Status.ERRO_PERSISTENCIA, null);
            // Se o processo cair daqui até a confirmação, PREPARADA + recibo recuperam a operação.
            if (!confirmar(preparada)) return new Resposta(Status.ERRO_PERSISTENCIA, preparada);
            Aposta finalizada = dados.apostas().get(id);
            return new Resposta(finalizada.estado() == Estado.REJEITADA ? Status.SEM_SALDO : Status.ACEITA, finalizada);
        }

        private boolean confirmar(Aposta aposta) {
            var movimento = dinheiro.transacionar(operacao("aposta", aposta.id()), aposta.jogador(), -aposta.valor());
            if (movimento == PlayerMoney.Movimento.ERRO_PERSISTENCIA || movimento == PlayerMoney.Movimento.CONFLITO)
                return false;
            Estado estado = movimento == PlayerMoney.Movimento.SEM_SALDO ? Estado.REJEITADA : Estado.ACEITA;
            return substituir(aposta.comEstado(estado, 0));
        }

        boolean tick(long tempo, Regras regras) {
            // Recuperar intentos antes de calcular resultados; não basta o jogador estar online.
            for (Aposta aposta : List.copyOf(dados.apostas().values()))
                if (aposta.estado() == Estado.PREPARADA && !confirmar(aposta)) return false;
            long agora = Math.max(0, tempo);
            List<Long> vencidas = new ArrayList<>();
            for (Rodada rodada : dados.rodadas().values())
                if (rodada.resultado() == null && venceu(rodada.dia(), rodada.horario(), agora)) vencidas.add(rodada.dia());
            long diaHoje = Math.floorDiv(agora, 24000L);
            // Saltos não inventam milhares de resultados vazios: somente hoje e dias com bilhetes.
            if (diaHoje >= Math.floorDiv(dados.maiorTempo(), 24000L)
                    && Math.floorMod(agora, 24000L) >= regras.horario() && diaHoje > dados.ultimoDiaSorteado()
                    && !vencidas.contains(diaHoje) && !dados.rodadas().containsKey(diaHoje)) vencidas.add(diaHoje);
            vencidas.sort(Long::compare);
            for (long dia : vencidas) {
                Rodada rodada = dados.rodadas().get(dia);
                int horario = rodada == null ? regras.horario() : rodada.horario();
                int numero = sorteador.getAsInt();
                if (numero < 1 || numero > 100) throw new IllegalStateException("Sorteador produziu dezena inválida");
                Resultado resultado = new Resultado(dia, horario, grupoDaDezena(numero), numero);
                Map<Long, Rodada> rodadas = new HashMap<>(dados.rodadas());
                rodadas.put(dia, new Rodada(dia, horario, resultado));
                // Resultado único é durável antes de aparecer ao cliente ou creditar qualquer prêmio.
                if (!persistir(new Documento(1, Math.max(dados.maiorTempo(), agora),
                        Math.max(dados.ultimoDiaSorteado(), dia), dados.apostas(), rodadas))) return false;
            }
            for (Aposta aposta : List.copyOf(dados.apostas().values())) {
                if (aposta.estado() != Estado.ACEITA && aposta.estado() != Estado.PREMIO_PENDENTE) continue;
                Rodada rodada = dados.rodadas().get(aposta.dia());
                if (rodada == null || rodada.resultado() == null) continue;
                Resultado resultado = rodada.resultado();
                boolean venceu = aposta.tipo() == Tipo.GRUPO ? aposta.grupo() == resultado.grupo() : aposta.dezena() == resultado.dezena();
                if (!venceu) {
                    if (!substituir(aposta.comEstado(Estado.PERDEU, 0))) return false;
                    continue;
                }
                long premio = (long) aposta.valor() * aposta.multiplicador();
                // Não alterar o saldo antes de registrar o direito ao prêmio.
                if (aposta.estado() == Estado.ACEITA) {
                    aposta = aposta.comEstado(Estado.PREMIO_PENDENTE, premio);
                    if (!substituir(aposta)) return false;
                }
                var pagamento = dinheiro.transacionar(operacao("premio", aposta.id()), aposta.jogador(), premio);
                if (pagamento == PlayerMoney.Movimento.APLICADO || pagamento == PlayerMoney.Movimento.REPETIDO) {
                    if (!substituir(aposta.comEstado(Estado.PAGA, premio))) return false;
                } else if (pagamento != PlayerMoney.Movimento.SALDO_CHEIO) return false;
            }
            return true;
        }

        Snapshot snapshot(long tempo, UUID jogador, Regras regras) {
            long dia = proximoDia(tempo, regras);
            Rodada rodada = dados.rodadas().get(dia);
            List<Aposta> apostas = dados.apostas().values().stream().filter(a -> a.jogador().equals(jogador))
                    .sorted(Comparator.comparingLong(Aposta::dia).reversed().thenComparing(a -> a.id().toString()))
                    .limit(80).toList();
            List<Resultado> resultados = dados.rodadas().values().stream().filter(r -> r.resultado() != null)
                    .map(Rodada::resultado).sorted(Comparator.comparingLong(Resultado::dia).reversed()).limit(7).toList();
            return new Snapshot(dia, rodada == null ? regras.horario() : rodada.horario(), dinheiro.get(jogador),
                    regras.minimo(), regras.maximo(), regras.grupo(), regras.dezena(), regras.limite(), apostas, resultados);
        }

        private long proximoDia(long tempo, Regras regras) {
            long observado = Math.max(Math.max(0, tempo), dados.maiorTempo());
            long dia = Math.floorDiv(observado, 24000L);
            Rodada rodada = dados.rodadas().get(dia);
            int horario = rodada == null ? regras.horario() : rodada.horario();
            if (Math.floorMod(observado, 24000L) >= horario) dia++;
            return Math.max(dia, dados.ultimoDiaSorteado() + 1);
        }
        private static boolean venceu(long dia, int horario, long tempo) {
            long atual = Math.floorDiv(tempo, 24000L);
            return dia < atual || (dia == atual && Math.floorMod(tempo, 24000L) >= horario);
        }
        static UUID operacao(String etapa, UUID bilhete) {
            return UUID.nameUUIDFromBytes(("snc:bicho:" + etapa + ":" + bilhete).getBytes(StandardCharsets.UTF_8));
        }
        private boolean substituir(Aposta aposta) {
            Map<UUID, Aposta> novas = new HashMap<>(dados.apostas());
            novas.put(aposta.id(), aposta);
            return persistir(new Documento(1, dados.maiorTempo(), dados.ultimoDiaSorteado(), novas, dados.rodadas()));
        }
        private boolean persistir(Documento candidato) {
            try {
                PlayerMoney.gravarAtomico(arquivo, candidato);
                dados = candidato;
                return true;
            } catch (IOException e) {
                // Log real, limitado durante ticks contínuos para não inundar o disco.
                long agora = System.nanoTime();
                if (ultimoErroLog == Long.MIN_VALUE || agora - ultimoErroLog > 30_000_000_000L) {
                    ultimoErroLog = agora;
                    IntoxicantesMod.LOGGER.error("[SNC Adventures] Loteria não persistiu em {}; operação permanece recuperável", arquivo, e);
                }
                return false;
            }
        }
        private static void validar(Documento d) throws IOException {
            if (d == null || d.formato() != 1 || d.apostas() == null || d.rodadas() == null
                    || d.maiorTempo() < 0 || d.ultimoDiaSorteado() < -1) throw new IOException("Formato da loteria inválido");
            long ultimoDia = -1;
            for (var entrada : d.rodadas().entrySet()) {
                Rodada r = entrada.getValue();
                if (r == null || r.dia() < 0 || entrada.getKey() != r.dia() || r.horario() < 0 || r.horario() >= 24000)
                    throw new IOException("Rodada inválida");
                Resultado x = r.resultado();
                if (x != null && (x.dia() != r.dia() || x.horarioSorteio() != r.horario()
                        || x.dezena() < 1 || x.dezena() > 100 || x.grupo() != grupoDaDezena(x.dezena())))
                    throw new IOException("Resultado inválido");
                if (x != null) ultimoDia = Math.max(ultimoDia, x.dia());
            }
            if (ultimoDia != d.ultimoDiaSorteado()) throw new IOException("Histórico de sorteios inconsistente");
            for (var entrada : d.apostas().entrySet()) {
                Aposta a = entrada.getValue();
                if (a == null || a.id() == null || !a.id().equals(entrada.getKey()) || a.jogador() == null
                        || a.tipo() == null || a.estado() == null || a.dia() < 0 || a.valor() < 1 || a.multiplicador() < 1
                        || a.grupo() < 1 || a.grupo() > 25 || a.premio() < 0
                        || !d.rodadas().containsKey(a.dia()) || a.horarioSorteio() != d.rodadas().get(a.dia()).horario()
                        || (a.tipo() == Tipo.DEZENA && (a.dezena() < 1 || a.dezena() > 100 || grupoDaDezena(a.dezena()) != a.grupo()))
                        || (a.tipo() == Tipo.GRUPO && a.dezena() != 0)) throw new IOException("Bilhete inválido");
                if (a.estado() == Estado.PERDEU || a.estado() == Estado.PREMIO_PENDENTE || a.estado() == Estado.PAGA) {
                    Resultado x = d.rodadas().get(a.dia()).resultado();
                    if (x == null) throw new IOException("Bilhete liquidado sem resultado");
                    boolean ganhou = a.tipo() == Tipo.GRUPO ? a.grupo() == x.grupo() : a.dezena() == x.dezena();
                    if ((a.estado() == Estado.PERDEU) == ganhou
                            || a.premio() != (ganhou ? (long) a.valor() * a.multiplicador() : 0))
                        throw new IOException("Pagamento de bilhete inconsistente");
                } else if (a.premio() != 0) throw new IOException("Bilhete aberto com prêmio pré-definido");
            }
        }
    }
}
