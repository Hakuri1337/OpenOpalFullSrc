package wtf.oraculus.client.screen.click.dropdown.panel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.screen.click.OraculusPanelComponent;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.ClientTheme;
import wtf.oraculus.utility.render.Scroller;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class CategoryPanel extends OraculusPanelComponent {

    private final Scroller scroller = new Scroller();
    private final ModuleCategory category;

    private Animation openAnimation;
    private final int panelIndex;

    private final boolean lastPanel;
    private boolean closing;
    private boolean draggingAllowed, dragging;
    private float baseX, baseY, dragOffsetX, dragOffsetY, dragMouseOffsetX, dragMouseOffsetY;

    private final List<ModulePanel> modulePanelList = new ArrayList<>();

    public CategoryPanel(final ModuleCategory category, final int panelIndex) {
        this.category = category;
        this.panelIndex = panelIndex;
        this.lastPanel = panelIndex == ModuleCategory.VALUES.length - 1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnimation.run(closing ? 0 : 1);
        updateDrag(mouseX, mouseY);

        if (lastPanel && openAnimation.isFinished() && closing) {
            mc.setScreen(null);
            return;
        }

        final float[] currentY = {y + height};
        final float totalHeight = getTotalHeight();

        final float openAnimationValue = openAnimation.getValue();
        final float scissorHeight = Math.min(mc.getWindow().getScaledHeight() - y, totalHeight * openAnimationValue);
        final float scrollOffset = scroller.getAnimation().getValue();

        NVGRenderer.globalAlpha(openAnimationValue);
        NVGRenderer.scissor(x, y, width, scissorHeight, () -> {
            NVGRenderer.roundedRectVarying(x, y + scrollOffset, width, height, 5, 5, 0, 0, NVGRenderer.BLUR_PAINT);
            final boolean sigmaStyle = ColorUtility.getClientTheme().first.equals(ClientTheme.SIGMA.getColors().first);
            final int panelColor = sigmaStyle ? 0xeef7fbff : 0xd90f0f0f;
            NVGRenderer.roundedRectVarying(x, y + scrollOffset, width, height, 5, 5, 0, 0, panelColor);
            FontRepository.getFont("productsans-bold").drawString(category.getName(), x + 5, y + scrollOffset + 13, 9, -1);
            FontRepository.getFont("materialicons-outlined").drawString(category.getIcon(), x + width - 15.5F, y + scrollOffset + 15, 10, -1);

            for (int i = 0; i < modulePanelList.size(); i++) {
                final ModulePanel panel = modulePanelList.get(i);
                float panelHeight = this.height + (panel.getExpandAnimation().getValue() * panel.getAddedHeight());

                panel.setDimensions(x, currentY[0] + scrollOffset, width, panelHeight);
                panel.setLastModule(i == modulePanelList.size() - 1);

                panel.render(context, mouseX, mouseY, delta);

                currentY[0] += panelHeight;
            }
        });
        NVGRenderer.globalAlpha(1);

        scroller.onScroll(getMaxOffset(totalHeight));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingAllowed && HoverUtility.isHovering(x, y, width, height, mouseX, mouseY)) {
            dragging = true;
            dragMouseOffsetX = (float) mouseX - x;
            dragMouseOffsetY = (float) mouseY - y;
            return;
        }

        modulePanelList.forEach(modulePanel -> modulePanel.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public void keyPressed(KeyInput keyInput) {
        modulePanelList.forEach(modulePanel -> modulePanel.keyPressed(keyInput));
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        modulePanelList.forEach(modulePanel -> modulePanel.charTyped(chr, modifiers));
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }

        modulePanelList.forEach(modulePanel -> modulePanel.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        final float totalHeight = getTotalHeight();

        if (HoverUtility.isHovering(x, y, width, totalHeight, mouseX, mouseY)) {
            scroller.addScroll(verticalAmount, getMaxOffset(totalHeight));
        }

        modulePanelList.forEach(modulePanel -> modulePanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount));
    }

    @Override
    public void init() {
        modulePanelList.clear();
        OraculusClient.getInstance().getModuleRepository().getModulesInCategory(category)
                .stream()
                .filter(module -> module.isVisible())
                .forEach(module -> modulePanelList.add(new ModulePanel(module)));
        modulePanelList.sort(Comparator.comparing(p -> p.getModule().getName()));

        this.openAnimation = new Animation(Easing.EASE_OUT_SINE, 100 + (panelIndex * 80L));
        closing = false;

        modulePanelList.forEach(ModulePanel::init);
    }

    @Override
    public void close() {
        closing = true;

        modulePanelList.forEach(ModulePanel::close);
    }

    private float getTotalHeight() {
        float totalHeight = this.height; // header height

        for (final ModulePanel panel : modulePanelList) {
            panel.getExpandAnimation().run(panel.isExpanded() ? 1 : 0);
            totalHeight += this.height + (panel.getExpandAnimation().getValue() * panel.getAddedHeight());
        }

        return totalHeight;
    }

    private float getMaxOffset(final float totalHeight) {
        final float relativeScreenHeight = mc.getWindow().getScaledHeight() - y;
        final float scissorHeight = Math.min(relativeScreenHeight, totalHeight * openAnimation.getValue());
        final float overflowPadding = scissorHeight == relativeScreenHeight ? y : 0;

        return Math.max(0, totalHeight - scissorHeight + overflowPadding);
    }

    public void setBasePosition(final float baseX, final float baseY) {
        this.baseX = baseX;
        this.baseY = baseY;
    }

    public void setDraggingAllowed(final boolean draggingAllowed) {
        this.draggingAllowed = draggingAllowed;
        if (!draggingAllowed) {
            this.dragging = false;
            this.dragOffsetX = 0;
            this.dragOffsetY = 0;
        }
    }

    public float getDragOffsetX() {
        return dragOffsetX;
    }

    public float getDragOffsetY() {
        return dragOffsetY;
    }

    private void updateDrag(final int mouseX, final int mouseY) {
        if (!draggingAllowed || !dragging || mouseX == -1 || mouseY == -1) {
            return;
        }

        final float newX = mouseX - dragMouseOffsetX;
        final float newY = mouseY - dragMouseOffsetY;
        final float maxX = mc.getWindow().getScaledWidth() - width;
        final float maxY = mc.getWindow().getScaledHeight() - height;

        this.dragOffsetX = Math.max(-baseX, Math.min(maxX - baseX, newX - baseX));
        this.dragOffsetY = Math.max(-baseY, Math.min(maxY - baseY, newY - baseY));
    }

}
