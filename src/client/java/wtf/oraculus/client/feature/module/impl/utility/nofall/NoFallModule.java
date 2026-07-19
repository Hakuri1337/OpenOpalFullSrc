package wtf.oraculus.client.feature.module.impl.utility.nofall;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.nofall.impl.SpoofNoFall;
import wtf.oraculus.client.feature.module.impl.utility.nofall.impl.WatchdogNoFall;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class
NoFallModule extends Module {

    public final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.SPOOF);
    private double fallDistance;

    public NoFallModule() {
        super("No Fall", "Removes your players fall damage.", ModuleCategory.UTILITY);
        addProperties(mode);
        addModuleModes(mode, new WatchdogNoFall(this), new SpoofNoFall(this));
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (mc.player.fallDistance == 0) {
            syncFallDifference();
        }
    }

    public void syncFallDifference() {
        this.fallDistance = mc.player.fallDistance;
    }

    public double getFallDifference() {
        if (mc.player.getAbilities().allowFlying) {
            return 0;
        }
        return mc.player.fallDistance - this.fallDistance;
    }

    @Override
    protected void onEnable() {
        this.fallDistance = 0;

        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public enum Mode {
        SPOOF("Spoof"),
        WATCHDOG("Watchdog");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
