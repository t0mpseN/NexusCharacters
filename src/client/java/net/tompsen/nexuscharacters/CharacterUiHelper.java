package net.tompsen.nexuscharacters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CharacterUiHelper {
    public static final Identifier CUSTOM_FONT = new Identifier("nexuscharacters", "nexuscharacters");
    public static final Identifier TRASH_ICON = new Identifier("nexuscharacters", "textures/gui/trash.png");
    public static final Identifier EDIT_ICON = new Identifier("nexuscharacters", "textures/gui/edit.png");
    public static final Identifier HEART_ICON = new Identifier("nexuscharacters", "textures/gui/heart.png");

    public static boolean autoRotate = false;
    public static boolean showEquipment = true;

    public static final Identifier[] ARMOR_ICONS = {
            new Identifier("minecraft", "textures/item/empty_armor_slot_helmet.png"),
            new Identifier("minecraft", "textures/item/empty_armor_slot_chestplate.png"),
            new Identifier("minecraft", "textures/item/empty_armor_slot_leggings.png"),
            new Identifier("minecraft", "textures/item/empty_armor_slot_boots.png")
    };

    public static float getScale(float targetW, float targetH, int width, int height) {
        float scaleX = width / targetW;
        float scaleY = height / targetH;
        return Math.min(1.0f, Math.min(scaleX, scaleY));
    }

    public static void drawRetroText(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color) {
        Text shadowText = Text.literal(text.getString()).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        ctx.drawText(tr, shadowText, x + 1, y + 1, 0xFF000000, false);
        ctx.drawText(tr, text, x, y, color, false);
    }

    public static void drawScaledTitle(DrawContext ctx, TextRenderer tr, String text, int centerX, int y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(centerX, y, 0);
        ctx.getMatrices().scale(scale, scale, 1.0F);

        int w = tr.getWidth(text);
        Text shadowText = Text.literal(text).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        ctx.drawText(tr, shadowText, -w / 2 + 1, 1, 0xFF000000, false);

        Text t = Text.literal(text).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        ctx.drawText(tr, t, -w / 2, 0, color, false);
        ctx.getMatrices().pop();
    }

    public static void drawMinecraftPanel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x80000000);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        ctx.fill(x, y, x + w, y + h, 0xFF373737);
        ctx.fill(x, y, x + w - 4, y + h - 4, 0xFF8B8B8B);
        ctx.fillGradient(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF3C3C3C, 0xFF282828);
        ctx.fill(x + 4, y + 4, x + w - 4, y + 6, 0x1AFFFFFF);
        ctx.fill(x + 4, y + 4, x + 6, y + h - 4, 0x1AFFFFFF);
        ctx.fill(x + 4, y + h - 6, x + w - 4, y + h - 4, 0x80000000);
        ctx.fill(x + w - 6, y + 4, x + w - 4, y + h - 4, 0x80000000);
    }

    public static void drawMinecraftButton(DrawContext ctx, int x, int y, int w, int h, boolean hovered) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        ctx.fill(x, y, x + w, y + h, 0xFF373737);
        int border = 2;
        ctx.fill(x, y, x + w - border, y + h - border, hovered ? 0xFFAFAFAF : 0xFF8B8B8B);
        ctx.fillGradient(x + border, y + border, x + w - border, y + h - border,
                hovered ? 0xFF4C4C4C : 0xFF3C3C3C,
                hovered ? 0xFF383838 : 0xFF282828);
        ctx.fill(x + border, y + border, x + w - border, y + border + 1, 0x1AFFFFFF);
        ctx.fill(x + border, y + border, x + border + 1, y + h - border, 0x1AFFFFFF);
        ctx.fill(x + border, y + h - border - 1, x + w - border, y + h - border, 0x40000000);
        ctx.fill(x + w - border - 1, y + border, x + w - border, y + h - border, 0x40000000);
    }

    public static void drawMinecraftCard(DrawContext ctx, int x, int y, int w, int h, boolean active) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        ctx.fill(x, y, x + w - 1, y + h, active ? 0xFFFFFFFF : 0xFF373737);
        int border = 2;
        ctx.fill(x, y, x + w - border, y + h - border, active ? 0xFFAFAFAF : 0xFF8B8B8B);
        int startCol = active ? 0xFF707070 : 0xFF4C4C4C;
        int endCol   = active ? 0xFF505050 : 0xFF333333;
        ctx.fillGradient(x + border, y + border, x + w - border, y + h - border, startCol, endCol);
    }

    public static void drawMinecraftRect(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xFF000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF373737);
        ctx.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF1B1B1B, 0xFF141414);
    }

    /**
     * Guards against mods whose items call client.player.getInventory() (or similar)
     * inside hasGlint() / getTooltip(), which crashes when player is null (pre-join screens).
     */
    public static void drawSafeItem(DrawContext ctx, net.minecraft.item.ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        try { ctx.drawItem(stack, x, y); } catch (Exception ignored) {}
    }

    public static void drawSafeItemInSlot(DrawContext ctx, TextRenderer tr, net.minecraft.item.ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        try { ctx.drawItemInSlot(tr, stack, x, y); } catch (Exception ignored) {}
    }

    /**
     * Draws a self-contained warning panel below the character-list center panel when the
     * active character's vault contains directories for mods that are not currently loaded.
     */
    public static void drawModWarningPanel(DrawContext ctx, TextRenderer tr, List<String> missingMods, int x, int y, int w) {
        String warningText =
                "This character has data from mods not present in this instance. Joining may result in PERMANENT loss of these items/data. "
                + "We recommend making a backup or storing modded items in a chest (on the previous instance/modpack) before proceeding.";
        
        Text warnText = Text.literal(warningText).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        List<net.minecraft.text.OrderedText> warnLines = tr.wrapLines(warnText, w - 22);
        int numLines = Math.min(3, warnLines.size());

        StringBuilder sb = new StringBuilder("Missing: ");
        for (int i = 0; i < missingMods.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(missingMods.get(i));
        }
        Text modNamesText = Text.literal(sb.toString()).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        List<net.minecraft.text.OrderedText> modNameLines = tr.wrapLines(modNamesText, w - 22);
        int modLineCount = Math.min(1, modNameLines.size());

        // height: 6 top + 10 header + 4 sep + (numLines*10) warn + (modLineCount*10) mods + 6 bottom
        int h = 6 + 10 + 4 + (numLines * 10) + (modLineCount * 10) + 6;

        // Shadow + outer border
        ctx.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x60000000);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);

        // Background gradient (dark orange/brown)
        ctx.fill(x, y, x + w, y + h, 0xFF1E0E00);
        ctx.fill(x, y, x + w - 4, y + h - 4, 0xFF2E1800);
        ctx.fillGradient(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF261200, 0xFF180900);

        // Orange accent line at top
        ctx.fill(x, y, x + w, y + 2, 0xFFFF8800);

        // Header: ⚠︎ WARNING
        Text header = Text.literal("WARNING").setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        drawRetroText(ctx, tr, header, x + 10, y + 6, 0xFFFF8800);

        // Separator
        int sepY = y + 16 + 2;
        ctx.fill(x + 6, sepY, x + w - 6, sepY + 1, 0xFF4A2800);

        // Warning text lines
        int lineY = sepY + 4;
        for (int i = 0; i < numLines; i++) {
            ctx.drawText(tr, warnLines.get(i), x + 11, lineY + 1, 0xFF000000, false);
            ctx.drawText(tr, warnLines.get(i), x + 10, lineY, 0xFFBBAA88, false);
            lineY += 10;
        }

        // Missing mod names at the bottom
        if (modLineCount > 0) {
            ctx.drawText(tr, modNameLines.get(0), x + 11, lineY + 1, 0xFF000000, false);
            ctx.drawText(tr, modNameLines.get(0), x + 10, lineY, 0xFFBB8800, false);
        }
    }

    public static int getWarningPanelHeight(TextRenderer tr, List<String> missingMods, int w) {
        String warningText =
                "Warning: This character has data from mods not present in this instance. Joining may result in PERMANENT loss of these items/data. "
                + "We recommend making a backup or storing modded items in a chest before proceeding.";
        Text warnText = Text.literal(warningText).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        int numLines = Math.min(3, tr.wrapLines(warnText, w - 22).size());

        StringBuilder sb = new StringBuilder("Missing: ");
        for (int i = 0; i < missingMods.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(missingMods.get(i));
        }
        Text modNamesText = Text.literal(sb.toString()).setStyle(net.minecraft.text.Style.EMPTY.withFont(CUSTOM_FONT));
        int modLineCount = Math.min(1, tr.wrapLines(modNamesText, w - 22).size());

        return 6 + 10 + 4 + (numLines * 10) + (modLineCount * 10) + 6;
    }

    public static void injectCameraIfNeeded(MinecraftClient client) {
        // InventoryScreen.drawEntity sets up the camera/dispatcher internally,
        // so no manual injection is needed. Left as a no-op to avoid breaking callers.
    }

    public static PlayerStatsInfo getPlayerStats(CharacterDto c) {
        int blocksMined = 0, mobKills = 0, diamonds = 0, playTime = 0;
        String statsJson = VaultManager.readStatsJson(c.id());
        if (statsJson != null) {
            try {
                JsonObject json = JsonParser.parseString(statsJson).getAsJsonObject();
                if (json.has("stats")) {
                    JsonObject stats = json.getAsJsonObject("stats");
                    if (stats.has("minecraft:mined")) {
                        JsonObject mined = stats.getAsJsonObject("minecraft:mined");
                        for (var entry : mined.entrySet()) {
                            blocksMined += entry.getValue().getAsInt();
                        }
                        if (mined.has("minecraft:diamond_ore")) diamonds += mined.get("minecraft:diamond_ore").getAsInt();
                        if (mined.has("minecraft:deepslate_diamond_ore")) diamonds += mined.get("minecraft:deepslate_diamond_ore").getAsInt();
                    }
                    if (stats.has("minecraft:killed")) {
                        for (var entry : stats.getAsJsonObject("minecraft:killed").entrySet()) {
                            mobKills += entry.getValue().getAsInt();
                        }
                    }
                    if (stats.has("minecraft:custom")) {
                        JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                        if (custom.has("minecraft:play_time")) playTime = custom.get("minecraft:play_time").getAsInt();
                        if (custom.has("minecraft:mob_kills")) mobKills = custom.get("minecraft:mob_kills").getAsInt();
                    }
                }
            } catch (Exception ignored) {}
        }
        return new PlayerStatsInfo(blocksMined, mobKills, diamonds, playTime);
    }

    public static AdvancementInfo getLatestAdvancement(CharacterDto c) {
        String advJson = VaultManager.readAdvancementsJson(c.id());
        if (advJson == null) return null;

        String latestId = null;
        String latestTime = "";

        try {
            JsonObject json = JsonParser.parseString(advJson).getAsJsonObject();
            for (var entry : json.entrySet()) {
                String id = entry.getKey();
                // Skip data version marker, recipes, and root/tab advancements
                if (id.equals("DataVersion")) continue;
                if (id.contains("recipes/")) continue;
                // Skip root advancements (e.g. "cobblemon:root", "minecraft:story/root", any path ending in /root or equal to namespace:root)
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                if (path.equals("root") || path.endsWith("/root")) continue;
                JsonObject node = entry.getValue().getAsJsonObject();
                if (node.has("done") && node.get("done").getAsBoolean() && node.has("criteria")) {
                    for (var crit : node.getAsJsonObject("criteria").entrySet()) {
                        String time = crit.getValue().getAsString();
                        if (time.compareTo(latestTime) > 0) {
                            latestTime = time;
                            latestId = id;
                        }
                    }
                }
            }
        } catch (Exception e) { return null; }

        if (latestId == null) return null;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Identifier advId = Identifier.tryParse(latestId);
            if (advId == null) return null;

            // 1. Search mod JARs directly.
            // Advancement definitions live under data/<ns>/advancement[s]/<path>.json inside mod JARs.
            // The client ResourceManager only serves assets/, so we must read data/ entries ourselves.
            AdvancementInfo fromJar = readAdvancementFromJars(advId);
            if (fromJar != null) return fromJar;

            // 3. Fallback to language keys
            net.minecraft.util.Language lang = net.minecraft.util.Language.getInstance();
            String pathDots = advId.getPath().replace('/', '.');
            String tk = "advancements." + pathDots + ".title";
            String dk = "advancements." + pathDots + ".description";
            String tk2 = "advancements." + advId.getNamespace() + "." + pathDots + ".title";
            String dk2 = "advancements." + advId.getNamespace() + "." + pathDots + ".description";

            String title = lang.hasTranslation(tk) ? lang.get(tk) : lang.hasTranslation(tk2) ? lang.get(tk2) : null;
            String desc = lang.hasTranslation(dk) ? lang.get(dk) : lang.hasTranslation(dk2) ? lang.get(dk2) : "";

            if (title == null) {
                String rawPath = advId.getPath().substring(advId.getPath().lastIndexOf('/') + 1);
                title = Character.toUpperCase(rawPath.charAt(0)) + rawPath.substring(1).replace('_', ' ');
            }
            return new AdvancementInfo(title, desc, new ItemStack(Items.MAP));
        } catch (Exception e) { return null; }
    }

    private static AdvancementInfo readAdvancementFromJars(Identifier advId) {
        // Advancement definitions live under data/<ns>/advancements/<path>.json or
        // data/<ns>/advancement/<path>.json (Cobblemon uses the singular form).
        String[] subDirs = {"advancements", "advancement"};
        String relPath = advId.getPath() + ".json"; // e.g. "story/mine_stone.json"

        for (var container : FabricLoader.getInstance().getAllMods()) {
            for (String subDir : subDirs) {
                String entryPath = "data/" + advId.getNamespace() + "/" + subDir + "/" + relPath;
                try {
                    Path root = container.getRootPaths().get(0);
                    Path file = root.resolve(entryPath);
                    if (!Files.exists(file)) continue;

                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (!obj.has("display")) continue;

                    JsonObject display = obj.getAsJsonObject("display");

                    String title = null;
                    String desc = "";
                    if (display.has("title")) title = resolveTextComponent(display.get("title"));
                    if (display.has("description")) desc = resolveTextComponent(display.get("description"));
                    if (desc == null) desc = "";

                    // Resolve icon item
                    ItemStack icon = new ItemStack(Items.MAP);
                    if (display.has("icon")) {
                        JsonElement iconEl = display.get("icon");
                        String itemId = null;
                        if (iconEl.isJsonObject()) {
                            JsonObject iconObj = iconEl.getAsJsonObject();
                            if (iconObj.has("id")) itemId = iconObj.get("id").getAsString();
                            else if (iconObj.has("item")) itemId = iconObj.get("item").getAsString();
                        } else if (iconEl.isJsonPrimitive()) {
                            itemId = iconEl.getAsString();
                        }
                        if (itemId != null) {
                            Identifier itemRl = Identifier.tryParse(itemId);
                            if (itemRl != null && Registries.ITEM.containsId(itemRl)) {
                                Item item = Registries.ITEM.get(itemRl);
                                icon = new ItemStack(item);
                                // Handle NBT/components on icon
                                if (iconEl.isJsonObject()) {
                                    JsonObject iconObj = iconEl.getAsJsonObject();
                                    if (iconObj.has("nbt")) {
                                        try {
                                            NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(iconObj.get("nbt").getAsString());
                                            icon.setNbt(nbt);
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    }

                    if (title == null) {
                        String rawPath = advId.getPath().substring(advId.getPath().lastIndexOf('/') + 1);
                        title = Character.toUpperCase(rawPath.charAt(0)) + rawPath.substring(1).replace('_', ' ');
                    }
                    return new AdvancementInfo(title, desc, icon);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String resolveTextComponent(JsonElement el) {
        if (el == null) return null;

        // Plain string — e.g. "My Advancement"
        if (el.isJsonPrimitive()) return el.getAsString();

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();

            // Translation key — e.g. {"translate": "advancements.mod.foo.title"}
            // Try the MC Text deserialiser first; fall back to a manual lang lookup.
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                try {
                    Text text = Text.Serializer.fromJson(el.toString());
                    if (text != null) {
                        String result = text.getString();
                        // If the result is the raw key the translation is missing; try lang directly.
                        if (!result.equals(key)) return result;
                    }
                } catch (Exception ignored) {}
                net.minecraft.util.Language lang = net.minecraft.util.Language.getInstance();
                if (lang.hasTranslation(key)) return lang.get(key);
                return key; // last resort: show the raw key rather than nothing
            }

            // Literal text object — e.g. {"text": "My Advancement"}
            if (obj.has("text")) return obj.get("text").getAsString();
        }

        // Array of components — use the first one
        if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
            return resolveTextComponent(el.getAsJsonArray().get(0));
        }

        // Generic fallback through MC deserialiser
        try {
            Text text = Text.Serializer.fromJson(el.toString());
            return text != null ? text.getString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record PlayerStatsInfo(int blocksMined, int mobKills, int diamonds, int playTime) {}
    public record AdvancementInfo(String title, String description, ItemStack icon) {}
}