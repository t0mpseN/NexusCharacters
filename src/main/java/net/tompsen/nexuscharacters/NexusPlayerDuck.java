package net.tompsen.nexuscharacters;

import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.stat.ServerStatHandler;

public interface NexusPlayerDuck {
    void nexus$setAdvancementTracker(PlayerAdvancementTracker tracker);
    void nexus$setStatHandler(ServerStatHandler handler);
}
