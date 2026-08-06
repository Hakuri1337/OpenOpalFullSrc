package wtf.oraculus.event.impl.game.chat;

import net.minecraft.text.Text;
import wtf.oraculus.event.EventCancellable;

public final class ChatReceivedEvent extends EventCancellable {

    private final Text text;
    private boolean overlay;

    public ChatReceivedEvent(final Text text, final boolean overlay) {
        this.text = text;
        this.overlay = overlay;
    }

    public Text getText() {
        return text;
    }

    public boolean isOverlay() {
        return overlay;
    }

    public void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }

}