package wtf.opal.client.feature.module.impl.world.blockfly;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import wtf.opal.client.renderer.MinecraftRenderer;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.animation.Animation;
import wtf.opal.utility.render.animation.Easing;

public final class BlockFlyIsland {
    private final BlockFlyModule parent;
    private Animation blockCounterAnimation;
    private float width = 140.0F;

    public BlockFlyIsland(final BlockFlyModule parent) {
        this.parent = parent;
    }

    public void render(final DrawContext context, final float posX, final float posY) {
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");
        final ItemStack stack = this.parent.getDisplayedBlockStack();
        final int stackSize = stack.getItem() instanceof BlockItem ? stack.getCount() : 0;
        final String stackText = stackSize + " ";
        final String suffix = "block" + (stackSize == 1 ? "" : "s");
        final String speedText = MoveUtility.getBlocksPerSecond() + " b/s";
        final float titleSize = 8.0F;
        final float footerSize = 6.0F;

        this.width = 130.0F + Math.max(
                titleFont.getStringWidth(stackText, titleSize) + footerFont.getStringWidth(suffix, titleSize),
                footerFont.getStringWidth(speedText, footerSize)
        );

        int color = -1;
        if (stack.getItem() instanceof BlockItem blockItem) {
            color = ColorUtility.applyOpacity(blockItem.getBlock().getDefaultMapColor().color, 255);
        }

        final float previousAlpha = NVGRenderer.globalAlpha;
        NVGRenderer.globalAlpha(1.0F);
        NVGRenderer.roundedRect(posX + 5.5F, posY + 4.0F, 17.0F, 17.0F, 8.25F,
                ColorUtility.applyOpacity(color, 120));
        NVGRenderer.globalAlpha(previousAlpha);

        if (!stack.isEmpty()) {
            MinecraftRenderer.addToQueue(() -> {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(posX + 8.0F, posY + 6.5F);
                context.getMatrices().scale(0.75F, 0.75F);
                context.drawItem(stack, 0, 0);
                context.getMatrices().popMatrix();
            });
        }

        NVGRenderer.roundedRect(posX + 28.0F, posY + 11.5F, 85.0F, 2.5F, 1.5F,
                ColorUtility.darker(color, 0.55F));
        final float progressWidth = Math.min(stackSize, 64) / 64.0F * 85.0F;
        if (this.blockCounterAnimation == null) {
            this.blockCounterAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            this.blockCounterAnimation.setValue(progressWidth);
        } else {
            this.blockCounterAnimation.run(progressWidth);
        }
        if (stackSize > 0) {
            NVGRenderer.roundedRectGradient(posX + 28.0F, posY + 11.5F,
                    this.blockCounterAnimation.getValue(), 2.5F, 1.25F,
                    ColorUtility.darker(color, 0.4F), color, 0);
        }

        titleFont.drawString(stackText, posX + 120.0F, posY + 12.0F, titleSize, color);
        footerFont.drawString(suffix,
                posX + 120.0F + titleFont.getStringWidth(stackText, titleSize),
                posY + 12.0F, titleSize, -1);
        footerFont.drawString(speedText, posX + 120.0F, posY + 19.0F, footerSize,
                ColorUtility.MUTED_COLOR);
    }

    public void reset() {
        this.blockCounterAnimation = null;
    }

    public float width() {
        return this.width;
    }
}
