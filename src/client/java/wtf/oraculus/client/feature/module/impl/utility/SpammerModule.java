package wtf.oraculus.client.feature.module.impl.utility;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.StringProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class
SpammerModule extends Module {

    private int stage;

    private final Stopwatch stopwatch = new Stopwatch();

    private final NumberProperty delay = new NumberProperty("Delay", 100, 0, 10000, 1);
    private final StringProperty message = new StringProperty("Message", "");

    private final BooleanProperty antiSpamBypass = new BooleanProperty("Anti spam bypass", true);

    public SpammerModule() {
        super("Spammer", "Spams the chat", ModuleCategory.UTILITY);
        addProperties(message, delay, antiSpamBypass);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.getNetworkHandler() == null || message.getValue().isEmpty()) return;

        if (shouldSend()) {
            String messageToSend = message.getValue();

            if (antiSpamBypass.getValue()) {
                messageToSend = Math.random() + " " + messageToSend;
            }

            mc.getNetworkHandler().sendChatMessage(messageToSend);

            stage++;
        }

    }

    private boolean shouldSend() {
        return stopwatch.hasTimeElapsed(delay.getValue().longValue(), true);
    }

}
