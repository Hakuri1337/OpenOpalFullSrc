package mixin;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.renderer.menu.OraculusMenuRenderer;

import static wtf.oraculus.client.Constants.mc;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void renderOraculusMenuButton(final DrawContext context, final int mouseX, final int mouseY,
                                          final float delta, final CallbackInfo ci) {
        if (!(mc.currentScreen instanceof TitleScreen)
                || !OraculusMenuRenderer.isEnhancedMenuEnabled()
                || !OraculusMenuRenderer.canRenderBranding()
                || !((Object) this instanceof ButtonWidget button)
                || button.getWidth() < 90) {
            return;
        }

        final int x = button.getX();
        final int y = button.getY();
        final int width = button.getWidth();
        final int height = button.getHeight();
        final boolean hovered = button.isHovered();
        final int textColor = button.active ? 0xFFF4FAFF : 0xFF75808A;

        final Identifier background = !button.active
                ? OraculusMenuRenderer.MENU_BUTTON_DISABLED
                : hovered ? OraculusMenuRenderer.MENU_BUTTON_HOVER : OraculusMenuRenderer.MENU_BUTTON;
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                background,
                x,
                y,
                0F,
                0F,
                width,
                height,
                256,
                32,
                256,
                32
        );
        context.drawCenteredTextWithShadow(mc.textRenderer, button.getMessage(), x + width / 2, y + (height - 8) / 2, textColor);
        if (hovered) {
            context.setCursor(button.active ? StandardCursors.POINTING_HAND : StandardCursors.NOT_ALLOWED);
        }
        ci.cancel();
    }
}
