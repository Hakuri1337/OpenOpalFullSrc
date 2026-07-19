package wtf.oraculus.client.feature.module.impl.world;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class FastBreakModule extends Module {

    private final BooleanProperty speedEnabled = new BooleanProperty("Enabled", true);
    private final NumberProperty speed = new NumberProperty("Speed", "%", 20, 1, 100, 1);

    private final BooleanProperty breakCooldownEnabled = new BooleanProperty("Enabled", true);
    private final NumberProperty breakCooldown = new NumberProperty("Cooldown", 0, 0, 5, 1);

    private final MultipleBooleanProperty breakSlowdowns = new MultipleBooleanProperty("Break slowdown",
            new BooleanProperty("In air", true),
            new BooleanProperty("In water", true),
            new BooleanProperty("Mining fatigue", true)
    );

    private final BooleanProperty spoofGroundState = new BooleanProperty("Spoof ground state", false);

    public FastBreakModule() {
        super("Fast Break", "Breaks blocks quicker.", ModuleCategory.WORLD);
        addProperties(
                new GroupProperty("Speed", speedEnabled, speed),
                new GroupProperty("Break cooldown", breakCooldownEnabled, breakCooldown),
                breakSlowdowns, spoofGroundState.hideIf(() -> breakSlowdowns.getProperty("In air").getValue())
        );
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (spoofGroundState.getValue() && mc.interactionManager.isBreakingBlock() && !mc.player.isTouchingWater() && !breakSlowdowns.getProperty("In air").getValue()) {
            event.setOnGround(true);
        }
    }

    public boolean isSpeedEnabled() {
        return speedEnabled.getValue();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public boolean isBreakCooldownEnabled() {
        return breakCooldownEnabled.getValue();
    }

    public int getBreakCooldown() {
        return breakCooldown.getValue().intValue();
    }

    public MultipleBooleanProperty getBreakSlowdowns() {
        return breakSlowdowns;
    }

}
