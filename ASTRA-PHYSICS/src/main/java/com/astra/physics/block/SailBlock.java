package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Canvas rig. {@code WIND} plays a wave so the cloth bellies rather than hanging as a flat sheet.
 * Sails catch some wind even with the engines off, so the loop never stops entirely — it just
 * runs slowly.
 *
 * <p>A sail is one panel of canvas, not a whole rig: place a block of them and they tile into a
 * sail of whatever size the player wants, with a mast down the leading column. The renderer
 * offsets each panel's frame by its position, so the billow travels across a large sail instead
 * of the whole sheet pulsing at once.
 */
public final class SailBlock extends AstraFacingBlock
        implements AnimatedComponent, ConnectedComponent {

    /** Drawn with a mast when this block starts the run, plain canvas otherwise. */
    public static final IntegerProperty MAST = IntegerProperty.create("mast", 0, 1);
    public static final IntegerProperty WIND = IntegerProperty.create("wind", 0, 3);

    public SailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WIND, 0)
                .setValue(MAST, 1));
    }

    @Override
    public IntegerProperty frameProperty() {
        return WIND;
    }

    @Override
    public int frameCount() {
        return 4;
    }

    @Override
    public Drive drive() {
        return Drive.CYCLE;
    }

    @Override
    public double speedFactor() {
        // A sail luffs gently; running it at engine speed looks frantic.
        return 0.45;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // Canvas moves in any breeze, so it never fully stops, but it works the sail harder
        // when the helm is asking for speed.
        return 0.35 + 0.65 * Math.abs(throttle);
    }

    @Override
    public int phaseOffset(int localX, int localY, int localZ) {
        // Diagonal offset, so the billow crosses a large sail rather than running in flat bands.
        return localX + localY + localZ;
    }

    @Override
    public IntegerProperty connectionProperty() {
        return MAST;
    }

    /**
     * Canvas hangs off the mast toward the block's clockwise side, so the mast belongs on the
     * column that has no sail behind it. Every block in that column carries a length of mast,
     * which is what lets a tall sail have a full-height pole.
     */
    @Override
    public int connectionValue(Direction facing, NeighbourQuery neighbours) {
        Direction mastSide = facing.getCounterClockWise();
        boolean sailTowardMast = neighbours.sameKindAt(
                mastSide.getStepX(), mastSide.getStepY(), mastSide.getStepZ());
        return sailTowardMast ? 0 : 1;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WIND);
        builder.add(MAST);
    }
}
