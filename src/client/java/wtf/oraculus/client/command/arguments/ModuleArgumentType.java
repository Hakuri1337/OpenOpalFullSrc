package wtf.oraculus.client.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.UnknownModuleException;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public final class ModuleArgumentType implements ArgumentType<Module> {

    private static final ModuleArgumentType INSTANCE = new ModuleArgumentType();
    private static final DynamicCommandExceptionType NO_SUCH_MODULE = new DynamicCommandExceptionType(name -> Text.literal("Module with name " + name + " doesn't exist."));

    private static final Collection<String> EXAMPLES = OraculusClient.getInstance().getModuleRepository().getModules()
            .stream()
            .limit(3)
            .map(Module::getId).toList();

    public static ModuleArgumentType create() {
        return INSTANCE;
    }

    public static Module get(final CommandContext<?> context) {
        return context.getArgument("module", Module.class);
    }

    private ModuleArgumentType() {
    }

    @Override
    public Module parse(final StringReader reader) throws CommandSyntaxException {
        final String argument = reader.readString();
        try {
            return OraculusClient.getInstance().getModuleRepository().getModule(argument);
        } catch (UnknownModuleException e) {
            throw NO_SUCH_MODULE.create(argument);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(OraculusClient.getInstance().getModuleRepository().getModules().stream().map(Module::getId), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
