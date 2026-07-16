package wtf.opal.client.feature.module.impl.world.blockfly.tick;

import net.minecraft.entity.Entity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class BlockFlyDelayedTickQueue {
    private static final Queue<DelayedTick> TASKS = new ConcurrentLinkedQueue<>();

    private BlockFlyDelayedTickQueue() {
    }

    public static void add(final Runnable task, final boolean skipEntityTick) {
        TASKS.add(new DelayedTick(task, skipEntityTick));
    }

    public static TickResult consumeFor(final Entity entity) {
        if (mc.player == null || entity != mc.player || TASKS.isEmpty()) {
            return TickResult.NONE;
        }
        final DelayedTick task = TASKS.poll();
        if (task != null) {
            try {
                task.action().run();
            } catch (final RuntimeException exception) {
                TASKS.clear();
                throw exception;
            }
        }
        return task != null && task.skipEntityTick() ? TickResult.SKIP : TickResult.CONTINUE;
    }

    public static int size() {
        return TASKS.size();
    }

    public static boolean isEmpty() {
        return TASKS.isEmpty();
    }

    public static void clear() {
        TASKS.clear();
    }

    public enum TickResult {
        NONE,
        SKIP,
        CONTINUE
    }

    private record DelayedTick(Runnable action, boolean skipEntityTick) {
    }
}
