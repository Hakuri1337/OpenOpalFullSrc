package wtf.oraculus.client.binding.repository;

import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.binding.BindingService;
import wtf.oraculus.client.binding.type.InputType;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.press.KeyPressEvent;
import wtf.oraculus.event.impl.press.MousePressEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class BindRepository implements IEventSubscriber {

    private final BindingService bindingService = new BindingService();

    private final Map<String, Integer> namedBindingMap = new HashMap<>();
    public static final String GLFW_KEY_PREFIX = "GLFW_KEY_";

    public
    BindRepository() {
        try {
            for (final Field field : GLFW.class.getDeclaredFields()) {
                if (field.getName().startsWith(GLFW_KEY_PREFIX)) {
                    namedBindingMap.put(field.getName().substring(GLFW_KEY_PREFIX.length()), field.getInt(null));
                }
            }
            for (int i = 0; i < 10; i++) {
                namedBindingMap.put("MOUSE_" + i, i);
            }
            namedBindingMap.put("CLEAR", -1);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        EventDispatcher.subscribe(this);
    }

    public Map<String, Integer> getNamedBindingMap() {
        return namedBindingMap;
    }

    public String getNameFromInteger(final int bind) {
        return namedBindingMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == bind)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public BindingService getBindingService() {
        return bindingService;
    }

    @Subscribe
    public void onKeyPress(final KeyPressEvent event) {
        bindingService.dispatch(event.getInteractionCode(), InputType.KEYBOARD);
    }

    @Subscribe
    public void onMousePress(final MousePressEvent event) {
        bindingService.dispatch(event.getInteractionCode(), InputType.MOUSE);
    }

}
