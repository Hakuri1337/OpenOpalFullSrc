package wtf.oraculus.mixin;

import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommonPingS2CPacket.class)
public interface CommonPingS2CPacketAccessor {
    @Mutable
    @Accessor
    void setParameter(int parameter);
}
