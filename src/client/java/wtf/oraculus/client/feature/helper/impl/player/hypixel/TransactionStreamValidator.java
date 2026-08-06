package wtf.oraculus.client.feature.helper.impl.player.hypixel;

import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class
TransactionStreamValidator implements IHelper { // should be removed in releases

    private Integer lastTransactionId;

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (event.getPacket() instanceof CommonPongC2SPacket packet) {
            if (packet.getParameter() == 0) return;
            if (this.lastTransactionId != null && packet.getParameter() != this.lastTransactionId - 1) {
//                ChatUtility.error("Invalid transaction id: " + packet.getParameter() + " prev: " + this.lastTransactionId);
                System.out.println("Invalid transaction id: " + packet.getParameter() + " prev: " + this.lastTransactionId);
            }
            this.lastTransactionId = packet.getParameter();
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.lastTransactionId = null;
    }

    @Override
    public boolean isHandlingEvents() {
        return LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer;
    }

    private static TransactionStreamValidator instance;

    public static void setInstance() {
        instance = new TransactionStreamValidator();
        EventDispatcher.subscribe(instance);
    }
}
