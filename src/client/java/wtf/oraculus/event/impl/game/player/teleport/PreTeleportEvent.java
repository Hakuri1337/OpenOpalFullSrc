package wtf.oraculus.event.impl.game.player.teleport;

//import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import wtf.oraculus.event.EventCancellable;

import java.util.Set;

public final class
PreTeleportEvent extends EventCancellable {
    private final int teleportId;
    private EntityPosition change;
    private final Set<PositionFlag> relatives;

    public PreTeleportEvent(int teleportId, EntityPosition change, Set<PositionFlag> relatives) {
        this.teleportId = teleportId;
        this.change = change;
        this.relatives = relatives;
    }

    public int getTeleportId() {
        return teleportId;
    }

    public EntityPosition getChange() {
        return change;
    }

    public void setChange(EntityPosition change) {
        this.change = change;
    }

    public Set<PositionFlag> getRelatives() {
        return relatives;
    }
}
