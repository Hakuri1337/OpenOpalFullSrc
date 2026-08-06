package wtf.oraculus.client.feature.helper.impl.player.swing;

import net.minecraft.util.math.MathHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;

import java.util.function.BooleanSupplier;

public final class CPSProperty {

    private final BooleanProperty modernDelay;
    private final NumberProperty delay;
    private final GroupProperty groupProperty;

    public CPSProperty(final Module parent) {
        this(parent, "CPS", true);
    }

    public CPSProperty(final Module parent, final String groupName, final boolean allowModernDelay) {
        if (allowModernDelay) {
            this.modernDelay = new BooleanProperty("Modern delay", false);
        } else {
            this.modernDelay = null;
        }
        this.delay = new NumberProperty("CPS", 10, 1, 20, 1).hideIf(this::isModernDelay);

        this.groupProperty = new GroupProperty(groupName, this.modernDelay, this.delay);
        parent.addProperties(this.groupProperty);
    }

    public CPSProperty hideIf(BooleanSupplier hiddenSupplier) {
        this.groupProperty.hideIf(hiddenSupplier);
        return this;
    }

    public boolean isModernDelay() {
        return modernDelay != null && modernDelay.getValue();
    }

    public int getCPS() {
        return this.delay.getValue().intValue();
    }

    public int getClickDelay() {
        return 1000 / getCPS();
    }

    private long nextClick;

    public void resetClick() {
        this.nextClick = getClickDelay();
    }

    public long getNextClick() {
        return nextClick;
    }
}
