package net.tompsen.charsel;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public record CharacterDto(
        UUID id,
        String name,
        NbtCompound playerNbt,
        NbtCompound worldPositions,  // key = worldId, value = {x, y, z, yaw, pitch}
        String skinValue,    // base64 encoded texture
        String skinSignature
) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("id", id);
        nbt.putString("name", name);
        nbt.put("playerNbt", playerNbt);
        nbt.put("worldPositions", worldPositions);
        nbt.putString("skinValue", skinValue != null ? skinValue : "");
        nbt.putString("skinSignature", skinSignature != null ? skinSignature : "");
        return nbt;
    }

    public static CharacterDto fromNbt(NbtCompound nbt) {
        return new CharacterDto(
                nbt.getUuid("id"),
                nbt.getString("name"),
                nbt.getCompound("playerNbt"),
                nbt.getCompound("worldPositions"),
                nbt.getString("skinValue"),
                nbt.getString("skinSignature")
        );
    }
}
