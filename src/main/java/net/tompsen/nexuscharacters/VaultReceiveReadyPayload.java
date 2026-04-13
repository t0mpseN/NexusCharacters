package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Server → Client: "I'm ready, start sending your vault." */
public record VaultReceiveReadyPayload() {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "vault_receive_ready");

    public VaultReceiveReadyPayload(PacketByteBuf buf) {
        this();
    }

    public void write(PacketByteBuf buf) {
        // no data
    }
}
