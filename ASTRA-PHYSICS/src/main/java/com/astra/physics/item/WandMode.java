package com.astra.physics.item;

/**
 * What the physics wand does when it is used.
 *
 * <p>The wand could only assemble, and undoing an assembly existed solely as an operator command —
 * so a survival player could turn their build into a construct and then never turn it back. These
 * modes close that, and add the one operation that was missing entirely: moving a construct
 * without having to fly it.
 */
public enum WandMode {
    /** Pick two corners, then click again to turn the selection into a construct. */
    ASSEMBLE("assemble"),
    /** Click a construct to return every block of it to the world. */
    DISASSEMBLE("disassemble"),
    /** Click a construct to carry it on the end of your gaze; click again to let go. */
    GRAB("grab"),
    /**
     * Assemble a selection and pick it straight up.
     *
     * <p>Assembling on the ground and then grabbing is two operations with a moment in between
     * where the new construct drops, which is exactly when a freshly built hull rolls off its
     * scaffold. Doing both at once means it never touches anything.
     */
    ASSEMBLE_AND_GRAB("assemble_and_grab");

    private final String key;

    WandMode(String key) {
        this.key = key;
    }

    /** Translation key suffix, so mode names are localisable like everything else. */
    public String translationKey() {
        return "wand.mode." + key;
    }

    public WandMode next() {
        WandMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
