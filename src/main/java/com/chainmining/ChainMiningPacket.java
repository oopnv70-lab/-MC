package com.chainmining;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Network packet: client → server
 * Tells the server the player is chain mining (just a boolean flag).
 */
public record ChainMiningPacket(boolean enabled) implements CustomPacketPayload {
    public static final Type<ChainMiningPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChainMiningMod.MOD_ID, "chain_mine"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ChainMiningPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, ChainMiningPacket::enabled,
            ChainMiningPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}