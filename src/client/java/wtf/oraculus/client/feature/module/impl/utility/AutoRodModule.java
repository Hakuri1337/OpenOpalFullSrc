package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

/** OpenZen AutoRod's held-button switch/use/restore flow. */
public final class AutoRodModule extends Module {

    private final ModeProperty<ButtonMode> button = new ModeProperty<>("Button", ButtonMode.MOUSE4);
    private final NumberProperty delay = new NumberProperty("Delay", "ticks", 2.0D, 0.0D, 20.0D, 1.0D);

    private boolean slotSwitched;
    private boolean active;
    private int previousSlot = -1;
    private int tickDelay;

    public AutoRodModule() {
        super("AutoRod", "Uses a fishing rod, egg, or snowball from the hotbar.", ModuleCategory.UTILITY);
        this.addProperties(this.button, this.delay);
    }

    @Override
    protected void onEnable() {
        this.resetState();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.restoreSlot();
        this.resetState();
        super.onDisable();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.resetState();
            return;
        }

        if (this.tickDelay > 0) {
            if (--this.tickDelay == 0) {
                this.restoreSlot();
                this.slotSwitched = false;
            }
            return;
        }

        if (!this.isMouseButtonDown()) {
            this.active = false;
            return;
        }

        if (this.active) {
            return;
        }

        this.active = true;
        final int slot = this.findUsableItemSlot();
        if (slot == -1) {
            return;
        }

        this.previousSlot = mc.player.getInventory().getSelectedSlot();
        final boolean fishingRod = mc.player.getInventory().getStack(slot).isOf(Items.FISHING_ROD);
        this.slotSwitched = true;
        this.selectHotbarSlot(slot);
        this.useItem();

        if (fishingRod) {
            this.tickDelay = this.delay.getValue().intValue();
        }

        if (!fishingRod || this.tickDelay <= 0) {
            this.restoreSlot();
            this.slotSwitched = false;
        }
    }

    public boolean shouldInterceptButton(final int mouseButton) {
        return this.isCurrentMouseButton(mouseButton)
                && (this.isActiveOrPending() || this.findUsableItemSlot() != -1);
    }

    private boolean isCurrentMouseButton(final int mouseButton) {
        return this.getMouseButtonCode() == mouseButton;
    }

    private boolean isActiveOrPending() {
        return this.slotSwitched || this.tickDelay > 0;
    }

    private boolean isMouseButtonDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), this.getMouseButtonCode()) == GLFW.GLFW_PRESS;
    }

    private int getMouseButtonCode() {
        return switch (this.button.getValue()) {
            case MIDDLE -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case MOUSE4 -> GLFW.GLFW_MOUSE_BUTTON_4;
            case MOUSE5 -> GLFW.GLFW_MOUSE_BUTTON_5;
        };
    }

    private int findUsableItemSlot() {
        final int rodSlot = this.findItemInHotbar(Items.FISHING_ROD);
        return rodSlot != -1 ? rodSlot : this.findThrowableSlot();
    }

    private int findItemInHotbar(final Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private int findThrowableSlot() {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (this.isThrowable(mc.player.getInventory().getStack(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private void selectHotbarSlot(final int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void useItem() {
        final Item item = mc.player.getMainHandStack().getItem();
        if (!this.isUsableItem(item)) {
            return;
        }

        mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    private boolean isUsableItem(final Item item) {
        return item == Items.FISHING_ROD || this.isThrowable(item);
    }

    private boolean isThrowable(final Item item) {
        return item == Items.EGG || item == Items.SNOWBALL;
    }

    private void restoreSlot() {
        if (mc.player != null && this.previousSlot != -1) {
            this.selectHotbarSlot(this.previousSlot);
            this.previousSlot = -1;
        }
    }

    private void resetState() {
        this.slotSwitched = false;
        this.active = false;
        this.previousSlot = -1;
        this.tickDelay = 0;
    }

    public enum ButtonMode {
        MIDDLE("Middle"),
        MOUSE4("Mouse 4"),
        MOUSE5("Mouse 5");

        private final String name;

        ButtonMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
