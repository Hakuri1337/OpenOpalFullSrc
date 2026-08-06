package wtf.oraculus.mixin;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInteractEntityC2SPacket.class)
public interface PlayerInteractEntityC2SPacketAccessor {
    @Accessor("ATTACK")
    static PlayerInteractEntityC2SPacket.InteractTypeHandler getAttackHandler() {
        throw new AssertionError();
    }

    @Accessor
    int getEntityId();

    @Accessor
    PlayerInteractEntityC2SPacket.InteractTypeHandler getType();
}
