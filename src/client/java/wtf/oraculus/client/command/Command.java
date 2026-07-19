package wtf.oraculus.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.command.CommandSource;

import java.util.List;

public abstract class Command {

    private final String name, description;
    private final List<String> aliases;

    protected Command(final String name, final String description, final String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    protected Command(final String name, final String description) {
        this(name, description, new String[0]);
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final List<String> getAliases() {
        return aliases;
    }

    protected static <T> RequiredArgumentBuilder<CommandSource, T> argument(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<CommandSource> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public final void registerTo(final CommandDispatcher<CommandSource> dispatcher) {
        register(dispatcher, name);
        for (final String alias : aliases)
            register(dispatcher, alias);
    }

    public final void register(final CommandDispatcher<CommandSource> dispatcher, final String name) {
        final LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(name);
        onCommand(builder);
        dispatcher.register(builder);
    }

    protected abstract void onCommand(final LiteralArgumentBuilder<CommandSource> builder);

}
