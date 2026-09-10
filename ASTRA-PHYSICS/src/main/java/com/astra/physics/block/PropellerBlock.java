package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Four-blade propeller.
 *
 * <p>The blades repeat every 90 degrees, so four frames at 22.5 degree steps make one complete
 * turn and the loop reads as continuous rotation. The pylon and gearbox are part of the same
 * model but sit outside the swept disc, so they stay still while the blades turn.
 */
public final class PropellerBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);

    public PropellerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SPIN, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return SPIN;
    }

    @Override
    public int frameCount() {
        return 4;
    }

    @Override
    public Drive drive() {
        return Drive.CYCLE;
    }

    @Override
    public double speedFactor() {
        // Fast enough to read as a spinning propeller rather than a turning paddle.
        return 5.5;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // A propeller needs both power and throttle; at idle it should be visibly stopped.
        return enginePower * Math.abs(throttle);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPIN);
    }
}
