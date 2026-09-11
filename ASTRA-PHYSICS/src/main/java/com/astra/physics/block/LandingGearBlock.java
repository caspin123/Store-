package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A leg that extends as the craft comes down and folds away once it is up.
 *
 * <p>Clockwork's Extendon is a shaft that telescopes under power. The useful thing that buys on a
 * flying machine is landing gear, and the part worth keeping is that it moves on its own: a pilot
 * on final approach has throttle, heading and altitude to think about, and remembering to put the
 * wheels down is exactly the kind of thing that gets forgotten once.
 *
 * <p>So it reads the construct's height above the ground rather than any control, and deploys
 * without being asked.
 */
public final class LandingGearBlock extends AstraFacingBlock implements AnimatedComponent {
    /** 0 = folded, 3 = fully extended. */
    public static final IntegerProperty EXTENSION = IntegerProperty.create("extension", 0, 3);
    public static final int FOLDED = 0;
    public static final int DEPLOYED = 3;

    public LandingGearBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(EXTENSION, DEPLOYED));
    }

    @Override
    public IntegerProperty frameProperty() {
        return EXTENSION;
    }

    @Override
    public int frameCount() {
        return 4;
    }

    @Override
    public Drive drive() {
        // Extension follows ground clearance, which only the server can measure, so it is stored
        // on the block rather than recomputed while rendering.
        return Drive.MANUAL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EXTENSION);
    }
}
