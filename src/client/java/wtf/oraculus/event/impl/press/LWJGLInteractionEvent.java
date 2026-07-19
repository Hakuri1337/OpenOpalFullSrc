package wtf.oraculus.event.impl.press;

class LWJGLInteractionEvent {

    private final int interactionCode;

    protected LWJGLInteractionEvent(final int interactionCode) {
        this.interactionCode = interactionCode;
    }

    public int getInteractionCode() {
        return interactionCode;
    }
}
