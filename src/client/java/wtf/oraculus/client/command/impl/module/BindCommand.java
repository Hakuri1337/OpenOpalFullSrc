package wtf.oraculus.client.command.impl.module;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.binding.repository.BindRepository;
import wtf.oraculus.client.binding.type.InputType;
import wtf.oraculus.client.command.Command;
import wtf.oraculus.client.command.arguments.BindArgumentType;
import wtf.oraculus.client.command.arguments.ConfigArgumentType;
import wtf.oraculus.client.command.arguments.ModuleArgumentType;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.utility.data.Config;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.List;
import java.util.Objects;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class BindCommand extends Command {

    private static final BindRepository BIND_REPOSITORY = OraculusClient.getInstance().getBindRepository();

    public BindCommand() {
        super("bind", "Sets the keybind of specified action to the specified key.", "b");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("list").executes(context -> {
            final MutableText text = Text.literal("§lBinds §r").formatted(Formatting.YELLOW)
                    .append(Text.literal("(" + BIND_REPOSITORY.getBindingService().getBindingMap().size() + "): ").formatted(Formatting.GRAY));

            BIND_REPOSITORY.getBindingService().getBindingMap().asMap().forEach((key, bindables) -> {
                final MutableText keyText = Text.literal("• " + BIND_REPOSITORY.getNameFromInteger(key.first)).formatted(Formatting.GRAY);

                List<String> names = bindables.stream()
                        .map(bindable -> (bindable instanceof Module module) ? Formatting.GRAY + "• Module: " + Formatting.YELLOW + module.getName() :
                                (bindable instanceof Config config) ? Formatting.GRAY + "• Config: " + Formatting.YELLOW + config.getName() : null)
                        .filter(Objects::nonNull)
                        .toList();

                if (!names.isEmpty()) {
                    keyText.setStyle(keyText.getStyle().withHoverEvent(
                            new HoverEvent.ShowText(Text.literal(String.join("\n", names)).formatted(Formatting.GRAY))
                    ));
                }

                text.append("\n").append(keyText);
            });

            ChatUtility.display(text);
            return SINGLE_SUCCESS;
        }));


        builder.then(literal("module")
                .then(argument("module_name", ModuleArgumentType.create())
                        .then(argument("bind", BindArgumentType.create())
                                .executes(context -> {
                                    final Module module = context.getArgument("module_name", Module.class);
                                    final String bind = context.getArgument("bind", String.class);
                                    final String bindName = bind.toUpperCase();

                                    if (bindName.equals("CLEAR")) {
                                        BIND_REPOSITORY.getBindingService().clearBindings(module);

                                        ChatUtility.print("Binds for §l" + module.getName() + "§7 have been cleared!");
                                        return SINGLE_SUCCESS;
                                    }

                                    final Integer bindCode = BIND_REPOSITORY.getNamedBindingMap().get(bindName);

                                    if (bindCode == null) {
                                        ChatUtility.error("Invalid bind: §l" + bindName);
                                        return SINGLE_SUCCESS;
                                    }

                                    if (bindCode < 10) {
                                        BIND_REPOSITORY.getBindingService().register(bindCode, module, InputType.MOUSE);
                                    } else {
                                        BIND_REPOSITORY.getBindingService().register(bindCode, module, InputType.KEYBOARD);
                                    }

                                    ChatUtility.print("Set §l" + module.getName() + "§7 bind to §l" + bindName + "§7!");
                                    return SINGLE_SUCCESS;
        }))));

        builder.then(literal("config")
                .then(argument("config_name", ConfigArgumentType.create())
                        .then(argument("bind", BindArgumentType.create())
                                .executes(context -> {
                                    final String configName = context.getArgument("config_name", String.class).toLowerCase();
                                    final String bind = context.getArgument("bind", String.class);
                                    final String bindName = bind.toUpperCase();

                                    final Config tempConfigObj = new Config(configName);

                                    if (bindName.equals("CLEAR")) {
                                        final List<Config> configsToClear = BIND_REPOSITORY.getBindingService().getBindingMap().values().stream()
                                                .filter(bindable -> bindable instanceof Config c && c.getName().equalsIgnoreCase(configName))
                                                .map(Config.class::cast)
                                                .toList();
                                        configsToClear.forEach(BIND_REPOSITORY.getBindingService()::clearBindings);

                                        ChatUtility.print("Binds for " + configName + " have been cleared!");
                                        return SINGLE_SUCCESS;
                                    }

                                    final Integer bindCode = BIND_REPOSITORY.getNamedBindingMap().get(bindName);

                                    if (bindCode == null) {
                                        ChatUtility.error("Invalid bind: §l" + bindName);
                                        return SINGLE_SUCCESS;
                                    }

                                    if (bindCode < 10) {
                                        BIND_REPOSITORY.getBindingService().register(bindCode, tempConfigObj, InputType.MOUSE);
                                    } else {
                                        BIND_REPOSITORY.getBindingService().register(bindCode, tempConfigObj, InputType.KEYBOARD);
                                    }

                                    ChatUtility.print("Set " + configName + "'s bind to §l" + bindName + "§7!");
                                    return SINGLE_SUCCESS;
        }))));
    }

}
