package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import net.minecraft.client.font.TextHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.module.property.impl.StringProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;

import static wtf.oraculus.client.Constants.mc;

public final class StringPropertyComponent extends PropertyPanel<StringProperty> {

    private boolean focused;
    private int selectionStart, selectionEnd;

    public StringPropertyComponent(final StringProperty property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        setHeight(26);

        super.render(context, mouseX, mouseY, delta);

        final StringProperty property = getProperty();
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        font.drawString(property.getName(), x + 5, y + 8.5F, 7, -1);

        NVGRenderer.roundedRectOutline(x + 5, y + 13, width - 10, 10, 2.5F, 1.5F, 0xff505050);
        NVGRenderer.roundedRect(x + 5, y + 13, width - 10, 10, 2.5F, 0xff191919);

        if (!property.getValue().isEmpty()) {
            final String selectedText = getSelectedText(property.getValue());
            if (!selectedText.isEmpty() && focused) {
                NVGRenderer.rect(x + 7, y + 20 - 5.5F, font.getStringWidth(selectedText, 7), 7, 0xff245292);
            }

            font.drawString(property.getValue(), x + 7, y + 20, 7, ColorUtility.MUTED_COLOR);

            if (focused && selectedText.isEmpty() && mc.player.age % 20 > 8) {
                NVGRenderer.rect(x + 7.5F + font.getStringWidth(property.getValue().substring(0, selectionStart), 7), y + 20 - 5.5F, 0.5F, 7, -1);
            }
        }
    }

    @Override
    public void init() {
        focused = false;
        DropdownClickGUI.typingString = false;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        focused = button == 0 && HoverUtility.isHovering(x, y, width, height, mouseX, mouseY);
        DropdownClickGUI.typingString = focused;

        if (focused) {
            final double relativeX = mouseX - (x + 7);

            float cursorX = 0;

            int index;
            for (index = 0; index < getProperty().getValue().length(); index++) {
                final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

                final float charWidth = font.getStringWidth(String.valueOf(getProperty().getValue().charAt(index)), 7);
                if (relativeX < cursorX + charWidth / 2) {
                    break;
                }
                cursorX += charWidth;
            }

            selectionStart = Math.min(index, getProperty().getValue().length());
            selectionEnd = selectionStart;
        }
    }

    @Override
    public void keyPressed(KeyInput keyInput) {
        if (!focused) {
            return;
        }

        if (keyInput.isSelectAll()) {
            selectionEnd = 0;
            selectionStart = getProperty().getValue().length();
            return;
        }
        if (keyInput.isCopy()) {
            mc.keyboard.setClipboard(getSelectedText(getProperty().getValue()));
            return;
        }
        if (keyInput.isPaste()) {
            paste();
            return;
        }

        final SelectionManager.SelectionType selectionType = keyInput.hasCtrl() ? SelectionManager.SelectionType.WORD : SelectionManager.SelectionType.CHARACTER;
        final int keyCode = keyInput.key();
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            delete(-1, selectionType);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            delete(1, selectionType);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            moveCursor(-1, keyInput.hasShift(), selectionType);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            moveCursor(1, keyInput.hasShift(), selectionType);
            return;
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!focused || !StringHelper.isValidChar(chr)) {
            return;
        }

        insert(Character.toString(chr));
    }

    private void updateSelectionRange(final boolean shiftDown) {
        if (!shiftDown) {
            selectionEnd = selectionStart;
        }
    }

    public void moveCursor(final int offset, final boolean shiftDown) {
        selectionStart = Util.moveCursor(getProperty().getValue(), selectionStart, offset);
        updateSelectionRange(shiftDown);
    }

    public void moveCursorPastWord(final int offset, final boolean shiftDown) {
        selectionStart = TextHandler.moveCursorByWords(getProperty().getValue(), offset, selectionStart, true);
        updateSelectionRange(shiftDown);
    }

    public void paste() {
        this.insert(mc.keyboard.getClipboard());
        this.selectionEnd = this.selectionStart;
    }

    public void moveCursor(final int offset, final boolean shiftDown, final SelectionManager.SelectionType selectionType) {
        switch (selectionType) {
            case CHARACTER -> {
                moveCursor(offset, shiftDown);
            }
            case WORD -> {
                moveCursorPastWord(offset, shiftDown);
            }
        }
    }

    private void insert(final String insertion) {
        String originalString = getProperty().getValue();

        if (selectionEnd != selectionStart) {
            originalString = deleteSelectedText(originalString);
        }
        selectionStart = MathHelper.clamp(selectionStart, 0, originalString.length());

        final String finishedString = new StringBuilder(originalString).insert(selectionStart, insertion).toString();

        getProperty().setValue(finishedString);
        selectionEnd = selectionStart = Math.min(finishedString.length(), selectionStart + insertion.length());
    }

    public void delete(final int offset, final SelectionManager.SelectionType selectionType) {
        switch (selectionType) {
            case CHARACTER -> {
                delete(offset);
            }
            case WORD -> {
                deleteWord(offset);
            }
        }
    }

    public void deleteWord(final int offset) {
        final int i = TextHandler.moveCursorByWords(getProperty().getValue(), offset, selectionStart, true);
        delete(i - selectionStart);
    }

    public void delete(final int offset) {
        if (!getProperty().getValue().isEmpty()) {
            String string;
            if (selectionEnd != selectionStart) {
                string = deleteSelectedText(getProperty().getValue());
            } else {
                final int cursor = Util.moveCursor(getProperty().getValue(), selectionStart, offset);
                final int minCursor = Math.min(cursor, selectionStart);
                final int maxCursor = Math.max(cursor, selectionStart);
                string = new StringBuilder(getProperty().getValue()).delete(minCursor, maxCursor).toString();
                if (offset < 0) {
                    selectionEnd = selectionStart = minCursor;
                }
            }
            getProperty().setValue(string);
        }
    }

    private String deleteSelectedText(final String string) {
        if (selectionEnd == selectionStart) {
            return string;
        }
        final int minSelection = Math.min(selectionStart, selectionEnd);
        final int maxSelection = Math.max(selectionStart, selectionEnd);

        selectionEnd = selectionStart = minSelection;

        return string.substring(0, minSelection) + string.substring(maxSelection);
    }

    private String getSelectedText(final String string) {
        final int minSelected = Math.min(selectionStart, selectionEnd);
        final int maxSelected = Math.max(selectionStart, selectionEnd);
        return string.substring(minSelected, maxSelected);
    }

}
