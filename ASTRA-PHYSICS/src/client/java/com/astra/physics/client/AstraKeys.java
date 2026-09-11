package com.astra.physics.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import com.astra.physics.AstraPhysics;

/**
 * Client key bindings.
 *
 * <p>There is exactly one, and it exists because jump and sneak became climb and dive. Those two
 * keys used to be how a pilot let go of the wheel, which is fine on a boat and impossible on an
 * aircraft — you cannot ask someone to climb without also throwing them off the helm.
 */
public final class AstraKeys {
    /**
     * Key categories became a registered type in 1.21.11 rather than a bare translation key, so
     * the category is registered once here and handed to every binding that belongs to it.
     */
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "controls")
    );

    private static KeyMapping leaveHelm;
    private static KeyMapping cycleWandMode;

    private AstraKeys() {}

    public static void register() {
        leaveHelm = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.astra_physics.leave_helm",
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));
        cycleWandMode = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.astra_physics.wand_mode",
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));
    }

    /**
     * Consumes one press of the wand mode key.
     *
     * <p>Reads as a press rather than a hold, so holding the key cycles once instead of racing
     * through every mode.
     */
    public static boolean consumeWandModePress() {
        return cycleWandMode != null && cycleWandMode.consumeClick();
    }

    /** True while the leave-helm key is held. */
    public static boolean leaveHelmPressed() {
        return leaveHelm != null && leaveHelm.isDown();
    }
}
