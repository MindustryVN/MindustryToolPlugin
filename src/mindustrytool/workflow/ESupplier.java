package mindustrytool.workflow;

@FunctionalInterface
public interface ESupplier<T> {

    public T get() throws Exception;

}
