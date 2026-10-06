package com.intoxicantes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * O estado mecanico do .38 (v1.2.33) — mesmo padrão da 12 (v1.2.32): vive num
 * DataComponent da stack, acompanha o item no baú, no chão, no inventário e
 * pela rede — cada revólver é UM tambor de verdade.
 *
 * anatomia do tambor:
 *   TAMBORES = bitmask de 6 bits (bit i = camara i carregada) — o .38 carrega
 *              os 6 juntos, um em cada buraco do tambor
 *   CÂMARA   = índice da camara alinhada com o cano (avança a cada tiro)
 *   FERRAO   = fase FERRAMENTA: o ferrolho fecha o tambor depois do giro
 *
 * Fases do mecanismo:
 *   PRONTA       = nada em andamento
 *   FERRAMENTA   = fecho do ferrolho apos o giro do tambor (recarga e pós-tiro)
 *   RECARREGANDO = jogador com o botao direito segurado; shells entram um a um
 *                  nos buracos vagos (duracao no estado = client/servidor juntos)
 *   TECLA        = recarga pela TECLA R (v1.2.41): balas entram pelo motor do
 *                  mecanismo (inventoryTick), sem travar a pose; soltar R fecha
 *                  o tambor com o fecho — o que entrou, ficou.
 */
public record RevolverEstado(int tambores, int camara, int timer, byte fase) {

    public static final byte FASE_PRONTA = 0;
    public static final byte FASE_FERRAMENTA = 1;
    public static final byte FASE_RECARREGANDO = 2;
    /** v1.2.41: recarga em curso disparada pela tecla R (não usa o botão direito). */
    public static final byte FASE_TECLA = 3;

    public static final int CAPACIDADE = 6;

    public static final RevolverEstado VAZIA =
            new RevolverEstado(0, 0, 0, FASE_PRONTA);

    public static final Codec<RevolverEstado> CODEC = RecordCodecBuilder.create(instancia -> instancia
            .group(Codec.INT.fieldOf("tambores").forGetter(RevolverEstado::tambores),
                    Codec.INT.fieldOf("camara").forGetter(RevolverEstado::camara),
                    Codec.INT.fieldOf("timer").forGetter(RevolverEstado::timer),
                    Codec.BYTE.fieldOf("fase").forGetter(RevolverEstado::fase))
            .apply(instancia, RevolverEstado::new));

    /** Sincroniza com o client (o HUD e a pose do item precisam do estado). */
    public static final StreamCodec<ByteBuf, RevolverEstado> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RevolverEstado::tambores,
            ByteBufCodecs.VAR_INT, RevolverEstado::camara,
            ByteBufCodecs.VAR_INT, RevolverEstado::timer,
            ByteBufCodecs.BYTE, RevolverEstado::fase,
            RevolverEstado::new);

    /** Camara i (0..5) carregada? */
    public boolean cheio(int i) {
        return (tambores & (1 << i)) != 0;
    }

    /** Total de balas no tambor. */
    public int balas() {
        return Integer.bitCount(tambores & 0b111111);
    }

    /** A camara alinhada com o cano tem bala? (pro gatilho) */
    public boolean alinhadaCarregada() {
        return cheio(camara % CAPACIDADE);
    }

    /** Existe buraco vago no tambor? (pro botao de recarga) */
    public boolean temBuracoVago() {
        return (tambores & 0b111111) != 0b111111;
    }

    /** Pronta pro gatilho (nada em andamento)? */
    public boolean pronta() {
        return fase == FASE_PRONTA && timer <= 0;
    }

    /** Recua o timer do mecanismo; chega a zero = fase completou. */
    public RevolverEstado tictac() {
        return new RevolverEstado(tambores, camara, Math.max(0, timer - 1), fase);
    }

    /** v1.2.41: mesmo estado com outro timer (fase preservada — usada na TECLA). */
    public RevolverEstado comTimer(int novoTimer) {
        return new RevolverEstado(tambores, camara, novoTimer, fase);
    }

    /** Carrega o buraco i (bit set). */
    public RevolverEstado carregar(int i) {
        return new RevolverEstado(tambores | (1 << i), camara, timer, fase);
    }

    /** Descarrega a camara alinhada e gira o tambor pro proximo buraco. */
    public RevolverEstado dispararEGirar() {
        int restante = tambores & ~(1 << (camara % CAPACIDADE));
        return new RevolverEstado(restante, (camara + 1) % CAPACIDADE, timer, fase);
    }
}
