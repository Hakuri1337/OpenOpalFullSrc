package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;

/** SSNG BlockFly state adapted to Oraculus' mature ordered blockage queue. */
public final class SsngPacketOrderManager {
    private static final BlockHolder OUTBOUND = new BlockHolder(OutboundNetworkBlockage.get());
    private static boolean desyncing, swap, rightClicking;
    private static int desyncTick;

    private SsngPacketOrderManager() { }

    public static void setup() { if (!desyncing) { OUTBOUND.block(); desyncing = true; desyncTick = 0; } }
    public static void tick() { if (desyncing) desyncTick++; }
    public static void release() { OUTBOUND.release(); desyncing = false; desyncTick = 0; swap = false; rightClicking = false; }
    public static boolean isDesyncing() { return desyncing; }
    public static int desyncTick() { return desyncTick; }
    public static void markSwap() { swap = true; }
    public static void markRightClicking() { rightClicking = true; }
    public static boolean isSwap() { return swap; }
    public static boolean isRightClicking() { return rightClicking; }
}
