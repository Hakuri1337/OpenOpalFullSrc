package mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {

    @Invoker
    void callSendSequencedPacket(final ClientWorld world, final SequencedPacketCreator packetCreator);

    @Invoker
    void callSyncSelectedSlot();

    @Accessor
    void setBlockBreakingCooldown(int blockBreakingCooldown);

}
