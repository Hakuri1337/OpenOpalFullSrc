package wtf.opal.mixin;

import net.minecraft.client.sound.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.concurrent.Executor;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("sources")
    Set<Channel.SourceManager> opal$getSources();

    @Accessor("executor")
    Executor opal$getExecutor();
}
