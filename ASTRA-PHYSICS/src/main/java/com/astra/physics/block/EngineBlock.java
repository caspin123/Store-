package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The powerplant. {@code STROKE} turns the flywheel and drives three pistons a third of a cycle
 * apart, so the running speed is a direct read-out of the engine's power setting.
 */
public final class EngineBlock extends AstraFacingBlock implements AnimatedComponent {
    public static final IntegerProperty STROKE = IntegerProperty.create("stroke", 0, 3);

    public EngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STROKE, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return STROKE;
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
        return 1.6;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STROKE);
    }
}
