package com.astra.physics.ship;

import net.minecraft.world.level.block.state.BlockState;

/** A block stored in construct-local coordinates, optionally with BlockEntity data. */
public record StoredBlock(
        int localX,
        int localY,
        int localZ,
        BlockState state,
        ConstructBlockEntityData blockEntityData
) {
    public StoredBlock(int localX, int localY, int localZ, BlockState state) {
        this(localX, localY, localZ, state, null);
    }

    public boolean hasBlockEntityData() {
        return blockEntityData != null;
    }
}
