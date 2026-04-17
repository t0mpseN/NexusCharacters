package net.tompsen.nexuscharacters;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * Owns everything under .minecraft/nexuscharacters/vaults/.
 */
public class VaultManager {

    // ── Constants ────────────────────────────────────────────────────────────

    public static final String PLAYER_TOKEN = "__player__";

    private static final Pattern UUID_PAT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> IGNORED_WORLD_DIRS = Set.of(
            "region", "DIM1", "DIM-1", "entities", "poi", "dimensions", "level.dat",
            "level.dat_old", "session.lock", "icon.png", "resources.zip"
    );

    /** File suffixes that are mod-internal backups and should not be vaulted. */
    private static boolean isBackupFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".old") || name.endsWith(".bak") || name.endsWith(".tmp");
    }

    /** Zero-byte files are uninitialized placeholders that crash mods like Biolith when restored. */
    private static boolean isEmptyFile(Path file) {
        try { return Files.size(file) == 0; } catch (IOException ignored) { return false; }
    }

    private static final Path VAULTS_DIR =
            FabricLoader.getInstance().getGameDir().resolve("nexuscharacters/vaults");

    // ── NBT cache (UI-only) ───────────────────

    private static final Map<UUID, NbtCompound> NBT_CACHE = new HashMap<>();
    private static final Map<UUID, List<String>> MISSING_MODS_CACHE = new HashMap<>();

    // ── Path resolution ──────────────────────────────────────────────────────

    public static Path getVaultDir(UUID characterId) {
        return VAULTS_DIR.resolve(characterId.toString());
    }

    public static String worldToVault(String rel) {
        return UUID_PAT.matcher(rel).replaceAll(PLAYER_TOKEN);
    }

    public static String vaultToWorld(String rel, UUID playerUuid) {
        return rel.replace(PLAYER_TOKEN, playerUuid.toString());
    }

    /**
     * NBT int-array tag header for a field named "UUID" with 4 elements.
     * Used to locate and replace the binary UUID stored inside playerdata NBT.
     * Format: tag_type(0x0b) + name_len(0x0004) + "UUID" + array_len(0x00000004)
     */
    private static final byte[] NBT_UUID_HEADER = {
        0x0b, 0x00, 0x04, 0x55, 0x55, 0x49, 0x44, 0x00, 0x00, 0x00, 0x04
    };
    /** 16-byte sentinel used in place of the binary UUID in vault files. */
    private static final byte[] NBT_UUID_TOKEN = new byte[16]; // all zeros

    private static byte[] uuidToBinaryBigEndian(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] b = new byte[16];
        for (int i = 7; i >= 0; i--) { b[i] = (byte)(msb & 0xFF); msb >>= 8; }
        for (int i = 15; i >= 8; i--) { b[i] = (byte)(lsb & 0xFF); lsb >>= 8; }
        return b;
    }

    private static byte[] rewriteContentToVault(byte[] content, UUID playerUuid) {
        // Replace string form: "a289ec68-645c-..." → "__player__"
        byte[] uuidStrBytes = playerUuid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tokenStrBytes = PLAYER_TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        content = replaceAll(content, uuidStrBytes, tokenStrBytes);
        // Replace binary NBT int-array form (inside playerdata NBT) with zero sentinel
        content = replaceNbtUuid(content, uuidToBinaryBigEndian(playerUuid), NBT_UUID_TOKEN);
        return content;
    }

    private static byte[] rewriteContentFromVault(byte[] content, UUID playerUuid) {
        // Replace string form: "__player__" → "a289ec68-645c-..."
        byte[] tokenStrBytes = PLAYER_TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] uuidStrBytes = playerUuid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        content = replaceAll(content, tokenStrBytes, uuidStrBytes);
        // Replace binary NBT int-array sentinel with the target player's UUID
        content = replaceNbtUuid(content, NBT_UUID_TOKEN, uuidToBinaryBigEndian(playerUuid));
        return content;
    }

    /**
     * Replaces the 16-byte UUID payload that follows an NBT int-array "UUID" tag header.
     * Only replaces occurrences where {@code fromUuid} matches; leaves all other data intact.
     */
    private static byte[] replaceNbtUuid(byte[] src, byte[] fromUuid, byte[] toUuid) {
        int headerLen = NBT_UUID_HEADER.length; // 11 bytes
        int limit = src.length - headerLen - 16;
        if (limit < 0) return src;
        ByteArrayOutputStream out = null; // lazy-init only if a replacement is needed
        int i = 0;
        while (i <= limit) {
            if (matches(src, i, NBT_UUID_HEADER) && matches(src, i + headerLen, fromUuid)) {
                if (out == null) {
                    out = new ByteArrayOutputStream(src.length);
                    out.write(src, 0, i);
                }
                out.write(NBT_UUID_HEADER, 0, headerLen);
                out.write(toUuid, 0, 16);
                i += headerLen + 16;
            } else {
                if (out != null) out.write(src[i]);
                i++;
            }
        }
        if (out == null) return src;
        if (i < src.length) out.write(src, i, src.length - i);
        return out.toByteArray();
    }

    private static byte[] replaceAll(byte[] src, byte[] from, byte[] to) {
        if (from.length == 0 || src.length < from.length) return src;
        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        int i = 0;
        while (i <= src.length - from.length) {
            if (matches(src, i, from)) {
                out.write(to, 0, to.length);
                i += from.length;
            } else {
                out.write(src[i++]);
            }
        }
        while (i < src.length) out.write(src[i++]);
        byte[] result = out.toByteArray();
        return result.length == src.length ? src : result;
    }

    private static boolean matches(byte[] src, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (src[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    public static final String SHARD_TOKEN = "__shard__";
    private static final java.util.regex.Pattern SHARD_PAT =
            java.util.regex.Pattern.compile("((?:[^/]+/)*)([0-9a-fA-F]{2})(/.+)");

    public static String worldShardToVault(String worldRel) {
        java.util.regex.Matcher m = SHARD_PAT.matcher(worldRel);
        if (!m.matches()) return worldRel;
        return m.group(1) + SHARD_TOKEN + m.group(3);
    }

    public static boolean isMatchingShard(String worldRel, UUID playerUuid) {
        java.util.regex.Matcher m = SHARD_PAT.matcher(worldRel);
        if (!m.matches()) return true;
        String shardDir = m.group(2).toLowerCase();
        String uuidNoHyphens = playerUuid.toString().replace("-", "").toLowerCase();
        String shard0 = uuidNoHyphens.substring(0, 2);
        String shard1 = uuidNoHyphens.substring(1, 3);
        return shardDir.equals(shard0) || shardDir.equals(shard1);
    }

    public static List<String> vaultToWorldAll(String vaultRel, UUID playerUuid) {
        String uuidStr = playerUuid.toString();
        String uuidNoHyphens = uuidStr.replace("-", "");
        if (vaultRel.contains(SHARD_TOKEN)) {
            String withUuid = vaultRel.replace(PLAYER_TOKEN, uuidStr);
            Set<String> seen = new LinkedHashSet<>();
            seen.add(withUuid.replace(SHARD_TOKEN, uuidNoHyphens.substring(0, 2)));
            seen.add(withUuid.replace(SHARD_TOKEN, uuidNoHyphens.substring(1, 3)));
            return new ArrayList<>(seen);
        }
        java.util.regex.Matcher m = SHARD_PAT.matcher(vaultRel);
        if (m.matches()) {
            String prefix = m.group(1);
            String suffix = m.group(3).replace(PLAYER_TOKEN, uuidStr);
            Set<String> seen = new LinkedHashSet<>();
            seen.add(prefix + uuidNoHyphens.substring(0, 2) + suffix);
            seen.add(prefix + uuidNoHyphens.substring(1, 3) + suffix);
            return new ArrayList<>(seen);
        }
        return List.of(vaultRel.replace(PLAYER_TOKEN, uuidStr));
    }

    public static void purgeStaleUuidFiles(UUID characterId) {
        Path vaultDir = getVaultDir(characterId);
        if (!Files.exists(vaultDir)) return;
        try (Stream<Path> walk = Files.walk(vaultDir)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> UUID_PAT.matcher(vaultDir.relativize(f).toString().replace("\\", "/")).find())
                .forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
        } catch (IOException e) {
            NexusCharacters.LOGGER.warn("[VaultManager] purgeStaleUuidFiles failed: {}", e.getMessage());
        }
    }

    // ── Vault lifecycle ──────────────────────────────────────────────────────

    public static void createVault(UUID characterId) {
        try { Files.createDirectories(getVaultDir(characterId)); } catch (IOException ignored) {}
    }

    public static void deleteVault(UUID characterId) {
        Path vaultDir = getVaultDir(characterId);
        if (!Files.exists(vaultDir)) return;
        try (Stream<Path> walk = Files.walk(vaultDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    /**
     * If this character's vault has no playerdata yet, looks for a pre-existing player in the
     * world whose username matches the character name and imports their data into the vault.
     * Resolution order:
     *   1. usercache.json (online-mode servers — exact name match)
     *   2. Offline UUID: {@code UUID.nameUUIDFromBytes("OfflinePlayer:<name>".getBytes("UTF-8"))}
     *      (singleplayer and offline-mode servers)
     *
     * @return true if legacy data was found and imported
     */
    /**
     * Marker file written to a world dir the first time NexusCharacters stages a vault there.
     * Its presence means the world is already managed by NexusCharacters, so any playerdata
     * files found in it were written by a character — not by a pre-NexusCharacters session.
     * Legacy import must be skipped for managed worlds to prevent new characters from
     * inheriting items and progress from whoever last played in that world.
     */
    private static final String NEXUS_MARKER = "nexuscharacters.marker";

    /** Writes the NexusCharacters marker to the world dir if not already present. */
    public static void writeWorldMarker(Path worldDir) {
        Path marker = worldDir.resolve(NEXUS_MARKER);
        if (!Files.exists(marker)) {
            try { Files.writeString(marker, "managed"); } catch (IOException ignored) {}
        }
    }

    /** Returns true if this world has been managed by NexusCharacters before. */
    public static boolean isWorldManaged(Path worldDir) {
        return Files.exists(worldDir.resolve(NEXUS_MARKER));
    }

    public static boolean importLegacyDataIfNeeded(UUID characterId, String characterName, Path worldDir) {
        Path vaultPlayerData = getVaultDir(characterId).resolve("playerdata/__player__.dat");
        if (Files.exists(vaultPlayerData)) return false; // vault already has data

        // If NexusCharacters has already managed this world, any playerdata here was written
        // by a character — not a legacy pre-NexusCharacters session. Skip import to prevent
        // new characters from inheriting data from whoever last played in this world.
        if (isWorldManaged(worldDir)) return false;

        Path playerDataDir = worldDir.resolve("playerdata");
        if (!Files.isDirectory(playerDataDir)) return false;

        // Try to resolve the legacy UUID for this character name
        UUID legacyUuid = resolveLegacyUuid(characterName, worldDir);
        if (legacyUuid == null) return false;

        // Check if a playerdata file exists for that UUID
        Path legacyFile = playerDataDir.resolve(legacyUuid + ".dat");
        if (!Files.exists(legacyFile)) return false;

        NexusCharacters.LOGGER.info("[Nexus] Importing legacy data for character '{}' from uuid {} into vault {}",
                characterName, legacyUuid, characterId);

        // Copy all per-player files from the world into the vault, tokenizing the legacy UUID
        copyWorldToVault(characterId, worldDir, legacyUuid);
        return true;
    }

    /**
     * Resolves a legacy player UUID from a username.
     * Checks usercache.json first, then falls back to the offline UUID formula.
     * Returns null only if the resulting UUID has no matching playerdata file.
     */
    private static UUID resolveLegacyUuid(String name, Path worldDir) {
        // 1. usercache.json lookup (online mode)
        Path usercache = FabricLoader.getInstance().getGameDir().resolve("usercache.json");
        if (Files.exists(usercache)) {
            try {
                JsonArray arr = JsonParser.parseString(Files.readString(usercache)).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    if (name.equalsIgnoreCase(obj.get("name").getAsString())) {
                        return UUID.fromString(obj.get("uuid").getAsString());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Offline UUID: UUID.nameUUIDFromBytes("OfflinePlayer:<name>")
        try {
            byte[] bytes = ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            UUID offlineUuid = UUID.nameUUIDFromBytes(bytes);
            Path offlineFile = worldDir.resolve("playerdata/" + offlineUuid + ".dat");
            if (Files.exists(offlineFile)) return offlineUuid;
        } catch (Exception ignored) {}

        return null;
    }

    public static void copyWorldToVault(UUID characterId, Path worldDir, UUID playerUuid) {
        Path vaultDir = getVaultDir(characterId);
        String uuidStr = playerUuid.toString();
        try { Files.createDirectories(vaultDir); } catch (IOException ignored) { return; }

        // Per-player directories: only copy files that contain the player UUID in their path.
        List<Path> playerDirs = new ArrayList<>();
        for (String d : new String[]{"playerdata", "advancements", "stats"}) {
            Path p = worldDir.resolve(d);
            if (Files.isDirectory(p)) playerDirs.add(p);
        }
        try (Stream<Path> top = Files.list(worldDir)) {
            top.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        return !IGNORED_WORLD_DIRS.contains(n)
                                && !n.equals("playerdata")
                                && !n.equals("advancements")
                                && !n.equals("stats")
                                && !n.equals("data");
                    })
                    .forEach(playerDirs::add);
        } catch (IOException ignored) {}

        for (Path dir : playerDirs) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> !isBackupFile(f))
                        .filter(f -> !isEmptyFile(f))
                        .filter(f -> f.toString().replace("\\", "/").contains(uuidStr))
                        .forEach(file -> {
                            String rel = worldDir.relativize(file).toString().replace("\\", "/");
                            if (!isMatchingShard(rel, playerUuid)) return;
                            String vaultRel = worldShardToVault(worldToVault(rel));
                            Path target = vaultDir.resolve(vaultRel);
                            try {
                                Files.createDirectories(target.getParent());
                                byte[] content = Files.readAllBytes(file);
                                content = rewriteContentToVault(content, playerUuid);
                                Files.write(target, content);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }

        // data/ directory: save ALL files without UUID filtering.
        // Mods like Sophisticated Backpacks store per-item inventories here keyed by
        // item UUID, not player UUID — so the player UUID filter would miss them entirely.
        // Zero-byte files are skipped: they are uninitialized placeholders (e.g. biolith_overworld_state.dat)
        // that crash mods like Biolith when restored via PersistentStateManager.
        Path dataDir = worldDir.resolve("data");
        if (Files.isDirectory(dataDir)) {
            try (Stream<Path> walk = Files.walk(dataDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> !isBackupFile(f))
                        .filter(f -> !isEmptyFile(f))
                        .forEach(file -> {
                            String rel = worldDir.relativize(file).toString().replace("\\", "/");
                            Path target = vaultDir.resolve(rel);
                            try {
                                Files.createDirectories(target.getParent());
                                Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }

        NBT_CACHE.remove(characterId);
    }

    public static void copyVaultToWorld(UUID characterId, Path worldDir, UUID playerUuid) {
        Path vaultDir = getVaultDir(characterId);
        if (!Files.exists(vaultDir)) return;

        purgeStaleUuidFiles(characterId);

        Map<String, byte[]> vaultFiles = new LinkedHashMap<>();
        List<Path> oldFormat = new ArrayList<>();
        List<Path> newFormat = new ArrayList<>();
        // Track top-level vault dirs whose files are NOT player-UUID-keyed.
        // These directories store data under mod-internal keys (e.g. Cobblemon's pokemon/).
        // Stale files from a previous character must be wiped from the world dir before
        // writing, because clearWorldFiles() only removes files that contain a UUID in their
        // path and will miss them entirely.
        Set<String> nonUuidModDirs = new LinkedHashSet<>();
        Set<String> standardDirs = Set.of("playerdata", "advancements", "stats", "data", "world_positions.json");

        try (Stream<Path> walk = Files.walk(vaultDir)) {
            walk.filter(Files::isRegularFile).forEach(f -> {
                String rel = vaultDir.relativize(f).toString().replace("\\", "/");
                if (rel.contains(SHARD_TOKEN)) newFormat.add(f);
                else oldFormat.add(f);

                // Collect top-level dir names whose vault paths contain no player token
                // (meaning they're not keyed by player UUID and won't be cleared by clearWorldFiles).
                String topLevel = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : "";
                if (!topLevel.isEmpty()
                        && !standardDirs.contains(topLevel)
                        && !rel.contains(PLAYER_TOKEN)
                        && !rel.contains(SHARD_TOKEN)) {
                    nonUuidModDirs.add(topLevel);
                }
            });
        } catch (IOException ignored) { return; }

        // Wipe stale non-UUID mod directories in the world before writing vault contents.
        // This prevents data from a previous character bleeding into the newly loaded one.
        for (String dirName : nonUuidModDirs) {
            Path worldModDir = worldDir.resolve(dirName);
            if (Files.isDirectory(worldModDir)) {
                try (Stream<Path> wipe = Files.walk(worldModDir)) {
                    wipe.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
                } catch (IOException ignored) {}
            }
        }

        // data/ files that ARE in the vault are written below (overwriting world copies).
        // We do NOT wipe data/ files that are absent from the vault: they may be shared
        // world-level state (e.g. Cobblemon Pokédex, raid data) that should persist across
        // character switches. Per-character mod state stored in data/ (e.g. Prominent talents)
        // is handled correctly because copyWorldToVault captures it and copyVaultToWorld
        // overwrites it — the evictPersistentStateCache call forces mods to re-read from disk.

        for (List<Path> batch : List.of(oldFormat, newFormat)) {
            for (Path file : batch) {
                String vaultRel = vaultDir.relativize(file).toString().replace("\\", "/");
                if (vaultRel.startsWith("world_positions")) continue;
                try {
                    byte[] content = Files.readAllBytes(file);
                    content = rewriteContentFromVault(content, playerUuid);
                    for (String worldRel : vaultToWorldAll(vaultRel, playerUuid)) {
                        vaultFiles.put(worldRel, content);
                    }
                } catch (IOException ignored) {}
            }
        }

        for (Map.Entry<String, byte[]> entry : vaultFiles.entrySet()) {
            Path target = worldDir.resolve(entry.getKey());
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            } catch (IOException ignored) {}
        }

        // Mark the world as managed so future character switches skip legacy import.
        writeWorldMarker(worldDir);
    }

    public static void clearWorldFiles(Path worldDir, UUID playerUuid) {
        List<Path> dirsToCheck = new ArrayList<>();
        for (String d : new String[]{"playerdata", "advancements", "stats"}) {
            Path p = worldDir.resolve(d);
            if (Files.isDirectory(p)) dirsToCheck.add(p);
        }
        try (Stream<Path> top = Files.list(worldDir)) {
            top.filter(Files::isDirectory)
                    .filter(d -> !IGNORED_WORLD_DIRS.contains(d.getFileName().toString()))
                    .forEach(dirsToCheck::add);
        } catch (IOException ignored) {}

        for (Path dir : dirsToCheck) {
            if (!Files.exists(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> UUID_PAT.matcher(f.toString().replace("\\", "/")).find())
                        .forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
            } catch (IOException ignored) {}
        }
    }

    // ── World positions ──────────────────────────────────────────────────────

    public record WorldPos(double x, double y, double z, float yaw, float pitch, String dimension) {}

    public static Optional<WorldPos> getWorldPosition(UUID characterId, String worldId) {
        Path posFile = getVaultDir(characterId).resolve("world_positions.json");
        if (!Files.exists(posFile)) return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(posFile)).getAsJsonObject();
            if (!root.has(worldId)) return Optional.empty();
            JsonObject p = root.getAsJsonObject(worldId);
            return Optional.of(new WorldPos(
                    p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                    p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat(),
                    p.has("dimension") ? p.get("dimension").getAsString() : null
            ));
        } catch (Exception e) { return Optional.empty(); }
    }

    public static Optional<WorldPos> getAnyPositionForWorld(UUID characterId, String hostSaveMatch) {
        Path posFile = getVaultDir(characterId).resolve("world_positions.json");
        if (!Files.exists(posFile)) return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(posFile)).getAsJsonObject();
            for (String key : root.keySet()) {
                if (key.startsWith(hostSaveMatch)) {
                    JsonObject p = root.getAsJsonObject(key);
                    return Optional.of(new WorldPos(
                            p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                            p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat(),
                            p.has("dimension") ? p.get("dimension").getAsString() : null
                    ));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static void saveWorldPosition(UUID characterId, String worldId,
                                         double x, double y, double z, float yaw, float pitch) {
        Path posFile = getVaultDir(characterId).resolve("world_positions.json");
        JsonObject root = new JsonObject();
        if (Files.exists(posFile)) {
            try { root = JsonParser.parseString(Files.readString(posFile)).getAsJsonObject().deepCopy(); }
            catch (Exception ignored) {}
        }
        JsonObject p = new JsonObject();
        p.addProperty("x", x); p.addProperty("y", y); p.addProperty("z", z);
        p.addProperty("yaw", yaw); p.addProperty("pitch", pitch);
        
        // Extract dimension from worldId if possible
        String[] parts = worldId.split("\\|");
        if (parts.length >= 4) {
            p.addProperty("dimension", parts[3]);
        }
        
        root.add(worldId, p);
        try {
            Files.createDirectories(posFile.getParent());
            Files.writeString(posFile, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException ignored) {}
    }

    // ── UI data readers ─────────────────────────────────────────────

    public static NbtCompound readPlayerNbt(UUID characterId) {
        return NBT_CACHE.computeIfAbsent(characterId, id -> {
            Path file = getVaultDir(id).resolve("playerdata/__player__.dat");
            if (!Files.exists(file)) return new NbtCompound();
            try { return NbtIo.readCompressed(file.toFile()); }
            catch (IOException e) { return new NbtCompound(); }
        });
    }

    public static String readStatsJson(UUID characterId) {
        Path file = getVaultDir(characterId).resolve("stats/__player__.json");
        if (!Files.exists(file)) return null;
        try { return Files.readString(file); } catch (IOException e) { return null; }
    }

    public static String readAdvancementsJson(UUID characterId) {
        Path file = getVaultDir(characterId).resolve("advancements/__player__.json");
        if (!Files.exists(file)) return null;
        try { return Files.readString(file); } catch (IOException e) { return null; }
    }

    public static void invalidateCache(UUID characterId) {
        NBT_CACHE.remove(characterId);
        MISSING_MODS_CACHE.remove(characterId);
    }
    public static void invalidateAll() {
        NBT_CACHE.clear();
        MISSING_MODS_CACHE.clear();
    }

    /**
     * Returns mod directory names found in the vault that don't correspond to any
     * currently loaded mod. These represent mods whose data was saved with this
     * character but may not be installed in the current instance.
     * Results are cached until the vault cache is invalidated.
     *
     * <p>Matching is intentionally lenient: a vault directory is considered "covered"
     * by a loaded mod if the mod ID or normalized display name matches the directory
     * name (exact, prefix, suffix, or substring), requiring at least 4 characters for
     * fuzzy checks to avoid false positives from very short mod IDs.
     */
    public static List<String> detectPotentiallyMissingMods(UUID characterId) {
        return MISSING_MODS_CACHE.computeIfAbsent(characterId, id -> {
            Path vaultDir = getVaultDir(id);
            if (!Files.isDirectory(vaultDir)) return List.of();

            Set<String> standardDirs = Set.of("playerdata", "advancements", "stats", "data", "world_positions.json");

            // Build a set of mod ID + display-name tokens for fuzzy matching.
            Set<String> loadedModIds = buildLoadedModTokens();

            // Collect dir names that appear in 2+ world saves. This handles mods like Cobblemon
            // whose folder name has no lexical relationship to the mod ID (e.g. "pokemon/").
            // Requiring 2+ worlds filters out dirs deposited by a single character from another
            // modpack that was loaded into one world here — those only appear once.
            Set<String> worldOnlyDirNames = buildWorldDirNamesExcludingVault();

            List<String> missing = new ArrayList<>();
            try (Stream<Path> top = Files.list(vaultDir)) {
                top.filter(Files::isDirectory)
                   .map(p -> p.getFileName().toString())
                   .filter(n -> !standardDirs.contains(n))
                   .forEach(dirName -> {
                       if (isCoveredByLoadedMod(dirName.toLowerCase(), loadedModIds)) return;
                       if (worldOnlyDirNames.contains(dirName.toLowerCase())) return;
                       missing.add(dirName);
                   });
            } catch (IOException ignored) {}
            return List.copyOf(missing);
        });
    }

    /**
     * Scans all world saves and collects top-level subdirectory names (excluding
     * standard Minecraft dirs) that appear in MORE THAN ONE world save.
     *
     * <p>A dir name found in only one world was likely deposited there by a single
     * character (e.g. an FTB character loaded into a Cobblemon world). A dir name
     * present in multiple worlds is almost certainly created by a mod that is
     * installed in this game instance (e.g. Cobblemon's {@code pokemon/} directory).
     */
    private static Set<String> buildWorldDirNamesExcludingVault() {
        Set<String> vanillaDirs = Set.of(
                "playerdata", "advancements", "stats", "data",
                "region", "entities", "poi", "dimensions",
                "DIM1", "DIM-1", "level.dat", "level.dat_old",
                "session.lock", "icon.png", "resources.zip"
        );

        // Count how many distinct worlds contain each dir name.
        Map<String, Integer> worldCount = new HashMap<>();
        Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
        if (!Files.isDirectory(savesDir)) return Set.of();
        try (Stream<Path> worlds = Files.list(savesDir)) {
            worlds.filter(Files::isDirectory).forEach(worldDir -> {
                try (Stream<Path> contents = Files.list(worldDir)) {
                    contents.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .filter(n -> !vanillaDirs.contains(n))
                            .map(String::toLowerCase)
                            .distinct()
                            .forEach(n -> worldCount.merge(n, 1, Integer::sum));
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}

        // Only treat a dir name as "installed mod" if it appears in at least 2 worlds.
        // Single-world occurrences are likely character-deposited data from another modpack.
        Set<String> known = new HashSet<>();
        worldCount.forEach((name, count) -> {
            if (count >= 2) known.add(name);
        });
        return known;
    }

    /**
     * Returns true if {@code dirName} is plausibly owned by one of the loaded mods.
     * Four matching strategies are tried, requiring at least 4 characters to match to
     * avoid false positives from very short mod IDs:
     * <ol>
     *   <li><b>Exact match</b>: mod {@code ironchests} → dir {@code ironchests/}.</li>
     *   <li><b>Dir starts with mod ID</b>: mod {@code cobblemon} → dir {@code cobblemonplayerdata/}.</li>
     *   <li><b>Mod ID starts with dir name</b>: dir {@code nether/} → mod {@code netherintegration}.</li>
     *   <li><b>Mod display name contains dir name</b>: mod display name "Cobblemon" contains "pokemon"
     *       when normalised — covers cases where the dir is a generic noun the mod is named after.</li>
     * </ol>
     */
    private static boolean isCoveredByLoadedMod(String dirNameLower, Set<String> loadedModIds) {
        // Minimum length for prefix/substring checks — prevents trivial overlaps
        // (e.g. dir "a" matching mod "a-lot-of-mods"). Exact matching always applies.
        final int MIN = 4;
        for (String modId : loadedModIds) {
            if (modId.equals(dirNameLower)) return true;  // exact — always checked
            if (dirNameLower.length() < MIN) continue;    // skip fuzzy checks for very short names
            if (dirNameLower.startsWith(modId) && modId.length() >= MIN) return true;
            if (modId.startsWith(dirNameLower)) return true;
            if (modId.contains(dirNameLower)) return true;
        }
        return false;
    }

    /**
     * Builds the set used by {@link #isCoveredByLoadedMod}: mod IDs plus normalised
     * display names (lowercased, spaces/hyphens stripped to catch "Cobblemon" → "cobblemon").
     */
    private static Set<String> buildLoadedModTokens() {
        Set<String> tokens = new HashSet<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId().toLowerCase();
            tokens.add(id);
            // Also add the display name normalised: lower-case, strip spaces/hyphens/underscores
            String name = mod.getMetadata().getName().toLowerCase()
                    .replaceAll("[\\s\\-_]+", "");
            if (!name.isEmpty()) tokens.add(name);
        }
        return tokens;
    }

    public static byte[] serializePlayerNbt(net.minecraft.server.network.ServerPlayerEntity player) throws IOException {
        NbtCompound nbt = new NbtCompound();
        player.writeNbt(nbt);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, baos);
        return baos.toByteArray();
    }

    /**
     * Serializes the player NBT from memory and replaces the player's UUID with
     * the {@code __player__} token so it can be stored safely in the vault.
     * Use this instead of {@link #serializePlayerNbt} whenever the result will be
     * written to the vault (tick sync, disconnect sync, etc.).
     */
    public static byte[] serializePlayerNbtTokenized(net.minecraft.server.network.ServerPlayerEntity player) throws IOException {
        byte[] raw = serializePlayerNbt(player);
        return rewriteContentToVault(raw, player.getUuid());
    }

    // ── Sync collectors ─────────────────────────────────────────────

    public static Map<String, byte[]> collectEssentialFiles(UUID characterId, Path worldDir, UUID playerUuid) {
        return collectFromDirs(worldDir, playerUuid, List.of("playerdata", "stats"));
    }

    public static Map<String, byte[]> collectModFiles(UUID characterId, Path worldDir, UUID playerUuid) {
        // UUID-filtered mod directories (per-player files named with the player UUID)
        List<String> modDirs = new ArrayList<>();
        try (Stream<Path> top = Files.list(worldDir)) {
            top.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        return !IGNORED_WORLD_DIRS.contains(n)
                                && !n.equals("playerdata")
                                && !n.equals("advancements")
                                && !n.equals("stats")
                                && !n.equals("data");
                    })
                    .forEach(d -> modDirs.add(d.getFileName().toString()));
        } catch (IOException ignored) {}
        Map<String, byte[]> result = new java.util.LinkedHashMap<>(collectFromDirs(worldDir, playerUuid, modDirs));

        // data/ directory: collect ALL files without UUID filtering.
        // Zero-byte files are skipped: they are uninitialized placeholders that crash mods
        // like Biolith when restored (arraycopy: length -1 in PersistentStateManager).
        Path dataDir = worldDir.resolve("data");
        if (Files.isDirectory(dataDir)) {
            try (Stream<Path> walk = Files.walk(dataDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> !isBackupFile(f))
                        .filter(f -> !isEmptyFile(f))
                        .forEach(file -> {
                            String rel = worldDir.relativize(file).toString().replace("\\", "/");
                            try {
                                result.put(rel, Files.readAllBytes(file));
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
        return result;
    }

    private static Map<String, byte[]> collectFromDirs(Path worldDir, UUID playerUuid, List<String> dirNames) {
        String uuidStr = playerUuid.toString();
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String dirName : dirNames) {
            Path dir = worldDir.resolve(dirName);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> !isBackupFile(f))
                        .filter(f -> !isEmptyFile(f))
                        .filter(f -> f.toString().replace("\\", "/").contains(uuidStr))
                        .forEach(file -> {
                            String rel = worldDir.relativize(file).toString().replace("\\", "/");
                            if (!isMatchingShard(rel, playerUuid)) return;
                            String vaultRel = worldShardToVault(worldToVault(rel));
                            try {
                                byte[] content = Files.readAllBytes(file);
                                content = rewriteContentToVault(content, playerUuid);
                                result.put(vaultRel, content);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
        return result;
    }

    public static Map<String, byte[]> collectAdvancementsFile(Path worldDir, UUID playerUuid) {
        String uuidStr = playerUuid.toString();
        Path advFile = worldDir.resolve("advancements").resolve(uuidStr + ".json");
        if (!Files.exists(advFile)) return Collections.emptyMap();
        try {
            String vaultRel = worldToVault("advancements/" + uuidStr + ".json");
            byte[] content = Files.readAllBytes(advFile);
            content = rewriteContentToVault(content, playerUuid);
            return Collections.singletonMap(vaultRel, content);
        } catch (IOException e) { return Collections.emptyMap(); }
    }

    public static void applyVaultSync(UUID characterId, Map<String, byte[]> files) {
        Path vaultDir = getVaultDir(characterId);
        try { Files.createDirectories(vaultDir); } catch (IOException ignored) {}
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path target = vaultDir.resolve(entry.getKey()).normalize();
            if (!target.startsWith(vaultDir.normalize())) continue;
            if (isBackupFile(target)) continue;
            if (UUID_PAT.matcher(entry.getKey()).find()) continue;
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
            } catch (IOException ignored) {}
        }
        invalidateCache(characterId);
    }

    // ── Zip / Unzip ──────────────────────────────────

    public static byte[] zipVault(UUID characterId) throws IOException {
        Path vaultDir = getVaultDir(characterId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (Files.exists(vaultDir)) {
                try (Stream<Path> walk = Files.walk(vaultDir)) {
                    for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)
                                                          .filter(f -> !isBackupFile(f))
                                                          .filter(f -> !isEmptyFile(f))::iterator) {
                        String entry = vaultDir.relativize(file).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entry));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    public static void unzipToVault(UUID characterId, byte[] zipData) throws IOException {
        unzipToVault(characterId, zipData, false);
    }

    /**
     * Extracts a vault ZIP, optionally preserving server-authoritative files.
     *
     * @param preserveServerFiles if true, files that are managed exclusively
     *   server-side (world_positions.json) are NOT overwritten.
     */
    public static void unzipToVault(UUID characterId, byte[] zipData, boolean preserveServerFiles) throws IOException {
        Path vaultDir = getVaultDir(characterId);
        Files.createDirectories(vaultDir);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                Path target = vaultDir.resolve(name).normalize();
                if (!target.startsWith(vaultDir.normalize())) continue;
                if (isBackupFile(target)) { zis.closeEntry(); continue; }
                if (UUID_PAT.matcher(name).find()) { zis.closeEntry(); continue; }
                // Never overwrite server-authoritative position data with client data
                if (preserveServerFiles && name.equals("world_positions.json")) { zis.closeEntry(); continue; }
                byte[] data = zis.readAllBytes();
                zis.closeEntry();
                // Skip zero-byte entries: uninitialized placeholders that crash mods like Biolith
                if (data.length == 0) continue;
                Files.createDirectories(target.getParent());
                Files.write(target, data);
            }
        }
        invalidateCache(characterId);
    }
}