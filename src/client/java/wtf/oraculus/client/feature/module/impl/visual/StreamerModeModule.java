package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.StringProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.chat.ChatReceivedEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static wtf.oraculus.client.Constants.mc;

public final class StreamerModeModule extends Module {

    // TODO: scoreboard replacement, hide other usernames
    private final BooleanProperty hideServerId = new BooleanProperty("Hide server ID", true);
    private final BooleanProperty hideUsername = new BooleanProperty("Hide username", true);
    private final StringProperty customUsername = new StringProperty("Custom username", "You").hideIf(() -> !hideUsername.getValue());

    public StreamerModeModule() {
        super("Streamer Mode", "Features for content creators.", ModuleCategory.VISUAL);
        this.addProperties(hideServerId, hideUsername, customUsername);
    }

    @Subscribe
    public void onChatReceived(final ChatReceivedEvent event) {
        if (hideServerId.getValue() && LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
            final String message = event.getText().getString();

            if (message.startsWith("Sending you to ")) {
                event.setCancelled();

                final String serverId = message.replace("Sending you to ", "").replace("!", "");
                ChatUtility.display(Text.literal("§aSending you to §k" + serverId + "§r§a!"));
            }
        }
    }

    public String filter(String text) {
        if (hideUsername.getValue()) {
            final String customUsername = this.getCustomUsername();
            if (!customUsername.isEmpty()) {
                text = StringUtils.replaceIgnoreCase(text, mc.getSession().getUsername(), customUsername);
            }
        }

        return text;
    }

    public boolean isHidingServerId() {
        return hideServerId.getValue();
    }

    public String getCustomUsername() {
        return customUsername.getValue().trim();
    }

}
