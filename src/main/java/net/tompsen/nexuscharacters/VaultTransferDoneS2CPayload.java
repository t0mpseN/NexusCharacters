package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record VaultTransferDoneS2CPayload(UUID characterId) {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_done_s2c");

    public VaultTransferDoneS2CPayload(PacketByteBuf buf) { this(buf.readUuid()); }

    public void write(PacketByteBuf buf) { buf.writeUuid(characterId); }
}
