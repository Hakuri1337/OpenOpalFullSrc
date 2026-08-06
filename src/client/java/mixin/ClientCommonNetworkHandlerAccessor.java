package mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientCommonNetworkHandler.class)
public interface ClientCommonNetworkHandlerAccessor {

    @Accessor
    Map<Identifier, byte[]> getServerCookies();

}
