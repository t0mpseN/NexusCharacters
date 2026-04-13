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
        
        int color = isHovered() ? 0xFFFFFFA0 : 0xFFFFFFFF;
        CharacterUiHelper.drawRetroText(context, net.minecraft.client.MinecraftClient.getInstance().textRenderer, 
                getMessage(), getX() + (getWidth() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(getMessage())) / 2, 
                getY() + (getHeight() - 8) / 2, color);
    }
}
