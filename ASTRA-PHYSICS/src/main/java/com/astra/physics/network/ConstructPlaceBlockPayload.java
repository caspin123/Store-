package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import com.astra.physics.AstraPhysics;

/** Client request to place a BlockItem onto a moving ASTRA construct. */
public record ConstructPlaceBlockPayload(
        UUID constructId,
        int localX,
        int localY,
        int localZ,
        Direction face,
        InteractionHand hand
) implements CustomPacketPayload {
    public static final Type<ConstructPlaceBlockPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_place_block")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructPlaceBlockPayload> CODEC =
            StreamCodec.ofMember(ConstructPlaceBlockPayload::write, ConstructPlaceBlockPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeVarInt(localX);
        buf.writeVarInt(localY);
        buf.writeVarInt(localZ);
        buf.writeByte(face.ordinal());
        buf.writeByte(hand.ordinal());
    }

    private static ConstructPlaceBlockPayload read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int z = buf.readVarInt();
        int faceIndex = Math.max(0, Math.min(Direction.values().length - 1, buf.readUnsignedByte()));
        int handIndex = Math.max(0, Math.min(InteractionHand.values().length - 1, buf.readUnsignedByte()));
        return new ConstructPlaceBlockPayload(id, x, y, z, Direction.values()[faceIndex], InteractionHand.values()[handIndex]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
