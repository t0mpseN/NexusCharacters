package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record VaultChunkC2SPayload(int index, int total, byte[] data) {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_chunk_c2s");

    public VaultChunkC2SPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readByteArray());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(index); buf.writeInt(total); buf.writeByteArray(data);
    }
}
