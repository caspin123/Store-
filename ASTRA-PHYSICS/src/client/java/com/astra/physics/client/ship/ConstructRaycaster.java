package com.astra.physics.client.ship;

import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure-Java ray test against translated construct-local unit block boxes. */
public final class ConstructRaycaster {
    private ConstructRaycaster() {}

    public static Hit raycast(Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
        Hit best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (ClientPhysicsConstruct construct : ClientConstructManager.all()) {
            // Rotating the ray into the hull's own space is exact and cheap; rotating every
            // block's box into world space would be neither, and the boxes would no longer be
            // axis aligned for AABB#clip to use.
            Vec3 localStart = new Vec3(
                    construct.toLocalX(start.x, start.z),
                    start.y - construct.y(),
                    construct.toLocalZ(start.x, start.z));
            Vec3 localEnd = new Vec3(
                    construct.toLocalX(end.x, end.z),
                    end.y - construct.y(),
                    construct.toLocalZ(end.x, end.z));

            for (ClientPhysicsConstruct.ClientBlock block : construct.blocks()) {
                AABB box = new AABB(
                        block.localX(), block.localY(), block.localZ(),
                        block.localX() + 1.0, block.localY() + 1.0, block.localZ() + 1.0
                );
                Optional<Vec3> clipped = box.clip(localStart, localEnd);
                if (clipped.isEmpty()) {
                    continue;
                }
                Vec3 localHit = clipped.get();
                Vec3 worldHit = new Vec3(
                        construct.toWorldX(localHit.x, localHit.z),
                        localHit.y + construct.y(),
                        construct.toWorldZ(localHit.x, localHit.z));
                double distanceSq = start.distanceToSqr(worldHit);
                if (distanceSq >= bestDistanceSq) {
                    continue;
                }
                // The face is resolved in local space, so it names the hull's own side and can
                // be used directly as a local placement direction.
                Direction face = closestFace(box, localHit);
                bestDistanceSq = distanceSq;
                best = new Hit(construct, block, face, worldHit, distanceSq);
            }
        }
        return best;
    }

    private static Direction closestFace(AABB box, Vec3 point) {
        Direction best = Direction.UP;
        double distance = Math.abs(point.y - box.maxY);

        double d = Math.abs(point.y - box.minY);
        if (d < distance) { distance = d; best = Direction.DOWN; }
        d = Math.abs(point.x - box.minX);
        if (d < distance) { distance = d; best = Direction.WEST; }
        d = Math.abs(point.x - box.maxX);
        if (d < distance) { distance = d; best = Direction.EAST; }
        d = Math.abs(point.z - box.minZ);
        if (d < distance) { distance = d; best = Direction.NORTH; }
        d = Math.abs(point.z - box.maxZ);
        if (d < distance) { best = Direction.SOUTH; }
        return best;
    }

    public record Hit(
            ClientPhysicsConstruct construct,
            ClientPhysicsConstruct.ClientBlock block,
            Direction face,
            Vec3 worldHit,
            double distanceSq
    ) {}
}
