package net.tompsen.nexuscharacters.mixin;

import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.tompsen.nexuscharacters.CharacterDataManager;
import net.tompsen.nexuscharacters.NexusCharacters;
import net.tompsen.nexuscharacters.VaultManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    /**
     * Fires at the very start of PlayerManager.onPlayerConnect, before loadPlayerData.
     * For singleplayer/LAN host, the character is already chosen (selectedCharacter is set
     * from the character picker before world load). We stage vault files here so that
     * loadPlayerData reads the correct playerdata/<uuid>.dat and all mods (including those
     * that hook readNbt) restore data properly during entity initialization — not as an
     * after-the-fact readNbt call on a live entity.
     */
    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void beforeLoadPlayerData(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        boolean isDedicated = player.server.isDedicated();
        boolean isHost = player.server.isHost(player.getGameProfile());
        boolean isLanGuest = !isDedicated && !isHost;

        // Only handle singleplayer/LAN host here.
        // Dedicated: login-phase already staged the vault.
        // LAN guest: no character selected yet (play-phase picker not shown yet).
        if (isDedicated || isLanGuest) return;

        // Character must already be selected from the title-screen picker.
        if (NexusCharacters.selectedCharacter == null) return;

        // Don't re-stage if prepareCharacterData already ran (e.g. a Nexus-triggered respawn).
        if (NexusCharacters.getSelectedCharacter(player) != null) return;

        NexusCharacters.setSelectedCharacter(player, NexusCharacters.selectedCharacter);
        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        UUID uuid = player.getUuid();

        VaultManager.importLegacyDataIfNeeded(NexusCharacters.selectedCharacter.id(),
                NexusCharacters.selectedCharacter.name(), worldDir);
        VaultManager.clearWorldFiles(worldDir, uuid);
        VaultManager.copyVaultToWorld(NexusCharacters.selectedCharacter.id(), worldDir, uuid);
        CharacterDataManager.evictPersistentStateCache(player.server);

        NexusCharacters.LOGGER.info("[Nexus] beforeLoadPlayerData: vault staged for {} BEFORE loadPlayerData.",
                player.getName().getString());
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void afterRemove(ServerPlayerEntity player, CallbackInfo ci) {
        UUID uuid = player.getUuid();

        // Skip vault save if this removal is part of a NexusCharacters-triggered respawn.
        // The respawn is used to reload mod data from disk — not a real disconnect.
        if (NexusCharacters.respawningPlayers.contains(uuid)) {
            NexusCharacters.LOGGER.info("[Nexus] afterRemove: skipping vault save for {} — nexus-triggered respawn.", player.getName().getString());
            return;
        }

        NexusCharacters.LOGGER.info("[Nexus] afterRemove: saving character data for {} (uuid={})", player.getName().getString(), uuid);
        // Runs AFTER PlayerManager.remove() which flushes playerdata/advancements/stats to disk.
        // LAN guests use the DISCONNECT event handler for their vault save — skip here.
        boolean isLanGuest = !player.server.isDedicated() && !player.server.isHost(player.getGameProfile());
        if (!isLanGuest) CharacterDataManager.saveCurrentCharacter(player);

        NexusCharacters.clearSelectedCharacter(player);
        NexusCharacters.playerJoinTick.remove(uuid);
        NexusCharacters.inventoryGuardTick.remove(uuid);
        NexusCharacters.inventoryGuardSnapshot.remove(uuid);
        // Only clear the static selectedCharacter for singleplayer/LAN host (not LAN guests)
        if (!player.server.isDedicated() && !isLanGuest) {
            NexusCharacters.selectedCharacter = null;
        }
    }

    @Inject(method = "createStatHandler", at = @At("HEAD"))
    private void beforeCreateStatHandler(PlayerEntity player, CallbackInfoReturnable<net.minecraft.stat.ServerStatHandler> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            prepareCharacterData(serverPlayer);
        }
    }

    @Inject(method = "getAdvancementTracker", at = @At("HEAD"))
    private void beforeGetAdvancementTracker(ServerPlayerEntity player, CallbackInfoReturnable<PlayerAdvancementTracker> cir) {
        prepareCharacterData(player);
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void onRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity newPlayer = cir.getReturnValue();
        if (NexusCharacters.deadHardcorePlayers.remove(newPlayer.getUuid())) {
            // Hardcore character died - it's already removed from the list in AFTER_DEATH.
            // Switch the NEW player instance to spectator and notify.
            newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            newPlayer.sendMessage(net.minecraft.text.Text.literal("§cYour character has perished and was removed from the list. §7You are now spectating."));
        }
    }

    private void prepareCharacterData(ServerPlayerEntity player) {
        Map<UUID, PlayerAdvancementTracker> trackers = ((PlayerManagerAccessor)(Object)this).getAdvancementTrackers();
        if (trackers.containsKey(player.getUuid())) {
            NexusCharacters.LOGGER.debug("[Nexus] prepareCharacterData: tracker already exists for {} — skipping.", player.getName().getString());
            return;
        }

        // If already selected (e.g. during a NexusCharacters-triggered respawn on dedicated),
        // the vault is already in the world dir — nothing to do here.
        if (NexusCharacters.getSelectedCharacter(player) != null) {
            NexusCharacters.LOGGER.debug("[Nexus] prepareCharacterData: character already selected for {} — skipping.", player.getName().getString());
            return;
        }

        boolean isDedicated = player.server.isDedicated();
        boolean isHost = player.server.isHost(player.getGameProfile());
        boolean isLanGuest = !isDedicated && !isHost;

        if (isLanGuest) {
            // LAN guests select character via play-phase packet — skip here.
            NexusCharacters.LOGGER.debug("[Nexus] prepareCharacterData: LAN-guest join for {} — character selection pending.", player.getName().getString());
            return;
        }

        if (isDedicated) {
            // On dedicated servers, the login-phase query stages vault files before this point.
            // pendingCharacters holds the selection; if present, the vault is already on disk.
            // Nothing to do here — the JOIN handler will call applyCharacterData after login.
            // If pendingCharacters is empty (first-time join / login query not answered),
            // the play-phase fallback handles it.
            NexusCharacters.LOGGER.debug("[Nexus] prepareCharacterData: dedicated join for {} — handled by login-phase.", player.getName().getString());
            return;
        }

        // Singleplayer / LAN host: vault staging should have happened in beforeLoadPlayerData
        // (which fires before loadPlayerData). If selectedCharacter was set at that point,
        // the vault is already staged and getSelectedCharacter(player) is already set — we
        // already returned early above. Reaching here means selectedCharacter was null at
        // beforeLoadPlayerData time but is now available (edge case), so try last-used.
        UUID charId = NexusCharacters.DATA_FILE_MANAGER.getLastUsed(player.getUuid());

        NexusCharacters.LOGGER.info("[Nexus] prepareCharacterData: player={} singleplayer/host charId={} (fallback)",
                player.getName().getString(), charId);

        if (charId == null) return;

        NexusCharacters.DATA_FILE_MANAGER.findById(charId).ifPresent(character -> {
            NexusCharacters.setSelectedCharacter(player, character);
            Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            // Import pre-existing player data for a matching username if this is a fresh vault
            VaultManager.importLegacyDataIfNeeded(character.id(), character.name(), worldDir);
            // Fallback: loadPlayerData already ran, so vault staging here is post-hoc.
            // copyVaultToWorld still updates the file on disk so applyCharacterData can read it.
            VaultManager.clearWorldFiles(worldDir, player.getUuid());
            VaultManager.copyVaultToWorld(character.id(), worldDir, player.getUuid());
            CharacterDataManager.evictPersistentStateCache(player.server);
            NexusCharacters.LOGGER.info("[Nexus] Vault installed for singleplayer/host join (fallback): {}", character.name());
        });
    }
}