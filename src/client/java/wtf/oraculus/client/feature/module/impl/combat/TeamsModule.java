package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

import java.util.Objects;

import static wtf.oraculus.client.Constants.mc;

/** OpenZen Teams adapted to Oraculus's module and property APIs. */
public final class TeamsModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.SCOREBOARD);

    public TeamsModule() {
        super("Teams", "Prevents you from attacking teammates.", ModuleCategory.WORLD);
        this.addProperties(this.mode);
    }

    public static boolean isTeammate(final Entity entity) {
        final TeamsModule module = OraculusClient.getInstance().getModuleRepository().getModule(TeamsModule.class);
        if (module == null || !module.isEnabled() || mc.player == null || !(entity instanceof PlayerEntity)) {
            return false;
        }

        if (module.mode.is(Mode.COLOR)) {
            final Integer entityColor = entity.getTeamColorValue();
            final Integer playerColor = mc.player.getTeamColorValue();
            return entityColor.equals(playerColor);
        }

        return Objects.equals(getTeam(entity), getTeam(mc.player));
    }

    public static String getTeam(final Entity entity) {
        if (entity == null || mc.getNetworkHandler() == null) {
            return null;
        }
        final PlayerListEntry playerInfo = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (playerInfo == null || playerInfo.getScoreboardTeam() == null) {
            return null;
        }
        return playerInfo.getScoreboardTeam().getName();
    }

    private enum Mode {
        COLOR("Color"),
        SCOREBOARD("Scoreboard");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
