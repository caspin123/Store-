package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

public record ConstructTransformPayload(UUID id, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ConstructTransformPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_transform")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructTransformPayload> CODEC =
            StreamCodec.ofMember(ConstructTransformPayload::write, ConstructTransformPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    private static ConstructTransformPayload read(RegistryFriendlyByteBuf buf) {
        return new ConstructTransformPayload(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
