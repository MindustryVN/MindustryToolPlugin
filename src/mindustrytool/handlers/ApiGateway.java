package mindustrytool.handlers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import arc.util.Strings;
import mindustrytool.utils.JsonUtils;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;

public class ApiGateway {

    private final HttpClient httpClient = HttpClient.newBuilder()//
            .connectTimeout(Duration.ofSeconds(20))//
            .build();

    private Builder setHeaders(Builder builder) {
        return builder.header("X-SERVER-ID", MindustryToolPlugin.SERVER_ID.toString());
    }

    private URI path(String... path) {
        return URI.create("http://server-manager:8088/internal-api/v1/" + Strings.join("/", path));
    }

    public SetPlayerMessageRequest setPlayer(PlayerMessageRequest payload) {
        var request = setHeaders(HttpRequest.newBuilder(path("players")))//
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(payload)))//
                .build();

        try {
            var result = httpClient.send(request, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, SetPlayerMessageRequest.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public int getTotalPlayer() {
        var request = setHeaders(HttpRequest.newBuilder(path("total-player")))//
                .GET()//
                .build();

        try {
            var result = httpClient.send(request, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, Integer.class);
        } catch (Exception e) {
            return 0;
        }

    }

    public void sendChatMessage(String chat) {
        var request = setHeaders(HttpRequest.newBuilder(path("chat")))//
                .POST(HttpRequest.BodyPublishers.ofString(chat))//
                .build();

        httpClient.sendAsync(request, BodyHandlers.ofString());
    }

    public void sendConsoleMessage(String chat) {
        var request = setHeaders(HttpRequest.newBuilder(path("console")))//
                .POST(HttpRequest.BodyPublishers.ofString(chat))//
                .build();

        httpClient.sendAsync(request, BodyHandlers.ofString());
    }

    public void onPlayerLeave(PlayerMessageRequest request) {
        var req = setHeaders(HttpRequest.newBuilder(path("player-leave")))//
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(request)))//
                .build();

        httpClient.sendAsync(req, BodyHandlers.ofString());
    }

    public int host(String targetServerId) {
        var request = setHeaders(HttpRequest.newBuilder(path("start-server")))//
                .POST(HttpRequest.BodyPublishers.ofString(targetServerId))//
                .build();

        try {
            var result = httpClient.send(request, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, Integer.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public GetServersMessageResponse getServers(GetServersMessageRequest request) {
        var req = setHeaders(HttpRequest.newBuilder(path("servers?page=%s&size=%s".formatted(request.getPage(), request.getSize()))))//
                .GET()//
                .build();

        try {
            var result = httpClient.send(req, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, GetServersMessageResponse.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
