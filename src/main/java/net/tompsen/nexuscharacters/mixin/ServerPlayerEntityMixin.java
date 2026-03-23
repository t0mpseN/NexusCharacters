package net.tompsen.nexuscharacters.mixin;

import net.tompsen.nexuscharacters.NexusPlayerDuck;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements NexusPlayerDuck {
    @Shadow @Final @Mutable
    private PlayerAdvancementTracker advancementTracker;

    @Shadow @Final @Mutable
    private ServerStatHandler statHandler;

    @Override
    public void nexus$setAdvancementTracker(PlayerAdvancementTracker tracker) {
        this.advancementTracker = tracker;
    }

    @Override
    public void nexus$setStatHandler(ServerStatHandler handler) {
        this.statHandler = handler;
    }
}
