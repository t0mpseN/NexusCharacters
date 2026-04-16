package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: manual save acknowledged.
 */
public record SaveAckPayload() implements CustomPayload {

    public static final Id<SaveAckPayload> ID =
            new Id<>(Identifier.of(NexusCharacters.MOD_ID, "save_ack"));
    public static final PacketCodec<PacketByteBuf, SaveAckPayload> CODEC =
            PacketCodec.of(SaveAckPayload::write, SaveAckPayload::new);

    public SaveAckPayload(PacketByteBuf buf) {
        this();
    }

    public void write(PacketByteBuf buf) {
        // no data
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
