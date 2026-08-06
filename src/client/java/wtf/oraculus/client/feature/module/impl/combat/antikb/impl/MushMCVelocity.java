package wtf.oraculus.client.feature.module.impl.combat.antikb.impl;

import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBMode;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBModule;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class MushMCVelocity extends AntiKBMode {

    public MushMCVelocity(AntiKBModule module) {
        super(module);
    }

    private boolean cancel;

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (mc.player != null && packet.getEntityId() == mc.player.getId()) {
                event.setCancelled();
                this.cancel = true;
            }
        } else if (event.getPacket() instanceof CommonPingS2CPacket) {
            if (this.cancel) {
                event.setCancelled();
                this.cancel = false;
            }
        } else if (event.getPacket() instanceof GameJoinS2CPacket) {
            this.cancel = false;
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return AntiKBModule.Mode.MUSHMC;
    }
}
