package wtf.oraculus.client.auth;

import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.client.ModuleToggleEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;

public final class AuthRuntimeGate implements IEventSubscriber {
    private final AuthService authService;

    public AuthRuntimeGate(final AuthService authService) {
        this.authService = authService;
        EventDispatcher.subscribe(this);
    }

    @Subscribe(priority = 10000)
    public void onModuleToggle(final ModuleToggleEvent event) {
        if (event.isEnabled() && (!authService.snapshot().allowsRuntime()
                || !RuntimeAccessGate.allowsHotPath())) {
            event.setCancelled();
        }
    }
}
