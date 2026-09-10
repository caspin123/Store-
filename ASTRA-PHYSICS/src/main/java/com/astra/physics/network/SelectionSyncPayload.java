package com.astra.physics.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/**
 * Server -> client snapshot of the wand selection.
 * Stage: 0=clear, 1=point1, 2=validated point1+point2+selected blocks.
 */
public record SelectionSyncPayload(
        int stage,
        int firstX, int firstY, int firstZ,
        int secondX, int secondY, int secondZ,
        List<BlockPos> blocks
) implements CustomPacketPayload {
    public static final Type<SelectionSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "selection_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectionSyncPayload> CODEC =
            StreamCodec.ofMember(SelectionSyncPayload::write, SelectionSyncPayload::read);

    public static SelectionSyncPayload clear() {
        return new SelectionSyncPayload(0, 0, 0, 0, 0, 0, 0, List.of());
    }

    public static SelectionSyncPayload pointOne(BlockPos first) {
        return new SelectionSyncPayload(
                1,
                first.getX(), first.getY(), first.getZ(),
                0, 0, 0,
                List.of()
        );
    }

    public static SelectionSyncPayload complete(BlockPos first, BlockPos second, List<BlockPos> blocks) {
        return new SelectionSyncPayload(
                2,
                first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ(),
                List.copyOf(blocks)
        );
    }

    public BlockPos first() {
        return stage >= 1 ? new BlockPos(firstX, firstY, firstZ) : null;
    }

    public BlockPos second() {
        return stage >= 2 ? new BlockPos(secondX, secondY, secondZ) : null;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(stage);
        if (stage >= 1) {
            buf.writeInt(firstX);
            buf.writeInt(firstY);
            buf.writeInt(firstZ);
        }
        if (stage >= 2) {
            buf.writeInt(secondX);
            buf.writeInt(secondY);
            buf.writeInt(secondZ);
            buf.writeVarInt(blocks.size());
            for (BlockPos pos : blocks) {
                buf.writeInt(pos.getX());
                buf.writeInt(pos.getY());
                buf.writeInt(pos.getZ());
            }
        }
    }

    private static SelectionSyncPayload read(RegistryFriendlyByteBuf buf) {
        int stage = buf.readVarInt();
        if (stage <= 0) {
            return clear();
        }

        int firstX = buf.readInt();
        int firstY = buf.readInt();
        int firstZ = buf.readInt();
        if (stage == 1) {
            return new SelectionSyncPayload(1, firstX, firstY, firstZ, 0, 0, 0, List.of());
        }

        int secondX = buf.readInt();
        int secondY = buf.readInt();
        int secondZ = buf.readInt();
        int encodedCount = buf.readVarInt();
        int count = Math.min(encodedCount, AstraPhysics.HARD_BLOCK_LIMIT);
        List<BlockPos> blocks = new ArrayList<>(count);
        for (int i = 0; i < encodedCount; i++) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            if (i < count) {
                blocks.add(new BlockPos(x, y, z));
            }
        }
        return new SelectionSyncPayload(
                2, firstX, firstY, firstZ, secondX, secondY, secondZ, List.copyOf(blocks)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
