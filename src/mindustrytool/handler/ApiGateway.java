package mindustrytool.handler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import arc.util.Log;
import arc.util.Strings;
import mindustrytool.utils.JsonUtils;
import mindustrytool.Config;
import mindustrytool.ServerController;
import mindustrytool.type.BuildLogDto;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.PaginationRequest;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerDto;

public class ApiGateway {

    final ServerController controller;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public Cache<PaginationRequest, ServerDto> serverQueryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(100)
            .build();

    public ApiGateway(ServerController controller) {
        this.controller = controller;
        Log.info("Api gateway handler created");
    }

    private final HttpClient httpClient = HttpClient.newBuilder()//
            .connectTimeout(Duration.ofSeconds(2))//
            .executor(Config.BACKGROUND_TASK_EXECUTOR)
            .build();

    public final BlockingQueue<BuildLogDto> buildLogs = new LinkedBlockingQueue<>(1000);

    public void init() {
        Log.info("Setup api gateway");

        Config.BACKGROUND_SCHEDULER.scheduleWithFixedDelay(() -> {
            if (buildLogs.size() > 0) {

                var logs = new ArrayList<>(buildLogs);

                buildLogs.clear();

                var request = setHeaders(HttpRequest.newBuilder(path("build-log")))//
                        .header("Content-Type", "application/json")//
                        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(logs)))//
                        .build();

                try {
                    httpClient.send(request, BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }, 0, 10, TimeUnit.SECONDS);

        Log.info("Setup api gateway done");

    }

    private Builder setHeaders(Builder builder) {
        return builder
                .header("X-SERVER-ID", ServerController.SERVER_ID.toString())
                .timeout(Duration.ofSeconds(2));
    }

    private URI path(String... path) {
        var uri = URI.create("http://server-manager:8088/internal-api/v1/" + Strings.join("/", path));
        Log.debug("[REQUEST]: " + uri.toString());

        return uri;
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
            e.printStackTrace();
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

    public void sendBuildLog(BuildLogDto buildLog) {
        if (!buildLogs.offer(buildLog)) {
            Log.warn("Build log queue is full. Dropping log.");
        }
    }

    public String host(String targetServerId) {
        Object lock = locks.computeIfAbsent(targetServerId, k -> new Object());

        synchronized (lock) {
            var request = setHeaders(HttpRequest.newBuilder(path("host")))//
                    .header("Content-Type", "text/plain")//
                    .POST(HttpRequest.BodyPublishers.ofString(targetServerId))//
                    .timeout(Duration.ofSeconds(45))
                    .build();

            try {
                return httpClient.send(request, BodyHandlers.ofString()).body();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized ServerDto getServers(PaginationRequest request) {
        return serverQueryCache.get(request, _ignore -> {
            var req = setHeaders(
                    HttpRequest.newBuilder(
                            path("servers?page=%s&size=%s".formatted(request.getPage(), request.getSize()))))//
                    .GET()//
                    .build();

            try {
                var result = httpClient.send(req, BodyHandlers.ofString()).body();
                return JsonUtils.readJsonAsClass(result, ServerDto.class);
            } catch (Exception e) {
                e.printStackTrace();
                return new ServerDto();
            }
        });
    }

    public String translate(String text, String targetLanguage) {
        var req = setHeaders(HttpRequest.newBuilder(path("translate/%s".formatted(targetLanguage))))//
                .POST(HttpRequest.BodyPublishers.ofString(text))//
                .timeout(Duration.ofSeconds(10))
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
