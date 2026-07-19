package wtf.oraculus.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import wtf.oraculus.client.renderer.NVGRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class Constants {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEGACY_DIRECTORY_NAME = "opal";
    private static final String MIGRATION_MARKER = ".legacy-opal-migrated";

    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final long VG = NVGRenderer.getContext();
    public static final File DIRECTORY = new File(mc.runDirectory, "oraculus");

    static {
        migrateLegacyDirectory();
    }

    public static final double FIRST_FALL_MOTION = 0.0784000015258789D;

    private static void migrateLegacyDirectory() {
        final Path target = DIRECTORY.toPath();
        final Path legacy = mc.runDirectory.toPath().resolve(LEGACY_DIRECTORY_NAME);
        final Path marker = target.resolve(MIGRATION_MARKER);
        if (!Files.isDirectory(legacy) || Files.exists(marker)) {
            return;
        }

        try {
            if (!Files.exists(target)) {
                try {
                    Files.move(legacy, target);
                    Files.writeString(marker, "Migrated from " + LEGACY_DIRECTORY_NAME);
                    return;
                } catch (IOException moveFailure) {
                    LOGGER.warn("Unable to move the legacy data directory directly; copying it instead", moveFailure);
                }
            }

            Files.createDirectories(target);
            try (Stream<Path> paths = Files.walk(legacy).sorted(Comparator.naturalOrder())) {
                for (final Path source : paths.toList()) {
                    final Path destination = target.resolve(legacy.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else if (!Files.exists(destination)) {
                        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            Files.writeString(marker, "Copied from " + LEGACY_DIRECTORY_NAME);
        } catch (IOException exception) {
            LOGGER.error("Unable to migrate the legacy client data directory", exception);
        }
    }
}
