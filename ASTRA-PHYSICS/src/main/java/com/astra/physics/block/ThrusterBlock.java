package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Lift thruster. Frame 0 is a cold nozzle with no plume; frames 1 to 3 are the burn cycle, so a
 * thruster that is not producing lift looks unmistakably inert.
 */
public final class ThrusterBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final IntegerProperty BURN = IntegerProperty.create("burn", 0, 3);

    public ThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BURN, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return BURN;
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
        return 4.0;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // Reverse throttle produces no lift, so the nozzle stays cold.
        return enginePower * Math.max(0.0, throttle);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BURN);
    }
}
