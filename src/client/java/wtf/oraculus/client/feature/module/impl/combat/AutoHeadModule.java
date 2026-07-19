package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.PlayerHeadItem;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class AutoHeadModule extends Module {

    private final NumberProperty healDelay = new NumberProperty("Heal Delay", 750, 0, 3000, 50),
            healthPercent = new NumberProperty("Health", "%", 50, 5, 95, 5);

    private final Stopwatch stopwatch = new Stopwatch();

    private boolean swapBack;

    public AutoHeadModule() {
        super("Auto Head", "Automatically eats golden heads.", ModuleCategory.COMBAT);
        addProperties(healDelay, healthPercent);
    }

    @Subscribe(priority = -10)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (swapBack) {
            SlotHelper slotHelper = SlotHelper.getInstance();
            slotHelper.stop();
            slotHelper.sync(true, true);

            swapBack = false;
        }
    }

    @Subscribe(priority = -10)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if ((mc.player.getHealth() / mc.player.getMaxHealth()) * 100 <= healthPercent.getValue()
                && stopwatch.hasTimeElapsed(healDelay.getValue().longValue(), false)) {
            final int headSlot = getHeadSlot();
            if (headSlot == -1) return;

            if (mc.player.getAbsorptionAmount() > 2) {
                return;
            }

            SlotHelper.getInstance().setTargetItem(headSlot).silence(SlotHelper.Silence.FULL);

            MouseHelper.getRightButton().setPressed(true, 2);
            swapBack = true;

            stopwatch.reset();
        }
    }

    private int getHeadSlot() {
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.player.getInventory().getMainStacks().get(i);
            if (itemStack.getItem() instanceof PlayerHeadItem playerHeadItem && playerHeadItem.getName().getString().contains("Head")) {
                return i;
            }
        }
        return -1;
    }

}
