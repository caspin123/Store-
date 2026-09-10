package com.astra.physics.client.selection;

import java.util.List;

import net.minecraft.core.BlockPos;

import com.astra.physics.network.SelectionSyncPayload;

/** Client mirror of the server-authoritative wand selection. */
public final class ClientSelectionManager {
    private static BlockPos first;
    private static BlockPos second;
    private static List<BlockPos> selectedBlocks = List.of();

    private ClientSelectionManager() {}

    public static void applyServerSync(SelectionSyncPayload payload) {
        if (payload.stage() <= 0) {
            clearSilently();
            return;
        }

        first = payload.first();
        second = payload.second();
        selectedBlocks = payload.stage() >= 2 ? List.copyOf(payload.blocks()) : List.of();
    }

    public static void clearSilently() {
        first = null;
        second = null;
        selectedBlocks = List.of();
    }

    public static BlockPos first() { return first; }
    public static BlockPos second() { return second; }
    public static List<BlockPos> selectedBlocks() { return selectedBlocks; }
    public static boolean hasSelection() { return first != null; }
}
