package com.astra.physics.client.ship;

import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import com.astra.physics.block.AstraFacingBlock;
import com.astra.physics.network.ConstructControlPayload;
import com.astra.physics.network.PilotExitPayload;
import com.astra.physics.registry.AstraBlocks;

/**
 * FCL-friendly helm grip controller.
 *
 * While active the movement keys are ship controls, not walking controls. The local player is
 * held in front of the selected moving helm and the server performs the same authoritative seat
 * anchoring. Shift or Jump releases the grip. Steering also drives the helm's visual wheel state.
 */
public final class ClientHelmController {
    private static UUID controlledConstruct;
    private static int sendCooldown;
    private static int releaseGuardTicks;
    private static float lastThrottle;
    private static float lastSteer;
    private static float visualSteer;
    private static int helmX;
    private static int helmY;
    private static int helmZ;

    private ClientHelmController() {}

    public static boolean isControlling(UUID id) {
        return id != null && id.equals(controlledConstruct);
    }

    /**
     * The local pilot's smoothed steering input, -1 to 1.
     *
     * <p>The renderer prefers this over the value echoed back by the server so the wheel and the
     * ailerons answer the pilot's keys on the same frame they are pressed, instead of a packet
     * round trip later.
     */
    public static float visualSteer() {
        return visualSteer;
    }

    public static void applyServerState(UUID id, boolean active, int localHelmX, int localHelmY, int localHelmZ) {
        controlledConstruct = active ? id : null;
        helmX = localHelmX;
        helmY = localHelmY;
        helmZ = localHelmZ;
        sendCooldown = 0;
        releaseGuardTicks = active ? 6 : 0;
        lastThrottle = 0.0F;
        lastSteer = 0.0F;
        visualSteer = 0.0F;
    }

    /** Drops all local piloting state, for a world change or disconnect. */
    public static void reset() {
        controlledConstruct = null;
        visualSteer = 0.0F;
        lastThrottle = 0.0F;
        lastSteer = 0.0F;
        sendCooldown = 0;
        releaseGuardTicks = 0;
    }

    public static void clearIf(UUID id) {
        if (id != null && id.equals(controlledConstruct)) {
            controlledConstruct = null;
            visualSteer = 0.0F;
        }
    }

    public static void tick(Minecraft client) {
        if (controlledConstruct == null || client.player == null || client.level == null) return;

        ClientPhysicsConstruct construct = ClientConstructManager.get(controlledConstruct);
        if (construct == null) {
            controlledConstruct = null;
            visualSteer = 0.0F;
            return;
        }

        ClientPhysicsConstruct.ClientBlock helm = construct.blocks().stream()
                .filter(block -> block.localX() == helmX && block.localY() == helmY && block.localZ() == helmZ)
                .filter(block -> block.state().is(AstraBlocks.HELM))
                .findFirst()
                .orElse(null);
        if (helm == null) {
            requestRelease();
            return;
        }

        if (releaseGuardTicks > 0) releaseGuardTicks--;
        if (releaseGuardTicks <= 0 && (client.options.keyShift.isDown() || client.options.keyJump.isDown())) {
            requestRelease();
            return;
        }

        float throttle = 0.0F;
        float steer = 0.0F;
        if (client.options.keyUp.isDown()) throttle += 1.0F;
        if (client.options.keyDown.isDown()) throttle -= 1.0F;
        if (client.options.keyLeft.isDown()) steer -= 1.0F;
        if (client.options.keyRight.isDown()) steer += 1.0F;

        // The wheel eases toward the input rather than snapping. With five baked wheel positions
        // the easing is what makes a turn read as a turn instead of a jump.
        visualSteer += (steer - visualSteer) * 0.35F;

        boolean changed = throttle != lastThrottle || steer != lastSteer;
        if (sendCooldown > 0) sendCooldown--;
        if ((changed || sendCooldown <= 0) && ClientPlayNetworking.canSend(ConstructControlPayload.TYPE)) {
            ClientPlayNetworking.send(new ConstructControlPayload(controlledConstruct, throttle, steer));
            if (steer != lastSteer && Math.abs(steer) > 0.01F) {
                client.player.swing(steer < 0.0F ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            }
            lastThrottle = throttle;
            lastSteer = steer;
            sendCooldown = 2;
        }

        anchorLocalPlayer(client, construct, helm);
    }

    private static void anchorLocalPlayer(Minecraft client, ClientPhysicsConstruct construct,
                                          ClientPhysicsConstruct.ClientBlock helm) {
        Direction facing = helm.state().hasProperty(AstraFacingBlock.FACING)
                ? helm.state().getValue(AstraFacingBlock.FACING)
                : Direction.NORTH;

        double px = construct.x() + helm.localX() + 0.5 + facing.getStepX() * 0.90;
        double py = construct.y() + helm.localY();
        double pz = construct.z() + helm.localZ() + 0.5 + facing.getStepZ() * 0.90;

        client.player.setPos(px, py, pz);
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setOnGround(true);
        client.player.resetFallDistance();
        // Deliberately does NOT touch yaw or pitch. Re-applying the helm's facing every tick
        // pinned the camera so the pilot could not look around at all; the server already
        // points the player at the wheel once, when they take it.
    }

    private static void requestRelease() {
        UUID id = controlledConstruct;
        if (id == null) return;
        if (ClientPlayNetworking.canSend(PilotExitPayload.TYPE)) {
            ClientPlayNetworking.send(new PilotExitPayload(id));
        }
        controlledConstruct = null;
        visualSteer = 0.0F;
        lastThrottle = 0.0F;
        lastSteer = 0.0F;
    }
}
