package wtf.oraculus.event.impl.client;

import wtf.oraculus.client.feature.module.property.Property;

public record PropertyUpdateEvent(Property<?> property) {
}
