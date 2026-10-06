package com.intoxicantes;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.intoxicantes.energia.CircuitoEletrico;
import com.intoxicantes.energia.Grandezas;
import com.intoxicantes.energia.EnergiaRedes;
import com.intoxicantes.QuadroEletricoBlock.QuadroEletricoBlockEntity;

/**
 * v1.2.71 — O MULTÍMETRO DO ELETRICISTA (módulo 5). Clique num componente da
 * instalação → a leitura REAL daquele ponto; clique no ar → o resumo do quadro
 * mais próximo (16 blocos). Tudo server-side (o client só mostra): o
 * servidor lê as BEs e manda as linhas de texto prontas.
 *
 * Leitura honesta: se não tem rede, diz que não tem — nada de número
 * inventado. O "click no ar" é o modo "procurar o quadro" do eletricista.
 */
public class MultimetroItem extends Item {

    /** Raio de busca do quadro no clique no ar. */
    private static final double RAIO_QUADRO = 16.0;

    public MultimetroItem(Properties properties) {
        super(properties);
    }

    // ==================================================== CLIQUE NO COMPONENTE

    @Override
    public InteractionResult useOn(UseOnContext contexto) {
        Level level = contexto.getLevel();
        if (level instanceof ServerLevel servidor) {
            var pos = contexto.getClickedPos();
            BlockState estado = level.getBlockState(pos);
            Player jogador = contexto.getPlayer();
            if (jogador == null) {
                return InteractionResult.PASS;
            }
            if (estado.getBlock() instanceof QuadroEletricoBlock
                    && level.getBlockEntity(pos) instanceof QuadroEletricoBlockEntity quadro) {
                linhasDoQuadro(quadro).forEach(jogador::sendSystemMessage);
            } else if (estado.getBlock() instanceof CaboEletricoBlock cabo) {
                var quadro = EnergiaRedes.quadroDaRede(servidor, pos);
                int indice = quadro == null ? -1 : quadro.circuitoDoPonto(pos);
                String circuito = indice < 0 ? "—" : "C" + (indice + 1);
                jogador.sendSystemMessage(Component.translatable(
                        "eletricidade.intoxicantes.multimetro.cabo",
                        cabo.bitola(), CaboEletricoBlock.amperagemDaBitola(cabo.bitola()),
                        circuito));
            } else if (estado.getBlock() instanceof SoqueteTetoBlock
                    && level.getBlockEntity(pos)
                        instanceof SoqueteTetoBlock.SoqueteBlockEntity soquete) {
                ItemStack lampada = soquete.getLampada();
                if (lampada.isEmpty()) {
                    jogador.sendSystemMessage(Component.translatable(
                            "eletricidade.intoxicantes.multimetro.soquete.vazio"));
                } else {
                    long w = soquete.consumoW();
                    var quadro = EnergiaRedes.quadroDaRede(servidor, pos);
                    int indiceCircuito = quadro == null ? -1 : quadro.circuitoDoPonto(pos);
                    CircuitoEletrico circuito = quadro != null && indiceCircuito >= 0
                            ? quadro.circuitos().get(indiceCircuito) : null;
                    String estadoChave = circuito != null && circuito.estado == CircuitoEletrico.DESARMADO
                            ? "eletricidade.intoxicantes.estado_desarmado"
                            : estado.getValue(SoqueteTetoBlock.ACESA)
                                    ? "eletricidade.intoxicantes.estado_ligado"
                                    : "eletricidade.intoxicantes.estado_desligado";
                    jogador.sendSystemMessage(Component.translatable(
                            "eletricidade.intoxicantes.multimetro.soquete",
                            lampada.getHoverName(), w, Grandezas.formatarETick(w),
                            circuito == null ? "—" : circuito.nome,
                            circuito == null || !estado.getValue(SoqueteTetoBlock.ACESA)
                                    ? "0 V" : circuito.tensao + " V",
                            circuito == null ? "—" : Grandezas.formatarA(
                                    estado.getValue(SoqueteTetoBlock.ACESA) ? w : 0L,
                                    circuito.tensao),
                            Component.translatable(estadoChave).getString()));
                }
            } else if (estado.getBlock() instanceof InterruptorSimplesBlock) {
                boolean ligado = estado.getValue(InterruptorSimplesBlock.LIGADO);
                var quadro = EnergiaRedes.quadroDaRede(servidor, pos);
                int indiceCircuito = quadro == null ? -1 : quadro.circuitoDoPonto(pos);
                String circuito = indiceCircuito < 0 ? "—" : "C" + (indiceCircuito + 1);
                jogador.sendSystemMessage(Component.translatable(
                        "eletricidade.intoxicantes.multimetro.interruptor",
                        Component.translatable(ligado
                                ? "eletricidade.intoxicantes.estado_ligado"
                                : "eletricidade.intoxicantes.estado_desligado"),
                        circuito));
            } else if (estado.getBlock() instanceof LedPotenciaBlock
                    && level.getBlockEntity(pos) instanceof LedPotenciaBlockEntity lampada) {
                var quadro = EnergiaRedes.quadroDaRede(servidor, pos);
                int indice = quadro == null ? -1 : quadro.circuitoDoPonto(pos);
                CircuitoEletrico circuito = indice < 0 ? null : quadro.circuitos().get(indice);
                boolean acesa = estado.getValue(LedPotenciaBlock.LIT);
                jogador.sendSystemMessage(Component.translatable(
                        "eletricidade.intoxicantes.multimetro.lampada",
                        lampada.watts(), lampada.habilitada()
                                ? Component.translatable("eletricidade.intoxicantes.estado_ligado")
                                : Component.translatable("eletricidade.intoxicantes.estado_desligado"),
                        acesa ? Component.translatable("eletricidade.intoxicantes.estado_ligado")
                                : Component.translatable("eletricidade.intoxicantes.estado_desligado"),
                        circuito == null ? "—" : circuito.nome,
                        circuito == null || !acesa ? "0 V" : circuito.tensao + " V",
                        circuito == null ? "0 A" : Grandezas.formatarA(acesa ? lampada.watts() : 0L,
                                circuito.tensao)));
            } else if (estado.getBlock() instanceof TomadaBlock
                    && level.getBlockEntity(pos)
                        instanceof TomadaBlock.TomadaBlockEntity tomada) {
                jogador.sendSystemMessage(Component.translatable(
                        tomada.energizada()
                                ? "eletricidade.intoxicantes.multimetro.tomada.ok"
                                : "eletricidade.intoxicantes.multimetro.tomada.morta",
                        tomada.nomeCircuito()));
            } else {
                jogador.sendSystemMessage(Component.translatable(
                        "eletricidade.intoxicantes.multimetro.nada"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ==================================================== CLIQUE NO AR

    @Override
    public InteractionResult use(Level level, Player jogador, InteractionHand mao) {
        if (jogador instanceof ServerPlayer servidor) {
            if (level instanceof ServerLevel mundoServidor) {
                var quadro = EnergiaRedes.quadroMaisProximo(mundoServidor,
                        jogador.blockPosition(), RAIO_QUADRO);
                if (quadro == null) {
                    jogador.sendSystemMessage(Component.translatable(
                            "eletricidade.intoxicantes.multimetro.sem_quadro"));
                } else {
                    linhasDoQuadro(quadro).forEach(jogador::sendSystemMessage);
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    /** As linhas do resumo do quadro (status + 1 por circuito). */
    private List<Component> linhasDoQuadro(QuadroEletricoBlockEntity quadro) {
        var linhas = new java.util.ArrayList<Component>();
        linhas.add(Component.translatable(
                "eletricidade.intoxicantes.quadro_status",
                quadro.fontes().isEmpty()
                        ? Component.translatable("eletricidade.intoxicantes.sem_fonte").getString()
                        : Component.translatable(
                                "eletricidade.intoxicantes.multimetro.fontes",
                                quadro.fontes().size()).getString(),
                Grandezas.formatarE(quadro.disponivelE()),
                Grandezas.formatarW(quadro.consumoW())));
        var cs = quadro.circuitos();
        for (int i = 0; i < cs.size(); i++) {
            CircuitoEletrico c = cs.get(i);
            String estadoChave = switch (c.estado) {
                case CircuitoEletrico.DESARMADO -> "eletricidade.intoxicantes.estado_desarmado";
                case CircuitoEletrico.DESLIGADO -> "eletricidade.intoxicantes.estado_desligado";
                default -> "eletricidade.intoxicantes.estado_ligado";
            };
            linhas.add(Component.translatable("eletricidade.intoxicantes.circuito",
                    "C" + (i + 1), c.nome, Component.translatable(estadoChave).getString(),
                    Grandezas.formatarW(c.consumoW()),
                    Grandezas.formatarW(c.capacidadeW()),
                    Grandezas.formatarA(c.consumoW(), c.tensao),
                    Grandezas.formatarE(c.totalConsumidoE),
                    quadro.limiteBitolaA(i) > 0
                            ? quadro.limiteBitolaA(i) + " A" : "—"));
        }
        return linhas;
    }
}
