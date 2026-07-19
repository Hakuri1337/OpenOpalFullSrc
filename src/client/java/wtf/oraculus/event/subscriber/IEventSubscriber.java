package wtf.oraculus.event.subscriber;

public interface IEventSubscriber {
    default boolean isHandlingEvents() {
        return true;
    }
}
