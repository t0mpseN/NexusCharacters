package net.tompsen.nexuscharacters;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SkinReloadPayload(String skinValue, String skinSignature) {
    public static final Identifier ID = new Identifier(NexusCharacters.MOD_ID, "skin_reload");

    public SkinReloadPayload(PacketByteBuf buf) {
        this(buf.readString(), buf.readString());
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(skinValue);
        buf.writeString(skinSignature);
    }
}
