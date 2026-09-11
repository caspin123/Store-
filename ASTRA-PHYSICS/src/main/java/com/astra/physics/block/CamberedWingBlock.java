package com.astra.physics.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A wing with a built-in angle of attack.
 *
 * <p>A flat wing only lifts once it is moving fast; a cambered one is curved so it lifts hard at
 * low speed, which is what gets a heavy hull off the ground. The curve costs drag, so a craft
 * built entirely from cambered wings climbs well and then refuses to go anywhere — the trade is
 * the point, and it is why a real aircraft mixes the two.
 */
public final class CamberedWingBlock extends AstraFacingBlock
        implements AnimatedComponent, ConnectedComponent {
    public static final int NEUTRAL_AILERON = 1;
    public static final IntegerProperty AILERON = IntegerProperty.create("aileron", 0, 2);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    public CamberedWingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AILERON, NEUTRAL_AILERON)
                .setValue(PART, WingBlock.PART_SINGLE));
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
    public IntegerProperty connectionProperty() {
        return PART;
    }

    @Override
    public int connectionValue(Direction facing, NeighbourQuery neighbours) {
        boolean inward = neighbours.sameKindAt(
                -facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
        boolean outward = neighbours.sameKindAt(
                facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (!inward && !outward) return WingBlock.PART_SINGLE;
        if (!inward) return WingBlock.PART_ROOT;
        if (!outward) return WingBlock.PART_TIP;
        return WingBlock.PART_MIDDLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AILERON);
        builder.add(PART);
    }
}
