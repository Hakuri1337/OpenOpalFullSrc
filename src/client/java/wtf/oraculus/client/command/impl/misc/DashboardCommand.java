package wtf.oraculus.client.command.impl.misc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Util;
import wtf.oraculus.client.command.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DashboardCommand extends Command {

    public DashboardCommand() {
        super("dashboard", "Opens the Oraculus dashboard.", "dash");
    }

    @Override
    protected void onCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Util.getOperatingSystem().open("https://oraculusclient.com/dash");
            return SINGLE_SUCCESS;
        });
    }

}
