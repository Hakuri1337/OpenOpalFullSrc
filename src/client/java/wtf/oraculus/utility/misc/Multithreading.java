package wtf.oraculus.utility.misc;

import java.util.concurrent.*;

public final class Multithreading {

    private static final ScheduledExecutorService SCHEDULE_POOL = Executors.newScheduledThreadPool(4);
    private static final ExecutorService CACHED_POOL = Executors.newCachedThreadPool();

    private Multithreading() {
    }

    public static ScheduledFuture<?> schedule(final Runnable r, final long delay, final TimeUnit unit) {
        return SCHEDULE_POOL.schedule(r, delay, unit);
    }

    public static ScheduledFuture<?> schedulePeriodic(final Runnable r, final long initialDelay, final long delay, final TimeUnit unit) {
        return SCHEDULE_POOL.scheduleAtFixedRate(r, initialDelay, delay, unit);
    }

    public static void runAsync(final Runnable runnable) {
        CACHED_POOL.execute(runnable);
    }

    public static int getTotal() {
        return ((ThreadPoolExecutor) Multithreading.CACHED_POOL).getActiveCount();
    }

}
