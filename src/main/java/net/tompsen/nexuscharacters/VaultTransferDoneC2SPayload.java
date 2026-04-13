package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record VaultTransferDoneC2SPayload(UUID characterId) {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_done_c2s");

    public VaultTransferDoneC2SPayload(PacketByteBuf buf) { this(buf.readUuid()); }

    public void write(PacketByteBuf buf) { buf.writeUuid(characterId); }
}
