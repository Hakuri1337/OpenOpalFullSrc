package wtf.oraculus.client.feature.helper.impl.target.impl;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;
import wtf.oraculus.client.feature.helper.impl.target.TargetFlags;
import wtf.oraculus.client.irc.IrcAttackPolicy;
import wtf.oraculus.utility.player.PlayerUtility;

import java.util.Objects;

import static wtf.oraculus.client.Constants.mc;

public final class TargetPlayer extends TargetLivingEntity {

    private boolean strength;

    public TargetPlayer(PlayerEntity entity) {
        super(entity);
    }

    @Override
    public boolean isMatchingFlags(int flags) {
        if (this.isLocal()) {
            return true;
        }
        if (!IrcAttackPolicy.canAttack(this.entity)) {
            return false;
        }

        if (Objects.equals(this.entity.getName().getLiteralString(), "BOT")) { // miniblox/bloxd translation layer antibot
            final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
            if (networkHandler != null) {
                final ServerInfo serverInfo = networkHandler.getServerInfo();
                if (serverInfo != null && serverInfo.address.equals("localhost")) {
                    return false;
                }
            }
        }

        if ((flags & TargetFlags.FRIENDLY) == 0 && PlayerUtility.areOnSameTeam(mc.player, entity)) {
            return false;
        }

        return (flags & TargetFlags.PLAYERS) != 0;
    }

    public boolean hasStrength() {
        return strength;
    }

    public void setStrength(final boolean strength) {
        this.strength = strength;
    }

    @Override
    public boolean isLocal() {
        return this.getEntityId() == mc.player.getId();
    }
}
