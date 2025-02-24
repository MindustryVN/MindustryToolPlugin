package mindustrytool.type;

import java.util.function.Consumer;

import mindustrytool.utils.JsonUtils;

public class ServerMessageEvent<T> {
    private final String id;
    private final String method;
    private final Consumer<String> response;

    private final T payload;

    public ServerMessageEvent(String id, String method, T payload, Consumer<String> response) {
        this.id = id;
        this.method = method;
        this.payload = payload;
        this.response = response;
    }

    public String getMethod() {
        return method;
    }

    public T getPayload() {
        return payload;
    }

    public void response(Object data) {
        var message = new ServerExchange()//
                .response()//
                .setData(data)//
                .setMethod(method)//
                .setId(id);

        response.accept(JsonUtils.toJsonString(message));
    }

    public void error(Object data) {
        var message = new ServerExchange()//
                .error()//
                .setMethod(method)//
                .setData(data)//
                .setId(id);

        response.accept(JsonUtils.toJsonString(message));
    }
}
