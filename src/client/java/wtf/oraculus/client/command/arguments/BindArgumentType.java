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
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.binding.repository.BindRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BindArgumentType implements ArgumentType<String> {
    private static final BindArgumentType INSTANCE = new BindArgumentType();
    private static final DynamicCommandExceptionType NO_SUCH_BIND = new DynamicCommandExceptionType(name -> Text.literal("No bind with name " + name + " exists."));
    private final List<String> binds = new ArrayList<>();
    private static final Collection<String> EXAMPLES = List.of("RIGHT_SHIFT", "G", "MOUSE_0", "CLEAR");

    public static BindArgumentType create() {
        return INSTANCE;
    }

    public static String get(final CommandContext<?> context) {
        return context.getArgument("bind", String.class);
    }

    private BindArgumentType() {
        for (final Field field : GLFW.class.getDeclaredFields()) {
            if (field.getName().startsWith(BindRepository.GLFW_KEY_PREFIX)) {
                binds.add(field.getName().substring(BindRepository.GLFW_KEY_PREFIX.length()));
            }
        }
        for (int i = 0; i < 10; i++) {
            binds.add("MOUSE_" + i);
        }
        binds.add("CLEAR");
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        final String argument = reader.readString();

        if (!binds.contains(argument.toUpperCase())) {
            throw NO_SUCH_BIND.create(argument);
        }

        return argument;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(binds, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}