package net.tompsen.charsel.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.tompsen.charsel.CharacterSelection;
import net.tompsen.charsel.CharacterSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    private boolean characterPicked = false;

    @Inject(method = "createLevel", at = @At("HEAD"), cancellable = true)
    private void onCreateLevel(CallbackInfo ci) {
        if (CharacterSelection.selectedCharacter != null) return; // already picked

        MinecraftClient client = MinecraftClient.getInstance();
        CreateWorldScreen self = (CreateWorldScreen)(Object)this;

        client.setScreen(new CharacterSelectionScreen(self, () ->
                ((CreateWorldScreenAccessor) self).invokeCreateLevel()
        ));
        ci.cancel();
    }
}