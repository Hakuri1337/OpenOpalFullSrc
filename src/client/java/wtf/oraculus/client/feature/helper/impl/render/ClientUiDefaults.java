package wtf.oraculus.client.feature.helper.impl.render;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static wtf.oraculus.client.Constants.DIRECTORY;
import static wtf.oraculus.client.Constants.mc;

/** Applies Oraculus-owned first-run client defaults without overwriting user preferences later. */
public final class ClientUiDefaults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path GUI_SCALE_MARKER = DIRECTORY.toPath().resolve(".gui-scale-default-v1");
    private static boolean initialized;

    private ClientUiDefaults() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            Files.createDirectories(DIRECTORY.toPath());
            if (Files.exists(GUI_SCALE_MARKER)) {
                return;
            }

            mc.options.getGuiScale().setValue(2);
            mc.options.write();
            Files.writeString(GUI_SCALE_MARKER, "GUI Scale default applied: 2");
        } catch (IOException exception) {
            LOGGER.warn("Unable to apply Oraculus' default GUI scale", exception);
        }
    }
}
