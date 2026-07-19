package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.item.BlockItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.MinecraftClientAccessor;

import static wtf.oraculus.client.Constants.mc;

public final class FastPlaceModule extends Module {

    private final NumberProperty delay = new NumberProperty("Delay", "ms", 0.0D, 0.0D, 500.0D, 10.0D);
    private final MultipleBooleanProperty itemTypes = new MultipleBooleanProperty("Item types",
            new BooleanProperty("Blocks", true),
            new BooleanProperty("Throwables", false)
    );

    private long lastPlaceReset;

    public FastPlaceModule() {
        super("FastPlace", "Speeds up block placement and throwable usage.", ModuleCategory.UTILITY);
        this.addProperties(this.delay, this.itemTypes);
    }

    @Override
    protected void onEnable() {
        this.lastPlaceReset = 0L;
        super.onEnable();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        final ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - this.lastPlaceReset < this.delay.getValue().longValue()) {
            return;
        }

        final Item item = stack.getItem();
        boolean shouldFastPlace = this.itemTypes.getProperty("Blocks").getValue() && item instanceof BlockItem;

        if (this.itemTypes.getProperty("Throwables").getValue() && this.isThrowable(item)) {
            shouldFastPlace = true;
        }

        if (!shouldFastPlace) {
            return;
        }

        ((MinecraftClientAccessor) mc).setItemUseCooldown(0);
        this.lastPlaceReset = now;
    }

    private boolean isThrowable(final Item item) {
        return item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderPearlItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof FishingRodItem
                || item instanceof TridentItem;
    }
}
