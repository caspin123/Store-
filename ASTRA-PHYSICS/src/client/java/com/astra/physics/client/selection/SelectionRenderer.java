package com.astra.physics.client.selection;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import com.astra.physics.registry.AstraItems;

public final class SelectionRenderer {
    private static final int CYAN = 0xFF00E5FF;
    private static final int GREEN = 0xFF57FF6A;
    private static final float LINE_WIDTH = 2.0f;

    private SelectionRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientSelectionManager.hasSelection()) {
            return;
        }
        if (minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() != AstraItems.PHYSICS_WAND
                && minecraft.player.getItemInHand(InteractionHand.OFF_HAND).getItem() != AstraItems.PHYSICS_WAND) {
            return;
        }

        BlockPos first = ClientSelectionManager.first();
        BlockPos second = ClientSelectionManager.second();
        if (first == null) {
            return;
        }

        Vec3 camera = context.gameRenderer().getMainCamera().position();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES_TRANSLUCENT);

        if (second == null) {
            drawBlock(context, lines, first, camera, GREEN);
            return;
        }

        // Render only the cached non-air blocks (max 4096), rather than rescanning
        // the entire cuboid every frame. This matters on Android/FCL.
        for (BlockPos pos : ClientSelectionManager.selectedBlocks()) {
            drawBlock(context, lines, pos, camera, CYAN);
        }

        // Make Point 1 visually distinct.
        drawBlock(context, lines, first, camera, GREEN);
    }

    private static void drawBlock(WorldRenderContext context, VertexConsumer lines, BlockPos pos, Vec3 camera, int color) {
        ShapeRenderer.renderShape(
                context.matrices(),
                lines,
                Shapes.block(),
                pos.getX() - camera.x,
                pos.getY() - camera.y,
                pos.getZ() - camera.z,
                color,
                LINE_WIDTH
        );
    }
}
