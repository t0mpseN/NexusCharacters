package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client → Server: acknowledge receipt and storage of a manual VaultSyncPayload.
 */
public record VaultSyncAckPayload(UUID characterId) {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_sync_ack");

    public VaultSyncAckPayload(PacketByteBuf buf) {
        this(buf.readUuid());
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(characterId);
    }
}
