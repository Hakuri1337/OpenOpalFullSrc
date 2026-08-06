package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.target.TargetList;
import wtf.oraculus.client.feature.helper.impl.target.TargetProperty;
import wtf.oraculus.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.math.RandomUtility;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.PlayerUtility;

import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class BlockModule extends Module {

    private final TargetProperty targetProperty = new TargetProperty(true, false, false, false, false, false);

    private final BooleanProperty allowSwingWhileBlocking = new BooleanProperty("Blocking", false);

    private final BooleanProperty autoBlock = new BooleanProperty("Enabled", true);
    private final BooleanProperty requireAttackKey = new BooleanProperty("Require attack key", false);
    private final NumberProperty blockRange = new NumberProperty("Block range", 3f, 3f, 8f, 0.5f);

    private boolean blocking;

    public BlockModule() {
        super("Block", "Allows illegitimate actions while blocking, or automatically blocks.", ModuleCategory.COMBAT);

        addProperties(
                new GroupProperty("Allow swing while...", allowSwingWhileBlocking),
                new GroupProperty("Auto block", autoBlock, requireAttackKey.hideIf(() -> !autoBlock.getValue()), blockRange.hideIf(() -> !autoBlock.getValue())),
                targetProperty.get()
        );
    }

    @Subscribe(priority = 2)
    public void onHandleInput(final MouseHandleInputEvent event) {
        blocking = false;

        final TargetList targetList = LocalDataWatch.getTargetList();
        final SlotHelper slotHelper = SlotHelper.getInstance();

        final ItemStack mainHandStack = slotHelper.getSilence() == SlotHelper.Silence.FULL
                ? slotHelper.getMainHandStack(mc.player)
                : mc.player.getMainHandStack();

        if (targetList == null || !autoBlock.getValue() || !(mainHandStack.isIn(ItemTags.SWORDS) || mc.player.getOffHandStack().getItem() instanceof ShieldItem)) {
            return;
        }

        if (requireAttackKey.getValue() && !mc.options.attackKey.isPressed()) {
            return;
        }

        final List<TargetLivingEntity> targets = targetList.collectTargets(targetProperty.getTargetFlags(), TargetLivingEntity.class);
        final double interactionRange = blockRange.getValue();

        for (final TargetLivingEntity target : targets) {
            if (target.isLocal()) {
                continue;
            }

            final LivingEntity entity = target.getEntity();

            if (PlayerUtility.getDistanceToEntity(entity) <= interactionRange) {
                final Block blockOver = PlayerUtility.getBlockOver();
                if (InventoryUtility.isBlockInteractable(blockOver)) {
                    return;
                }

                MouseHelper.getRightButton().setPressed(true, RandomUtility.getRandomInt(2));
                blocking = true;
            }
        }
    }

    public boolean isSwingAllowed() {
        return allowSwingWhileBlocking.getValue();
    }

    public boolean isBlocking() {
        return blocking;
    }

}
