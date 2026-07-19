package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.time.LocalTime;

import static wtf.oraculus.client.Constants.mc;

public final class AmbienceModule extends Module {

    private final BooleanProperty useRealTime = new BooleanProperty("Use real time", false);

    private final NumberProperty time = new NumberProperty("Time", 1000, 0, 23450, 50).hideIf(useRealTime::getValue);
    private final BooleanProperty endSky = new BooleanProperty("End sky", false);

    public AmbienceModule() {
        super("Ambience", "Changes the time of day.", ModuleCategory.VISUAL);
        addProperties(useRealTime, time, endSky);
    }

    @Subscribe
    public void onPreGameTick(final PostGameTickEvent event) {
        if (mc.world == null) {
            return;
        }

        long time = this.time.getValue().longValue();
        if (useRealTime.getValue()) {
            final LocalTime localTime = LocalTime.now();
            final int hour = localTime.getHour();
            final int minute = localTime.getMinute();

            final long totalMinutes = hour * 60L + minute;
            long minecraftTime = (totalMinutes * 1000L / 1440L) * 24L;
            time = (minecraftTime + 18000L) % 24000L;
        }

        mc.world.getLevelProperties().setTimeOfDay(time);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            event.setCancelled();
        }
    }

    public boolean isEndSky() {
        return this.endSky.getValue();
    }
}
