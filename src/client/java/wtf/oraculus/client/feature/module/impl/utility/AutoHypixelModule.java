package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.StringProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.client.notification.NotificationType;
import wtf.oraculus.event.impl.game.chat.ChatReceivedEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.Multithreading;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.concurrent.TimeUnit;

public final class AutoHypixelModule extends Module {

    private final BooleanProperty autoGGEnabled = new BooleanProperty("Enabled", true);
    private final StringProperty autoGGMessage = new StringProperty("Message", "gg").hideIf(() -> !autoGGEnabled.getValue());

    private final BooleanProperty autoPlayEnabled = new BooleanProperty("Enabled", true);
    private final NumberProperty autoPlayDelay = new NumberProperty("Delay", "s", 2.5, 0, 8, 0.5).hideIf(() -> !autoPlayEnabled.getValue());

    private final BooleanProperty autoLeaveOnPlayerBan = new BooleanProperty("Auto leave on ban", false);

    private long lastAutoGGMessage;

    public AutoHypixelModule() {
        super("Auto Hypixel", "Useful features for Hypixel.", ModuleCategory.UTILITY);
        addProperties(
                new GroupProperty("Auto GG", autoGGEnabled, autoGGMessage),
                new GroupProperty("Auto Play", autoPlayEnabled, autoPlayDelay),
                autoLeaveOnPlayerBan
        );
    }

    @Subscribe
    public void onChatReceived(final ChatReceivedEvent event) {
        if (!(LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer)) {
            return;
        }

        final HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (location == null) {
            return;
        }

        final String message = event.getText().getString();

        if (autoLeaveOnPlayerBan.getValue() && message.equals("A player has been removed from your game.")) {
            ChatUtility.sendCommand("l");

            OraculusClient.getInstance().getNotificationManager()
                    .builder(NotificationType.INFO)
                    .title(this.getName())
                    .description("A player in your game got banned.")
                    .duration(2000)
                    .buildAndPublish();

            return;
        }

        if (autoGGEnabled.getValue() && System.currentTimeMillis() - this.lastAutoGGMessage > 5000 && HypixelServer.KARMA_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(message).matches())) {
            ChatUtility.sendCommand("ac " + autoGGMessage.getValue());
            this.lastAutoGGMessage = System.currentTimeMillis();
        }

        if (autoPlayEnabled.getValue()) {
            if (message.equals("Queued! Use the bed to cancel!")) {
                this.scheduleAutoPlay();
            } else if (!location.isLobby()) {
                for (final Text sibling : event.getText().getSiblings()) {
                    final ClickEvent clickEvent = sibling.getStyle().getClickEvent();
                    if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.RUN_COMMAND) {
                        continue;
                    }

                    if (!((ClickEvent.RunCommand) clickEvent).command().startsWith("/play ")) {
                        continue;
                    }

                    this.scheduleAutoPlay();
                    break;
                }
            }
        }
    }

    private void scheduleAutoPlay() {
        final HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (location == null) {
            return;
        }

        final double delay = autoPlayDelay.getValue();
        final int delayMs = (int) (delay * 1000);

        Multithreading.schedule(() -> ChatUtility.sendCommand("play " + location.mode()), delayMs, TimeUnit.MILLISECONDS);

        OraculusClient.getInstance().getNotificationManager()
                .builder(NotificationType.SUCCESS)
                .title(this.getName())
                .description("Auto Play" + (delay > 0 ? " in " + delay + "s" : "") + "!")
                .duration(delayMs + 200)
                .buildAndPublish();
    }

}
