package com.astra.physics.block;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A component block whose appearance is a short loop of baked model frames.
 *
 * <p>ASTRA construct blocks are not real world blocks, so nothing ticks them and no BlockEntity
 * can drive an animation. Instead each animated component declares a frame property, and the
 * construct renderer overrides that property per frame from the ship's live state. The frames
 * themselves are ordinary block models, so this costs no extra draw calls and works unchanged on
 * Android and FCL.
 *
 * <p>A block placed in the world and not part of a construct simply stays on its default frame,
 * which is the correct "switched off" pose for every component.
 */
public interface AnimatedComponent {
    /** The blockstate property that selects the frame. */
    IntegerProperty frameProperty();

    /** Number of frames in the loop. Frame indices run from 0 to this minus one. */
    int frameCount();

    /**
     * How the frame is chosen.
     *
     * <ul>
     *   <li>{@link Drive#CYCLE} plays the loop continuously at a speed taken from engine power.</li>
     *   <li>{@link Drive#CYCLE_FROM_ONE} keeps frame 0 as the idle pose and cycles the rest, for
     *       components that must look inert when they are not running.</li>
     *   <li>{@link Drive#STEER} maps the helm's steering input onto the frame range.</li>
     *   <li>{@link Drive#MANUAL} leaves the frame alone: it is a setting the player chose, stored
     *       on the block itself, not something that moves on its own.</li>
     * </ul>
     */
    Drive drive();

    /** Loop speed multiplier, relative to a nominal one loop per second at full power. */
    default double speedFactor() {
        return 1.0;
    }

    /**
     * How hard this component is working right now, from 0 (idle) to 1 (flat out).
     *
     * <p>Each component reads the ship's state differently — a propeller needs throttle as well
     * as power, a thruster only counts forward throttle, a sail works with no engine at all — so
     * the rule lives on the component rather than in the renderer.
     *
     * @param enginePower the construct's engine setting, 0 to 1
     * @param throttle    the helm throttle, -1 to 1
     */
    default double activity(double enginePower, double throttle) {
        return enginePower;
    }

    /**
     * Frame offset for a block at a given local position.
     *
     * <p>A tiled component covering many blocks would otherwise pulse as one flat sheet. Shifting
     * each block a frame along its position turns the same loop into a wave crossing the surface,
     * at no extra cost.
     */
    default int phaseOffset(int localX, int localY, int localZ) {
        return 0;
    }

    enum Drive {
        CYCLE,
        CYCLE_FROM_ONE,
        STEER,
        MANUAL
    }
}
