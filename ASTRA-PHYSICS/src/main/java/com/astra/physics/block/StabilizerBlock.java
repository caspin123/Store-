package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A vertical fin that makes a craft want to fly straight.
 *
 * <p>Nothing stopped a hull from carrying on turning after the pilot let go of the wheel, so a
 * craft wandered off heading constantly and every turn had to be caught by hand. A fin sitting
 * behind the centre of the hull drags the tail back in line whenever the nose swings, which is
 * the single change that makes an aircraft feel like it flies rather than skids.
 *
 * <p>It works off airflow, so it does nothing at a standstill — that is what a reaction wheel is
 * for.
 */
public final class StabilizerBlock extends AstraFacingBlock implements AnimatedComponent {
    /** How far the trim tab is deflected. Driven by yaw rate, not by the pilot. */
    public static final IntegerProperty TRIM = IntegerProperty.create("trim", 0, 2);
    public static final int NEUTRAL_TRIM = 1;

    public StabilizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TRIM, NEUTRAL_TRIM));
    }

    @Override
    public IntegerProperty frameProperty() {
        return TRIM;
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
        builder.add(TRIM);
    }
}
