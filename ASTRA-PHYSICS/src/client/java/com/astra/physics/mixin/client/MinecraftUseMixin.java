package com.astra.physics.mixin.client;

import com.astra.physics.client.AstraPhysicsClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minimal client-only input bridge for moving construct blocks.
 *
 * A construct helm is not a real Level block after assembly, so vanilla block-use callbacks can
 * miss it completely (especially with an empty hand on FCL). We intercept the actual Minecraft
 * use action and only cancel vanilla handling when ASTRA's own raycaster hit a closer construct.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void astra$useMovingConstruct(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (AstraPhysicsClient.tryHandleConstructUse(client)) {
            ci.cancel();
        }
    }
}
