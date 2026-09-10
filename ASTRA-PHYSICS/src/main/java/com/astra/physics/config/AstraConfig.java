package com.astra.physics.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import com.astra.physics.AstraPhysics;

/**
 * Every tunable number in ASTRA Physics lives here instead of being scattered through the
 * solver as a private constant.
 *
 * <p>The file is written to {@code config/astra_physics.json} on first launch and reloaded
 * with {@code /astra reload}. Missing keys fall back to the built-in defaults, so a config
 * written by an older build keeps working after an update.
 */
public final class AstraConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve(AstraPhysics.MOD_ID + ".json");

    private static volatile AstraConfig instance = new AstraConfig();

    // ---------------------------------------------------------------- limits

    /** Hard cap on the number of blocks a single construct may contain. */
    public int maxConstructBlocks = 4096;
    /** Hard cap on the cuboid volume the wand is allowed to scan. */
    public int maxSelectionScanVolume = 65_536;
    /** Maximum constructs allowed per dimension. Prevents accidental server-wide lag. */
    public int maxConstructsPerDimension = 64;
    /** Reach, in blocks, for interacting with construct-local blocks. */
    public double interactionReach = 6.0;

    // --------------------------------------------------------------- physics

    public double gravityPerTick = -0.08;
    public double airDamping = 0.997;
    /** Upward force contributed by one fully submerged block, before mass division. */
    public double buoyancyPerBlock = 0.180;
    /**
     * Blocks sampled per tick when measuring how deep the hull sits. Each sampled block is
     * probed at eight heights, so this is a budget of roughly eight times as many fluid
     * lookups. Vertical resolution matters far more than breadth here: a coarse waterline
     * reading is what the solver ends up chasing.
     */
    public int buoyancySampleBudget = 320;

    public double maxMarineSpeed = 0.30;
    public double maxSailSpeed = 0.17;
    public double maxAircraftSpeed = 0.38;
    public double maxVerticalSpeed = 0.42;
    /** Speed limit for a construct with no working propulsion. */
    public double maxDriftSpeed = 0.24;

    // -------------------------------------------------------------- rendering

    /** Constructs further than this (in blocks) from the camera are not rendered. */
    public double renderDistance = 192.0;
    /** Use real world lighting instead of full-bright. Disable for a small FPS gain. */
    public boolean useWorldLighting = true;
    /** Skip blocks that are completely enclosed by other construct blocks. */
    public boolean cullInteriorBlocks = true;
    /** Particle effects emitted per construct per burst. Set to 0 to disable effects. */
    public int maxEffectEmitters = 48;

    // -------------------------------------------------------------- behaviour

    /** Require that a selection forms one connected group of blocks. */
    public boolean requireConnectedSelection = true;
    /** Honour world protection (spawn protection, claim mods) when assembling. */
    public boolean respectBlockProtection = true;
    /** Move construct-local hoppers every N ticks. 0 disables local hopper logic. */
    public int localHopperInterval = 8;
    /** Maximum helm control packets accepted from one player per second. */
    public int maxControlPacketsPerSecond = 30;

    public static AstraConfig get() {
        return instance;
    }

    /** Loads the config from disk, writing a default file if none exists. */
    public static void load() {
        AstraConfig loaded = new AstraConfig();
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                AstraConfig parsed = GSON.fromJson(reader, AstraConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonSyntaxException ex) {
                AstraPhysics.LOGGER.error(
                        "Could not read {}. Falling back to defaults; the file was left untouched.", PATH, ex);
                instance = new AstraConfig();
                return;
            }
        }

        loaded.clampToSaneRanges();
        instance = loaded;
        save();
    }

    /** Writes the active config back to disk so new keys appear after an update. */
    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException ex) {
            AstraPhysics.LOGGER.error("Could not write {}", PATH, ex);
        }
    }

    /**
     * A hand-edited config must never be able to crash the server or stall a tick, so every
     * value is forced back into a range the solver can actually handle.
     */
    private void clampToSaneRanges() {
        maxConstructBlocks = clamp(maxConstructBlocks, 1, 32_768);
        maxSelectionScanVolume = clamp(maxSelectionScanVolume, 1, 4_194_304);
        maxConstructsPerDimension = clamp(maxConstructsPerDimension, 1, 4096);
        interactionReach = clamp(interactionReach, 1.0, 64.0);

        gravityPerTick = clamp(gravityPerTick, -4.0, 0.0);
        airDamping = clamp(airDamping, 0.50, 1.0);
        buoyancyPerBlock = clamp(buoyancyPerBlock, 0.0, 4.0);
        buoyancySampleBudget = clamp(buoyancySampleBudget, 8, 8192);

        maxMarineSpeed = clamp(maxMarineSpeed, 0.01, 4.0);
        maxSailSpeed = clamp(maxSailSpeed, 0.01, 4.0);
        maxAircraftSpeed = clamp(maxAircraftSpeed, 0.01, 4.0);
        maxVerticalSpeed = clamp(maxVerticalSpeed, 0.01, 4.0);
        maxDriftSpeed = clamp(maxDriftSpeed, 0.01, 4.0);

        renderDistance = clamp(renderDistance, 16.0, 1024.0);
        maxEffectEmitters = clamp(maxEffectEmitters, 0, 4096);

        localHopperInterval = clamp(localHopperInterval, 0, 1200);
        maxControlPacketsPerSecond = clamp(maxControlPacketsPerSecond, 1, 200);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
