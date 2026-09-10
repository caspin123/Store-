package com.astra.physics.client.ship;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.astra.physics.network.ConstructSpawnPayload;

public final class ClientPhysicsConstruct {
    private final UUID id;
    private final List<ClientBlock> blocks;

    private double previousX, previousY, previousZ;
    private double x, y, z;

    public ClientPhysicsConstruct(ConstructSpawnPayload payload) {
        this.id = payload.id();
        this.previousX = this.x = payload.x();
        this.previousY = this.y = payload.y();
        this.previousZ = this.z = payload.z();
        this.blocks = payload.blocks().stream()
                .map(b -> new ClientBlock(b.localX(), b.localY(), b.localZ(), Block.stateById(b.stateId())))
                .toList();
    }

    public UUID id() { return id; }
    public List<ClientBlock> blocks() { return blocks; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double previousX() { return previousX; }
    public double previousY() { return previousY; }
    public double previousZ() { return previousZ; }
    public double deltaX() { return x - previousX; }
    public double deltaY() { return y - previousY; }
    public double deltaZ() { return z - previousZ; }

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
