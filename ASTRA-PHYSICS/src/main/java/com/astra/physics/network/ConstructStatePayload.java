package com.astra.physics.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/**
 * Live drivetrain state, so clients can animate a construct's components.
 *
 * <p>Without this the client knows only where a construct is, not whether its engines are running
 * — which would leave propellers spinning on a parked ship and thrusters burning with the power
 * off. Throttle and steer are quantised to a byte each so the packet is only sent when a value
 * actually changes enough to alter a visible frame, rather than on every control packet.
 */
public record ConstructStatePayload(
        UUID constructId,
        int enginePowerStep,
        boolean aircraftMode,
        byte throttle,
        byte steer
) implements CustomPacketPayload {
    public static final Type<ConstructStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructStatePayload> CODEC =
            StreamCodec.ofMember(ConstructStatePayload::write, ConstructStatePayload::read);

    /** Quantises a -1..1 control input to a signed byte. */
    public static byte quantise(float value) {
        float clamped = Math.max(-1.0F, Math.min(1.0F, Float.isNaN(value) ? 0.0F : value));
        return (byte) Math.round(clamped * 100.0F);
    }

    public float throttleFraction() {
        return Math.max(-1.0F, Math.min(1.0F, throttle / 100.0F));
    }

    public float steerFraction() {
        return Math.max(-1.0F, Math.min(1.0F, steer / 100.0F));
    }

    /** Engine power as a 0..1 scale, matching the server's own power steps. */
    public float powerScale() {
        return Math.max(0, Math.min(4, enginePowerStep)) / 4.0F;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(constructId);
        buf.writeByte(enginePowerStep);
        buf.writeBoolean(aircraftMode);
        buf.writeByte(throttle);
        buf.writeByte(steer);
    }

    private static ConstructStatePayload read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int power = Math.max(0, Math.min(4, buf.readUnsignedByte()));
        boolean aircraft = buf.readBoolean();
        return new ConstructStatePayload(id, power, aircraft, buf.readByte(), buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
