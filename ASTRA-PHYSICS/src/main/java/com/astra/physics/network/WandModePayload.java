package com.astra.physics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/**
 * Client asking to advance its physics wand to the next mode.
 *
 * <p>It carries no mode of its own on purpose: the server holds the authoritative mode and simply
 * steps it, so a modified client cannot jump straight to a mode it should not be in, and the two
 * sides cannot disagree about which mode is active.
 */
public record WandModePayload() implements CustomPacketPayload {
    public static final WandModePayload INSTANCE = new WandModePayload();

    public static final Type<WandModePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "wand_mode")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, WandModePayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
