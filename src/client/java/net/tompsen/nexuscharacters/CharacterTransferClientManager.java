package net.tompsen.nexuscharacters;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.Arrays;
import java.util.UUID;

public class CharacterTransferClientManager {

    public static final int CHUNK_SIZE = CharacterTransferManager.CHUNK_SIZE;

    // ── Client → Server upload (play phase) ──────────────────────────────────

    public static void startUpload(UUID characterId) {
        new Thread(() -> {
            try {
                byte[] zip   = VaultManager.zipVault(characterId);
                int    total = Math.max(1, (zip.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
                NexusCharacters.LOGGER.info("[Transfer] Upload {} bytes ({} chunks).", zip.length, total);

                for (int i = 0; i < total; i++) {
                    int from  = i * CHUNK_SIZE;
                    int to    = Math.min(from + CHUNK_SIZE, zip.length);
                    byte[] chunk = Arrays.copyOfRange(zip, from, to);
                    PacketByteBuf chunkBuf = PacketByteBufs.create();
                    new VaultChunkC2SPayload(i, total, chunk).write(chunkBuf);
                    ClientPlayNetworking.send(VaultChunkC2SPayload.ID, chunkBuf);
                    Thread.sleep(30);
                }

                PacketByteBuf doneBuf = PacketByteBufs.create();
                new VaultTransferDoneC2SPayload(characterId).write(doneBuf);
                ClientPlayNetworking.send(VaultTransferDoneC2SPayload.ID, doneBuf);
                NexusCharacters.LOGGER.info("[Transfer] Upload complete.");
            } catch (Exception e) {
                NexusCharacters.LOGGER.error("[Transfer] Upload failed:", e);
            }
        }, "NexusChars-Upload").start();
    }

    // ── Client-side: assemble downloaded chunks ──────────────────────────────

    private static byte[][] clientChunks;

    public static void clientReceiveChunk(int index, int total, byte[] data) {
        if (clientChunks == null || clientChunks.length != total) clientChunks = new byte[total][];
        clientChunks[index] = data;
    }

    public static byte[] clientAssemble() {
        if (clientChunks == null) return new byte[0];
        int len = 0;
        for (byte[] c : clientChunks) if (c != null) len += c.length;
        byte[] zip = new byte[len];
        int pos = 0;
        for (byte[] c : clientChunks) { if (c != null) { System.arraycopy(c, 0, zip, pos, c.length); pos += c.length; } }
        clientChunks = null;
        return zip;
    }
}
