package net.tompsen.charsel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class CharacterSelectionScreen extends Screen {
    private final Screen parent;
    private final Runnable onConfirm;

    public CharacterSelectionScreen(Screen parent, Runnable onConfirm) {
        super(Text.literal("Pick a Character"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        refreshList();
    }

    private void refreshList() {
        clearChildren();
        List<CharacterDto> characters = CharacterSelection.DATA_FILE_MANAGER.characterList;
        int x = width / 2 - 150;
        int y = height / 2 - (characters.size() * 36) / 2;

        for (int i = 0; i < characters.size(); i++) {
            CharacterDto character = characters.get(i);
            int rowY = y + i * 36;

            // Select button (entire row)
            addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
                CharacterSelection.selectedCharacter = character;
                client.setScreen(null);
                onConfirm.run();
            }).dimensions(x, rowY, 260, 32).build());

            // Delete button
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> {
                CharacterSelection.DATA_FILE_MANAGER.deleteCharacter(character.id());
                refreshList();
            }).dimensions(x + 264, rowY, 20, 32).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> client.setScreen(parent))
                .dimensions(width / 2 - 50, y + characters.size() * 36 + 12, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, 0, 0, 0);
        super.render(context, mouseX, mouseY, delta);

        List<CharacterDto> characters = CharacterSelection.DATA_FILE_MANAGER.characterList;
        int x = width / 2 - 150;
        int y = height / 2 - (characters.size() * 36) / 2;

        context.drawCenteredTextWithShadow(textRenderer, "Select a Character", width / 2, y - 20, 0xFFFFFF);

        for (int i = 0; i < characters.size(); i++) {
            CharacterDto character = characters.get(i);
            int rowY = y + i * 36;

            // Player face (8x8 from skin, scaled to 24x24)
            drawPlayerFace(context, character, x + 4, rowY + 4, 24);

            // Name
            context.drawTextWithShadow(textRenderer,
                    Text.literal(character.name()).formatted(Formatting.WHITE),
                    x + 34, rowY + 6, 0xFFFFFF);

            // Level
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Level " + character.playerNbt().getInt("XpLevel")).formatted(Formatting.GRAY),
                    x + 34, rowY + 18, 0xAAAAAA);
        }
    }

    private void drawPlayerFace(DrawContext context, CharacterDto character, int x, int y, int size) {
        // If character has a saved skin, decode and draw it
        // Otherwise draw a default steve face using the standard skin texture
        Identifier skin = getCharacterSkin(character);
        // Draw head (u=8, v=8, 8x8 region of skin texture)
        context.drawTexture(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
        // Draw hat layer (u=40, v=8, 8x8 region)
        context.drawTexture(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
    }

    private Identifier getCharacterSkin(CharacterDto character) {
        // Use the stored skin if available, otherwise fallback to default
        if (character.skinValue() != null && !character.skinValue().isEmpty()) {
            return Identifier.of("charsel", "skin_" + character.id());
        }
        return DefaultSkinHelper.getSkinTextures(character.id()).texture();
    }
}