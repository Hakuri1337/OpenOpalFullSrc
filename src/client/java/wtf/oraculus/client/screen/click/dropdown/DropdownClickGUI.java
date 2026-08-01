package wtf.oraculus.client.screen.click.dropdown;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.ClickGUIModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.screen.click.dropdown.panel.CategoryPanel;
import wtf.oraculus.utility.misc.Multithreading;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static wtf.oraculus.client.Constants.mc;

public final class DropdownClickGUI extends Screen {

    private static final float SEARCH_PANEL_OFFSET = 23;

    private final List<CategoryPanel> categoryPanelList = new ArrayList<>();
    private final Animation islandSearchAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private TextFieldWidget searchField;
    public static boolean displayingBinds, selectingBind, typingString;
    private boolean closing;

    public DropdownClickGUI() {
        super(Text.empty());

        int index = 0;
        for (ModuleCategory category : ModuleCategory.VALUES) {
            categoryPanelList.add(new CategoryPanel(category, index));

            index++;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        final boolean frameStarted = NVGRenderer.beginFrame();

        this.islandSearchAnimation.run(this.closing ? 0 : 1);

        displayingBinds = PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_TAB);
        final boolean allowDrag = OraculusClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).isAllowDrag();

        final int categoryAmount = categoryPanelList.size();
        for (int i = 0; i < categoryAmount; i++) {
            final CategoryPanel panel = categoryPanelList.get(i);

            final float y = 25 + SEARCH_PANEL_OFFSET * this.islandSearchAnimation.getValue();
            final float width = 110;
            final float spacing = 10;
            final float height = 20;

            final float totalWidth = categoryAmount * width + (categoryAmount - 1) * spacing;

            final float startX = (mc.getWindow().getScaledWidth() - totalWidth) / 2;
            final float x = startX + i * (width + spacing);

            panel.setBasePosition(x, y);
            panel.setDraggingAllowed(allowDrag);
            panel.setDimensions(x + panel.getDragOffsetX(), y + panel.getDragOffsetY(), width, height);
            panel.render(context, mouseX, mouseY, delta);
        }

        if (frameStarted) {
            NVGRenderer.endFrameAndReset(true);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && DynamicIslandElement.isSearchHovered(click.x(), click.y())) {
            this.searchField.setFocused(true);
            this.searchField.setCursorToEnd(false);
            typingString = true;
            return true;
        }

        if (this.isSearchFocused()) {
            this.blurSearch();
        }

        final boolean allowDrag = OraculusClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).isAllowDrag();
        categoryPanelList.forEach(categoryPanel -> categoryPanel.setDraggingAllowed(allowDrag));
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseClicked(click.x(), click.y(), click.button()));

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseReleased(click.x(), click.y(), click.button()));

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount));

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (this.isSearchFocused()) {
            if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE || keyInput.key() == GLFW.GLFW_KEY_ENTER) {
                this.blurSearch();
            } else {
                this.searchField.keyPressed(keyInput);
            }
            return true;
        }

        // needed for close functionality
        super.keyPressed(keyInput);

        categoryPanelList.forEach(categoryPanel -> categoryPanel.keyPressed(keyInput));

        return true;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (this.isSearchFocused()) {
            this.searchField.charTyped(charInput);
            return true;
        }

        categoryPanelList.forEach(categoryPanel -> categoryPanel.charTyped((char) charInput.codepoint(), charInput.modifiers()));
        return true;
    }

    @Override
    protected void init() {
        this.closing = false;
        this.islandSearchAnimation.setValue(0);
        this.islandSearchAnimation.setFinished(false);
        this.searchField = new TextFieldWidget(mc.textRenderer, 0, 0, 1, 1, Text.literal("Module search"));
        this.searchField.setMaxLength(64);
        this.searchField.setDrawsBackground(false);
        this.searchField.setChangedListener(this::applySearch);
        this.applySearch("");
        typingString = false;
        categoryPanelList.forEach(CategoryPanel::init);
    }

    @Override
    public void close() {
        if (selectingBind) {
            return;
        }

        this.closing = true;
        this.blurSearch();
        categoryPanelList.forEach(CategoryPanel::close);

        Multithreading.schedule(
                () -> OraculusClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).setEnabled(false),
                100, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public float getIslandSearchProgress() {
        return this.islandSearchAnimation.getValue();
    }

    public boolean isClosing() {
        return this.closing;
    }

    public boolean isSearchFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    public String getSearchText() {
        return this.searchField == null ? "" : this.searchField.getText();
    }

    public int getSearchCursor() {
        return this.searchField == null ? 0 : this.searchField.getCursor();
    }

    private void blurSearch() {
        if (this.searchField == null) {
            return;
        }

        this.searchField.setFocused(false);
        typingString = false;
    }

    private void applySearch(final String query) {
        this.categoryPanelList.forEach(panel -> panel.setSearchQuery(query));
    }

}
