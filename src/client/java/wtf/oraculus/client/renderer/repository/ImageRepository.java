package wtf.oraculus.client.renderer.repository;

import net.fabricmc.loader.api.FabricLoader;
import wtf.oraculus.client.renderer.image.NVGImageRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public final class ImageRepository {

    private static final HashMap<String, NVGImageRenderer> imageMap = new HashMap<>();

    public static NVGImageRenderer getImage(final Path path, final int flags) {
        final String pathString = path.toString();

        if (imageMap.containsKey(pathString)) {
            return imageMap.get(pathString);
        }

        try {
            imageMap.put(pathString, new NVGImageRenderer(Files.newInputStream(path), flags));
            return imageMap.get(pathString);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static NVGImageRenderer getImage(final String path, final int flags) {
        if (imageMap.containsKey(path))
            return imageMap.get(path);

        final Path pathURL = FabricLoader.getInstance().getModContainer("oraculus")
                .flatMap(c -> c.findPath("assets/oraculus/" + path))
                .orElse(null);

        try {
            imageMap.put(path, new NVGImageRenderer(Files.newInputStream(pathURL), flags));

            return imageMap.get(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static NVGImageRenderer getImage(final String path) {
        if (imageMap.containsKey(path))
            return imageMap.get(path);

        final Path pathURL = FabricLoader.getInstance().getModContainer("oraculus")
                .flatMap(c -> c.findPath("assets/oraculus/" + path))
                .orElse(null);

        try {
            imageMap.put(path, new NVGImageRenderer(Files.newInputStream(pathURL)));

            return imageMap.get(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
