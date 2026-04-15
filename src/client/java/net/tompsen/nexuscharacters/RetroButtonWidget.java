package net.tompsen.nexuscharacters;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class RetroButtonWidget extends ButtonWidget {
    public RetroButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        CharacterUiHelper.drawMinecraftButton(context, getX(), getY(), getWidth(), getHeight(), isHovered());
        
        net.minecraft.client.font.TextRenderer tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int color = isHovered() ? 0xFFFFFFA0 : 0xFFFFFFFF;
        CharacterUiHelper.drawRetroText(context, tr,
                getMessage(), getX() + (getWidth() - tr.getWidth(getMessage())) / 2,
                getY() + (getHeight() - tr.fontHeight) / 2, color);
    }
}
