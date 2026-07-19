package wtf.oraculus.client.screen.click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

public interface IOraculusComponent {

    default void init() {
    }

    default void close() {
    }

    void render(final DrawContext context, final int mouseX, final int mouseY, final float delta);

    void mouseClicked(final double mouseX, final double mouseY, final int button);

    default void mouseScrolled(final double mouseX, final double mouseY, final double horizontalAmount, final double verticalAmount) {
    }

    default void mouseReleased(final double mouseX, final double mouseY, final int button) {
    }

    default void keyPressed(KeyInput keyInput) {
    }

    default void charTyped(char chr, int modifiers) {
    }

}
