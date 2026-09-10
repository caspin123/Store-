package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Lifting surface with a working aileron.
 *
 * <p>{@code AILERON} deflects the trailing edge down, neutral or up with the pilot's steering
 * input, which gives an aircraft a visible control response instead of a rigid plank — and makes
 * it obvious at a glance which way a craft is being told to roll.
 */
public final class WingBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final int NEUTRAL_AILERON = 1;
    public static final IntegerProperty AILERON = IntegerProperty.create("aileron", 0, 2);

    public WingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AILERON, NEUTRAL_AILERON));
    }

    @Override
    public IntegerProperty frameProperty() {
        return AILERON;
    }

    @Override
    public int frameCount() {
        return 3;
    }

    @Override
    public Drive drive() {
        return Drive.STEER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AILERON);
    }
}
