package com.astra.physics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

public record ConstructSpawnPayload(
        UUID id,
        double x, double y, double z,
        float yaw,
        int sizeX, int sizeY, int sizeZ,
        List<NetBlock> blocks
) implements CustomPacketPayload {
    public static final Type<ConstructSpawnPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "construct_spawn")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructSpawnPayload> CODEC =
            StreamCodec.ofMember(ConstructSpawnPayload::write, ConstructSpawnPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        buf.writeVarInt(blocks.size());
        for (NetBlock block : blocks) {
            buf.writeVarInt(block.localX());
            buf.writeVarInt(block.localY());
            buf.writeVarInt(block.localZ());
            buf.writeVarInt(block.stateId());
        }
    }

    private static ConstructSpawnPayload read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        int sizeX = buf.readVarInt();
        int sizeY = buf.readVarInt();
        int sizeZ = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), AstraPhysics.HARD_BLOCK_LIMIT);
        List<NetBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(new NetBlock(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new ConstructSpawnPayload(id, x, y, z, yaw, sizeX, sizeY, sizeZ, List.copyOf(blocks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record NetBlock(int localX, int localY, int localZ, int stateId) {}
}
