package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server → Client: incremental vault sync.
 * Carries a map of vault-relative paths → file bytes for one character.
 * Sent every second during play so the client always has a fresh local copy.
 */
public record VaultSyncPayload(UUID characterId, Map<String, byte[]> files, boolean isManual) {

    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_sync");

    public VaultSyncPayload(PacketByteBuf buf) {
        this(buf.readUuid(), readFiles(buf), buf.readBoolean());
    }

    private static Map<String, byte[]> readFiles(PacketByteBuf buf) {
        int count = buf.readVarInt();
        Map<String, byte[]> map = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String path = buf.readString(4096);
            byte[] data = buf.readByteArray();
            map.put(path, data);
        }
        return map;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(characterId);
        buf.writeVarInt(files.size());
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeByteArray(entry.getValue());
        }
        buf.writeBoolean(isManual);
    }
}
