package wtf.oraculus.client.command.impl.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import wtf.oraculus.client.Constants;
import net.minecraft.command.CommandSource;
import wtf.oraculus.client.command.Command;
import wtf.oraculus.client.command.arguments.ConfigArgumentType;
import wtf.oraculus.utility.data.SaveUtility;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.List;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", "Interacts with configs.", "c");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            printConfigs();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("save").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.saveConfig(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 saved!");
            } else {
                ChatUtility.error("Failed to save config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            printConfigs();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("load").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.loadConfigFile(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 loaded!");
            } else {
                ChatUtility.error("Failed to load config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = getConfigName(context.getArgument("config_name", String.class));

            if (SaveUtility.deleteConfig(configName)) {
                ChatUtility.success("Config \u00a7l" + configName + "\u00a77 deleted!");
            } else {
                ChatUtility.error("Failed to delete config \u00a7l" + configName + "\u00a77.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("folder").executes(context -> {
            openConfigFolder();
            return SINGLE_SUCCESS;
        }));
    }

    private static String getConfigName(final String configName) {
        return configName == null ? "" : configName.trim().toLowerCase();
    }

    private static void printConfigs() {
        final List<String> configs = SaveUtility.listConfigs();
        if (configs.isEmpty()) {
            ChatUtility.print("No configs found.");
            return;
        }

        ChatUtility.print("Configs: \u00a7l" + String.join("\u00a77, \u00a7l", configs) + "\u00a77");
    }

    private static void openConfigFolder() {
        final File directory = new File(Constants.DIRECTORY, "configs");
        if (!directory.exists() && !directory.mkdirs()) {
            ChatUtility.error("Unable to create the config folder.");
            return;
        }

        if (!directory.isDirectory()) {
            ChatUtility.error("Config path is not a directory.");
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory);
                ChatUtility.success("Opened config folder.");
                return;
            }

            if (openWithPlatformLauncher(directory)) {
                ChatUtility.success("Opened config folder.");
                return;
            }
        } catch (final IOException | RuntimeException exception) {
            // Fall through to the platform launcher when Desktop is unavailable
            // or rejected by the current Java runtime.
            if (openWithPlatformLauncher(directory)) {
                ChatUtility.success("Opened config folder.");
                return;
            }
        }

        ChatUtility.error("Unable to open the config folder.");
    }

    private static boolean openWithPlatformLauncher(final File directory) {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String launcher;
        final String argument;

        if (os.contains("win")) {
            launcher = "explorer.exe";
            argument = directory.getAbsolutePath();
        } else if (os.contains("mac")) {
            launcher = "open";
            argument = directory.getAbsolutePath();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            launcher = "xdg-open";
            argument = directory.getAbsolutePath();
        } else {
            return false;
        }

        try {
            new ProcessBuilder(launcher, argument).start();
            return true;
        } catch (final IOException | RuntimeException exception) {
            return false;
        }
    }
}
