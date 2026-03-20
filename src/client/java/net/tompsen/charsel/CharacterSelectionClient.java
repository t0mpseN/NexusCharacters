package net.tompsen.charsel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class CharacterSelectionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CharacterSelectionClientNetwork.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CharacterSelection.selectedCharacter = null;
		});
	}
}