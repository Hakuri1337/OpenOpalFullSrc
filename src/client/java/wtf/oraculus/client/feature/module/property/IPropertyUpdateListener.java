package wtf.oraculus.client.feature.module.property;

public interface IPropertyUpdateListener<T> {
    void onChange(T prevValue, T value);
}
