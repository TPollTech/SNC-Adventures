package com.intoxicantes;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/**
 * v1.2.54 — OS TESTES DA SAÚDE: a garantia de que os 3 sistemas conversam e
 * cumprem as regras de ouro:
 *
 *  1. SEDE: drena com o tempo (mais rápido bêbado), debuff sem dano, e as
 *     fontes de água hidratam (garrafa/coco/detox — o detox CURA órgãos).
 *  2. VICIO: dose repetida sobe a dependência, síndrome entra em estágios,
 *     o COLAPSO NUNCA MATA (para em 1 coração — regra de ouro do mod) e a
 *     recaída zera a cura; dia limpo derrete o vício.
 *  3. EFEITOS POR DROGA: cada substância aplica o SEU efeito assinatura com
 *     a intensidade da janela (dose repetida = viagem mais forte) e a QUEDA
 *     chega no fim da viagem.
 */
public class SaudeGameTest {

    // ==================================================== SEDE

    @GameTest
    public void sedeDrenaComOTempoEDesidrataMaisBebado(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_sede.json"));

        // limpo: 1 ponto a cada saudeSedeSegundos (config) — o teste lê o
        // config: sem número mágico quebrando quando o balanceamento muda
        SaudeData.alterarHidratacao(player, -40); // 60/100
        int ciclo = ModConfig.get().saudeSedeSegundos;
        for (int i = 0; i < ciclo; i++) {
            SaudeSystem.logicaPorSegundo(player);
        }
        helper.assertTrue(SaudeData.hidratacao(player) == 59,
                "Sede drena 1 ponto por ciclo limpo (59, veio " + SaudeData.hidratacao(player) + ")");

        // bêbado: o fator 2x do Embriaguez seca mais rápido (2 pontos no mesmo ciclo)
        Embriaguez.setNivelTeste(player, 5); // acima do limiar da fala (3)
        for (int i = 0; i < ciclo; i++) {
            SaudeSystem.logicaPorSegundo(player);
        }
        helper.assertTrue(SaudeData.hidratacao(player) == 57,
                "Bêbado desidrata no dobro (57, veio " + SaudeData.hidratacao(player) + ")");
        Embriaguez.setNivelTeste(player, 0);
        helper.succeed();
    }

    @GameTest
    public void sedeZeradaDaDebuffMasNuncaDano(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_debuff.json"));

        SaudeData.alterarHidratacao(player, -SaudeData.HIDRATACAO_MAX); // 0
        SaudeSystem.logicaPorSegundo(player);
        float vidaAntes = player.getHealth();
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS),
                "Sede zerada dá fraqueza");
        helper.assertTrue(SaudeData.hidratacao(player) == 0, "Hidratação continua em 0");
        // o tick de sede NÃO tira vida (regra da casa: sede castiga, não mata)
        helper.assertTrue(player.getHealth() == vidaAntes,
                "Sede zerada NUNCA causa dano direto");
        // garrafa de água recupera
        SaudeSystem.consumirFonteDeAguaTeste(player, Items.POTION.getDefaultInstance(), 20);
        helper.assertTrue(SaudeData.hidratacao(player) == 20, "Garrafa de água hidrata +20");
        helper.succeed();
    }

    @GameTest
    public void sucoDetoxCuraOsTresOrgaos(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_detox.json"));

        SaudeData.danoDeOrgao(player, SaudeData.Orgao.FIGADO, 500);
        SaudeData.danoDeOrgao(player, SaudeData.Orgao.PULMAO, 400);
        SaudeData.danoDeOrgao(player, SaudeData.Orgao.ESTOMAGO, 300);
        helper.assertTrue(SaudeData.danoFigado(player) == 500, "Dano de fígado acumula");

        // drenar a sede antes (o +35 do detox clampava no teto de 100)
        SaudeData.alterarHidratacao(player, -SaudeData.HIDRATACAO_MAX); // 0

        // um detox: -60 de dano em cada órgão + 35 de hidratação (v1.2.55:
        // 120 era forte demais — a redenção custa tempo e suco agora)
        SaudeSystem.consumirFonteDeAguaTeste(player, IntoxicantesMod.SUCO_DETOX.getDefaultInstance(), 0);
        helper.assertTrue(SaudeData.danoFigado(player) == 440, "Detox cura fígado (500→440)");
        helper.assertTrue(SaudeData.danoPulmao(player) == 340, "Detox cura pulmão (400→340)");
        helper.assertTrue(SaudeData.danoEstomago(player) == 240, "Detox cura estômago (300→240)");
        helper.assertTrue(SaudeData.hidratacao(player) == 35, "Detox hidrata +35");
        helper.succeed();
    }

    // ==================================================== VICIO / ABSTINENCIA

    @GameTest
    public void vicioSobeComDoseEAbstinenciaEntraEmEstagios(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_vicio.json"));

        // a cocaína vicia 12/dose: 3 doses = 36 (acima do limiar 30)
        CatalogoSubstancias.Ficha cocaina = CatalogoSubstancias.porId("cocaina");
        for (int i = 0; i < 3; i++) {
            SaudeSystem.consumir(player, cocaina);
        }
        helper.assertTrue(SaudeData.nivelVicio(player) == 36,
                "3 doses de cocaína = vício 36 (veio " + SaudeData.nivelVicio(player) + ")");
        helper.assertTrue(SaudeData.dosesDaSubstancia(player, "cocaina") == 3,
                "Janela de doses conta as 3");

        // o efeito assinatura ligou (overdrive)
        helper.assertTrue(player.hasEffect(Efeitos.OVERDRIVE),
                "Cocaína aplica o efeito Overdrive");

        // síndrome: 1 estagio em saudeAbstinenciaSegundos/2, 2 em
        // saudeAbstinenciaSegundos (v1.2.56: lê o config, sem número mágico)
        SaudeData.setVicioTeste(player, "cocaina", 50);
        int abc = ModConfig.get().saudeAbstinenciaSegundos;
        avancarTempoLimpo(player, abc / 2);
        helper.assertTrue(SaudeSystem.estagioAbstinencia(player) == 1,
                (abc / 2) + "s limpos = abstinência estágio 1");
        avancarTempoLimpo(player, abc / 2); // abc no total
        helper.assertTrue(SaudeSystem.estagioAbstinencia(player) == 2,
                abc + "s limpos = abstinência estágio 2");
        helper.assertTrue(player.hasEffect(Efeitos.ABSTINENCIA),
                "A síndrome aplica o efeito próprio");
        helper.succeed();
    }

    @GameTest
    public void colapsoDaAbstinenciaNuncaMata(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_colapso.json"));

        SaudeData.setVicioTeste(player, "heroina", 80);
        // direto no estágio 4 (4x saudeAbstinenciaSegundos, do config)
        avancarTempoLimpo(player, 4 * ModConfig.get().saudeAbstinenciaSegundos);
        helper.assertTrue(SaudeSystem.estagioAbstinencia(player) == 4,
                "360s limpos = colapso (estágio 4)");
        float vidaCheia = player.getMaxHealth();

        // muitos colapsos seguidos: a vida desce ATÉ o piso e nunca abaixo
        for (int i = 0; i < 200; i++) {
            SaudeSystem.logicaPorSegundo(player);
        }
        helper.assertTrue(player.getHealth() >= 10.0F,
                "O colapso PARA em 1 coração (vida " + player.getHealth() + ")");
        helper.assertTrue(player.getHealth() < vidaCheia,
                "O colapso machuca de verdade antes do piso");
        helper.assertTrue(player.isAlive(), "O fregues SOBREVIVE ao vício (regra de ouro)");
        helper.succeed();
    }

    @GameTest
    public void recaidaZeraACuraEDiaLimpoDerreteOVicio(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_cura.json"));

        SaudeData.setVicioTeste(player, "cocaina", 60);
        avancarTempoLimpo(player, 100);
        helper.assertTrue(SaudeData.relogioLimpo(player) >= 100, "Relógio limpo corre");

        // RECAÍDA: usar de novo zera o relógio (e a cura)
        SaudeSystem.consumir(player, CatalogoSubstancias.porId("cocaina"));
        helper.assertTrue(SaudeData.relogioLimpo(player) == 0,
                "Recaída zera o relógio limpo");
        helper.assertTrue(SaudeData.curasSeguidas(player) == 0, "Recaída zera os dias limpos");

        // CURA: 1 dia limpo = -10 de vício. 1200 "segundos" fecham o dia do
        // relógio (1200 x 20 ticks = 24000) e o limpo já passa da síndrome aguda
        avancarTempoLimpo(player, 1200);
        helper.assertTrue(SaudeData.curasSeguidas(player) == 1,
                "Dia limpo conta na cura (curas: " + SaudeData.curasSeguidas(player) + ")");
        // a recaída COBROU a dose (60 + 12 do novo uso = 72) e o dia derreteu
        // 10: 72 - 10 = 62. Usar de novo sobe a dependência — o ciclo cruel.
        helper.assertTrue(SaudeData.nivelVicio(player) == 62,
                "Recaída sobe o vício e o dia derrete 10 (72→62, veio "
                        + SaudeData.nivelVicio(player) + ")");
        helper.succeed();
    }

    // ==================================================== EFEITOS POR DROGA

    @GameTest
    public void cadaDrogaAplicaOSeuEfeito(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_efeitos.json"));

        SaudeSystem.consumir(player, CatalogoSubstancias.porId("baseado"));
        helper.assertTrue(player.hasEffect(Efeitos.TRANQUILO), "Baseado → Tranquilo");
        helper.assertFalse(player.hasEffect(Efeitos.VIAGEM), "Baseado NÃO dá viagem de LSD");

        player.removeEffect(Efeitos.TRANQUILO);
        SaudeSystem.consumir(player, CatalogoSubstancias.porId("opio"));
        helper.assertTrue(player.hasEffect(Efeitos.MORNO), "Ópio → Morno");

        player.removeEffect(Efeitos.MORNO);
        SaudeSystem.consumir(player, CatalogoSubstancias.porId("heroina"));
        helper.assertTrue(player.hasEffect(Efeitos.SONHO), "Heroína → Sonho");

        player.removeEffect(Efeitos.SONHO);
        SaudeSystem.consumir(player, CatalogoSubstancias.porId("lsd"));
        helper.assertTrue(player.hasEffect(Efeitos.VIAGEM), "LSD → Viagem");
        helper.assertTrue(player.hasEffect(MobEffects.LEVITATION), "A viagem levita (vanilla)");
        helper.succeed();
    }

    @GameTest
    public void doseRepetidaEscalaAIntensidadeDaViagem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_intensidade.json"));

        CatalogoSubstancias.Ficha lsd = CatalogoSubstancias.porId("lsd");
        SaudeSystem.consumir(player, lsd);
        MobEffectInstance primeira = player.getEffect(Efeitos.VIAGEM);
        helper.assertTrue(primeira.getAmplifier() == 0, "1ª dose = intensidade 1");

        SaudeSystem.consumir(player, lsd);
        SaudeSystem.consumir(player, lsd);
        MobEffectInstance terceira = player.getEffect(Efeitos.VIAGEM);
        helper.assertTrue(terceira.getAmplifier() == 1,
                "3ª dose = intensidade 2 (amplificador 1, veio " + terceira.getAmplifier() + ")");

        // a QUEDA chega no fim da viagem (duração + 15s)
        SaudeSystem.setQuedaTeste(player, 1, "VIAGEM");
        SaudeSystem.logicaPorSegundo(player);
        helper.assertTrue(player.hasEffect(MobEffects.NAUSEA) || player.hasEffect(MobEffects.DARKNESS),
                "A queda da viagem chega (náusea/trevas)");
        helper.succeed();
    }

    @GameTest
    public void janelaDeDosesDecaiComOTempoLimpo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SaudeData.initTeste(new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_janela.json"));

        CatalogoSubstancias.Ficha lsd = CatalogoSubstancias.porId("lsd");
        for (int i = 0; i < 5; i++) {
            SaudeSystem.consumir(player, lsd); // 5 doses = intensidade 3
        }
        helper.assertTrue(SaudeData.dosesDaSubstancia(player, "lsd") == 5,
                "5 doses acumulam na janela");

        // v1.2.56: 5 minutos limpo derrete METADE das doses (piso 1) — a
        // intensidade é memória recente, não reputação vitalícia
        SaudeSystem.expirarJanelaTeste(player, "lsd");
        SaudeSystem.logicaPorSegundo(player);
        helper.assertTrue(SaudeData.dosesDaSubstancia(player, "lsd") == 2,
                "Janela vencida derrete metade (5→2, veio "
                        + SaudeData.dosesDaSubstancia(player, "lsd") + ")");

        // o piso: quem já tinha 1 dose não desce pra 0 (a primeira viagem
        // continua valendo intensidade 1)
        SaudeData.setDosesTeste(player, "lsd", 1);
        SaudeSystem.expirarJanelaTeste(player, "lsd");
        SaudeSystem.logicaPorSegundo(player);
        helper.assertTrue(SaudeData.dosesDaSubstancia(player, "lsd") == 1,
                "O piso é 1 — primeira viagem nunca morre");
        helper.succeed();
    }

    @GameTest
    public void vicioSobreviveAoRelog(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        java.io.File arquivo = new java.io.File(helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "teste_saude_persist.json");
        SaudeData.initTeste(arquivo);

        SaudeData.setVicioTeste(player, "lsd", 42);
        SaudeData.danoDeOrgao(player, SaudeData.Orgao.FIGADO, 250);
        SaudeData.alterarHidratacao(player, -30);
        SaudeData.contarDoseVida(player, "alcool");
        SaudeData.save(); // o "logout"

        // o "relog": limpa memória e recarrega do disco
        SaudeData.initTeste(arquivo);
        SaudeData.loadTeste();

        helper.assertTrue(SaudeData.nivelVicio(player) == 42, "Vício sobrevive ao relog");
        helper.assertTrue(SaudeData.danoFigado(player) == 250, "Órgãos sobrevivem ao relog");
        helper.assertTrue(SaudeData.hidratacao(player) == 70, "Hidratação sobrevive ao relog");
        helper.assertTrue(SaudeData.dosesVida(player, "alcool") == 1, "Histórico sobrevive ao relog");
        helper.succeed();
    }

    // ==================================================== HELPERS

    /** Avança o relógio limpo simulando segundos sem dose (1 chamada = 1s). */
    private static void avancarTempoLimpo(ServerPlayer player, int segundos) {
        for (int i = 0; i < segundos; i++) {
            SaudeSystem.logicaPorSegundo(player);
        }
    }
}
