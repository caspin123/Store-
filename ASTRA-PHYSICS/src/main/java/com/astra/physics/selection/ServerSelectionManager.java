package com.astra.physics.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.astra.physics.config.AstraConfig;
import com.astra.physics.network.SelectionSyncPayload;
import com.astra.physics.ship.PhysicsConstructManager;
import com.astra.physics.util.AstraText;

/**
 * Server-authoritative wand selection.
 *
 * <p>The client never decides what is selected; it only draws what the server tells it. That
 * keeps a modified client from assembling blocks it was never allowed to touch.
 */
public final class ServerSelectionManager {
    private static final Map<UUID, SelectionState> STATES = new HashMap<>();

    private ServerSelectionManager() {}

    public static void handleClick(ServerPlayer player, Level level, BlockPos clicked) {
        SelectionState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new SelectionState());
        AstraConfig config = AstraConfig.get();

        if (state.first == null) {
            state.first = clicked.immutable();
            state.second = null;
            state.blocks = List.of();
            AstraText.sendActionBar(player, AstraText.info("selection.point1", format(state.first)));
            sync(player, SelectionSyncPayload.pointOne(state.first));
            return;
        }

        if (state.second == null) {
            long scanVolume = selectionVolume(state.first, clicked);
            if (scanVolume > config.maxSelectionScanVolume) {
                AstraText.sendActionBar(player, AstraText.warning("selection.too_large",
                        scanVolume, config.maxSelectionScanVolume));
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }

            List<BlockPos> blocks = collectSolidBlocks(level, state.first, clicked,
                    config.maxConstructBlocks + 1);
            if (blocks.size() > config.maxConstructBlocks) {
                AstraText.sendActionBar(player, AstraText.warning("selection.rejected_count",
                        config.maxConstructBlocks));
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }
            if (blocks.isEmpty()) {
                AstraText.sendActionBar(player, AstraText.warning("selection.empty"));
                sync(player, SelectionSyncPayload.pointOne(state.first));
                return;
            }

            // Reject the two block kinds assembly can never handle. BlockEntities are allowed:
            // their NBT and inventories are captured before any world block is removed.
            for (BlockPos pos : blocks) {
                BlockState blockState = level.getBlockState(pos);
                if (blockState.is(Blocks.BEDROCK)) {
                    AstraText.sendActionBar(player, AstraText.warning("selection.bedrock", format(pos)));
                    sync(player, SelectionSyncPayload.pointOne(state.first));
                    return;
                }
                if (!blockState.getFluidState().isEmpty()) {
                    AstraText.sendActionBar(player, AstraText.warning("selection.fluid", format(pos)));
                    sync(player, SelectionSyncPayload.pointOne(state.first));
                    return;
                }
            }

            state.second = clicked.immutable();
            state.blocks = List.copyOf(blocks);
            AstraText.sendActionBar(player, AstraText.info("selection.selected",
                    state.blocks.size(), config.maxConstructBlocks));
            sync(player, SelectionSyncPayload.complete(state.first, state.second, state.blocks));
            return;
        }

        // Third click assembles the selection the server already validated.
        if (PhysicsConstructManager.assemble(player, level, state.blocks)) {
            STATES.remove(player.getUUID());
            sync(player, SelectionSyncPayload.clear());
        }
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
        sync(player, SelectionSyncPayload.clear());
        AstraText.sendActionBar(player, AstraText.info("selection.cleared"));
    }

    /** Selections are per-player scratch state and must not outlive the session. */
    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            STATES.remove(player.getUUID());
        }
    }

    public static void clearAll() {
        STATES.clear();
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

        List<BlockPos> blocks = new ArrayList<>(256);
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
