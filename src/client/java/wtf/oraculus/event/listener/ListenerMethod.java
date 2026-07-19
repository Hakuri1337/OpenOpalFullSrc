package wtf.oraculus.event.listener;

import wtf.oraculus.event.EventCancellable;
import wtf.oraculus.event.subscriber.IEventSubscriber;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

public final class ListenerMethod {
    private final int priority;
    private final CallSite callSite;
    private final IEventSubscriber subscriber;
    private final MethodHandle dynamicInvoker;

    public ListenerMethod(final int priority, final CallSite callSite, final IEventSubscriber subscriber) {
        this.priority = -priority;
        this.callSite = callSite;
        this.subscriber = subscriber;
        this.dynamicInvoker = callSite.dynamicInvoker();
    }

    public CallSite getCallSite() {
        return callSite;
    }

    public int getPriority() {
        return priority;
    }

    public boolean invoke(Object event) {
        if (subscriber.isHandlingEvents()) {
            try {
                dynamicInvoker.invoke((Object) subscriber, event);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw new RuntimeException("Error invoking event", throwable);
            }
            return event instanceof EventCancellable cancellable && cancellable.isCancelled();
        }
        return false;
    }
}