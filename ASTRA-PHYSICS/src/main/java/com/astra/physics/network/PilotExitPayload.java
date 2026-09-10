package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/** Explicit pilot release, used by Shift/Jump so steering keys never double as walking keys. */
public record PilotExitPayload(UUID constructId) implements CustomPacketPayload {
    public static final Type<PilotExitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "pilot_exit")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PilotExitPayload> CODEC =
            StreamCodec.ofMember(PilotExitPayload::write, PilotExitPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
    }

    private static PilotExitPayload read(RegistryFriendlyByteBuf buf) {
        return new PilotExitPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
