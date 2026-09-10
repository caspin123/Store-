package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/** Client request to break a construct-local block. */
public record ConstructBreakBlockPayload(UUID constructId, int localX, int localY, int localZ)
        implements CustomPacketPayload {
    public static final Type<ConstructBreakBlockPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_break_block")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructBreakBlockPayload> CODEC =
            StreamCodec.ofMember(ConstructBreakBlockPayload::write, ConstructBreakBlockPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeVarInt(localX);
        buf.writeVarInt(localY);
        buf.writeVarInt(localZ);
    }

    private static ConstructBreakBlockPayload read(RegistryFriendlyByteBuf buf) {
        return new ConstructBreakBlockPayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
