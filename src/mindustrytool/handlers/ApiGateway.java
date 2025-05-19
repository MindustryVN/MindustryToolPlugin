package mindustrytool.handlers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import arc.util.Log;
import arc.util.Strings;
import mindustrytool.utils.JsonUtils;
import mindustrytool.Config;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.type.BuildLogDto;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.PaginationRequest;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerDto;

public class ApiGateway {

    private static final HttpClient httpClient = HttpClient.newBuilder()//
            .connectTimeout(Duration.ofSeconds(2))//
            .build();

    private final BlockingQueue<BuildLogDto> buildLogs = new LinkedBlockingQueue<>(1000);

    public void init() {
        System.out.println("Setup api gateway");

        Config.BACKGROUND_SCHEDULER.scheduleWithFixedDelay(() -> {
            if (buildLogs.size() > 0) {

                var logs = new ArrayList<>(buildLogs);

                buildLogs.clear();

                var request = setHeaders(HttpRequest.newBuilder(path("build-log")))//
                        .header("Content-Type", "application/json")//
                        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(logs)))//
                        .build();

                httpClient.sendAsync(request, BodyHandlers.ofString()).exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
            }

        }, 0, 10, TimeUnit.SECONDS);

        System.out.println("Setup api gateway done");

    }

    private Builder setHeaders(Builder builder) {
        return builder.header("X-SERVER-ID", MindustryToolPlugin.SERVER_ID.toString());
    }

    private URI path(String... path) {
        return URI.create("http://server-manager:8088/internal-api/v1/" + Strings.join("/", path));
    }

    public MindustryPlayerDto setPlayer(PlayerDto payload) {
        var request = setHeaders(HttpRequest.newBuilder(path("players")))//
                .header("Content-Type", "application/json")//
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(payload)))//
                .build();

        try {
            var result = httpClient.send(request, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, MindustryPlayerDto.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPlayerLeave(PlayerDto payload) {
        var request = setHeaders(HttpRequest.newBuilder(path("players/leave")))//
                .header("Content-Type", "application/json")//
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(payload)))//
                .build();

        try {
            httpClient.send(request, BodyHandlers.ofString());
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
                .header("Content-Type", "application/json")//
                .POST(HttpRequest.BodyPublishers.ofString(chat))//
                .build();

        try {
            httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendConsoleMessage(String chat) {
        var request = setHeaders(HttpRequest.newBuilder(path("console")))//
                .header("Content-Type", "application/json")//
                .POST(HttpRequest.BodyPublishers.ofString(chat))//
                .build();

        try {
            httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendBuildLog(BuildLogDto buildLog) {
        if (!buildLogs.offer(buildLog)) {
            Log.warn("Build log queue is full. Dropping log.");
        }
    }

    public String host(String targetServerId) {
        var request = setHeaders(HttpRequest.newBuilder(path("host")))//
                .header("Content-Type", "text/plain")//
                .POST(HttpRequest.BodyPublishers.ofString(targetServerId))//
                .build();

        try {
            return httpClient.send(request, BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ServerDto getServers(PaginationRequest request) {
        var req = setHeaders(
                HttpRequest.newBuilder(path("servers?page=%s&size=%s".formatted(request.getPage(), request.getSize()))))//
                .GET()//
                .build();

        try {
            var result = httpClient.send(req, BodyHandlers.ofString()).body();
            return JsonUtils.readJsonAsClass(result, ServerDto.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String translate(String text, String targetLanguage) {
        var req = setHeaders(HttpRequest.newBuilder(path("translate/%s".formatted(targetLanguage))))//
                .POST(HttpRequest.BodyPublishers.ofString(text))//
                .build();

        try {
            var result = httpClient.send(req, BodyHandlers.ofString());

            if (result.statusCode() != 200) {
                throw new RuntimeException("Can not translate: " + result.body());
            }

            return result.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
