package com.astra.physics.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.astra.physics.ship.PhysicsConstructManager;

public final class AstraNetworking {
    private AstraNetworking() {}

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ConstructSpawnPayload.TYPE, ConstructSpawnPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructTransformPayload.TYPE, ConstructTransformPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructRemovePayload.TYPE, ConstructRemovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionSyncPayload.TYPE, SelectionSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PilotStatePayload.TYPE, PilotStatePayload.CODEC);

        PayloadTypeRegistry.playC2S().register(ConstructPlaceBlockPayload.TYPE, ConstructPlaceBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConstructBreakBlockPayload.TYPE, ConstructBreakBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConstructInteractPayload.TYPE, ConstructInteractPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConstructControlPayload.TYPE, ConstructControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PilotExitPayload.TYPE, PilotExitPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConstructPlaceBlockPayload.TYPE, (payload, context) ->
                PhysicsConstructManager.placeBlock(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConstructBreakBlockPayload.TYPE, (payload, context) ->
                PhysicsConstructManager.breakBlock(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConstructInteractPayload.TYPE, (payload, context) ->
                PhysicsConstructManager.interact(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConstructControlPayload.TYPE, (payload, context) ->
                PhysicsConstructManager.control(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PilotExitPayload.TYPE, (payload, context) ->
                PhysicsConstructManager.releasePilot(context.player(), payload.constructId()));
    }
}
