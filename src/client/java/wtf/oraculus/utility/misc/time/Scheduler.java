package wtf.oraculus.utility.misc.time;

import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Scheduler implements IHelper {

    private static final Map<Runnable, AtomicInteger> TASKS = new ConcurrentHashMap<>();

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        TASKS.forEach((function, remainingTicks) -> {
            if (remainingTicks.getAndDecrement() < 1) {
                TASKS.remove(function);
                function.run();
            }
        });
    }

    public static void addTask(final Runnable function, final int tickDelay) {
        TASKS.put(function, new AtomicInteger(tickDelay));
    }

    static {
        EventDispatcher.subscribe(new Scheduler());
    }

}
