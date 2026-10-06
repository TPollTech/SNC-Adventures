package com.intoxicantes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/**
 * O estado mecanico da 12 (v1.2.32): vive num DataComponent da stack, entao
 * acompanha o item no bau, no chao, no inventario e pela rede — cada escopeta
 * e' UM mecanismo de verdade, nao mais "cartucho sumido do inventario".
 *
 * anatomia pump-action:
 *   TUBO    = cartuchos reserva (alimentados shell-by-shell na recarga)
 *   CAMARA  = 1 cartucho engatilhado, pronto pro gatilho
 *   BOMBA   = ciclo do pump (ejetando o vazio + camara o proximo do tubo)
 *
 * Fases do mecanismo:
 *   PRONTA        = nada em andamento
 *   BOMBA         = timer conta o ciclo do pump; ao terminar, camara fica cheia
 *   RECARREGANDO  = jogador com o botao direito segurado; timer e' a DURACAO
 *                   total do uso (cada shell insere a cada intervalo) — duração
 *                   estável no estado = cliente e servidor terminam juntos.
 *   TECLA         = recarga pela TECLA R (v1.2.41): shells entram pelo motor do
 *                   mecanismo (inventoryTick), sem travar a pose; soltar R (ou
 *                   tubo cheio/reserva no fim) fecha — o que entrou, ficou.
 */
public record EscopetaEstado(int noTubo, boolean camara, int timer, byte fase) {

    public static final byte FASE_PRONTA = 0;
    public static final byte FASE_BOMBA = 1;
    public static final byte FASE_RECARREGANDO = 2;
    /** v1.2.41: recarga em curso disparada pela tecla R (não usa o botão direito). */
    public static final byte FASE_TECLA = 3;

    public static final EscopetaEstado VAZIA = new EscopetaEstado(0, false, 0, FASE_PRONTA);

    public static final Codec<EscopetaEstado> CODEC = RecordCodecBuilder.create(instancia -> instancia
            .group(Codec.INT.fieldOf("no_tubo").forGetter(EscopetaEstado::noTubo),
                    Codec.BOOL.fieldOf("camara").forGetter(EscopetaEstado::camara),
                    Codec.INT.fieldOf("timer").forGetter(EscopetaEstado::timer),
                    Codec.BYTE.fieldOf("fase").forGetter(EscopetaEstado::fase))
            .apply(instancia, EscopetaEstado::new));

    /** Sincroniza com o client (o HUD e a pose do item precisam do estado). */
    public static final StreamCodec<ByteBuf, EscopetaEstado> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EscopetaEstado::noTubo,
            ByteBufCodecs.BOOL, EscopetaEstado::camara,
            ByteBufCodecs.VAR_INT, EscopetaEstado::timer,
            ByteBufCodecs.BYTE, EscopetaEstado::fase,
            EscopetaEstado::new);

    /** Pronta pro gatilho (nada em andamento)? */
    public boolean pronta() {
        return fase == FASE_PRONTA && timer <= 0;
    }

    /** Recua o timer do mecanismo; chega a zero = fase completou. */
    public EscopetaEstado tictac() {
        return new EscopetaEstado(noTubo, camara, Math.max(0, timer - 1), fase);
    }
}
