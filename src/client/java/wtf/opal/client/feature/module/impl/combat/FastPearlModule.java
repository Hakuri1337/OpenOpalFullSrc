package wtf.opal.client.feature.module.impl.combat;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.press.MousePressEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

/** OpenUitems FastPearl state machine, adapted to OpenOpal's mouse event. */
public final class FastPearlModule extends Module {

    private final ModeProperty<ButtonMode> button = new ModeProperty<>("Button", ButtonMode.MIDDLE);
    private Stage stage = Stage.IDLE;
    private int oldSlot = -1;
    private int pearlSlot = -1;
    private int stageTicks;

    public FastPearlModule() {
        super("FastPearl", "Throws an ender pearl from the hotbar on a mouse press.", ModuleCategory.COMBAT);
        this.addProperties(this.button);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && this.isHotbarSlotValid(this.oldSlot)) {
            mc.player.getInventory().setSelectedSlot(this.oldSlot);
        }
        this.resetState();
        super.onDisable();
    }

    @Subscribe
    public void onMousePress(final MousePressEvent event) {
        if (mc.player == null || mc.currentScreen != null || !this.isTriggerButton(event.getInteractionCode())) {
            return;
        }

        this.startThrow();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.interactionManager == null) {
            this.resetState();
            return;
        }

        if (this.stage == Stage.IDLE) {
            return;
        }

        if (++this.stageTicks > 10) {
            this.resetState();
            return;
        }

        final PlayerInventory inventory = mc.player.getInventory();
        switch (this.stage) {
            case SWITCH_TO_PEARL -> {
                if (!this.isHotbarSlotValid(this.pearlSlot) || !inventory.getStack(this.pearlSlot).isOf(Items.ENDER_PEARL)) {
                    this.resetState();
                    return;
                }
                inventory.setSelectedSlot(this.pearlSlot);
                this.stage = Stage.THROW;
                this.stageTicks = 0;
            }
            case THROW -> {
                if (!mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                    this.resetState();
                    return;
                }
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                this.stage = Stage.RESTORE;
                this.stageTicks = 0;
            }
            case RESTORE -> {
                if (this.isHotbarSlotValid(this.oldSlot)) {
                    inventory.setSelectedSlot(this.oldSlot);
                }
                this.resetState();
            }
            default -> this.resetState();
        }
    }

    private void startThrow() {
        if (this.stage != Stage.IDLE || mc.player == null) {
            return;
        }

        final PlayerInventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(Items.ENDER_PEARL)) {
                this.pearlSlot = slot;
                break;
            }
        }

        if (!this.isHotbarSlotValid(this.pearlSlot)) {
            this.pearlSlot = -1;
            return;
        }

        this.oldSlot = inventory.getSelectedSlot();
        this.stage = Stage.SWITCH_TO_PEARL;
        this.stageTicks = 0;
    }

    private boolean isTriggerButton(final int pressedButton) {
        return switch (this.button.getValue()) {
            case MIDDLE -> pressedButton == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case MOUSE4 -> pressedButton == GLFW.GLFW_MOUSE_BUTTON_4;
            case MOUSE5 -> pressedButton == GLFW.GLFW_MOUSE_BUTTON_5;
        };
    }

    private boolean isHotbarSlotValid(final int slot) {
        return slot >= 0 && slot < 9;
    }

    private void resetState() {
        this.stage = Stage.IDLE;
        this.stageTicks = 0;
        this.oldSlot = -1;
        this.pearlSlot = -1;
    }

    private enum Stage {
        IDLE,
        SWITCH_TO_PEARL,
        THROW,
        RESTORE
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
