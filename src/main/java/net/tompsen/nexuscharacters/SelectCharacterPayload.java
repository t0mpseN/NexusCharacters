package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.UUID;

/**
 * Client → Server: "I chose this character."
 * Carries the full lightweight DTO so the server can store the selection.
 */
public record SelectCharacterPayload(CharacterDto character) {
    public static final Identifier ID = new Identifier("nexuscharacters", "select_character");

    public SelectCharacterPayload(PacketByteBuf buf) {
        this(CharacterDto.fromNbt(buf.readNbt()));
    }

    public void write(PacketByteBuf buf) {
        buf.writeNbt(character.toNbt());
    }

    /** Convenience accessor — matches old call sites that used .characterId() */
    public UUID characterId() { return character.id(); }
}
