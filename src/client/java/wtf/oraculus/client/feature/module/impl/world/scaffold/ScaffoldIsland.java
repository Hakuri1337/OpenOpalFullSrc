package wtf.oraculus.client.feature.module.impl.world.scaffold;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.player.MoveUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldIsland {

    private final ScaffoldModule parent;

    public ScaffoldIsland(ScaffoldModule parent) {
        this.parent = parent;
    }

    private float width;

    private Animation blockCounterAnimation;

    public void render(DrawContext context, float posX, float posY) {
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");

        final float titleTextSize = 8;
        final float secondaryTextSize = 6;

        final ItemStack handStack = mc.player.getMainHandStack().getItem() instanceof BlockItem ?
                mc.player.getMainHandStack() : mc.player.getOffHandStack();
        final boolean isBlock = handStack.getItem() instanceof BlockItem;

        final int stackSize = isBlock ? handStack.getCount() : 0;
        final String stackText = stackSize + " ";
        final String postStackText = "block" + (stackSize != 1 ? "s" : "");
        final String bpsText = MoveUtility.getBlocksPerSecond() + " b/s";

        width = 130 + Math.max(titleFont.getStringWidth(stackText, titleTextSize) + footerFont.getStringWidth(postStackText, titleTextSize), footerFont.getStringWidth(bpsText, secondaryTextSize));

        // TODO: replace with scaffold block
        int color = -1;
        if (isBlock) {
            color = ColorUtility.applyOpacity(((BlockItem) handStack.getItem()).getBlock().getDefaultMapColor().color, 255);
        }

        final float prevGlobalAlpha = NVGRenderer.globalAlpha;
        NVGRenderer.globalAlpha(1);
        NVGRenderer.roundedRect(posX + 5.5f, posY + 4f, 17, 17, 8.25f, ColorUtility.applyOpacity(color, 120));
        NVGRenderer.globalAlpha(prevGlobalAlpha);

        if (handStack.getItem() instanceof BlockItem) {
            MinecraftRenderer.addToQueue(() -> {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(posX + 8.f, posY + 6.5f);
                context.getMatrices().scale(0.75f, 0.75f);
                context.drawItem(mc.player, handStack, 0, 0, 0);
                context.getMatrices().popMatrix();
            });
        }

        NVGRenderer.roundedRect(posX + 28, posY + 11.5f, 85, 2.5f, 1.5f, ColorUtility.darker(color, 0.55f));

        final float scaledWidth = (Math.min(stackSize, 64) / 64f) * 85;
        if (this.blockCounterAnimation == null) {
            this.blockCounterAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            this.blockCounterAnimation.setValue(scaledWidth);
        } else {
            this.blockCounterAnimation.run(scaledWidth);
        }
        if (stackSize > 0) {
            NVGRenderer.roundedRectGradient(posX + 28, posY + 11.5f, this.blockCounterAnimation.getValue(), 2.5f, 1.25f, ColorUtility.darker(color, 0.4f), color, 0);
        }

        titleFont.drawString(stackText, posX + 28 + 85 + 7, posY + 12, titleTextSize, color);
        footerFont.drawString(postStackText, posX + 28 + 85 + 7 + titleFont.getStringWidth(stackText, titleTextSize), posY + 12, titleTextSize, -1);

        footerFont.drawString(bpsText, posX + 28 + 85 + 7, posY + 12 + titleTextSize - 1, secondaryTextSize, ColorUtility.MUTED_COLOR);
    }

    public void onDisable() {
        this.blockCounterAnimation = null;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return 25;
    }
}
