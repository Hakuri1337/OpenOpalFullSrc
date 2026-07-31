package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.auth.AuthBootstrap;
import wtf.oraculus.client.auth.AuthService;
import wtf.oraculus.client.auth.AuthState;
import wtf.oraculus.client.auth.OraculusLoginScreen;
import wtf.oraculus.client.renderer.menu.ClientBootTransition;
import wtf.oraculus.client.renderer.menu.MenuVideoBackground;
import wtf.oraculus.client.renderer.menu.OraculusMenuRenderer;
import wtf.oraculus.client.screen.settings.OraculusSettingsScreen;

@Mixin(TitleScreen.class)
public final class TitleScreenMixin {

    @Shadow
    private boolean doBackgroundFade;

    @Unique
    private static final Text ORACULUS_SETTINGS_TEXT = Text.literal("Oraculus Settings");

    private TitleScreenMixin() {
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void finishInitialBootBeforeTitleWidgets(final CallbackInfo ci) {
        // The splash overlay can outlive the first title-screen frame. Do not
        // leave the title fade at zero while waiting for a logo render callback.
        this.doBackgroundFade = false;
        ClientBootTransition.finishInitialBoot();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void requireOraculusAuthentication(final CallbackInfo ci) {
        final AuthService auth = AuthBootstrap.getService();
        if (auth == null || auth.snapshot().state() == AuthState.READY
                || auth.snapshot().state() == AuthState.NETWORK_GRACE) {
            return;
        }
        final TitleScreen current = (TitleScreen) (Object) this;
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().currentScreen == current) {
                MinecraftClient.getInstance().setScreen(new OraculusLoginScreen(current));
            }
        });
    }

    @WrapOperation(
            method = "addNormalWidgets",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ButtonWidget;builder(Lnet/minecraft/text/Text;Lnet/minecraft/client/gui/widget/ButtonWidget$PressAction;)Lnet/minecraft/client/gui/widget/ButtonWidget$Builder;",
                    ordinal = 2
            )
    )
    private ButtonWidget.Builder replaceRealmsButton(final Text message, final ButtonWidget.PressAction action,
                                                      final Operation<ButtonWidget.Builder> original) {
        final TitleScreen parent = (TitleScreen) (Object) this;
        final ButtonWidget.PressAction openSettings = button -> MinecraftClient.getInstance()
                .setScreen(new OraculusSettingsScreen(parent));
        return original.call(ORACULUS_SETTINGS_TEXT, openSettings);
    }

    @Inject(method = "addNormalWidgets", at = @At("RETURN"))
    private void enableOraculusSettingsButton(final int y, final int spacing,
                                               final CallbackInfoReturnable<Integer> cir) {
        for (final Element child : ((Screen) (Object) this).children()) {
            if (child instanceof ButtonWidget button && button.getMessage().equals(ORACULUS_SETTINGS_TEXT)) {
                button.active = true;
                button.setTooltip(null);
                break;
            }
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V")
    )
    private void renderOraculusBackground(final TitleScreen instance, final DrawContext context, final float delta,
                                          final Operation<Void> original) {
        if (OraculusMenuRenderer.isEnhancedMenuEnabled()) {
            OraculusMenuRenderer.renderBackground(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        } else {
            MenuVideoBackground.setActive(false);
            original.call(instance, context, delta);
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V")
    )
    private void renderOraculusTitle(final LogoDrawer instance, final DrawContext context, final int width,
                                     final float alpha, final Operation<Void> original) {
        ClientBootTransition.finishInitialBoot();
        if (OraculusMenuRenderer.canRenderBranding()) {
            OraculusMenuRenderer.renderTitleBranding(context, width, context.getScaledWindowHeight(), alpha);
        } else {
            original.call(instance, context, width, alpha);
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;F)V")
    )
    private void hideVanillaSplashText(final SplashTextRenderer instance, final DrawContext context, final int width,
                                       final TextRenderer textRenderer, final float alpha, final Operation<Void> original) {
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderOraculusFooter(final DrawContext context, final int mouseX, final int mouseY,
                                      final float delta, final CallbackInfo ci) {
        if (OraculusMenuRenderer.isEnhancedMenuEnabled()) {
            OraculusMenuRenderer.renderFooter(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void stopMenuVideo(final CallbackInfo ci) {
        MenuVideoBackground.setActive(false);
    }
}
