package net.tompsen.nexuscharacters;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;


public class NexusCharactersClient implements ClientModInitializer {

	private static final java.util.Set<String> NEXUS_SCREENS = java.util.Set.of(
			"net.tompsen.nexuscharacters.CharacterListScreen",
			"net.tompsen.nexuscharacters.CharacterSelectionScreen",
			"net.tompsen.nexuscharacters.CharacterCreationScreen",
			"net.tompsen.nexuscharacters.CharacterEditScreen",
			"net.tompsen.nexuscharacters.ConfirmDeleteScreen"
	);

	/**
	 * Registers a FancyMenu screen blacklist rule so it does not intercept or
	 * overlay our custom screens. Uses reflection so we don't hard-depend on FancyMenu.
	 */
	private static void blacklistFromFancyMenu() {
		try {
			Class<?> sc = Class.forName("de.keksuccino.fancymenu.customization.ScreenCustomization");
			Class<?> ruleInterface = Class.forName("de.keksuccino.fancymenu.customization.ScreenCustomization$ScreenBlacklistRule");
			java.lang.reflect.Method addRule = sc.getMethod("addScreenBlacklistRule", ruleInterface);
			Object rule = java.lang.reflect.Proxy.newProxyInstance(
					NexusCharactersClient.class.getClassLoader(),
					new Class[]{ruleInterface},
					(proxy, method, args) -> {
						if ("isScreenBlacklisted".equals(method.getName()) && args != null && args.length == 1) {
							return NEXUS_SCREENS.contains(args[0]);
						}
						return false;
					}
			);
			addRule.invoke(null, rule);
			NexusCharacters.LOGGER.info("[NexusCharacters] Registered FancyMenu blacklist for Nexus screens.");
		} catch (ClassNotFoundException ignored) {
			// FancyMenu not present — nothing to do
		} catch (Exception e) {
			NexusCharacters.LOGGER.warn("[NexusCharacters] Failed to register FancyMenu blacklist: {}", e.getMessage());
		}
	}
	@Override
	public void onInitializeClient() {
		NexusCharactersClientNetwork.register();

		ClientPlayNetworking.registerGlobalReceiver(SkinReloadPayload.ID, (client, handler, buf, responseSender) -> {
			SkinReloadPayload payload = new SkinReloadPayload(buf);
			client.execute(() -> {
				net.minecraft.client.network.ClientPlayerEntity player = client.player;
				if (player == null) return;

				com.mojang.authlib.properties.Property prop =
						new com.mojang.authlib.properties.Property(
								"textures", payload.skinValue(), payload.skinSignature());

				player.getGameProfile().getProperties().removeAll("textures");
				player.getGameProfile().getProperties().put("textures", prop);

				// Reload skin with new profile
				client.getSkinProvider().loadSkin(
						player.getGameProfile(),
						(type, textureId, texture) -> {
							if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN) {
								NexusCharacters.LOGGER.info("[NexusCharacters] Skin reloaded: {}", textureId);
							}
						},
						true
				);
			});
		});

		// Init fake world immediately at launch
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			DummyWorldManager.initAtStartup();
			blacklistFromFancyMenu();
		});

		//ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
		//	ClientWorld world = MinecraftClient.getInstance().world;
		//	if (world != null) DummyWorldManager.captureFromWorld(world);
		//});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			// Vault copy is handled server-side by PlayerManagerMixin.afterRemove.
			// Just clear the client-side selected character reference.
			NexusCharacters.selectedCharacter = null;
			NexusCharactersClientNetwork.pendingCharacterSelection = false;
		});
	}
}