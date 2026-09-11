package com.astra.physics.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.astra.physics.block.AstraFacingBlock;
import com.astra.physics.config.AstraConfig;
import com.astra.physics.network.ConstructStatePayload;
import com.astra.physics.registry.AstraBlocks;

/**
 * A server-authoritative ASTRA moving construct.
 *
 * <h2>Coordinate model</h2>
 * Blocks are stored in construct-local integer coordinates. The construct itself has a
 * double-precision world origin and is translated only — there is no yaw rotation yet, so
 * local space stays axis-aligned with world space. Every consumer (collision, raycasting,
 * deck support, rendering) relies on that, which is why steering is modelled as rudder thrust
 * rather than a heading change: a visual rotation the collision code did not share would put
 * the hitbox somewhere other than the hull the player can see.
 *
 * <h2>Why the caches exist</h2>
 * A 4096-block construct is ticked twenty times a second. Rebuilding derived data is O(n), but
 * it only happens when the block set actually changes; everything on the per-tick path reads a
 * cache instead of walking the block list:
 * <ul>
 *   <li>{@code index} makes local lookups O(1) instead of a linear scan.</li>
 *   <li>{@code collisionShell} holds, per direction, only the blocks with an exposed face in
 *       that direction. Terrain collision previously tested all 4096 blocks ten times per axis
 *       per tick — roughly 120k world queries — and now tests only the leading surface.</li>
 *   <li>{@code buoyancySamples} is a fixed, evenly spread subset. Re-picking it every tick made
 *       the measured waterline jitter, which the solver then fought with visible bobbing.</li>
 *   <li>{@code componentBlocks} and {@code hopperBlocks} avoid scanning the hull for the handful
 *       of blocks that emit particles or move items.</li>
 * </ul>
 */
public final class PhysicsConstruct {
    private static final double REST_EPSILON = 1.0E-5;
    // Eight sample heights rather than four: the measured waterline moves in steps of one
    // sample, and a coarse step feeds the solver a jittery signal it then tries to correct.
    private static final int VERTICAL_SAMPLES_PER_BLOCK = 8;
    private static final double[] SAMPLE_HEIGHTS =
            {0.07, 0.19, 0.31, 0.44, 0.56, 0.69, 0.81, 0.93};
    private static final Direction[] DIRECTIONS = Direction.values();

    /** How many ticks of helm input are honoured after the last control packet. */
    private static final int CONTROL_GRACE_TICKS = 8;

    /** Degrees per tick. Roughly a full turn in three seconds at the limit. */
    private static final double MAX_YAW_SPEED = 6.0;

    public enum EngineMode { MARINE, AIRCRAFT }

    private final UUID id;
    private final List<StoredBlock> blocks;
    private final Map<Long, StoredBlock> index = new HashMap<>();
    private final double wavePhase;

    // ---- caches rebuilt only when the block set changes
    private final List<StoredBlock>[] collisionShell = newShellArray();
    private final List<StoredBlock> componentBlocks = new ArrayList<>();
    /** Blocks with at least one exposed face, tested when the hull turns in place. */
    private List<StoredBlock> outerShell = List.of();
    private final List<StoredBlock> hopperBlocks = new ArrayList<>();
    private final List<StoredBlock> buoyancySamples = new ArrayList<>();
    private final Map<Long, List<StoredBlock>> columnIndex = new HashMap<>();

    private int minLocalX, minLocalY, minLocalZ;
    private int maxLocalX, maxLocalY, maxLocalZ;
    private double mass;

    private int helmCount, engineCount, propellerCount, sailCount, wingCount, thrusterCount;
    private int reactionWheelCount, balloonCount;
    private double propellerThrustX, propellerThrustZ;
    private double helmForwardX, helmForwardZ;
    private double wingBalanceFactor;

    // Construct-wide engine profile. Interacting with any engine configures every ASTRA engine
    // on the same construct; per-engine configuration can come later.
    private EngineMode engineMode = EngineMode.MARINE;
    private int enginePowerStep = 2; // 0=OFF, 1=25%, 2=50%, 3=75%, 4=100%
    private float helmThrottle;
    private float helmSteer;
    private int controlTicksRemaining;

    private double previousX, previousY, previousZ;
    private double x, y, z;
    private double vx, vy, vz;
    private double submergedFraction;

    // ---- orientation
    // Yaw is the whole reason local space and world space are different. Every consumer of
    // geometry - rendering, raycasting, terrain collision, buoyancy, deck support - has to go
    // through toWorld/toLocal, or the hull ends up somewhere other than its own hitbox.
    private double yaw;
    private double previousYaw;
    private double yawVelocity;
    private double pivotX;
    private double pivotZ;
    private double yawCos = 1.0;
    private double yawSin = 0.0;

    /** Vertical control input, -1 to 1, separate from forward throttle. */
    private float helmLift;

    /** Set when the block set changes, so the manager knows to resend the full snapshot. */
    private boolean structureDirty;

    // Quantised drivetrain state mirrored to clients for component animation. Comparing the
    // quantised values means a packet only goes out when something visible actually changed.
    private byte netThrottle;
    private byte netSteer;
    private int netPowerStep = -1;
    private boolean netAircraftMode;
    private boolean stateDirty = true;

    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

    public PhysicsConstruct(UUID id, List<StoredBlock> blocks, double x, double y, double z) {
        this.id = id;
        this.blocks = new ArrayList<>(blocks);
        this.x = this.previousX = x;
        this.y = this.previousY = y;
        this.z = this.previousZ = z;
        this.wavePhase = ((id.getMostSignificantBits() ^ id.getLeastSignificantBits()) & 0xFFFFL)
                / 65535.0 * Math.PI * 2.0;
        rebuildDerivedData();
    }

    @SuppressWarnings("unchecked")
    private static List<StoredBlock>[] newShellArray() {
        List<StoredBlock>[] array = new List[DIRECTIONS.length];
        for (int i = 0; i < array.length; i++) {
            array[i] = List.of();
        }
        return array;
    }

    // ------------------------------------------------------------------ state

    public UUID id() { return id; }
    public List<StoredBlock> blocks() { return Collections.unmodifiableList(blocks); }
    public int blockCount() { return blocks.size(); }
    public int sizeX() { return maxLocalX - minLocalX + 1; }
    public int sizeY() { return maxLocalY - minLocalY + 1; }
    public int sizeZ() { return maxLocalZ - minLocalZ + 1; }
    public double mass() { return mass; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double previousX() { return previousX; }
    public double previousY() { return previousY; }
    public double previousZ() { return previousZ; }
    public double deltaX() { return x - previousX; }
    public double deltaY() { return y - previousY; }
    public double deltaZ() { return z - previousZ; }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }
    public double submergedFraction() { return submergedFraction; }
    public double yaw() { return yaw; }
    public double previousYaw() { return previousYaw; }
    public double yawVelocity() { return yawVelocity; }
    public double pivotX() { return pivotX; }
    public double pivotZ() { return pivotZ; }

    /** Shortest signed difference between this tick's yaw and last tick's, in degrees. */
    public double deltaYaw() { return wrapDegrees(yaw - previousYaw); }

    // ---- local space <-> world space

    public double toWorldX(double localX, double localZ) {
        double dx = localX - pivotX;
        double dz = localZ - pivotZ;
        return x + pivotX + dx * yawCos - dz * yawSin;
    }

    public double toWorldZ(double localX, double localZ) {
        double dx = localX - pivotX;
        double dz = localZ - pivotZ;
        return z + pivotZ + dx * yawSin + dz * yawCos;
    }

    public double toLocalX(double worldX, double worldZ) {
        double dx = worldX - x - pivotX;
        double dz = worldZ - z - pivotZ;
        return pivotX + dx * yawCos + dz * yawSin;
    }

    public double toLocalZ(double worldX, double worldZ) {
        double dx = worldX - x - pivotX;
        double dz = worldZ - z - pivotZ;
        return pivotZ - dx * yawSin + dz * yawCos;
    }

    /**
     * Same as {@link #toLocalX} but against last tick's transform.
     *
     * <p>Carrying a rider is "where was this point on the deck last tick, and where is that same
     * deck point now" - which covers turning as well as translation, where a plain position
     * delta only covers translation.
     */
    public double previousToLocalX(double worldX, double worldZ) {
        double c = Math.cos(Math.toRadians(previousYaw));
        double sn = Math.sin(Math.toRadians(previousYaw));
        double dx = worldX - previousX - pivotX;
        double dz = worldZ - previousZ - pivotZ;
        return pivotX + dx * c + dz * sn;
    }

    public double previousToLocalZ(double worldX, double worldZ) {
        double c = Math.cos(Math.toRadians(previousYaw));
        double sn = Math.sin(Math.toRadians(previousYaw));
        double dx = worldX - previousX - pivotX;
        double dz = worldZ - previousZ - pivotZ;
        return pivotZ - dx * sn + dz * c;
    }

    /** Rotates a local direction into world space. Heights are untouched by yaw. */
    public double rotateDirectionX(double localX, double localZ) {
        return localX * yawCos - localZ * yawSin;
    }

    public double rotateDirectionZ(double localX, double localZ) {
        return localX * yawSin + localZ * yawCos;
    }

    private void setYaw(double degrees) {
        yaw = wrapDegrees(degrees);
        double radians = Math.toRadians(yaw);
        yawCos = Math.cos(radians);
        yawSin = Math.sin(radians);
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
    public int helmCount() { return helmCount; }
    public int engineCount() { return engineCount; }
    public int propellerCount() { return propellerCount; }
    public int sailCount() { return sailCount; }
    public int wingCount() { return wingCount; }
    public int thrusterCount() { return thrusterCount; }
    public int reactionWheelCount() { return reactionWheelCount; }
    public int balloonCount() { return balloonCount; }
    /**
     * True only when the construct has an engine block AND its power is turned up.
     *
     * <p>The power step is stored on the construct, not on any block, and it defaults to 50%.
     * Testing it alone meant a hull with no engine at all still counted as running: its
     * propellers spun and its exhaust smoked while the physics, which does check for an engine,
     * produced no thrust at all.
     */
    public boolean enginesEnabled() { return enginePowerStep > 0 && engineCount > 0; }
    public EngineMode engineMode() { return engineMode; }
    public int enginePowerStep() { return enginePowerStep; }
    public int enginePowerPercent() { return enginePowerStep * 25; }
    public double enginePowerScale() { return enginesEnabled() ? enginePowerStep / 4.0 : 0.0; }

    /** Power step as the client should see it: zero when there is no engine to run. */
    public int effectivePowerStep() { return enginesEnabled() ? enginePowerStep : 0; }
    public double wingBalanceFactor() { return wingBalanceFactor; }

    public boolean isStructureDirty() { return structureDirty; }
    public void clearStructureDirty() { structureDirty = false; }

    public boolean isStateDirty() { return stateDirty; }
    public void clearStateDirty() { stateDirty = false; }
    public byte netThrottle() { return netThrottle; }
    public byte netSteer() { return netSteer; }

    /**
     * Recomputes the client-visible drivetrain state and flags it when it changed.
     *
     * <p>Quantising first is what keeps this cheap: a throttle drifting by a hundredth does not
     * move any animation frame, so it must not cost a packet either.
     */
    private void refreshNetworkState() {
        byte throttle = ConstructStatePayload.quantise(helmThrottle);
        byte steer = ConstructStatePayload.quantise(helmSteer);
        boolean aircraft = engineMode == EngineMode.AIRCRAFT;

        int power = effectivePowerStep();
        if (throttle != netThrottle || steer != netSteer
                || power != netPowerStep || aircraft != netAircraftMode) {
            netThrottle = throttle;
            netSteer = steer;
            netPowerStep = power;
            netAircraftMode = aircraft;
            stateDirty = true;
        }
    }

    /** True when the construct actually moved this tick, so idle hulls cost no bandwidth. */
    public boolean hasMoved() {
        return Math.abs(deltaX()) > REST_EPSILON
                || Math.abs(deltaY()) > REST_EPSILON
                || Math.abs(deltaZ()) > REST_EPSILON
                || Math.abs(deltaYaw()) > 1.0E-4;
    }

    /** Restores saved motion and control state during world load. */
    public void restoreRuntimeState(double vx, double vy, double vz,
                                    EngineMode mode, int powerStep) {
        restoreRuntimeState(vx, vy, vz, mode, powerStep, 0.0);
    }

    public void restoreRuntimeState(double vx, double vy, double vz,
                                    EngineMode mode, int powerStep, double savedYaw) {
        setYaw(savedYaw);
        this.previousYaw = this.yaw;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.engineMode = mode == null ? EngineMode.MARINE : mode;
        this.enginePowerStep = Math.max(0, Math.min(4, powerStep));
    }

    /**
     * World bounding box of the hull at a given origin, widened to cover its current yaw.
     *
     * <p>A rotated rectangle needs a larger axis-aligned box than an unrotated one, and this is
     * used for range checks and tracking, so it has to enclose the hull at every angle rather
     * than only when the ship happens to be square to the world.
     */
    public AABB aabbAt(double px, double py, double pz) {
        double halfX = (maxLocalX - minLocalX + 1.0) * 0.5;
        double halfZ = (maxLocalZ - minLocalZ + 1.0) * 0.5;
        double absCos = Math.abs(yawCos);
        double absSin = Math.abs(yawSin);
        double rotatedHalfX = halfX * absCos + halfZ * absSin;
        double rotatedHalfZ = halfX * absSin + halfZ * absCos;

        double centreX = px + pivotX;
        double centreZ = pz + pivotZ;
        return new AABB(
                centreX - rotatedHalfX, py + minLocalY, centreZ - rotatedHalfZ,
                centreX + rotatedHalfX, py + maxLocalY + 1.0, centreZ + rotatedHalfZ
        );
    }

    public AABB boundingBox() {
        return aabbAt(x, y, z);
    }

    // ------------------------------------------------------------ block access

    public StoredBlock getLocalBlock(int localX, int localY, int localZ) {
        return index.get(localKey(localX, localY, localZ));
    }

    public boolean hasLocalBlock(int localX, int localY, int localZ) {
        return index.containsKey(localKey(localX, localY, localZ));
    }

    public boolean addBlock(int localX, int localY, int localZ, BlockState state) {
        return addBlock(localX, localY, localZ, state, null);
    }

    public boolean addBlock(int localX, int localY, int localZ, BlockState state,
                            ConstructBlockEntityData blockEntityData) {
        if (state == null || state.isAir()
                || blocks.size() >= AstraConfig.get().maxConstructBlocks
                || hasLocalBlock(localX, localY, localZ)) {
            return false;
        }
        blocks.add(new StoredBlock(localX, localY, localZ, state, blockEntityData));
        rebuildDerivedData();
        return true;
    }

    public StoredBlock removeBlock(int localX, int localY, int localZ) {
        StoredBlock target = getLocalBlock(localX, localY, localZ);
        if (target == null) {
            return null;
        }
        blocks.remove(target);
        rebuildDerivedData();
        return target;
    }

    // --------------------------------------------------------------- controls

    /** Normal engine interaction cycles OFF -&gt; 25 -&gt; 50 -&gt; 75 -&gt; 100 -&gt; OFF. */
    public int cycleEnginePower() {
        enginePowerStep = (enginePowerStep + 1) % 5;
        if (enginePowerStep == 0) {
            helmThrottle = 0.0F;
            helmSteer = 0.0F;
        }
        return enginePowerPercent();
    }

    /** Sneak-use on an engine switches the shared construct engine profile. */
    public EngineMode toggleEngineMode() {
        engineMode = engineMode == EngineMode.MARINE ? EngineMode.AIRCRAFT : EngineMode.MARINE;
        return engineMode;
    }

    public void applyHelmInput(float throttle, float steer) {
        applyHelmInput(throttle, steer, 0.0F);
    }

    public void applyHelmInput(float throttle, float steer, float lift) {
        helmThrottle = clampUnit(throttle);
        helmSteer = clampUnit(steer);
        helmLift = clampUnit(lift);
        controlTicksRemaining = CONTROL_GRACE_TICKS;
    }

    public float helmLift() { return helmLift; }

    private static float clampUnit(float value) {
        if (Float.isNaN(value)) return 0.0F;
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    // ------------------------------------------------------------ deck surface

    /**
     * The construct surface that can actually support a player's feet.
     *
     * <p>This deliberately is not the highest block in the column. A helm, sail or wing mount can
     * sit above the deck; treating the component's top as the floor made players stop being
     * supported as they walked toward it and fall through the hull. Instead this returns the
     * highest block top that is not significantly above the player's current feet.
     */
    public double supportSurfaceY(double worldX, double worldZ, double feetY, boolean previousTransform) {
        double baseY = previousTransform ? previousY : y;
        int localX = (int) Math.floor(previousTransform
                ? previousToLocalX(worldX, worldZ) : toLocalX(worldX, worldZ));
        int localZ = (int) Math.floor(previousTransform
                ? previousToLocalZ(worldX, worldZ) : toLocalZ(worldX, worldZ));

        List<StoredBlock> column = columnIndex.get(columnKey(localX, localZ));
        if (column == null) {
            return Double.NaN;
        }

        double best = Double.NaN;
        double maxAllowedTop = feetY + 0.62;
        for (StoredBlock block : column) {
            double top = baseY + block.localY() + 1.0;
            if (top <= maxAllowedTop && (Double.isNaN(best) || top > best)) {
                best = top;
            }
        }
        return best;
    }

    /**
     * Blocks in the nine local columns around a world X/Z point.
     *
     * <p>Player push-out used to walk the whole hull once per player per tick. Only blocks within
     * one column of the player can possibly intersect them, so this is what that pass reads.
     */
    public List<StoredBlock> blocksNear(double worldX, double worldZ) {
        int centerX = (int) Math.floor(toLocalX(worldX, worldZ));
        int centerZ = (int) Math.floor(toLocalZ(worldX, worldZ));
        List<StoredBlock> nearby = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<StoredBlock> column = columnIndex.get(columnKey(centerX + dx, centerZ + dz));
                if (column != null) {
                    nearby.addAll(column);
                }
            }
        }
        return nearby;
    }

    /** The geometric highest column top, for ray and debug callers. */
    public double topSurfaceY(double worldX, double worldZ, boolean previousTransform) {
        double baseY = previousTransform ? previousY : y;
        List<StoredBlock> column = columnIndex.get(columnKey(
                (int) Math.floor(previousTransform
                        ? previousToLocalX(worldX, worldZ) : toLocalX(worldX, worldZ)),
                (int) Math.floor(previousTransform
                        ? previousToLocalZ(worldX, worldZ) : toLocalZ(worldX, worldZ))));
        if (column == null) {
            return Double.NaN;
        }
        int highest = Integer.MIN_VALUE;
        for (StoredBlock block : column) {
            highest = Math.max(highest, block.localY());
        }
        return highest == Integer.MIN_VALUE ? Double.NaN : baseY + highest + 1.0;
    }

    // -------------------------------------------------------------------- tick

    public void tick(ServerLevel level) {
        previousX = x;
        previousY = y;
        previousZ = z;
        previousYaw = yaw;

        if (blocks.isEmpty()) {
            return;
        }

        AstraConfig config = AstraConfig.get();
        updateControlDecay();

        vy += config.gravityPerTick;

        tickLocalHoppers(level, config);

        double rawSubmerged = sampleSubmergedFraction(level);
        // Low-pass the measurement, but only lightly. Filtering harder hides sampling noise at
        // the cost of lag, and lag in a feedback loop is what turns a stable float into a bob.
        submergedFraction += (rawSubmerged - submergedFraction) * 0.50;
        if (submergedFraction < 1.0E-4) {
            submergedFraction = 0.0;
        }

        applyBuoyancyAndWaves(level, config, submergedFraction);
        applyComponents(level, config, submergedFraction);

        // Water is damped anisotropically on purpose: vertical oscillation is suppressed much
        // harder than forward motion, which removes tick-to-tick bobbing without making the
        // helm feel sluggish.
        double horizontalWaterDamping = 1.0 - Math.min(0.065, submergedFraction * 0.055);
        vx *= config.airDamping * horizontalWaterDamping;
        vz *= config.airDamping * horizontalWaterDamping;
        vy *= config.airDamping;

        // A settled hull has to actually come to rest. A few thousandths of a block per tick
        // is still a visible shimmer when it never stops.
        if (submergedFraction > 0.05 && Math.abs(vy) < 0.0008) {
            vy = 0.0;
        }

        clampVelocity(config, submergedFraction);

        applyYaw(level);

        moveAxis(level, vx, 0.0, 0.0);
        moveAxis(level, 0.0, vy, 0.0);
        moveAxis(level, 0.0, 0.0, vz);

        emitComponentEffects(level, config, submergedFraction);
        refreshNetworkState();
    }

    private void updateControlDecay() {
        if (controlTicksRemaining > 0) {
            controlTicksRemaining--;
            return;
        }
        if (helmCount > 0) {
            // A helm-equipped construct coasts to a stop if the pilot stops sending input.
            helmThrottle = 0.0F;
            helmSteer = 0.0F;
            helmLift = 0.0F;
        } else if (enginesEnabled()) {
            // Engine-only rigs with no helm cruise gently instead of running at full power.
            helmThrottle = 0.35F;
        }
    }

    // -------------------------------------------------------------- derived data

    private void rebuildDerivedData() {
        structureDirty = true;

        index.clear();
        columnIndex.clear();
        componentBlocks.clear();
        hopperBlocks.clear();

        minLocalX = minLocalY = minLocalZ = Integer.MAX_VALUE;
        maxLocalX = maxLocalY = maxLocalZ = Integer.MIN_VALUE;
        helmCount = engineCount = propellerCount = sailCount = wingCount = thrusterCount = 0;
        reactionWheelCount = balloonCount = 0;
        propellerThrustX = propellerThrustZ = 0.0;
        helmForwardX = 0.0;
        helmForwardZ = 1.0;

        for (StoredBlock block : blocks) {
            index.put(localKey(block.localX(), block.localY(), block.localZ()), block);
            columnIndex.computeIfAbsent(columnKey(block.localX(), block.localZ()),
                    ignored -> new ArrayList<>(4)).add(block);
        }

        Direction helmFacing = null;
        for (StoredBlock block : blocks) {
            minLocalX = Math.min(minLocalX, block.localX());
            minLocalY = Math.min(minLocalY, block.localY());
            minLocalZ = Math.min(minLocalZ, block.localZ());
            maxLocalX = Math.max(maxLocalX, block.localX());
            maxLocalY = Math.max(maxLocalY, block.localY());
            maxLocalZ = Math.max(maxLocalZ, block.localZ());

            BlockState state = block.state();
            boolean isComponent = false;

            if (state.is(AstraBlocks.HELM)) {
                helmCount++;
                isComponent = true;
                if (helmFacing == null && state.hasProperty(AstraFacingBlock.FACING)) {
                    helmFacing = state.getValue(AstraFacingBlock.FACING);
                }
            } else if (state.is(AstraBlocks.ENGINE)) {
                engineCount++;
                isComponent = true;
            } else if (state.is(AstraBlocks.PROPELLER)) {
                propellerCount++;
                isComponent = true;
                if (state.hasProperty(AstraFacingBlock.FACING)) {
                    Direction facing = state.getValue(AstraFacingBlock.FACING);
                    // A propeller pushes the hull opposite the direction its visible face points.
                    propellerThrustX -= facing.getStepX();
                    propellerThrustZ -= facing.getStepZ();
                }
            } else if (state.is(AstraBlocks.SAIL)) {
                sailCount++;
                isComponent = true;
            } else if (state.is(AstraBlocks.WING)) {
                wingCount++;
                isComponent = true;
            } else if (state.is(AstraBlocks.THRUSTER)) {
                thrusterCount++;
                isComponent = true;
            } else if (state.is(AstraBlocks.REACTION_WHEEL)) {
                reactionWheelCount++;
                isComponent = true;
            } else if (state.is(AstraBlocks.BALLOON)) {
                balloonCount++;
                isComponent = true;
            }

            if (isComponent) {
                componentBlocks.add(block);
            }
            ConstructBlockEntityData data = block.blockEntityData();
            if (data != null && data.kind() == ConstructBlockEntityData.Kind.HOPPER && data.hasInventory()) {
                hopperBlocks.add(block);
            }
        }

        if (blocks.isEmpty()) {
            minLocalX = minLocalY = minLocalZ = 0;
            maxLocalX = maxLocalY = maxLocalZ = 0;
            wingBalanceFactor = 0.0;
        } else {
            if (helmFacing != null) {
                helmForwardX = helmFacing.getStepX();
                helmForwardZ = helmFacing.getStepZ();
            }
            recalculateWingBalance(helmFacing);
        }

        // The hull turns about the centre of its own footprint. Rotating about the origin corner
        // would swing the whole ship sideways instead of pivoting it.
        pivotX = (minLocalX + maxLocalX + 1) * 0.5;
        pivotZ = (minLocalZ + maxLocalZ + 1) * 0.5;

        rebuildCollisionShell();
        rebuildBuoyancySamples();
        mass = BlockMassProperties.totalMass(blocks);
    }

    /**
     * For each direction, the blocks whose neighbour in that direction is empty. Only those can
     * be the first thing to touch terrain when the construct moves that way, so terrain collision
     * ignores the entire interior of the hull.
     */
    private void rebuildCollisionShell() {
        for (Direction direction : DIRECTIONS) {
            List<StoredBlock> exposed = new ArrayList<>();
            for (StoredBlock block : blocks) {
                if (!hasLocalBlock(block.localX() + direction.getStepX(),
                        block.localY() + direction.getStepY(),
                        block.localZ() + direction.getStepZ())) {
                    exposed.add(block);
                }
            }
            collisionShell[direction.ordinal()] = List.copyOf(exposed);
        }

        List<StoredBlock> outer = new ArrayList<>();
        for (StoredBlock block : blocks) {
            for (Direction direction : DIRECTIONS) {
                if (!hasLocalBlock(block.localX() + direction.getStepX(),
                        block.localY() + direction.getStepY(),
                        block.localZ() + direction.getStepZ())) {
                    outer.add(block);
                    break;
                }
            }
        }
        outerShell = List.copyOf(outer);
    }

    /**
     * A fixed, evenly spread subset of the hull used to measure the waterline. Keeping the same
     * sample set every tick is what makes the measurement stable; re-striding it each tick made
     * the reported depth flicker and the hull bob in response.
     */
    private void rebuildBuoyancySamples() {
        buoyancySamples.clear();
        if (blocks.isEmpty()) {
            return;
        }
        int budget = Math.max(1, AstraConfig.get().buoyancySampleBudget);
        int stride = Math.max(1, (blocks.size() + budget - 1) / budget);
        for (int i = 0; i < blocks.size(); i += stride) {
            buoyancySamples.add(blocks.get(i));
        }
    }

    /**
     * Wings are meant to sit on the hull's edges and face outward. A correctly mirrored
     * left/right pair gets full efficiency; centre-mounted or inward-facing wings are penalised
     * so aircraft have to be built like aircraft.
     */
    private void recalculateWingBalance(Direction helmFacing) {
        if (wingCount <= 0) {
            wingBalanceFactor = 0.0;
            return;
        }

        double centerX = (minLocalX + maxLocalX) * 0.5;
        double centerZ = (minLocalZ + maxLocalZ) * 0.5;
        int correctLeft = 0;
        int correctRight = 0;
        int misplaced = 0;
        boolean forwardAlongZ = helmFacing == null || helmFacing.getAxis() == Direction.Axis.Z;

        for (StoredBlock block : componentBlocks) {
            if (!block.state().is(AstraBlocks.WING)) continue;

            double lateral = forwardAlongZ ? block.localX() - centerX : block.localZ() - centerZ;
            Direction wingFacing = block.state().hasProperty(AstraFacingBlock.FACING)
                    ? block.state().getValue(AstraFacingBlock.FACING) : null;

            if (lateral < -0.35) {
                Direction expected = forwardAlongZ ? Direction.WEST : Direction.NORTH;
                if (wingFacing == null || wingFacing == expected) correctLeft++;
                else misplaced++;
            } else if (lateral > 0.35) {
                Direction expected = forwardAlongZ ? Direction.EAST : Direction.SOUTH;
                if (wingFacing == null || wingFacing == expected) correctRight++;
                else misplaced++;
            } else {
                misplaced++;
            }
        }

        int paired = Math.min(correctLeft, correctRight) * 2;
        double pairedRatio = (double) paired / wingCount;
        double extraCorrect = Math.max(0, correctLeft + correctRight - paired) / (double) wingCount;
        double misplacedRatio = misplaced / (double) wingCount;
        wingBalanceFactor = Math.max(0.10,
                Math.min(1.0, pairedRatio + extraCorrect * 0.30 + misplacedRatio * 0.08));
    }

    // ------------------------------------------------------- local automation

    /**
     * A deliberately narrow local automation layer, far short of a full ShipLevel: construct-local
     * hoppers move one item between construct-local container mirrors on an interval. Chests work
     * as local storage; furnaces and modded BlockEntities keep their data but still need the
     * future ShipLevel to run their real world tick.
     */
    private void tickLocalHoppers(ServerLevel level, AstraConfig config) {
        if (hopperBlocks.isEmpty() || config.localHopperInterval <= 0) {
            return;
        }
        if (level.getGameTime() % config.localHopperInterval != 0L) {
            return;
        }

        for (StoredBlock block : hopperBlocks) {
            ConstructBlockEntityData data = block.blockEntityData();
            if (data == null || !data.hasInventory()) continue;

            Direction facing = block.state().hasProperty(HopperBlock.FACING)
                    ? block.state().getValue(HopperBlock.FACING)
                    : Direction.DOWN;

            // Pull one item from a local container directly above the hopper.
            StoredBlock source = getLocalBlock(block.localX(), block.localY() + 1, block.localZ());
            if (source != null && source.blockEntityData() != null && source.blockEntityData().hasInventory()) {
                transferOne(source.blockEntityData(), data);
            }

            // Push one item toward the hopper's local facing direction.
            StoredBlock target = getLocalBlock(
                    block.localX() + facing.getStepX(),
                    block.localY() + facing.getStepY(),
                    block.localZ() + facing.getStepZ()
            );
            if (target != null && target.blockEntityData() != null && target.blockEntityData().hasInventory()) {
                transferOne(data, target.blockEntityData());
            }
        }
    }

    private static boolean transferOne(ConstructBlockEntityData sourceData, ConstructBlockEntityData targetData) {
        SimpleContainer source = sourceData.inventory();
        SimpleContainer target = targetData.inventory();
        if (source == null || target == null || source == target) return false;

        for (int fromSlot = 0; fromSlot < source.getContainerSize(); fromSlot++) {
            ItemStack sourceStack = source.getItem(fromSlot);
            if (sourceStack.isEmpty()) continue;

            for (int toSlot = 0; toSlot < target.getContainerSize(); toSlot++) {
                ItemStack targetStack = target.getItem(toSlot);
                if (targetStack.isEmpty()) {
                    target.setItem(toSlot, sourceStack.copyWithCount(1));
                    sourceStack.shrink(1);
                    source.setChanged();
                    target.setChanged();
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
                    int max = Math.min(target.getMaxStackSize(targetStack), targetStack.getMaxStackSize());
                    if (targetStack.getCount() < max) {
                        targetStack.grow(1);
                        sourceStack.shrink(1);
                        source.setChanged();
                        target.setChanged();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------- hydrostatics

    private double sampleSubmergedFraction(ServerLevel level) {
        if (buoyancySamples.isEmpty()) return 0.0;

        int wetSamples = 0;
        int totalSamples = 0;
        for (StoredBlock block : buoyancySamples) {
            double worldX = toWorldX(block.localX() + 0.5, block.localZ() + 0.5);
            double worldZ = toWorldZ(block.localX() + 0.5, block.localZ() + 0.5);
            for (int s = 0; s < VERTICAL_SAMPLES_PER_BLOCK; s++) {
                double worldY = y + block.localY() + SAMPLE_HEIGHTS[s];
                // A mutable cursor keeps this off the allocation path; the old code created
                // thousands of BlockPos objects per construct per tick.
                scratchPos.set((int) Math.floor(worldX), (int) Math.floor(worldY), (int) Math.floor(worldZ));
                if (level.getFluidState(scratchPos).is(FluidTags.WATER)) {
                    wetSamples++;
                }
                totalSamples++;
            }
        }
        return totalSamples == 0 ? 0.0 : (double) wetSamples / totalSamples;
    }

    private void applyBuoyancyAndWaves(ServerLevel level, AstraConfig config, double submerged) {
        if (submerged <= 0.0) return;

        double displacedVolume = blocks.size() * submerged;
        vy += displacedVolume * config.buoyancyPerBlock / Math.max(1.0, mass);

        // Buoyancy is a spring: push the hull down and more of it goes under water, which
        // pushes back harder. A spring with no damper oscillates forever, which is exactly what
        // the hull did - it bobbed by up to a full block for as long as it floated.
        //
        // The damping is derived from the spring's own stiffness so it lands near critical for
        // any hull, instead of being one constant tuned against one test boat. Stiffness falls
        // as the hull gets taller, because moving a tall hull by one block changes how much of
        // it is submerged by proportionally less.
        double stiffness = config.buoyancyPerBlock / Math.max(1.0, sizeY());
        double drag = Math.min(0.85, 1.8 * Math.sqrt(stiffness) * Math.min(1.0, submerged / 0.30));
        vy -= vy * drag;

        // With the bobbing gone, a deliberate heave is what keeps a floating hull from looking
        // welded to the surface. It is vertical only: injecting X/Z velocity every tick was a
        // major source of the old stutter.
        double time = level.getGameTime();
        double wave = Math.sin(time * 0.045 + wavePhase) + Math.sin(time * 0.021 + wavePhase * 1.73) * 0.45;
        vy += wave * 0.0025 * submerged;
    }

    // --------------------------------------------------------------- propulsion

    private void applyComponents(ServerLevel level, AstraConfig config, double submerged) {
        double throttle = helmThrottle;
        double steer = helmSteer;
        boolean pilotInputActive = helmCount > 0 && controlTicksRemaining > 0;
        double power = enginePowerScale();

        // Resolve a normalised propulsion direction. Propellers win when they agree; otherwise
        // the helm facing is the predictable fallback for a test craft.
        double driveX = propellerThrustX;
        double driveZ = propellerThrustZ;
        double driveLen = Math.sqrt(driveX * driveX + driveZ * driveZ);
        if (driveLen < 0.01) {
            driveX = helmForwardX;
            driveZ = helmForwardZ;
            driveLen = Math.sqrt(driveX * driveX + driveZ * driveZ);
        }
        if (driveLen > 0.001) {
            driveX /= driveLen;
            driveZ /= driveLen;
        }
        // The drive direction is worked out in local space from the components' own facings,
        // then rotated into the world, so a turning ship accelerates where its bow points.
        double localDriveX = driveX;
        double localDriveZ = driveZ;
        driveX = rotateDirectionX(localDriveX, localDriveZ);
        driveZ = rotateDirectionZ(localDriveX, localDriveZ);

        boolean poweredDrive = enginesEnabled() && propellerCount > 0;

        if (poweredDrive && Math.abs(throttle) > 0.001) {
            double engineCountFactor = Math.min(1.45, 0.85 + Math.sqrt(engineCount) * 0.18);
            if (engineMode == EngineMode.MARINE) {
                // Marine mode is strongest in water and capped at a boat-like speed.
                double mediumEfficiency = submerged > 0.05 ? 1.0 : 0.28;
                double targetSpeed = throttle * (0.10 + 0.20 * power) * mediumEfficiency;
                double acceleration = (0.0045 + 0.0105 * power) * engineCountFactor * mediumEfficiency;
                accelerateTowardHorizontalSpeed(driveX, driveZ, targetSpeed, acceleration);
            } else {
                // Aircraft mode turns the same engine into an air-optimised prop engine. It still
                // works weakly in water so switching mode can never strand a craft.
                double airEfficiency = submerged < 0.20 ? 1.0 : 0.22;
                // Target and cap now agree. They did not before: the formula asked for 0.65
                // blocks per tick while the cap allowed 0.68, so an aircraft simply pinned
                // itself at the limit and flew like a missile.
                double targetSpeed = throttle * (0.09 + 0.29 * power) * airEfficiency;
                // Halved, so reaching cruise takes a couple of seconds instead of one.
                double acceleration = (0.003 + 0.008 * power) * engineCountFactor * airEfficiency;
                accelerateTowardHorizontalSpeed(driveX, driveZ, targetSpeed, acceleration);
            }
        }

        if (enginesEnabled() && thrusterCount > 0 && throttle > 0.0) {
            double modeEfficiency = engineMode == EngineMode.AIRCRAFT ? 1.0 : 0.30;
            double lift = thrusterCount * (0.010 + 0.025 * power) * throttle * modeEfficiency;
            vy += Math.min(0.095, lift);
        }

        if (sailCount > 0) {
            double time = level.getGameTime();
            double airFactor = Math.max(0.15, 1.0 - submerged * 0.72);

            // A very small passive drift keeps sails feeling alive without lateral jitter.
            double passive = Math.sin(time * 0.0035 + wavePhase) * 0.00020 * Math.min(3, sailCount) * airFactor;
            vx += rotateDirectionX(-helmForwardZ, helmForwardX) * passive;
            vz += rotateDirectionZ(-helmForwardZ, helmForwardX) * passive;

            if (pilotInputActive && Math.abs(throttle) > 0.001) {
                // Sails stay below a 100% marine engine. Extra sails improve response more than
                // top speed, so a wall of sails cannot become a rocket.
                double sailResponse = Math.min(1.35, 0.75 + Math.sqrt(sailCount) * 0.20);
                double targetSpeed = throttle * config.maxSailSpeed * airFactor;
                accelerateTowardHorizontalSpeed(
                        rotateDirectionX(helmForwardX, helmForwardZ),
                        rotateDirectionZ(helmForwardX, helmForwardZ),
                        targetSpeed, 0.0060 * sailResponse * airFactor);
            }
        }

        // Steering turns the hull. A rudder only bites when water is flowing past it, so a boat
        // has to be moving to turn; an aircraft's control surfaces work off thrust and keep some
        // authority even when slow, which is what makes a plane flyable.
        if (pilotInputActive && Math.abs(steer) > 0.001) {
            double speed = Math.sqrt(vx * vx + vz * vz);
            double authority;
            if (engineMode == EngineMode.AIRCRAFT) {
                authority = 0.35 + Math.min(1.0, speed / 0.30) * 0.65;
            } else {
                // Below a slow walk a rudder does nothing at all, which is why a moored boat
                // cannot spin on the spot.
                authority = Math.min(1.0, speed / 0.10);
            }
            // A reaction wheel pushes against its own rotor rather than the medium, so it works
            // at a standstill. Three of them give a hull full authority with no way on at all,
            // which is what makes a hovering or becalmed craft steerable.
            if (reactionWheelCount > 0 && enginesEnabled()) {
                authority = Math.max(authority, Math.min(1.0, reactionWheelCount * 0.34) * power);
            }

            double turnPower = engineMode == EngineMode.AIRCRAFT ? 0.32 : 0.24;
            yawVelocity += steer * turnPower * authority * (0.35 + 0.65 * power);
        }

        // Vertical control. Thrusters point down, so they lift; with no thrusters an aircraft
        // still trades a little speed for climb, and a boat simply cannot fly.
        if (pilotInputActive && Math.abs(helmLift) > 0.001 && enginesEnabled()) {
            double climb = 0.0;
            if (thrusterCount > 0) {
                double modeEfficiency = engineMode == EngineMode.AIRCRAFT ? 1.0 : 0.45;
                climb = thrusterCount * (0.008 + 0.020 * power) * modeEfficiency;
            } else if (engineMode == EngineMode.AIRCRAFT && wingCount > 0) {
                climb = 0.010 * power * wingBalanceFactor;
            }
            vy += Math.min(0.11, climb) * helmLift;
        }

        if (balloonCount > 0) {
            applyBalloonLift(level, config, submerged);
        }

        if (wingCount > 0) {
            double horizontalSpeedSq = vx * vx + vz * vz;
            double wingRatio = wingCount / Math.max(1.0, mass);
            double airFactor = Math.max(0.0, 1.0 - submerged);
            double modeFactor = engineMode == EngineMode.AIRCRAFT ? 1.0 : 0.35;
            // Tuned so a reasonably light aircraft with a mirrored wing pair can actually fly once
            // it reaches take-off speed. Marine mode intentionally gets far less lift.
            double lift = horizontalSpeedSq * wingRatio * 4.25 * airFactor * wingBalanceFactor * modeFactor;
            vy += Math.min(0.14, lift);
        }
    }

    /**
     * Lighter-than-air lift from gas envelopes.
     *
     * <p>Unlike a wing this needs no speed, so a balloon hull can take off and hover. Lift falls
     * away with altitude the way it does for a real balloon: without that an airship would simply
     * climb until it left the world, and with it a hull finds its own ceiling and sits there.
     * Submerged bags are crushed and lift nothing, so a sunk airship cannot haul itself out.
     */
    private void applyBalloonLift(ServerLevel level, AstraConfig config, double submerged) {
        double ceiling = config.balloonCeiling;
        double base = level.getSeaLevel();
        double thinning = 1.0 - Math.max(0.0, (y - base) / Math.max(1.0, ceiling - base));
        double density = Math.max(0.0, Math.min(1.0, thinning));

        double airFactor = Math.max(0.0, 1.0 - submerged);
        double lift = balloonCount * config.balloonLiftPerBlock * density * airFactor;
        vy += lift / Math.max(1.0, mass);
    }

    private void accelerateTowardHorizontalSpeed(double dirX, double dirZ, double targetSpeed, double maxAcceleration) {
        double lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq < 1.0E-6 || maxAcceleration <= 0.0) return;
        double currentForward = vx * dirX + vz * dirZ;
        double change = Math.max(-maxAcceleration, Math.min(maxAcceleration, (targetSpeed - currentForward) * 0.22));
        vx += dirX * change;
        vz += dirZ * change;
    }

    private void clampVelocity(AstraConfig config, double submerged) {
        double limit;
        if (engineMode == EngineMode.AIRCRAFT && submerged < 0.20 && enginesEnabled()) {
            limit = config.maxAircraftSpeed;
        } else if (enginesEnabled() && propellerCount > 0) {
            limit = config.maxMarineSpeed;
        } else if (sailCount > 0) {
            limit = config.maxSailSpeed + 0.035;
        } else {
            limit = config.maxDriftSpeed;
        }

        // Sail plus engine earns a small combined bonus instead of additive runaway speed.
        if (sailCount > 0 && enginesEnabled() && propellerCount > 0
                && engineMode == EngineMode.MARINE) {
            limit = Math.min(config.maxMarineSpeed + 0.08, limit + 0.035);
        }

        double horizontalSq = vx * vx + vz * vz;
        if (horizontalSq > limit * limit) {
            double scale = limit / Math.sqrt(horizontalSq);
            vx *= scale;
            vz *= scale;
        }
        vy = Math.max(-config.maxVerticalSpeed, Math.min(config.maxVerticalSpeed, vy));
    }

    // ---------------------------------------------------------------- effects

    private void emitComponentEffects(ServerLevel level, AstraConfig config, double submerged) {
        // Effects are kept light for Android and FCL: server-spawned at 5 Hz, from the cached
        // component list rather than a scan of the whole hull.
        if (config.maxEffectEmitters <= 0 || componentBlocks.isEmpty()) return;
        if ((level.getGameTime() & 3L) != 0L) return;
        if (!enginesEnabled()) return;

        int emitted = 0;
        for (StoredBlock block : componentBlocks) {
            if (emitted >= config.maxEffectEmitters) break;
            double wx = toWorldX(block.localX() + 0.5, block.localZ() + 0.5);
            double wy = y + block.localY() + 0.5;
            double wz = toWorldZ(block.localX() + 0.5, block.localZ() + 0.5);
            BlockState state = block.state();

            if (state.is(AstraBlocks.ENGINE)) {
                level.sendParticles(ParticleTypes.SMOKE, wx, wy + 0.75, wz,
                        Math.max(1, enginePowerStep / 2), 0.10, 0.05, 0.10,
                        0.010 + enginePowerScale() * 0.012);
                emitted++;
            } else if (state.is(AstraBlocks.PROPELLER) && Math.abs(helmThrottle) > 0.01F) {
                int count = Math.max(1, enginePowerStep);
                if (submerged > 0.05) {
                    level.sendParticles(ParticleTypes.SPLASH, wx, wy, wz, count, 0.22, 0.16, 0.22, 0.035);
                } else {
                    level.sendParticles(ParticleTypes.CLOUD, wx, wy, wz,
                            Math.max(1, count / 2), 0.16, 0.10, 0.16, 0.020);
                }
                emitted++;
            } else if (state.is(AstraBlocks.THRUSTER) && helmThrottle > 0.01F) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy - 0.25, wz,
                        Math.max(1, enginePowerStep), 0.08, 0.06, 0.08,
                        0.020 + enginePowerScale() * 0.02);
                emitted++;
            }
        }
    }

    // -------------------------------------------------------------- collision

    private void moveAxis(ServerLevel level, double dx, double dy, double dz) {
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < REST_EPSILON) return;

        Direction movementDirection = dx > 0.0 ? Direction.EAST
                : dx < 0.0 ? Direction.WEST
                : dy > 0.0 ? Direction.UP
                : dy < 0.0 ? Direction.DOWN
                : dz > 0.0 ? Direction.SOUTH : Direction.NORTH;

        if (canOccupy(level, x + dx, y + dy, z + dz, movementDirection)) {
            x += dx;
            y += dy;
            z += dz;
            return;
        }

        // Binary search for the last safe fraction of the step. Eight iterations resolve to
        // better than 1/256 of a block, which is finer than the renderer can show.
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 8; i++) {
            double mid = (lo + hi) * 0.5;
            if (canOccupy(level, x + dx * mid, y + dy * mid, z + dz * mid, movementDirection)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        x += dx * lo;
        y += dy * lo;
        z += dz * lo;

        if (dx != 0.0) vx = 0.0;
        if (dy != 0.0) vy = 0.0;
        if (dz != 0.0) vz = 0.0;
    }

    /**
     * Narrow collision test: only the blocks with an exposed face on the leading side are
     * checked. Testing the construct's full AABB made it behave like a solid cuboid that
     * included its own empty interior, so a boat would stop short of a shoreline the real hull
     * could clear.
     */
    private boolean canOccupy(ServerLevel level, double baseX, double baseY, double baseZ,
                              Direction movementDirection) {
        return shellClear(level, collisionShell[movementDirection.ordinal()], baseX, baseY, baseZ);
    }

    /**
     * Tests a set of hull blocks against world terrain at a given origin.
     *
     * <p>A rotated block is no longer axis aligned, so it is tested as the axis-aligned box that
     * encloses it. That box grows to about 1.41 blocks across at 45 degrees, which makes contact
     * slightly early at intermediate angles - deliberately the safe direction, since the
     * alternative is a hull visibly sinking into a cliff face.
     */
    private boolean shellClear(ServerLevel level, List<StoredBlock> shell,
                               double baseX, double baseY, double baseZ) {
        double halfExtent = 0.5 * (Math.abs(yawCos) + Math.abs(yawSin));
        for (StoredBlock block : shell) {
            double centreLocalX = block.localX() + 0.5;
            double centreLocalZ = block.localZ() + 0.5;
            double dx = centreLocalX - pivotX;
            double dz = centreLocalZ - pivotZ;
            double cx = baseX + pivotX + dx * yawCos - dz * yawSin;
            double cz = baseZ + pivotZ + dx * yawSin + dz * yawCos;
            double by = baseY + block.localY();

            if (!level.noBlockCollision(null, new AABB(
                    cx - halfExtent, by, cz - halfExtent,
                    cx + halfExtent, by + 1.0, cz + halfExtent))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Integrates yaw, refusing a turn that would sweep the hull through terrain.
     *
     * <p>Reverting the whole step rather than turning partway keeps orientation and collision in
     * agreement: a hull that rotated into a wall and stopped there would have its blocks inside
     * the wall until something moved it back out.
     */
    private void applyYaw(ServerLevel level) {
        // A hull turns against water or air resistance, so an untouched wheel settles quickly.
        yawVelocity *= 0.86;
        if (Math.abs(yawVelocity) < 0.01) {
            yawVelocity = 0.0;
            return;
        }
        yawVelocity = Math.max(-MAX_YAW_SPEED, Math.min(MAX_YAW_SPEED, yawVelocity));

        double before = yaw;
        setYaw(yaw + yawVelocity);
        if (!shellClear(level, outerShell, x, y, z)) {
            setYaw(before);
            yawVelocity = 0.0;
        }
    }

    // --------------------------------------------------------------- indexing

    /**
     * Packs a local coordinate triple into one long. Twenty-one bits per axis covers roughly
     * ±1,048,576, far beyond the configurable block limit, so keys never collide.
     */
    private static long localKey(int x, int y, int z) {
        final long mask = 0x1FFFFFL;
        return ((long) x & mask) << 42 | ((long) y & mask) << 21 | ((long) z & mask);
    }

    private static long columnKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) << 32 | ((long) z & 0xFFFFFFFFL);
    }
}
