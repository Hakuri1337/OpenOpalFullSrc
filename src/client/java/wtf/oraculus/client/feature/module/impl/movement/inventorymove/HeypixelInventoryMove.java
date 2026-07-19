package wtf.oraculus.client.feature.module.impl.movement.inventorymove;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class HeypixelInventoryMove extends ModuleMode<InventoryMoveModule> {

    public HeypixelInventoryMove(final InventoryMoveModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return InventoryMoveModule.Mode.HEYPIXEL;
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!module.canProcessScreenInput() || !this.isHeypixelMovementContext()) {
            return;
        }

        module.applyMovementInput(event);
    }

    private boolean isHeypixelMovementContext() {
        if (mc.currentScreen instanceof DropdownClickGUI) {
            return true;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            return false;
        }

        return OraculusClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class)
                .isPerformingAction();
    }
}
