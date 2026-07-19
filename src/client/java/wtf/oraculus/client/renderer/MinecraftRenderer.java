package wtf.oraculus.client.renderer;

import java.util.LinkedList;
import java.util.Queue;

public final class MinecraftRenderer {

    private static final Queue<Runnable> RENDER_QUEUE = new LinkedList<>();

    private MinecraftRenderer() {
    }

    public static void addToQueue(final Runnable runnable) {
        RENDER_QUEUE.add(runnable);
    }

    public static void render() {
        while (!RENDER_QUEUE.isEmpty()) {
            RENDER_QUEUE.poll().run();
        }
    }

}
