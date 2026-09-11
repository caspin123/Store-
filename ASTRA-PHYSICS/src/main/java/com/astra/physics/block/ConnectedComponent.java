package com.astra.physics.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A component whose model changes shape depending on the components next to it.
 *
 * <p>Wings and sails used to be fixed-size objects: one block was one whole wing, so the only
 * wing you could ever have was the size the model happened to be. Making them tile means the
 * player builds a wing or a sail at whatever size they want by placing more blocks, and the
 * physics already scales with the count.
 *
 * <p>Connections are resolved by the construct renderer from the hull's own block list, not by
 * world neighbour updates. A wing only matters once it is part of a ship, and a construct's
 * blocks are not world blocks, so there are no neighbour updates to hook in the first place.
 */
public interface ConnectedComponent {
    /** The blockstate property that selects which piece of the run this block draws. */
    IntegerProperty connectionProperty();

    /**
     * Chooses the piece to draw.
     *
     * @param facing     this block's own facing
     * @param neighbours tests whether another block of the same kind sits at a local offset
     */
    int connectionValue(Direction facing, NeighbourQuery neighbours);

    /** Tests the construct's block list for a component of the same kind at an offset. */
    @FunctionalInterface
    interface NeighbourQuery {
        boolean sameKindAt(int dx, int dy, int dz);
    }
}
