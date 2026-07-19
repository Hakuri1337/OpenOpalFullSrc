package wtf.oraculus.client.feature.module.property.impl.mode;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.subscriber.IEventSubscriber;

public abstract class ModuleMode<T extends Module> implements IEventSubscriber {

    protected T module;

    private boolean enabled;

    protected ModuleMode(final T module) {
        this.module = module;
        EventDispatcher.subscribe(this);
    }

    public T getModule() {
        return module;
    }

    public void onEnable() {
        if (module.getActiveMode() == this) {
            enabled = true;
        }
    }

    public void onDisable() {
        if (module.getActiveMode() == this) {
            enabled = false;
        }
    }

    @Override
    public boolean isHandlingEvents() {
        return enabled;
    }

    public abstract Enum<?> getEnumValue();

}
