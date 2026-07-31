package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.inventorymove.InventoryMoveInputTracker;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.duck.ClientConnectionAccess;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.KeyBindingAccessor;
import wtf.oraculus.utility.player.MoveUtility;

import java.util.ArrayDeque;
import java.util.Queue;

import static wtf.oraculus.client.Constants.mc;

/**
 * LiquidBounce InventoryMove port.  Input is sourced from the current binding rather
 * than KeyBinding#isPressed so the behavior survives handled screens and remaps.
 */
public final class InventoryMoveModule extends Module {

    private final ModeProperty<Behaviour> behaviour = new ModeProperty<>("Behavior", this, Behaviour.NORMAL)
            .alias("Legit", Behaviour.STOP_ON_ACTION)
            // Removed modes continue to load as Normal rather than leaving a
            // stale enum value in existing configurations.
            .alias("Heypixel", Behaviour.NORMAL)
            .alias("Safe", Behaviour.NORMAL)
            .alias("Undetectable", Behaviour.NORMAL);
    private final BooleanProperty passthroughSneak = new BooleanProperty("PassthroughSneak", false);

    private final BooleanProperty sprintControl = new BooleanProperty("SprintControl", false);
    private final ModeProperty<SprintMode> clientSprint = new ModeProperty<>("Client sprint", SprintMode.DO_NOT_CHANGE);
    private final ModeProperty<SprintMode> serverSprint = new ModeProperty<>("Server sprint", SprintMode.DO_NOT_CHANGE);
    private final BooleanProperty sneakControl = new BooleanProperty("SneakControl", false);
    private final ModeProperty<SneakMode> clientSneak = new ModeProperty<>("Client sneak", SneakMode.DO_NOT_CHANGE);

    private final BooleanProperty timer = new BooleanProperty("Timer", false);
    private final NumberProperty timerSpeed = new NumberProperty("Timer speed", "x", 1.0D, 0.1D, 2.0D, 0.1D);
    private final BooleanProperty blink = new BooleanProperty("Blink", false);
    private final NumberProperty maximumBlinkTime = new NumberProperty("Maximum blink time", "ms", 10000.0D, 0.0D, 30000.0D, 50.0D);

    private final InventoryMoveInputTracker inputTracker = new InventoryMoveInputTracker();
    private final Queue<Packet<?>> delayedContainerPackets = new ArrayDeque<>();
    private final Queue<Packet<?>> blinkPackets = new ArrayDeque<>();
    private boolean releasingPackets;
    private boolean stopOnAction;
    private long blinkOpenedAt = -1L;
    private float timerBeforeInventory = 1.0F;

    public InventoryMoveModule() {
        super("Inventory Move", "Allows you to move while inventories are opened.", ModuleCategory.MOVEMENT);
        addProperties(behaviour, passthroughSneak,
                sprintControl, clientSprint, serverSprint,
                sneakControl, clientSneak,
                timer, timerSpeed, blink, maximumBlinkTime);
        clientSprint.hideIf(() -> !sprintControl.getValue());
        serverSprint.hideIf(() -> !sprintControl.getValue());
        clientSneak.hideIf(() -> !sneakControl.getValue());
        timerSpeed.hideIf(() -> !timer.getValue());
        maximumBlinkTime.hideIf(() -> !blink.getValue());
    }

    @Override
    protected void onEnable() {
        inputTracker.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        releasePackets(delayedContainerPackets);
        releasePackets(blinkPackets);
        stopOnAction = false;
        blinkOpenedAt = -1L;
        TimerHelper.getInstance().timer = timerBeforeInventory;
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return behaviour.getValue().toString();
    }

    public boolean allowsMovementOverride() {
        return isEnabled() && behaviour.is(Behaviour.NORMAL);
    }

    /** Called from KeyboardInputMixin for every movement binding. */
    public boolean isPressed(final KeyBinding binding) {
        final boolean pressed = inputTracker.isPressed(binding);
        onRawInput(binding, pressed);
        return shouldHandleInputs(binding) && pressed;
    }

    public boolean shouldHandleInputs(final KeyBinding binding) {
        if (!isEnabled() || mc.player == null || mc.currentScreen == null || mc.getOverlay() != null
                || mc.currentScreen instanceof ChatScreen) {
            return false;
        }
        if (binding == mc.options.sneakKey && !passthroughSneak.getValue()) {
            return false;
        }
        if (mc.currentScreen instanceof HandledScreen<?>) {
            return behaviour.is(Behaviour.NORMAL)
                    || behaviour.is(Behaviour.STOP_ON_ACTION);
        }
        return true;
    }

    public void applyMovementInput(final MoveInputEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (stopOnAction && behaviour.is(Behaviour.STOP_ON_ACTION)) {
            event.setForward(0.0F);
            event.setSideways(0.0F);
            event.setJump(false);
            event.setSneak(false);
            stopOnAction = false;
            mc.execute(() -> releasePackets(delayedContainerPackets));
            return;
        }

        if (sneakControl.getValue() && isHandledScreenOpen()) {
            if (clientSneak.is(SneakMode.FORCE_SNEAK)) event.setSneak(true);
            if (clientSneak.is(SneakMode.FORCE_NO_SNEAK)) event.setSneak(false);
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!isHandledScreenOpen()) {
            blinkOpenedAt = -1L;
            releasePackets(blinkPackets);
            if (timer.getValue()) {
                TimerHelper.getInstance().timer = timerBeforeInventory;
            }
            return;
        }

        if (timer.getValue()) {
            timerBeforeInventory = TimerHelper.getInstance().timer;
            TimerHelper.getInstance().timer = timerSpeed.getValue().floatValue();
        }

        if (blink.getValue()) {
            if (blinkOpenedAt < 0L) blinkOpenedAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - blinkOpenedAt >= maximumBlinkTime.getValue().longValue()) {
                mc.player.closeHandledScreen();
                blinkOpenedAt = -1L;
                releasePackets(blinkPackets);
            }
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        applyMovementInput(event);
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (releasingPackets || mc.player == null) return;
        final Packet<?> packet = event.getPacket();

        if (behaviour.is(Behaviour.STOP_ON_ACTION) && isHandledScreenOpen()
                && isContainerPacket(packet) && isMoving()) {
            event.setCancelled();
            delayedContainerPackets.add(packet);
            stopOnAction = true;
            return;
        }

        if (blink.getValue() && isHandledScreenOpen() && !isContainerPacket(packet)) {
            event.setCancelled();
            blinkPackets.add(packet);
        }
    }

    public void onRawInput(final KeyBinding binding, final boolean pressed) {
        // Input tracking remains intentionally side-effect free after removal
        // of Safe's close-before-move behavior.
    }

    public boolean shouldForceClientSprint(final boolean original, final boolean moving) {
        if (!sprintControl.getValue() || !isHandledScreenOpen()) return original;
        if (clientSprint.is(SprintMode.FORCE_NO_SPRINT)) return false;
        return clientSprint.is(SprintMode.FORCE_SPRINT) && moving || original;
    }

    public boolean shouldForceServerSprint(final boolean original, final boolean moving) {
        if (!sprintControl.getValue() || !isHandledScreenOpen()) return original;
        if (serverSprint.is(SprintMode.FORCE_NO_SPRINT)) return false;
        return serverSprint.is(SprintMode.FORCE_SPRINT) && moving || original;
    }

    private boolean isHandledScreenOpen() {
        return mc.currentScreen instanceof HandledScreen<?>;
    }

    private boolean isMoving() {
        return MoveUtility.isMoving()
                || inputTracker.isPressed(mc.options.forwardKey)
                || inputTracker.isPressed(mc.options.backKey)
                || inputTracker.isPressed(mc.options.leftKey)
                || inputTracker.isPressed(mc.options.rightKey)
                || inputTracker.isPressed(mc.options.jumpKey);
    }

    private boolean isContainerPacket(final Packet<?> packet) {
        return packet instanceof ClickSlotC2SPacket
                || packet instanceof ButtonClickC2SPacket
                || packet instanceof CreativeInventoryActionC2SPacket
                || packet instanceof SlotChangedStateC2SPacket
                || packet instanceof CloseHandledScreenC2SPacket;
    }

    private void releasePackets(final Queue<Packet<?>> packets) {
        if (packets.isEmpty()) return;
        if (mc.getNetworkHandler() == null || !(mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access)) {
            packets.clear();
            return;
        }
        releasingPackets = true;
        try {
            Packet<?> packet;
            while ((packet = packets.poll()) != null) access.oraculus$sendPacketSilent(packet);
        } finally {
            releasingPackets = false;
        }
    }

    public enum Behaviour {
        NORMAL("Normal"), STOP_ON_ACTION("StopOnAction");
        private final String name;
        Behaviour(final String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum SprintMode {
        DO_NOT_CHANGE("DoNotChange"), FORCE_SPRINT("ForceSprint"), FORCE_NO_SPRINT("ForceNoSprint");
        private final String name;
        SprintMode(final String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum SneakMode {
        DO_NOT_CHANGE("DoNotChange"), FORCE_SNEAK("ForceSneak"), FORCE_NO_SNEAK("ForceNoSneak");
        private final String name;
        SneakMode(final String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}
