package mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.feature.helper.impl.render.ScreenPositionManager;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin implements ParentElement {

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void hookMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ScreenPositionManager.getInstance().onMouseClick(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            ScreenPositionManager.getInstance().releaseDraggedProperties();
        }
        return ParentElement.super.mouseReleased(click);
    }

}
