package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/** W/S throttle and A/D steering input while a player is piloting an ASTRA helm. */
public record ConstructControlPayload(UUID constructId, float throttle, float steer)
        implements CustomPacketPayload {
    public static final Type<ConstructControlPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_control")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructControlPayload> CODEC =
            StreamCodec.ofMember(ConstructControlPayload::write, ConstructControlPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeFloat(throttle);
        buf.writeFloat(steer);
    }

    private static ConstructControlPayload read(RegistryFriendlyByteBuf buf) {
        return new ConstructControlPayload(buf.readUUID(), buf.readFloat(), buf.readFloat());
    }

    public float safeThrottle() { return Math.max(-1.0F, Math.min(1.0F, throttle)); }
    public float safeSteer() { return Math.max(-1.0F, Math.min(1.0F, steer)); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
