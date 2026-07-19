package wtf.oraculus.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerMoveC2SPacket.class)
public interface PlayerMoveC2SPacketAccessor {

    @Mutable
    @Accessor("x")
    void setX(final double x);

    @Mutable
    @Accessor("y")
    void setY(final double y);

    @Mutable
    @Accessor("z")
    void setZ(final double z);

    @Mutable
    @Accessor("onGround")
    void setOnGround(final boolean onGround);

    @Mutable
    @Accessor("yaw")
    void setYaw(final float yaw);

    @Mutable
    @Accessor("pitch")
    void setPitch(final float pitch);

}
