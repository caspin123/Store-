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
 *
 * <p>{@code PART} lets wings tile: place several in a row along their facing and they draw as one
 * continuous wing with a mount at the hull and a winglet at the tip, so the player decides how
 * long a wing is rather than being stuck with whatever the model happened to be.
 */
public final class WingBlock extends AstraFacingBlock
        implements AnimatedComponent, ConnectedComponent {

    /** Which piece of a wing run this block draws. */
    public static final int PART_SINGLE = 0;
    public static final int PART_ROOT = 1;
    public static final int PART_MIDDLE = 2;
    public static final int PART_TIP = 3;

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final int NEUTRAL_AILERON = 1;
    public static final IntegerProperty AILERON = IntegerProperty.create("aileron", 0, 2);

    public WingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AILERON, NEUTRAL_AILERON)
                .setValue(PART, PART_SINGLE));
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

    /**
     * A wing runs outward along its facing, so the hull is behind it and the tip is in front.
     * A block with a wing behind it is not a root; one with a wing in front is not a tip.
     */
    @Override
    public int connectionValue(Direction facing, NeighbourQuery neighbours) {
        boolean inward = neighbours.sameKindAt(
                -facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
        boolean outward = neighbours.sameKindAt(
                facing.getStepX(), facing.getStepY(), facing.getStepZ());

        if (!inward && !outward) return PART_SINGLE;
        if (!inward) return PART_ROOT;
        if (!outward) return PART_TIP;
        return PART_MIDDLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AILERON);
        builder.add(PART);
    }
}
