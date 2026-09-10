package com.astra.physics.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.astra.physics.AstraPhysics;
import com.astra.physics.block.AstraFacingBlock;
import com.astra.physics.config.AstraConfig;
import com.astra.physics.network.ConstructBreakBlockPayload;
import com.astra.physics.network.ConstructControlPayload;
import com.astra.physics.network.ConstructInteractPayload;
import com.astra.physics.network.ConstructPlaceBlockPayload;
import com.astra.physics.network.ConstructRemovePayload;
import com.astra.physics.network.ConstructSpawnPayload;
import com.astra.physics.network.ConstructStatePayload;
import com.astra.physics.network.ConstructSpawnPayload.NetBlock;
import com.astra.physics.network.ConstructTransformPayload;
import com.astra.physics.network.PilotStatePayload;
import com.astra.physics.registry.AstraBlocks;
import com.astra.physics.util.AstraText;

/**
 * Owns every server-side ASTRA construct: their lifecycle, persistence, player interaction and
 * the packets that mirror them to clients.
 *
 * <h2>Client tracking</h2>
 * The full block list of a construct is expensive to send, so each player has a set of constructs
 * they have already been told about. A player entering range gets one spawn packet; leaving range
 * gets one remove packet; and per-tick transform packets go only to players who are tracking that
 * construct and only when it actually moved. The previous build broadcast every construct's full
 * block list to every player in the dimension on every block placement, and a transform packet for
 * every construct to every player twenty times a second regardless of distance.
 */
public final class PhysicsConstructManager {
    private static final Map<ResourceKey<Level>, Map<UUID, PhysicsConstruct>> BY_LEVEL = new HashMap<>();
    private static final Map<UUID, PilotSession> PILOTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> TRACKED_BY_PLAYER = new HashMap<>();

    private static final double STAND_BELOW_TOLERANCE = 0.60;
    private static final double STAND_ABOVE_TOLERANCE = 0.75;
    /** Autosave interval in ticks. Ten minutes, matching vanilla's own autosave cadence. */
    private static final long AUTOSAVE_INTERVAL_TICKS = 12_000L;

    private PhysicsConstructManager() {}

    /** Per-pilot state, including the token bucket that bounds inbound control packets. */
    private static final class PilotSession {
        private final UUID constructId;
        private final BlockPos helmPos;
        private long windowStartTick;
        private int packetsInWindow;

        private PilotSession(UUID constructId, BlockPos helmPos) {
            this.constructId = constructId;
            this.helmPos = helmPos;
        }
    }

    // ------------------------------------------------------------- lifecycle

    public static void onLevelLoad(ServerLevel level) {
        Map<UUID, PhysicsConstruct> constructs = new HashMap<>();
        for (PhysicsConstruct construct : ConstructStorage.load(level)) {
            constructs.put(construct.id(), construct);
        }
        BY_LEVEL.put(level.dimension(), constructs);
    }

    public static void onLevelUnload(ServerLevel level) {
        saveLevel(level);
        BY_LEVEL.remove(level.dimension());
    }

    public static void saveLevel(ServerLevel level) {
        Map<UUID, PhysicsConstruct> constructs = BY_LEVEL.get(level.dimension());
        if (constructs != null) {
            ConstructStorage.save(level, constructs.values());
        }
    }

    public static void saveAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            saveLevel(level);
        }
    }

    /**
     * Drops every scrap of state. Static maps that outlive a server are how a singleplayer client
     * ends up rendering the previous world's ships in the next one, and how a dedicated server
     * leaks a construct for every reload.
     */
    public static void onServerStopped() {
        BY_LEVEL.clear();
        PILOTS.clear();
        TRACKED_BY_PLAYER.clear();
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        PILOTS.remove(player.getUUID());
        TRACKED_BY_PLAYER.remove(player.getUUID());
    }

    // -------------------------------------------------------------- assembly

    public static boolean assemble(ServerPlayer player, Level level, List<BlockPos> selected) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        AstraConfig config = AstraConfig.get();

        if (selected.isEmpty() || selected.size() > config.maxConstructBlocks) {
            AstraText.sendActionBar(player, AstraText.warning("assemble.invalid_size", config.maxConstructBlocks));
            return false;
        }

        Map<UUID, PhysicsConstruct> constructs =
                BY_LEVEL.computeIfAbsent(serverLevel.dimension(), ignored -> new HashMap<>());
        if (constructs.size() >= config.maxConstructsPerDimension) {
            AstraText.sendActionBar(player, AstraText.warning("assemble.too_many", config.maxConstructsPerDimension));
            return false;
        }

        if (config.requireConnectedSelection && !isConnected(selected)) {
            AstraText.sendActionBar(player, AstraText.warning("assemble.not_connected"));
            return false;
        }

        // Validate everything before touching the world. A half-assembled construct that left
        // some blocks deleted and some in place would be unrecoverable for the player.
        for (BlockPos pos : selected) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
                AstraText.sendActionBar(player,
                        AstraText.warning("assemble.unsupported_block", pos.toShortString()));
                return false;
            }
            if (!mayModify(serverLevel, player, pos, state)) {
                AstraText.sendActionBar(player, AstraText.warning("assemble.protected", pos.toShortString()));
                return false;
            }
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : selected) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }

        List<StoredBlock> stored = new ArrayList<>(selected.size());
        int blockEntityCount = 0;
        for (BlockPos pos : selected) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            ConstructBlockEntityData data = null;
            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            if (blockEntity != null) {
                try {
                    data = ConstructBlockEntityData.capture(blockEntity, serverLevel);
                    blockEntityCount++;
                } catch (RuntimeException ex) {
                    AstraPhysics.LOGGER.error(
                            "Failed to snapshot BlockEntity at {} while assembling an ASTRA construct", pos, ex);
                    AstraText.sendActionBar(player,
                            AstraText.warning("assemble.blockentity_failed", pos.toShortString()));
                    return false;
                }
            }
            stored.add(new StoredBlock(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ, state, data));
        }

        if (stored.isEmpty()) {
            AstraText.sendActionBar(player, AstraText.warning("assemble.nothing_left"));
            return false;
        }

        // The BlockEntity inventories were copied above. Clearing the live containers before
        // removal stops any block whose removal path drops its inventory from duplicating items.
        for (BlockPos pos : selected) {
            if (serverLevel.getBlockEntity(pos) instanceof Container container) {
                container.clearContent();
                container.setChanged();
            }
        }
        for (BlockPos pos : selected) {
            serverLevel.removeBlock(pos, false);
        }

        UUID id = UUID.randomUUID();
        PhysicsConstruct construct = new PhysicsConstruct(id, stored, minX, minY + 0.20, minZ);
        constructs.put(id, construct);

        AstraText.sendActionBar(player, AstraText.success("assemble.done",
                stored.size(), blockEntityCount, construct.engineCount(), construct.propellerCount(),
                construct.sailCount(), construct.wingCount(), construct.thrusterCount()));
        AstraPhysics.LOGGER.info("{} assembled ASTRA construct {} with {} blocks and {} BlockEntities in {}",
                player.getGameProfile().getName(), id, stored.size(), blockEntityCount,
                serverLevel.dimension().location());

        saveLevel(serverLevel);
        return true;
    }

    /**
     * Turns a construct back into ordinary world blocks.
     *
     * <p>Without this the only way to recover an assembled build was to break it one block at a
     * time, which meant a build could be assembled but never truly undone.
     *
     * @return the number of blocks returned to the world, or -1 if the construct was not found.
     */
    public static int disassemble(ServerLevel level, UUID constructId, ServerPlayer requester) {
        PhysicsConstruct construct = get(level, constructId);
        if (construct == null) {
            return -1;
        }

        int baseX = (int) Math.floor(construct.x());
        int baseY = (int) Math.floor(construct.y());
        int baseZ = (int) Math.floor(construct.z());

        int placed = 0;
        int dropped = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (StoredBlock block : construct.blocks()) {
            cursor.set(baseX + block.localX(), baseY + block.localY(), baseZ + block.localZ());
            BlockState existing = level.getBlockState(cursor);

            if (!existing.isAir() && !existing.canBeReplaced()) {
                // Something moved into the space while the construct was flying. Drop the block
                // as an item rather than overwriting whatever is already there.
                dropped += dropStoredBlock(level, cursor, block);
                continue;
            }

            level.setBlockAndUpdate(cursor.immutable(), block.state());
            placed++;

            ConstructBlockEntityData data = block.blockEntityData();
            if (data != null && data.hasInventory()
                    && level.getBlockEntity(cursor) instanceof Container container) {
                for (ItemStack overflow : data.restoreInto(container)) {
                    Block.popResource(level, cursor, overflow);
                }
            } else if (data != null && data.hasInventory()) {
                // The block no longer exposes a container. Everything it held still goes back.
                for (ItemStack content : data.copyContents()) {
                    if (!content.isEmpty()) {
                        Block.popResource(level, cursor, content);
                    }
                }
            }
        }

        removeConstruct(level, constructId);
        saveLevel(level);

        if (requester != null) {
            AstraText.sendActionBar(requester, AstraText.success("disassemble.done", placed, dropped));
        }
        AstraPhysics.LOGGER.info("Disassembled ASTRA construct {}: {} blocks placed, {} dropped as items",
                constructId, placed, dropped);
        return placed;
    }

    private static int dropStoredBlock(ServerLevel level, BlockPos pos, StoredBlock block) {
        int drops = 0;
        ItemStack stack = new ItemStack(block.state().getBlock().asItem());
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
            drops++;
        }
        ConstructBlockEntityData data = block.blockEntityData();
        if (data != null && data.hasInventory()) {
            for (ItemStack content : data.copyContents()) {
                if (!content.isEmpty()) {
                    Block.popResource(level, pos, content);
                    drops++;
                }
            }
        }
        return drops;
    }

    /**
     * Flood fill over the six face neighbours. A selection made of two separate lumps would
     * otherwise assemble into one construct whose halves fly around with nothing between them.
     */
    private static boolean isConnected(List<BlockPos> selected) {
        if (selected.size() <= 1) {
            return true;
        }
        Set<Long> remaining = new HashSet<>(selected.size() * 2);
        for (BlockPos pos : selected) {
            remaining.add(pos.asLong());
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = selected.get(0);
        queue.add(start);
        remaining.remove(start.asLong());

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);
                if (remaining.remove(neighbour.asLong())) {
                    queue.addLast(neighbour);
                }
            }
        }
        return remaining.isEmpty();
    }

    /**
     * Assembly deletes world blocks, so it has to respect the same rules block breaking does.
     * Firing Fabric's block-break event lets land-claim and protection mods veto it exactly as
     * they would veto a pickaxe.
     */
    private static boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!AstraConfig.get().respectBlockProtection) {
            return true;
        }
        if (!player.mayBuild()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server != null && server.isUnderSpawnProtection(level, pos, player)) {
            return false;
        }
        return PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
    }

    // ----------------------------------------------------------- interaction

    /** Server-authoritative placement of a BlockItem onto construct-local coordinates. */
    public static void placeBlock(ServerPlayer player, ConstructPlaceBlockPayload payload) {
        ServerLevel level = player.level();
        PhysicsConstruct construct = get(level, payload.constructId());
        if (construct == null || !construct.hasLocalBlock(payload.localX(), payload.localY(), payload.localZ())) {
            return;
        }

        ItemStack stack = player.getItemInHand(payload.hand());
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        int targetX = payload.localX() + payload.face().getStepX();
        int targetY = payload.localY() + payload.face().getStepY();
        int targetZ = payload.localZ() + payload.face().getStepZ();

        if (construct.hasLocalBlock(targetX, targetY, targetZ)
                || construct.blockCount() >= AstraConfig.get().maxConstructBlocks) {
            AstraText.sendActionBar(player,
                    AstraText.warning("place.blocked", AstraConfig.get().maxConstructBlocks));
            return;
        }
        if (!withinInteractionDistance(player, construct, targetX, targetY, targetZ)) {
            return;
        }

        BlockState state = orientPlacedState(blockItem.getBlock().defaultBlockState(), player, payload.face());
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            AstraText.sendActionBar(player, AstraText.warning("place.fluid"));
            return;
        }

        AABB targetBox = new AABB(
                construct.x() + targetX, construct.y() + targetY, construct.z() + targetZ,
                construct.x() + targetX + 1.0, construct.y() + targetY + 1.0, construct.z() + targetZ + 1.0
        );
        if (!level.noBlockCollision(null, targetBox)) {
            AstraText.sendActionBar(player, AstraText.warning("place.through_terrain"));
            return;
        }

        ConstructBlockEntityData blockEntityData = null;
        if (state.hasBlockEntity() && blockItem.getBlock() instanceof EntityBlock entityBlock) {
            try {
                BlockEntity created = entityBlock.newBlockEntity(BlockPos.ZERO, state);
                if (created != null) {
                    blockEntityData = ConstructBlockEntityData.empty(created);
                }
            } catch (RuntimeException ex) {
                AstraPhysics.LOGGER.warn("Could not initialise construct-local BlockEntity data for {}", state, ex);
            }
        }

        if (!construct.addBlock(targetX, targetY, targetZ, state, blockEntityData)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        AstraText.sendActionBar(player, AstraText.info("place.done", construct.blockCount()));
    }

    /** Breaks a construct-local block and returns it, with any contents, to the player. */
    public static void breakBlock(ServerPlayer player, ConstructBreakBlockPayload payload) {
        ServerLevel level = player.level();
        PhysicsConstruct construct = get(level, payload.constructId());
        if (construct == null) {
            return;
        }
        StoredBlock target = construct.getLocalBlock(payload.localX(), payload.localY(), payload.localZ());
        if (target == null
                || !withinInteractionDistance(player, construct, target.localX(), target.localY(), target.localZ())) {
            return;
        }

        // Removing the block a pilot is steering from has to release them, not leave them bound
        // to a helm that no longer exists.
        if (target.state().is(AstraBlocks.HELM)) {
            releasePilotsOfHelm(construct.id(), payload.localX(), payload.localY(), payload.localZ(), level);
        }

        StoredBlock removed = construct.removeBlock(target.localX(), target.localY(), target.localZ());
        if (removed == null) {
            return;
        }

        if (!player.getAbilities().instabuild) {
            ItemStack blockStack = new ItemStack(removed.state().getBlock().asItem());
            if (!blockStack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(blockStack);
            }
        }
        ConstructBlockEntityData data = removed.blockEntityData();
        if (data != null && data.hasInventory()) {
            for (ItemStack content : data.copyContents()) {
                if (!content.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(content);
                }
            }
        }

        if (construct.blockCount() == 0) {
            removeConstruct(level, construct.id());
        }
        AstraText.sendActionBar(player, AstraText.info("break.done"));
    }

    /** Right click: engines cycle power, helms toggle piloting, containers open local storage. */
    public static void interact(ServerPlayer player, ConstructInteractPayload payload) {
        ServerLevel level = player.level();
        PhysicsConstruct construct = get(level, payload.constructId());
        if (construct == null) {
            return;
        }
        StoredBlock block = construct.getLocalBlock(payload.localX(), payload.localY(), payload.localZ());
        if (block == null
                || !withinInteractionDistance(player, construct, block.localX(), block.localY(), block.localZ())) {
            return;
        }

        if (block.state().is(AstraBlocks.ENGINE)) {
            if (player.isShiftKeyDown()) {
                PhysicsConstruct.EngineMode mode = construct.toggleEngineMode();
                AstraText.sendActionBar(player, AstraText.info("engine.mode",
                        modeLabel(mode), construct.enginePowerPercent()));
            } else {
                int power = construct.cycleEnginePower();
                AstraText.sendActionBar(player, AstraText.info("engine.power",
                        power == 0 ? AstraText.plain("engine.off") : Component.literal(power + "%"),
                        modeLabel(construct.engineMode())));
            }
            return;
        }

        if (block.state().is(AstraBlocks.HELM)) {
            handleHelmInteraction(player, construct, payload);
            return;
        }

        ConstructBlockEntityData data = block.blockEntityData();
        if (data != null && data.hasInventory()) {
            openLocalContainer(player, data, block.state().getBlock().getName());
            return;
        }
        if (data != null) {
            AstraText.sendActionBar(player, AstraText.info("interact.data_only", data.sourceClass()));
        }
    }

    private static void handleHelmInteraction(ServerPlayer player, PhysicsConstruct construct,
                                              ConstructInteractPayload payload) {
        PilotSession existing = PILOTS.get(player.getUUID());
        if (existing != null && existing.constructId.equals(construct.id())) {
            releasePilot(player, construct.id());
            return;
        }

        BlockPos helmPos = new BlockPos(payload.localX(), payload.localY(), payload.localZ());
        if (isHelmOccupied(construct.id(), helmPos, player.getUUID())) {
            AstraText.sendActionBar(player, AstraText.warning("helm.occupied"));
            return;
        }

        PILOTS.put(player.getUUID(), new PilotSession(construct.id(), helmPos));

        Component drive;
        if (construct.propellerCount() > 0 && construct.engineCount() > 0) {
            drive = AstraText.plain("drive.engine",
                    modeLabel(construct.engineMode()), construct.enginePowerPercent());
        } else if (construct.sailCount() > 0) {
            drive = AstraText.plain("drive.sail");
        } else {
            drive = AstraText.plain("drive.none");
        }
        AstraText.sendActionBar(player, AstraText.success("helm.engaged", drive));

        send(player, PilotStatePayload.TYPE, new PilotStatePayload(
                construct.id(), true, payload.localX(), payload.localY(), payload.localZ()));

        // Face the wheel once on mount. Earlier builds re-applied the rotation every tick, which
        // meant a pilot could not look around at all and the server fought every look packet.
        StoredBlock helm = construct.getLocalBlock(payload.localX(), payload.localY(), payload.localZ());
        if (helm != null && helm.state().hasProperty(AstraFacingBlock.FACING)) {
            player.setYRot(helm.state().getValue(AstraFacingBlock.FACING).getOpposite().toYRot());
            player.setXRot(8.0F);
        }
        anchorPilot(player, construct);
    }

    /**
     * Engine mode names are written as literal keys rather than built from the enum name, so the
     * asset validator can prove every key it sees in the source actually has a translation.
     */
    private static Component modeLabel(PhysicsConstruct.EngineMode mode) {
        return mode == PhysicsConstruct.EngineMode.AIRCRAFT
                ? AstraText.plain("engine.mode.aircraft")
                : AstraText.plain("engine.mode.marine");
    }

    private static boolean isHelmOccupied(UUID constructId, BlockPos helmPos, UUID excludingPlayer) {
        for (Map.Entry<UUID, PilotSession> entry : PILOTS.entrySet()) {
            if (entry.getKey().equals(excludingPlayer)) {
                continue;
            }
            PilotSession session = entry.getValue();
            if (session.constructId.equals(constructId) && session.helmPos.equals(helmPos)) {
                return true;
            }
        }
        return false;
    }

    public static void control(ServerPlayer player, ConstructControlPayload payload) {
        ServerLevel level = player.level();
        PilotSession session = PILOTS.get(player.getUUID());
        if (session == null || !session.constructId.equals(payload.constructId())) {
            return;
        }

        PhysicsConstruct construct = get(level, payload.constructId());
        if (construct == null || construct.helmCount() <= 0) {
            return;
        }
        if (!construct.boundingBox().inflate(10.0).contains(player.position())) {
            return;
        }
        if (!acceptControlPacket(level, session)) {
            return;
        }
        construct.applyHelmInput(payload.safeThrottle(), payload.safeSteer());
    }

    /**
     * Simple per-second token bucket. Control packets drive server-side physics, so a client that
     * sends thousands per tick must not be able to turn that into server load.
     */
    private static boolean acceptControlPacket(ServerLevel level, PilotSession session) {
        long tick = level.getGameTime();
        if (tick - session.windowStartTick >= 20L) {
            session.windowStartTick = tick;
            session.packetsInWindow = 0;
        }
        if (session.packetsInWindow >= AstraConfig.get().maxControlPacketsPerSecond) {
            return false;
        }
        session.packetsInWindow++;
        return true;
    }

    /** Explicitly leave the helm. Shift or Jump on the client calls this. */
    public static void releasePilot(ServerPlayer player, UUID requestedConstructId) {
        PilotSession session = PILOTS.get(player.getUUID());
        if (session == null || !session.constructId.equals(requestedConstructId)) {
            return;
        }

        PhysicsConstruct construct = get(player.level(), session.constructId);
        if (construct != null) {
            construct.applyHelmInput(0.0F, 0.0F);
        }
        PILOTS.remove(player.getUUID());

        AstraText.sendActionBar(player, AstraText.info("helm.released"));
        send(player, PilotStatePayload.TYPE, new PilotStatePayload(
                session.constructId, false,
                session.helmPos.getX(), session.helmPos.getY(), session.helmPos.getZ()));
    }

    private static void releasePilotsOfHelm(UUID constructId, int localX, int localY, int localZ, ServerLevel level) {
        BlockPos helmPos = new BlockPos(localX, localY, localZ);
        List<UUID> affected = new ArrayList<>();
        for (Map.Entry<UUID, PilotSession> entry : PILOTS.entrySet()) {
            PilotSession session = entry.getValue();
            if (session.constructId.equals(constructId) && session.helmPos.equals(helmPos)) {
                affected.add(entry.getKey());
            }
        }
        for (UUID playerId : affected) {
            ServerPlayer player = level.getServer() == null
                    ? null : level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                releasePilot(player, constructId);
            } else {
                PILOTS.remove(playerId);
            }
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(ServerLevel level) {
        Map<UUID, PhysicsConstruct> constructs = BY_LEVEL.get(level.dimension());
        if (constructs == null || constructs.isEmpty()) {
            return;
        }

        for (PhysicsConstruct construct : constructs.values()) {
            construct.tick(level);
        }

        for (ServerPlayer player : level.players()) {
            if (!anchorPilotOf(player, constructs)) {
                supportPlayer(player, constructs.values());
            }
        }

        syncToPlayers(level, constructs);

        if (level.getGameTime() % AUTOSAVE_INTERVAL_TICKS == 0L) {
            saveLevel(level);
        }
    }

    // --------------------------------------------------------------- syncing

    private static void syncToPlayers(ServerLevel level, Map<UUID, PhysicsConstruct> constructs) {
        double range = AstraConfig.get().renderDistance;
        double rangeSq = range * range;

        for (PhysicsConstruct construct : constructs.values()) {
            boolean structureDirty = construct.isStructureDirty();
            boolean stateDirty = construct.isStateDirty();
            boolean moved = construct.hasMoved();
            ConstructStatePayload statePayload = stateDirty ? statePayloadFor(construct) : null;

            for (ServerPlayer player : level.players()) {
                Set<UUID> tracked = TRACKED_BY_PLAYER.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
                boolean inRange = construct.boundingBox().distanceToSqr(player.position()) <= rangeSq;

                if (!inRange) {
                    if (tracked.remove(construct.id())) {
                        send(player, ConstructRemovePayload.TYPE, new ConstructRemovePayload(construct.id()));
                    }
                    continue;
                }

                if (tracked.add(construct.id()) || structureDirty) {
                    sendFullState(player, construct);
                    // A player who has just started tracking needs the drivetrain state too,
                    // or the ship's components would stay frozen until the next change.
                    send(player, ConstructStatePayload.TYPE, statePayloadFor(construct));
                    continue;
                }
                if (statePayload != null) {
                    send(player, ConstructStatePayload.TYPE, statePayload);
                }
                if (moved) {
                    send(player, ConstructTransformPayload.TYPE, new ConstructTransformPayload(
                            construct.id(), construct.x(), construct.y(), construct.z()));
                }
            }
            construct.clearStructureDirty();
            construct.clearStateDirty();
        }
    }

    private static ConstructStatePayload statePayloadFor(PhysicsConstruct construct) {
        return new ConstructStatePayload(
                construct.id(),
                construct.enginePowerStep(),
                construct.engineMode() == PhysicsConstruct.EngineMode.AIRCRAFT,
                construct.netThrottle(),
                construct.netSteer());
    }

    private static void sendFullState(ServerPlayer player, PhysicsConstruct construct) {
        List<StoredBlock> blocks = construct.blocks();
        List<NetBlock> netBlocks = new ArrayList<>(blocks.size());
        for (StoredBlock block : blocks) {
            netBlocks.add(new NetBlock(block.localX(), block.localY(), block.localZ(),
                    Block.getId(block.state())));
        }
        send(player, ConstructSpawnPayload.TYPE, new ConstructSpawnPayload(
                construct.id(), construct.x(), construct.y(), construct.z(),
                construct.sizeX(), construct.sizeY(), construct.sizeZ(), List.copyOf(netBlocks)));
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload>
            void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
                      T payload) {
        if (ServerPlayNetworking.canSend(player, type)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ------------------------------------------------------------- riders

    private static boolean anchorPilotOf(ServerPlayer player, Map<UUID, PhysicsConstruct> constructs) {
        PilotSession session = PILOTS.get(player.getUUID());
        if (session == null) {
            return false;
        }

        PhysicsConstruct construct = constructs.get(session.constructId);
        if (construct == null) {
            // The construct is gone or in another dimension; drop the stale session.
            PILOTS.remove(player.getUUID());
            return false;
        }

        StoredBlock helm = construct.getLocalBlock(
                session.helmPos.getX(), session.helmPos.getY(), session.helmPos.getZ());
        if (helm == null || !helm.state().is(AstraBlocks.HELM)) {
            releasePilot(player, session.constructId);
            return false;
        }
        anchorPilot(player, construct);
        return true;
    }

    /**
     * Holds the pilot at the wheel so the movement keys can act as ship controls instead of
     * walking the player off the helm. Rotation is deliberately not touched here — the player
     * keeps full control of the camera while piloting.
     */
    private static void anchorPilot(ServerPlayer player, PhysicsConstruct construct) {
        PilotSession session = PILOTS.get(player.getUUID());
        if (session == null) {
            return;
        }
        StoredBlock helm = construct.getLocalBlock(
                session.helmPos.getX(), session.helmPos.getY(), session.helmPos.getZ());
        if (helm == null) {
            return;
        }

        Direction facing = helm.state().hasProperty(AstraFacingBlock.FACING)
                ? helm.state().getValue(AstraFacingBlock.FACING)
                : Direction.NORTH;

        // The wheel is modelled on the FACING side. 0.90 keeps the player's body outside the
        // helm's own cell while their hands and camera stay at the wheel.
        player.setPos(
                construct.x() + helm.localX() + 0.5 + facing.getStepX() * 0.90,
                construct.y() + helm.localY(),
                construct.z() + helm.localZ() + 0.5 + facing.getStepZ() * 0.90
        );
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance();
    }

    private static void supportPlayer(ServerPlayer player, Collection<PhysicsConstruct> constructs) {
        if (player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0.08) {
            return; // jumping: let the player detach naturally
        }

        for (PhysicsConstruct construct : constructs) {
            double previousTop = construct.supportSurfaceY(player.getX(), player.getZ(), player.getY(), true);
            if (Double.isNaN(previousTop)) {
                continue;
            }
            double feetY = player.getY();
            if (feetY < previousTop - STAND_BELOW_TOLERANCE || feetY > previousTop + STAND_ABOVE_TOLERANCE) {
                continue;
            }

            double carriedX = player.getX() + construct.deltaX();
            double carriedZ = player.getZ() + construct.deltaZ();
            double currentTop = construct.supportSurfaceY(
                    carriedX, carriedZ, player.getY() + construct.deltaY(), false);
            if (Double.isNaN(currentTop)) {
                continue;
            }

            player.setPos(carriedX, currentTop, carriedZ);
            resolveHorizontalCollisions(player, construct);
            player.setOnGround(true);
            player.resetFallDistance();
            if (motion.y < 0.0) {
                player.setDeltaMovement(motion.x, 0.0, motion.z);
            }
            return;
        }
    }

    /**
     * Pushes a player out of construct blocks that sit above the deck.
     *
     * <p>A construct is not a vanilla collision source yet, so a helm or sail standing proud of
     * the deck would otherwise be walked straight through. Only the blocks in the columns around
     * the player are considered, rather than the whole hull.
     */
    private static void resolveHorizontalCollisions(ServerPlayer player, PhysicsConstruct construct) {
        for (StoredBlock block : construct.blocksNear(player.getX(), player.getZ())) {
            double bx0 = construct.x() + block.localX();
            double by0 = construct.y() + block.localY();
            double bz0 = construct.z() + block.localZ();
            AABB blockBox = new AABB(bx0, by0, bz0, bx0 + 1.0, by0 + 1.0, bz0 + 1.0);
            AABB playerBox = player.getBoundingBox();
            if (!playerBox.intersects(blockBox)) {
                continue;
            }
            if (blockBox.maxY <= player.getY() + 0.12) {
                continue; // this is the supporting floor, not an obstacle
            }

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

    // ---------------------------------------------------------------- helpers

    /**
     * Best-effort orientation for construct-local placement. Vanilla's BlockPlaceContext cannot
     * be pointed at construct-local coordinates yet, so the common direction properties are
     * preserved explicitly until a real ShipLevel exists.
     */
    private static BlockState orientPlacedState(BlockState state, ServerPlayer player, Direction hitFace) {
        Direction horizontal = Direction.fromYRot(player.getYRot()).getOpposite();

        if (state.hasProperty(AstraFacingBlock.FACING)) {
            // Edge-mounted parts should point away from the face they were attached to.
            boolean edgeMounted = state.is(AstraBlocks.WING)
                    || state.is(AstraBlocks.PROPELLER)
                    || state.is(AstraBlocks.THRUSTER);
            state = state.setValue(AstraFacingBlock.FACING,
                    edgeMounted && hitFace.getAxis() != Direction.Axis.Y ? hitFace : horizontal);
        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, horizontal);
        }
        if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            Direction hopperFacing = hitFace.getOpposite();
            state = state.setValue(BlockStateProperties.FACING_HOPPER,
                    hopperFacing == Direction.UP ? Direction.DOWN : hopperFacing);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, hitFace);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_AXIS, horizontal.getAxis());
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            state = state.setValue(BlockStateProperties.AXIS, hitFace.getAxis());
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return state;
    }

    public static PhysicsConstruct get(Level level, UUID id) {
        Map<UUID, PhysicsConstruct> constructs = BY_LEVEL.get(level.dimension());
        return constructs == null ? null : constructs.get(id);
    }

    public static Collection<PhysicsConstruct> constructsIn(ServerLevel level) {
        Map<UUID, PhysicsConstruct> constructs = BY_LEVEL.get(level.dimension());
        return constructs == null ? List.of() : List.copyOf(constructs.values());
    }

    /** The construct whose bounding box is nearest the player's eyes, for command targeting. */
    public static PhysicsConstruct nearestTo(ServerLevel level, Vec3 position, double maxDistance) {
        PhysicsConstruct best = null;
        double bestDistanceSq = maxDistance * maxDistance;
        for (PhysicsConstruct construct : constructsIn(level)) {
            double distanceSq = construct.boundingBox().distanceToSqr(position);
            if (distanceSq <= bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = construct;
            }
        }
        return best;
    }

    private static boolean withinInteractionDistance(ServerPlayer player, PhysicsConstruct construct,
                                                     int localX, int localY, int localZ) {
        double reach = AstraConfig.get().interactionReach;
        // A little slack beyond the client's reach absorbs the movement that happens between the
        // client sending the packet and the server handling it.
        double allowed = (reach + 2.0) * (reach + 2.0);
        return player.getEyePosition().distanceToSqr(
                construct.x() + localX + 0.5,
                construct.y() + localY + 0.5,
                construct.z() + localZ + 0.5) <= allowed;
    }

    private static void openLocalContainer(ServerPlayer player, ConstructBlockEntityData data, Component blockName) {
        int rows = data.displayRows();
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        if (data.hasHiddenSlots()) {
            AstraText.sendActionBar(player, AstraText.warning("container.too_large",
                    ConstructBlockEntityData.MAX_DISPLAYABLE_SLOTS, data.logicalSlots()));
        }
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, openingPlayer) ->
                        new ChestMenu(type, syncId, playerInventory, data.inventory(), rows),
                AstraText.plain("container.title", blockName)
        ));
    }

    /**
     * Deletes a construct without returning its blocks. Only ever reached through an explicit
     * operator command; every in-game path uses {@link #disassemble} so nothing is destroyed.
     */
    public static void deleteConstruct(ServerLevel level, UUID id) {
        removeConstruct(level, id);
        saveLevel(level);
    }

    private static void removeConstruct(ServerLevel level, UUID id) {
        Map<UUID, PhysicsConstruct> constructs = BY_LEVEL.get(level.dimension());
        if (constructs != null) {
            constructs.remove(id);
        }

        List<UUID> releasedPilots = new ArrayList<>();
        for (Map.Entry<UUID, PilotSession> entry : PILOTS.entrySet()) {
            if (entry.getValue().constructId.equals(id)) {
                releasedPilots.add(entry.getKey());
            }
        }
        for (UUID playerId : releasedPilots) {
            PILOTS.remove(playerId);
        }

        ConstructRemovePayload payload = new ConstructRemovePayload(id);
        for (ServerPlayer target : level.players()) {
            Set<UUID> tracked = TRACKED_BY_PLAYER.get(target.getUUID());
            if (tracked != null) {
                tracked.remove(id);
            }
            send(target, ConstructRemovePayload.TYPE, payload);
        }
    }
}
