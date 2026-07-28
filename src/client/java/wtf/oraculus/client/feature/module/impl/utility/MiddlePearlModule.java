package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.press.MousePressEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.ClientPlayerInteractionManagerAccessor;

import static wtf.oraculus.client.Constants.mc;

public final class MiddlePearlModule extends Module {
    private static final int MIDDLE_MOUSE_BUTTON = 2;
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.NONE);
    private boolean grimButtonHeld;
    private boolean grimPearlSelected;
    private int grimThrowCountdown;
    private int grimRestoreDelay;
    private int grimSavedSlot = -1;

    public MiddlePearlModule() {
        super("Middle Pearl", "Throws an ender pearl from the hotbar with the middle mouse button.", ModuleCategory.UTILITY);
        this.addProperties(this.mode);
    }

    @Subscribe
    public void onMousePress(final MousePressEvent event) {
        if (event.getInteractionCode() != MIDDLE_MOUSE_BUTTON || this.mode.getValue() == Mode.GRIM
                || mc.player == null || mc.interactionManager == null || mc.currentScreen != null) {
            return;
        }
        final int pearlSlot = this.findPearlSlot(mc.player.getInventory());
        if (pearlSlot >= 0) {
            this.throwPearlImmediately(pearlSlot);
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (this.mode.getValue() != Mode.GRIM) {
            if (this.hasGrimState()) this.resetGrimState(true);
            return;
        }
        if (mc.player == null || mc.interactionManager == null) {
            this.resetGrimState(false);
            return;
        }
        if (mc.currentScreen != null) {
            this.resetGrimState(true);
            return;
        }
        if (this.grimRestoreDelay > 0) {
            if (--this.grimRestoreDelay == 0) this.resetGrimState(true);
            return;
        }
        if (this.grimThrowCountdown > 0) {
            if (--this.grimThrowCountdown == 0) {
                this.useSelectedPearl();
                this.grimRestoreDelay = 2;
            }
            return;
        }
        final boolean pressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), MIDDLE_MOUSE_BUTTON) == GLFW.GLFW_PRESS;
        if (pressed) {
            if (!this.grimButtonHeld && !this.grimPearlSelected) {
                final PlayerInventory inventory = mc.player.getInventory();
                final int pearlSlot = this.findPearlSlot(inventory);
                if (pearlSlot >= 0) {
                    this.grimSavedSlot = inventory.getSelectedSlot();
                    inventory.setSelectedSlot(pearlSlot);
                    this.grimPearlSelected = true;
                }
            }
            this.grimButtonHeld = true;
        } else {
            if (this.grimButtonHeld && this.grimPearlSelected) {
                this.grimPearlSelected = false;
                this.grimThrowCountdown = 1;
            }
            this.grimButtonHeld = false;
        }
    }

    @Override
    protected void onDisable() {
        this.resetGrimState(true);
        super.onDisable();
    }

    private void throwPearlImmediately(final int pearlSlot) {
        final PlayerInventory inventory = mc.player.getInventory();
        final int originalSlot = inventory.getSelectedSlot();
        if (pearlSlot != originalSlot) this.selectAndSync(inventory, pearlSlot);
        this.useSelectedPearl();
        if (pearlSlot != originalSlot) this.selectAndSync(inventory, originalSlot);
    }

    private void useSelectedPearl() {
        final ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || !player.getMainHandStack().isOf(Items.ENDER_PEARL)) return;
        final ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (result.isAccepted()) player.swingHand(Hand.MAIN_HAND);
    }

    private int findPearlSlot(final PlayerInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(Items.ENDER_PEARL)) return slot;
        }
        return -1;
    }

    private boolean hasGrimState() {
        return this.grimButtonHeld || this.grimPearlSelected || this.grimThrowCountdown > 0
                || this.grimRestoreDelay > 0 || this.grimSavedSlot >= 0;
    }

    private void resetGrimState(final boolean restoreSlot) {
        if (restoreSlot && this.grimSavedSlot >= 0 && mc.player != null) {
            this.selectAndSync(mc.player.getInventory(), this.grimSavedSlot);
        }
        this.grimButtonHeld = false;
        this.grimPearlSelected = false;
        this.grimThrowCountdown = 0;
        this.grimRestoreDelay = 0;
        this.grimSavedSlot = -1;
    }

    private void selectAndSync(final PlayerInventory inventory, final int slot) {
        inventory.setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
    }

    public enum Mode {
        NONE("None"), GRIM("Grim");
        private final String name;
        Mode(final String name) { this.name = name; }
        @Override public String toString() { return this.name; }
    }
}
