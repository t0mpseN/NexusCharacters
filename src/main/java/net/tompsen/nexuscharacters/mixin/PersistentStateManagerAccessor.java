package net.tompsen.nexuscharacters.mixin;

import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PersistentStateManager.class)
public interface PersistentStateManagerAccessor {
    @Accessor("loadedStates")
    Map<String, PersistentState> getLoadedStates();
}
