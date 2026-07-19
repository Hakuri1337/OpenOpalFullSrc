package wtf.oraculus.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor()
    int getItemUseCooldown();

    @Accessor()
    void setItemUseCooldown(int ticks);

    @Invoker
    void callDoItemUse();

}
