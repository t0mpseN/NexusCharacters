package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client → Server: acknowledge receipt and storage of a manual VaultSyncPayload.
 */
public record VaultSyncAckPayload(UUID characterId) implements CustomPayload {

    public static final Id<VaultSyncAckPayload> ID =
            new Id<>(Identifier.of(NexusCharacters.MOD_ID, "vault_sync_ack"));
    public static final PacketCodec<PacketByteBuf, VaultSyncAckPayload> CODEC =
            PacketCodec.of(VaultSyncAckPayload::write, VaultSyncAckPayload::new);

    public VaultSyncAckPayload(PacketByteBuf buf) {
        this(buf.readUuid());
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(characterId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
