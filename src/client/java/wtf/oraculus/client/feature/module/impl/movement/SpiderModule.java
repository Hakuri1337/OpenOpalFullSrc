package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.util.math.Direction;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class SpiderModule extends Module {
    private final NumberProperty speedProperty = new NumberProperty("Speed", 0.5D, 0.1D, 10.D, 0.1D).hideIf(this::isBloxd);

    public SpiderModule() {
        super("Spider", "Lets you climb walls.", ModuleCategory.MOVEMENT);
        this.addProperties(this.speedProperty);
    }

    private boolean isBloxd() {
        return OraculusClient.getInstance().getModuleRepository().getModule(PhysicsModule.class).isEnabled();
    }

    @Subscribe(priority = 5)
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player.horizontalCollision && !mc.player.isClimbing()) {
            final PhysicsModule physicsModule = OraculusClient.getInstance().getModuleRepository().getModule(PhysicsModule.class);
            if (physicsModule.isEnabled()) {
                physicsModule.getPhysics().velocity = 8.0D;
            } else {
                mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, this.speedProperty.getValue()));
            }
        }
    }
}
