package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A gas envelope that makes a construct lighter than air.
 *
 * <p>Wings only lift once a craft is already fast, which makes taking off the hardest part of
 * flying and leaves no way to hover. A balloon lifts at a standstill, needs no engine, and turns
 * flight into something a player can build toward gradually — add gasbags until the hull floats.
 *
 * <p>Lift thins out with altitude, exactly as it does for a real balloon, which gives an airship a
 * natural ceiling instead of climbing until it leaves the world.
 */
public final class BalloonBlock extends Block implements AnimatedComponent {
    public static final IntegerProperty SWELL = IntegerProperty.create("swell", 0, 3);

    public BalloonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SWELL, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return SWELL;
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
        // A slow breathing swell. Fabric under pressure moves, but it does not flap.
        return 0.30;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // A balloon is passive: it holds gas whether or not anything on the hull is running.
        return 1.0;
    }

    @Override
    public int phaseOffset(int localX, int localY, int localZ) {
        // Neighbouring bags breathe slightly out of step so a large envelope looks like fabric
        // rather than one solid object changing size.
        return localX + localZ;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SWELL);
    }
}
