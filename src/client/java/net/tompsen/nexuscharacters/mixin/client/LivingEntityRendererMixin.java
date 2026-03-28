package net.tompsen.nexuscharacters.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.tompsen.nexuscharacters.NexusDummyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.render.entity.LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void guardNullPlayer(LivingEntity entity,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftClient.getInstance().player == null) {
            cir.setReturnValue(false);
        }
    }
}
