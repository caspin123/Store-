package com.astra.physics.client.ship;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import com.astra.physics.block.HelmBlock;
import com.astra.physics.registry.AstraBlocks;

/** Simple per-block renderer for the 0.0.1 prototype. Mesh caching comes later. */
public final class ConstructRenderer {
    private ConstructRenderer() {}

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ClientConstructManager.all().isEmpty()) {
            return;
        }

        Vec3 camera = context.gameRenderer().getMainCamera().position();
        // Render between the previous and current authoritative 20 TPS snapshots instead of
        // snapping to the newest packet. This is the main visual smoothness fix for FCL/high-FPS.
        float alpha = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        PoseStack matrices = context.matrices();

        for (ClientPhysicsConstruct construct : ClientConstructManager.all()) {
            double baseX = construct.renderX(alpha) - camera.x;
            double baseY = construct.renderY(alpha) - camera.y;
            double baseZ = construct.renderZ(alpha) - camera.z;

            matrices.pushPose();
            matrices.translate(baseX, baseY, baseZ);
            for (ClientPhysicsConstruct.ClientBlock block : construct.blocks()) {
                matrices.pushPose();
                matrices.translate(block.localX(), block.localY(), block.localZ());
                BlockState renderState = block.state();
                if (renderState.is(AstraBlocks.HELM) && renderState.hasProperty(HelmBlock.STEER)) {
                    renderState = renderState.setValue(
                            HelmBlock.STEER,
                            ClientHelmController.visualSteerFor(construct.id())
                    );
                }
                minecraft.getBlockRenderer().renderSingleBlock(
                        renderState, matrices, context.consumers(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                );
                matrices.popPose();
            }
            matrices.popPose();
        }
    }
}
