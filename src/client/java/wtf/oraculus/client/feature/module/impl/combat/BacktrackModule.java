package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.duck.ClientConnectionAccess;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.game.world.EntityRemoveEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.EntityS2CPacketAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.oraculus.client.Constants.mc;

public final class BacktrackModule extends Module {

    private final NumberProperty minRange = new NumberProperty("Min Range", 3.0D, 1.0D, 6.0D, 0.1D);
    private final NumberProperty maxRange = new NumberProperty("Max Range", 6.0D, 1.0D, 6.0D, 0.1D);
    private final NumberProperty delay = new NumberProperty("Delay", "ms", 200.0D, 0.0D, 1000.0D, 10.0D);
    private final NumberProperty chance = new NumberProperty("Chance", "%", 100.0D, 5.0D, 100.0D, 1.0D);
    private final BooleanProperty resetOnVelocity = new BooleanProperty("Reset On Velocity", true);
    private final BooleanProperty render = new BooleanProperty("Render", true);
    private final BooleanProperty debug = new BooleanProperty("Debug", false);

    private final ConcurrentLinkedQueue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();

    private volatile PositionTracker positionTracker;
    private volatile boolean backtrackingActive;
    private boolean releasingPackets;
    private int debugBlockedPackets;

    public BacktrackModule() {
        super("Backtrack", "Gives you more reach using a legit strategy.", ModuleCategory.COMBAT);
        addProperties(this.minRange, this.maxRange, this.delay, this.chance, this.resetOnVelocity, this.render, this.debug);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.positionTracker = null;
            this.releasePackets("invalid world");
            return;
        }

        final PositionTracker tracker = this.positionTracker;
        if (tracker == null) {
            this.releasePackets("no tracker");
            return;
        }

        if (!tracker.player.isAlive() || tracker.player.isRemoved()) {
            this.positionTracker = null;
            this.releasePackets("target gone");
            return;
        }

        tracker.applyPos();
        if (this.backtrackingActive) {
            this.checkBacktrackRange(tracker);
            this.processQueue();
        }
    }

    @Subscribe
    public void onAttack(final AttackEvent event) {
        if (!(event.getTarget() instanceof PlayerEntity player) || player == mc.player) {
            return;
        }

        final double roll = Math.random();
        if (roll > this.chance.getValue() / 100.0D) {
            this.debug("skip chance roll=" + format(roll * 100.0D) + "% > " + format(this.chance.getValue()) + "%");
            this.positionTracker = null;
            this.releasePackets("chance");
            return;
        }

        if (this.positionTracker == null) {
            this.releasePackets("retarget");
            this.positionTracker = new PositionTracker(player, player.getEntityPos());
            this.debug("tracking " + player.getName().getString() + " delay=" + this.delay.getValue().intValue() + "ms");
        }
    }

    @Subscribe(priority = 1)
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (this.releasingPackets || event.isCancelled()) {
            return;
        }

        final PositionTracker tracker = this.positionTracker;
        if (tracker == null || mc.player == null || mc.world == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof EntityS2CPacket move) {
            if (((EntityS2CPacketAccessor) move).getId() == tracker.player.getId()) {
                tracker.setCurrentPos(tracker.decodeRelativePos(move.getDeltaX(), move.getDeltaY(), move.getDeltaZ()));
                this.checkBacktrackRange(tracker);
            }
        } else if (packet instanceof EntityPositionS2CPacket teleport) {
            if (teleport.entityId() == tracker.player.getId()) {
                tracker.updatePos(teleport.change().position());
                tracker.setCurrentPos(teleport.change().position());
                this.checkBacktrackRange(tracker);
            }
        } else if (packet instanceof EntitiesDestroyS2CPacket destroyEntities) {
            if (destroyEntities.getEntityIds().contains(tracker.player.getId())) {
                this.positionTracker = null;
                this.releasePackets("target destroyed");
                return;
            }
        } else if (packet instanceof PlayerPositionLookS2CPacket) {
            this.positionTracker = null;
            this.releasePackets("flag");
            return;
        }

        if (!this.backtrackingActive) {
            return;
        }

        if (this.resetOnVelocity.getValue()
                && packet instanceof EntityVelocityUpdateS2CPacket velocity
                && velocity.getEntityId() == mc.player.getId()) {
            this.positionTracker = null;
            this.releasePackets("velocity packet");
            return;
        }

        this.packetQueue.add(new PacketEntry(packet));
        this.debugBlockedPackets++;
        if (this.debugBlockedPackets == 1 || this.debugBlockedPackets % 10 == 0) {
            this.debug("blocked packets=" + this.debugBlockedPackets
                    + " queue=" + this.packetQueue.size()
                    + " last=" + this.packetName(packet));
        }
        event.setCancelled();
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!this.render.getValue() || !this.backtrackingActive) {
            return;
        }

        final PositionTracker tracker = this.positionTracker;
        if (tracker == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }

        final Vec3d position = tracker.getInterpolatedPos(event.tickDelta());
        final double halfWidth = tracker.player.getWidth() / 2.0D;
        final Vec3d min = position.subtract(halfWidth, 0.0D, halfWidth);
        final Vec3d dimensions = new Vec3d(tracker.player.getWidth(), tracker.player.getHeight(), tracker.player.getWidth());

        final VertexConsumerProvider.Immediate vcp = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        final WorldRenderer renderer = new WorldRenderer(vcp);
        renderer.drawFilledCube(
                event.matrixStack(),
                CustomRenderLayers.getPositionColorQuads(true),
                min,
                dimensions,
                ColorUtility.rgbaToHex(255, 255, 255, 64)
        );
        this.drawOutlinedBox(renderer, event, min, dimensions, ColorUtility.rgbaToHex(255, 255, 255, 204));
        vcp.draw();
    }

    @Subscribe
    public void onEntityRemove(final EntityRemoveEvent event) {
        final PositionTracker tracker = this.positionTracker;
        if (tracker != null && event.getEntity() == tracker.player) {
            this.positionTracker = null;
            this.releasePackets("entity remove");
        }
    }

    @Subscribe
    public void onServerDisconnect(final ServerDisconnectEvent event) {
        this.positionTracker = null;
        this.releasePackets("disconnect");
    }

    public boolean isActive() {
        return this.backtrackingActive && !this.packetQueue.isEmpty();
    }

    public boolean isBacktracking() {
        return this.backtrackingActive;
    }

    public boolean isBlinking() {
        return this.backtrackingActive;
    }

    private void checkBacktrackRange(final PositionTracker tracker) {
        if (tracker == null || mc.player == null) {
            this.positionTracker = null;
            this.releasePackets();
            return;
        }

        final Vec3d eye = mc.player.getEyePos();
        final double halfWidth = tracker.player.getWidth() / 2.0D;
        final Box trackedBox = new Box(
                tracker.currentPos.x - halfWidth,
                tracker.currentPos.y,
                tracker.currentPos.z - halfWidth,
                tracker.currentPos.x + halfWidth,
                tracker.currentPos.y + tracker.player.getHeight(),
                tracker.currentPos.z + halfWidth
        );

        final Vec3d closest = PlayerUtility.getClosestVectorToBox(eye, trackedBox);
        final Vec3d realClosest = PlayerUtility.getClosestVectorToBox(eye, tracker.player.getBoundingBox());
        final double distance = eye.distanceTo(closest);
        final double realDistance = eye.distanceTo(realClosest);

        if (realDistance <= 3.0D
                && distance >= this.minRange.getValue()
                && distance < this.maxRange.getValue()) {
            if (!this.backtrackingActive) {
                this.debug("range active real=" + format(realDistance)
                        + " tracked=" + format(distance)
                        + " min=" + format(this.minRange.getValue())
                        + " max=" + format(this.maxRange.getValue()));
            }
            this.backtrackingActive = true;
            return;
        }

        this.positionTracker = null;
        this.releasePackets("range real=" + format(realDistance) + " tracked=" + format(distance));
    }

    private void processQueue() {
        final long now = System.currentTimeMillis();
        final long delayMs = this.delay.getValue().longValue();
        int released = 0;
        long oldestAge = 0L;

        while (!this.packetQueue.isEmpty()) {
            final PacketEntry entry = this.packetQueue.peek();
            if (entry == null || now - entry.timestamp < delayMs) {
                break;
            }

            this.packetQueue.poll();
            oldestAge = Math.max(oldestAge, now - entry.timestamp);
            this.handlePacket(entry.packet);
            released++;
        }

        if (released > 0) {
            this.debug("released delayed packets=" + released
                    + " age=" + oldestAge + "ms"
                    + " queue=" + this.packetQueue.size());
        }

        if (this.packetQueue.isEmpty()) {
            this.positionTracker = null;
            this.backtrackingActive = false;
            this.debugBlockedPackets = 0;
        }
    }

    private void releasePackets() {
        this.releasePackets("release");
    }

    private void releasePackets(final String reason) {
        final int queued = this.packetQueue.size();
        if (this.backtrackingActive) {
            this.backtrackingActive = false;
            int released = 0;
            PacketEntry entry;
            while ((entry = this.packetQueue.poll()) != null) {
                this.handlePacket(entry.packet);
                released++;
            }
            this.debug("released packets=" + released + " reason=" + reason);
        } else if (queued > 0) {
            this.debug("cleared packets=" + queued + " reason=" + reason);
        }
        this.packetQueue.clear();
        this.debugBlockedPackets = 0;
    }

    private void handlePacket(final Packet<?> packet) {
        final ClientConnection connection = this.getConnection();
        if (connection == null) {
            return;
        }

        this.releasingPackets = true;
        try {
            ((ClientConnectionAccess) connection).oraculus$channelReadSilent(packet);
        } catch (Exception ignored) {
        } finally {
            this.releasingPackets = false;
        }
    }

    private ClientConnection getConnection() {
        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        return networkHandler == null ? null : networkHandler.getConnection();
    }

    private void drawOutlinedBox(final WorldRenderer renderer, final RenderWorldEvent event, final Vec3d min, final Vec3d dimensions, final int color) {
        final Vec3d max = min.add(dimensions);
        final Vec3d p000 = new Vec3d(min.x, min.y, min.z);
        final Vec3d p001 = new Vec3d(min.x, min.y, max.z);
        final Vec3d p010 = new Vec3d(min.x, max.y, min.z);
        final Vec3d p011 = new Vec3d(min.x, max.y, max.z);
        final Vec3d p100 = new Vec3d(max.x, min.y, min.z);
        final Vec3d p101 = new Vec3d(max.x, min.y, max.z);
        final Vec3d p110 = new Vec3d(max.x, max.y, min.z);
        final Vec3d p111 = new Vec3d(max.x, max.y, max.z);

        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p000, p100, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p100, p101, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p101, p001, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p001, p000, color);

        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p010, p110, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p110, p111, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p111, p011, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p011, p010, color);

        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p000, p010, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p100, p110, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p101, p111, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true), p001, p011, color);
    }

    @Override
    protected void onEnable() {
        this.positionTracker = null;
        this.backtrackingActive = false;
        this.packetQueue.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.positionTracker = null;
        this.releasePackets("disable");
        super.onDisable();
    }

    private void debug(final String message) {
        if (this.debug.getValue()) {
            ChatUtility.print("Backtrack Debug | " + message);
        }
    }

    private String packetName(final Packet<?> packet) {
        return packet.getClass().getSimpleName();
    }

    private static String format(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class PacketEntry {
        private final Packet<?> packet;
        private final long timestamp;

        private PacketEntry(final Packet<?> packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final class PositionTracker {
        private final PlayerEntity player;
        private final TrackedPosition trackedPosition = new TrackedPosition();
        private Vec3d currentPos;
        private Vec3d lastPos;

        private PositionTracker(final PlayerEntity player, final Vec3d position) {
            this.player = player;
            this.currentPos = position;
            this.lastPos = position;
            this.trackedPosition.setPos(position);
        }

        private Vec3d decodeRelativePos(final short deltaX, final short deltaY, final short deltaZ) {
            return this.trackedPosition.withDelta(deltaX, deltaY, deltaZ);
        }

        private void setCurrentPos(final Vec3d position) {
            this.currentPos = position;
            this.trackedPosition.setPos(position);
        }

        private void updatePos(final Vec3d position) {
            this.lastPos = this.currentPos;
            this.setCurrentPos(position);
        }

        private void applyPos() {
            this.lastPos = this.currentPos;
        }

        private Vec3d getInterpolatedPos(final float tickDelta) {
            return new Vec3d(
                    this.lastPos.x + (this.currentPos.x - this.lastPos.x) * tickDelta,
                    this.lastPos.y + (this.currentPos.y - this.lastPos.y) * tickDelta,
                    this.lastPos.z + (this.currentPos.z - this.lastPos.z) * tickDelta
            );
        }
    }
}
