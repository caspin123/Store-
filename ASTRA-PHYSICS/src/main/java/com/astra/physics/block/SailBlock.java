package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Canvas rig. {@code WIND} plays a wave travelling up the sail so the cloth bellies rather than
 * hanging as a flat sheet. Sails catch some wind even with the engines off, so the loop never
 * stops entirely — it just runs slowly.
 */
public final class SailBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final IntegerProperty WIND = IntegerProperty.create("wind", 0, 3);

    public SailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WIND, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return WIND;
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
        // A sail luffs gently; running it at engine speed looks frantic.
        return 0.45;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // Canvas moves in any breeze, so it never fully stops, but it works the sail harder
        // when the helm is asking for speed.
        return 0.35 + 0.65 * Math.abs(throttle);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WIND);
    }
}
