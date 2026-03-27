package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server → Client: a hardcore character was deleted server-side.
 * The client removes it from its local character list immediately.
 */
public record CharacterDeletedPayload(UUID characterId) implements CustomPayload {

    public static final Id<CharacterDeletedPayload> ID =
            new Id<>(Identifier.of(NexusCharacters.MOD_ID, "character_deleted"));
    public static final PacketCodec<PacketByteBuf, CharacterDeletedPayload> CODEC =
            PacketCodec.of(CharacterDeletedPayload::write, CharacterDeletedPayload::new);

    public CharacterDeletedPayload(PacketByteBuf buf) {
        this(buf.readUuid());
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(characterId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
