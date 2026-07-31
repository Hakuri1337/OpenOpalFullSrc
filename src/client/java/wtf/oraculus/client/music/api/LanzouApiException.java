package wtf.oraculus.client.music.api;

public final class LanzouApiException extends RuntimeException {
    public LanzouApiException(final String message) {
        super(message);
    }

    public LanzouApiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
