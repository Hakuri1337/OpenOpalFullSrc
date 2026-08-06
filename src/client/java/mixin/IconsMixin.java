package mixin;

import net.fabricmc.loader.impl.launch.knot.Knot;
import net.minecraft.client.util.Icons;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(Icons.class)
public final class IconsMixin {

    private IconsMixin() {
    }

    @Inject(
            at = @At("HEAD"),
            method = "getIcon",
            cancellable = true
    )
    private void getIcon(final ResourcePack resourcePack, final String fileName, final CallbackInfoReturnable<InputSupplier<InputStream>> info) {
        info.setReturnValue(() -> Knot.getLauncher().getTargetClassLoader().getResourceAsStream("assets/oraculus/window-icons/" + fileName));
    }
}
