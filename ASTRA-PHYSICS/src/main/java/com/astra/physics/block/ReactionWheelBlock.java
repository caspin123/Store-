package com.astra.physics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A powered gyroscope that turns the hull without pushing against anything outside it.
 *
 * <p>Steering otherwise depends entirely on flow: a rudder needs water moving past it, so a moored
 * boat cannot turn at all, and a slow aircraft turns badly. A reaction wheel spins a heavy rotor
 * and takes the opposite torque into the hull, so it gives full turning authority at a standstill.
 * That is how spacecraft point themselves, and it is the piece that makes a hovering or drifting
 * craft actually steerable.
 *
 * <p>It has no facing: yaw torque is the same whichever way the housing is turned.
 */
public final class ReactionWheelBlock extends Block implements AnimatedComponent {
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);

    public ReactionWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SPIN, 0));
    }

    @Override
    public IntegerProperty frameProperty() {
        return SPIN;
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
        return 4.5;
    }

    @Override
    public double activity(double enginePower, double throttle) {
        // The rotor spins whenever the wheel has power. It does not need throttle: holding a
        // heading is work even when the craft is not going anywhere.
        return enginePower;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPIN);
    }
}
