package wtf.oraculus.client.feature.module.impl.world.breaker;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.duck.ClientPlayerInteractionManagerAccess;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static wtf.oraculus.client.Constants.mc;

public final class BreakerIsland implements IslandTrigger {

    private final BreakerModule parent;

    public BreakerIsland(BreakerModule parent) {
        this.parent = parent;
    }

    private Animation breakProgressAnimation;

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");

        final float titleTextSize = 8;
        final float secondaryTextSize = 6;

        if (parent.getCurrentTarget() == null || mc.world == null
                || mc.world.getBlockState(parent.getCurrentTarget().candidate().getPos()).isAir()) {
            DynamicIslandElement.removeTrigger(this);
            this.onDisable();
            return;
        }

        final Block block = mc.world.getBlockState(parent.getCurrentTarget().candidate().getPos()).getBlock();

        final int color = ColorUtility.applyOpacity(block.getDefaultMapColor().color, 255);

        final float prevGlobalAlpha = NVGRenderer.globalAlpha;
        NVGRenderer.globalAlpha(1);
        NVGRenderer.roundedRect(posX + 5.5f, posY + 4f, 17, 17, 8.25f, ColorUtility.applyOpacity(color, 120));
        NVGRenderer.globalAlpha(prevGlobalAlpha);

        MinecraftRenderer.addToQueue(() -> {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(posX + 8.f, posY + 6.5f);
            context.getMatrices().scale(0.75f, 0.75f);
            context.drawItem(mc.player, block.asItem().getDefaultStack(), 0, 0, 0);
            context.getMatrices().popMatrix();
        });

        NVGRenderer.roundedRect(posX + 28, posY + 11.5f, 85, 2.5f, 1.5f, ColorUtility.darker(color, 0.55f));

        final ClientPlayerInteractionManagerAccess access = (ClientPlayerInteractionManagerAccess) mc.interactionManager;
        final float breakProgress = access.oraculus$currentBreakingProgress();

        final float scaledWidth = (Math.min(breakProgress, 1)) * 85;
        if (this.breakProgressAnimation == null) {
            this.breakProgressAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            this.breakProgressAnimation.setValue(scaledWidth);
        } else {
            this.breakProgressAnimation.run(scaledWidth);
        }
        if (breakProgress > 0) {
            NVGRenderer.roundedRectGradient(posX + 28, posY + 11.5f, this.breakProgressAnimation.getValue(), 2.5f, 1.25f, ColorUtility.darker(color, 0.4f), color, 0);
        }

        titleFont.drawString((int) (breakProgress * 100) + "%", posX + 28 + 85 + 6, posY + 15, 7, -1);
    }

    public void onDisable() {
        this.breakProgressAnimation = null;
    }

    @Override
    public float getIslandWidth() {
        return 140;
    }

    @Override
    public float getIslandHeight() {
        return 25;
    }

    @Override
    public int getIslandPriority() {
        return 3;
    }
}
