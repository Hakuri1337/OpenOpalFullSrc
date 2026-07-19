package wtf.oraculus.client.feature.module.impl.movement.inventorymove;

import wtf.oraculus.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class NormalInventoryMove extends ModuleMode<InventoryMoveModule> {

    public NormalInventoryMove(final InventoryMoveModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return InventoryMoveModule.Mode.NORMAL;
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!module.canProcessScreenInput() || module.isNormalScreenBlocked()) {
            return;
        }

        module.applyMovementInput(event);
    }
}
