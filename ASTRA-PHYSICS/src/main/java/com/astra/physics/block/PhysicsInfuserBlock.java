package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A fixed assembly station: right-click it and everything it is attached to becomes a construct.
 *
 * <p>The wand asks the player to pick two corners, which means a box — and a ship is not a box.
 * Selecting one always means either catching scenery the hull was resting on or missing a mast
 * that stuck out of the corner. The infuser takes the shape the player actually built instead,
 * by following the blocks outward from itself, and it travels with the ship as part of it.
 */
public final class PhysicsInfuserBlock extends AstraFacingBlock implements AnimatedComponent {
    /** 0 = idle, 1 to 3 = the charge-up cycle shown while it is part of a live construct. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 3);

    public PhysicsInfuserBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHARGE, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return CHARGE;
    }

    @Override
    public int frameCount() {
        return 4;
    }

    @Override
    public Drive drive() {
        return Drive.CYCLE_FROM_ONE;
    }

    @Override
    public double speedFactor() {
        return 1.2;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // It glows whenever it is aboard a construct at all, which is its whole job: showing at a
        // glance that this pile of blocks is a ship rather than scenery.
        return 1.0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }
}
