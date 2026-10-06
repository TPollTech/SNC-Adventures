package com.intoxicantes;

import com.intoxicantes.energia.CabosSuspensos;
import com.intoxicantes.energia.CircuitoEletrico;
import com.intoxicantes.energia.EloCabo;
import com.intoxicantes.energia.Grandezas;
import com.intoxicantes.energia.SNCEnergiesAdapter;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v1.2.70 — A ELETRICIDADE (módulo 2): adapter por reflexão (sem SNC
 * Energies no classpath de teste: tudo devolve 0), cabo que conecta,
 * soquete que veste lâmpada, quadro com entrada SNC Energies REAL
 * (o gerador a carvão do próprio SNC Energies colocado no mundo de teste —
 * presente porque ele é dependência do ambiente de gametests) e a cadeia
 * completa: fogão/gerador → quadro → cabo → interruptor → lâmpada acesa.
 */
public class EletricidadeGameTest {

    // ==================================================== ADAPTER

    /** Sem SNC Energies o adapter NUNCA crasha: tudo 0/false. */
    @GameTest
    public void adapterSemSNCEnergiesNaoCrasha(GameTestHelper helper) {
        helper.assertTrue(SNCEnergiesAdapter.disponivel()
                        == net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("snc_energies"),
                "Adapter availability agrees with the actual loaded companion mod");
        helper.assertTrue(SNCEnergiesAdapter.eFonteValida(null) == false,
                "null BE is not a source");
        helper.assertTrue(SNCEnergiesAdapter.energia(null) == 0L,
                "null storage reads 0 E");
        helper.assertTrue(SNCEnergiesAdapter.extrair(null, 100L) == 0L,
                "null storage extracts 0 E");
        // uma BE vanilla comum não é fonte
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        var fornalha = helper.getLevel().getBlockEntity(
                helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(!SNCEnergiesAdapter.eFonteValida(fornalha),
                "A vanilla furnace is not an SNC Energies source");
        helper.succeed();
    }

    /** A conversão W/V/A (a única do mod, testada uma vez aqui). */
    @GameTest
    public void grandezasConvertemEParaWattsEAmpere(GameTestHelper helper) {
        helper.assertTrue(Grandezas.wattsDe(80L) == 800L,
                "The wood stove's 80 E/t displays as 800 W");
        helper.assertTrue(Grandezas.eTickDe(15L) == 2L,
                "Whole-tick helper rounds upward when required");
        helper.assertTrue(Grandezas.energiaDe(15L, 20L) == 30L
                        && Grandezas.formatarETick(15L).equals("1,5"),
                "Network and meter preserve fractional E/t without inflating actual draw");
        helper.assertTrue(Grandezas.amperes(1270L, Grandezas.V_127) == 10L,
                "1270 W at 127 V is 10 A");
        helper.assertTrue(Grandezas.wattsDoDisjuntor(16L, Grandezas.V_127) == 2032L,
                "A 16 A breaker at 127 V caps at 2032 W");
        helper.assertTrue(Grandezas.wattsDoDisjuntor(10L, Grandezas.V_220) == 2200L,
                "A 10 A breaker at 220 V caps at 2200 W");
        helper.succeed();
    }

    // ==================================================== CABO

    /** O cabo conecta em cabos vizinhos e em fontes SNC Energies reais. */
    @GameTest
    public void caboConectaVizinhosEFonteReal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos vizinho = pos.east();
        level.setBlockAndUpdate(pos, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(vizinho, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        var estado = level.getBlockState(pos);
        helper.assertTrue(estado.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST),
                "Cable connects EAST to another cable");
        helper.assertTrue(!estado.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP),
                "Cable does NOT connect UP to air (no proximity power)");
        // fonte real do SNC Energies (se presente no ambiente de teste)
        if (SNCEnergiesAdapter.disponivel()) {
            var blocoGerador = blocoDoEnergies("coal_generator");
            if (blocoGerador != null) {
                BlockPos fontePos = pos.above();
                level.setBlockAndUpdate(fontePos, blocoGerador.defaultBlockState());
                helper.assertTrue(level.getBlockState(pos).getValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.UP),
                        "Cable connects UP to a real SNC Energies generator");
            }
        }
        helper.succeed();
    }

    /** O bloco do SNC Energies pelo ID (null se o mod não estiver). */
    private static net.minecraft.world.level.block.Block blocoDoEnergies(String caminho) {
        try {
            var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
            var id = net.minecraft.resources.Identifier.parse("snc_energies:" + caminho);
            return registry.getOptional(id).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================================================== SOQUETE + LÂMPADA

    /** O soquete veste a lâmpada: instala, troca, remove — sem perder nada. */
    @GameTest
    public void soqueteVesteETrocaALampada(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete = (SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(soquete.getLampada().isEmpty(),
                "The socket is born empty");
        helper.assertTrue(soquete.consumoW() == 0L,
                "An empty socket draws 0 W");

        // instala a LED 15W (a família existente vira consumidor real)
        var led15 = IntoxicantesMod.LAMPADA_LED_15W;
        soquete.setLampada(new ItemStack(led15));
        helper.assertTrue(soquete.getLampada().is(led15.asItem()),
                "The 15W LED installs in the socket");
        helper.assertTrue(soquete.consumoW() == 15L,
                "The installed LED draws its tag wattage (15 W)");

        // troca por outro bulbo de 20W (a antiga devolve ao jogador)
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.DOWN, pos, false);
        level.getBlockState(pos).useItemOn(new ItemStack(IntoxicantesMod.LAMPADA_LED_20W),
                level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(soquete.getLampada().is(IntoxicantesMod.LAMPADA_LED_20W.asItem()),
                "Clicking with another LED swaps it");
        helper.assertTrue(player.getInventory().countItem(led15.asItem()) == 1,
                "The old LED returns to the player (nothing vanishes)");

        // mão vazia remove
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(soquete.getLampada().isEmpty() && !soquete.getLampada().getClass().isEnum(),
                "Empty hand removes the bulb");
        helper.assertTrue(player.getInventory().countItem(IntoxicantesMod.LAMPADA_LED_20W.asItem()) == 1,
                "The removed LED goes to the player");
        helper.succeed();
    }

    // ==================================================== A CADEIA COMPLETA (energia REAL)

    /**
     * A CADEIA: fonte do SNC Energies → quadro → cabo → soquete com LED
     * acesa. Com o SNC Energies no ambiente: consumo REAL (o buffer do
     * gerador diminui). Sem: a lâmpada não acende (e nada crasha).
     */
    @GameTest
    public void cadeiaCompletaAcendeLEDComEnergiaReal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos posCabo = posQuadro.east();
        BlockPos posCabo2 = posCabo.east();
        BlockPos posSoquete = posCabo2.above();

        // montagem: quadro → cabo → cabo → soquete em cima do 2º cabo
        level.setBlockAndUpdate(posQuadro, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        level.setBlockAndUpdate(posCabo, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(posCabo2, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(posSoquete, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete = (SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(posSoquete);
        soquete.setLampada(new ItemStack(IntoxicantesMod.LAMPADA_LED_15W));

        var quadro = (QuadroEletricoBlock.QuadroEletricoBlockEntity) level.getBlockEntity(posQuadro);

        // SEM fonte: a lâmpada não acende (mas nada crasha)
        quadro.tickRede(level);
        helper.assertTrue(!level.getBlockState(posSoquete).getValue(SoqueteTetoBlock.ACESA),
                "No source, no light (honest wiring)");

        // COM fonte real do SNC Energies (presente nos gametests via dependência):
        if (SNCEnergiesAdapter.disponivel()) {
            var blocoGerador = blocoDoEnergies("coal_generator");
            if (blocoGerador != null) {
                // o gerador do SNC Energies encostado no quadro
                BlockPos posFonte = posQuadro.west();
                level.setBlockAndUpdate(posFonte, blocoGerador.defaultBlockState());
                BlockEntity fonte = level.getBlockEntity(posFonte);
                Object storage = SNCEnergiesAdapter.storageDe(fonte, Direction.UP);
                if (storage != null) {
                    // o gerador nasce vazio: injeta energia simulando uma queima
                    injetaEnergia(fonte, 1000L);
                    long antes = SNCEnergiesAdapter.energia(storage);
                    helper.assertTrue(antes >= 1000L,
                            "The injected source holds energy (real SNC Energies storage)");
                    // o teste é síncrono: a fila de rebuild do EnergiaRedes não roda aqui
                    quadro.reconstruirTopologia();
                    quadro.tickRede(level);
                    helper.assertTrue(level.getBlockState(posSoquete)
                                    .getValue(SoqueteTetoBlock.ACESA),
                            "Source → panel → cable → socket: the LED lights with REAL energy");
                    // o consumo REAL: a fonte caiu (LED 15W ≈ 2 E/t no tick da rede)
                    long depois = SNCEnergiesAdapter.energia(storage);
                    helper.assertTrue(depois < antes,
                            "The source's buffer really dropped (consumption is real)");
                }
            }
        }
        helper.succeed();
    }

    /** Injeta energia numa fonte do SNC Energies por reflexão (insert). */
    private void injetaEnergia(BlockEntity fonte, long quantidade) {
        try {
            Object storage = SNCEnergiesAdapter.storageDe(fonte, Direction.UP);
            if (storage == null) {
                return;
            }
            var m = storage.getClass().getMethod("insert", long.class, boolean.class);
            m.invoke(storage, quantidade, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not seed the real SNC Energies storage", e);
        }
    }

    // ==================================================== INTERRUPTOR

    /** Interruptor OFF é parede elétrica: a travessia para nele. */
    @GameTest
    public void interruptorDesligadoCortaATravessia(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos posCabo = posQuadro.east();
        BlockPos posInterruptor = posCabo.east();
        BlockPos posCabo2 = posInterruptor.east();
        BlockPos posSoquete = posCabo2.above();

        level.setBlockAndUpdate(posQuadro, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        level.setBlockAndUpdate(posCabo, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(posInterruptor,
                IntoxicantesMod.INTERRUPTOR_SIMPLES.defaultBlockState()
                        .setValue(InterruptorSimplesBlock.LIGADO, Boolean.TRUE));
        level.setBlockAndUpdate(posCabo2, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(posSoquete, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete = (SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(posSoquete);
        soquete.setLampada(new ItemStack(IntoxicantesMod.LAMPADA_LED_15W));
        var quadro = (QuadroEletricoBlock.QuadroEletricoBlockEntity) level.getBlockEntity(posQuadro);

        // interruptor ON: a travessia alcança o soquete (com fonte, acende)
        quadro.reconstruirTopologia();
        helper.assertTrue(quadro.reachTest(posSoquete),
                "Switch ON lets the traversal reach the socket");

        // interruptor OFF: parede elétrica
        level.setBlockAndUpdate(posInterruptor,
                level.getBlockState(posInterruptor)
                        .setValue(InterruptorSimplesBlock.LIGADO, Boolean.FALSE));
        quadro.reconstruirTopologia();
        helper.assertTrue(!quadro.reachTest(posSoquete),
                "Switch OFF is an electrical wall (downstream dies)");
        helper.succeed();
    }

    // ==================================================== SOBRECARGA

    /** Sobrecarga 3 s desarma o disjuntor; rearme volta a LIGADO. */
    @GameTest
    public void sobrecargaDesarmaERearmeVolta(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(posQuadro, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        var quadro = (QuadroEletricoBlock.QuadroEletricoBlockEntity) level.getBlockEntity(posQuadro);
        var circuito = quadro.circuitos().get(0);

        // O circuito de iluminação sai de fábrica com 10 A @ 127 V = 1270 W.
        helper.assertTrue(circuito.capacidadeW() == 1270L,
                "The lighting circuit has the expected 1270 W capacity");
        circuito.consumoWAtual = 1200L;
        helper.assertTrue(!circuito.medir() && circuito.energizado(),
                "Consumption below capacity does not trip the breaker");

        // A rede mede uma vez por segundo: três medições de 1300 W desarmam.
        circuito.consumoWAtual = 1300L;
        for (int i = 0; i < 2; i++) {
            helper.assertTrue(circuito.medir() == false,
                    "Below 3 s of overload the breaker holds");
        }
        helper.assertTrue(circuito.medir() == true,
                "3 seconds of overload trips the breaker");
        helper.assertTrue(circuito.estado == com.intoxicantes.energia.CircuitoEletrico.DESARMADO,
                "The circuit is TRIPPED");
        helper.assertTrue(!circuito.energizado(),
                "A tripped circuit delivers no power");

        // o rearme manual
        helper.assertTrue(quadro.rearmarTodos() == 1,
                "The panel resets the tripped breaker");
        helper.assertTrue(circuito.estado == com.intoxicantes.energia.CircuitoEletrico.LIGADO,
                "Back to ON");
        helper.succeed();
    }

    // ==================================================== MÓDULOS 3–6

    /** As cinco bitolas mantêm ampacidade e receita registradas. */
    @GameTest
    public void cincoBitolasTrazemAmpacidadeEReceitas(GameTestHelper helper) {
        Block[] cabos = {IntoxicantesMod.CABO_COBRE_1_5MM,
                IntoxicantesMod.CABO_COBRE_2_5MM, IntoxicantesMod.CABO_COBRE_4MM,
                IntoxicantesMod.CABO_COBRE_6MM, IntoxicantesMod.CABO_COBRE_10MM};
        double[] mm2 = {1.5, 2.5, 4.0, 6.0, 10.0};
        long[] amperes = {10L, 20L, 32L, 40L, 63L};
        for (int i = 0; i < cabos.length; i++) {
            helper.assertTrue(cabos[i] instanceof CaboEletricoBlock,
                    "Bitola " + mm2[i] + " is a passive copper cable block");
            var cabo = (CaboEletricoBlock) cabos[i];
            helper.assertTrue(cabo.bitola() == mm2[i]
                            && cabo.amperagemMaxima() == amperes[i],
                    "Bitola " + mm2[i] + " mm² supports " + amperes[i] + " A");
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "cabo_cobre_" + (mm2[i] == 1.5 ? "1_5" :
                            mm2[i] == 2.5 ? "2_5" : (int) mm2[i]) + "mm");
            var chave = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE, id);
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager()
                            .byKey(chave).isPresent(),
                    "Bitola recipe exists for " + mm2[i] + " mm²");
        }
        helper.succeed();
    }

    /** As quatro saídas físicas conduzem a circuitos independentes. */
    @GameTest
    public void saidasDoQuadroDistribuemQuatroCircuitos(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painelPos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(painelPos,
                IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState()
                        .setValue(QuadroEletricoBlock.FACING, Direction.NORTH));
        Direction[] lados = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        var tomadas = new BlockPos[4];
        for (Direction lado : lados) {
            BlockPos cabo1 = painelPos.relative(lado);
            BlockPos cabo2 = cabo1.relative(lado);
            BlockPos tomada = cabo2.above();
            level.setBlockAndUpdate(cabo1, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
            level.setBlockAndUpdate(cabo2, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
            level.setBlockAndUpdate(tomada, IntoxicantesMod.TOMADA.defaultBlockState());
            tomadas[java.util.Arrays.asList(lados).indexOf(lado)] = tomada;
        }
        var painel = (QuadroEletricoBlock.QuadroEletricoBlockEntity)
                level.getBlockEntity(painelPos);
        painel.reconstruirTopologia();
        for (int i = 0; i < lados.length; i++) {
            var tomada = (TomadaBlock.TomadaBlockEntity) level.getBlockEntity(tomadas[i]);
            helper.assertTrue(tomada.circuito() == i,
                    "Output " + lados[i] + " routes to circuit C" + (i + 1));
        }
        helper.succeed();
    }

    /** Fio de 1,5 mm² desarma o disjuntor por sobrecorrente após cinco medições. */
    @GameTest
    public void fioSubdimensionadoDesarmaAposCincoSegundos(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painelPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(painelPos,
                IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        var circuito = ((QuadroEletricoBlock.QuadroEletricoBlockEntity)
                level.getBlockEntity(painelPos)).circuitos().get(1);
        circuito.disjuntorAmperes = 40L;
        circuito.tensao = Grandezas.V_127;
        for (int n = 1; n <= 7; n++) {
            BlockPos cabo = painelPos.relative(Direction.EAST, n);
            level.setBlockAndUpdate(cabo, IntoxicantesMod.CABO_COBRE_1_5MM.defaultBlockState());
            BlockPos soquetePos = cabo.above();
            level.setBlockAndUpdate(soquetePos, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
            var soquete = (SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(soquetePos);
            soquete.setLampada(new ItemStack(IntoxicantesMod.LAMPADA_LED_200W));
        }
        var painel = (QuadroEletricoBlock.QuadroEletricoBlockEntity)
                level.getBlockEntity(painelPos);
        painel.reconstruirTopologia();
        helper.assertTrue(painel.limiteBitolaA(1) == 10L,
                "The thinnest loaded branch limits C2 to 10 A");
        for (int i = 1; i <= 4; i++) {
            painel.buffer().insert(10000L, false);
            painel.tickRede(level);
            helper.assertTrue(circuito.estado == CircuitoEletrico.LIGADO,
                    "The cable protection holds until measurement " + i);
        }
        painel.buffer().insert(10000L, false);
        painel.tickRede(level);
        helper.assertTrue(circuito.estado == CircuitoEletrico.DESARMADO,
                "Five seconds above the wire ampacity trips the breaker");
        helper.succeed();
    }

    /** Tomada recebe circuito, estado de energia e consulta real no multímetro. */
    @GameTest
    public void tomadaEnergizaEVoltaAMorrerComOCircuito(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painelPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos cabo = painelPos.east();
        BlockPos tomadaPos = cabo.east();
        level.setBlockAndUpdate(painelPos, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        level.setBlockAndUpdate(cabo, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(tomadaPos, IntoxicantesMod.TOMADA.defaultBlockState());
        var painel = (QuadroEletricoBlock.QuadroEletricoBlockEntity)
                level.getBlockEntity(painelPos);
        var tomada = (TomadaBlock.TomadaBlockEntity) level.getBlockEntity(tomadaPos);
        painel.buffer().insert(1L, false); // energia disponível mesmo sem carga conectada
        painel.reconstruirTopologia();
        helper.assertTrue(tomada.circuito() == 1,
                "The east-facing output feeds C2");
        painel.tickRede(level);
        helper.assertTrue(tomada.energizada(), "Powered C2 energizes the wall outlet");
        painel.circuitos().get(1).estado = CircuitoEletrico.DESLIGADO;
        painel.tickRede(level);
        helper.assertTrue(!tomada.energizada(), "Switching off C2 de-energizes its outlet");
        helper.succeed();
    }

    /** Multímetro aceita leituras no painel e no bloco; nomes do coqueiro usam block.*. */
    @GameTest
    public void multimetroLeComponentesENomeDoTroncoUsaChaveDeBloco(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos painelPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(painelPos, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        var jogador = helper.makeMockServerPlayerInLevel();
        jogador.getInventory().setItem(0, new ItemStack(IntoxicantesMod.MULTIMETRO));
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(painelPos), Direction.UP, painelPos, false);
        var contexto = new UseOnContext(jogador, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(IntoxicantesMod.MULTIMETRO.useOn(contexto)
                        == net.minecraft.world.InteractionResult.SUCCESS,
                "Multimeter inspects a panel using a server-authoritative action");
        helper.assertTrue(IntoxicantesMod.COQUEIRO_TRONCO.asItem().getDescriptionId()
                        .equals("block.intoxicantes.coqueiro_tronco"),
                "Palm trunk BlockItem resolves to the block translation key (not item.*)");
        helper.succeed();
    }

    private QuadroEletricoBlock.QuadroEletricoBlockEntity montarPainel(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
        return (QuadroEletricoBlock.QuadroEletricoBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    @GameTest
    public void todasAsPotenciasConsomemExatamenteSemLuzGratis(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var painel = montarPainel(h, pos);
        level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        for (Block led : IntoxicantesMod.LAMPADAS_POTENCIA) {
            level.setBlockAndUpdate(pos.east(2), led.defaultBlockState());
            h.assertTrue(!level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT), "LED must start dark without supply");
            painel.buffer().insert(10000L, false);
            painel.reconstruirTopologia();
            long antes = painel.buffer().getEnergy();
            long totalAntes = painel.circuitos().get(1).totalConsumidoE;
            painel.tickRede(level);
            long watts = LedPotenciaBlock.wattsDoBloco(led);
            h.assertTrue(level.getBlockState(pos.east(2)).getLightEmission() == LedPotenciaBlock.luzPorWatts((int) watts), "Supplied LED uses its correct light level");
            h.assertTrue(antes - painel.buffer().getEnergy() == watts * 2L, "Exact one-second energy, including 5W and 9W");
            h.assertTrue(painel.circuitos().get(1).totalConsumidoE - totalAntes == watts * 2L, "Meter agrees with actual consumption");
        }
        h.succeed();
    }

    @GameTest
    public void tomadaAlimentaRefletorESoqueteSeguePotencia(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        var painel=montarPainel(h,pos);
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2),IntoxicantesMod.TOMADA.defaultBlockState());
        level.setBlockAndUpdate(pos.east(3),IntoxicantesMod.LAMPADA_LED_50W.defaultBlockState());
        level.setBlockAndUpdate(pos.east().above(),IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete=(SoqueteTetoBlock.SoqueteBlockEntity)level.getBlockEntity(pos.east().above());
        soquete.setLampada(new ItemStack(IntoxicantesMod.LAMPADA_LED_5W));
        painel.buffer().insert(1000L,false); painel.reconstruirTopologia(); painel.tickRede(level);
        h.assertTrue(level.getBlockState(pos.east(3)).getValue(LedPotenciaBlock.LIT),"Outlet powers a plugged floodlight");
        h.assertTrue(level.getBlockState(pos.east().above()).getLightEmission()==7,"5W installed bulb emits level 7, not constant 10");
        h.assertTrue(painel.circuitos().get(1).consumoW()==55L,"Outlet and socket loads share real circuit consumption");
        painel.circuitos().get(1).estado=CircuitoEletrico.DESLIGADO; painel.reconstruirTopologia();
        h.assertTrue(!level.getBlockState(pos.east(3)).getValue(LedPotenciaBlock.LIT),"Breaker immediately cuts lamp");
        h.assertTrue(!((TomadaBlock.TomadaBlockEntity)level.getBlockEntity(pos.east(2))).energizada(),"Breaker immediately cuts outlet");
        h.succeed();
    }

    @GameTest
    public void corteDoCaboPelaFilaNaoDeixaLuzResidual(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        var painel=montarPainel(h,pos);
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2),IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        painel.buffer().insert(1000L,false); painel.reconstruirTopologia(); painel.tickRede(level);
        level.setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
        com.intoxicantes.energia.EnergiaRedes.processarFila(level);
        h.assertTrue(!level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),"Cut wire extinguishes the disconnected load through the real event queue");
        long antes=painel.buffer().getEnergy(); painel.tickRede(level);
        h.assertTrue(antes==painel.buffer().getEnergy(),"Disconnected load no longer consumes");
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        com.intoxicantes.energia.EnergiaRedes.processarFila(level); painel.tickRede(level);
        h.assertTrue(level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),"Replacing wire restores supply");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.assertTrue(!level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),"Removing panel cuts remote loads immediately");
        h.succeed();
    }

    @GameTest
    public void saidasUnidasDesarmamSemDuplicarEnergia(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(3,2,3));
        var painel=montarPainel(h,pos);
        for (BlockPos p:java.util.List.of(pos.north(),pos.north().east(),pos.east()))
            level.setBlockAndUpdate(p,IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.north(2),IntoxicantesMod.LAMPADA_LED_20W.defaultBlockState());
        painel.buffer().insert(1000L,false); painel.reconstruirTopologia(); painel.tickRede(level);
        h.assertTrue(painel.circuitos().get(0).estado==CircuitoEletrico.DESARMADO && painel.circuitos().get(1).estado==CircuitoEletrico.DESARMADO,"Joined outputs trip both circuit breakers");
        h.assertTrue(painel.buffer().getEnergy()==1000L,"Miswired circuits consume nothing");
        painel.rearmarTodos(); painel.tickRede(level);
        h.assertTrue(painel.circuitos().get(0).estado==CircuitoEletrico.DESARMADO,"Reset cannot hide the remaining wiring fault");
        h.succeed();
    }

    @GameTest
    public void chaveAbertaIsolaSaidasEFechadaDetectaLigacaoErrada(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(3,2,3));
        var painel=montarPainel(h,pos);
        level.setBlockAndUpdate(pos.north(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        BlockPos chave=pos.north().east();
        level.setBlockAndUpdate(chave,IntoxicantesMod.INTERRUPTOR_SIMPLES.defaultBlockState());
        painel.buffer().insert(1000L,false); painel.reconstruirTopologia(); painel.tickRede(level);
        h.assertTrue(painel.circuitos().get(0).energizado()&&painel.circuitos().get(1).energizado(),"Open switch isolates its two contacts");
        level.setBlockAndUpdate(chave,level.getBlockState(chave).setValue(InterruptorSimplesBlock.LIGADO,true));
        painel.reconstruirTopologia(); painel.tickRede(level);
        h.assertTrue(painel.circuitos().get(0).estado==CircuitoEletrico.DESARMADO&&painel.circuitos().get(1).estado==CircuitoEletrico.DESARMADO,"Closing a bridge across outputs trips both");
        h.succeed();
    }

    @GameTest
    public void faltaDeEnergiaNaoDesarmaNemContaConsumo(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2)); var painel=montarPainel(h,pos);
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_1_5MM.defaultBlockState());
        for(int n=1;n<=7;n++) {
            BlockPos cabo=pos.east().above(n); level.setBlockAndUpdate(cabo,IntoxicantesMod.CABO_COBRE_1_5MM.defaultBlockState());
            level.setBlockAndUpdate(cabo.east(),IntoxicantesMod.LAMPADA_LED_200W.defaultBlockState());
        }
        painel.reconstruirTopologia();
        for(int n=0;n<7;n++) painel.tickRede(level);
        h.assertTrue(painel.circuitos().get(1).energizado(),"No actual current means no thermal overload");
        h.assertTrue(painel.circuitos().get(1).consumoW()==0 && painel.circuitos().get(1).totalConsumidoE==0,"Unserved demand is not billed as consumption");
        h.succeed();
    }

    @GameTest
    public void fioFinoAntesDaChaveContinuaProtegido(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2)); var painel=montarPainel(h,pos);
        painel.circuitos().get(1).disjuntorAmperes=40L;
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_1_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2),IntoxicantesMod.INTERRUPTOR_SIMPLES.defaultBlockState().setValue(InterruptorSimplesBlock.LIGADO,true));
        for(int n=0;n<7;n++) {
            BlockPos cabo=pos.east(3).above(n); level.setBlockAndUpdate(cabo,IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(cabo.east(),IntoxicantesMod.LAMPADA_LED_200W.defaultBlockState());
        }
        painel.reconstruirTopologia(); h.assertTrue(painel.limiteBitolaA(1)==10L,"Switch does not erase upstream ampacity");
        for(int n=0;n<5;n++) { painel.buffer().insert(10000L,false); painel.tickRede(level); }
        h.assertTrue(painel.circuitos().get(1).estado==CircuitoEletrico.DESARMADO,"Thin upstream conductor trips after five supplied intervals");
        h.succeed();
    }

    @GameTest
    public void ramalFinoNaoLimitaCargaDoRamalGrosso(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2)); var painel=montarPainel(h,pos);
        painel.circuitos().get(1).disjuntorAmperes=40L;
        for(int n=1;n<=7;n++) {
            BlockPos cabo=pos.east(n); level.setBlockAndUpdate(cabo,IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(cabo.above(),IntoxicantesMod.LAMPADA_LED_200W.defaultBlockState());
        }
        level.setBlockAndUpdate(pos.east().below(),IntoxicantesMod.CABO_COBRE_1_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east().below(2),IntoxicantesMod.LAMPADA_LED_5W.defaultBlockState());
        painel.reconstruirTopologia();
        for(int n=0;n<6;n++) { painel.buffer().insert(10000L,false); painel.tickRede(level); }
        h.assertTrue(painel.circuitos().get(1).energizado(),"Only 5W traverses the thin branch, not the total 1405W");
        h.succeed();
    }

    @GameTest
    public void quadroConectadoPrevaleceSobreMaisProximo(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2)); var painel=montarPainel(h,pos);
        level.setBlockAndUpdate(pos.east(),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2),IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        BlockPos tomadaPos=pos.east(3); level.setBlockAndUpdate(tomadaPos,IntoxicantesMod.TOMADA.defaultBlockState());
        var outro=montarPainel(h,tomadaPos.above(2)); outro.circuitos().get(1).nome="Errado";
        painel.circuitos().get(1).nome="Correto"; painel.reconstruirTopologia(); outro.reconstruirTopologia();
        h.assertTrue(com.intoxicantes.energia.EnergiaRedes.quadroDaRede(level,tomadaPos)==painel,"Diagnostics locate actual wired panel");
        h.assertTrue(((TomadaBlock.TomadaBlockEntity)level.getBlockEntity(tomadaPos)).nomeCircuito().equals("Correto"),"Outlet label belongs to its wired panel");
        h.succeed();
    }

    @GameTest
    public void lampadaPersistidaPreservaChaveAposFaltaDeEnergia(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        var led=(LedPotenciaBlockEntity)level.getBlockEntity(pos);
        var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
        led.saveWithoutMetadata(output);
        var recriada=new LedPotenciaBlockEntity(pos,level.getBlockState(pos));
        recriada.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
        h.assertTrue(recriada.habilitada(),"New saved dark lamp preserves enabled local switch");
        var antigo=new net.minecraft.nbt.CompoundTag(); antigo.put("lampada",new net.minecraft.nbt.CompoundTag());
        var antiga=new LedPotenciaBlockEntity(pos,IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState().setValue(LedPotenciaBlock.LIT,false));
        antiga.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),antigo));
        h.assertTrue(!antiga.habilitada(),"Legacy dark lamp preserves old off switch");
        h.succeed();
    }

    @GameTest
    public void soqueteRecusaRefletorERespeitaCriativo(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete=(SoqueteTetoBlock.SoqueteBlockEntity)level.getBlockEntity(pos);
        var player=h.makeMockServerPlayerInLevel();
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.DOWN,pos,false);
        var refletor=new ItemStack(IntoxicantesMod.LAMPADA_LED_50W);
        level.getBlockState(pos).useItemOn(refletor,level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(soquete.getLampada().isEmpty()&&refletor.getCount()==1,"Floodlight cannot screw into bulb socket");
        player.getAbilities().instabuild=true;
        var bulbo=new ItemStack(IntoxicantesMod.LAMPADA_LED_9W);
        level.getBlockState(pos).useItemOn(bulbo,level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(soquete.consumoW()==9L&&bulbo.getCount()==1,"Creative installation preserves held bulb");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player,level,ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(soquete.getLampada().isEmpty(),"Real server interaction removes installed bulb with empty hand");
        h.succeed();
    }

    @GameTest
    public void cliqueRealAlternaLedSemGerarEnergia(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        var led = (LedPotenciaBlockEntity) level.getBlockEntity(pos);
        var player = h.makeMockServerPlayerInLevel();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(!led.habilitada(), "Real click disables the lamp's local switch");
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(led.habilitada() && !level.getBlockState(pos).getValue(LedPotenciaBlock.LIT),
                "Local switch requests supply but never creates free light");
        h.succeed();
    }

    @GameTest
    public void fonteRealCobraExatoERecuperaAposApagao(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var painel = montarPainel(h, pos);
        level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2), IntoxicantesMod.LAMPADA_LED_9W.defaultBlockState());
        Block gerador = blocoDoEnergies("coal_generator");
        if (!SNCEnergiesAdapter.disponivel()) {
            h.assertTrue(gerador == null, "Standalone environment has no SNC Energies generator");
            painel.reconstruirTopologia();
            painel.tickRede(level);
            h.assertTrue(painel.disponivelE() == 0 && painel.consumoW() == 0
                    && !level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),
                    "Standalone wiring stays safely unpowered");
            h.succeed();
            return;
        }
        h.assertTrue(gerador != null, "Loaded integration must register its real generator");
        level.setBlockAndUpdate(pos.above(), gerador.defaultBlockState());
        var fonte = level.getBlockEntity(pos.above());
        var storage = SNCEnergiesAdapter.storageDe(fonte, Direction.DOWN);
        h.assertTrue(storage != null, "Generator exposes storage toward panel");
        injetaEnergia(fonte, 36L);
        painel.reconstruirTopologia();
        painel.tickRede(level);
        h.assertTrue(SNCEnergiesAdapter.energia(storage) == 18L && painel.consumoW() == 9L,
                "9W consumes exactly 18 E per second from the real companion source");
        painel.tickRede(level);
        painel.tickRede(level);
        h.assertTrue(SNCEnergiesAdapter.energia(storage) == 0L
                && painel.circuitos().get(1).totalConsumidoE == 36L
                && painel.circuitos().get(1).energizado()
                && !level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),
                "Empty source cuts light without phantom billing or breaker trip");
        injetaEnergia(fonte, 18L);
        painel.tickRede(level);
        h.assertTrue(level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT),
                "Restoring source energy automatically restores the enabled lamp");
        h.succeed();
    }

    @GameTest
    public void cargaTerminalEntreDoisQuadrosNaoRecebeDuasFontes(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var primeiro = montarPainel(h, pos);
        var segundo = montarPainel(h, pos.east(4));
        level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2), IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        level.setBlockAndUpdate(pos.east(3), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        primeiro.buffer().insert(1000L, false);
        segundo.buffer().insert(1000L, false);
        primeiro.reconstruirTopologia();
        segundo.reconstruirTopologia();
        primeiro.tickRede(level);
        segundo.tickRede(level);
        h.assertTrue(primeiro.circuitos().get(1).estado == CircuitoEletrico.DESARMADO
                && segundo.circuitos().get(3).estado == CircuitoEletrico.DESARMADO,
                "A terminal shared by different panels is a wiring fault");
        h.assertTrue(primeiro.buffer().getEnergy() == 1000L && segundo.buffer().getEnergy() == 1000L,
                "No double billing or power from shared sources");
        h.succeed();
    }

    @GameTest
    public void soquetePreservaComponentesEDevolveComInventarioCheio(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
        var soquete = (SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(pos);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var bulbo = new ItemStack(IntoxicantesMod.LAMPADA_LED_15W);
        bulbo.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Bulbo da oficina"));
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.DOWN, pos, false);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bulbo);
        player.gameMode.useItemOn(player, level, bulbo, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(bulbo.isEmpty() && soquete.getLampada().getHoverName().getString().equals("Bulbo da oficina"),
                "Survival installation uses exactly one bulb and preserves its components");
        var tag = soquete.saveWithoutMetadata(level.registryAccess());
        var recriado = new SoqueteTetoBlock.SoqueteBlockEntity(pos, level.getBlockState(pos));
        recriado.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
        h.assertTrue(ItemStack.isSameItemSameComponents(recriado.getLampada(), soquete.getLampada()),
                "Socket serialization preserves the entire bulb stack");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1),
                item -> item.getItem().is(IntoxicantesMod.LAMPADA_LED_15W.asItem()));
        h.assertTrue(soquete.getLampada().isEmpty() && drops.size() == 1
                && drops.getFirst().getItem().getCount() == 1
                && drops.getFirst().getItem().getHoverName().getString().equals("Bulbo da oficina"),
                "Full inventory returns exactly one named bulb as a world item");
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1),
                item -> item.getItem().is(IntoxicantesMod.LAMPADA_LED_15W.asItem())).size() == 1,
                "Repeated removal cannot duplicate the bulb");
        h.succeed();
    }

    @GameTest
    public void quadroRecarregadoRecuperaCircuitosERegistro(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var quadro = montarPainel(h, pos);
        quadro.buffer().insert(500L, false);
        var circuito = quadro.circuitos().get(1);
        circuito.nome = "Oficina antiga";
        circuito.tensao = 220L;
        circuito.estado = CircuitoEletrico.DESARMADO;
        circuito.totalConsumidoE = 72L;
        var tag = quadro.saveWithoutMetadata(level.registryAccess());
        var recriado = new QuadroEletricoBlock.QuadroEletricoBlockEntity(pos, level.getBlockState(pos));
        recriado.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
        level.setBlockEntity(recriado);
        com.intoxicantes.energia.EnergiaRedes.processarFila(level);
        h.assertTrue(recriado.buffer().getEnergy() == 500L
                && recriado.circuitos().get(1).nome.equals("Oficina antiga")
                && recriado.circuitos().get(1).tensao == 220L
                && recriado.circuitos().get(1).estado == CircuitoEletrico.DESARMADO
                && recriado.circuitos().get(1).totalConsumidoE == 72L,
                "Reload preserves buffer, circuit label, voltage, breaker and real metered total");
        h.assertTrue(com.intoxicantes.energia.EnergiaRedes.quadroMaisProximo(level, pos, 1) == recriado,
                "Assigning a loaded panel to the level registers it again");
        h.succeed();
    }

    @GameTest
    public void quedaDoChunkDoQuadroCortaCargasCarregadas(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var quadro = montarPainel(h, pos);
        level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2), IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState());
        quadro.buffer().insert(500L, false);
        quadro.reconstruirTopologia();
        quadro.tickRede(level);
        h.assertTrue(level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT), "Load is powered before unload");
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, level.getChunkAt(pos));
        h.assertTrue(!level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT) && quadro.consumoW() == 0L,
                "Actual unload callback de-energizes still-loaded outputs and clears current draw");
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, level.getChunkAt(pos), false);
        com.intoxicantes.energia.EnergiaRedes.processarFila(level);
        quadro.tickRede(level);
        h.assertTrue(level.getBlockState(pos.east(2)).getValue(LedPotenciaBlock.LIT), "Loading the panel rebuilds and restores valid supply");
        h.succeed();
    }

    @GameTest
    public void recargaDeChunkApagaEstadosSalvosSemQuadro(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, IntoxicantesMod.LAMPADA_LED_15W.defaultBlockState().setValue(LedPotenciaBlock.LIT, true));
        level.setBlockAndUpdate(pos.east(), IntoxicantesMod.SOQUETE_TETO.defaultBlockState().setValue(SoqueteTetoBlock.ACESA, true));
        level.setBlockAndUpdate(pos.east(2), IntoxicantesMod.TOMADA.defaultBlockState().setValue(TomadaBlock.ENERGIZADA, true));
        // A origem da estrutura pode cair na borda: os três blocos nem sempre
        // pertencem ao mesmo chunk. Recarregar cada chunk efetivamente usado.
        var chunks = new java.util.HashSet<net.minecraft.world.level.chunk.LevelChunk>();
        for (int i = 0; i < 3; i++) chunks.add(level.getChunkAt(pos.east(i)));
        for (var chunk : chunks)
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.invoker()
                    .onChunkLoad(level, chunk, false);
        h.assertTrue(!level.getBlockState(pos).getValue(LedPotenciaBlock.LIT),
                "Chunk reload cannot preserve a stale lit LED");
        h.assertTrue(!level.getBlockState(pos.east()).getValue(SoqueteTetoBlock.ACESA),
                "Chunk reload cannot preserve a stale lit socket");
        h.assertTrue(!level.getBlockState(pos.east(2)).getValue(TomadaBlock.ENERGIZADA),
                "Chunk reload cannot preserve a stale energized outlet");
        h.succeed();
    }

    // ==================================================== CABO SUSPENSO (v1.2.80)

    /**
     * O cabo de cobre padrão só liga blocos vizinhos. O cabo suspenso liga
     * DOIS PONTOS LONGE: a travessia do quadro tem de atravessar o elo, e a
     * bitola do vão vale como limite de corrente do circuito.
     */
    @GameTest
    public void caboSuspensoAtravessaOGapELevaALampada(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(1, 1, 1));
        // cabo perto do quadro + conector apontando para o vão
        BlockPos caboA = posQuadro.east();
        BlockPos conectorA = caboA.east();
        // a outra ponta: cabo + soquete com lâmpada, 2 blocos de vão depois
        BlockPos caboB = conectorA.east(3);
        BlockPos conectorB = caboB.west();
        BlockPos posSoquete = caboB.above();
        try {
            level.setBlockAndUpdate(posQuadro, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
            var quadro = (QuadroEletricoBlock.QuadroEletricoBlockEntity)
                    level.getBlockEntity(posQuadro);
            level.setBlockAndUpdate(caboA, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
            level.setBlockAndUpdate(conectorA,
                    IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                            .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(caboB, IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
            level.setBlockAndUpdate(conectorB,
                    IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                            .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
            level.setBlockAndUpdate(posSoquete, IntoxicantesMod.SOQUETE_TETO.defaultBlockState());
            ((SoqueteTetoBlock.SoqueteBlockEntity) level.getBlockEntity(posSoquete))
                    .setLampada(new ItemStack(IntoxicantesMod.LAMPADA_LED_15W));

            // SEM elo: o vão é ar, o soquete não é alcançado
            quadro.reconstruirTopologia();
            helper.assertTrue(!quadro.reachTest(posSoquete),
                    "Without the span, the far socket is not part of the network");

            // com o elo de 1,5 mm² (10 A) a rede atravessa o vão
            CabosSuspensos cabos = CabosSuspensos.get(level);
            helper.assertTrue(cabos.adicionar(new EloCabo(conectorA, Direction.EAST,
                    conectorB, Direction.WEST, 1.5)),
                    "The span is stored once as data (no block fills the gap)");
            helper.assertTrue(cabos.total() == 1, "One span, one record");
            quadro.reconstruirTopologia();
            helper.assertTrue(quadro.reachTest(posSoquete),
                    "The panel traversal crosses the suspended span");
            int circuito = quadro.circuitoDoSoquete(posSoquete);
            helper.assertTrue(circuito >= 0 && quadro.limiteBitolaA(circuito) == 10L,
                    "The thinnest gauge of the path (the span) limits the circuit to 10 A");

            // energia de verdade: o soquete acende a lâmpada
            quadro.buffer().insert(1_000L, false);
            quadro.tickRede(level);
            helper.assertTrue(level.getBlockState(posSoquete).getValue(SoqueteTetoBlock.ACESA),
                    "Panel -> span -> socket: the LED lights through the gap");

            // recolher o cabo corta a ligação na hora
            helper.assertTrue(cabos.removerEm(conectorB) != null,
                    "Reeling the span in removes the link");
            quadro.reconstruirTopologia();
            helper.assertTrue(!quadro.reachTest(posSoquete),
                    "A cut span stops carrying the circuit");
        } finally {
            // a dimensão é compartilhada pelos testes: elo nenhum pode sobrar
            CabosSuspensos.get(level).removerTodosEm(conectorA);
            CabosSuspensos.get(level).removerTodosEm(conectorB);
        }
        helper.succeed();
    }

    /** A bobina só age em conector, gasta uma por trecho e recusa repetição. */
    @GameTest
    public void bobinaEstendeUmTrechoPorVez(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos conectorA = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos conectorB = helper.absolutePos(new BlockPos(5, 1, 1));
        try {
            level.setBlockAndUpdate(posQuadro, IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
            level.setBlockAndUpdate(conectorA, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(conectorB.east(), IntoxicantesMod.CABO_COBRE_2_5MM.defaultBlockState());
            level.setBlockAndUpdate(conectorB, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.WEST));

            var jogador = helper.makeMockServerPlayerInLevel();
            jogador.getAbilities().instabuild = false;
            ItemStack bobina = new ItemStack(IntoxicantesMod.BOBINA_COBRE_2_5MM, 2);
            jogador.getInventory().setItem(0, bobina);
            var cabos = CabosSuspensos.get(level);

            // clique em bloco que não é conector: não cria elo
            bobina.useOn(contextoEm(jogador, posQuadro));
            helper.assertTrue(cabos.total() == 0,
                    "The spool does nothing on a non-connector block");

            // 1º clique: fixa a origem; 2º clique: estende o cabo
            bobina.useOn(contextoEm(jogador, conectorA));
            helper.assertTrue(bobina.get(IntoxicantesMod.TIPO_ORIGEM_ELO) != null,
                    "First click locks the span origin on the stack itself");
            bobina.useOn(contextoEm(jogador, conectorB));
            helper.assertTrue(cabos.total() == 1 && bobina.getCount() == 1,
                    "Second click spends one spool and stretches the span");
            helper.assertTrue(cabos.elos().get(0).amperagem() == 20L,
                    "A 2.5 mm² span carries 20 A");
            helper.assertTrue(bobina.get(IntoxicantesMod.TIPO_ORIGEM_ELO) == null,
                    "A completed span clears the origin on remaining spools");

            // repetir o mesmo par é recusado (nada de cobre duplicado)
            ItemStack outra = new ItemStack(IntoxicantesMod.BOBINA_COBRE_2_5MM);
            jogador.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, outra);
            outra.useOn(contextoEm(jogador, conectorA));
            outra.useOn(contextoEm(jogador, conectorB));
            helper.assertTrue(cabos.total() == 1 && outra.getCount() == 1,
                    "A second span between the same connectors is refused");

            // agachado: recolhe o cabo e devolve a bobina
            jogador.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            helper.assertTrue(jogador.isCrouching(), "The player really is crouching");
            jogador.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bobina);
            ItemStack naMao = jogador.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            naMao.useOn(contextoEm(jogador, conectorA));
            helper.assertTrue(cabos.total() == 0,
                    "Sneaking with the spool reels the span in");
            helper.assertTrue(naMao.getCount() == 2,
                    "Reeling in returns exactly the spent spool, even with a matching stack");

            // o alcance é de 32 blocos (medido em passos de bloco)
            BlockPos longe = conectorA.east(CabosSuspensos.ALCANCE_MAX + 2);
            helper.assertTrue(new EloCabo(conectorA, Direction.EAST, longe, Direction.WEST, 2.5)
                    .comprimento() > CabosSuspensos.ALCANCE_MAX,
                    "The span reach limit is 32 blocks");
        } finally {
            CabosSuspensos.get(level).removerTodosEm(conectorA);
            CabosSuspensos.get(level).removerTodosEm(conectorB);
        }
        helper.succeed();
    }

    /** O conector precisa de suporte; perder o bloco de apoio derruba o cabo. */
    @GameTest
    public void conectorExigeSuporteECortaOCaboComAMontanha(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos posQuadro = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos conector = helper.absolutePos(new BlockPos(1, 1, 2));
        try {
            var jogador = helper.makeMockServerPlayerInLevel();
            jogador.getInventory().setItem(0,
                    new ItemStack(IntoxicantesMod.CONECTOR_ELETRICO.asItem()));

            // no ar: não instala
            var contextoAr = new net.minecraft.world.item.context.BlockPlaceContext(jogador,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(IntoxicantesMod.CONECTOR_ELETRICO.asItem()),
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(conector), Direction.SOUTH,
                            conector, false));
            helper.assertTrue(
                    IntoxicantesMod.CONECTOR_ELETRICO.getStateForPlacement(contextoAr) == null,
                    "A connector needs a panel, cable, socket, outlet or switch behind it");

            // com o quadro atrás: instala apontando para longe do suporte
            level.setBlockAndUpdate(posQuadro,
                    IntoxicantesMod.QUADRO_ELETRICO.defaultBlockState());
            var contextoQuadro = new net.minecraft.world.item.context.BlockPlaceContext(jogador,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(IntoxicantesMod.CONECTOR_ELETRICO.asItem()),
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(posQuadro), Direction.SOUTH,
                            posQuadro, false));
            var instalado = IntoxicantesMod.CONECTOR_ELETRICO.getStateForPlacement(contextoQuadro);
            helper.assertTrue(instalado != null
                            && instalado.getValue(ConectorEletricoBlock.FACING) == Direction.SOUTH,
                    "The connector clamps onto the panel face and points away from it");
            level.setBlockAndUpdate(conector, instalado);

            var cabos = CabosSuspensos.get(level);
            cabos.adicionar(new EloCabo(conector, Direction.SOUTH,
                    helper.absolutePos(new BlockPos(1, 1, 5)), Direction.NORTH, 2.5));
            helper.assertTrue(cabos.total() == 1, "The span hangs from the connector");

            // tirar o quadro de baixo derruba o conector e o cabo junto
            level.setBlockAndUpdate(posQuadro,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(level.getBlockState(conector).isAir(),
                    "Removing the support block drops the connector");
            helper.assertTrue(cabos.total() == 0,
                    "The span cannot survive without its connector");
        } finally {
            CabosSuspensos.get(level).removerTodosEm(conector);
        }
        helper.succeed();
    }

    @GameTest
    public void bobinaValidaOrigemAlcanceBitolaECriativo(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1));
        BlockPos b = a.east(3);
        BlockPos distante = b.east(33);
        var player = h.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        var cabos = CabosSuspensos.get(level);
        try {
            level.setBlockAndUpdate(a.west(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(b.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(b, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
            var spool = new ItemStack(IntoxicantesMod.BOBINA_COBRE_6MM, 2);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, spool);
            spool.set(IntoxicantesMod.TIPO_ORIGEM_ELO,
                    new EloCabo.Extremo(a, Direction.EAST, net.minecraft.world.level.Level.NETHER));
            spool.useOn(contextoEm(player, b));
            h.assertTrue(!cabos.contemPar(a, b) && spool.getCount() == 2
                    && spool.get(IntoxicantesMod.TIPO_ORIGEM_ELO) == null,
                    "An origin in another dimension is cancelled without spending copper");
            spool.set(IntoxicantesMod.TIPO_ORIGEM_ELO,
                    new EloCabo.Extremo(a, Direction.WEST, level.dimension()));
            spool.useOn(contextoEm(player, b));
            h.assertTrue(!cabos.contemPar(a, b), "A rotated origin cannot create a stale span");
            spool.useOn(contextoEm(player, a));
            level.setBlockAndUpdate(a, Blocks.AIR.defaultBlockState());
            spool.useOn(contextoEm(player, b));
            h.assertTrue(!cabos.contemPar(a, b) && spool.getCount() == 2,
                    "A destroyed origin refuses the connection without spending the spool");
            level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(distante.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(distante, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
            spool.set(IntoxicantesMod.TIPO_ORIGEM_ELO,
                    new EloCabo.Extremo(distante, Direction.WEST, level.dimension()));
            spool.useOn(contextoEm(player, b));
            h.assertTrue(!cabos.contemPar(distante, b) && spool.getCount() == 2
                    && spool.get(IntoxicantesMod.TIPO_ORIGEM_ELO) != null,
                    "A span beyond 32 blocks between live connectors is refused without losing its origin");
            spool.remove(IntoxicantesMod.TIPO_ORIGEM_ELO);
            spool.useOn(contextoEm(player, a));
            spool.useOn(contextoEm(player, b));
            h.assertTrue(cabos.contemPar(a, b) && spool.getCount() == 1,
                    "A valid survival span consumes one spool");
            var cutter = new ItemStack(IntoxicantesMod.BOBINA_COBRE_1_5MM);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cutter);
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            cutter.useOn(contextoEm(player, b));
            int returned = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var item = player.getInventory().getItem(i);
                if (item.is(IntoxicantesMod.BOBINA_COBRE_6MM)) returned += item.getCount();
            }
            h.assertTrue(!cabos.contemPar(a, b) && returned == 1 && cutter.getCount() == 1,
                    "Cutting with a different gauge returns exactly the gauge of the cut span");
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.getAbilities().instabuild = true;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, spool);
            spool.useOn(contextoEm(player, a));
            spool.useOn(contextoEm(player, b));
            h.assertTrue(spool.getCount() == 1 && cabos.contemPar(a, b),
                    "Creative placement does not consume the held spool");
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            spool.useOn(contextoEm(player, b));
            h.assertTrue(spool.getCount() == 1 && !cabos.contemPar(a, b),
                    "Creative cutting does not duplicate spools");
        } finally {
            cabos.removerTodosEm(a);
            cabos.removerTodosEm(b);
            cabos.removerTodosEm(distante);
            level.setBlockAndUpdate(distante, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(distante.east(), Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest
    public void fonteRemotaAlimentaQuadroPeloCaboSuspenso(GameTestHelper h) {
        var gerador = blocoDoEnergies("coal_generator");
        if (!SNCEnergiesAdapter.disponivel()) {
            h.assertTrue(gerador == null, "Standalone has no companion generator");
            h.succeed();
            return;
        }
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var painel = montarPainel(h, pos);
        var a = pos.east();
        var b = pos.east(4);
        var dados = CabosSuspensos.get(level);
        try {
            level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(b.east(), gerador.defaultBlockState());
            level.setBlockAndUpdate(b, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
            level.setBlockAndUpdate(pos.north(), IntoxicantesMod.LAMPADA_LED_9W.defaultBlockState());
            var fonte = level.getBlockEntity(b.east());
            injetaEnergia(fonte, 36L);
            dados.adicionar(new EloCabo(a, Direction.EAST, b, Direction.WEST, 2.5));
            painel.reconstruirTopologia();
            h.assertTrue(painel.fontes().contains(b.east()),
                    "A generator attached to the far connector is a real panel source");
            painel.tickRede(level);
            h.assertTrue(painel.consumoW() == 9L
                    && level.getBlockState(pos.north()).getValue(LedPotenciaBlock.LIT)
                    && SNCEnergiesAdapter.energia(SNCEnergiesAdapter.storageDe(fonte, Direction.WEST)) == 18L,
                    "A remote generator powers the panel and is charged exactly 18 E for 9 W");
        } finally {
            dados.removerTodosEm(a);
            dados.removerTodosEm(b);
        }
        h.succeed();
    }

    @GameTest
    public void elosPersistemEIndiceReconstruiSemDuplicar(GameTestHelper h) {
        var a = h.absolutePos(new BlockPos(1, 2, 1));
        var b = a.east(3);
        var elo = new EloCabo(a, Direction.EAST, b, Direction.WEST, 4.0);
        var dados = new CabosSuspensos();
        dados.adicionar(elo);
        var tag = CabosSuspensos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, dados).getOrThrow();
        var loaded = CabosSuspensos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
        h.assertTrue(loaded.total() == 1 && loaded.elosEm(a).equals(java.util.List.of(elo))
                && loaded.elosEm(b).equals(java.util.List.of(elo)),
                "SavedData round-trip preserves endpoints, faces, gauge and connector index");
        h.assertTrue(!loaded.adicionar(new EloCabo(b, Direction.WEST, a, Direction.EAST, 6.0)),
                "Reversed duplicate connections cannot survive the persisted index");
        loaded.removerEm(a);
        h.assertTrue(loaded.elosEm(a).isEmpty() && loaded.elosEm(b).isEmpty(),
                "Removing a persisted span invalidates both connector indexes");
        var antigo = new net.minecraft.nbt.CompoundTag();
        antigo.put("pos", net.minecraft.core.BlockPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, a).getOrThrow());
        antigo.putString("face", "east");
        var origem = EloCabo.Extremo.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, antigo).getOrThrow();
        h.assertTrue(origem.dimensao().equals(net.minecraft.world.level.Level.OVERWORLD),
                "Legacy origin components without a dimension remain readable");
        h.succeed();
    }

    @GameTest
    public void eloFinoProtegeSoSeuCircuito(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var painel = montarPainel(h, pos);
        var a = pos.east(2);
        var b = pos.east(5);
        var dados = CabosSuspensos.get(level);
        try {
            level.setBlockAndUpdate(pos.north(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(pos.north(2), IntoxicantesMod.LAMPADA_LED_5W.defaultBlockState());
            level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(b.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(b, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.WEST));
            for (int n = 0; n < 7; n++) {
                var fio = b.east().above(n);
                level.setBlockAndUpdate(fio, IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
                level.setBlockAndUpdate(fio.east(), IntoxicantesMod.LAMPADA_LED_200W.defaultBlockState());
            }
            dados.adicionar(new EloCabo(a, Direction.EAST, b, Direction.WEST, 1.5));
            painel.circuitos().get(1).disjuntorAmperes = 40L;
            painel.reconstruirTopologia();
            for (int n = 0; n < 5; n++) {
                painel.buffer().insert(10000L, false);
                painel.tickRede(level);
                if (n < 4) h.assertTrue(painel.circuitos().get(1).energizado(),
                        "Span protection waits for five supplied overload intervals");
            }
            h.assertTrue(painel.circuitos().get(1).estado == CircuitoEletrico.DESARMADO,
                    "A 1.5 mm² suspended span trips under 1400 W at 127 V");
            h.assertTrue(painel.circuitos().get(0).energizado()
                    && level.getBlockState(pos.north(2)).getValue(LedPotenciaBlock.LIT),
                    "An overloaded span cannot trip or extinguish an independent circuit");
        } finally {
            dados.removerTodosEm(a);
            dados.removerTodosEm(b);
        }
        h.succeed();
    }

    @GameTest
    public void eloEntreSaidasDetectaConflitoNosDoisSentidos(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var painel = montarPainel(h, pos);
        var a = pos.north(2);
        var b = pos.east(2);
        var dados = CabosSuspensos.get(level);
        try {
            level.setBlockAndUpdate(pos.north(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(a, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(pos.east(), IntoxicantesMod.CABO_COBRE_6MM.defaultBlockState());
            level.setBlockAndUpdate(b, IntoxicantesMod.CONECTOR_ELETRICO.defaultBlockState()
                    .setValue(ConectorEletricoBlock.FACING, Direction.EAST));
            dados.adicionar(new EloCabo(a, Direction.NORTH, b, Direction.EAST, 2.5));
            painel.reconstruirTopologia();
            h.assertTrue(painel.circuitos().get(0).conflitoLigacao && painel.circuitos().get(1).conflitoLigacao,
                    "Both traversals detect circuits joined through the same suspended span");
            painel.buffer().insert(1000L, false);
            painel.tickRede(level);
            h.assertTrue(!painel.circuitos().get(0).energizado() && !painel.circuitos().get(1).energizado(),
                    "Both conflicting outputs trip without providing energy");
        } finally {
            dados.removerTodosEm(a);
            dados.removerTodosEm(b);
        }
        h.succeed();
    }

    private static UseOnContext contextoEm(net.minecraft.server.level.ServerPlayer jogador,
            BlockPos pos) {
        return new UseOnContext(jogador, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
