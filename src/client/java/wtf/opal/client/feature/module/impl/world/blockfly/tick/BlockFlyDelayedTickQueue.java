package wtf.opal.client.feature.module.impl.world.blockfly.tick;

import net.minecraft.entity.Entity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class BlockFlyDelayedTickQueue {
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    private BlockFlyDelayedTickQueue() {
    }

    public static void add(final Runnable task) {
        TASKS.add(task);
    }

    public static boolean consumeFor(final Entity entity) {
        if (mc.player == null || entity != mc.player || TASKS.isEmpty()) {
            return false;
        }
        final Runnable task = TASKS.poll();
        if (task != null) {
            try {
                task.run();
            } catch (final RuntimeException exception) {
                TASKS.clear();
                throw exception;
            }
        }
        return true;
    }

    public static int size() {
        return TASKS.size();
    }

    public static void clear() {
        TASKS.clear();
    }
}
