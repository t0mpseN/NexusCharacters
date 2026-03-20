package net.tompsen.charsel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.UUID;

public class CharacterCreationScreen extends Screen {
    private final Screen parent;
    private final Runnable onAdd;
    private TextFieldWidget nameField;

    public CharacterCreationScreen(Screen parent, Runnable onAdd) {
        super(Text.literal("New Character"));
        this.parent = parent;
        this.onAdd = onAdd;
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 200, 20, Text.literal("Name"));
        nameField.setPlaceholder(Text.literal("Character name..."));
        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> confirm())
                .dimensions(width / 2 - 100, height / 2 + 20, 95, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> client.setScreen(parent))
                .dimensions(width / 2 + 5, height / 2 + 20, 95, 20).build());
    }

    private void confirm() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        // Just add to file, do NOT set selectedCharacter here
        CharacterSelection.DATA_FILE_MANAGER.addCharacter(new CharacterDto(
                UUID.randomUUID(), name, new NbtCompound(), new NbtCompound(), "", ""
        ));

        onAdd.run();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xA0000000, 0xA0000000);
        context.drawCenteredTextWithShadow(textRenderer, "Character Name", width / 2, height / 2 - 30, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
