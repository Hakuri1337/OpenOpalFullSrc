package wtf.oraculus.event.impl.game.player.teleport;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;

import java.util.Set;

public record PostTeleportEvent(int teleportId, EntityPosition change, Set<PositionFlag> relatives) {
}
