package net.tompsen.charsel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class CharacterListScreen extends Screen {
    private final Screen parent;

    public CharacterListScreen(Screen parent) {
        super(Text.literal("Characters"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshList();
    }

    private void refreshList() {
        clearChildren();
        List<CharacterDto> characters = CharacterSelection.DATA_FILE_MANAGER.characterList;
        int x = width / 2 - 150;
        int y = getListStartY(characters.size());

        for (int i = 0; i < characters.size(); i++) {
            CharacterDto character = characters.get(i);
            int rowY = y + i * 36;

            // Delete button
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> {
                CharacterSelection.DATA_FILE_MANAGER.deleteCharacter(character.id());
                refreshList();
            }).dimensions(x + 264, rowY, 20, 32).build());
        }

        // Add Character
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Character"), btn ->
                client.setScreen(new CharacterCreationScreen(this, () -> {
                    clearChildren();
                    refreshList();
                }))).dimensions(width / 2 - 100, y + characters.size() * 36 + 12, 200, 20).build());

        // Close
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> client.setScreen(parent))
                .dimensions(width / 2 - 50, y + characters.size() * 36 + 36, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dim + panel background
        context.fillGradient(0, 0, width, height, 0xA0000000, 0xA0000000);

        List<CharacterDto> characters = CharacterSelection.DATA_FILE_MANAGER.characterList;
        int x = width / 2 - 150;
        int y = getListStartY(characters.size());
        int panelPad = 16;
        int panelH = characters.size() * 36 + 80;
        context.fill(x - panelPad, y - panelPad, x + 300 + panelPad, y + panelH, 0xFF1E1E1E);
        context.fill(x - panelPad, y - panelPad, x + 300 + panelPad, y - panelPad + 2, 0xFF4A90D9);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Character Management").formatted(Formatting.BOLD),
                width / 2, y - panelPad + 6, 0x4A90D9);

        for (int i = 0; i < characters.size(); i++) {
            CharacterDto character = characters.get(i);
            int rowY = y + i * 36;

            // Row background
            //context.fill(x, rowY, x + 260, rowY + 32, 0xFF2A2A2A);
            //context.fill(x, rowY, x + 260, rowY + 1, 0xFF3A3A3A);

            // Player face
            Identifier skin = DefaultSkinHelper.getSkinTextures(character.id()).texture();
            context.drawTexture(skin, x + 4, rowY + 4, 24, 24, 8, 8, 8, 8, 64, 64);
            context.drawTexture(skin, x + 4, rowY + 4, 24, 24, 40, 8, 8, 8, 64, 64);

            // Name
            context.drawTextWithShadow(textRenderer,
                    Text.literal(character.name()).formatted(Formatting.WHITE),
                    x + 34, rowY + 6, 0xFFFFFF);

            // Level
            int level = character.playerNbt().isEmpty() ? 0 : character.playerNbt().getInt("XpLevel");
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Level " + level).formatted(Formatting.GRAY),
                    x + 34, rowY + 18, 0xAAAAAA);
        }

        if (characters.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No characters yet").formatted(Formatting.GRAY),
                    width / 2, y - 20, 0x888888);
        }
    }

    private int getListStartY(int count) {
        return height / 2 - (count * 36 + 60) / 2 + 16;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void close() { client.setScreen(parent); }
}