package net.tompsen.nexuscharacters;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.UUID;

public class NexusCharactersNetwork {

    /**
     * Login-phase query ID — sent server→client during login handshake so that
     * character selection (and vault staging) happens before player data loads from disk.
     * This ensures mods like Cobblemon read the correct character files at join time.
     */
    static final Identifier LOGIN_QUERY_ID =
            new Identifier(NexusCharacters.MOD_ID, "char_select_login");

    // ── Install vault into world dir (play-phase fallback) ────────────────────

    /**
     * Called on the server thread once a vault zip is fully received from the client.
     * Writes the vault to disk and applies character data in-place via readNbt.
     */
    private static void installVaultAndApply(ServerPlayerEntity player, CharacterDto dto, byte[] zip) {
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
            NexusCharacters.pendingVaultUpload.remove(playerUuid);
            return;
        }

        NexusCharacters.pendingVaultUpload.remove(playerUuid);
        CharacterDataManager.evictPersistentStateCache(player.server);
        CharacterDataManager.applyCharacterData(player);
        NexusCharacters.LOGGER.info("[Server] Character data applied for {} (char {}).", dto.name(), dto.id());
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

        // ── Login-phase: character selection before player data loads ─────────
        //
        // On dedicated servers, we send a login query during the handshake so the
        // client selects a character BEFORE the player entity is constructed and
        // before any mod (e.g. Cobblemon) reads its per-player data from disk.
        // Once the client responds, we stage the vault files immediately so that
        // all mods pick up the correct character data at join time.
        //
        // Correct Fabric pattern:
        //   1. registerGlobalReceiver at startup (handles responses for all sessions)
        //   2. QUERY_START sends the query packet for each login
        //   3. Response handler uses synchronizer.waitFor() to block login while staging

        ServerLoginNetworking.registerGlobalReceiver(LOGIN_QUERY_ID,
                (server, handler, understood, responseBuf, synchronizer, responder) -> {
                    if (!understood || responseBuf == null || !responseBuf.isReadable()) {
                        // Client doesn't have the mod, or user closed the picker without choosing.
                        // Login proceeds normally — JOIN handler will send play-phase CharacterSelectRequest.
                        NexusCharacters.LOGGER.info("[Nexus] Login query: no character selected (understood={}) — play-phase fallback.", understood);
                        return;
                    }

                    CharacterDto dto;
                    try {
                        dto = CharacterDto.fromNbt(responseBuf.readNbt());
                    } catch (Exception e) {
                        NexusCharacters.LOGGER.warn("[Nexus] Login query response malformed: {}", e.getMessage());
                        return;
                    }

                    com.mojang.authlib.GameProfile profile =
                            ((net.tompsen.nexuscharacters.mixin.ServerLoginNetworkHandlerAccessor) handler).getProfile();
                    if (profile == null) {
                        NexusCharacters.LOGGER.warn("[Nexus] Login query: profile not yet set — cannot stage vault.");
                        return;
                    }
                    UUID playerUuid = profile.getId();
                    NexusCharacters.LOGGER.info("[Nexus] Login-phase character selected: {} ({}) for uuid={}",
                            dto.name(), dto.id(), playerUuid);

                    // Read vault zip if included (may not be present if client had a zip error).
                    byte[] zipData = null;
                    try {
                        if (responseBuf.isReadable()) {
                            zipData = responseBuf.readByteArray();
                        }
                    } catch (Exception e) {
                        NexusCharacters.LOGGER.warn("[Nexus] Login query: could not read vault zip: {}", e.getMessage());
                    }
                    final byte[] finalZip = zipData;

                    // Store the selection so the JOIN event handler can find it.
                    NexusCharacters.pendingCharacters.put(playerUuid, dto);

                    // Stage vault on the server thread, blocking login until done.
                    // This runs BEFORE onPlayerConnect/loadPlayerData, so all mods
                    // (including Cobblemon) read the correct character's files at join.
                    synchronizer.waitFor(server.submit(() -> {
                        Path worldDir = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
                        try {
                            if (finalZip != null && finalZip.length > 0) {
                                // Client sent vault zip — unzip it and stage to world dir.
                                VaultManager.unzipToVault(dto.id(), finalZip, true);
                                NexusCharacters.LOGGER.info("[Nexus] Login-phase vault unzipped for char {}.", dto.id());
                            } else if (java.nio.file.Files.isDirectory(VaultManager.getVaultDir(dto.id()))) {
                                // No zip from client but server has an existing vault — use it.
                                NexusCharacters.LOGGER.info("[Nexus] Login-phase using existing server vault for char {}.", dto.id());
                            } else {
                                // No vault anywhere — first join, nothing to stage.
                                NexusCharacters.LOGGER.info("[Nexus] Login-phase: no vault for char {} — staging skipped.", dto.id());
                                return;
                            }
                            VaultManager.importLegacyDataIfNeeded(dto.id(), dto.name(), worldDir);
                            VaultManager.clearWorldFiles(worldDir, playerUuid);
                            VaultManager.copyVaultToWorld(dto.id(), worldDir, playerUuid);
                            NexusCharacters.LOGGER.info("[Nexus] Login-phase vault staged for {} char {}.", playerUuid, dto.id());
                        } catch (Exception e) {
                            NexusCharacters.LOGGER.error("[Nexus] Login-phase vault staging failed:", e);
                        }
                    }));
                });

        // Send the login query for every dedicated-server login.
        ServerLoginConnectionEvents.QUERY_START.register((loginHandler, server, sender, synchronizer) -> {
            if (!server.isDedicated()) return;
            // Empty payload — just a trigger for the client to open the character picker.
            sender.sendPacket(LOGIN_QUERY_ID, PacketByteBufs.empty());
        });

        // ── Play-phase handlers ───────────────────────────────────────────────

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
                            // Dedicated / LAN guest: vault not yet staged (first-time join or login
                            // query not answered). Request vault upload from client.
                            NexusCharacters.pendingVaultUpload.add(player.getUuid());
                            NexusCharacters.LOGGER.info("[Server] Requesting vault upload from {} (upload pending).", player.getName().getString());
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

        // 3. Client signals upload complete → assemble, write to world, apply data
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

                        installVaultAndApply(player, dto, zip);
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
