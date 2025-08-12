package mindustrytool.handler;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.thread.ExecutorThreadPool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.struct.Seq;
import arc.util.CommandHandler.Command;
import arc.util.CommandHandler.ResponseType;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.core.GameState.State;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.net.Administration.PlayerInfo;
import mindustrytool.Config;
import mindustrytool.ServerController;
import mindustrytool.type.PlayerInfoDto;
import mindustrytool.type.CommandParamDto;
import mindustrytool.type.WorkflowContext;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.ModDto;
import mindustrytool.type.ModMetaDto;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerCommandDto;
import mindustrytool.type.StartServerDto;
import mindustrytool.type.StatsDto;
import mindustrytool.type.TeamDto;
import mindustrytool.utils.Utils;
import mindustrytool.workflow.errors.WorkflowError;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.RouteOverviewPlugin;

public class HttpServer {
    private static final String MAP_PREVIEW_FILE_NAME = "MapPreview";

    private Javalin app;

    private final WeakReference<ServerController> context;

    public class RequestInfo {
        public final String method;
        public final String path;
        public final String ip;
        public final long timestamp;

        public RequestInfo(String method, String path, String ip, long timestamp) {
            this.method = method;
            this.path = path;
            this.ip = ip;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, RequestInfo> activeRequests = new ConcurrentHashMap<>();

    public HttpServer(WeakReference<ServerController> context) {
        this.context = context;

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper//

                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)//
                        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            }));

            config.http.asyncTimeout = 5_000;
            config.useVirtualThreads = true;

            var pool = new ExecutorThreadPool(50, 0);
            pool.setName("HttpServer");
            config.jetty.threadPool = pool;
            config.jetty.modifyServer(server -> server.setStopTimeout(5_000)); // wait 5 seconds for existing requests

            config.registerPlugin(new RouteOverviewPlugin());

            config.requestLogger.http((ctx, ms) -> {
                if (!ctx.fullUrl().contains("stats") && !ctx.fullUrl().contains("hosting")) {
                    Log.info("[" + ctx.method().name() + "] " + Math.round(ms) + "ms " + ctx.fullUrl());
                }
            });
        });

        app.before(ctx -> {
            String reqId = UUID.randomUUID().toString();
            ctx.attribute("reqId", reqId);
            activeRequests.put(reqId, new RequestInfo(
                    ctx.method().name(), ctx.path(), ctx.ip(), System.currentTimeMillis()));
        });

        // Remove when request finishes
        app.after(ctx -> {
            String reqId = ctx.attribute("reqId");
            if (reqId != null) {
                activeRequests.remove(reqId);
            }
        });

        Log.info("Http server created: " + this);
    }

    public void init() {
        Log.info("Setup http server");

        app.get("stats", ctx -> {
            var stats = Utils.appPostWithTimeout(this::getStats);
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.json(stats);
        });

        app.get("image", ctx -> {
            var mapPreview = Utils.appPostWithTimeout(this::mapPreview);
            ctx.contentType(ContentType.IMAGE_PNG).result(mapPreview);
        });

        app.get("ok", (ctx) -> {
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.json("Ok");
        });

        app.get("plugin-version", ctx -> {
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.json(Config.PLUGIN_VERSION);
        });

        app.get("hosting", (ctx) -> {
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.json(Vars.state.isGame());
        });

        app.post("discord", ctx -> {
            String message = ctx.body();

            Call.sendMessage(message);
            ctx.result();
        });

        app.post("pause", ctx -> {
            if (Vars.state.isPaused()) {
                Vars.state.set(State.playing);
            } else if (Vars.state.isPlaying()) {
                Vars.state.set(State.paused);
            }
            ctx.json(Vars.state.isPaused());
        });

        app.post("host", ctx -> {
            Utils.appPostWithTimeout(() -> {
                StartServerDto request = ctx.bodyAsClass(StartServerDto.class);
                host(request);
                ctx.result();
            }, 50_000);
        });

        app.post("set-player", ctx -> {
            MindustryPlayerDto request = ctx.bodyAsClass(MindustryPlayerDto.class);

            String uuid = request.getUuid();
            boolean isAdmin = request.isAdmin();

            PlayerInfo target = Vars.netServer.admins.getInfoOptional(uuid);
            Player player = Groups.player.find(p -> p.getInfo() == target);

            if (target != null) {
                if (isAdmin) {
                    Vars.netServer.admins.adminPlayer(target.id,
                            player == null ? target.adminUsid : player.usid());
                } else {
                    Vars.netServer.admins.unAdminPlayer(target.id);
                }
                if (player != null)
                    player.admin = isAdmin;
            } else {
                Log.err("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
            }

            if (player != null) {
                context.get().hudHandler.closeFollowDisplay(player, HudHandler.LOGIN_UI);
                context.get().eventHandler.setPlayerData(request, player);
            }
            ctx.result();

        });

        app.get("players", ctx -> {

            var players = new ArrayList<Player>();
            Groups.player.forEach(players::add);

            var result = Utils
                    .appPostWithTimeout(() -> (players.stream()//
                            .map(player -> new PlayerDto()//
                                    .setName(player.coloredName())//
                                    .setUuid(player.uuid())//
                                    .setIp(player.ip())
                                    .setLocale(player.locale())//
                                    .setAdmin(player.admin)//
                                    .setJoinedAt(context.get().sessionHandler.contains(player) //
                                            ? context.get().sessionHandler.get(player).joinedAt
                                            : Instant.now().toEpochMilli())
                                    .setTeam(new TeamDto()//
                                            .setColor(player.team().color.toString())//
                                            .setName(player.team().name)))
                            .collect(Collectors.toList())));

            ctx.json(result);
        });

        app.get("player-infos", ctx -> {
            var pageString = ctx.queryParam("page");
            var sizeString = ctx.queryParam("size");
            var isBannedString = ctx.queryParam("banned");
            var filter = ctx.queryParam("filter");

            int page = pageString != null ? Integer.parseInt(pageString) : 0;
            int size = sizeString != null ? Integer.parseInt(sizeString) : 10;
            Boolean isBanned = isBannedString != null ? Boolean.parseBoolean(isBannedString) : null;

            int offset = page * size;

            List<Predicate<PlayerInfo>> conditions = new ArrayList<>();

            if (filter != null) {
                conditions.add(info -> //
                info.names.contains(name -> name.contains(filter))
                        || info.ips.contains(ip -> ip.contains(filter)));
            }

            if (isBanned != null) {
                conditions.add(info -> info.banned == isBanned);
            }

            var result = Utils.appPostWithTimeout(() -> {
                Seq<PlayerInfo> bans = Vars.netServer.admins.playerInfo.values().toSeq();

                return bans.list()//
                        .stream()//
                        .filter(info -> conditions.stream().allMatch(condition -> condition.test(info)))//
                        .skip(offset)//
                        .limit(size)//
                        .map(ban -> new PlayerInfoDto()
                                .setId(ban.id)
                                .setLastName(ban.lastName)
                                .setLastIP(ban.lastIP)
                                .setIps(ban.ips.list())
                                .setNames(ban.names.list())
                                .setAdminUsid(ban.adminUsid)
                                .setTimesKicked(ban.timesKicked)
                                .setTimesJoined(ban.timesJoined)
                                .setBanned(ban.banned)
                                .setAdmin(ban.admin)
                                .setLastKicked(ban.lastKicked))
                        .collect(Collectors.toList());
            });

            ctx.json(result);
        });

        app.get("kicks", ctx -> {

            var result = Utils.appPostWithTimeout(() -> {
                var res = new HashMap<>();
                for (var entry : Vars.netServer.admins.kickedIPs.entries()) {
                    if (entry.value != 0 && Time.millis() - entry.value < 0) {
                        res.put(entry.key, entry.value);
                    }
                }
                return res;
            });

            ctx.json(result);
        });

        app.get("commands", ctx -> {
            var commands = context.get().serverCommandHandler.getHandler() == null
                    ? Arrays.asList()
                    : context.get().serverCommandHandler.getHandler()//
                            .getCommandList()
                            .map(command -> new ServerCommandDto()
                                    .setText(command.text)
                                    .setDescription(command.description)
                                    .setParamText(command.paramText)
                                    .setParams(new Seq<>(command.params)
                                            .map(param -> new CommandParamDto()//
                                                    .setName(param.name)//
                                                    .setOptional(param.optional)
                                                    .setVariadic(param.variadic))//
                                            .list()))
                            .list();

            ctx.json(commands);
        });

        app.post("commands", ctx -> {
            String[] commands = ctx.bodyAsClass(String[].class);

            if (commands != null) {
                for (var command : commands) {
                    Log.info("Execute command: " + command);

                    context.get().serverCommandHandler.execute(command, response -> {
                        if (response.type == ResponseType.unknownCommand) {

                            int minDst = 0;
                            Command closest = null;

                            for (Command cmd : context.get().serverCommandHandler.getHandler().getCommandList()) {
                                int dst = Strings.levenshtein(cmd.text, response.runCommand);

                                if (dst < 3 && (closest == null || dst < minDst)) {
                                    minDst = dst;
                                    closest = cmd;
                                }
                            }

                            if (closest != null && !closest.text.equals("yes")) {
                                Log.err("Command not found. Did you mean \"" + closest.text + "\"?");
                            } else {
                                Log.err("Invalid command. Type 'help' for help.");
                            }
                        } else if (response.type == ResponseType.fewArguments) {
                            Log.err("Too few command arguments. Usage: " + response.command.text + " "
                                    + response.command.paramText);
                        } else if (response.type == ResponseType.manyArguments) {
                            Log.err("Too many command arguments. Usage: " + response.command.text + " "
                                    + response.command.paramText);
                        }
                    });
                }
            }
            ctx.result();
        });

        app.post("say", ctx -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting. Host a game first.");
            } else {
                String message = ctx.body();
                Call.sendMessage("[]" + message);
            }

            ctx.result();
        });

        app.get("workflow/nodes", ctx -> {
            ctx.json(context.get().workflow.getNodeTypes());
        });

        app.get("workflow/nodes/{id}/autocomplete", ctx -> {
            var id = ctx.pathParam("id");
            var input = ctx.queryParam("input");
            var node = context.get().workflow.getNodes().get(id);

            if (node == null) {
                ctx.status(404);
                ctx.result();
                return;
            }

            ctx.json(node.autocomplete(input.trim()));
        });

        app.get("workflow/version", ctx -> {
            var data = context.get().workflow.readWorkflowData();
            if (data == null || data.get("createdAt") == null) {
                ctx.json(0L);
            } else {
                ctx.json(data.get("createdAt").asLong());
            }
        });

        app.get("workflow", ctx -> {
            ctx.json(context.get().workflow.readWorkflowData());
        });

        app.post("workflow", ctx -> {
            var payload = ctx.bodyAsClass(JsonNode.class);
            context.get().workflow.writeWorkflowData(payload);
        });

        app.post("workflow/load", ctx -> {
            var payload = ctx.bodyAsClass(WorkflowContext.class);
            try {
                context.get().workflow.load(payload);
                ctx.json(context.get().workflow.getWorkflowContext());
            } catch (WorkflowError e) {
                Log.err("Failed to load workflow", e);
                HashMap<String, String> result = new HashMap<>();
                result.put("message", "Failed to load workflow: " + e.getMessage());
                ctx.status(400).json(result);
            }
        });

        app.get("json", ctx -> {
            var res = Utils.appPostWithTimeout(() -> {

                var data = new HashMap<String, Object>();

                data.put("stats", getStats());
                data.put("session", context.get().sessionHandler.get());
                data.put("hud", context.get().hudHandler.menus.asMap());
                data.put("buildLogs", context.get().apiGateway.buildLogs);
                data.put("isHub", Config.IS_HUB);
                data.put("ip", Config.SERVER_IP);
                data.put("units", Groups.unit.size());
                data.put("enemies", Vars.state.enemies);
                data.put("tps", Core.graphics.getFramesPerSecond());

                var gameStats = new HashMap<String, Object>();

                gameStats.put("buildingsBuilt", Vars.state.stats.buildingsBuilt);
                gameStats.put("buildingsDeconstructed", Vars.state.stats.buildingsDeconstructed);
                gameStats.put("buildingsDestroyed", Vars.state.stats.buildingsDestroyed);
                gameStats.put("coreItemCount", Vars.state.stats.coreItemCount);
                gameStats.put("enemyUnitsDestroyed", Vars.state.stats.enemyUnitsDestroyed);
                gameStats.put("placedBlockCount", Vars.state.stats.placedBlockCount);
                gameStats.put("unitsCreated", Vars.state.stats.unitsCreated);
                gameStats.put("wavesLasted", Vars.state.stats.wavesLasted);

                HashMap<String, String> executors = new HashMap<>();
                executors.put("backgroundExecutor", context.get().BACKGROUND_TASK_EXECUTOR.toString());
                executors.put("backgroundScheduler", context.get().BACKGROUND_SCHEDULER.toString());

                data.put("executors", executors);

                data.put("gameStats", gameStats);
                data.put("locales", Vars.locales);
                data.put("threads",
                        Thread.getAllStackTraces().keySet().stream()
                                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                                .map(thread -> {
                                    HashMap<String, Object> info = new HashMap<>();

                                    info.put("id", thread.getId());
                                    info.put("name", thread.getName());
                                    info.put("state", thread.getState().name());
                                    info.put("group", thread.getThreadGroup() == null ? "null"
                                            : thread.getThreadGroup().getName());
                                    info.put("stacktrace", Arrays.asList(thread.getStackTrace()).stream()
                                            .map(stack -> stack.toString()).collect(Collectors.toList()));

                                    return info;
                                })
                                .collect(Collectors.toList()));

                data.put("activeRequest", activeRequests.values());

                var maps = new ArrayList<HashMap<String, String>>();
                Vars.maps.all().forEach(map -> {
                    var tags = new HashMap<String, String>();
                    map.tags.each((key, value) -> tags.put(key, value));
                    maps.add(tags);
                });
                data.put("maps",
                        Vars.maps.all().map(map -> {
                            HashMap<String, Object> info = new HashMap<>();
                            info.put("name", map.name()); //
                            info.put("author", map.author()); //
                            info.put("file", map.file.absolutePath());
                            info.put("tags", map.tags);
                            info.put("description", map.description());
                            info.put("width", map.width);
                            info.put("height", map.height);

                            return info;
                        }).list());
                data.put("mods", Vars.mods.list().map(mod -> mod.meta.toString()).list());
                data.put("votes", context.get().voteHandler.votes);

                var settings = new HashMap<String, Object>();

                Core.settings.keys().forEach(key -> {
                    settings.put(key, Core.settings.get(key, null));
                });

                data.put("settings", settings);

                return data;
            });

            ctx.json(res);
        });

        app.sse("workflow/events", client ->

        {
            client.keepAlive();
            client.sendComment("connected");

            client.onClose(() -> {
                context.get().workflow.getWorkflowEventConsumers().remove(client);
            });

            context.get().workflow.getWorkflowEventConsumers().add(client);
        });

        app.exception(TimeoutException.class, (exception, ctx) -> {
            Log.warn("Timeout exception", exception);
            HashMap<String, Object> result = new HashMap<>();
            result.put("message", exception.getMessage() == null ? "Unknown error" : exception.getMessage());
            ctx.status(400).json(result);
        });

        app.exception(Exception.class, (exception, ctx) -> {
            Log.err("Unhandled api exception", exception);

            try {

                HashMap<String, Object> result = new HashMap<>();
                result.put("message", exception.getMessage() == null ? "Unknown error" : exception.getMessage());
                ctx.status(500).json(result);
            } catch (Exception e) {
                Log.err("Failed to create error response", e);
                ctx.status(500).json("Failed to create error response");
            }
        });

        app.exception(Exception.class, (exception, ctx) -> {
            Log.err("Unhandled api exception", exception);

            try {

                HashMap<String, Object> result = new HashMap<>();
                result.put("message", exception.getMessage() == null ? "Unknown error" : exception.getMessage());
                ctx.status(500).json(result);
            } catch (Exception e) {
                Log.err("Failed to create error response", e);
                ctx.status(500).json("Failed to create error response");
            }
        });

        if (!ServerController.isUnloaded) {
            app.start(9999);
        }
        Log.info("Setup http server done");
    }

    private synchronized void host(StartServerDto request) {
        if (Vars.state.isGame()) {
            Log.info("Already hosting. Type 'stop' to stop hosting first.");
            return;
        }

        String mapName = request.getMapName();
        String gameMode = request.getMode();
        String commands = request.getHostCommand();

        if (commands != null && !commands.trim().isEmpty()) {
            String[] commandsArray = commands.split("\n");
            for (var command : commandsArray) {
                Log.info("Host command: " + command);
                context.get().serverCommandHandler.execute(command, (_ignore) -> {
                });
            }
            return;
        }

        Utils.host(mapName, gameMode);
    }

    private StatsDto getStats() {
        var map = Vars.state.map;
        String mapName = map != null ? map.name() : "";
        List<ModDto> mods = Vars.mods == null //
                ? Arrays.asList()
                : Vars.mods.list().map(mod -> new ModDto()//
                        .setFilename(mod.file.name())//
                        .setName(mod.name)
                        .setMeta(new ModMetaDto()//
                                .setAuthor(mod.meta.author)//
                                .setDependencies(mod.meta.dependencies.list())
                                .setDescription(mod.meta.description)
                                .setDisplayName(mod.meta.displayName)
                                .setHidden(mod.meta.hidden)
                                .setInternalName(mod.meta.internalName)
                                .setJava(mod.meta.java)
                                .setMain(mod.meta.main)
                                .setMinGameVersion(mod.meta.minGameVersion)
                                .setName(mod.meta.name)
                                .setRepo(mod.meta.repo)
                                .setSubtitle(mod.meta.subtitle)
                                .setVersion(mod.meta.version)))
                        .list();

        int players = Groups.player.size();

        return new StatsDto()//
                .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024)//
                .setPlayers(players)//
                .setMapName(mapName)
                .setMods(mods)//
                .setTps(Core.graphics.getFramesPerSecond())//
                .setHosting(Vars.state.isGame())
                .setPaused(Vars.state.isPaused())//
                .setVersion("V" + Version.number + "Build" + Version.build)
                .setKicks(Vars.netServer.admins.kickedIPs.values().toSeq()
                        .select(value -> Time.millis() - value < 0).size)//
                .setStatus(Vars.state.isGame() ? "HOST" : "UP")
                .setStartedAt(Core.settings.getLong("startedAt", System.currentTimeMillis()));
    }

    public byte[] mapPreview() {
        Pixmap pix = null;
        try {
            if (Vars.state.map != null) {
                pix = MapIO.generatePreview(Vars.world.tiles);
                Fi file = Vars.dataDirectory.child(MAP_PREVIEW_FILE_NAME);
                file.writePng(pix);
                pix.dispose();

                return file.readBytes();
            }

            return new byte[] {};
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[] {};
        } finally {
            if (pix != null) {
                pix.dispose();
            }
        }
    }

    public void unload() {
        app.stop();
        app = null;

        Log.info("Stop http server");
    }
}
