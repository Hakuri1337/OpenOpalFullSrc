package wtf.oraculus.client.feature.helper.impl.server.impl;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import wtf.oraculus.client.feature.helper.impl.server.KnownServer;

import static wtf.oraculus.client.Constants.mc;

public final class CubecraftServer extends KnownServer {
    public CubecraftServer() {
        super("Cubecraft");
    }

    @Override
    public boolean isValidTarget(LivingEntity livingEntity) {
        if (livingEntity instanceof PlayerEntity player) {
            final PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (playerListEntry == null || playerListEntry.getProfile() == null) {
                return false;
            }
            final Team scoreboardTeam = playerListEntry.getScoreboardTeam();
            return scoreboardTeam != null && !scoreboardTeam.getName().equals(player.getName().getString()); // antibot
        }
        return true;
    }
}
