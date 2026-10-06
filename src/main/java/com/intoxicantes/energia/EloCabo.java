package com.intoxicantes.energia;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.intoxicantes.CaboEletricoBlock;

/**
 * v1.2.80 — UM ELO DE CABO SUSPENSO (o cabo do Immersive Engineering): dois
 * CONECTORES presos na face de dois blocos da instalação, unidos por um trecho
 * de cobre que atravessa o vão em curva pendente.
 *
 * O vão NÃO é bloco: o elo vive no SavedData da dimensão (CabosSuspensos),
 * então um cabo de 20 blocos custa um elo, não 20 blocos de cobre. A bitola é
 * a do trecho e vale como limite de corrente na rede, igual ao cabo de bloco.
 */
public record EloCabo(BlockPos a, Direction faceA, BlockPos b, Direction faceB,
        double bitola) {

    public static final Codec<EloCabo> CODEC = RecordCodecBuilder.create(instancia ->
            instancia.group(
                    BlockPos.CODEC.fieldOf("a").forGetter(EloCabo::a),
                    Direction.CODEC.fieldOf("face_a").forGetter(EloCabo::faceA),
                    BlockPos.CODEC.fieldOf("b").forGetter(EloCabo::b),
                    Direction.CODEC.fieldOf("face_b").forGetter(EloCabo::faceB),
                    Codec.DOUBLE.fieldOf("bitola").forGetter(EloCabo::bitola))
                    .apply(instancia, EloCabo::new));

    public static final Codec<List<EloCabo>> LIST_CODEC = CODEC.listOf();

    /** Uma das pontas do elo: o conector e a face para onde ele aponta. */
    public record Extremo(BlockPos pos, Direction face,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensao) {
        public Extremo(BlockPos pos, Direction face) {
            this(pos, face, net.minecraft.world.level.Level.OVERWORLD);
        }
        public static final Codec<Extremo> CODEC = RecordCodecBuilder.create(instancia ->
                instancia.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(Extremo::pos),
                        Direction.CODEC.fieldOf("face").forGetter(Extremo::face),
                        net.minecraft.resources.ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
                                .optionalFieldOf("dimensao", net.minecraft.world.level.Level.OVERWORLD)
                                .forGetter(Extremo::dimensao))
                        .apply(instancia, Extremo::new));

        public static final net.minecraft.network.codec.StreamCodec<
                net.minecraft.network.RegistryFriendlyByteBuf, Extremo> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Extremo::pos,
                        Direction.STREAM_CODEC, Extremo::face,
                        net.minecraft.resources.ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION),
                        Extremo::dimensao, Extremo::new);
    }

    /** A ponta que fica em {@code pos} (a outra é o destino). */
    public Extremo extremoEm(BlockPos pos) {
        return a.equals(pos) ? new Extremo(a, faceA) : new Extremo(b, faceB);
    }

    /** A outra ponta do elo (o cabo atravessa para cá). */
    public Extremo outroExtremo(BlockPos pos) {
        return a.equals(pos) ? new Extremo(b, faceB) : new Extremo(a, faceA);
    }

    public boolean toca(BlockPos pos) {
        return a.equals(pos) || b.equals(pos);
    }

    /** O vão em passos de bloco (Manhattan: o caminho que o cobre percorre). */
    public int comprimento() {
        return a.distManhattan(b);
    }

    /** A corrente máxima da bitola deste trecho. */
    public long amperagem() {
        return CaboEletricoBlock.amperagemDaBitola(bitola);
    }

    /** Mesmo par de conectores (impede elo duplicado entre os mesmos pontos). */
    public boolean mesmoPar(EloCabo outro) {
        return (a.equals(outro.a) && b.equals(outro.b))
                || (a.equals(outro.b) && b.equals(outro.a));
    }
}