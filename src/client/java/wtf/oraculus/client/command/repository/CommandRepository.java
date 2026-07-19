package wtf.oraculus.client.command.repository;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.command.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class CommandRepository {

    public static final CommandDispatcher<CommandSource> DISPATCHER = new CommandDispatcher<>();
    public static final List<Command> COMMANDS = new ArrayList<>();

    private CommandRepository(final Builder builder) {
        for (Command command : builder.commands) {
            add(command);
        }

        COMMANDS.sort(Comparator.comparing(Command::getName));
    }

    public static void add(final Command command) {
        COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));
        command.registerTo(DISPATCHER);
        COMMANDS.add(command);
    }

    public static void dispatch(final String message) throws CommandSyntaxException {
        DISPATCHER.execute(message, mc.getNetworkHandler().getCommandSource());
    }

    public List<Command> getCommands() {
        return COMMANDS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public final List<Command> commands = new ArrayList<>();

        public Builder putAll(final Command... commands) {
            Collections.addAll(this.commands, commands);
            return this;
        }

        public CommandRepository build() {
            return new CommandRepository(this);
        }

    }

}
