package wtf.oraculus.client.feature.module.impl.visual.esp;

record NameTagIcon(String unicode, NameTagIconPosition position, float horizontalOffset) {
    NameTagIcon(String unicode) {
        this(unicode, NameTagIconPosition.RIGHT, 0.5F);
    }

    NameTagIcon(String unicode, float horizontalOffset) {
        this(unicode, NameTagIconPosition.RIGHT, horizontalOffset);
    }

    NameTagIcon(String unicode, NameTagIconPosition position) {
        this(unicode, position, 0.5F);
    }
}
