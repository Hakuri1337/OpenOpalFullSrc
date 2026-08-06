package wtf.oraculus.client.feature.module;

public final class UnknownModuleException extends Exception {
    private final String id;

    public UnknownModuleException(final String id) {
        super(String.format("Module with the id %s could not be found.", id));
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
