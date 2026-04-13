package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SaveAckPayload() {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "save_ack");

    public SaveAckPayload(PacketByteBuf buf) {
        this();
    }

    public void write(PacketByteBuf buf) {
        // no data
    }
}
