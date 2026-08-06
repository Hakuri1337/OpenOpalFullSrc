package wtf.oraculus.client.command.impl.module;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.command.Command;
import wtf.oraculus.client.command.arguments.ModuleArgumentType;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class
ToggleCommand extends Command {

    public ToggleCommand() {
        super("toggle", "Enables or disables specified module.", "t");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("module", ModuleArgumentType.create()).executes(context -> {
            Module module = context.getArgument("module", Module.class);
            module.toggle();
            ChatUtility.print(module.getName() + " has been " + (module.isEnabled() ? "enabled" : "disabled") + "!");
            return SINGLE_SUCCESS;
        }));
    }
}
