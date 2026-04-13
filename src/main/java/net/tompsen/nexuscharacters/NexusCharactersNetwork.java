package net.tompsen.nexuscharacters;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.UUID;

public class NexusCharactersNetwork {

    // ── Respawn helper (play phase, after character switch mid-session) ────────

    /**
     * Respawns the player in-place so Minecraft (and all mods) re-read player data
     * from the world dir files we just placed there.
     */
    static void respawnPlayer(ServerPlayerEntity player) {
        CharacterDto character = NexusCharacters.getSelectedCharacter(player);
        if (character == null) {
            NexusCharacters.LOGGER.warn("[Nexus] respawnPlayer: no character selected for {} — falling back to applyCharacterData", player.getName().getString());
            CharacterDataManager.applyCharacterData(player);
            return;
        }

        NexusCharacters.LOGGER.info("[Nexus] Respawning player {} (uuid={}) to reload mod data for char {} ({})",
                player.getName().getString(), player.getUuid(), character.name(), character.id());

        NexusCharacters.respawningPlayers.add(player.getUuid());
        ServerPlayerEntity newPlayer;
        try {
            newPlayer = player.server.getPlayerManager().respawnPlayer(player, true);
        } finally {
            NexusCharacters.respawningPlayers.remove(player.getUuid());
        }
        NexusCharacters.LOGGER.info("[Nexus] Respawn call complete, new player uuid={}", newPlayer.getUuid());

        // Re-apply character to the NEW player object (respawn creates a new instance)
        NexusCharacters.setSelectedCharacter(newPlayer, character);

        // Respawn resets game mode to default — enforce from DTO
        newPlayer.changeGameMode(net.minecraft.world.GameMode.byId(character.gameMode()));

        // Restore position from vault
        String worldId = CharacterDataManager.getWorldId(newPlayer);
        java.util.Optional<VaultManager.WorldPos> pos = VaultManager.getWorldPosition(character.id(), worldId);
        if (pos.isPresent()) {
            NexusCharacters.LOGGER.info("[Nexus] Teleporting {} to saved pos [{}, {}, {}] in world {}",
                    character.name(), pos.get().x(), pos.get().y(), pos.get().z(), worldId);
            CharacterDataManager.teleportTo(newPlayer, pos.get());
        } else {
            String hostSave = worldId.substring(0, worldId.lastIndexOf('|'));
            java.util.Optional<VaultManager.WorldPos> fallback = VaultManager.getAnyPositionForWorld(character.id(), hostSave);
            if (fallback.isPresent()) {
                NexusCharacters.LOGGER.info("[Nexus] Teleporting {} to fallback pos [{}, {}, {}]",
                        character.name(), fallback.get().x(), fallback.get().y(), fallback.get().z());
                CharacterDataManager.teleportTo(newPlayer, fallback.get());
            } else {
                net.minecraft.util.math.BlockPos spawn = newPlayer.getServerWorld().getSpawnPos();
                NexusCharacters.LOGGER.info("[Nexus] No saved position for {} — teleporting to spawn {}", character.name(), spawn);
                newPlayer.teleport(newPlayer.getServerWorld(), spawn.getX(), spawn.getY() + 1, spawn.getZ(),
                        java.util.Set.of(), 0f, 0f);
            }
        }

        newPlayer.sendAbilitiesUpdate();
        NexusCharacters.LOGGER.info("[Nexus] Respawn complete for {} (char {}).", character.name(), character.id());
    }

    // ── Install vault into world dir ──────────────────────────────────────────

    /**
     * Called on the server thread once a vault zip is assembled during play phase.
     * Installs files into the world dir then respawns the player.
     */
    private static void installVaultAndRespawn(ServerPlayerEntity player, CharacterDto dto, byte[] zip) {
        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        UUID playerUuid = player.getUuid();

        try {
            VaultManager.unzipToVault(dto.id(), zip, true);
            VaultManager.importLegacyDataIfNeeded(dto.id(), dto.name(), worldDir);
            VaultManager.clearWorldFiles(worldDir, playerUuid);
            VaultManager.copyVaultToWorld(dto.id(), worldDir, playerUuid);
            NexusCharacters.LOGGER.info("[Server] Play-phase vault installed for player {} char {}.", playerUuid, dto.id());
        } catch (Exception e) {
            NexusCharacters.LOGGER.error("[Server] Failed to install vault during play phase:", e);
            return;
        }

        respawnPlayer(player);
    }

    // ── Helper: send a payload S2C ────────────────────────────────────────────

    static void sendToClient(ServerPlayerEntity player, VaultReceiveReadyPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, VaultReceiveReadyPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, VaultChunkS2CPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, VaultChunkS2CPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, VaultTransferDoneS2CPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, VaultTransferDoneS2CPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, VaultSyncPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, VaultSyncPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, CharacterDeletedPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, CharacterDeletedPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, SkinReloadPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, SkinReloadPayload.ID, buf);
    }

    static void sendToClient(ServerPlayerEntity player, SaveAckPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, SaveAckPayload.ID, buf);
    }

    static void sendCharacterSelectRequest(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        new CharacterSelectRequestPayload().write(buf);
        ServerPlayNetworking.send(player, CharacterSelectRequestPayload.ID, buf);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {

        // 1. Client chose a character → apply data or request vault upload
        ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    SelectCharacterPayload payload = new SelectCharacterPayload(buf);
                    server.execute(() -> {
                        CharacterDto dto = payload.character();
                        NexusCharacters.setSelectedCharacter(player, dto);
                        NexusCharacters.LOGGER.info("[Server] Play SelectCharacter: char={} ({}) for player={}",
                                dto.name(), dto.id(), player.getName().getString());

                        boolean isSPorLANHost = !server.isDedicated() && server.isHost(player.getGameProfile());
                        if (isSPorLANHost) {
                            // Singleplayer / LAN host: vault already copied to world dir by the mixin.
                            CharacterDataManager.applyCharacterData(player);
                        } else {
                            // Dedicated / LAN guest: request vault upload from client.
                            NexusCharacters.LOGGER.info("[Server] Requesting vault upload from {}.", player.getName().getString());
                            sendToClient(player, new VaultReceiveReadyPayload());
                        }
                    });
                });

        // 2. Receive one vault chunk from client
        ServerPlayNetworking.registerGlobalReceiver(VaultChunkC2SPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    VaultChunkC2SPayload payload = new VaultChunkC2SPayload(buf);
                    UUID playerUuid = player.getUuid();
                    CharacterTransferManager.serverReceiveChunk(playerUuid, payload.index(), payload.total(), payload.data());
                    if (payload.index() == 0 || payload.index() == payload.total() - 1) {
                        NexusCharacters.LOGGER.info("[Server] Vault chunk {}/{} received from {}.",
                                payload.index() + 1, payload.total(), player.getName().getString());
                    }
                });

        // 3. Client signals upload complete → assemble, write to world, respawn player
        ServerPlayNetworking.registerGlobalReceiver(VaultTransferDoneC2SPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    VaultTransferDoneC2SPayload payload = new VaultTransferDoneC2SPayload(buf);
                    server.execute(() -> {
                        UUID playerUuid = player.getUuid();
                        NexusCharacters.LOGGER.info("[Server] VaultTransferDone received from {}.", player.getName().getString());

                        byte[] zip = CharacterTransferManager.serverAssemble(playerUuid);
                        NexusCharacters.LOGGER.info("[Server] Assembled {} bytes for player {}.", zip.length, player.getName().getString());

                        CharacterDto dto = NexusCharacters.getSelectedCharacter(player);
                        if (dto == null) {
                            NexusCharacters.LOGGER.error("[Server] No selected character for {} — cannot install vault.", player.getName().getString());
                            return;
                        }

                        installVaultAndRespawn(player, dto, zip);
                    });
                });

        // 4. Vault sync ack — player confirmed manual save received
        ServerPlayNetworking.registerGlobalReceiver(VaultSyncAckPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    VaultSyncAckPayload payload = new VaultSyncAckPayload(buf);
                    server.execute(() -> {
                        CharacterDto character = NexusCharacters.getSelectedCharacter(player);
                        if (character != null && character.id().equals(payload.characterId())) {
                            if (ServerPlayNetworking.canSend(player, SaveAckPayload.ID)) {
                                sendToClient(player, new SaveAckPayload());
                            }
                            player.sendMessage(net.minecraft.text.Text.literal("[Nexus] Character data saved successfully."), false);
                        }
                    });
                });
    }
}
