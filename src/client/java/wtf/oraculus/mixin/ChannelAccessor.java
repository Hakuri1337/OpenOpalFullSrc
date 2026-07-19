package wtf.oraculus.mixin;

import net.minecraft.client.sound.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.concurrent.Executor;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("sources")
    Set<Channel.SourceManager> oraculus$getSources();

    @Accessor("executor")
    Executor oraculus$getExecutor();
}
