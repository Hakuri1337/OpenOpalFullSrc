package wtf.oraculus.client.feature.helper.impl.player.slot;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import static wtf.oraculus.client.Constants.mc;

public final class SlotHelper {
    private SlotHelper() {
    }

    private int currentItem, targetItem;
    private boolean active;
    private int activeTick, ticks;
    private Silence silence = Silence.DEFAULT;

    public SlotHelper setTargetItem(int currentItem) {
        currentItem = MathHelper.clamp(currentItem, 0, 8);

        if (!this.active) {
            this.currentItem = mc.player.getInventory().getSelectedSlot();
        }
        this.targetItem = currentItem;
        this.activeTick = this.ticks;
        this.active = true;
        this.sync(true, true);
        return this;
    }

    public SlotHelper silence(Silence silence) {
        this.silence = silence;
        return this;
    }

    public void setVisualSlot(int currentItem) {
        this.currentItem = currentItem;
    }

    public void stop() { // if you need something to swap back instantly use this, otherwise there will be a 1 tick delay purposefully to prevent collisions with mc
        this.activeTick = -1;
        this.sync(true, true);
    }

    public void sync(boolean reset, boolean check) {
        if (this.active) {
            if (!check || (mc.getOverlay() == null && mc.currentScreen == null)) {
                if (reset && this.activeTick != this.ticks) {
                    mc.player.getInventory().setSelectedSlot(this.currentItem);
                    this.silence = Silence.DEFAULT;
                    this.active = false;
                } else {
                    mc.player.getInventory().setSelectedSlot(this.targetItem);
                }
            }
        }
    }

    public void tick() {
        this.sync(true, true);
        this.ticks++;
    }

    public ItemStack getMainHandStack(ClientPlayerEntity player) {
        return this.active && this.silence != Silence.NONE ? player.getInventory().getMainStacks().get(this.currentItem) : player.getMainHandStack();
    }

    public int getSelectedSlot(PlayerInventory inventory) {
        return this.active && this.silence == Silence.FULL ? this.currentItem : inventory.getSelectedSlot();
    }

    public boolean isActive() {
        return active;
    }

    public int getVisualSlot() {
        return currentItem;
    }

    public Silence getSilence() {
        return silence;
    }

    private static SlotHelper instance;

    public static SlotHelper getInstance() {
        return instance;
    }

    public static void setInstance() {
        instance = new SlotHelper();
    }

    public static SlotHelper setCurrentItem(int currentItem) {
        return instance.setTargetItem(currentItem);
    }

    public enum Silence {
        NONE,
        DEFAULT,
        FULL
    }
}
