package mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.feature.module.impl.visual.PostProcessingModule;
import wtf.oraculus.client.feature.module.impl.visual.NoRenderModule;
import wtf.oraculus.client.feature.module.impl.visual.StreamerModeModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.shader.LiquidGlassV2Renderer;
import wtf.oraculus.client.renderer.shader.ShaderFramebuffer;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.render.RenderBloomEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.SidebarEntry;

import java.util.Comparator;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static wtf.oraculus.client.Constants.mc;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Final
    @Shadow
    private static Comparator<ScoreboardEntry> SCOREBOARD_ENTRY_COMPARATOR;

    @Unique
    private float sbRectX, sbRectY, sbRectWidth, sbRectHeight;

    private InGameHudMixin() {
    }

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // rendering previous draws to fix layering
        // TOO MANY BUGS, breaks blur with vignette, causes shit to render over mc guis, etc
        // we are probably gonna have to find a way to mixin to their rendering :(
//        final GameRendererAccessor gameRenderer = (GameRendererAccessor) mc.gameRenderer;
//        final GuiRenderer guiRenderer = gameRenderer.getGuiRenderer();
//        final FogRenderer fogRenderer = gameRenderer.getFogRenderer();
//        guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));

        final float tickDelta = tickCounter.getTickProgress(false);

        this.applyPostProcessing(context, tickDelta);

        final Window window = mc.getWindow();

        final int scaledWidth = window.getScaledWidth();
        final int scaledHeight = window.getScaledHeight();

        final double mouseX = mc.mouse.getX() * ((double) scaledWidth / window.getWidth());
        final double mouseY = mc.mouse.getY() * ((double) scaledHeight / window.getHeight());

        NVGRenderer.beginFrame();
        NVGRenderer.rect(0, 0, scaledWidth, scaledHeight, NVGRenderer.GLOW_PAINT);

        oraculus$renderScoreboardRect(false);
        EventDispatcher.dispatch(new RenderScreenEvent(context, tickDelta, mouseX, mouseY));
        NVGRenderer.endFrameAndReset(true);

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                ShaderFramebuffer.getGlowFramebuffer().getColorAttachment(),
                0,
                ShaderFramebuffer.getGlowFramebuffer().getDepthAttachment(),
                1
        );

        MinecraftRenderer.render();
    }

    @Redirect(
            method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedStack()Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack getMainHandStack(PlayerInventory instance) {
        SlotHelper slotHelper = SlotHelper.getInstance();
        if (slotHelper.isActive() && slotHelper.getSilence() == SlotHelper.Silence.FULL) {
            return slotHelper.getMainHandStack(mc.player);
        }
        return instance.getSelectedStack();
    }

    @Redirect(
            method = "renderHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedSlot()I")
    )
    private int onRenderHotbarSlot(PlayerInventory instance) {
        return SlotHelper.getInstance().getSelectedSlot(instance);
    }

    @Unique
    private void applyPostProcessing(final DrawContext context, final float tickDelta) {
        NVGTextRenderer.blockTextRendering = true;
        ShaderFramebuffer.applyBlurToFullScreen();
        ShaderFramebuffer.applyLiquidGlassToFullScreen();

        final PostProcessingModule postProcessingModule = OraculusClient.getInstance().getModuleRepository().getModule(PostProcessingModule.class);

        if (postProcessingModule.isEnabled() && postProcessingModule.isBloom()) {
            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "oraculus/bloom", ShaderFramebuffer.getGlowFramebuffer().getColorAttachmentView(), OptionalInt.empty(), ShaderFramebuffer.getGlowFramebuffer().useDepthAttachment ? ShaderFramebuffer.getGlowFramebuffer().getDepthAttachmentView() : null, OptionalDouble.empty())) {
                renderPass.setPipeline(RenderPipelines.GUI);

                NVGRenderer.beginFrame();
                oraculus$renderScoreboardRect(true);
                EventDispatcher.dispatch(new RenderBloomEvent(context, tickDelta));
                NVGRenderer.endFrameAndReset(false);
            }

            ShaderFramebuffer.applyGlowToNVGObjects();
        }

        NVGTextRenderer.blockTextRendering = false;
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getOffHandStack()Lnet/minecraft/item/ItemStack;"), method = "renderHotbar")
    public ItemStack hideOffhandSlot(PlayerEntity player) {
        ItemStack realStack = player.getOffHandStack();
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() &&
                animationsModule.isHideShieldSlotInHotbar() &&
                realStack.getItem() instanceof ShieldItem &&
                animationsModule.isHideShield()) {
            return ItemStack.EMPTY;
        }
        return realStack;
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"),
            index = 5
    )
    private boolean hookScoreboardTextShadow(boolean shadow) {
        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        return overlayModule.isEnabled() && overlayModule.isScoreboardTextShadow();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void resetScoreboardRectDimensions(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        this.sbRectX = 0;
        this.sbRectY = 0;
        this.sbRectWidth = 0;
        this.sbRectHeight = 0;
    }

    /**
     * @author senoe
     * @reason sucks to modify position normally
     */
    @Overwrite
    private void renderScoreboardSidebar(DrawContext drawContext, ScoreboardObjective objective) {
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        SidebarEntry[] sidebarEntrys = scoreboard.getScoreboardEntries(objective)
                .stream()
                .filter(score -> !score.hidden())
                .sorted(SCOREBOARD_ENTRY_COMPARATOR)
                .limit(15L)
                .map(scoreboardEntry -> {
                    Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                    Text textx = scoreboardEntry.name();
                    Text text2 = Team.decorateName(team, textx);
                    Text text3 = scoreboardEntry.formatted(numberFormat);
                    int ix = mc.textRenderer.getWidth(text3);
                    return new SidebarEntry(text2, text3, ix);
                })
                .toArray(SidebarEntry[]::new);
        Text text = objective.getDisplayName();
        int i = mc.textRenderer.getWidth(text);
        int j = i;
        int k = mc.textRenderer.getWidth(": ");

        for (SidebarEntry sidebarEntry : sidebarEntrys) {
            j = Math.max(j, mc.textRenderer.getWidth(sidebarEntry.name()) + (sidebarEntry.scoreWidth() > 0 ? k + sidebarEntry.scoreWidth() : 0));
        }

        final ModuleRepository moduleRepository = OraculusClient.getInstance().getModuleRepository();
        final OverlayModule overlayModule = moduleRepository.getModule(OverlayModule.class);
        final StreamerModeModule streamerModeModule = moduleRepository.getModule(StreamerModeModule.class);

        final boolean textShadow = overlayModule.isEnabled() && overlayModule.isScoreboardTextShadow();

        final boolean isOnHypixel = LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer;
        final boolean hideServerId = isOnHypixel && streamerModeModule.isEnabled() && streamerModeModule.isHidingServerId();

        final float moduleListHeight = overlayModule.getToggledModules().getSettings().isOffsetScoreboard()
                ? overlayModule.getToggledModules().getTotalHeight()
                : 0;

        final float scale = overlayModule.getScoreboardScale();

        int m = sidebarEntrys.length;
        int n = m * 9;
        int o = (int) (drawContext.getScaledWindowHeight() / scale / 2 + n / 3F);
        int q = (int) (drawContext.getScaledWindowWidth() / scale - j - 3);
        int r = (int) (drawContext.getScaledWindowWidth() / scale - 3 + 2);
//        int s = mc.options.getTextBackgroundColor(0.3F);
//        int t = mc.options.getTextBackgroundColor(0.4F);
        int u = o - m * 9;

        if (moduleListHeight != 0 && (moduleListHeight + 20) / scale > u) {
            final int adjustedHeight = (int) ((moduleListHeight + 20) / scale);
            final int difference = adjustedHeight - u;
            u = adjustedHeight;
            o += difference;
        }

        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().scale(scale);

//        // header bg
//        drawContext.fill(q - 2, u - 9 - 1, r, u - 1, t);
//        // entry bg
//        drawContext.fill(q - 2, u - 1, r, o, s);

        drawContext.drawText(mc.textRenderer, text, q + j / 2 - i / 2 - 1, u - 9, Colors.WHITE, textShadow);

        for (int v = 0; v < m; v++) {
            SidebarEntry sidebarEntry2 = sidebarEntrys[v];
            int w = u + v * 9;

            Text name = sidebarEntry2.name();
            final String nameStr = name.getString();

            if (hideServerId && v == 0 && nameStr.contains("/") && nameStr.contains("  ")) {
                final String[] parts = nameStr.split(" {2}");
                if (parts.length > 1) {
                    name = Text.literal("§7" + parts[0] + "  §8§k" + parts[1]);
                }
            }

            drawContext.drawText(mc.textRenderer, name, q - 1, w, Colors.WHITE, textShadow);
            drawContext.drawText(mc.textRenderer, sidebarEntry2.score(), r - sidebarEntry2.scoreWidth() - 1, w, Colors.WHITE, textShadow);
        }

        drawContext.getMatrices().popMatrix();

        this.sbRectX = (q - 2 - 2 - 0.5F) * scale;
        this.sbRectY = (u - 9 - 1 - 1) * scale;
        this.sbRectWidth = ((r - 0.5F) * scale) - this.sbRectX;
        this.sbRectHeight = ((m * 9) + 13) * scale;
    }

    @Unique
    private void oraculus$renderScoreboardRect(final boolean bloom) {
        if (sbRectWidth == 0 || sbRectHeight == 0) {
            return;
        }
        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        final float cornerRadius = overlayModule.getScoreboardCornerRadius();
        if (overlayModule.isScoreboardLiquidGlassV2()) {
            if (!bloom && !LiquidGlassV2Renderer.draw(
                    sbRectX, sbRectY, sbRectWidth, sbRectHeight, cornerRadius,
                    overlayModule.getScoreboardLiquidGlassV2Settings()
            )) {
                oraculus$renderDefaultScoreboardRect();
            }
        } else if (bloom) {
            NVGRenderer.roundedRect(sbRectX, sbRectY, sbRectWidth, sbRectHeight, cornerRadius, ColorUtility.applyOpacity(Colors.BLACK, 0.75F));
        } else {
            oraculus$renderDefaultScoreboardRect();
        }
    }

    @Unique
    private void oraculus$renderDefaultScoreboardRect() {
        final float cornerRadius = OraculusClient.getInstance().getModuleRepository()
                .getModule(OverlayModule.class).getScoreboardCornerRadius();
        NVGRenderer.roundedRect(sbRectX, sbRectY, sbRectWidth, sbRectHeight, cornerRadius, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(sbRectX, sbRectY, sbRectWidth, sbRectHeight, cornerRadius, mc.options.getTextBackgroundColor(0.4F));
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(CallbackInfo ci) {
        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        if (overlayModule.isEnabled() && !overlayModule.isStatusEffectOverlayEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void oraculus$hideNauseaOverlay(final DrawContext context, final float nauseaStrength, final CallbackInfo ci) {
        if (NoRenderModule.shouldSuppressNausea()) {
            ci.cancel();
        }
    }

}
