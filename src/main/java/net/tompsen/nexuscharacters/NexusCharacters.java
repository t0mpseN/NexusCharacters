package net.tompsen.nexuscharacters;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NexusCharacters implements ModInitializer {
	public static final String MOD_ID = "nexuscharacters";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static DataFileManager DATA_FILE_MANAGER;
	public static CharacterDto selectedCharacter = null;
	// Per-player map instead of single static field
	public static final Map<UUID, CharacterDto> selectedCharacters = new ConcurrentHashMap<>();
	// Track when players joined
	public static final Map<UUID, Long> playerJoinTick = new ConcurrentHashMap<>();
	public static final java.util.Set<UUID> deadHardcorePlayers = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
	/** Players currently being respawned by NexusCharacters — skip vault save in afterRemove. */
	public static final java.util.Set<UUID> respawningPlayers = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
	/**
	 * Players whose vault upload is currently in-flight from client to server.
	 * The periodic vault sync tick must NOT send stale server-side data to these players
	 * while the upload is pending, or the client vault will be overwritten with old data.
	 * Added when we send VaultReceiveReadyPayload; removed when installVaultAndRespawn completes.
	 */
	public static final java.util.Set<UUID> pendingVaultUpload = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

	/**
	 * Characters selected during the configuration phase (before the player entity exists).
	 * Consumed on ServerPlayConnectionEvents.JOIN to set selectedCharacters.
	 */
	public static final Map<UUID, CharacterDto> pendingCharacters = new ConcurrentHashMap<>();
	/** Ticks between per-second incremental vault syncs (20 ticks = 1 second). */
	private static final int VAULT_SYNC_INTERVAL_TICKS = 20;
	/** Ticks between advancements + pokedex syncs. Configurable via /nexus saveinterval. Default 30 s. */
	public static volatile int ADVANCEMENTS_SYNC_INTERVAL_TICKS = 600;
	/** Set to true to force a full sync on the next tick regardless of interval. */
	public static volatile boolean FORCE_FULL_SYNC_NEXT_TICK = false;

	// ── Config persistence ────────────────────────────────────────────────────

	private static Path serverConfigPath = null;

	private static void loadServerConfig(MinecraftServer server) {
		// On dedicated: ROOT/../nexuscharacters.properties (server root)
		// On integrated: ROOT/nexuscharacters.properties (inside the world dir)
		Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();
		serverConfigPath = server.isDedicated()
				? root.getParent().resolve("nexuscharacters.properties")
				: root.resolve("nexuscharacters.properties");
		if (!Files.exists(serverConfigPath)) return;
		try {
			Properties props = new Properties();
			props.load(Files.newBufferedReader(serverConfigPath));
			String val = props.getProperty("saveinterval_ticks");
			if (val != null) ADVANCEMENTS_SYNC_INTERVAL_TICKS = Integer.parseInt(val.trim());
		} catch (Exception e) {
			LOGGER.warn("[Nexus] Could not load server config: {}", e.getMessage());
		}
	}

	public static void saveServerConfig() {
		if (serverConfigPath == null) return;
		try {
			Properties props = new Properties();
			props.setProperty("saveinterval_ticks", String.valueOf(ADVANCEMENTS_SYNC_INTERVAL_TICKS));
			try (var w = Files.newBufferedWriter(serverConfigPath)) {
				props.store(w, "NexusCharacters server config");
			}
		} catch (IOException e) {
			LOGGER.warn("[Nexus] Could not save server config: {}", e.getMessage());
		}
	}


	public static CharacterDto getSelectedCharacter(ServerPlayerEntity player) {
		return selectedCharacters.get(player.getUuid());
	}

	public static void setSelectedCharacter(ServerPlayerEntity player, CharacterDto character) {
		selectedCharacters.put(player.getUuid(), character);
	}

	public static void clearSelectedCharacter(ServerPlayerEntity player) {
		selectedCharacters.remove(player.getUuid());
	}

	@Override
	public void onInitialize() {
		DATA_FILE_MANAGER = new DataFileManager();
		NexusCharactersNetwork.register();

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			loadServerConfig(server);
			LOGGER.info("[Nexus] Server started — saveinterval = {}t ({} s)",
					ADVANCEMENTS_SYNC_INTERVAL_TICKS, ADVANCEMENTS_SYNC_INTERVAL_TICKS / 20);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			UUID uuid = handler.player.getUuid();
			playerJoinTick.put(uuid, (long) server.getTicks());
			ServerPlayerEntity player = handler.player;
			LOGGER.info("[Nexus] JOIN event for {} (uuid={}) dedicated={}", player.getName().getString(), uuid, server.isDedicated());

			boolean isLanGuest = !server.isDedicated() && !server.isHost(player.getGameProfile());
			if (server.isDedicated()) {
				// Dedicated server: check if character was already selected during the login query.
				CharacterDto loginChar = pendingCharacters.remove(uuid);
				if (loginChar != null) {
					// Login-phase succeeded: vault was staged before player data loaded.
					// The player entity already has the correct data (loadPlayerData read the
					// staged files). Just apply our metadata (trackers, position, skin).
					setSelectedCharacter(player, loginChar);
					LOGGER.info("[Nexus] JOIN: login-phase vault staged for {} — applying character data.", player.getName().getString());
					server.execute(() -> CharacterDataManager.applyCharacterData(player));
				} else {
					// Login-phase was skipped (client didn't answer query, or vanilla client).
					// Fall back to play-phase character selection.
					LOGGER.info("[Nexus] JOIN: no login-phase character for {} — sending play-phase CharacterSelectRequest.", player.getName().getString());
					server.execute(() -> NexusCharactersNetwork.sendCharacterSelectRequest(player));
				}
			} else if (isLanGuest) {
				// LAN non-host: prompt client to select a character via play-phase packet.
				LOGGER.info("[Nexus] JOIN: LAN guest {} — sending play-phase CharacterSelectRequest.", player.getName().getString());
				server.execute(() -> NexusCharactersNetwork.sendCharacterSelectRequest(player));
			} else {
				// Singleplayer / LAN host — character was already chosen from the title screen.
				LOGGER.info("[Nexus] JOIN: singleplayer/LAN host {}, selectedCharacter={}",
						player.getName().getString(), NexusCharacters.selectedCharacter != null ? NexusCharacters.selectedCharacter.name() : "null");
				if (NexusCharacters.selectedCharacter != null) {
					NexusCharacters.setSelectedCharacter(player, NexusCharacters.selectedCharacter);
				}
				server.execute(() -> CharacterDataManager.applyCharacterData(player));
			}
		});

		// For dedicated servers AND LAN non-host: on disconnect, serialize player state from memory,
		// save the server-side vault, and send the final state to the client.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.player;
			boolean isLanGuest = !server.isDedicated() && !server.isHost(player.getGameProfile());
			if (!server.isDedicated() && !isLanGuest) return;

			UUID disconnectUuid = player.getUuid();
			LOGGER.info("[Nexus] DISCONNECT event for {} (uuid={})",
					player.getName().getString(), disconnectUuid);

			pendingCharacters.remove(disconnectUuid);
			pendingVaultUpload.remove(disconnectUuid);

			CharacterDto current = NexusCharacters.getSelectedCharacter(player);
			if (current == null) return;

			// Sync gameMode from live player state
			int liveGameMode = player.interactionManager.getGameMode().getId();
			final CharacterDto charToSave = liveGameMode != current.gameMode()
					? new CharacterDto(current.id(), current.name(), current.skinValue(),
							current.skinSignature(), current.skinUsername(), liveGameMode, current.hardcore())
					: current;
			if (liveGameMode != current.gameMode()) {
				DATA_FILE_MANAGER.updateCharacter(charToSave);
			}

			// Serialize playerdata directly from memory, with UUID tokenized for vault storage
			byte[] playerNbtBytes = null;
			try {
				playerNbtBytes = VaultManager.serializePlayerNbtTokenized(player);
			} catch (Exception e) {
				LOGGER.warn("[Nexus] DISCONNECT: failed to serialize playerdata: {}", e.getMessage());
			}

			// Save position to vault metadata
			String worldId = CharacterDataManager.getWorldId(player);
			VaultManager.saveWorldPosition(charToSave.id(), worldId,
					player.getX(), player.getY(), player.getZ(),
					player.getYaw(), player.getPitch());

			// Flush disk data
			try { player.getAdvancementTracker().save(); } catch (Exception ignored) {}
			try { player.getStatHandler().save(); } catch (Exception ignored) {}

			// Final sync to client
			if (playerNbtBytes != null && ServerPlayNetworking.canSend(player, VaultSyncPayload.ID)) {
				try {
					java.nio.file.Path worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
							.toAbsolutePath().normalize();
					java.util.Map<String, byte[]> allFiles = new java.util.LinkedHashMap<>();
					// Disconnect sync includes everything
					allFiles.putAll(VaultManager.collectEssentialFiles(charToSave.id(), worldDir, disconnectUuid));
					allFiles.putAll(VaultManager.collectModFiles(charToSave.id(), worldDir, disconnectUuid));
					allFiles.putAll(VaultManager.collectAdvancementsFile(worldDir, disconnectUuid));
					allFiles.put("playerdata/" + VaultManager.PLAYER_TOKEN + ".dat", playerNbtBytes);

					NexusCharactersNetwork.sendToClient(player, new VaultSyncPayload(charToSave.id(), allFiles, false));
					LOGGER.info("[Nexus] DISCONNECT: sent final VaultSyncPayload ({} files) to {}.",
							allFiles.size(), player.getName().getString());
				} catch (Exception e) {
					LOGGER.warn("[Nexus] DISCONNECT: failed to send final VaultSyncPayload: {}", e.getMessage());
				}
			}

			// Background server vault update
			final byte[] finalPlayerNbt = playerNbtBytes;
			server.execute(() -> {
				java.nio.file.Path worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
						.toAbsolutePath().normalize();
				if (finalPlayerNbt != null) {
					try {
						java.nio.file.Path playerFile = worldDir.resolve("playerdata").resolve(disconnectUuid + ".dat");
						java.nio.file.Files.createDirectories(playerFile.getParent());
						java.nio.file.Files.write(playerFile, finalPlayerNbt);
					} catch (Exception ignored) {}
				}
				VaultManager.copyWorldToVault(charToSave.id(), worldDir, disconnectUuid);
			});
		});

		// Periodic server→client incremental vault sync.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(tickServer -> {
			int ticks = tickServer.getTicks();
			if (ticks % VAULT_SYNC_INTERVAL_TICKS != 0) return;

			int advInterval = ADVANCEMENTS_SYNC_INTERVAL_TICKS;
			boolean forceNow = FORCE_FULL_SYNC_NEXT_TICK;
			if (forceNow) FORCE_FULL_SYNC_NEXT_TICK = false;
			boolean includeAll = forceNow || (advInterval > 0 && (ticks % advInterval == 0));

			// On full-sync ticks, ask the server to flush all world/player data to disk
			// so mod files (e.g. Cobblemon pokedex) are written before we read and sync them.
			// save(suppressLog=true, flush=false, force=false) — avoids log spam and is non-blocking.
			if (includeAll) {
				try { tickServer.save(true, false, false); } catch (Exception ignored) {}
			}

			for (ServerPlayerEntity player : tickServer.getPlayerManager().getPlayerList()) {
				CharacterDto character = getSelectedCharacter(player);
				if (character == null) continue;
				if (!ServerPlayNetworking.canSend(player, VaultSyncPayload.ID)) continue;
				// Skip sync while the client is uploading their vault — sending stale server data
				// back to the client during the upload window would overwrite the correct client vault.
				if (pendingVaultUpload.contains(player.getUuid())) continue;

				final UUID playerUuid = player.getUuid();
				final UUID charId = character.id();
				final boolean doFullSync = includeAll;

				// Save current position
				String worldId = CharacterDataManager.getWorldId(player);
				VaultManager.saveWorldPosition(charId, worldId,
						player.getX(), player.getY(), player.getZ(),
						player.getYaw(), player.getPitch());

				try {
					player.getAdvancementTracker().save();
					player.getStatHandler().save();
					((net.tompsen.nexuscharacters.mixin.PlayerManagerAccessor) tickServer.getPlayerManager())
							.invokeSavePlayerData(player);
				} catch (Exception ignored) {}

				final byte[] playerNbtBytes;
				try {
					playerNbtBytes = VaultManager.serializePlayerNbtTokenized(player);
				} catch (Exception e) { continue; }
				
				final String playerNbtKey = "playerdata/" + VaultManager.PLAYER_TOKEN + ".dat";

				new Thread(() -> {
					try {
						java.nio.file.Path worldDir = tickServer.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
								.toAbsolutePath().normalize();
						
						java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
						files.putAll(VaultManager.collectEssentialFiles(charId, worldDir, playerUuid));
						
						if (doFullSync) {
							files.putAll(VaultManager.collectModFiles(charId, worldDir, playerUuid));
							files.putAll(VaultManager.collectAdvancementsFile(worldDir, playerUuid));
						}
						
						files.put(playerNbtKey, playerNbtBytes);
						
						final java.util.Map<String, byte[]> snapshot = java.util.Collections.unmodifiableMap(files);
						tickServer.execute(() ->
								NexusCharactersNetwork.sendToClient(player, new VaultSyncPayload(charId, snapshot, false)));
					} catch (Exception ignored) {}
				}, "NexusChars-VaultSync").start();
			}
		});

		// Hardcore Death
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, dmg) -> {
			if (entity instanceof ServerPlayerEntity player) {
				CharacterDto ch = getSelectedCharacter(player);
				if (ch != null && ch.hardcore()) {
					deadHardcorePlayers.add(player.getUuid());
					DATA_FILE_MANAGER.deleteCharacter(ch.id());
					clearSelectedCharacter(player);
					boolean isRemotePlayer = player.server.isDedicated()
							|| !player.server.isHost(player.getGameProfile());
					if (isRemotePlayer && ServerPlayNetworking.canSend(player, CharacterDeletedPayload.ID)) {
						NexusCharactersNetwork.sendToClient(player, new CharacterDeletedPayload(ch.id()));
					}
				}
			}
		});

		// Commands
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				net.minecraft.server.command.CommandManager.literal("nexus")
					.then(net.minecraft.server.command.CommandManager.literal("save")
						.executes(ctx -> {
							net.minecraft.server.command.ServerCommandSource src = ctx.getSource();
							if (!(src.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
								src.sendFeedback(() -> net.minecraft.text.Text.literal("[Nexus] Only players can use this command."), false);
								return 0;
							}
							CharacterDto character = getSelectedCharacter(player);
							if (character == null) {
								src.sendFeedback(() -> net.minecraft.text.Text.literal("[Nexus] No active character to save."), false);
								return 0;
							}

							// Flush world and mod data to disk first — matches what the autosave does on full-sync
							// ticks. flush=true waits for async I/O to complete so mod files (Cobblemon Pokedex,
							// backpacks, etc.) are fully written before we read them.
							try { player.server.save(true, true, false); } catch (Exception ignored) {}

							// Flush disk data on the server thread
							String worldId = CharacterDataManager.getWorldId(player);
							VaultManager.saveWorldPosition(character.id(), worldId,
									player.getX(), player.getY(), player.getZ(),
									player.getYaw(), player.getPitch());
							try { player.getAdvancementTracker().save(); } catch (Exception ignored) {}
							try { player.getStatHandler().save(); } catch (Exception ignored) {}
							try {
								((net.tompsen.nexuscharacters.mixin.PlayerManagerAccessor) player.server.getPlayerManager())
										.invokeSavePlayerData(player);
							} catch (Exception ignored) {}

							final byte[] nbtBytes;
							try {
								nbtBytes = VaultManager.serializePlayerNbtTokenized(player);
							} catch (Exception e) {
								src.sendFeedback(() -> net.minecraft.text.Text.literal("[Nexus] Save failed: could not serialize player data."), false);
								return 0;
							}

							final UUID charId = character.id();
							final UUID playerUuid = player.getUuid();
							final net.minecraft.server.MinecraftServer saveServer = player.server;

							new Thread(() -> {
								try {
									java.nio.file.Path worldDir = saveServer.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
											.toAbsolutePath().normalize();

									// For singleplayer/LAN host: directly copy world → vault, mirroring what
									// CharacterDataManager.saveCurrentCharacter() does on disconnect. This handles
									// UUID-named data/ files (e.g. Cobblemon Pokedex) that VaultSyncPayload skips
									// due to its UUID path filter.
									if (!saveServer.isDedicated()) {
										VaultManager.copyWorldToVault(charId, worldDir, playerUuid);
									}

									java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
									files.putAll(VaultManager.collectEssentialFiles(charId, worldDir, playerUuid));
									files.putAll(VaultManager.collectModFiles(charId, worldDir, playerUuid));
									files.putAll(VaultManager.collectAdvancementsFile(worldDir, playerUuid));
									files.put("playerdata/" + VaultManager.PLAYER_TOKEN + ".dat", nbtBytes);
									final java.util.Map<String, byte[]> snapshot = java.util.Collections.unmodifiableMap(files);
									saveServer.execute(() -> {
										// Re-fetch the live player in case they disconnected during disk I/O
										net.minecraft.server.network.ServerPlayerEntity livePlayer =
												saveServer.getPlayerManager().getPlayer(playerUuid);
										if (livePlayer == null) return;
										if (ServerPlayNetworking.canSend(livePlayer, VaultSyncPayload.ID)) {
											NexusCharactersNetwork.sendToClient(livePlayer, new VaultSyncPayload(charId, snapshot, true));
										}
									});
								} catch (Exception e) {
									LOGGER.warn("[Nexus] Manual save failed for {}: {}", playerUuid, e.getMessage());
								}
							}, "NexusChars-ManualSave").start();

							src.sendFeedback(() -> net.minecraft.text.Text.literal("[Nexus] Saving your character progress..."), false);
							return 1;
						}))
					.then(net.minecraft.server.command.CommandManager.literal("autosaveinterval")
						.requires(src -> src.hasPermissionLevel(2))
						.then(net.minecraft.server.command.CommandManager.argument("seconds",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3600))
							.executes(ctx -> {
								int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
								ADVANCEMENTS_SYNC_INTERVAL_TICKS = seconds * 20;
								FORCE_FULL_SYNC_NEXT_TICK = true; // trigger immediate full sync
								saveServerConfig();
								ctx.getSource().sendFeedback(() ->
										net.minecraft.text.Text.literal("[Nexus] Non-vanilla data autosave interval set to "
												+ seconds + "s (takes effect immediately, persisted)."), true);
								return 1;
							}))));
		});

	}
}