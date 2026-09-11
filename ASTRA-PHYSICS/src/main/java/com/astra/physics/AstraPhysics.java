package com.astra.physics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;

import com.astra.physics.command.AstraCommands;
import com.astra.physics.config.AstraConfig;
import com.astra.physics.item.WandMode;
import com.astra.physics.network.AstraNetworking;
import com.astra.physics.registry.AstraBlocks;
import com.astra.physics.registry.AstraItems;
import com.astra.physics.selection.ServerSelectionManager;
import com.astra.physics.ship.PhysicsConstructManager;

public final class AstraPhysics implements ModInitializer {
    public static final String MOD_ID = "astra_physics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Absolute ceiling on blocks in one construct, independent of the configurable gameplay
     * limit. Packet decoders use this so a malformed or hostile packet cannot make the receiver
     * allocate an unbounded list; the playable limit lives in {@link AstraConfig}.
     */
    public static final int HARD_BLOCK_LIMIT = 32_768;

    @Override
    public void onInitialize() {
        AstraConfig.load();

        AstraBlocks.initialize();
        AstraItems.initialize();
        AstraNetworking.initialize();
        AstraCommands.register();

        registerWandHandling();
        registerLifecycle();

        LOGGER.info("ASTRA Physics {} initialised (block limit {}, {} constructs per dimension).",
                FabricLoader.getInstance().getModContainer(MOD_ID)
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                AstraConfig.get().maxConstructBlocks,
                AstraConfig.get().maxConstructsPerDimension);
    }

    private void registerWandHandling() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            // An infuser assembles what it is attached to, with no wand and no selection.
            //
            // Only on an empty hand or the wand: this callback runs before block placement, so
            // taking every click would mean a player could never place a block against the
            // infuser they are still building around.
            ItemStack held = player.getItemInHand(hand);
            if (level instanceof ServerLevel serverLevel
                    && (held.isEmpty() || held.getItem() == AstraItems.PHYSICS_WAND)
                    && !player.isSecondaryUseActive()
                    && level.getBlockState(hitResult.getBlockPos()).is(AstraBlocks.PHYSICS_INFUSER)) {
                PhysicsConstructManager.assembleFromInfuser(
                        serverPlayer, serverLevel, hitResult.getBlockPos());
                return InteractionResult.SUCCESS;
            }

            if (held.getItem() != AstraItems.PHYSICS_WAND) {
                return InteractionResult.PASS;
            }

            // Corner picking belongs to assemble mode. In the other modes the wand acts on a
            // construct, and a stray click on terrain should do nothing rather than quietly
            // start a selection the player did not ask for.
            WandMode mode = PhysicsConstructManager.wandMode(serverPlayer);
            if (mode != WandMode.ASSEMBLE && mode != WandMode.ASSEMBLE_AND_GRAB) {
                return InteractionResult.SUCCESS;
            }

            if (player.isSecondaryUseActive()) {
                ServerSelectionManager.clear(serverPlayer);
            } else {
                ServerSelectionManager.handleClick(serverPlayer, level, hitResult.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        });
    }

    /**
     * Constructs are not entities and not world blocks, so nothing loads, saves or cleans them up
     * automatically. Every hook below exists because its absence caused a concrete bug:
     * builds vanishing on restart, a singleplayer client carrying the previous world's constructs
     * into the next one, and pilot and selection state leaking for players who had logged off.
     */
    private void registerLifecycle() {
        ServerWorldEvents.LOAD.register((server, level) -> PhysicsConstructManager.onLevelLoad(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> PhysicsConstructManager.onLevelUnload(level));

        ServerLifecycleEvents.SERVER_STOPPING.register(PhysicsConstructManager::saveAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PhysicsConstructManager.onServerStopped();
            ServerSelectionManager.clearAll();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PhysicsConstructManager.onPlayerDisconnect(handler.getPlayer());
            ServerSelectionManager.onPlayerDisconnect(handler.getPlayer());
        });

        ServerTickEvents.END_WORLD_TICK.register(PhysicsConstructManager::tick);
    }
}
