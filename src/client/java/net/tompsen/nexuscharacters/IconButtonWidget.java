package net.tompsen.nexuscharacters;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class IconButtonWidget extends ButtonWidget {

    private static final Identifier ICON = new Identifier("nexuscharacters", "textures/gui/user-list.png");

    public IconButtonWidget(int x, int y, PressAction onPress) {
        super(x, y, 20, 20, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        setTooltip(Tooltip.of(Text.literal("Characters List")));
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        CharacterUiHelper.drawMinecraftButton(context, getX(), getY(), getWidth(), getHeight(), isHovered());
        // Draw icon centered on button
        context.drawTexture(ICON, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
    }
}
