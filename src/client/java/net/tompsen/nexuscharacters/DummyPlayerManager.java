package net.tompsen.nexuscharacters;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DummyPlayerManager {

    private static final Map<UUID, OtherClientPlayerEntity> DUMMY_CACHE = new HashMap<>();
    private static final Map<UUID, Identifier> SKIN_CACHE = new HashMap<>();

    /**
     * Character UUIDs for which equipment was automatically disabled because a mod
     * feature-renderer layer crashed during the first preview render.  Once a UUID
     * is in this set the equipment toggle stays off for that character for the
     * lifetime of the screen session.
     */
    private static final java.util.Set<UUID> equipmentAutoDisabled =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Called each frame after drawEntity.  Checks whether any feature-renderer layer
     * crashed for the given character's dummy entity and, if so, strips its equipment
     * and records it in {@link #equipmentAutoDisabled} so the toggle stays off.
     *
     * @return {@code true} if equipment was just auto-disabled for this character.
     */
    public static boolean checkAndAutoDisableEquipment(CharacterDto character) {
        if (equipmentAutoDisabled.contains(character.id())) return false; // already handled
        if (!SafeFeatureRendererWrapper.crashedEntityUuids.remove(character.id())) return false;

        // A layer crashed — strip equipment for this character only.
        OtherClientPlayerEntity dummy = DUMMY_CACHE.get(character.id());
        if (dummy != null) {
            for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
                dummy.equipStack(slot, net.minecraft.item.ItemStack.EMPTY);
            }
        }
        equipmentAutoDisabled.add(character.id());
        return true;
    }

    /** Returns true if equipment was auto-disabled for this character due to a crash. */
    public static boolean isEquipmentAutoDisabled(UUID characterId) {
        return equipmentAutoDisabled.contains(characterId);
    }

    // ── Subclasse que bypassa o getNetworkHandler() ──────────────────────────
    static class DummyClientPlayer extends OtherClientPlayerEntity implements NexusDummyEntity {
        private final UUID characterId;

        DummyClientPlayer(ClientWorld world, GameProfile profile, UUID characterId) {
            super(world, profile);
            this.characterId = characterId;
            forceModelPartsVisible(this);
        }

        @Override
        public Identifier getSkinTexture() {
            Identifier cached = SKIN_CACHE.get(characterId);
            if (cached != null) return cached;
            return DefaultSkinHelper.getTexture(
                    DummyPlayerManager.resolveDefaultSkinUUID(characterId));
        }

        @Override
        public boolean isSpectator() { return false; }

        @Override
        public boolean isCreative() { return false; }

        @Override
        public boolean isInvisibleTo(net.minecraft.entity.player.PlayerEntity player) {
            return false;
        }

        @Override
        public boolean isCustomNameVisible() {
            return false;
        }

        @Override
        protected net.minecraft.client.network.PlayerListEntry getPlayerListEntry() {
            return null;
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public static OtherClientPlayerEntity getDummyPlayer(CharacterDto character) {
        ClientWorld world = DummyWorldManager.getDummyWorld();
        if (world == null) return null;

        if (DUMMY_CACHE.containsKey(character.id())) {
            OtherClientPlayerEntity cached = DUMMY_CACHE.get(character.id());
            if (cached.getWorld() == world) return cached;
        }

        // 1. Montar GameProfile com a skin armazenada
        GameProfile profile = new GameProfile(character.id(), character.name());
        String skinVal = character.skinValue();
        String skinSig = character.skinSignature();
        if (skinVal != null && !skinVal.isEmpty()) {
            profile.getProperties().put("textures",
                    new Property("textures", skinVal, skinSig != null ? skinSig : ""));
        }

        // 2. Criar a entidade com nossa subclasse
        DummyClientPlayer dummy = new DummyClientPlayer(world, profile, character.id());
        dummy.setPosition(0, 0, 0);
        dummy.age = 20;

        forceModelPartsVisible(dummy);

        // 3. Equipar itens do inventário
        NbtCompound playerNbt = VaultManager.readPlayerNbt(character.id());
        if (playerNbt != null && playerNbt.contains("Inventory")) {
            NbtList inventory = playerNbt.getList("Inventory", 10);
            int selectedSlot = playerNbt.contains("SelectedItemSlot")
                    ? playerNbt.getInt("SelectedItemSlot") : 0;

            for (int i = 0; i < inventory.size(); i++) {
                NbtCompound itemTag = inventory.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                ItemStack stack;
                try { stack = ItemStack.fromNbt(itemTag); } catch (Exception ignored) { continue; }

                if (!stack.isEmpty()) {
                    if (slot == 100) dummy.equipStack(EquipmentSlot.FEET, stack);
                    else if (slot == 101) dummy.equipStack(EquipmentSlot.LEGS, stack);
                    else if (slot == 102) dummy.equipStack(EquipmentSlot.CHEST, stack);
                    else if (slot == 103) dummy.equipStack(EquipmentSlot.HEAD, stack);
                    else if (slot == 150) dummy.equipStack(EquipmentSlot.OFFHAND, stack); // -106 & 0xFF = 150
                    // Skip world-dependent items in mainhand (maps, compasses, clocks)
                    // — their renderers call world.getMapState() / world.getTime() which NPE
                    // because the dummy world has no real chunk data / map storage.
                    if (slot == selectedSlot && !isWorldDependentItem(stack)) {
                        dummy.equipStack(EquipmentSlot.MAINHAND, stack);
                        // Trigger the arm-raise / using pose for items that have one
                        // (bows, crossbows, shields, spyglasses, etc.).
                        if (triggersUsingPose(stack)) {
                            dummy.setCurrentHand(net.minecraft.util.Hand.MAIN_HAND);
                        }
                    }
                }
            }
        }

        GameProfile loadProfile = (skinVal != null && !skinVal.isEmpty())
                ? profile
                : new GameProfile(character.id(), character.name());
        UUID charId = character.id();
        MinecraftClient.getInstance().getSkinProvider().loadSkin(
                loadProfile,
                (type, textureId, minecraftProfileTexture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SKIN_CACHE.put(charId, textureId);
                    }
                },
                true
        );

        DUMMY_CACHE.put(character.id(), dummy);
        return dummy;
    }

    /** Items whose renderers require a real world (map state, time, position). */
    private static boolean isWorldDependentItem(ItemStack stack) {
        return stack.getItem() instanceof FilledMapItem
                || stack.isOf(net.minecraft.item.Items.COMPASS)
                || stack.isOf(net.minecraft.item.Items.RECOVERY_COMPASS)
                || stack.isOf(net.minecraft.item.Items.CLOCK);
    }

    /**
     * Returns true for items that produce a visible arm/body pose when "in use"
     * (bows draw back, crossbows raise, shields block, spyglasses zoom, etc.).
     * Modded items that implement {@link net.minecraft.item.RangedWeaponItem} or
     * whose use-action is {@link net.minecraft.item.ItemUsageContext} bow/block
     * will also benefit automatically via the same vanilla pose logic.
     */
    private static boolean triggersUsingPose(ItemStack stack) {
        net.minecraft.item.Item item = stack.getItem();
        return item instanceof net.minecraft.item.BowItem
                || item instanceof net.minecraft.item.CrossbowItem
                || item instanceof net.minecraft.item.ShieldItem
                || item instanceof net.minecraft.item.SpyglassItem
                || item instanceof net.minecraft.item.RangedWeaponItem;
    }

    public static Identifier getSkinIdentifier(CharacterDto character) {
        Identifier cached = SKIN_CACHE.get(character.id());
        if (cached != null) return cached;

        if (character.skinValue() != null && !character.skinValue().isEmpty()) {
            GameProfile profile = new GameProfile(character.id(), character.name());
            profile.getProperties().put("textures",
                    new Property("textures", character.skinValue(),
                            character.skinSignature() != null ? character.skinSignature() : ""));
            UUID charId = character.id();
            MinecraftClient.getInstance().getSkinProvider().loadSkin(
                    profile,
                    (type, textureId, minecraftProfileTexture) -> {
                        if (type == MinecraftProfileTexture.Type.SKIN) {
                            SKIN_CACHE.put(charId, textureId);
                        }
                    },
                    true
            );
        }

        // Derives default skin using real player UUID (not the character's random UUID)
        return DefaultSkinHelper.getTexture(resolveDefaultSkinUUID(character));
    }

    // Extrai o UUID real guardado, ou usa o id do character como fallback
    private static UUID resolveDefaultSkinUUID(CharacterDto character) {
        String skinUsername = character.skinUsername();
        if (skinUsername != null && skinUsername.startsWith("__default__:")) {
            try {
                return UUID.fromString(skinUsername.substring("__default__:".length()));
            } catch (Exception ignored) {}
        }
        return character.id();
    }

    static UUID resolveDefaultSkinUUID(UUID characterId) {
        // O SKIN_CACHE já foi populado com o UUID correto pelo getDummyPlayer()
        // Este fallback só é chamado nos primeiros frames antes do fetch completar
        return characterId;
    }

    /**
     * Applies or removes equipment visibility on all cached dummies without
     * rebuilding them.  Re-equipping restores from the saved NBT, so this is
     * safe to call every frame.
     */
    public static void applyEquipmentVisibility(boolean showEquipment) {
        if (showEquipment) {
            // Restore from NBT for every cached dummy, skipping characters whose
            // equipment was auto-disabled due to a mod layer crash — re-equipping
            // them would just trigger the crash again on the next frame.
            for (Map.Entry<UUID, OtherClientPlayerEntity> entry : DUMMY_CACHE.entrySet()) {
                if (equipmentAutoDisabled.contains(entry.getKey())) continue;
                OtherClientPlayerEntity dummy = entry.getValue();
                // Find the matching character by id to reload NBT
                NexusCharacters.DATA_FILE_MANAGER.characterList.stream()
                        .filter(c -> c.id().equals(entry.getKey()))
                        .findFirst()
                        .ifPresent(c -> reloadEquipment(dummy, c));
            }
        } else {
            for (OtherClientPlayerEntity dummy : DUMMY_CACHE.values()) {
                dummy.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
                dummy.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
                dummy.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
                dummy.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
                dummy.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                dummy.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    private static void reloadEquipment(OtherClientPlayerEntity dummy, CharacterDto character) {
        // Clear first
        for (EquipmentSlot slot : EquipmentSlot.values()) dummy.equipStack(slot, ItemStack.EMPTY);

        NbtCompound playerNbt = VaultManager.readPlayerNbt(character.id());
        if (playerNbt == null || !playerNbt.contains("Inventory")) return;
        NbtList inventory = playerNbt.getList("Inventory", 10);
        int selectedSlot = playerNbt.contains("SelectedItemSlot") ? playerNbt.getInt("SelectedItemSlot") : 0;
        for (int i = 0; i < inventory.size(); i++) {
            NbtCompound itemTag = inventory.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            ItemStack stack;
            try { stack = ItemStack.fromNbt(itemTag); } catch (Exception ignored) { continue; }
            if (!stack.isEmpty()) {
                if (slot == 100) dummy.equipStack(EquipmentSlot.FEET, stack);
                else if (slot == 101) dummy.equipStack(EquipmentSlot.LEGS, stack);
                else if (slot == 102) dummy.equipStack(EquipmentSlot.CHEST, stack);
                else if (slot == 103) dummy.equipStack(EquipmentSlot.HEAD, stack);
                else if (slot == 150) dummy.equipStack(EquipmentSlot.OFFHAND, stack);
                if (slot == selectedSlot && !isWorldDependentItem(stack)) {
                    dummy.equipStack(EquipmentSlot.MAINHAND, stack);
                    if (triggersUsingPose(stack)) {
                        dummy.setCurrentHand(net.minecraft.util.Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    /**
     * Forces all player model overlay parts (hat, jacket, sleeves, pants, cape) visible.
     * Uses a field scan instead of getDeclaredField("PLAYER_MODEL_PARTS") because that
     * name is the Yarn-mapped name and will be obfuscated in modpacks using other mappings.
     * We find the TrackedData<Byte> static field in PlayerEntity by type signature.
     */
    private static void forceModelPartsVisible(OtherClientPlayerEntity entity) {
        try {
            for (java.lang.reflect.Field f : net.minecraft.entity.player.PlayerEntity.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!net.minecraft.entity.data.TrackedData.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val instanceof net.minecraft.entity.data.TrackedData<?> td) {
                    // The model-parts TrackedData holds a Byte; skip others (Float, Boolean, etc.)
                    try {
                        Object current = entity.getDataTracker().get(td);
                        if (current instanceof Byte) {
                            @SuppressWarnings("unchecked")
                            net.minecraft.entity.data.TrackedData<Byte> byteTd =
                                    (net.minecraft.entity.data.TrackedData<Byte>) td;
                            entity.getDataTracker().set(byteTd, (byte) 0x7F);
                            return; // found and set
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable e) {
            NexusCharacters.LOGGER.warn("[NexusCharacters] forceModelPartsVisible failed: {}", e.getMessage());
        }
    }

    public static void clearCache() {
        DUMMY_CACHE.clear();
        SKIN_CACHE.clear();
        equipmentAutoDisabled.clear();
        SafeFeatureRendererWrapper.crashedEntityUuids.clear();
        DummyWorldManager.clear();
    }

    public static void invalidateDummies() {
        DUMMY_CACHE.clear();
        equipmentAutoDisabled.clear();
        SafeFeatureRendererWrapper.crashedEntityUuids.clear();
    }
}