package net.tompsen.nexuscharacters;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class NexusCharactersClientNetwork {
    public static void register() {

        // ── Play-phase handlers ───────────────────────────────────────────────

        // Server asks client to pick a character (play phase — dedicated/LAN guest).
        ClientPlayNetworking.registerGlobalReceiver(CharacterSelectRequestPayload.ID,
                (client, handler, buf, responseSender) -> {
                    new CharacterSelectRequestPayload(buf); // consume buf
                    client.execute(() -> {
                        NexusCharacters.LOGGER.info("[Client] Received CharacterSelectRequest — showing picker.");
                        client.setScreen(new CharacterSelectionScreen(null, () -> {
                            if (NexusCharacters.selectedCharacter == null) {
                                NexusCharacters.LOGGER.warn("[Client] No character selected — cannot proceed.");
                                return;
                            }
                            NexusCharacters.LOGGER.info("[Client] Character selected: {}", NexusCharacters.selectedCharacter.name());
                            PacketByteBuf sendBuf = PacketByteBufs.create();
                            new SelectCharacterPayload(NexusCharacters.selectedCharacter).write(sendBuf);
                            ClientPlayNetworking.send(SelectCharacterPayload.ID, sendBuf);
                            client.setScreen(null);
                        }));
                    });
                });

        // Server sends ModPresent → show character picker (singleplayer / LAN host fallback).
        ClientPlayNetworking.registerGlobalReceiver(ModPresentPayload.ID,
                (client, handler, buf, responseSender) -> {
                    new ModPresentPayload(buf); // consume buf
                    client.execute(() -> {
                        client.setScreen(new CharacterSelectionScreen(null, () -> {
                            if (NexusCharacters.selectedCharacter == null) return;
                            PacketByteBuf sendBuf = PacketByteBufs.create();
                            new SelectCharacterPayload(NexusCharacters.selectedCharacter).write(sendBuf);
                            ClientPlayNetworking.send(SelectCharacterPayload.ID, sendBuf);
                            client.setScreen(null);
                        }));
                    });
                });

        // Server ready to receive vault → start upload.
        ClientPlayNetworking.registerGlobalReceiver(VaultReceiveReadyPayload.ID,
                (client, handler, buf, responseSender) -> {
                    new VaultReceiveReadyPayload(buf); // consume buf
                    client.execute(() -> {
                        if (NexusCharacters.selectedCharacter != null) {
                            NexusCharacters.LOGGER.info("[Client] VaultReceiveReady — starting upload for {}.", NexusCharacters.selectedCharacter.name());
                            CharacterTransferClientManager.startUpload(NexusCharacters.selectedCharacter.id());
                        } else {
                            NexusCharacters.LOGGER.warn("[Client] VaultReceiveReady but no character selected.");
                        }
                    });
                });

        // Server streams vault back after join (S2C download).
        ClientPlayNetworking.registerGlobalReceiver(VaultChunkS2CPayload.ID,
                (client, handler, buf, responseSender) -> {
                    VaultChunkS2CPayload payload = new VaultChunkS2CPayload(buf);
                    CharacterTransferClientManager.clientReceiveChunk(payload.index(), payload.total(), payload.data());
                });

        ClientPlayNetworking.registerGlobalReceiver(VaultTransferDoneS2CPayload.ID,
                (client, handler, buf, responseSender) -> {
                    VaultTransferDoneS2CPayload payload = new VaultTransferDoneS2CPayload(buf);
                    client.execute(() -> {
                        byte[] zip = CharacterTransferClientManager.clientAssemble();
                        try {
                            VaultManager.unzipToVault(payload.characterId(), zip);
                            NexusCharacters.LOGGER.info("[Client] Vault received and stored for char {}.", payload.characterId());
                        } catch (Exception e) {
                            NexusCharacters.LOGGER.error("[Client] Failed to store received vault:", e);
                        }
                    });
                });

        // Incremental vault sync: server pushes small files every ~1 second.
        ClientPlayNetworking.registerGlobalReceiver(VaultSyncPayload.ID,
                (client, handler, buf, responseSender) -> {
                    VaultSyncPayload payload = new VaultSyncPayload(buf);
                    if (payload.files().isEmpty()) {
                        if (payload.isManual()) {
                            PacketByteBuf ackBuf = PacketByteBufs.create();
                            new VaultSyncAckPayload(payload.characterId()).write(ackBuf);
                            ClientPlayNetworking.send(VaultSyncAckPayload.ID, ackBuf);
                        }
                        return;
                    }
                    client.execute(() -> {
                        VaultManager.applyVaultSync(payload.characterId(), payload.files());
                        if (payload.isManual()) {
                            PacketByteBuf ackBuf = PacketByteBufs.create();
                            new VaultSyncAckPayload(payload.characterId()).write(ackBuf);
                            ClientPlayNetworking.send(VaultSyncAckPayload.ID, ackBuf);
                        }
                    });
                });

        // Server deleted a hardcore character (death).
        ClientPlayNetworking.registerGlobalReceiver(CharacterDeletedPayload.ID,
                (client, handler, buf, responseSender) -> {
                    CharacterDeletedPayload payload = new CharacterDeletedPayload(buf);
                    client.execute(() -> {
                        NexusCharacters.DATA_FILE_MANAGER.deleteCharacter(payload.characterId());
                        NexusCharacters.LOGGER.info("[Client] Hardcore character {} deleted by server.", payload.characterId());
                    });
                });

        // Manual save acknowledged — show a toast notification.
        ClientPlayNetworking.registerGlobalReceiver(SaveAckPayload.ID,
                (client, handler, buf, responseSender) -> {
                    new SaveAckPayload(buf); // consume buf
                    client.execute(() -> {
                        SystemToast.add(
                                client.getToastManager(),
                                SystemToast.Type.WORLD_BACKUP,
                                Text.literal("Progress Saved"),
                                Text.literal("Your character data has been saved.")
                        );
                    });
                });

    }
}
