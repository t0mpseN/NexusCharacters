package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Server → Client (play phase):
 * "Please select a character to begin playing."
 */
public record CharacterSelectRequestPayload() {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "char_select_request");

    public CharacterSelectRequestPayload(PacketByteBuf buf) {
        this();
    }

    public void write(PacketByteBuf buf) {
        // no data
    }
}
