package wtf.oraculus.client.feature.module.impl.utility.inventory;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.inventory.ManualInventoryInteractionEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class AutoArmorModule extends Module {

    private final ModeProperty<AcaInventoryActionScheduler.TimingMode> timingMode =
            new ModeProperty<>("Timing", AcaInventoryActionScheduler.TimingMode.ACA);

    public AutoArmorModule() {
        super("Auto Armor", "Delegates armor handling to Inventory Manager.", ModuleCategory.UTILITY);
        addProperties(timingMode);
    }

    @Subscribe
    public void onPostGameTickEvent(final PostGameTickEvent event) {
        if (mc.player == null) {
            return;
        }

        final InventoryManagerModule inventoryManagerModule = OraculusClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class);

        if (inventoryManagerModule.isEnabled()) {
            return;
        }

        inventoryManagerModule.runAutoArmorOnly(this.timingMode.getValue());
    }

    @Subscribe
    public void onManualInventoryInteraction(final ManualInventoryInteractionEvent event) {
        if (mc.player == null || event.syncId() != mc.player.playerScreenHandler.syncId) {
            return;
        }
        AcaInventoryActionScheduler.getInstance().pauseForManualInput(
                AcaInventoryActionScheduler.Owner.INVENTORY_MANAGER,
                mc.player.age
        );
    }

    @Override
    protected void onDisable() {
        final InventoryManagerModule inventoryManagerModule = OraculusClient.getInstance()
                .getModuleRepository()
                .getModule(InventoryManagerModule.class);
        inventoryManagerModule.stopAutoArmorOnlySession();
        super.onDisable();
    }
}
