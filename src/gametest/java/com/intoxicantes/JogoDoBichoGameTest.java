package com.intoxicantes;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Regressões em pastas temporárias: não alteram saldos/relógio do mundo da suíte. */
public class JogoDoBichoGameTest {
    private static final JogoDoBicho.Regras REGRAS = new JogoDoBicho.Regras(12000, 1, 1000, 19, 75, 20);

    @GameTest
    public void catalogoCobreCemDezenasEZeroZero(GameTestHelper helper) {
        var dezenas = new HashSet<Integer>();
        helper.assertTrue(JogoDoBicho.grupos().size() == 25, "Existem 25 grupos");
        for (var grupo : JogoDoBicho.grupos()) {
            helper.assertTrue(grupo.ultimaDezena() - grupo.primeiraDezena() == 3, "Cada grupo possui quatro dezenas");
            for (int n = grupo.primeiraDezena(); n <= grupo.ultimaDezena(); n++) {
                helper.assertTrue(dezenas.add(n), "Dezenas não se repetem entre grupos");
                helper.assertTrue(JogoDoBicho.grupoDaDezena(n) == grupo.numero(), "Tabela de grupo corresponde à dezena");
            }
        }
        helper.assertTrue(dezenas.size() == 100 && JogoDoBicho.grupoDaDezena(0) == 25
                && JogoDoBicho.grupoDaDezena(50) == 13, "00 pertence à vaca; 50 ao galo");
        helper.succeed();
    }

    @GameTest
    public void sorteioUnicoPagaTodosOsJogadoresOffline(GameTestHelper helper) {
        isolado(helper, pasta -> {
            var money = new PlayerMoney.Livro(pasta);
            AtomicInteger sorteios = new AtomicInteger();
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> { sorteios.incrementAndGet(); return 50; });
            UUID grupo = UUID.randomUUID(), dezena = UUID.randomUUID(), perdeu = UUID.randomUUID();
            money.set(grupo, 1000); money.set(dezena, 1000); money.set(perdeu, 1000);
            var a = jogo.apostar(11000, UUID.randomUUID(), grupo, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS);
            var b = jogo.apostar(11000, UUID.randomUUID(), dezena, JogoDoBicho.Tipo.DEZENA, 13, 50, 100, REGRAS);
            var c = jogo.apostar(11000, UUID.randomUUID(), perdeu, JogoDoBicho.Tipo.GRUPO, 20, 0, 150, REGRAS);
            helper.assertTrue(a.status() == JogoDoBicho.Status.ACEITA && b.status() == JogoDoBicho.Status.ACEITA
                    && c.status() == JogoDoBicho.Status.ACEITA, "Bilhetes debitados e registrados");
            helper.assertTrue(jogo.tick(12000, REGRAS), "Sorteio liquidou");
            helper.assertTrue(sorteios.get() == 1 && money.get(grupo) == 2800 && money.get(dezena) == 8400
                    && money.get(perdeu) == 850, "Único resultado; multiplicadores brutos 19x/75x e perdas definitivas");
            var outro = jogo.snapshot(12000, perdeu, REGRAS);
            helper.assertTrue(outro.resultados().equals(jogo.snapshot(12000, grupo, REGRAS).resultados()),
                    "Todos consultam o mesmo resultado");
            helper.assertTrue(outro.apostas().size() == 1 && outro.apostas().getFirst().jogador().equals(perdeu),
                    "Consulta de bilhetes é privada por jogador");
            var recarregado = new PlayerMoney.Livro(pasta);
            var reload = new JogoDoBicho.Loteria(pasta, recarregado, () -> { throw new AssertionError("Não sortear novamente"); });
            helper.assertTrue(reload.tick(12000, REGRAS) && recarregado.get(dezena) == 8400, "Restart não duplica prêmio");
        });
    }

    @GameTest
    public void horarioLimiteSonoERecuoNaoReabremSorteio(GameTestHelper helper) {
        isolado(helper, pasta -> {
            var money = new PlayerMoney.Livro(pasta);
            UUID player = UUID.randomUUID(); money.set(player, 1000);
            AtomicInteger sorteios = new AtomicInteger();
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> { sorteios.incrementAndGet(); return 50; });
            var antes = jogo.apostar(11999, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 20, 0, 10, REGRAS);
            var depois = jogo.apostar(12000, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 20, 0, 10, REGRAS);
            helper.assertTrue(antes.aposta().dia() == 0 && depois.aposta().dia() == 1 && sorteios.get() == 1,
                    "Bilhete no tick do fechamento participa somente do próximo sorteio");
            jogo.tick(24000, REGRAS); // jogador dorme e acorda às 06h do dia 1
            helper.assertTrue(sorteios.get() == 1, "Amanhecer não antecipa o sorteio das 18h");
            jogo.tick(36000, REGRAS); jogo.tick(0, REGRAS); jogo.tick(12000, REGRAS);
            var recuo = jogo.apostar(100, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 20, 0, 10, REGRAS);
            helper.assertTrue(sorteios.get() == 2 && recuo.aposta().dia() == 2,
                    "Relógio para trás nunca repete resultado nem vende bilhete para rodada encerrada");
        });
    }

    @GameTest
    public void saltoDeCemDiasLiquidaBilhetesSemSorteiosVaziosIntermediarios(GameTestHelper helper) {
        isolado(helper, pasta -> {
            var money = new PlayerMoney.Livro(pasta);
            UUID player = UUID.randomUUID(); money.set(player, 1000);
            AtomicInteger sorteios = new AtomicInteger();
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> { sorteios.incrementAndGet(); return 50; });
            jogo.apostar(11000, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS);
            long depois = 100L * 24000 + 13000;
            helper.assertTrue(jogo.tick(depois, REGRAS) && jogo.tick(depois, REGRAS), "Saltos e tick repetido aceitos");
            var snap = jogo.snapshot(depois, player, REGRAS);
            helper.assertTrue(sorteios.get() == 2 && snap.resultados().size() == 2
                    && snap.resultados().getFirst().dia() == 100 && money.get(player) == 2800,
                    "Só dia com bilhete vencido e dia atual geram resultados; pagamento ocorre uma vez");
        });
    }

    @GameTest
    public void idempotenciaLimitesERequisicaoAdulterada(GameTestHelper helper) {
        isolado(helper, pasta -> {
            var money = new PlayerMoney.Livro(pasta);
            UUID player = UUID.randomUUID(); money.set(player, 1000);
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> 50);
            UUID id = UUID.randomUUID();
            var regras = new JogoDoBicho.Regras(12000, 1, 1000, 19, 75, 3);
            jogo.apostar(0, id, player, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras);
            helper.assertTrue(jogo.apostar(0, id, player, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras).status()
                    == JogoDoBicho.Status.REPETIDA && money.get(player) == 990, "Retry não debita duas vezes");
            helper.assertTrue(jogo.apostar(0, id, player, JogoDoBicho.Tipo.GRUPO, 2, 0, 10, regras).status()
                    == JogoDoBicho.Status.CONFLITO, "UUID de confirmação não autoriza outro bilhete");
            helper.assertTrue(jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.DEZENA, 1, 50, 10, regras).status()
                    == JogoDoBicho.Status.INVALIDA, "Cliente não mistura grupo e dezena");
            jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras);
            jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras);
            helper.assertTrue(jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras).status()
                    == JogoDoBicho.Status.LIMITE && money.get(player) == 970, "Limite é server-side");
            UUID semSaldo = UUID.randomUUID(), pedido = UUID.randomUUID();
            helper.assertTrue(jogo.apostar(0, pedido, semSaldo, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras).status()
                    == JogoDoBicho.Status.SEM_SALDO, "Sem saldo não há bilhete válido");
            money.set(semSaldo, 100);
            helper.assertTrue(jogo.apostar(0, pedido, semSaldo, JogoDoBicho.Tipo.GRUPO, 1, 0, 10, regras).status()
                    == JogoDoBicho.Status.SEM_SALDO && money.get(semSaldo) == 100, "Pedido rejeitado não vira débito tardio");
        });
    }

    @GameTest
    public void premioQueNaoCabePermanecePendenteAteSaldoLiberar(GameTestHelper helper) {
        isolado(helper, pasta -> {
            var money = new PlayerMoney.Livro(pasta);
            UUID player = UUID.randomUUID(); money.set(player, Integer.MAX_VALUE - 100);
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> 50);
            jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS);
            helper.assertTrue(jogo.tick(12000, REGRAS), "Overflow não derruba o sorteio");
            var aposta = jogo.snapshot(12000, player, REGRAS).apostas().getFirst();
            helper.assertTrue(aposta.estado() == JogoDoBicho.Estado.PREMIO_PENDENTE && aposta.premio() == 1900
                    && money.get(player) == Integer.MAX_VALUE - 200, "Prêmio integral preservado sem truncar");
            money.set(player, 1000);
            var recarregado = new PlayerMoney.Livro(pasta);
            var reload = new JogoDoBicho.Loteria(pasta, recarregado, () -> 99);
            reload.tick(12000, REGRAS); reload.tick(12000, REGRAS);
            helper.assertTrue(recarregado.get(player) == 2900
                    && reload.snapshot(12000, player, REGRAS).apostas().getFirst().estado() == JogoDoBicho.Estado.PAGA,
                    "Prêmio offline paga integralmente uma vez após liberar saldo");
        });
    }

    @GameTest
    public void migracaoPreservaSaldosFiadoERecibosSeparadosPorMundo(GameTestHelper helper) {
        isolado(helper, pasta -> {
            UUID player = UUID.randomUUID();
            String legado = "{\"" + player + "\":1234}";
            String fiado = "{\"" + player + "\":75}";
            Files.writeString(pasta.resolve("intoxicantes_money.json"), legado, StandardCharsets.UTF_8);
            Files.writeString(pasta.resolve("intoxicantes_fiado.json"), fiado, StandardCharsets.UTF_8);
            var money = new PlayerMoney.Livro(pasta);
            helper.assertTrue(money.get(player) == 1234 && Files.readString(pasta.resolve("intoxicantes_fiado.json")).equals(fiado)
                    && Files.readString(pasta.resolve("intoxicantes_money.legacy-backup.json")).equals(legado),
                    "Migração mantém todos os saldos, fiados e cópia original");
            UUID operacao = UUID.randomUUID();
            money.transacionar(operacao, player, -100);
            var reload = new PlayerMoney.Livro(pasta);
            helper.assertTrue(reload.transacionar(operacao, player, -100) == PlayerMoney.Movimento.REPETIDO
                    && reload.get(player) == 1134, "Recibo sobrevive à leitura do formato migrado");
            var novoMundo = new PlayerMoney.Livro(pasta.resolve("outro-mundo"));
            helper.assertTrue(novoMundo.get(player) == 0, "Outro mundo não herda saldo do anterior");
        });
    }

    @GameTest
    public void quedaEntreDinheiroEConfirmacaoRecuperaSemDuplicar(GameTestHelper helper) {
        isolado(helper, pasta -> {
            UUID player = UUID.randomUUID(), id = UUID.randomUUID();
            var money = new PlayerMoney.Livro(pasta); money.set(player, 1000);
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> 50);
            jogo.apostar(0, id, player, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS);
            mudarEstado(pasta, id, "PREPARADA", 0); // débito está gravado, confirmação não chegou ao disco
            var reloadMoney = new PlayerMoney.Livro(pasta);
            var reload = new JogoDoBicho.Loteria(pasta, reloadMoney, () -> 50);
            helper.assertTrue(reload.tick(0, REGRAS) && reloadMoney.get(player) == 900,
                    "Recuperação do intento não repete débito já gravado");
            reload.tick(12000, REGRAS);
            helper.assertTrue(reloadMoney.get(player) == 2800, "Primeiro pagamento ocorreu");
            mudarEstado(pasta, id, "PREMIO_PENDENTE", 1900); // crédito já salvo, confirmação não chegou ao disco
            var finalMoney = new PlayerMoney.Livro(pasta);
            var finalReload = new JogoDoBicho.Loteria(pasta, finalMoney, () -> { throw new AssertionError("Resultado já é persistente"); });
            helper.assertTrue(finalReload.tick(12000, REGRAS) && finalMoney.get(player) == 2800
                    && finalReload.snapshot(12000, player, REGRAS).apostas().getFirst().estado() == JogoDoBicho.Estado.PAGA,
                    "Recuperação do prêmio não duplica crédito e conserva resultado");
        });
    }

    @GameTest
    public void falhaDePersistenciaNaoDescontaNemPerdePremio(GameTestHelper helper) {
        isolado(helper, pasta -> {
            UUID player = UUID.randomUUID();
            var money = new PlayerMoney.Livro(pasta); money.set(player, 1000);
            var jogo = new JogoDoBicho.Loteria(pasta, money, () -> 50);
            Path bloquearLoteria = pasta.resolve("intoxicantes_bicho.json.pending");
            Files.createDirectory(bloquearLoteria);
            var falha = jogo.apostar(0, UUID.randomUUID(), player, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS);
            helper.assertTrue(falha.status() == JogoDoBicho.Status.ERRO_PERSISTENCIA && money.get(player) == 1000,
                    "Intento não gravado nunca desconta dinheiro");
            Files.delete(bloquearLoteria);
            UUID id = UUID.randomUUID();
            Path bloquearDinheiro = pasta.resolve("intoxicantes_money.json.pending");
            Files.createDirectory(bloquearDinheiro);
            helper.assertTrue(jogo.apostar(0, id, player, JogoDoBicho.Tipo.GRUPO, 13, 0, 100, REGRAS).status()
                    == JogoDoBicho.Status.ERRO_PERSISTENCIA && money.get(player) == 1000,
                    "Falha do ledger mantém intento recuperável e saldo intacto");
            Files.delete(bloquearDinheiro);
            helper.assertTrue(jogo.tick(0, REGRAS) && money.get(player) == 900, "Intento recupera uma única cobrança");
            Files.createDirectory(bloquearLoteria);
            helper.assertTrue(!jogo.tick(12000, REGRAS) && money.get(player) == 900
                    && jogo.snapshot(12000, player, REGRAS).resultados().isEmpty(),
                    "Resultado não persistido nunca fica público nem autoriza crédito");
            Files.delete(bloquearLoteria);
            Files.createDirectory(bloquearDinheiro);
            helper.assertTrue(!jogo.tick(12000, REGRAS) && money.get(player) == 900
                    && jogo.snapshot(12000, player, REGRAS).apostas().getFirst().estado() == JogoDoBicho.Estado.PREMIO_PENDENTE,
                    "Falha ao creditar não apaga o direito ao prêmio");
            Files.delete(bloquearDinheiro);
            helper.assertTrue(jogo.tick(12000, REGRAS) && money.get(player) == 2800, "Prêmio recupera após disco voltar");
        });
    }

    @GameTest
    public void dinheiroCorrompidoNaoViraSaldoVazio(GameTestHelper helper) {
        isolado(helper, pasta -> {
            Path arquivo = pasta.resolve("intoxicantes_money.json");
            Files.writeString(arquivo, "{arquivo interrompido");
            boolean recusou = false;
            try { new PlayerMoney.Livro(pasta); }
            catch (IllegalStateException esperado) { recusou = true; }
            helper.assertTrue(recusou && Files.readString(arquivo).equals("{arquivo interrompido"),
                    "Corrupção é visível e o arquivo original permanece intocado");
        });
    }

    private static void mudarEstado(Path pasta, UUID id, String estado, long premio) throws Exception {
        Path arquivo = pasta.resolve("intoxicantes_bicho.json");
        var documento = JsonParser.parseString(Files.readString(arquivo)).getAsJsonObject();
        var aposta = documento.getAsJsonObject("apostas").getAsJsonObject(id.toString());
        aposta.addProperty("estado", estado); aposta.addProperty("premio", premio);
        Files.writeString(arquivo, documento.toString(), StandardCharsets.UTF_8);
    }
    @FunctionalInterface private interface Cenario { void executar(Path pasta) throws Exception; }
    private static void isolado(GameTestHelper helper, Cenario cenario) {
        Path pasta = null;
        try {
            pasta = Files.createTempDirectory("snc-bicho-gametest-").toAbsolutePath().normalize();
            cenario.executar(pasta);
            helper.succeed();
        } catch (Exception e) { throw new IllegalStateException("Falha na regressão da loteria", e); }
        finally {
            if (pasta != null) {
                try (var arquivos = Files.walk(pasta)) {
                    for (Path p : arquivos.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                } catch (Exception e) { IntoxicantesMod.LOGGER.warn("Não foi possível limpar pasta temporária de teste {}", pasta, e); }
            }
        }
    }
}
