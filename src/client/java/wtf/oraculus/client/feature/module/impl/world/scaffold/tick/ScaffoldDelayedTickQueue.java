package wtf.oraculus.client.feature.module.impl.world.scaffold.tick;

import net.minecraft.entity.Entity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldDelayedTickQueue {
    private static final Queue<DelayedTick> TASKS = new ConcurrentLinkedQueue<>();

    private ScaffoldDelayedTickQueue() {
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
