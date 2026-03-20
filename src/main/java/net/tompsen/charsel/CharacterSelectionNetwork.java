package net.tompsen.charsel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.network.ServerPlayerEntity;

public class CharacterSelectionNetwork {
    public static void register() {
        PayloadTypeRegistry.playC2S().register(SelectCharacterPayload.ID, PacketCodec.of(
                SelectCharacterPayload::write, SelectCharacterPayload::new
        ));
        PayloadTypeRegistry.playC2S().register(RequestSavePayload.ID,
                PacketCodec.unit(new RequestSavePayload()));
        PayloadTypeRegistry.playS2C().register(ModPresentPayload.ID,
                PacketCodec.unit(new ModPresentPayload()));
        PayloadTypeRegistry.playS2C().register(SaveCharacterPayload.ID, PacketCodec.of(
                SaveCharacterPayload::write, SaveCharacterPayload::new
        ));

        // Client sends selected character → server loads it
        ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                CharacterDto character = payload.character();
                CharacterSelection.setSelectedCharacter(context.player(), character);
                CharacterSelection.DATA_FILE_MANAGER.updateCharacter(character);
                context.server().execute(() ->
                        CharacterDataManager.loadCharacterToPlayer(context.player())
                );
            });
        });

        // Client requests save
        ServerPlayNetworking.registerGlobalReceiver(RequestSavePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                CharacterDataManager.saveCurrentCharacter(player);
                CharacterDto current = CharacterSelection.getSelectedCharacter(player);
                if (current != null && ServerPlayNetworking.canSend(player.networkHandler, SaveCharacterPayload.ID)) {
                    ServerPlayNetworking.send(player, new SaveCharacterPayload(current.toNbt()));
                }
            });
        });
    }
}
