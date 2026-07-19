package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.util.hit.HitResult;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.swing.CPSProperty;
import wtf.oraculus.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackDelayEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class AutoClickerModule extends Module {

    private final MultipleBooleanProperty mouseButtons = new MultipleBooleanProperty("Mouse buttons",
            new BooleanProperty("Left", true),
            new BooleanProperty("Right", false)
    );
    private final CPSProperty cpsProperty = new CPSProperty(this);
    private final BooleanProperty requirePressed = new BooleanProperty("Require pressed", true);

    public AutoClickerModule() {
        super("Auto Clicker", "Clicks for you automatically.", ModuleCategory.COMBAT);
        addProperties(mouseButtons, requirePressed);
    }

    @Subscribe
    public void onHandleInput(final MouseHandleInputEvent event) {
        final BlockModule blockModule = OraculusClient.getInstance().getModuleRepository().getModule(BlockModule.class);
        final boolean allowSwingWhenUsing = blockModule.isEnabled() && blockModule.isSwingAllowed();
        if (mc.player.isUsingItem() && !allowSwingWhenUsing) {
            return;
        }

        if (SwingDelay.isSwingAvailable(cpsProperty)) {
            if (mouseButtons.getProperty("Left").getValue() && mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
                if (!requirePressed.getValue() || mc.options.attackKey.isPressed()) {
                    MouseHelper.getLeftButton().setPressed();
                }
            }

            if (mouseButtons.getProperty("Right").getValue()) {
                if (!requirePressed.getValue() || mc.options.useKey.isPressed()) {
                    MouseHelper.getRightButton().setPressed();
                }
            }
        }
    }

    @Subscribe
    public void onAttackCooldown(AttackDelayEvent event) {
        event.setDelay(0);
    }

}
