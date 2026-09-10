package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * ASTRA helm with a tiny render-state steering property.
 * The construct remains server authoritative; the client only changes this property while
 * rendering so the wheel can visibly turn left/centre/right without a BlockEntity.
 */
public final class HelmBlock extends AstraFacingBlock {
    public static final IntegerProperty STEER = IntegerProperty.create("steer", 0, 2);

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STEER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STEER);
    }
}
