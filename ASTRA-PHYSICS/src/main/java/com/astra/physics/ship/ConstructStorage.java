package com.astra.physics.ship;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import com.astra.physics.AstraPhysics;
import com.astra.physics.util.BlockStateSerializer;

/**
 * Reads and writes assembled constructs to disk.
 *
 * <h2>Why this exists</h2>
 * Assembling a construct deletes its blocks from the world. Before this class the constructs
 * themselves lived only in a static {@code HashMap}, so stopping the server destroyed every
 * assembled build permanently — the single most damaging bug in the mod. Constructs are now
 * saved with the world and restored on load.
 *
 * <h2>Format</h2>
 * One file per dimension under {@code <world>/data/astra_physics/}. Scalars are written with
 * {@link DataOutputStream} and block states through {@link BlockStateSerializer}, so the format
 * never depends on registry ordering. Writes go to a temporary file and are then moved into
 * place, so a crash mid-save cannot leave a half-written file where the good one used to be.
 */
public final class ConstructStorage {
    private static final int FORMAT_VERSION = 1;
    private static final String DIRECTORY = AstraPhysics.MOD_ID;

    private ConstructStorage() {}

    public static void save(ServerLevel level, Iterable<PhysicsConstruct> constructs) {
        Path file = fileFor(level);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

        List<PhysicsConstruct> snapshot = new ArrayList<>();
        for (PhysicsConstruct construct : constructs) {
            if (construct.blockCount() > 0) {
                snapshot.add(construct);
            }
        }

        try {
            Files.createDirectories(file.getParent());
            if (snapshot.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }

            try (OutputStream raw = Files.newOutputStream(temporary);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.size());
                for (PhysicsConstruct construct : snapshot) {
                    writeConstruct(out, construct, level.registryAccess());
                }
            }
            Files.move(temporary, file,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            AstraPhysics.LOGGER.debug("Saved {} ASTRA construct(s) for {}",
                    snapshot.size(), level.dimension().identifier());
        } catch (IOException ex) {
            AstraPhysics.LOGGER.error("Failed to save ASTRA constructs for {}. "
                    + "The previous save file was left in place.", level.dimension().identifier(), ex);
        }
    }

    public static List<PhysicsConstruct> load(ServerLevel level) {
        Path file = fileFor(level);
        if (!Files.exists(file)) {
            return List.of();
        }

        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {

            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                AstraPhysics.LOGGER.error(
                        "ASTRA construct file for {} has format version {}, but this build reads version {}. "
                                + "Refusing to load so the file is not overwritten with a bad interpretation.",
                        level.dimension().identifier(), version, FORMAT_VERSION);
                return List.of();
            }

            int count = in.readInt();
            List<PhysicsConstruct> loaded = new ArrayList<>(Math.max(0, count));
            for (int i = 0; i < count; i++) {
                PhysicsConstruct construct = readConstruct(in, level.registryAccess());
                if (construct != null) {
                    loaded.add(construct);
                }
            }
            AstraPhysics.LOGGER.info("Loaded {} ASTRA construct(s) for {}",
                    loaded.size(), level.dimension().identifier());
            return loaded;

        } catch (EOFException ex) {
            AstraPhysics.LOGGER.error("ASTRA construct file for {} is truncated. "
                    + "Any constructs after the damaged point were not loaded.",
                    level.dimension().identifier(), ex);
            return List.of();
        } catch (IOException | RuntimeException ex) {
            AstraPhysics.LOGGER.error("Failed to load ASTRA constructs for {}",
                    level.dimension().identifier(), ex);
            return List.of();
        }
    }

    // ---------------------------------------------------------------- writing

    private static void writeConstruct(DataOutputStream out, PhysicsConstruct construct,
                                       HolderLookup.Provider registries) throws IOException {
        out.writeLong(construct.id().getMostSignificantBits());
        out.writeLong(construct.id().getLeastSignificantBits());

        out.writeDouble(construct.x());
        out.writeDouble(construct.y());
        out.writeDouble(construct.z());
        out.writeDouble(construct.vx());
        out.writeDouble(construct.vy());
        out.writeDouble(construct.vz());

        out.writeUTF(construct.engineMode().name());
        out.writeInt(construct.enginePowerStep());

        List<StoredBlock> blocks = construct.blocks();
        out.writeInt(blocks.size());
        for (StoredBlock block : blocks) {
            out.writeInt(block.localX());
            out.writeInt(block.localY());
            out.writeInt(block.localZ());
            BlockStateSerializer.write(out, block.state());

            ConstructBlockEntityData data = block.blockEntityData();
            out.writeBoolean(data != null);
            if (data != null) {
                data.write(out, registries);
            }
        }
    }

    // ---------------------------------------------------------------- reading

    private static PhysicsConstruct readConstruct(DataInputStream in,
                                                  HolderLookup.Provider registries) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());

        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        double vx = in.readDouble();
        double vy = in.readDouble();
        double vz = in.readDouble();

        String modeName = in.readUTF();
        int powerStep = in.readInt();

        int blockCount = in.readInt();
        List<StoredBlock> blocks = new ArrayList<>(Math.max(0, Math.min(blockCount, 65_536)));
        int droppedBlocks = 0;

        for (int i = 0; i < blockCount; i++) {
            int localX = in.readInt();
            int localY = in.readInt();
            int localZ = in.readInt();
            BlockState state = BlockStateSerializer.read(in);

            ConstructBlockEntityData data = in.readBoolean()
                    ? ConstructBlockEntityData.read(in, registries)
                    : null;

            if (state == null) {
                // The block's mod is gone. Skipping it is the only safe option, but it must be
                // visible in the log rather than silently changing someone's ship.
                droppedBlocks++;
                continue;
            }
            blocks.add(new StoredBlock(localX, localY, localZ, state, data));
        }

        if (droppedBlocks > 0) {
            AstraPhysics.LOGGER.warn("Construct {} lost {} block(s) whose block type is no longer "
                    + "registered. Check that every mod present when it was built is still installed.",
                    id, droppedBlocks);
        }
        if (blocks.isEmpty()) {
            AstraPhysics.LOGGER.warn("Construct {} had no loadable blocks and was discarded.", id);
            return null;
        }

        PhysicsConstruct construct = new PhysicsConstruct(id, blocks, x, y, z);
        construct.restoreRuntimeState(vx, vy, vz, parseMode(modeName), powerStep);
        return construct;
    }

    private static PhysicsConstruct.EngineMode parseMode(String name) {
        for (PhysicsConstruct.EngineMode mode : PhysicsConstruct.EngineMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return PhysicsConstruct.EngineMode.MARINE;
    }

    // ------------------------------------------------------------------ paths

    private static Path fileFor(ServerLevel level) {
        MinecraftServer server = level.getServer();
        String dimension = level.dimension().identifier().toString()
                .replace(':', '.')
                .replace('/', '.');
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(DIRECTORY)
                .resolve("constructs." + dimension + ".dat");
    }
}
