package wtf.oraculus.client.feature.module.impl.combat;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class ReachModule extends Module {

    // hooked in PlayerEntityMixin#hookEntityReach & PlayerEntity#hookBlockReach

    private final NumberProperty entityInteractionRange = new NumberProperty("Entity interaction range", 3D, 3D, 6D, 0.05D),
            blockInteractionRange = new NumberProperty("Block interaction range", 4.5D, 4.5D, 6D, 0.05D);

    public ReachModule() {
        super("Reach", "Allows you to interact or attack further.", ModuleCategory.COMBAT);
        addProperties(entityInteractionRange, blockInteractionRange);
    }

    public double getEntityInteractionRange() {
        return entityInteractionRange.getValue();
    }

    public double getBlockInteractionRange() {
        return blockInteractionRange.getValue();
    }

}
