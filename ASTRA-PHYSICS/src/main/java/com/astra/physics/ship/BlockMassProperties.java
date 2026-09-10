package com.astra.physics.ship;

import java.util.Collection;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Alpha mass policy: every assembled non-air block has exactly the same mass.
 *
 * <p>Material-dependent density is deliberately switched off for now so buoyancy, propulsion and
 * flight can all be tuned against one predictable baseline on desktop and on Android/FCL alike.
 * The policy stays centralised here so a future data-driven density system can be added without
 * touching the solver.
 */
public final class BlockMassProperties {
    public static final double UNIFORM_BLOCK_MASS = 1.0;

    private BlockMassProperties() {}

    public static double massOf(BlockState state) {
        return UNIFORM_BLOCK_MASS;
    }

    public static double totalMass(Collection<StoredBlock> blocks) {
        return Math.max(UNIFORM_BLOCK_MASS, blocks.size() * UNIFORM_BLOCK_MASS);
    }
}
