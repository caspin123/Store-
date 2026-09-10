package com.astra.physics.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.HitResult;

import com.astra.physics.client.selection.ClientSelectionManager;
import com.astra.physics.client.selection.SelectionRenderer;
import com.astra.physics.client.ship.ClientConstructManager;
import com.astra.physics.client.ship.ClientHelmController;
import com.astra.physics.client.ship.ClientMovingPlatformSupport;
import com.astra.physics.client.ship.ConstructRaycaster;
import com.astra.physics.client.ship.ConstructRenderer;
import com.astra.physics.network.ConstructBreakBlockPayload;
import com.astra.physics.network.ConstructInteractPayload;
import com.astra.physics.network.ConstructPlaceBlockPayload;
import com.astra.physics.network.ConstructRemovePayload;
import com.astra.physics.network.ConstructSpawnPayload;
import com.astra.physics.network.ConstructTransformPayload;
import com.astra.physics.network.SelectionSyncPayload;
import com.astra.physics.network.PilotStatePayload;
import com.astra.physics.registry.AstraBlocks;
import com.astra.physics.registry.AstraItems;

public final class AstraPhysicsClient implements ClientModInitializer {
    private static final double CONSTRUCT_REACH = 6.0;

    @Override
    public void onInitializeClient() {
        // The wand still targets real Minecraft blocks through Fabric's normal block-use callback.
        // Moving ASTRA construct blocks are NOT real Level blocks after assembly, so their use input
        // is intercepted at Minecraft.startUseItem() by MinecraftUseMixin instead. This is required
        // for reliable empty-hand interaction on FCL/Android.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (player.getItemInHand(hand).getItem() == AstraItems.PHYSICS_WAND) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount == 0 || client.level == null) return false;
            ConstructRaycaster.Hit hit = ConstructRaycaster.raycast(player, CONSTRUCT_REACH);
            if (hit == null) return false;

            if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) {
                double vanillaDistanceSq = player.getEyePosition().distanceToSqr(client.hitResult.getLocation());
                if (hit.distanceSq() > vanillaDistanceSq + 1.0E-5) return false;
            }
            if (!ClientPlayNetworking.canSend(ConstructBreakBlockPayload.TYPE)) return false;
            ClientPlayNetworking.send(new ConstructBreakBlockPayload(
                    hit.construct().id(), hit.block().localX(), hit.block().localY(), hit.block().localZ()
            ));
            return true;
        });

        ClientPlayNetworking.registerGlobalReceiver(ConstructSpawnPayload.TYPE, (payload, context) -> {
            ClientConstructManager.spawn(payload);
            ClientSelectionManager.clearSilently();
        });
        ClientPlayNetworking.registerGlobalReceiver(ConstructTransformPayload.TYPE, (payload, context) ->
                ClientConstructManager.transform(payload));
        ClientPlayNetworking.registerGlobalReceiver(ConstructRemovePayload.TYPE, (payload, context) -> {
            ClientConstructManager.remove(payload.constructId());
            ClientHelmController.clearIf(payload.constructId());
        });
        ClientPlayNetworking.registerGlobalReceiver(SelectionSyncPayload.TYPE, (payload, context) ->
                ClientSelectionManager.applyServerSync(payload));
        ClientPlayNetworking.registerGlobalReceiver(PilotStatePayload.TYPE, (payload, context) ->
                ClientHelmController.applyServerState(payload.constructId(), payload.active(), payload.helmX(), payload.helmY(), payload.helmZ()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientMovingPlatformSupport.tick(client);
            ClientHelmController.tick(client);
        });
        WorldRenderEvents.END_MAIN.register(SelectionRenderer::render);
        WorldRenderEvents.END_MAIN.register(ConstructRenderer::render);
    }

    /**
     * Called directly from Minecraft.startUseItem() before vanilla processes right-click/use.
     *
     * ASTRA construct blocks are removed from the real Level during assembly, so vanilla has no
     * BlockHitResult for a helm/chest/engine that is only present in construct-local space. In
     * particular an empty hand can completely bypass UseItemCallback. Intercepting the actual use
     * action makes virtual-block interaction reliable on desktop and FCL touch controls.
     */
    public static boolean tryHandleConstructUse(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.screen != null) {
            return false;
        }

        var player = client.player;
        // The assembler wand intentionally keeps vanilla/Fabric block targeting semantics.
        if (player.getMainHandItem().getItem() == AstraItems.PHYSICS_WAND
                || player.getOffhandItem().getItem() == AstraItems.PHYSICS_WAND) {
            return false;
        }

        ConstructRaycaster.Hit hit = ConstructRaycaster.raycast(player, CONSTRUCT_REACH);
        if (hit == null) return false;

        // Do not steal a click from a closer real Minecraft block/entity.
        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) {
            double vanillaDistanceSq = player.getEyePosition().distanceToSqr(client.hitResult.getLocation());
            if (!isBeforeVanillaTarget(player, hit, vanillaDistanceSq)) return false;
        }

        InteractionHand hand = chooseUseHand(player, hit);
        boolean handled;
        // Engine always owns the click, including Sneak+Use, because sneak is how its
        // MARINE/AIRCRAFT profile is changed. Other interactive blocks keep the old
        // sneak-to-place-nearby behavior.
        if (hit.block().state().is(AstraBlocks.ENGINE)) {
            handled = sendInteraction(hit, hand);
        } else if (!player.isShiftKeyDown() && prefersInteraction(hit)) {
            handled = sendInteraction(hit, hand);
        } else if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
            handled = sendPlacement(hit, hand);
        } else {
            handled = sendInteraction(hit, hand);
        }

        if (handled) player.swing(hand);
        return handled;
    }

    private static InteractionHand chooseUseHand(net.minecraft.world.entity.player.Player player,
                                                  ConstructRaycaster.Hit hit) {
        // Helm/engine/container interaction works with an empty main hand.
        if (!player.isShiftKeyDown() && prefersInteraction(hit)) return InteractionHand.MAIN_HAND;
        if (player.getMainHandItem().getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        return InteractionHand.MAIN_HAND;
    }

    private static boolean isBeforeVanillaTarget(net.minecraft.world.entity.player.Player player,
                                                 ConstructRaycaster.Hit hit,
                                                 double vanillaDistanceSq) {
        return hit.distanceSq() <= vanillaDistanceSq + 1.0E-5;
    }

    private static boolean prefersInteraction(ConstructRaycaster.Hit hit) {
        return hit.block().state().is(AstraBlocks.HELM)
                || hit.block().state().is(AstraBlocks.ENGINE)
                || hit.block().state().hasBlockEntity();
    }

    private static boolean sendPlacement(ConstructRaycaster.Hit hit, net.minecraft.world.InteractionHand hand) {
        if (!ClientPlayNetworking.canSend(ConstructPlaceBlockPayload.TYPE)) return false;
        ClientPlayNetworking.send(new ConstructPlaceBlockPayload(
                hit.construct().id(), hit.block().localX(), hit.block().localY(), hit.block().localZ(), hit.face(), hand
        ));
        return true;
    }

    private static boolean sendInteraction(ConstructRaycaster.Hit hit, net.minecraft.world.InteractionHand hand) {
        if (!ClientPlayNetworking.canSend(ConstructInteractPayload.TYPE)) return false;
        ClientPlayNetworking.send(new ConstructInteractPayload(
                hit.construct().id(), hit.block().localX(), hit.block().localY(), hit.block().localZ(), hand
        ));
        return true;
    }
}
