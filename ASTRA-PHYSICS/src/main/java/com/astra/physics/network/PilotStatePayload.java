package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/** Server acknowledgement for ASTRA helm grip state and the exact local helm cell. */
public record PilotStatePayload(
        UUID constructId,
        boolean active,
        int helmX,
        int helmY,
        int helmZ
) implements CustomPacketPayload {
    public static final Type<PilotStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "pilot_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PilotStatePayload> CODEC =
            StreamCodec.ofMember(PilotStatePayload::write, PilotStatePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeBoolean(active);
        buf.writeVarInt(helmX);
        buf.writeVarInt(helmY);
        buf.writeVarInt(helmZ);
    }

    private static PilotStatePayload read(RegistryFriendlyByteBuf buf) {
        return new PilotStatePayload(
                buf.readUUID(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
