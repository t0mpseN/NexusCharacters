package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server → Client: a hardcore character was deleted server-side.
 * The client removes it from its local character list immediately.
 */
public record CharacterDeletedPayload(UUID characterId) {

    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "character_deleted");

    public CharacterDeletedPayload(PacketByteBuf buf) {
        this(buf.readUuid());
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(characterId);
    }
}
