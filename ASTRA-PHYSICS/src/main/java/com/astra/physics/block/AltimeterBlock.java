package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Altitude hold.
 *
 * <p>Clockwork's altimeter emits a redstone signal near a target height and lets the builder wire
 * that into whatever they like. That cannot work here: construct blocks are not world blocks, so
 * nothing on a moving hull ticks and there is no redstone to emit into. The useful half of the
 * idea survives without it — right-click to set the height you are at, and the construct holds it
 * on its own.
 *
 * <p>Holding altitude needs something that can push vertically, so it does nothing without
 * thrusters or gas envelopes to work with.
 */
public final class AltimeterBlock extends AstraFacingBlock implements AnimatedComponent {
    /** 0 = idle, 1 = armed and holding a target. */
    public static final IntegerProperty READOUT = IntegerProperty.create("readout", 0, 1);

    public AltimeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(READOUT, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return READOUT;
    }

    @Override
    public int frameCount() {
        return 2;
    }

    @Override
    public Drive drive() {
        // Armed or not is a state the player set, so it is stored on the block rather than
        // recomputed every frame.
        return Drive.MANUAL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(READOUT);
    }
}
