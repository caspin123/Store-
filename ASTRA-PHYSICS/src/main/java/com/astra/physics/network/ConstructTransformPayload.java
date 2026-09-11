package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.astra.physics.AstraPhysics;

/**
 * Position and heading of a construct.
 *
 * <p>Yaw rides along with position rather than in its own packet: the client has to apply both
 * in the same frame, or a turning ship would be drawn at last tick's angle in this tick's place.
 * It is sent as a compressed short - a tenth of a degree is finer than the eye can follow on a
 * hull, and it keeps this packet, the most frequent one in the mod, small.
 */
public record ConstructTransformPayload(UUID id, double x, double y, double z, float yaw)
        implements CustomPacketPayload {
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
        buf.writeShort((int) Math.round(Mth.wrapDegrees(yaw) * 100.0));
    }

    private static ConstructTransformPayload read(RegistryFriendlyByteBuf buf) {
        return new ConstructTransformPayload(buf.readUUID(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readShort() / 100.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
