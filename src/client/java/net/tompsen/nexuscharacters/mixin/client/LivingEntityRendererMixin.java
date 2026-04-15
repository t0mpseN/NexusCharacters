package net.tompsen.nexuscharacters.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.tompsen.nexuscharacters.NexusDummyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = net.minecraft.client.render.entity.LivingEntityRenderer.class, priority = 500)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void guardNullCamera(LivingEntity entity,
                                 CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Cancel label rendering if there is no active camera (e.g. rendering a dummy
        // entity on the main menu). Without this, mods that access dispatcher.camera
        // in their own hasLabel injections will crash with a NullPointerException.
        if (client.player == null
                || client.getEntityRenderDispatcher().camera == null) {
            cir.setReturnValue(false);
        }
    }
}
