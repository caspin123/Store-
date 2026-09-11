package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Heading hold.
 *
 * <p>A gyro remembers the direction it was set to and steers back to it whenever the pilot is not
 * turning, so a craft flies a course instead of drifting off it. Take the wheel and it stands
 * aside immediately; let go and it takes the heading you left it on.
 *
 * <p>It steers, it does not push, so it still needs something that can actually turn the hull —
 * a rudder with water moving past it, or a reaction wheel.
 */
public final class GyroBlock extends Block implements AnimatedComponent {
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);

    public GyroBlock(Properties properties) {
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
        return 3.0;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // A gyro's rotor is kept spinning so it has something to measure against.
        return 1.0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPIN);
    }
}
