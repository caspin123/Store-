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
        this.blocks = payload.blocks().stream()
                .map(b -> new ClientBlock(b.localX(), b.localY(), b.localZ(), Block.stateById(b.stateId())))
                .toList();
        this.visibleBlocks = computeVisibleBlocks(this.blocks);
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
        double baseX = previousTransform ? previousX : x;
        double baseY = previousTransform ? previousY : y;
        double baseZ = previousTransform ? previousZ : z;
        int localX = (int) Math.floor(worldX - baseX);
        int localZ = (int) Math.floor(worldZ - baseZ);
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
        double baseX = previousTransform ? previousX : x;
        double baseY = previousTransform ? previousY : y;
        double baseZ = previousTransform ? previousZ : z;
        int localX = (int) Math.floor(worldX - baseX);
        int localZ = (int) Math.floor(worldZ - baseZ);
        int highest = Integer.MIN_VALUE;
        for (ClientBlock block : blocks) {
            if (block.localX() == localX && block.localZ() == localZ) {
                highest = Math.max(highest, block.localY());
            }
        }
        return highest == Integer.MIN_VALUE ? Double.NaN : baseY + highest + 1.0;
    }

    public void updateTransform(double nx, double ny, double nz) {
        previousX = x;
        previousY = y;
        previousZ = z;
        x = nx;
        y = ny;
        z = nz;
    }

    public double renderX(float alpha) { return previousX + (x - previousX) * alpha; }
    public double renderY(float alpha) { return previousY + (y - previousY) * alpha; }
    public double renderZ(float alpha) { return previousZ + (z - previousZ) * alpha; }

    public record ClientBlock(int localX, int localY, int localZ, BlockState state) {}
}
