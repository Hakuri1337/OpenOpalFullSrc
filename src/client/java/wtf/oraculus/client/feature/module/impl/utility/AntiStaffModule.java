package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.antistaff.OpenZenStaffList;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.lang.reflect.Method;

import static wtf.oraculus.client.Constants.mc;

public final class AntiStaffModule extends Module {

    private boolean triggered;

    public AntiStaffModule() {
        super("AntiStaff", "Leaves the server when a staff account is detected.", ModuleCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        this.triggered = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.triggered = false;
        super.onDisable();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null || this.triggered) {
            return;
        }

        if (event.getPacket() instanceof PlayerListS2CPacket packet) {
            for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                if (this.isStaffName(this.extractProfileName(entry.profile()))) {
                    this.exitGame();
                    return;
                }

                final Text displayName = entry.displayName();
                if (displayName != null && this.isStaffName(displayName.getString())) {
                    this.exitGame();
                    return;
                }
            }
            return;
        }

        if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType().isSummonable()) {
            final PlayerEntity player = mc.world.getPlayers().stream()
                    .filter(worldPlayer -> worldPlayer.getId() == packet.getEntityId())
                    .findFirst()
                    .orElse(null);

            if (player != null && this.isStaffName(player.getName().getString())) {
                this.exitGame();
            }
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null || this.triggered) {
            return;
        }

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (this.isStaffName(this.extractProfileName(entry.getProfile()))) {
                this.exitGame();
                return;
            }

            final Text displayName = entry.getDisplayName();
            if (displayName != null && this.isStaffName(displayName.getString())) {
                this.exitGame();
                return;
            }
        }

        if (mc.world == null) {
            return;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && this.isStaffName(player.getName().getString())) {
                this.exitGame();
                return;
            }
        }
    }

    private boolean isStaffName(final String name) {
        return OpenZenStaffList.contains(name);
    }

    private String extractProfileName(final Object profile) {
        if (profile == null) {
            return null;
        }

        for (final String methodName : new String[]{"getName", "name"}) {
            try {
                final Method method = profile.getClass().getMethod(methodName);
                final Object value = method.invoke(profile);
                if (value instanceof String string) {
                    return string;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private void exitGame() {
        if (this.triggered) {
            return;
        }

        this.triggered = true;
        ChatUtility.print("Staff detected!");

        if (mc.player != null && mc.player.networkHandler != null) {
            ChatUtility.sendCommand("hub");
        }
    }
}
