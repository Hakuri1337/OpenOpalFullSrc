package wtf.opal.client.command.impl.module;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.client.OpalClient;
import wtf.opal.client.command.Command;
import wtf.opal.client.feature.module.DeprecatedModule;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.UnknownModuleException;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DeprecatedModulesCommand extends Command {

    public DeprecatedModulesCommand() {
        super("deprecated_modules", "Lists and manages modules hidden from module GUIs.", "deprecatedmodules");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("list").executes(context -> {
            printDeprecatedModules();
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("module", StringArgumentType.word())
                .suggests((context, suggestions) -> CommandSource.suggestMatching(
                        getDeprecatedModules().stream().map(Module::getId), suggestions
                ))
                .then(literal("toggle").executes(context -> {
                    final String moduleName = StringArgumentType.getString(context, "module");
                    final Module module = findDeprecatedModule(moduleName);
                    if (module == null) {
                        ChatUtility.error("Deprecated module " + moduleName + " does not exist.");
                        return SINGLE_SUCCESS;
                    }

                    module.setVisible(!module.isVisible());
                    ChatUtility.success(module.getName() + " is now " + (module.isVisible() ? "shown" : "hidden") + " in module GUIs.");
                    return SINGLE_SUCCESS;
                })));
    }

    private static void printDeprecatedModules() {
        final List<Module> modules = getDeprecatedModules();
        if (modules.isEmpty()) {
            ChatUtility.print("No deprecated modules are registered.");
            return;
        }

        final String entries = modules.stream()
                .map(module -> module.getName() + " [" + (module.isVisible() ? "shown" : "hidden") + "]")
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
        ChatUtility.print("Deprecated modules: " + entries);
    }

    private static Module findDeprecatedModule(final String moduleName) {
        try {
            final Module module = OpalClient.getInstance().getModuleRepository().getModule(moduleName);
            return module instanceof DeprecatedModule ? module : null;
        } catch (UnknownModuleException ignored) {
            return null;
        }
    }

    private static List<Module> getDeprecatedModules() {
        return OpalClient.getInstance().getModuleRepository().getModules().stream()
                .filter(DeprecatedModule.class::isInstance)
                .sorted(Comparator.comparing(Module::getName))
                .toList();
    }
}
