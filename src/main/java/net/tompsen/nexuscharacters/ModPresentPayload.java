package net.tompsen.nexuscharacters;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModPresentPayload() implements CustomPayload {
    public static final CustomPayload.Id<ModPresentPayload> ID =
            new CustomPayload.Id<>(Identifier.of("nexuscharacters", "mod_present"));

    @Override
    public CustomPayload.Id<ModPresentPayload> getId() { return ID; }
}