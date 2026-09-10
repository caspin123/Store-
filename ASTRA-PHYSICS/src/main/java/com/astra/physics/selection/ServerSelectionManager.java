package com.astra.physics.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.astra.physics.AstraPhysics;
import com.astra.physics.network.SelectionSyncPayload;
import com.astra.physics.ship.PhysicsConstructManager;

/** Server-authoritative wand selection state. */
public final class ServerSelectionManager {
    private static final long MAX_SCAN_VOLUME = 65_536L;
    private static final Map<UUID, SelectionState> STATES = new HashMap<>();

    private ServerSelectionManager() {}

    public static void handleClick(ServerPlayer player, Level level, BlockPos clicked) {
        SelectionState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new SelectionState());

        if (state.first == null) {
            state.first = clicked.immutable();
            state.second = null;
            state.blocks = List.of();
            player.displayClientMessage(Component.literal(
                    "ASTRA: Point 1 selected at " + format(state.first)
                            + " | Right-click Point 2 | Shift+Right-click = clear"
            ), true);
            sync(player, SelectionSyncPayload.pointOne(state.first));
            return;
        }

        if (state.second == null) {
            long scanVolume = selectionVolume(state.first, clicked);
            if (scanVolume > MAX_SCAN_VOLUME) {
                player.displayClientMessage(Component.literal(
                        "ASTRA: Area is too large to scan safely (" + scanVolume + " cells)."
                ), true);
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }

            List<BlockPos> blocks = collectSolidBlocks(level, state.first, clicked, AstraPhysics.MAX_SELECTED_BLOCKS + 1);
            if (blocks.size() > AstraPhysics.MAX_SELECTED_BLOCKS) {
                player.displayClientMessage(Component.literal(
                        "ASTRA: Selection rejected: more than " + AstraPhysics.MAX_SELECTED_BLOCKS + " non-air blocks."
                ), true);
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }
            if (blocks.isEmpty()) {
                player.displayClientMessage(Component.literal("ASTRA: Selection contains no blocks."), true);
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }

            // Safety rules: reject terrain-only blocks/fluids; BlockEntities are captured safely during assembly.
            for (BlockPos pos : blocks) {
                BlockState blockState = level.getBlockState(pos);
                if (blockState.is(Blocks.BEDROCK)) {
                    player.displayClientMessage(Component.literal(
                            "ASTRA: Bedrock cannot be assembled (" + format(pos) + ")."
                    ), true);
                    sync(player, SelectionSyncPayload.pointOne(state.first));
                    return;
                }
                if (!blockState.getFluidState().isEmpty()) {
                    player.displayClientMessage(Component.literal(
                            "ASTRA: Fluids/waterlogged blocks are not supported yet (" + format(pos) + ")."
                    ), true);
                    sync(player, SelectionSyncPayload.pointOne(state.first));
                    return;
                }
                // BlockEntities are allowed from 0.0.6 onward. Their NBT/inventory is
                // captured server-side during assembly before any world block is removed.
            }

            state.second = clicked.immutable();
            state.blocks = List.copyOf(blocks);
            player.displayClientMessage(Component.literal(
                    "ASTRA: Selected " + state.blocks.size() + "/" + AstraPhysics.MAX_SELECTED_BLOCKS
                            + " blocks | Right-click again = ASSEMBLE | Shift+Right-click = clear"
            ), true);
            sync(player, SelectionSyncPayload.complete(state.first, state.second, state.blocks));
            return;
        }

        // Third normal click = assemble the already validated server-side selection.
        if (PhysicsConstructManager.assemble(player, level, state.blocks)) {
            STATES.remove(player.getUUID());
            sync(player, SelectionSyncPayload.clear());
        }
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
        sync(player, SelectionSyncPayload.clear());
        player.displayClientMessage(Component.literal("ASTRA: Selection cleared."), true);
    }

    private static void sync(ServerPlayer player, SelectionSyncPayload payload) {
        if (ServerPlayNetworking.canSend(player, SelectionSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static List<BlockPos> collectSolidBlocks(Level level, BlockPos a, BlockPos b, int stopAfter) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        List<BlockPos> blocks = new ArrayList<>(Math.min(AstraPhysics.MAX_SELECTED_BLOCKS, 256));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    if (!level.isEmptyBlock(cursor)) {
                        blocks.add(cursor.immutable());
                        if (blocks.size() >= stopAfter) {
                            return blocks;
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static long selectionVolume(BlockPos a, BlockPos b) {
        long dx = Math.abs((long) b.getX() - a.getX()) + 1L;
        long dy = Math.abs((long) b.getY() - a.getY()) + 1L;
        long dz = Math.abs((long) b.getZ() - a.getZ()) + 1L;
        return dx * dy * dz;
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static final class SelectionState {
        private BlockPos first;
        private BlockPos second;
        private List<BlockPos> blocks = List.of();
    }
}
