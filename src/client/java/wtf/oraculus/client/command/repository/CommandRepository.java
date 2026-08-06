package wtf.oraculus.client.command.repository;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.command.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static wtf.oraculus.client.Constants.mc;

public final class CommandRepository {

    private static final CommandDispatcher<CommandSource> DISPATCHER = new CommandDispatcher<>();
    private static final List<Command> COMMANDS = new ArrayList<>();

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

    public static ParseResults<CommandSource> parse(final StringReader reader) {
        return DISPATCHER.parse(reader, mc.getNetworkHandler().getCommandSource());
    }

    public static CompletableFuture<Suggestions> getCompletionSuggestions(
            final ParseResults<CommandSource> parse, final int cursor) {
        return DISPATCHER.getCompletionSuggestions(parse, cursor);
    }

    public List<Command> getCommands() {
        return List.copyOf(COMMANDS);
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
