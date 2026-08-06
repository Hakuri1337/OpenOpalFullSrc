package wtf.oraculus.client.feature.module.impl.visual.esp;

record NameTagElement(NameTagIcon icon, String text, int color) {
    NameTagElement(NameTagIcon icon, int color) {
        this(icon, null, color);
    }

    NameTagElement(String text, int color) {
        this(null, text, color);
    }
}
