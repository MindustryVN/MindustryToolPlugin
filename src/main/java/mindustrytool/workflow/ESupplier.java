package mindustrytool.workflow;

@FunctionalInterface
public interface ESupplier<T> {

    T get() throws Exception;

}
