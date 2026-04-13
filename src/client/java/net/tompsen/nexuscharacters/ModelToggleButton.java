package net.tompsen.nexuscharacters;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ModelToggleButton extends ButtonWidget {
    private final Identifier icon;
    private final Supplier<Boolean> stateSupplier;
    private final Consumer<Boolean> stateConsumer;

    public ModelToggleButton(int x, int y, Identifier icon, Supplier<Boolean> stateSupplier, Consumer<Boolean> stateConsumer) {
        super(x, y, 20, 20, Text.empty(), btn -> stateConsumer.accept(!stateSupplier.get()), DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        this.stateSupplier = stateSupplier;
        this.stateConsumer = stateConsumer;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean active = stateSupplier.get();
        CharacterUiHelper.drawMinecraftButton(context, getX(), getY(), getWidth(), getHeight(), isHovered());
        
        if (active) {
            // Glow effect for active state (light gray)
            context.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0x44AFAFAF);
        }

        context.drawTexture(icon, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
    }
}
