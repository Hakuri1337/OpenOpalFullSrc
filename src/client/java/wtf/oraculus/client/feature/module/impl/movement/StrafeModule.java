package wtf.oraculus.client.feature.module.impl.movement;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.player.MoveUtility;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class
StrafeModule extends Module {

    private final MultipleBooleanProperty conditions = new MultipleBooleanProperty("Conditions",
            new BooleanProperty("On ground", true),
            new BooleanProperty("In air", true));

    private final BooleanProperty ncpMax = new BooleanProperty("NCP Max", false);

    private final NumberProperty strength = new NumberProperty("Strength", "%", 100, 1, 100, 1);

    public StrafeModule() {
        super("Strafe", "Makes you strafe more.", ModuleCategory.MOVEMENT);
        this.addProperties(this.conditions, this.strength, this.ncpMax);
    }

    @Subscribe(priority = 999)
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player != null && MoveUtility.isMoving()) {
            final boolean allowOnGround = conditions.getProperty("On ground").getValue();
            final boolean allowInAir = conditions.getProperty("In air").getValue();

            if (!allowOnGround && !allowInAir) {
                return;
            }
            if (!allowOnGround && mc.player.isOnGround()) {
                return;
            }
            if (!allowInAir && !mc.player.isOnGround()) {
                return;
            }

            final boolean itemSlowdown = mc.player.isUsingItem() && !OraculusClient.getInstance().getModuleRepository().getModule(NoSlowModule.class).isEnabled();
            double speed = MoveUtility.getSpeed();
            if (ncpMax.getValue() && !mc.player.isInFluid() && !mc.player.isSneaking() && LocalDataWatch.get().ticksSinceTeleport > 5 && !PlayerUtility.isInsideBlock() && !itemSlowdown) {
                speed = Math.max(speed, MoveUtility.getSwiftnessSpeed(0.2873D) - (0.0001D * RandomUtility.RANDOM.nextDouble()));
            }
            MoveUtility.setSpeed(speed, strength.getValue());
        }
    }

}
