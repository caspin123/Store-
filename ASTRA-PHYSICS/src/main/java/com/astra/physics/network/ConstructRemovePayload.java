package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/** Removes an empty/deleted ASTRA construct from the client. */
public record ConstructRemovePayload(UUID constructId) implements CustomPacketPayload {
    public static final Type<ConstructRemovePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_remove")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructRemovePayload> CODEC =
            StreamCodec.ofMember(ConstructRemovePayload::write, ConstructRemovePayload::read);

    private void write(RegistryFriendlyByteBuf buf) { buf.writeUUID(constructId); }
    private static ConstructRemovePayload read(RegistryFriendlyByteBuf buf) {
        return new ConstructRemovePayload(buf.readUUID());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
