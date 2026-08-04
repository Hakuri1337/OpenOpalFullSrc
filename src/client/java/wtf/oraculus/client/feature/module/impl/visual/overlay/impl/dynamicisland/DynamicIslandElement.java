package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.preset.DefaultIsland;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.shader.LiquidGlassV2Renderer;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.client.ModuleToggleEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;
import net.minecraft.util.Util;

import java.util.Collections;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class DynamicIslandElement implements IOverlayElement, IEventSubscriber {

    private static final String SEARCH_PLACEHOLDER = "Type to search modules";
    private static final float SEARCH_WIDTH = 210;
    private static final float SEARCH_HORIZONTAL_MARGIN = 12;
    private static final float SEARCH_TEXT_SIZE = 9.5F;
    private static final float SEARCH_ICON_SIZE = 12;
    private static final float SEARCH_CONTENT_GAP = 9;

    private static final List<IslandTrigger> ACTIVE_TRIGGERS = Lists.newArrayList(new DefaultIsland());
    private final OverlayModule module;

    public DynamicIslandElement(OverlayModule module) {
        this.module = module;
        EventDispatcher.subscribe(this);
    }

    private boolean positioned;

    @Override
    public void render(DrawContext context, float delta, boolean isBloom) {
        if (SORTING_DIRTY) {
            this.sort();
        }

        final IslandTrigger trigger = this.getDecidingTrigger();
        final boolean custom;
        float width = trigger.getIslandWidth(), height = trigger.getIslandHeight();
        float x, y;
        if (trigger instanceof CustomIslandTrigger customTrigger) {
            x = customTrigger.getIslandX();
            y = customTrigger.getIslandY();
            custom = true;
        } else {
            x = this.module.isDynamicIslandLeftAligned() ? 4 : (mc.getWindow().getScaledWidth() - width) / 2.0F;
            y = this.module.isDynamicIslandLeftAligned() ? 6 : 10;
            custom = false;
        }

        final DropdownClickGUI clickGUI = mc.currentScreen instanceof DropdownClickGUI screen ? screen : null;
        final float searchProgress = clickGUI == null ? 0 : clickGUI.getIslandSearchProgress();
        final boolean renderSearch = clickGUI != null && (!clickGUI.isClosing() || searchProgress > 0.001F);

        if (renderSearch) {
            this.renderClickGuiSearch(clickGUI, x, y, width, height, searchProgress, isBloom);
            return;
        }

        SEARCH_BOUNDS_VISIBLE = false;
        this.searchTransitionActive = false;

        this.updateAnimations(x, y, width, height);

        final float animatedX = this.xAnimation.getValue(), animatedY = this.yAnimation.getValue();
        final float animatedWidth = this.widthAnimation.getValue(), animatedHeight = this.heightAnimation.getValue();

        final float progress = Math.min(1, this.heightAnimation.getProgress());

        // The bloom pass is already inside Minecraft's render pass. Rendering
        // an island head there can lazily upload a skin texture, which is
        // illegal while that pass is open. Keep the island background in bloom
        // but render all trigger content only in the normal HUD pass.
        if (isBloom) {
            if (!custom && !this.module.isDynamicIslandLiquidGlassV2()) {
                this.renderIslandBackground(animatedX, animatedY, animatedWidth, animatedHeight);
            }
            return;
        }

        final Runnable render = () -> trigger.renderIsland(context, animatedX, animatedY, animatedWidth, animatedHeight, progress);

        if (custom) {
            render.run();
        } else {
            this.renderIslandBackground(animatedX, animatedY, animatedWidth, animatedHeight);

            if (!(trigger instanceof DefaultIsland)) {
                NVGRenderer.globalAlpha(progress);
            }
            NVGRenderer.scissor(animatedX, animatedY, animatedWidth, animatedHeight, render);
            NVGRenderer.globalAlpha(1);
        }
    }

    @Override
    public void onResize() {
        this.positioned = false;
    }

    @Subscribe
    public void onModuleToggle(ModuleToggleEvent event) {
        if (event.getModule() instanceof IslandTrigger trigger) {
            if (event.isEnabled()) {
                addTrigger(trigger);
            } else {
                removeTrigger(trigger);
            }
        }
    }

    public static void addTrigger(IslandTrigger trigger) {
        if (!ACTIVE_TRIGGERS.contains(trigger)) {
            ACTIVE_TRIGGERS.add(trigger);
            SORTING_DIRTY = true;
        }
    }

    public static void removeTrigger(IslandTrigger trigger) {
        if (ACTIVE_TRIGGERS.remove(trigger)) {
            SORTING_DIRTY = true;
        }
    }

    private static boolean SORTING_DIRTY;

    private void sort() {
        Collections.sort(ACTIVE_TRIGGERS);
        SORTING_DIRTY = false;
    }

    private void updateAnimations(float x, float y, float width, float height) {
        if (!this.positioned) {
            this.xAnimation.setValue(x);
            this.yAnimation.setValue(y);

            this.widthAnimation.setValue(width);
            this.heightAnimation.setValue(height);

            this.positioned = true;
        } else {
            this.xAnimation.run(x);
            this.yAnimation.run(y);

            this.widthAnimation.run(width);
            this.heightAnimation.run(height);
        }
    }

    public void renderIslandBackground(float x, float y, float width, float height) {
        if (this.module.isDynamicIslandLiquidGlassV2()
                && LiquidGlassV2Renderer.draw(
                        x + 1, y + 1, width - 2, height - 2, 13,
                        this.module.getDynamicIslandLiquidGlassV2Settings()
                )) {
            return;
        }

        NVGRenderer.roundedRect(x + 1, y + 1, width - 2, height - 2, 13, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(x + 1, y + 1, width - 2, height - 2, 13, 0x80090909);
    }

    public static boolean isBackgroundVisible() {
        if (mc.currentScreen instanceof DropdownClickGUI clickGUI
                && (!clickGUI.isClosing() || clickGUI.getIslandSearchProgress() > 0.001F)) {
            return true;
        }

        return !(ACTIVE_TRIGGERS.getFirst() instanceof CustomIslandTrigger);
    }

    private void renderClickGuiSearch(
            final DropdownClickGUI clickGUI,
            final float x,
            final float y,
            final float width,
            final float height,
            final float rawProgress,
            final boolean isBloom
    ) {
        if (!this.searchTransitionActive) {
            if (!this.positioned) {
                this.updateAnimations(x, y, width, height);
            }

            this.searchBaseX = this.xAnimation.getValue();
            this.searchBaseY = this.yAnimation.getValue();
            this.searchBaseWidth = Math.max(2, this.widthAnimation.getValue());
            this.searchBaseHeight = Math.max(2, this.heightAnimation.getValue());
            this.searchWidthAnimation.setValue(this.searchBaseWidth);
            this.searchTransitionActive = true;
        }

        final float transition = Math.max(0, rawProgress);
        final float textAlpha = Math.min(1, transition);
        final float availableWidth = mc.getWindow().getScaledWidth() - SEARCH_HORIZONTAL_MARGIN * 2;
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");
        final boolean focused = clickGUI.isSearchFocused();
        final String searchText = clickGUI.getSearchText();
        final boolean searchActive = focused || !searchText.isEmpty();
        final String renderedText = searchActive ? searchText : SEARCH_PLACEHOLDER;
        final float focusedWidth = SEARCH_CONTENT_GAP * 3
                + SEARCH_ICON_SIZE
                + font.getStringWidth(searchText, SEARCH_TEXT_SIZE);
        final float contentWidth = searchActive ? focusedWidth : SEARCH_WIDTH;
        final float targetWidth = clickGUI.isClosing()
                ? this.searchBaseWidth
                : Math.max(SEARCH_ICON_SIZE + SEARCH_CONTENT_GAP * 3, Math.min(contentWidth, availableWidth));

        this.searchWidthAnimation.run(targetWidth);
        final float animatedWidth = this.searchWidthAnimation.getValue();
        final float animatedX = this.module.isDynamicIslandLeftAligned()
                ? this.searchBaseX
                : this.searchBaseX + (this.searchBaseWidth - animatedWidth) / 2;

        SEARCH_BOUNDS_X = animatedX;
        SEARCH_BOUNDS_Y = this.searchBaseY;
        SEARCH_BOUNDS_WIDTH = animatedWidth;
        SEARCH_BOUNDS_HEIGHT = this.searchBaseHeight;
        SEARCH_BOUNDS_VISIBLE = true;

        if (!isBloom || !this.module.isDynamicIslandLiquidGlassV2()) {
            this.renderIslandBackground(
                    animatedX,
                    this.searchBaseY,
                    animatedWidth,
                    this.searchBaseHeight
            );
        }

        if (isBloom || textAlpha <= 0) {
            return;
        }

        final float iconX = animatedX + SEARCH_CONTENT_GAP;
        final float iconY = this.searchBaseY + (this.searchBaseHeight - SEARCH_ICON_SIZE) / 2;
        final float textX = iconX + SEARCH_ICON_SIZE + SEARCH_CONTENT_GAP;
        final float textY = this.searchBaseY + this.searchBaseHeight / 2 + SEARCH_TEXT_SIZE * 0.38F;

        NVGRenderer.scissor(animatedX, this.searchBaseY, animatedWidth, this.searchBaseHeight, () -> {
            NVGRenderer.globalAlpha(textAlpha);
            SearchSvgIcon.INSTANCE.render(iconX, iconY, SEARCH_ICON_SIZE);
            if (!renderedText.isEmpty()) {
                font.drawString(renderedText, textX, textY, SEARCH_TEXT_SIZE, -1);
            }
            if (focused && (Util.getMeasuringTimeMs() / 500L) % 2L == 0L) {
                final int cursor = Math.max(0, Math.min(clickGUI.getSearchCursor(), searchText.length()));
                final float cursorX = textX + font.getStringWidth(searchText.substring(0, cursor), SEARCH_TEXT_SIZE);
                NVGRenderer.rect(cursorX + 0.5F, textY - 7.5F, 0.75F, 9, -1);
            }
            NVGRenderer.globalAlpha(1);
        });
    }

    private final Animation xAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private final Animation yAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);

    private final Animation widthAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private final Animation heightAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private final Animation searchWidthAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);

    private boolean searchTransitionActive;
    private float searchBaseX;
    private float searchBaseY;
    private float searchBaseWidth;
    private float searchBaseHeight;

    private static boolean SEARCH_BOUNDS_VISIBLE;
    private static float SEARCH_BOUNDS_X;
    private static float SEARCH_BOUNDS_Y;
    private static float SEARCH_BOUNDS_WIDTH;
    private static float SEARCH_BOUNDS_HEIGHT;

    public static boolean isSearchHovered(final double mouseX, final double mouseY) {
        return SEARCH_BOUNDS_VISIBLE
                && mouseX >= SEARCH_BOUNDS_X
                && mouseX <= SEARCH_BOUNDS_X + SEARCH_BOUNDS_WIDTH
                && mouseY >= SEARCH_BOUNDS_Y
                && mouseY <= SEARCH_BOUNDS_Y + SEARCH_BOUNDS_HEIGHT;
    }

    public boolean isAnimationFinished() {
        return this.xAnimation.isFinished();
    }

    public float getAnimatedX() {
        return this.xAnimation.getValue();
    }

    public float getAnimatedY() {
        return this.yAnimation.getValue();
    }

    public float getAnimatedWidth() {
        return this.widthAnimation.getValue();
    }

    public float getAnimatedHeight() {
        return this.heightAnimation.getValue();
    }

    @Override
    public boolean isActive() {
        if (mc.currentScreen instanceof DropdownClickGUI clickGUI
                && (!clickGUI.isClosing() || clickGUI.getIslandSearchProgress() > 0.001F)) {
            return true;
        }

        return !(this.getDecidingTrigger() instanceof CustomIslandTrigger);
    }

    private IslandTrigger getDecidingTrigger() {
        return ACTIVE_TRIGGERS.getFirst();
    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
