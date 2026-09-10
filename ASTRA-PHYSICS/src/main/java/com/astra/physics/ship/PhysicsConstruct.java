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

    public enum EngineMode { MARINE, AIRCRAFT }

    private final UUID id;
    private final List<StoredBlock> blocks;
    private final Map<Long, StoredBlock> index = new HashMap<>();
    private final double wavePhase;

    // ---- caches rebuilt only when the block set changes
    private final List<StoredBlock>[] collisionShell = newShellArray();
    private final List<StoredBlock> componentBlocks = new ArrayList<>();
    private final List<StoredBlock> hopperBlocks = new ArrayList<>();
    private final List<StoredBlock> buoyancySamples = new ArrayList<>();
    private final Map<Long, List<StoredBlock>> columnIndex = new HashMap<>();

    private int minLocalX, minLocalY, minLocalZ;
    private int maxLocalX, maxLocalY, maxLocalZ;
    private double mass;

    private int helmCount, engineCount, propellerCount, sailCount, wingCount, thrusterCount;
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
    public int helmCount() { return helmCount; }
    public int engineCount() { return engineCount; }
    public int propellerCount() { return propellerCount; }
    public int sailCount() { return sailCount; }
    public int wingCount() { return wingCount; }
    public int thrusterCount() { return thrusterCount; }
    public boolean enginesEnabled() { return enginePowerStep > 0; }
    public EngineMode engineMode() { return engineMode; }
    public int enginePowerStep() { return enginePowerStep; }
    public int enginePowerPercent() { return enginePowerStep * 25; }
    public double enginePowerScale() { return enginePowerStep / 4.0; }
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

        if (throttle != netThrottle || steer != netSteer
                || enginePowerStep != netPowerStep || aircraft != netAircraftMode) {
            netThrottle = throttle;
            netSteer = steer;
            netPowerStep = enginePowerStep;
            netAircraftMode = aircraft;
            stateDirty = true;
        }
    }

    /** True when the construct actually moved this tick, so idle hulls cost no bandwidth. */
    public boolean hasMoved() {
        return Math.abs(deltaX()) > REST_EPSILON
                || Math.abs(deltaY()) > REST_EPSILON
                || Math.abs(deltaZ()) > REST_EPSILON;
    }

    /** Restores saved motion and control state during world load. */
    public void restoreRuntimeState(double vx, double vy, double vz,
                                    EngineMode mode, int powerStep) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.engineMode = mode == null ? EngineMode.MARINE : mode;
        this.enginePowerStep = Math.max(0, Math.min(4, powerStep));
    }

    public AABB aabbAt(double px, double py, double pz) {
        return new AABB(
                px + minLocalX, py + minLocalY, pz + minLocalZ,
                px + maxLocalX + 1.0, py + maxLocalY + 1.0, pz + maxLocalZ + 1.0
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
        helmThrottle = clampUnit(throttle);
        helmSteer = clampUnit(steer);
        controlTicksRemaining = CONTROL_GRACE_TICKS;
    }

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
        double baseX = previousTransform ? previousX : x;
        double baseY = previousTransform ? previousY : y;
        double baseZ = previousTransform ? previousZ : z;
        int localX = (int) Math.floor(worldX - baseX);
        int localZ = (int) Math.floor(worldZ - baseZ);

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
        int centerX = (int) Math.floor(worldX - x);
        int centerZ = (int) Math.floor(worldZ - z);
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
        double baseX = previousTransform ? previousX : x;
        double baseY = previousTransform ? previousY : y;
        double baseZ = previousTransform ? previousZ : z;
        List<StoredBlock> column = columnIndex.get(columnKey(
                (int) Math.floor(worldX - baseX), (int) Math.floor(worldZ - baseZ)));
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
            double worldX = x + block.localX() + 0.5;
            double worldZ = z + block.localZ() + 0.5;
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

        boolean poweredDrive = enginesEnabled() && engineCount > 0 && propellerCount > 0;

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
                double targetSpeed = throttle * (0.20 + 0.45 * power) * airEfficiency;
                double acceleration = (0.006 + 0.016 * power) * engineCountFactor * airEfficiency;
                accelerateTowardHorizontalSpeed(driveX, driveZ, targetSpeed, acceleration);
            }
        }

        if (enginesEnabled() && engineCount > 0 && thrusterCount > 0 && throttle > 0.0) {
            double modeEfficiency = engineMode == EngineMode.AIRCRAFT ? 1.0 : 0.30;
            double lift = thrusterCount * (0.010 + 0.025 * power) * throttle * modeEfficiency;
            vy += Math.min(0.095, lift);
        }

        if (sailCount > 0) {
            double time = level.getGameTime();
            double airFactor = Math.max(0.15, 1.0 - submerged * 0.72);

            // A very small passive drift keeps sails feeling alive without lateral jitter.
            double passive = Math.sin(time * 0.0035 + wavePhase) * 0.00020 * Math.min(3, sailCount) * airFactor;
            vx += -helmForwardZ * passive;
            vz += helmForwardX * passive;

            if (pilotInputActive && Math.abs(throttle) > 0.001) {
                // Sails stay below a 100% marine engine. Extra sails improve response more than
                // top speed, so a wall of sails cannot become a rocket.
                double sailResponse = Math.min(1.35, 0.75 + Math.sqrt(sailCount) * 0.20);
                double targetSpeed = throttle * config.maxSailSpeed * airFactor;
                accelerateTowardHorizontalSpeed(helmForwardX, helmForwardZ, targetSpeed,
                        0.0060 * sailResponse * airFactor);
            }
        }

        // Steering is rudder thrust, not a heading change. The construct has no yaw rotation, so
        // turning the hull would move the visual model away from the collision volume every other
        // system uses. It scales with actual speed so A/D cannot kick a stationary hull sideways.
        if (pilotInputActive && Math.abs(steer) > 0.001) {
            double speed = Math.sqrt(vx * vx + vz * vz);
            if (speed > 0.015) {
                double sideX = -helmForwardZ;
                double sideZ = helmForwardX;
                double response = engineMode == EngineMode.AIRCRAFT ? 0.010 : 0.0065;
                double correction = Math.min(response, speed * 0.035) * steer;
                vx += sideX * correction;
                vz += sideZ * correction;
            }
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
        } else if (enginesEnabled() && engineCount > 0 && propellerCount > 0) {
            limit = config.maxMarineSpeed;
        } else if (sailCount > 0) {
            limit = config.maxSailSpeed + 0.035;
        } else {
            limit = config.maxDriftSpeed;
        }

        // Sail plus engine earns a small combined bonus instead of additive runaway speed.
        if (sailCount > 0 && enginesEnabled() && engineCount > 0 && propellerCount > 0
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
            double wx = x + block.localX() + 0.5;
            double wy = y + block.localY() + 0.5;
            double wz = z + block.localZ() + 0.5;
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
        for (StoredBlock block : collisionShell[movementDirection.ordinal()]) {
            double bx0 = baseX + block.localX();
            double by0 = baseY + block.localY();
            double bz0 = baseZ + block.localZ();
            if (!level.noBlockCollision(null, new AABB(bx0, by0, bz0, bx0 + 1.0, by0 + 1.0, bz0 + 1.0))) {
                return false;
            }
        }
        return true;
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
