package com.astra.physics.client.ship;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

import com.astra.physics.block.AnimatedComponent;
import com.astra.physics.config.AstraConfig;

/**
 * Draws every construct block, choosing an animation frame per component as it goes.
 *
 * <h2>Animation</h2>
 * Components are animated by swapping the blockstate frame property before the model is drawn.
 * The frame comes from the ship's own drivetrain state, so a propeller only spins when it has
 * both power and throttle, a thruster only burns on forward throttle, and the wheel and ailerons
 * follow the pilot's steering. Nothing is interpolated per vertex and no extra draw calls are
 * added, which keeps it viable on Android and FCL.
 */
public final class ConstructRenderer {
    /** Blocks drawn by a single frame across all constructs, as a safety valve. */
    private static final int MAX_BLOCKS_PER_FRAME = 60_000;
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();

    private ConstructRenderer() {}

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ClientConstructManager.all().isEmpty()) {
            return;
        }

        AstraConfig config = AstraConfig.get();
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        double renderDistanceSq = config.renderDistance * config.renderDistance;

        // Render between the previous and current authoritative 20 TPS snapshots rather than
        // snapping to the newest packet. This is the main smoothness fix for high frame rates.
        float alpha = Math.max(0.0F, Math.min(1.0F,
                minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)));
        double seconds = (minecraft.level.getGameTime() + alpha) / 20.0;

        PoseStack matrices = context.matrices();
        int budget = MAX_BLOCKS_PER_FRAME;

        for (ClientPhysicsConstruct construct : ClientConstructManager.all()) {
            double baseX = construct.renderX(alpha);
            double baseY = construct.renderY(alpha);
            double baseZ = construct.renderZ(alpha);

            if (camera.distanceToSqr(baseX, baseY, baseZ) > renderDistanceSq) {
                continue;
            }

            float steer = steerOf(construct);
            float renderYaw = (float) construct.renderYaw(alpha);

            matrices.pushPose();
            matrices.translate(baseX - camera.x, baseY - camera.y, baseZ - camera.z);
            // Turn about the hull's own centre, matching the pivot the server collides around.
            matrices.translate(construct.pivotX(), 0.0, construct.pivotZ());
            matrices.mulPose(Axis.YP.rotationDegrees(renderYaw));
            matrices.translate(-construct.pivotX(), 0.0, -construct.pivotZ());

            for (ClientPhysicsConstruct.ClientBlock block : construct.visibleBlocks()) {
                if (budget-- <= 0) {
                    break;
                }
                matrices.pushPose();
                matrices.translate(block.localX(), block.localY(), block.localZ());

                BlockState renderState = animate(block.state(), construct, steer, seconds);
                int light = lightAt(minecraft, config,
                        construct.renderToWorldX(block.localX() + 0.5, block.localZ() + 0.5, alpha),
                        baseY + block.localY(),
                        construct.renderToWorldZ(block.localX() + 0.5, block.localZ() + 0.5, alpha));

                minecraft.getBlockRenderer().renderSingleBlock(
                        renderState, matrices, context.consumers(), light, OverlayTexture.NO_OVERLAY);
                matrices.popPose();
            }
            matrices.popPose();
        }
    }

    /**
     * Picks the frame for one component.
     *
     * <p>Cycling components advance on a continuous clock rather than a per-frame counter, so the
     * animation runs at the same speed regardless of the viewer's frame rate.
     */
    private static BlockState animate(BlockState state, ClientPhysicsConstruct construct,
                                      float steer, double seconds) {
        if (!(state.getBlock() instanceof AnimatedComponent component)) {
            return state;
        }
        IntegerProperty property = component.frameProperty();
        if (!state.hasProperty(property)) {
            return state;
        }

        int frames = Math.max(1, component.frameCount());
        int frame;

        switch (component.drive()) {
            case STEER -> {
                // -1..1 maps onto the full frame range, with the middle frame as neutral.
                float normalised = (steer + 1.0F) * 0.5F;
                frame = Math.round(normalised * (frames - 1));
            }
            case CYCLE_FROM_ONE -> {
                double activity = component.activity(construct.enginePower(), construct.throttle());
                if (activity <= 0.001 || frames <= 1) {
                    frame = 0; // the idle pose
                } else {
                    int cycling = frames - 1;
                    frame = 1 + (int) Math.floorMod(
                            (long) Math.floor(seconds * activity * component.speedFactor() * cycling),
                            cycling);
                }
            }
            default -> {
                double activity = component.activity(construct.enginePower(), construct.throttle());
                if (activity <= 0.001) {
                    frame = 0;
                } else {
                    frame = (int) Math.floorMod(
                            (long) Math.floor(seconds * activity * component.speedFactor() * frames),
                            frames);
                }
            }
        }

        return state.setValue(property, Math.max(0, Math.min(frames - 1, frame)));
    }

    /**
     * Steering input for this construct, preferring the local pilot's own smoothed value so the
     * wheel responds to their keys immediately instead of waiting for the next server packet.
     */
    private static float steerOf(ClientPhysicsConstruct construct) {
        if (ClientHelmController.isControlling(construct.id())) {
            return ClientHelmController.visualSteer();
        }
        return construct.steer();
    }

    /**
     * Samples the world's light at the block's position.
     *
     * <p>Constructs used to be drawn full-bright, which made every ship glow like a lantern at
     * night and look pasted on top of the world during the day.
     */
    private static int lightAt(Minecraft minecraft, AstraConfig config,
                               double worldX, double worldY, double worldZ) {
        if (!config.useWorldLighting || minecraft.level == null) {
            return LightTexture.FULL_BRIGHT;
        }
        LIGHT_POS.set((int) Math.floor(worldX), (int) Math.floor(worldY), (int) Math.floor(worldZ));
        int blockLight = minecraft.level.getBrightness(LightLayer.BLOCK, LIGHT_POS);
        int skyLight = minecraft.level.getBrightness(LightLayer.SKY, LIGHT_POS);
        return LightTexture.pack(blockLight, skyLight);
    }
}
