package wtf.oraculus.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.ClientPlayerEntityAccessor;

import static wtf.oraculus.client.Constants.mc;

public final class MinibloxDisabler extends AbstractMinibloxDisabler {
    private boolean sendingPayload;
    private int movementPackets;

    public MinibloxDisabler(DisablerModule module) {
        super(module);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (mc.player != null && mc.world != null && mc.getNetworkHandler() != null) {
            this.canSend();
        }
    }

    // Run after packet-buffering listeners so a cancelled movement packet never gets an orphan payload.
    @Subscribe(priority = -100)
    public void onSendPacket(SendPacketEvent event) {
        if (this.sendingPayload
                || !(event.getPacket() instanceof PlayerMoveC2SPacket movePacket)
                || !this.canSend()) {
            return;
        }

        final Vec2f movement = mc.player.input.getMovementInput();
        final float sideways = sanitize(movement.x);
        final float forward = sanitize(movement.y);
        final ClientPlayerEntityAccessor playerAccessor = (ClientPlayerEntityAccessor) mc.player;
        final MovePayload payload = new MovePayload(
                finiteOrZero(playerAccessor.getLastXClient()),
                finiteOrZero(playerAccessor.getLastYClient()),
                finiteOrZero(playerAccessor.getLastZClient()),
                sanitizeAngle(movePacket.getYaw(mc.player.getYaw())),
                sanitizeAngle(movePacket.getPitch(mc.player.getPitch())),
                forward,
                sideways,
                mc.player.input.playerInput.jump(),
                mc.player.input.playerInput.sneak(),
                movePacket.isOnGround(),
                mc.player.isSprinting()
        );

        this.sendingPayload = true;
        try {
            mc.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(payload));
            this.packetSent();
            this.movementPackets++;
        } catch (Throwable throwable) {
            this.stopWithError("move payload send failed: " + throwable.getClass().getSimpleName());
            return;
        } finally {
            this.sendingPayload = false;
        }

        if (this.isDebugWindowElapsed()) {
            this.debug(this.takeDebugWindowPackets() + " payloads/2s"
                    + " | movement=" + this.movementPackets
                    + " | input=(" + forward + "," + sideways + ")"
                    + " | jump=" + payload.jump()
                    + " sneak=" + payload.sneak()
                    + " ground=" + payload.onGround()
                    + " sprint=" + payload.sprint());
            this.movementPackets = 0;
        }
    }

    @Override
    public void onEnable() {
        this.resetModeState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.resetModeState();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.MINIBLOX;
    }

    @Override
    protected String label() {
        return "MiniBlox";
    }

    private void resetModeState() {
        this.sendingPayload = false;
        this.movementPackets = 0;
    }

    private static float sanitize(float value) {
        return Float.isFinite(value) ? MathHelper.clamp(value, -1.0F, 1.0F) : 0.0F;
    }

    private static float sanitizeAngle(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    public record MovePayload(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float forward,
            float sideways,
            boolean jump,
            boolean sneak,
            boolean onGround,
            boolean sprint
    ) implements CustomPayload {
        public static final Id<MovePayload> ID = new Id<>(Identifier.of("miniblox", "movepacket"));
        public static final PacketCodec<PacketByteBuf, MovePayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeDouble(value.x);
                    buf.writeDouble(value.y);
                    buf.writeDouble(value.z);
                    buf.writeFloat(value.yaw);
                    buf.writeFloat(value.pitch);
                    buf.writeFloat(value.forward);
                    buf.writeFloat(value.sideways);
                    buf.writeBoolean(value.jump);
                    buf.writeBoolean(value.sneak);
                    buf.writeBoolean(value.onGround);
                    buf.writeBoolean(value.sprint);
                },
                buf -> new MovePayload(
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
