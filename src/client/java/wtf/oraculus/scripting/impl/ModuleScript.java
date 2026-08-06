package wtf.oraculus.scripting.impl;

import org.graalvm.polyglot.Value;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.HashMap;
import java.util.Map;

public class ModuleScript implements IEventSubscriber {

    private String name, description;
    private boolean enabled;

    private final Map<String, Value> handlerMap = new HashMap<>();

    public ModuleScript(final String name, final String description) {
        this.name = name;
        this.description = description;

        EventDispatcher.subscribe(this);
    }

    public final void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.onEnable();
        } else {
            this.onDisable();
        }
    }

    public final void on(final String event, final Value handler) {
        handlerMap.put(event, handler);
    }

    private void executeCallback(final String event, final Object... args) {
        Value handler = handlerMap.get(event);
        if (handler != null && handler.canExecute()) {
            handler.execute(args);
        }
    }

    @Override
    public final boolean isHandlingEvents() {
        return enabled;
    }

    // Callbacks
    private void onEnable() {
        this.executeCallback("enable");
    }

    private void onDisable() {
        this.executeCallback("disable");
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.executeCallback("preGameTick", event);
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        this.executeCallback("renderScreen", event);
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        this.executeCallback("preMovementPacket", event);
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        this.executeCallback("postMovementPacket", event);
    }

    @Subscribe
    public void onPreMove(final PreMoveEvent event) {
        this.executeCallback("preMove", event);
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        this.executeCallback("postMove", event);
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        this.executeCallback("sendPacket", event);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        this.executeCallback("receivePacket", event);
    }

    @Subscribe
    public void onInstantaneousSendPacket(final InstantaneousSendPacketEvent event) {
        this.executeCallback("instantaneousSendPacket", event);
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        this.executeCallback("instantaneousReceivePacket", event);
    }

}
