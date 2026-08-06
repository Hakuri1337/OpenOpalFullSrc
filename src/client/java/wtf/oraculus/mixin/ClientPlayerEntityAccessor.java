package wtf.oraculus.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {

    @Accessor
    void setInSneakingPose(final boolean inSneakingPose);

    @Accessor
    double getLastXClient();

    @Accessor
    double getLastYClient();

    @Accessor
    double getLastZClient();

    @Mutable
    @Accessor
    void setLastXClient(double lastX);

    @Mutable
    @Accessor
    void setLastYClient(double lastBaseY);

    @Mutable
    @Accessor
    void setLastZClient(double lastZ);

    @Mutable
    @Accessor
    void setLastYawClient(float lastYaw);

    @Mutable
    @Accessor
    void setLastPitchClient(float lastPitch);

    @Mutable
    @Accessor
    void setLastOnGround(boolean lastOnGround);

    @Accessor
    float getLastYawClient();

    @Accessor
    float getLastPitchClient();

    @Accessor
    boolean isLastOnGround();

    @Accessor
    boolean getLastSprinting();

    @Accessor
    int getTicksSinceLastPositionPacketSent();

    @Mutable
    @Accessor("lastSprinting")
    void setLastSprinting(final boolean lastSprinting);

    @Mutable
    @Accessor
    void setTicksSinceLastPositionPacketSent(int ticksSinceLastPositionPacketSent);

    @Invoker
    void callSendMovementPackets();

    @Invoker
    boolean callCanStartSprinting();
}
