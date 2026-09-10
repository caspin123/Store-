package com.astra.physics.client.ship;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side companion to the authoritative server deck support.
 * It keeps local movement visually attached to translated constructs and prevents
 * the vanilla client from visually falling through between server corrections.
 */
public final class ClientMovingPlatformSupport {
    private static final double STAND_BELOW_TOLERANCE = 0.60;
    private static final double STAND_ABOVE_TOLERANCE = 0.75;

    private ClientMovingPlatformSupport() {}

    public static void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || client.level == null || player.isSpectator() || player.getAbilities().flying) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0.08) {
            return; // jumping: detach naturally
        }

        for (ClientPhysicsConstruct construct : ClientConstructManager.all()) {
            double previousTop = construct.supportSurfaceY(player.getX(), player.getZ(), player.getY(), true);
            if (Double.isNaN(previousTop)) {
                continue;
            }

            double feetY = player.getY();
            if (feetY < previousTop - STAND_BELOW_TOLERANCE || feetY > previousTop + STAND_ABOVE_TOLERANCE) {
                continue;
            }

            // Same rule as the server: map the rider's spot on last tick's deck onto this
            // tick's deck, which covers turning as well as translation.
            double localX = construct.toLocalX(player.getX(), player.getZ());
            double localZ = construct.toLocalZ(player.getX(), player.getZ());
            double carriedX = construct.toWorldX(localX, localZ) + construct.deltaX();
            double carriedZ = construct.toWorldZ(localX, localZ) + construct.deltaZ();
            double currentTop = construct.supportSurfaceY(carriedX, carriedZ, player.getY() + construct.deltaY(), false);
            if (Double.isNaN(currentTop)) {
                continue;
            }

            player.setPos(carriedX, currentTop, carriedZ);
            resolveHorizontalConstructCollisions(player, construct);
            player.setOnGround(true);
            player.resetFallDistance();
            if (motion.y < 0.0) {
                player.setDeltaMovement(motion.x, 0.0, motion.z);
            }
            return;
        }
    }

    private static void resolveHorizontalConstructCollisions(Player player, ClientPhysicsConstruct construct) {
        for (ClientPhysicsConstruct.ClientBlock block : construct.blocks()) {
            double cx = construct.toWorldX(block.localX() + 0.5, block.localZ() + 0.5);
            double cz = construct.toWorldZ(block.localX() + 0.5, block.localZ() + 0.5);
            double by0 = construct.y() + block.localY();
            net.minecraft.world.phys.AABB blockBox = new net.minecraft.world.phys.AABB(
                    cx - 0.5, by0, cz - 0.5, cx + 0.5, by0 + 1.0, cz + 0.5
            );
            net.minecraft.world.phys.AABB playerBox = player.getBoundingBox();
            if (!playerBox.intersects(blockBox)) continue;
            if (blockBox.maxY <= player.getY() + 0.12) continue;

            double west = blockBox.minX - playerBox.maxX;
            double east = blockBox.maxX - playerBox.minX;
            double north = blockBox.minZ - playerBox.maxZ;
            double south = blockBox.maxZ - playerBox.minZ;
            double pushX = Math.abs(west) < Math.abs(east) ? west : east;
            double pushZ = Math.abs(north) < Math.abs(south) ? north : south;
            if (Math.abs(pushX) < Math.abs(pushZ)) {
                player.setPos(player.getX() + pushX, player.getY(), player.getZ());
            } else {
                player.setPos(player.getX(), player.getY(), player.getZ() + pushZ);
            }
        }
    }
}
