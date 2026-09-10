package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The ship's wheel.
 *
 * <p>{@code STEER} selects one of five wheel positions, from hard to port through centred to hard
 * to starboard. The construct stays server authoritative: the client only changes this property
 * while rendering, so the wheel visibly turns without a BlockEntity and without any packet of its
 * own. The wheel's marked king spoke is what makes all five positions distinguishable — eight
 * evenly spaced spokes alone repeat every 45 degrees.
 */
public final class HelmBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final int CENTRE_STEER = 2;
    public static final IntegerProperty STEER = IntegerProperty.create("steer", 0, 4);

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STEER, CENTRE_STEER));
    }

    @Override
    public IntegerProperty frameProperty() {
        return STEER;
    }

    @Override
    public int frameCount() {
        return 5;
    }

    @Override
    public Drive drive() {
        return Drive.STEER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STEER);
    }
}
