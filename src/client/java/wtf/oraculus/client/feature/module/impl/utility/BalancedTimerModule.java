package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.world.TimerModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.client.notification.NotificationType;
import wtf.oraculus.event.impl.client.ModuleToggleEvent;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.network.PacketUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.oraculus.client.Constants.mc;

public final class BalancedTimerModule extends Module {

    private static final int MAX_BALANCE = 20;
    private static final int FULL_BALANCE_RELEASE_TIMEOUT_TICKS = 15 * 20;

    public enum TimerMode {
        TIMER_064("0.64", 0.64F),
        TIMER_06666("0.6666", 0.6666F);

        private final String name;
        private final float timer;

        TimerMode(final String name, final float timer) {
            this.name = name;
            this.timer = timer;
        }

        public float getTimer() {
            return this.timer;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum Stage {
        STORE,
        IDLE,
        RELEASE
    }

    private final NumberProperty mouseButton = new NumberProperty("MouseButton", 3, 0, 7, 1);
    private final ModeProperty<TimerMode> timerMode = new ModeProperty<>("TimerMode", TimerMode.TIMER_064);
    private final NumberProperty verticalPosition = new NumberProperty("Vertical Position", -140, -300, 300, 5);

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    private static int balance;
    private static int delay;
    private boolean needSkip;
    private Stage stage = Stage.IDLE;
    private boolean autoStore;
    private boolean wasPressed;
    private boolean forcedRelease;
    private boolean packetWarningShown;
    private int fullBalanceTicks;

    public BalancedTimerModule() {
        super("BalancedTimer", "按对应鼠标按键（如侧键 1、侧键 2 等真实按键，而非 MouseButton 数值）以开始缓存/释放包。", ModuleCategory.UTILITY);
        this.addProperties(this.mouseButton, this.timerMode, this.verticalPosition);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.resetTimer();
            return;
        }

        final int button = this.mouseButton.getValue().intValue();
        final boolean pressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;

        if (!pressed && this.wasPressed && balance > 0) {
            this.autoStore = true;
        }
        if (pressed && !this.wasPressed && balance == 0) {
            this.autoStore = true;
        }
        this.wasPressed = pressed;

        if (this.forcedRelease) {
            this.releaseBalanceTick();
        } else if (balance >= MAX_BALANCE && ++this.fullBalanceTicks >= FULL_BALANCE_RELEASE_TIMEOUT_TICKS) {
            this.flushPackets();
            this.forcedRelease = true;
            this.releaseBalanceTick();
        } else if (pressed && balance >= 1 && !this.needSkip && delay <= 0) {
            this.stage = Stage.RELEASE;
            TimerHelper.getInstance().timer = 2.0F;
            balance--;
            if (balance == 0) {
                this.autoStore = false;
            }
        } else if (this.autoStore && !pressed && !this.needSkip && delay <= 0) {
            if (balance < MAX_BALANCE) {
                this.stage = Stage.STORE;
                TimerHelper.getInstance().timer = this.timerMode.getValue().getTimer();
                balance++;
            } else {
                this.stage = Stage.RELEASE;
                TimerHelper.getInstance().timer = 2.0F;
                balance--;
            }
        } else if (!this.needSkip && delay <= 0) {
            this.stage = Stage.IDLE;
            TimerHelper.getInstance().timer = 1.0F;
        } else {
            this.needSkip = false;
            this.flushPackets();
            this.stage = Stage.IDLE;
            TimerHelper.getInstance().timer = 1.0F;
            balance = 0;
            delay--;
        }

        if (balance < MAX_BALANCE && !this.forcedRelease) {
            this.fullBalanceTicks = 0;
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if ((packet instanceof EntityVelocityUpdateS2CPacket velocity
                && velocity.getEntityId() == mc.player.getId())
                || (packet instanceof EntityPositionS2CPacket position
                && position.entityId() == mc.player.getId())
                || packet instanceof PlayerPositionLookS2CPacket) {
            this.needSkip = true;
            this.resetTimer();
            this.flushPackets();
            delay = 20;
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (this.stage == Stage.IDLE || this.isAllowedPacket(event.getPacket())) {
            return;
        }

        event.setCancelled();
        this.packets.add(event.getPacket());
        if (!this.packetWarningShown) {
            this.packetWarningShown = true;
            OraculusClient.getInstance().getNotificationManager()
                    .builder(NotificationType.WARN)
                    .title("BalancedTimer")
                    .description("尽快释放存包否则你将被妖猫害死")
                    .duration(5000)
                    .buildAndPublish();
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.clearWorldState();
    }

    @Subscribe
    public void onDisconnect(final ServerDisconnectEvent event) {
        this.clearWorldState();
    }

    @Subscribe
    public void onModuleToggle(final ModuleToggleEvent event) {
        if (event.isEnabled() && event.getModule() instanceof TimerModule) {
            event.setCancelled();
        }
    }

    private boolean isAllowedPacket(final Packet<?> packet) {
        return packet instanceof LoginHelloC2SPacket
                || packet instanceof QueryRequestC2SPacket
                || packet instanceof QueryPingC2SPacket
                || packet instanceof LoginKeyC2SPacket
                || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof ChatMessageC2SPacket
                || packet instanceof PlayerActionC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket
                || packet instanceof PlayerMoveC2SPacket
                // 1.21.2+ moved the tick boundary and input state into dedicated packets.
                || packet instanceof ClientTickEndC2SPacket
                || packet instanceof PlayerInputC2SPacket
                || packet instanceof HandSwingC2SPacket
                || packet instanceof ClientCommandC2SPacket
                || packet instanceof KeepAliveC2SPacket
                || packet instanceof UpdateSelectedSlotC2SPacket;
    }

    private void resetTimer() {
        this.stage = Stage.IDLE;
        TimerHelper.getInstance().timer = 1.0F;
        balance = 0;
        this.forcedRelease = false;
        this.fullBalanceTicks = 0;
    }

    private void clearWorldState() {
        balance = 0;
        delay = 0;
        this.stage = Stage.IDLE;
        TimerHelper.getInstance().timer = 1.0F;
        this.packets.clear();
        this.forcedRelease = false;
        this.packetWarningShown = false;
        this.fullBalanceTicks = 0;
    }

    private void flushPackets() {
        if (this.packets.isEmpty()) {
            return;
        }

        if (!mc.isInSingleplayer() && mc.getNetworkHandler() != null) {
            Packet<?> packet;
            while ((packet = this.packets.poll()) != null) {
                PacketUtility.sendQueued(packet);
            }
        }
        this.packets.clear();
        this.packetWarningShown = false;
    }

    private void releaseBalanceTick() {
        this.stage = Stage.RELEASE;
        TimerHelper.getInstance().timer = 2.0F;
        balance--;
        if (balance <= 0) {
            balance = 0;
            this.autoStore = false;
            this.forcedRelease = false;
            this.fullBalanceTicks = 0;
        }
    }

    @Override
    protected void onEnable() {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository != null) {
            final TimerModule timer = repository.getModule(TimerModule.class);
            if (timer != null && timer.isEnabled()) {
                timer.setEnabled(false);
            }
        }
        OraculusClient.getInstance().getNotificationManager()
                .builder(NotificationType.INFO)
                .title("BalancedTimer")
                .description("按对应鼠标按键（如侧键 1、侧键 2），而非 MouseButton 数值，以开始缓存/释放包。")
                .duration(6000)
                .buildAndPublish();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.resetTimer();
        this.autoStore = false;
        delay = 0;
        this.needSkip = false;
        this.flushPackets();
        super.onDisable();
    }

    public int getBalance() {
        return balance;
    }

    public double getVerticalPosition() {
        return this.verticalPosition.getValue();
    }

    public Stage getStage() {
        return this.stage;
    }
}
