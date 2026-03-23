package net.tompsen.nexuscharacters;

import com.google.gson.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class ModDataScanner {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> IGNORED_FOLDERS = Set.of(
            "region", "DIM1", "DIM-1", "entities", "poi", "dimensions"
    );

    public static NbtCompound scanPlayerModData(ServerPlayerEntity player, boolean fullScan) {
        NbtCompound modData = new NbtCompound();
        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        UUID uuid = player.getUuid();

        NexusCharacters.LOGGER.info("[Nexus-Scanner] Starting scan for {} (Full: {})", player.getName().getString(), fullScan);

        // Always scan vanilla advancements and stats
        scanFolder(worldDir.resolve("advancements"), worldDir, uuid, modData);
        scanFolder(worldDir.resolve("stats"), worldDir, uuid, modData);

        if (fullScan) {
            try (Stream<Path> dirs = Files.list(worldDir)) {
                dirs.filter(Files::isDirectory)
                        .filter(dir -> !IGNORED_FOLDERS.contains(dir.getFileName().toString()))
                        .filter(dir -> !dir.getFileName().toString().equals("advancements"))
                        .filter(dir -> !dir.getFileName().toString().equals("stats"))
                        .forEach(dir -> scanFolder(dir, worldDir, uuid, modData));
            } catch (IOException e) {
                NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to list world directory: {}", e.getMessage());
            }
        }

        return modData;
    }

    private static void scanFolder(Path folder, Path worldDir, UUID uuid, NbtCompound modData) {
        if (!Files.exists(folder)) return;
        String uuidStr = uuid.toString();
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> {
                        String relPath = worldDir.relativize(file).toString().replace("\\", "/");
                        return relPath.contains(uuidStr);
                    })
                    .forEach(file -> {
                        try {
                            String key = worldDir.relativize(file).toString().replace("\\", "/");
                            byte[] bytes = Files.readAllBytes(file);
                            modData.putByteArray(key, bytes);
                            NexusCharacters.LOGGER.info("[Nexus-Scanner] Captured: {} ({} bytes)", key, bytes.length);
                        } catch (IOException e) {
                            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to read file: {}", file);
                        }
                    });
        } catch (IOException e) {
            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to walk folder {}: {}", folder, e.getMessage());
        }
    }

    public static void restorePlayerModData(ServerPlayerEntity player, NbtCompound modData) {
        if (modData.isEmpty()) {
            NexusCharacters.LOGGER.info("[Nexus-Scanner] No mod data to restore for {}", player.getName().getString());
            return;
        }

        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        String currentUuid = player.getUuid().toString();

        NexusCharacters.LOGGER.info("[Nexus-Scanner] Restoring {} mod files for {}", modData.getKeys().size(), player.getName().getString());

        for (String key : modData.getKeys()) {
            if (key.startsWith("_nexuscharacters:")) continue;
            try {
                byte[] bytes = modData.getByteArray(key);
                String resolvedKey = replaceUuidInPath(key, currentUuid);
                Path target = worldDir.resolve(resolvedKey);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                NexusCharacters.LOGGER.info("[Nexus-Scanner] Restored: {} -> {}", key, resolvedKey);
            } catch (IOException e) {
                NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to restore file {}: {}", key, e.getMessage());
            }
        }
    }

    public static void clearPlayerModData(ServerPlayerEntity player) {
        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        String uuidStr = player.getUuid().toString();

        NexusCharacters.LOGGER.info("[Nexus-Scanner] Wiping all server-side progress for UUID: {}", uuidStr);

        deleteMatchingFiles(worldDir.resolve("advancements"), uuidStr);
        deleteMatchingFiles(worldDir.resolve("stats"), uuidStr);
        deleteMatchingFiles(worldDir.resolve("playerdata"), uuidStr);

        try (Stream<Path> dirs = Files.list(worldDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> {
                        String name = dir.getFileName().toString();
                        return !name.equals("region") && !name.equals("DIM1") && !name.equals("DIM-1");
                    })
                    .forEach(dir -> deleteMatchingFiles(dir, uuidStr));
        } catch (IOException e) {
            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to list world directory during clear: {}", e.getMessage());
        }
    }

    private static void deleteMatchingFiles(Path folder, String uuidStr) {
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.toString().replace("\\", "/");
                        return name.contains(uuidStr);
                    })
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            NexusCharacters.LOGGER.info("[Nexus-Scanner] Deleted: {}", file);
                        } catch (IOException e) {
                            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to delete {}: {}", file, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to walk folder {} during delete: {}", folder, e.getMessage());
        }
    }

    public static NbtCompound loadPlayerNbtFromWorld(ServerPlayerEntity player) {
        Path worldDir = player.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path playerFile = worldDir.resolve("playerdata/" + player.getUuid().toString() + ".dat");
        if (Files.exists(playerFile)) {
            try {
                NexusCharacters.LOGGER.info("[Nexus-Scanner] Reading world player NBT from: {}", playerFile);
                return NbtIo.readCompressed(playerFile, NbtSizeTracker.ofUnlimitedBytes());
            } catch (IOException e) {
                NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to read world player data: {}", e.getMessage());
            }
        }
        return new NbtCompound();
    }

    public static NbtCompound mergeModData(NbtCompound serverData, NbtCompound characterData) {
        NbtCompound merged = new NbtCompound();
        
        // Start with character data
        for (String key : characterData.getKeys()) {
            merged.put(key, characterData.get(key).copy());
        }

        // Merge server data into it
        for (String key : serverData.getKeys()) {
            if (merged.contains(key)) {
                byte[] characterBytes = merged.getByteArray(key);
                byte[] serverBytes = serverData.getByteArray(key);
                byte[] mergedBytes = mergeBytes(key, serverBytes, characterBytes);
                merged.putByteArray(key, mergedBytes);
            } else {
                merged.put(key, serverData.get(key).copy());
            }
        }
        return merged;
    }

    private static byte[] mergeBytes(String path, byte[] serverBytes, byte[] characterBytes) {
        try {
            if (path.endsWith(".json")) {
                String serverJson = new String(serverBytes, StandardCharsets.UTF_8);
                String characterJson = new String(characterBytes, StandardCharsets.UTF_8);
                JsonElement serverEl = JsonParser.parseString(serverJson);
                JsonElement characterEl = JsonParser.parseString(characterJson);
                JsonElement mergedEl = mergeJson(serverEl, characterEl);
                return GSON.toJson(mergedEl).getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith(".dat")) {
                // Try as compressed NBT first
                try (DataInputStream serverIn = new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(serverBytes)));
                     DataInputStream charIn = new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(characterBytes)))) {
                    NbtCompound serverNbt = NbtIo.readCompound(serverIn, NbtSizeTracker.ofUnlimitedBytes());
                    NbtCompound charNbt = NbtIo.readCompound(charIn, NbtSizeTracker.ofUnlimitedBytes());
                    NbtCompound mergedNbt = mergeNbt(serverNbt, charNbt);
                    
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (DataOutputStream dos = new DataOutputStream(new java.util.zip.GZIPOutputStream(out))) {
                        NbtIo.writeCompound(mergedNbt, dos);
                    }
                    return out.toByteArray();
                } catch (Exception e) {
                    // Fallback to uncompressed NBT
                    try (DataInputStream serverIn = new DataInputStream(new ByteArrayInputStream(serverBytes));
                         DataInputStream charIn = new DataInputStream(new ByteArrayInputStream(characterBytes))) {
                        NbtCompound serverNbt = NbtIo.readCompound(serverIn, NbtSizeTracker.ofUnlimitedBytes());
                        NbtCompound charNbt = NbtIo.readCompound(charIn, NbtSizeTracker.ofUnlimitedBytes());
                        NbtCompound mergedNbt = mergeNbt(serverNbt, charNbt);
                        
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        try (DataOutputStream dos = new DataOutputStream(out)) {
                            NbtIo.writeCompound(mergedNbt, dos);
                        }
                        return out.toByteArray();
                    }
                }
            }
        } catch (Exception e) {
            NexusCharacters.LOGGER.warn("[Nexus-Scanner] Failed to merge file {}: {}", path, e.getMessage());
        }
        // Fallback: prefer server data for conflicts we can't merge
        return serverBytes;
    }

    private static JsonElement mergeJson(JsonElement server, JsonElement character) {
        if (server.isJsonObject() && character.isJsonObject()) {
            JsonObject sObj = server.getAsJsonObject();
            JsonObject cObj = character.getAsJsonObject();
            JsonObject merged = new JsonObject();
            
            // Add all from server
            for (String key : sObj.keySet()) {
                merged.add(key, sObj.get(key));
            }
            // Merge character into it
            for (String key : cObj.keySet()) {
                if (merged.has(key)) {
                    merged.add(key, mergeJson(merged.get(key), cObj.get(key)));
                } else {
                    merged.add(key, cObj.get(key));
                }
            }
            return merged;
        } else if (server.isJsonArray() && character.isJsonArray()) {
            JsonArray sArr = server.getAsJsonArray();
            JsonArray cArr = character.getAsJsonArray();
            JsonArray merged = new JsonArray();
            Set<String> existing = new java.util.HashSet<>();
            for (JsonElement e : sArr) {
                merged.add(e);
                existing.add(e.toString());
            }
            for (JsonElement e : cArr) {
                if (!existing.contains(e.toString())) {
                    merged.add(e);
                }
            }
            return merged;
        }
        // If they are primitives or types don't match, prefer server
        return server;
    }

    private static NbtCompound mergeNbt(NbtCompound server, NbtCompound character) {
        NbtCompound merged = server.copy();
        for (String key : character.getKeys()) {
            if (merged.contains(key)) {
                // If both are compounds, recurse
                if (merged.get(key) instanceof NbtCompound sComp && character.get(key) instanceof NbtCompound cComp) {
                    merged.put(key, mergeNbt(sComp, cComp));
                } else {
                    // Otherwise prefer server (already in merged)
                }
            } else {
                merged.put(key, character.get(key).copy());
            }
        }
        return merged;
    }

    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private static String replaceUuidInPath(String path, String newUuid) {
        return UUID_PATTERN.matcher(path).replaceAll(newUuid);
    }
}

