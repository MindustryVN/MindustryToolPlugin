package mindustrytool.handler;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import arc.util.Http;
import arc.util.Http.HttpRequest;
import arc.util.Http.HttpResponse;
import arc.util.Log;
import arc.util.Strings;
import mindustrytool.utils.JsonUtils;
import mindustrytool.ServerController;
import mindustrytool.type.BuildLogDto;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.PaginationRequest;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerDto;

public class ApiGateway {

    private final WeakReference<ServerController> context;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public Cache<PaginationRequest, ServerDto> serverQueryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(10)
            .build();

    public ApiGateway(WeakReference<ServerController> context) {
        this.context = context;

        Log.info("Api gateway handler created: " + this);
    }

    public final BlockingQueue<BuildLogDto> buildLogs = new LinkedBlockingQueue<>(100);

    public void init() {
        Log.info("Setup api gateway");

        context.get().BACKGROUND_SCHEDULER.scheduleWithFixedDelay(() -> {
            if (buildLogs.size() > 0) {
                try {

                    Log.debug("Sending build logs: " + buildLogs.size());

                    List logs = new ArrayList<>(buildLogs);

                    buildLogs.clear();

                    send((post("build-log"))
                            .header("Content-Type", "application/json")//
                            .content(JsonUtils.toJsonString(logs)));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

        }, 0, 1, TimeUnit.SECONDS);

        Log.info("Setup api gateway done");

    }

    public void unload() {
        serverQueryCache.invalidateAll();
        serverQueryCache = null;
    }

    private String uri(String... path) {
        URI uri = URI.create("http://server-manager:8088/internal-api/v1/" + Strings.join("/", path));
        Log.debug("[REQUEST]: " + uri);

        return uri.toString();
    }

    private HttpRequest get(String... path) {
        return Http.get(uri(path));
    }

    private HttpRequest post(String... path) {
        return Http.post(uri(path));
    }

    private HttpResponse send(HttpRequest req) {
        return send(req, 2);
    }

    private HttpResponse send(HttpRequest req, int timeout) {
        CompletableFuture<HttpResponse> res = new CompletableFuture<>();
        req
                .header("X-SERVER-ID", ServerController.SERVER_ID.toString())
                .timeout(timeout * 1000)
                .error(error -> res.completeExceptionally(new RuntimeException(req.url, error)))
                .submit(result -> {
                    if (result.getStatus().code >= 400) {
                        res.completeExceptionally(new RuntimeException(req.url + " " + result.getResultAsString()));
                    } else {
                        res.complete(result);
                    }
                });
        try {
            return res.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public MindustryPlayerDto setPlayer(PlayerDto payload) {
        HttpResponse response = send((post("players"))
                .header("Content-Type", "application/json")//
                .content(JsonUtils.toJsonString(payload)));

        try {
            String result = response.getResultAsString();

            if (result == null || result.isEmpty()) {
                throw new RuntimeException("Received empty response from server");
            }

            return JsonUtils.readJsonAsClass(result, MindustryPlayerDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPlayerLeave(PlayerDto payload) {
        try {
            send(post("players/leave")
                    .header("Content-Type", "application/json")//
                    .content(JsonUtils.toJsonString(payload)));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public int getTotalPlayer() {
        try {
            HttpResponse response = send(get("total-player"));
            String result = response.getResultAsString();
            return JsonUtils.readJsonAsClass(result, Integer.class);
        } catch (Exception e) {
            return 0;
        }

    }

    public void sendChatMessage(String chat) {
        try {
            send(post("chat")
                    .header("Content-Type", "application/json")//
                    .content(JsonUtils.toJsonString(chat)));
        } catch (Exception e) {
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
            try {
                return send(post("host")
                        .header("Content-Type", "text/plain")//
                        .content(targetServerId), 45)
                        .getResultAsString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                locks.remove(targetServerId);
            }
        }
    }

    public synchronized ServerDto getServers(PaginationRequest request) {
        return serverQueryCache.get(request, _ignore -> {
            try {
                HttpResponse response = send(
                        get(String.format("servers?page=%s&size=%s", request.getPage(), request.getSize())));
                String result = response.getResultAsString();
                return JsonUtils.readJsonAsClass(result, ServerDto.class);
            } catch (Exception e) {
                e.printStackTrace();
                return new ServerDto();
            }
        });
    }

    public String translate(String text, String targetLanguage) {
        try {
            HttpResponse response = send(post(String.format("translate/%s", targetLanguage))
                    .header("Content-Type", "text/plain")//
                    .content(text));

            String result = response.getResultAsString();

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
