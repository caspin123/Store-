package com.astra.physics.client.ship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.astra.physics.block.AnimatedComponent;
import com.astra.physics.config.AstraConfig;
import com.astra.physics.network.ConstructSpawnPayload;
import com.astra.physics.network.ConstructStatePayload;

public final class ClientPhysicsConstruct {
    private final UUID id;
    private final List<ClientBlock> blocks;
    private final List<ClientBlock> visibleBlocks;

    private double previousX, previousY, previousZ;
    private double x, y, z;
    private double previousYaw, yaw;
    private double pivotX, pivotZ;

    // Drivetrain state mirrored from the server, used to drive component animation.
    private float enginePower;
    private float throttle;
    private float steer;
    private boolean aircraftMode;

    public ClientPhysicsConstruct(ConstructSpawnPayload payload) {
        this.id = payload.id();
        this.previousX = this.x = payload.x();
        this.previousY = this.y = payload.y();
        this.previousZ = this.z = payload.z();
        this.previousYaw = this.yaw = payload.yaw();
        this.blocks = payload.blocks().stream()
                .map(b -> new ClientBlock(b.localX(), b.localY(), b.localZ(), Block.stateById(b.stateId())))
                .toList();
        this.visibleBlocks = computeVisibleBlocks(this.blocks);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ClientBlock block : this.blocks) {
            minX = Math.min(minX, block.localX());
            maxX = Math.max(maxX, block.localX());
            minZ = Math.min(minZ, block.localZ());
            maxZ = Math.max(maxZ, block.localZ());
        }
        if (this.blocks.isEmpty()) {
            minX = maxX = minZ = maxZ = 0;
        }
        // Must match the server's pivot exactly, or the hull is drawn turning about a different
        // point than the one it actually collides about.
        this.pivotX = (minX + maxX + 1) * 0.5;
        this.pivotZ = (minZ + maxZ + 1) * 0.5;
    }

    /**
     * Drops blocks that are completely buried inside the hull.
     *
     * <p>A large ship is mostly interior, and every one of those blocks was being drawn every
     * frame despite being invisible. Components are never culled: their models reach well outside
     * their own cell, so a sail or wing with six neighbours is still visible from outside.
     */
    private static List<ClientBlock> computeVisibleBlocks(List<ClientBlock> all) {
        if (!AstraConfig.get().cullInteriorBlocks) {
            return all;
        }

        Set<Long> occupied = new HashSet<>(all.size() * 2);
        Set<Long> components = new HashSet<>();
        for (ClientBlock block : all) {
            long key = key(block.localX(), block.localY(), block.localZ());
            occupied.add(key);
            if (block.state().getBlock() instanceof AnimatedComponent) {
                components.add(key);
            }
        }

        List<ClientBlock> visible = new ArrayList<>(all.size());
        for (ClientBlock block : all) {
            if (block.state().getBlock() instanceof AnimatedComponent || block.state().hasBlockEntity()) {
                visible.add(block);
                continue;
            }

            boolean enclosed = true;
            for (Direction direction : Direction.values()) {
                long neighbour = key(block.localX() + direction.getStepX(),
                        block.localY() + direction.getStepY(),
                        block.localZ() + direction.getStepZ());
                // A component neighbour does not fill its cell, so it cannot hide this block.
                if (!occupied.contains(neighbour) || components.contains(neighbour)) {
                    enclosed = false;
                    break;
                }
            }
            if (!enclosed) {
                visible.add(block);
            }
        }
        return List.copyOf(visible);
    }

    private static long key(int x, int y, int z) {
        final long mask = 0x1FFFFFL;
        return ((long) x & mask) << 42 | ((long) y & mask) << 21 | ((long) z & mask);
    }

    public UUID id() { return id; }
    public List<ClientBlock> blocks() { return blocks; }

    /** Blocks worth drawing: everything except the fully buried interior. */
    public List<ClientBlock> visibleBlocks() { return visibleBlocks; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double previousX() { return previousX; }
    public double previousY() { return previousY; }
    public double previousZ() { return previousZ; }
    public double deltaX() { return x - previousX; }
    public double deltaY() { return y - previousY; }
    public double deltaZ() { return z - previousZ; }
    public float enginePower() { return enginePower; }
    public float throttle() { return throttle; }
    public float steer() { return steer; }
    public boolean aircraftMode() { return aircraftMode; }

    public void applyState(ConstructStatePayload payload) {
        this.enginePower = payload.powerScale();
        this.throttle = payload.throttleFraction();
        this.steer = payload.steerFraction();
        this.aircraftMode = payload.aircraftMode();
    }

    /**
     * Deck/platform surface under the player's feet. Unlike topSurfaceY this ignores
     * component blocks whose top is above the player's current feet (helm/sail/etc.).
     */
    public double supportSurfaceY(double worldX, double worldZ, double feetY, boolean previousTransform) {
        double baseY = previousTransform ? previousY : y;
        double useYaw = previousTransform ? previousYaw : yaw;
        double baseX = previousTransform ? previousX : x;
        double baseZ = previousTransform ? previousZ : z;
        double radians = Math.toRadians(useYaw);
        double dx = worldX - baseX - pivotX, dz = worldZ - baseZ - pivotZ;
        int localX = (int) Math.floor(pivotX + dx * Math.cos(radians) + dz * Math.sin(radians));
        int localZ = (int) Math.floor(pivotZ - dx * Math.sin(radians) + dz * Math.cos(radians));
        double best = Double.NaN;
        double maxAllowedTop = feetY + 0.62;
        for (ClientBlock block : blocks) {
            if (block.localX() != localX || block.localZ() != localZ) continue;
            double top = baseY + block.localY() + 1.0;
            if (top <= maxAllowedTop && (Double.isNaN(best) || top > best)) best = top;
        }
        return best;
    }

    /** Highest translated ASTRA block top under a world X/Z point. */
    public double topSurfaceY(double worldX, double worldZ, boolean previousTransform) {
        double baseY = previousTransform ? previousY : y;
        int localX = (int) Math.floor(toLocalX(worldX, worldZ));
        int localZ = (int) Math.floor(toLocalZ(worldX, worldZ));
        int highest = Integer.MIN_VALUE;
        for (ClientBlock block : blocks) {
            if (block.localX() == localX && block.localZ() == localZ) {
                highest = Math.max(highest, block.localY());
            }
        }
        return highest == Integer.MIN_VALUE ? Double.NaN : baseY + highest + 1.0;
    }

    public void updateTransform(double nx, double ny, double nz, double nyaw) {
        previousX = x;
        previousY = y;
        previousZ = z;
        previousYaw = yaw;
        x = nx;
        y = ny;
        z = nz;
        yaw = nyaw;
    }

    public double yaw() { return yaw; }
    public double pivotX() { return pivotX; }
    public double pivotZ() { return pivotZ; }

    /** Yaw between the last two snapshots, taking the short way round the circle. */
    public double renderYaw(float alpha) {
        double delta = wrapDegrees(yaw - previousYaw);
        return previousYaw + delta * alpha;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    // ---- local space <-> world space, mirroring PhysicsConstruct

    public double toWorldX(double localX, double localZ) {
        double radians = Math.toRadians(yaw);
        double dx = localX - pivotX, dz = localZ - pivotZ;
        return x + pivotX + dx * Math.cos(radians) - dz * Math.sin(radians);
    }

    public double toWorldZ(double localX, double localZ) {
        double radians = Math.toRadians(yaw);
        double dx = localX - pivotX, dz = localZ - pivotZ;
        return z + pivotZ + dx * Math.sin(radians) + dz * Math.cos(radians);
    }

    public double toLocalX(double worldX, double worldZ) {
        double radians = Math.toRadians(yaw);
        double dx = worldX - x - pivotX, dz = worldZ - z - pivotZ;
        return pivotX + dx * Math.cos(radians) + dz * Math.sin(radians);
    }

    public double toLocalZ(double worldX, double worldZ) {
        double radians = Math.toRadians(yaw);
        double dx = worldX - x - pivotX, dz = worldZ - z - pivotZ;
        return pivotZ - dx * Math.sin(radians) + dz * Math.cos(radians);
    }

    /** Interpolated transform, so riders and the camera sit on the hull as it is drawn. */
    public double renderToWorldX(double localX, double localZ, float alpha) {
        double radians = Math.toRadians(renderYaw(alpha));
        double dx = localX - pivotX, dz = localZ - pivotZ;
        return renderX(alpha) + pivotX + dx * Math.cos(radians) - dz * Math.sin(radians);
    }

    public double renderToWorldZ(double localX, double localZ, float alpha) {
        double radians = Math.toRadians(renderYaw(alpha));
        double dx = localX - pivotX, dz = localZ - pivotZ;
        return renderZ(alpha) + pivotZ + dx * Math.sin(radians) + dz * Math.cos(radians);
    }

    public double renderX(float alpha) { return previousX + (x - previousX) * alpha; }
    public double renderY(float alpha) { return previousY + (y - previousY) * alpha; }
    public double renderZ(float alpha) { return previousZ + (z - previousZ) * alpha; }

    public record ClientBlock(int localX, int localY, int localZ, BlockState state) {}
}
