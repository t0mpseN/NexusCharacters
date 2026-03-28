package net.tompsen.nexuscharacters.mixin;

import net.tompsen.nexuscharacters.CharacterDto;
import net.tompsen.nexuscharacters.NexusCharacters;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * Override getName() for server players that have a character selected.
     * This affects chat messages, tab list, and any other system that calls
     * player.getName() or player.getDisplayName().
     */
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void nexus$overrideName(CallbackInfoReturnable<Text> cir) {
        if (!((Object) this instanceof ServerPlayerEntity self)) return;
        CharacterDto character = NexusCharacters.getSelectedCharacter(self);
        if (character != null) {
            cir.setReturnValue(Text.literal(character.name()));
        }
    }
}
