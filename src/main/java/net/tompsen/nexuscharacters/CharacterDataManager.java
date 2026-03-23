package net.tompsen.nexuscharacters;

import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.tompsen.nexuscharacters.mixin.PlayerManagerAccessor;
import net.tompsen.nexuscharacters.NexusPlayerDuck;

import java.util.*;

public class CharacterDataManager {
    public static void loadCharacterToPlayer(ServerPlayerEntity player) {
        CharacterDto character = NexusCharacters.getSelectedCharacter(player);
        if (character == null) {
            NexusCharacters.LOGGER.warn("[Nexus-Loader] No character selected for player: {}", player.getName().getString());
            return;
        }

        String worldId = getWorldId(player);
        NbtCompound playerNbt = character.playerNbt().copy();
        NbtCompound worldPositions = character.worldPositions();
        List<net.minecraft.item.ItemStack> toDrop = new ArrayList<>();

        NexusCharacters.LOGGER.info("[Nexus-Loader] Loading character {} for player {} in world {}", 
                character.name(), player.getName().getString(), worldId);

        boolean inherited = false;
        // 1. Handle Inheritance & Stacking for First-Time Join
        if (!worldPositions.contains(worldId)) {
            NexusCharacters.LOGGER.info("[Nexus-Loader] First-time join for character {} in this world.", character.name());
            if (character.name().equalsIgnoreCase(player.getGameProfile().getName())) {
                NexusCharacters.LOGGER.info("[Nexus-Loader] Name matches account username. Attempting inheritance...");
                NbtCompound worldData = ModDataScanner.loadPlayerNbtFromWorld(player);
                if (!worldData.isEmpty()) {
                    // SERVER IS BASE, CHARACTER IS ADDITIONAL (for item overflow dropping)
                    playerNbt = mergePlayerNbt(player, worldData, playerNbt, toDrop);
                    inherited = true;
                    NexusCharacters.LOGGER.info("[Nexus-Loader] Character {} inherited existing server progress.", character.name());
                }

                // Merge mod files as well
                NbtCompound serverModData = ModDataScanner.scanPlayerModData(player, true);
                NbtCompound mergedModData = ModDataScanner.mergeModData(serverModData, character.modData());
                ModDataScanner.restorePlayerModData(player, mergedModData);
            } else {
                NexusCharacters.LOGGER.info("[Nexus-Loader] Name MISMATCH ({} != {}). Enforcing fresh start.", 
                        character.name(), player.getGameProfile().getName());
                ModDataScanner.clearPlayerModData(player);
                ModDataScanner.restorePlayerModData(player, character.modData());
            }
        } else {
            NexusCharacters.LOGGER.info("[Nexus-Loader] Character has been here before. Restoring world files from DTO.");
            ModDataScanner.clearPlayerModData(player);
            ModDataScanner.restorePlayerModData(player, character.modData());
        }

        NexusCharacters.LOGGER.info("[Nexus-Loader] Refreshing trackers (advancements/stats)...");
        refreshTrackers(player);

        player.getInventory().clear();

        if (!playerNbt.isEmpty()) {
            playerNbt.remove("Pos");
            playerNbt.remove("Rotation");
            playerNbt.remove("Dimension");
            playerNbt.remove("SpawnDimension");
            playerNbt.remove("RootVehicle");

            int gmId = playerNbt.contains("playerGameType") ? playerNbt.getInt("playerGameType") : 0;

            UUID uuid = player.getUuid();
            player.clearStatusEffects();
            player.readNbt(playerNbt);
            player.setUuid(uuid);
            player.changeGameMode(GameMode.byId(gmId));

            if (!inherited && !worldPositions.contains(worldId)) {
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
            }
            NexusCharacters.LOGGER.info("[Nexus-Loader] Player NBT applied successfully.");
        } else {
            NexusCharacters.LOGGER.info("[Nexus-Loader] Starting with clean entity slate (Survival).");
            player.getInventory().clear();
            player.clearStatusEffects();
            player.experienceLevel = 0;
            player.experienceProgress = 0f;
            player.totalExperience = 0;
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(5.0f);
        }

        if (worldPositions.contains(worldId)) {
            NbtCompound pos = worldPositions.getCompound(worldId);
            player.teleport(player.getServerWorld(),
                    pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"),
                    Set.of(), pos.getFloat("yaw"), pos.getFloat("pitch"));
        } else {
            BlockPos spawn = player.getServerWorld().getSpawnPos();
            player.teleport(player.getServerWorld(),
                    spawn.getX(), spawn.getY(), spawn.getZ(),
                    Set.of(), 0f, 0f);
        }

        // Drop overflow items after teleport
        for (net.minecraft.item.ItemStack stack : toDrop) {
            player.dropItem(stack, false);
        }

        String skinValue = character.skinValue();
        String skinSig   = character.skinSignature();

        if (skinValue != null && !skinValue.isEmpty()) {
            player.getGameProfile().getProperties().removeAll("textures");
            player.getGameProfile().getProperties().put("textures",
                    new Property("textures", skinValue, skinSig));
            applySkingToAllClients(player);
            if (ServerPlayNetworking.canSend(player.networkHandler, SkinReloadPayload.ID)) {
                ServerPlayNetworking.send(player,
                        new SkinReloadPayload(skinValue, skinSig != null ? skinSig : ""));
            }
        }

        player.sendAbilitiesUpdate();
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        NexusCharacters.LOGGER.info("[Nexus-Loader] Load complete for {}.", character.name());
    }

    private static void refreshTrackers(ServerPlayerEntity player) {
        PlayerManager manager = player.server.getPlayerManager();
        PlayerManagerAccessor managerAccessor = (PlayerManagerAccessor) manager;
        NexusPlayerDuck playerDuck = (NexusPlayerDuck) player;
        UUID uuid = player.getUuid();

        managerAccessor.getAdvancementTrackers().remove(uuid);
        managerAccessor.getStatHandlers().remove(uuid);

        // Standard Minecraft: force lazy-recreation of trackers in ServerPlayerEntity fields
        net.minecraft.advancement.PlayerAdvancementTracker newAdvancements = manager.getAdvancementTracker(player);
        net.minecraft.stat.ServerStatHandler newStats = manager.createStatHandler(player);

        playerDuck.nexus$setAdvancementTracker(newAdvancements);
        playerDuck.nexus$setStatHandler(newStats);

        if (newAdvancements != null) {
            newAdvancements.sendUpdate(player);
        }
    }

    private static NbtCompound mergePlayerNbt(ServerPlayerEntity player, NbtCompound base, NbtCompound additional, List<net.minecraft.item.ItemStack> toDrop) {
        if (base.isEmpty()) return additional;
        NbtCompound merged = base.copy();

        net.minecraft.nbt.NbtList baseInv = merged.getList("Inventory", 10);
        net.minecraft.nbt.NbtList addInv = additional.getList("Inventory", 10);
        Map<Integer, NbtCompound> slots = new HashMap<>();
        for (int i = 0; i < baseInv.size(); i++) {
            NbtCompound item = baseInv.getCompound(i);
            slots.put((int) item.getByte("Slot") & 255, item);
        }
        for (int i = 0; i < addInv.size(); i++) {
            NbtCompound item = addInv.getCompound(i).copy();
            int slot = (int) item.getByte("Slot") & 255;
            if (slots.containsKey(slot)) {
                int newSlot = -1;
                for (int s = 0; s < 36; s++) {
                    if (!slots.containsKey(s)) { newSlot = s; break; }
                }
                if (newSlot != -1) {
                    item.putByte("Slot", (byte) newSlot);
                    slots.put(newSlot, item);
                } else {
                    toDrop.add(net.minecraft.item.ItemStack.fromNbtOrEmpty(player.getRegistryManager(), item));
                }
            } else { slots.put(slot, item); }
        }
        net.minecraft.nbt.NbtList finalInv = new net.minecraft.nbt.NbtList();
        slots.values().forEach(finalInv::add);
        merged.put("Inventory", finalInv);

        int baseTotal = merged.getInt("XpTotal");
        int addTotal = additional.getInt("XpTotal");
        merged.putInt("XpTotal", baseTotal + addTotal);
        merged.remove("XpLevel");
        merged.remove("XpP");

        if (additional.contains("active_effects", 9)) {
            net.minecraft.nbt.NbtList baseEffects = merged.getList("active_effects", 10);
            net.minecraft.nbt.NbtList addEffects = additional.getList("active_effects", 10);
            Set<String> existingEffects = new HashSet<>();
            for (int i = 0; i < baseEffects.size(); i++) existingEffects.add(baseEffects.getCompound(i).getString("id"));
            for (int i = 0; i < addEffects.size(); i++) {
                NbtCompound effect = addEffects.getCompound(i);
                if (!existingEffects.contains(effect.getString("id"))) baseEffects.add(effect.copy());
            }
            merged.put("active_effects", baseEffects);
        }
        return merged;
    }

    public static void saveCurrentCharacter(ServerPlayerEntity player) {
        saveCurrentCharacter(player, true);
    }

    public static void saveCurrentCharacter(ServerPlayerEntity player, boolean forceFull) {
        try {
            CharacterDto current = NexusCharacters.getSelectedCharacter(player);
            if (current == null) return;

            if (player.getAdvancementTracker() != null) {
                player.getAdvancementTracker().save();
            }
            if (player.getStatHandler() != null) {
                player.getStatHandler().save();
            }
            player.getInventory().markDirty();
            
            NbtCompound playerNbt = new NbtCompound();
            player.writeNbt(playerNbt);
            if (current.playerNbt().getBoolean("hardcore")) playerNbt.putBoolean("hardcore", true);

            String worldId = getWorldId(player);
            NbtCompound worldPositions = current.worldPositions().copy();
            NbtCompound pos = new NbtCompound();
            pos.putDouble("x", player.getX());
            pos.putDouble("y", player.getY());
            pos.putDouble("z", player.getZ());
            pos.putFloat("yaw", player.getYaw());
            pos.putFloat("pitch", player.getPitch());
            worldPositions.put(worldId, pos);

            String[] skin = getSkinProperties(player);
            NbtCompound modData = current.modData().copy();

            NbtCompound currentWorldData = ModDataScanner.scanPlayerModData(player, forceFull);
            for (String key : currentWorldData.getKeys()) {
                modData.put(key, currentWorldData.get(key));
                modData.remove(worldId + "::" + key);
            }

            NbtCompound displayCache = modData.getCompound("_nexuscharacters:adv_display_cache");
            updateAdvancementDisplayCache(player, displayCache);
            modData.put("_nexuscharacters:adv_display_cache", displayCache);

            CharacterDto updated = new CharacterDto(
                    current.id(), current.name(), playerNbt, worldPositions,
                    skin[0], skin[1], current.skinUsername(), modData
            );

            NexusCharacters.setSelectedCharacter(player, updated);
            NexusCharacters.DATA_FILE_MANAGER.updateCharacter(updated);

            if (player.server.isDedicated() || !player.server.isHost(player.getGameProfile())) {
                if (ServerPlayNetworking.canSend(player.networkHandler, SaveCharacterPayload.ID)) {
                    ServerPlayNetworking.send(player, new SaveCharacterPayload(updated.toNbt()));
                }
            }

            if (!player.server.isDedicated()) NexusCharacters.selectedCharacter = updated;
        } catch (Exception e) {
            NexusCharacters.LOGGER.error("[Nexus-Saver] Failed to save character for " + player.getName().getString(), e);
        }
    }

    private static void updateAdvancementDisplayCache(ServerPlayerEntity player, NbtCompound cache) {
        net.minecraft.server.ServerAdvancementLoader loader = player.server.getAdvancementLoader();
        net.minecraft.advancement.PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        for (net.minecraft.advancement.AdvancementEntry entry : loader.getAdvancements()) {
            if (tracker.getProgress(entry).isDone()) {
                entry.value().display().ifPresent(display -> {
                    NbtCompound info = new NbtCompound();
                    info.putString("title", display.getTitle().getString());
                    info.putString("desc", display.getDescription().getString());
                    info.put("icon", display.getIcon().encode(player.getRegistryManager()));
                    cache.put(entry.id().toString(), info);
                });
            }
        }
    }

    public static String getWorldId(ServerPlayerEntity player) {
        String saveName = player.server.getSaveProperties().getLevelName();
        String serverId = player.server.isDedicated() ? player.server.getServerIp() + ":" + player.server.getServerPort() : "integrated";
        long seed = player.getServerWorld().getSeed();
        return serverId + "|" + saveName + "|" + seed;
    }

    public static String[] getSkinProperties(ServerPlayerEntity player) {
        Collection<Property> properties = player.getGameProfile().getProperties().get("textures");
        if (properties.isEmpty()) return new String[]{"", ""};
        Property texture = properties.iterator().next();
        return new String[]{ texture.value(), texture.signature() };
    }

    public static void applySkingToAllClients(ServerPlayerEntity player) {
        MinecraftServer server = player.server;
        PlayerManager playerManager = server.getPlayerManager();
        playerManager.sendToAll(new PlayerRemoveS2CPacket(List.of(player.getUuid())));
        PlayerListS2CPacket addPacket = new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER), List.of(player));
        playerManager.sendToAll(addPacket);
        for (ServerPlayerEntity other : playerManager.getPlayerList()) {
            if (other == player) continue;
            other.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(player.getUuid())));
            other.networkHandler.sendPacket(new EntitySpawnS2CPacket(player, 0, player.getBlockPos()));
        }
        player.networkHandler.sendPacket(addPacket);
    }
}
