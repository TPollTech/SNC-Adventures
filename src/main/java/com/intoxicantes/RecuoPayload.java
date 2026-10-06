package com.intoxicantes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload S2C de recuo com kick de câmera — compartilhado pela 12 e pelo .38.
 * O servidor manda o chute calculado (pitch pra cima + yaw aleatório) e o client
 * aplica no camera; ~55% volta suavemente em ~12 ticks (constituição de atirador).
 */
public record RecuoPayload(float pitch, float yaw) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecuoPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    IntoxicantesMod.MOD_ID, "recuo_arma"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecuoPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (p, buf) -> {
                        buf.writeFloat(p.pitch());
                        buf.writeFloat(p.yaw());
                    },
                    buf -> new RecuoPayload(buf.readFloat(), buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
