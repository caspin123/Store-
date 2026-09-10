package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import com.astra.physics.AstraPhysics;

/** Right-click interaction against a construct-local block. */
public record ConstructInteractPayload(UUID constructId, int localX, int localY, int localZ, InteractionHand hand)
        implements CustomPacketPayload {
    public static final Type<ConstructInteractPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_interact")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructInteractPayload> CODEC =
            StreamCodec.ofMember(ConstructInteractPayload::write, ConstructInteractPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeVarInt(localX);
        buf.writeVarInt(localY);
        buf.writeVarInt(localZ);
        buf.writeByte(hand.ordinal());
    }

    private static ConstructInteractPayload read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int z = buf.readVarInt();
        int hand = Math.max(0, Math.min(InteractionHand.values().length - 1, buf.readUnsignedByte()));
        return new ConstructInteractPayload(id, x, y, z, InteractionHand.values()[hand]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
