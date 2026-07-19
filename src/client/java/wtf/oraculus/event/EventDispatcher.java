package wtf.oraculus.event;

import wtf.oraculus.event.registry.EventRegistry;

public final class
EventDispatcher {
    private EventDispatcher() {
    }

    private static final EventRegistry eventRegistry = new EventRegistry();

    public static void subscribe(final Object subscriber) {
        eventRegistry.subscribe(subscriber);
    }

    public static void dispatch(final Object event) {
        eventRegistry.dispatch(event);
    }
}
