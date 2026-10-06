package com.intoxicantes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.MinecraftServer;

/**
 * Config do mod: config/intoxicantes.json na raiz da instancia (irma de mods/),
 * criado com padroes no primeiro boot. Deixa calibrar escopeta, cotacao da rua
 * e UV sem recompilar. Valores fora da faixa saneada caem pro padrao — config
 * quebrado nao derruba o mod.
 */
public final class ModConfig {
    ModConfig() {} // package-private: o game test cria instancia com valores de calibracao

    // ==================================================== escopeta
    public int escopetaCooldownTicks = 20;      // 1s entre tiros
    public float escopetaDanoPorBalim = 3.0F;   // 8 balins = ate 24 de perto
    public int escopetaAlcanceMaximo = 24;      // blocos de efetivo
    public int escopetaBalins = 8;
    // v1.2.12: o Gago nao e' serrilha de boss — dano maximo do volley dele
    // contra alvos de vida maxima alta (withers, dragoes, golem...)
    public float escopetaCapDanoBoss = 6.0F;

    // v1.2.32: nivel gun mod — mecanismo pump-action calibravel
    public int escopetaCapacidadeTubo = 5;      // cartuchos no tubo interno
    public int escopetaTicksPorShell = 5;       // ticks entre cada shell na recarga
    public int escopetaTicksPump = 8;           // duracao do ciclo da bomba apos o tiro
    public float escopetaKickPitch = 7.0F;      // graus de chute PRA CIMA na camera
    public float escopetaKickYaw = 1.5F;        // graus de chute lateral (maximo)
    public float escopetaAdsFov = 0.8F;         // multiplicador de FOV no ADS (0.8 = zoom de 20%)

    // v1.2.33: o revólver .38 (três oitão) — mesmo padrão gun mod da 12
    public int revolverCooldownTicks = 14;      // tempo entre tiros (cascavel rápido)
    public float revolverDano = 7.0F;           // por bala (6 no tambor = até 42)
    public int revolverAlcanceMaximo = 30;      // blocos de efetivo (mais preciso que a 12)
    public int revolverTicksPorShell = 6;       // ticks entre cada shell na recarga
    public int revolverTicksFecho = 5;          // duração do fecho do ferrolho (pós-tiro/recarga/giro)
    public float revolverKickPitch = 5.0F;      // graus de chute PRA CIMA na camera
    public float revolverKickYaw = 1.2F;        // graus de chute lateral (maximo)
    public float revolverAdsFov = 0.85F;        // multiplicador de FOV no ADS (0.85 = zoom de 15%)

    // ==================================================== cotacao da rua
    public boolean cotacaoFlutuante = true;     // false = Traficante fixo
    public float cotacaoMinima = 0.7F;          // -30%
    public float cotacaoMaxima = 1.4F;          // +40%

    // ==================================================== maturacao UV
    public float uvChanceMaturacao = 0.35F;     // chance por randomTick no UV max
    public boolean uvCueMaturacao = true;       // som + particulas ao amadurecer
    public int uvRaio = 2;                      // raio de acao da lampada (2 = 5x5x5)

    // ==================================================== embriaguez (v1.2.10)
    public int embriaguezDecaySegundos = 60;    // 1 nivel a cada 60s de vida limpa
    public int embriaguezLimiarFonar = 3;       // a partir daqui o chat sai embaralhado
    public int embriaguezLimiarHic = 5;         // a partir daqui vem soluço audivel
    public int embriaguezCap = 7;               // teto do nivel de embriaguez
    public boolean embriaguezTrancaTeto = true; // no teto o corpo devolve o gole (refluxo)
    // v1.2.13: o refinamento supimpa — ressaca, cambaleio e a vantagem do Gago
    public int embriaguezRessacaSegundos = 90;  // voltar a zero deixa de ressaca (0 = desligada)
    public boolean embriaguezCambaleio = true;  // no nivel do soluco: empurrao aleatorio
    public int embriaguezMarkup = 10;           // % a mais por nivel acima do limiar de fala (teto 30)

    // ==================================================== destilaria (v1.2.50)
    // Escala de tempo de TODAS as etapas da cadeia (spec 4: tempos divertidos,
    // não reais). 1.0 = padrão; 0.1 = teste rápido; 2.0 = paciencia de monge.
    public float bebidaVelocidade = 1.0F;
    // Garrafas por lote pronto do barril (spec 14: 1 lote → várias garrafas)
    public int bebidaGarrafasPorLote = 4;

    // ==================================================== guia (v1.2.51)
    // Entrega automática do Guia do SNC Adventures na 1ª entrada do jogador.
    // Servidor pode desligar; o livro continua obtível pelo CRAFT (livro + R$).
    public boolean guiaNaPrimeiraEntrada = true;

    // Peru: sorteio pelo relógio do overworld, sem dinheiro ou relógio externos.
    public int bichoHorarioSorteioTicks = 12000;
    public int bichoApostaMinima = 1;
    public int bichoApostaMaxima = 1000;
    public int bichoMultiplicadorGrupo = 19;
    public int bichoMultiplicadorDezena = 75;
    public int bichoMaxApostasPorSorteio = 20;
    public boolean peruSpawnAtivo = true;
    public int peruMaximoNoMundo = 3;
    public int peruCooldownTicks = 24000;
    public int peruDistanciaMinima = 32;
    public int peruDistanciaMaxima = 72;
    public int peruSeparacaoRegioes = 256;

    // ==================================================== saude (v1.2.54, calibrada na v1.2.56)
    // SEDE: 1 ponto cai a cada `saudeSedeSegundos` de vida limpa (multiplicado
    // por corrida/Nether/bêbado). Zerada = fraqueza/lentidão/nausea, NUNCA dano.
    // 75s = ~2h parado, ~1h correndo, ~30 min na bebedeira correndo: dá pra
    // perceber a barra sem ela dominar a partida (v1.2.55: 60).
    public int saudeSedeSegundos = 75;
    // VICIO: acima deste nível o fregues é dependente (a síndrome de abstinência liga).
    public int saudeVicioLimiar = 30;
    // ABSTINENCIA: segundos limpos até o PICO da síndrome (metade = início,
    // 2x = grave, 4x = colapso; o colapso NUNCA mata — para em 1 coração).
    // 150s: início aos 75s, colapso aos 10 min — dá tempo de correr atrás da
    // dose ou do detox sem o mundo acabar (v1.2.55: 90 = tremia em 45s kkkk).
    public int saudeAbstinenciaSegundos = 150;
    // MARKUP DO DESPERADO: % de markup pro fregues em abstinência comprando no
    // Gago (ele aceita qualquer preço kkkk). 0 = desligado. Vira o markup MAIOR
    // entre este e o da embriaguez.
    public int saudeMarkupDesesperado = 30;

    private static ModConfig instancia;
    /** Escala extraimposta (game tests); null = usa o valor configurado.
     *  STATIC de propósito (v1.2.51): os game tests rodam em PARALELO no mesmo
     *  servidor — um teste setava a escala enquanto outro zerava, e a máquina
     *  do primeiro calculava a duração errada (as 4 cadeias de bebida falhavam
     *  por isso). Como static, sobrevive também à recarga do config. */
    private static Float velocidadeTeste;

    /** Game tests: acelera TODOS os processos de bebida (x20 = 0.05). */
    public static void setVelocidadeTeste(Float escala) {
        velocidadeTeste = escala;
    }

    /** Valor atual do gancho — pra teste que mexe nele RESTAURAR, não zerar. */
    public static Float escalaTesteAtual() {
        return velocidadeTeste;
    }

    /** A velocidade efetiva: o gancho de teste vence o config. */
    public float velocidadeEfetiva() {
        return velocidadeTeste != null ? velocidadeTeste : bebidaVelocidade;
    }

    /**
     * v1.2.51 — SEGUNDOS de design → TICKS reais. As durações do ProcessosBebida
     * são declaradas em segundos (600 = 10 min), mas os consumidores usavam o
     * número DIRETO como ticks (600 ticks = 30 s): tudo 20× mais rápido que o
     * planejado — o "barril envelhece rápido demais" do playtest.
     */
    public static int ticksDeSegundos(float segundos) {
        return Math.max(5, Math.round(segundos * 20F * ModConfig.get().velocidadeEfetiva()));
    }

    public static ModConfig get() {
        if (instancia == null) {
            instancia = new ModConfig(); // fallback seguro antes do load
        }
        return instancia;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Chamado no boot do servidor: carrega ou cria config/intoxicantes.json. */
    public static void init(MinecraftServer server) {
        File mundo = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        // v1.2.24: achar a RAIZ DA INSTANCIA de verdade (a pasta que contem
        // mods/ e saves/). A conta antiga (subir 2 diretorios) quebrava quando
        // o mundo tinha espaco no nome + a raiz ficava em outro nivel: o
        // config nascia em saves/config/ e ninguem achava pra editar.
        File raiz = raizDaInstancia(mundo);
        File arquivo = new File(raiz, "config" + File.separator + "intoxicantes.json");
        if (arquivo.exists()) {
            try {
                ModConfig lida = GSON.fromJson(Files.readString(arquivo.toPath()), ModConfig.class);
                if (lida != null) {
                    instancia = lida;
                    instancia.sanear();
                }
            } catch (Exception e) {
                System.err.println("[Intoxicantes] config quebrado, usando padroes: " + e);
                instancia = new ModConfig();
            }
        } else {
            instancia = new ModConfig();
            try {
                Files.createDirectories(arquivo.getParentFile().toPath());
                Files.writeString(arquivo.toPath(), GSON.toJson(instancia));
            } catch (IOException e) {
                System.err.println("[Intoxicantes] nao consegui criar o config: " + e);
            }
        }
    }

    /**
     * Sobe de .../saves/<mundo> ate achar a pasta que contem mods/ E saves/
     * (a raiz da instancia — irma de mods/). Fallback: a regra antiga de
     * subir 2 niveis (dev/runner em que o layout e diferente).
     */
    private static File raizDaInstancia(File mundo) {
        File atual = mundo.getParentFile(); // .../saves
        while (atual != null) {
            File pai = atual.getParentFile();
            if (pai != null && new File(pai, "mods").isDirectory()
                    && new File(pai, "saves").isDirectory()) {
                return pai;
            }
            atual = pai;
        }
        // fallback (o comportamento antigo): sobe 2 diretorios do mundo
        return mundo.getParentFile() != null && mundo.getParentFile().getParentFile() != null
                ? mundo.getParentFile().getParentFile()
                : mundo.getParentFile();
    }

    /** Valores fora de faixa voltam pro padrao. */
    private void sanear() {
        escopetaCooldownTicks = (int) clamp(escopetaCooldownTicks, 5, 100);
        escopetaDanoPorBalim = (float) clamp(escopetaDanoPorBalim, 0.5, 20.0);
        escopetaAlcanceMaximo = (int) clamp(escopetaAlcanceMaximo, 4, 64);
        escopetaBalins = (int) clamp(escopetaBalins, 1, 16);
        escopetaCapDanoBoss = (float) clamp(escopetaCapDanoBoss, 1.0, 50.0);
        escopetaCapacidadeTubo = (int) clamp(escopetaCapacidadeTubo, 1, 8);
        escopetaTicksPorShell = (int) clamp(escopetaTicksPorShell, 1, 20);
        escopetaTicksPump = (int) clamp(escopetaTicksPump, 1, 40);
        escopetaKickPitch = (float) clamp(escopetaKickPitch, 0.0, 30.0);
        escopetaKickYaw = (float) clamp(escopetaKickYaw, 0.0, 15.0);
        escopetaAdsFov = (float) clamp(escopetaAdsFov, 0.3, 1.0);
        revolverCooldownTicks = (int) clamp(revolverCooldownTicks, 4, 60);
        revolverDano = (float) clamp(revolverDano, 1.0, 30.0);
        revolverAlcanceMaximo = (int) clamp(revolverAlcanceMaximo, 4, 64);
        revolverTicksPorShell = (int) clamp(revolverTicksPorShell, 1, 20);
        revolverTicksFecho = (int) clamp(revolverTicksFecho, 1, 20);
        revolverKickPitch = (float) clamp(revolverKickPitch, 0.0, 30.0);
        revolverKickYaw = (float) clamp(revolverKickYaw, 0.0, 15.0);
        revolverAdsFov = (float) clamp(revolverAdsFov, 0.3, 1.0);
        cotacaoMinima = (float) clamp(cotacaoMinima, 0.3, 1.0);
        cotacaoMaxima = (float) clamp(cotacaoMaxima, 1.0, 3.0);
        uvChanceMaturacao = (float) clamp(uvChanceMaturacao, 0.05, 1.0);
        uvRaio = (int) clamp(uvRaio, 1, 6);
        embriaguezDecaySegundos = (int) clamp(embriaguezDecaySegundos, 10, 600);
        embriaguezLimiarFonar = (int) clamp(embriaguezLimiarFonar, 1, 20);
        embriaguezLimiarHic = (int) clamp(embriaguezLimiarHic, 2, 20);
        embriaguezCap = (int) clamp(embriaguezCap, 2, 20);
        embriaguezRessacaSegundos = (int) clamp(embriaguezRessacaSegundos, 0, 600);
        embriaguezMarkup = (int) clamp(embriaguezMarkup, 0, 30);
        // coerencia entre limiares: fala embaralha antes do soluço, ninguem passa do teto
        embriaguezLimiarHic = Math.max(embriaguezLimiarHic, embriaguezLimiarFonar + 1);
        embriaguezLimiarFonar = Math.min(embriaguezLimiarFonar, embriaguezCap);
        embriaguezLimiarHic = Math.min(embriaguezLimiarHic, embriaguezCap + 1);
        guiaNaPrimeiraEntrada = Boolean.TRUE.equals(guiaNaPrimeiraEntrada);
        bichoHorarioSorteioTicks = Math.floorMod(bichoHorarioSorteioTicks, 24000);
        bichoApostaMinima = (int) clamp(bichoApostaMinima, 1, 100000);
        bichoApostaMaxima = (int) clamp(bichoApostaMaxima, bichoApostaMinima, 100000);
        bichoMultiplicadorGrupo = (int) clamp(bichoMultiplicadorGrupo, 1, 10000);
        bichoMultiplicadorDezena = (int) clamp(bichoMultiplicadorDezena, 1, 10000);
        bichoMaxApostasPorSorteio = (int) clamp(bichoMaxApostasPorSorteio, 1, 100);
        peruMaximoNoMundo = (int) clamp(peruMaximoNoMundo, 1, 8);
        peruCooldownTicks = (int) clamp(peruCooldownTicks, 1200, 240000);
        peruDistanciaMinima = (int) clamp(peruDistanciaMinima, 25, 64);
        peruDistanciaMaxima = (int) clamp(peruDistanciaMaxima, peruDistanciaMinima + 8, 112);
        peruSeparacaoRegioes = (int) clamp(peruSeparacaoRegioes, 128, 2048);
        // v1.2.54: saúde — sede de 10s a 10min, limiar de vício 10..100, síndrome 30s..30min
        saudeSedeSegundos = (int) clamp(saudeSedeSegundos, 10, 600);
        saudeVicioLimiar = (int) clamp(saudeVicioLimiar, 10, 100);
        saudeAbstinenciaSegundos = (int) clamp(saudeAbstinenciaSegundos, 30, 1800);
        saudeMarkupDesesperado = (int) clamp(saudeMarkupDesesperado, 0, 100);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ==================================================== helpers de leitura
    /** Fator de cotacao do dia (1.0 se a flutuacao estiver desligada). */
    public float fatorCotacao(long seedDia, String produto) {
        if (!cotacaoFlutuante) {
            return 1.0F;
        }
        java.util.Random r = new java.util.Random(seedDia * 31 + produto.hashCode());
        return cotacaoMinima + r.nextFloat() * (cotacaoMaxima - cotacaoMinima);
    }
}
