package wtf.oraculus.client.irc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.auth.AuthBootstrap;
import wtf.oraculus.client.auth.AuthService;
import wtf.oraculus.mixin.PlayerInteractEntityC2SPacketAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static wtf.oraculus.client.Constants.mc;

public final class IrcAttackPolicy {
    private static long lastNoticeAt;

    private IrcAttackPolicy() {
    }

    public static boolean canAttack(final Entity target) {
        if (!(target instanceof PlayerEntity player)) return true;
        final AuthService auth = AuthBootstrap.getService();
        if (auth == null || !"FREE".equalsIgnoreCase(auth.snapshot().tier())) return true;
        final IrcService irc = OraculusClient.getInstance().getIrcService();
        return irc == null || !irc.isProtectedProfile(player.getUuid());
    }

    public static boolean shouldBlock(final Packet<?> packet) {
        if (!(packet instanceof PlayerInteractEntityC2SPacket interact) || mc.world == null) return false;
        final PlayerInteractEntityC2SPacketAccessor accessor = (PlayerInteractEntityC2SPacketAccessor) interact;
        if (accessor.getType() != PlayerInteractEntityC2SPacketAccessor.getAttackHandler()) return false;
        final Entity target = mc.world.getEntityById(accessor.getEntityId());
        return target != null && !canAttack(target);
    }

    public static void notifyBlocked() {
        final long timestamp = System.currentTimeMillis();
        if (timestamp - lastNoticeAt < 1500L) return;
        lastNoticeAt = timestamp;
        ChatUtility.error("Free 用户不能攻击当前在线的 Oraculus 用户。");
    }
}
