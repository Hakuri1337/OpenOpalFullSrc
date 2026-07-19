package wtf.oraculus.client.feature.module.impl.movement.noslow;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.GrimJumpNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.NoC0FNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.UniversalNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.VanillaNoSlow;
import wtf.oraculus.client.feature.module.impl.movement.noslow.impl.WatchdogNoSlow;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class NoSlowModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.VANILLA)
            .alias("GrimFast", Mode.NO_C0F)
            .alias("GrimC0F", Mode.NO_C0F)
            .alias("Heypixel", Mode.NO_C0F)
            .alias("Heypixel 3", Mode.NO_C0F);
    private final BooleanProperty allowSprinting = new BooleanProperty("Keep sprinting", true);

    private Action action = Action.NONE;

    public NoSlowModule() {
        super("No Slow", "Removes vanilla slowdowns such as item usage.", ModuleCategory.MOVEMENT);
        addModuleModes(mode, new VanillaNoSlow(this), new WatchdogNoSlow(this), new UniversalNoSlow(this), new GrimJumpNoSlow(this), new NoC0FNoSlow(this));
        addProperties(mode, allowSprinting);
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            this.action = Action.NONE;
            return;
        }

        SlotHelper slotHelper = SlotHelper.getInstance();
        ItemStack mainHandStack = slotHelper.getSilence() == SlotHelper.Silence.FULL ? slotHelper.getMainHandStack(mc.player) : mc.player.getMainHandStack();
        switch (mainHandStack.getUseAction()) {
            case BLOCK -> action = Action.BLOCKABLE;
            case NONE -> action = mainHandStack.isIn(ItemTags.SWORDS) ? Action.BLOCKABLE : Action.NONE;
            case BOW -> action = Action.BOW;
            default -> action = Action.USEABLE;
        }
    }

    @Override
    protected void onEnable() {
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public Action getAction() {
        return action;
    }

    public enum Mode {
        VANILLA("Vanilla"),
        WATCHDOG("Watchdog"),
        UNIVERSAL("Universal"),
        GRIM_JUMP("GrimJump"),
        NO_C0F("NoC0F");

        private final String name;

        Mode(String name) {
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

    public boolean isSprintingAllowed() {
        return allowSprinting.getValue();
    }

    public boolean applyLegacyModeValue(final Object propertyValue) {
        if (!(propertyValue instanceof String valueString)) {
            return false;
        }

        final String normalizedValue = normalize(valueString);
        if (normalizedValue.equals("grimfast")
                || normalizedValue.equals("grimc0f")
                || normalizedValue.equals("noc0f")
                || normalizedValue.equals("heypixel")
                || normalizedValue.equals("heypixel3")) {
            this.mode.setValueOrdinal(Mode.NO_C0F.ordinal());
            return true;
        }

        if (normalizedValue.equals("grimjump")) {
            this.mode.setValueOrdinal(Mode.GRIM_JUMP.ordinal());
            return true;
        }

        return false;
    }

    public boolean isLegacyKeepSprintingProperty(final String propertyName) {
        return normalize(propertyName).equals("keepsprinting");
    }

    public void applyLegacyKeepSprintingValue(final Object propertyValue) {
        this.allowSprinting.applyValue(propertyValue);
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

}
