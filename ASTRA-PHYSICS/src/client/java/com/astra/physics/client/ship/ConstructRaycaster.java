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
            double baseX = construct.x();
            double baseY = construct.y();
            double baseZ = construct.z();
            for (ClientPhysicsConstruct.ClientBlock block : construct.blocks()) {
                AABB box = new AABB(
                        baseX + block.localX(), baseY + block.localY(), baseZ + block.localZ(),
                        baseX + block.localX() + 1.0, baseY + block.localY() + 1.0, baseZ + block.localZ() + 1.0
                );
                Optional<Vec3> clipped = box.clip(start, end);
                if (clipped.isEmpty()) {
                    continue;
                }
                Vec3 hitPoint = clipped.get();
                double distanceSq = start.distanceToSqr(hitPoint);
                if (distanceSq >= bestDistanceSq) {
                    continue;
                }
                Direction face = closestFace(box, hitPoint);
                bestDistanceSq = distanceSq;
                best = new Hit(construct, block, face, hitPoint, distanceSq);
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
