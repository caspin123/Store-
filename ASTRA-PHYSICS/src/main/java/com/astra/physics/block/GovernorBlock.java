package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A speed limiter.
 *
 * <p>Engine power is a single construct-wide setting, so the only way to travel slowly was to run
 * the engine down and lose the ability to manoeuvre with it. A governor caps top speed
 * independently of power, which is what lets a craft dock, hold formation or creep through
 * terrain with full authority still available.
 *
 * <p>Clockwork does this with a redstone resistor trimming shaft RPM. Constructs have no redstone,
 * so the cap is set by hand on the block instead.
 */
public final class GovernorBlock extends AstraFacingBlock implements AnimatedComponent {
    /** Quarter steps: 0 = 25%, 1 = 50%, 2 = 75%, 3 = unrestricted. */
    public static final IntegerProperty LIMIT = IntegerProperty.create("limit", 0, 3);
    public static final int UNRESTRICTED = 3;

    public GovernorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIMIT, UNRESTRICTED));
    }

    @Override
    public IntegerProperty frameProperty() {
        return LIMIT;
    }

    @Override
    public int frameCount() {
        return 4;
    }

    @Override
    public Drive drive() {
        // The dial shows the setting the player dialled in, so nothing should move it.
        return Drive.MANUAL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIMIT);
    }
}
