package wtf.oraculus.client.feature.module.impl.movement.noslow;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.GrimJumpNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.UniversalNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.VanillaNoSlow;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

/** Free edition: Watchdog/Hypixel and NoC0F modes are intentionally absent. */
public final class NoSlowModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.VANILLA);
    private final BooleanProperty allowSprinting = new BooleanProperty("Keep sprinting", true);
    private Action action = Action.NONE;

    public NoSlowModule() {
        super("No Slow", "Removes vanilla slowdowns such as item usage.", ModuleCategory.MOVEMENT);
        addModuleModes(mode, new VanillaNoSlow(this), new UniversalNoSlow(this), new GrimJumpNoSlow(this));
        addProperties(mode, allowSprinting);
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            this.action = Action.NONE;
            return;
        }

        final SlotHelper slotHelper = SlotHelper.getInstance();
        final ItemStack mainHandStack = slotHelper.getSilence() == SlotHelper.Silence.FULL
                ? slotHelper.getMainHandStack(mc.player)
                : mc.player.getMainHandStack();
        switch (mainHandStack.getUseAction()) {
            case BLOCK -> action = Action.BLOCKABLE;
            case NONE -> action = mainHandStack.isIn(ItemTags.SWORDS) ? Action.BLOCKABLE : Action.NONE;
            case BOW -> action = Action.BOW;
            default -> action = Action.USEABLE;
        }
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public Action getAction() {
        return action;
    }

    public boolean isSprintingAllowed() {
        return allowSprinting.getValue();
    }

    public boolean applyLegacyModeValue(final Object propertyValue) {
        if (!(propertyValue instanceof String value)) {
            return false;
        }
        if (normalize(value).equals("grimjump")) {
            mode.setValueOrdinal(Mode.GRIM_JUMP.ordinal());
            return true;
        }
        return false;
    }

    public boolean isLegacyKeepSprintingProperty(final String propertyName) {
        return normalize(propertyName).equals("keepsprinting");
    }

    public void applyLegacyKeepSprintingValue(final Object propertyValue) {
        allowSprinting.applyValue(propertyValue);
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }

    public enum Mode {
        VANILLA("Vanilla"),
        UNIVERSAL("Universal"),
        GRIM_JUMP("GrimJump");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Action {
        BLOCKABLE,
        USEABLE,
        BOW,
        NONE
    }
}
