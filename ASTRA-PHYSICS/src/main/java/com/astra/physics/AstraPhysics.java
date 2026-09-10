package com.astra.physics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import com.astra.physics.network.AstraNetworking;
import com.astra.physics.registry.AstraBlocks;
import com.astra.physics.registry.AstraItems;
import com.astra.physics.selection.ServerSelectionManager;
import com.astra.physics.ship.PhysicsConstructManager;

public final class AstraPhysics implements ModInitializer {
    public static final String MOD_ID = "astra_physics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int MAX_SELECTED_BLOCKS = 4096;

    @Override
    public void onInitialize() {
        AstraBlocks.initialize();
        AstraItems.initialize();
        AstraNetworking.initialize();

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (player.getItemInHand(hand).getItem() != AstraItems.PHYSICS_WAND) {
                return InteractionResult.PASS;
            }

            if (player.isSecondaryUseActive()) {
                ServerSelectionManager.clear(serverPlayer);
            } else {
                ServerSelectionManager.handleClick(serverPlayer, level, hitResult.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_WORLD_TICK.register(PhysicsConstructManager::tick);
        LOGGER.info("ASTRA Physics {} initialized. Server-authoritative selection limit: {} blocks.",
                "0.0.10-alpha-smooth-engine", MAX_SELECTED_BLOCKS);
    }
}
