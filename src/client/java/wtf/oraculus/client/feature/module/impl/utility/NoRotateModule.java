package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.oraculus.client.feature.helper.impl.player.rotation.handler.RotationMouseHandler;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.player.teleport.PostTeleportEvent;
import wtf.oraculus.event.impl.game.player.teleport.PreTeleportEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

public final class
NoRotateModule extends Module {
    private final RotationProperty rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE);
    private final BooleanProperty ignoreTeleports = new BooleanProperty("Ignore teleports", true);

    public NoRotateModule() {
        super("No Rotate", "Prevents the server from setting your rotation.", ModuleCategory.UTILITY);
        this.addProperties(this.rotationProperty.get(), ignoreTeleports);
    }

    private Vec2f rotation;

    @Subscribe
    public void onPreTeleport(final PreTeleportEvent event) {
        if (this.ignoreTeleports.getValue()) {
            final EntityPosition change = event.getChange();
            if (change.position().squaredDistanceTo(mc.player.getEntityPos()) >= 100.0D) {
                return;
            }
        }
        final Set<PositionFlag> relatives = event.getRelatives();
        if (!relatives.contains(PositionFlag.X_ROT) || !relatives.contains(PositionFlag.Y_ROT)) {
            this.rotation = RotationHelper.getClientHandler().getRotation();
        }
    }

    @Subscribe
    public void onPostTeleport(final PostTeleportEvent event) {
        if (this.rotation != null) {
            RotationHelper.getClientHandler().setRotation(this.rotation);

            final RotationMouseHandler rotationHandler = RotationHelper.getHandler();
            rotationHandler.rotate(this.rotation, this.rotationProperty.createModel());

            final EntityPosition change = event.change(); // teleport rotation
            rotationHandler.setTickRotation(new Vec2f(change.yaw(), change.pitch()));

            rotationHandler.reverse();

            this.rotation = null;
        }
    }
}
