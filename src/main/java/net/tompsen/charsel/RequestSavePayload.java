package net.tompsen.charsel;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestSavePayload() implements CustomPayload {
    public static final CustomPayload.Id<RequestSavePayload> ID =
            new CustomPayload.Id<>(Identifier.of("charsel", "request_save"));

    @Override
    public CustomPayload.Id<RequestSavePayload> getId() { return ID; }
}