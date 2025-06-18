package mindustrytool.handler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import mindustry.maps.Map;
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
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Utils;
import mindustrytool.workflow.errors.WorkflowError;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.RouteOverviewPlugin;

public class HttpServer {
    private static final String MAP_PREVIEW_FILE_NAME = "MapPreview";

    private Javalin app;

    private final ServerController controller;

    public HttpServer(ServerController controller) {
        this.controller = controller;
        Log.info("Http server created");
    }

    public void init() {
        Log.info("Setup http server");

        if (app != null) {
            throw new RuntimeException("Already init");
        }

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper//

                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)//
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            }));

            int maxThreads = 20;
            int minThreads = 1;
            int idleTimeoutMillis = 10000;

            config.useVirtualThreads = true;
            config.jetty.threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeoutMillis);

            config.registerPlugin(new RouteOverviewPlugin());

            config.requestLogger.http((context, ms) -> {
                Log.debug("[" + context.method().name() + "] " + Math.round(ms) + "ms " + context.fullUrl());
            });
        });

        app.get("stats", context -> {
            context.future(() -> {
                var future = new CompletableFuture<StatsDto>();

                Core.app.post(() -> {
                    var stats = getStats();
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(stats);
                    future.complete(stats);
                });

                return future;
            });
        });

        app.get("image", context -> {
            context.future(() -> {
                var future = new CompletableFuture<byte[]>();

                Core.app.post(() -> {
                    var mapPreview = mapPreview();
                    context.contentType(ContentType.IMAGE_PNG).result(mapPreview);
                    future.complete(mapPreview);
                });

                return future;
            });
        });

        app.get("ok", (context) -> {
            context.contentType(ContentType.APPLICATION_JSON);
            context.json("Ok");
        });

        app.get("plugin-version", context -> {
            context.contentType(ContentType.APPLICATION_JSON);
            context.json(Config.PLUGIN_VERSION);
        });

        app.get("hosting", (context) -> {
            context.contentType(ContentType.APPLICATION_JSON);
            context.json(Vars.state.isGame());
        });

        app.post("discord", context -> {
            String message = context.body();

            Call.sendMessage(message);
            context.result();
        });

        app.post("pause", context -> {
            if (Vars.state.isPaused()) {
                Vars.state.set(State.playing);
            } else if (Vars.state.isPlaying()) {
                Vars.state.set(State.paused);
            }
            context.json(Vars.state.isPaused());
        });

        app.post("host", context -> {
            StartServerDto request = context.bodyAsClass(StartServerDto.class);
            host(request);
            context.result();
        });

        app.post("set-player", context -> {
            MindustryPlayerDto request = context.bodyAsClass(MindustryPlayerDto.class);

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
                HudUtils.closeFollowDisplay(player, HudUtils.LOGIN_UI);
                controller.eventHandler.setPlayerData(request, player);
            }
            context.result();

        });

        app.get("players", context -> {
            context.future(() -> {
                var future = new CompletableFuture<List<PlayerDto>>();

                Core.app.post(() -> {
                    var players = new ArrayList<Player>();
                    Groups.player.forEach(players::add);

                    var result = (players.stream()//
                            .map(player -> new PlayerDto()//
                                    .setName(player.coloredName())//
                                    .setUuid(player.uuid())//
                                    .setIp(player.ip())
                                    .setLocale(player.locale())//
                                    .setAdmin(player.admin)//
                                    .setJoinedAt(controller.sessionHandler.contains(player) //
                                            ? controller.sessionHandler.get(player).joinedAt
                                            : Instant.now().toEpochMilli())
                                    .setTeam(new TeamDto()//
                                            .setColor(player.team().color.toString())//
                                            .setName(player.team().name)))
                            .collect(Collectors.toList()));
                    context.json(result);
                    future.complete(result);
                });
                return future;
            });
        });

        app.get("player-infos", context -> {
            var pageString = context.queryParam("page");
            var sizeString = context.queryParam("size");
            var isBannedString = context.queryParam("banned");
            var filter = context.queryParam("filter");

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

            context.future(() -> {
                var future = new CompletableFuture<List<PlayerInfoDto>>();

                Core.app.post(() -> {

                    Seq<PlayerInfo> bans = Vars.netServer.admins.playerInfo.values().toSeq();

                    var result = bans.list()//
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
                            .toList();

                    context.json(result);
                    future.complete(result);
                });
                return future;
            });
        });

        app.get("kicks", context -> {
            context.future(() -> {
                var future = new CompletableFuture<HashMap<Object, Object>>();

                Core.app.post(() -> {
                    var result = new HashMap<>();
                    for (var entry : Vars.netServer.admins.kickedIPs.entries()) {
                        if (entry.value != 0 && Time.millis() - entry.value < 0) {
                            result.put(entry.key, entry.value);
                        }
                    }

                    context.json(result);
                    future.complete(result);
                });

                return future;
            });
        });

        app.get("commands", context -> {
            var commands = controller.serverCommandHandler.getHandler() == null
                    ? List.of()
                    : controller.serverCommandHandler.getHandler()//
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

            context.json(commands);

        });

        app.post("commands", context -> {
            String[] commands = context.bodyAsClass(String[].class);

            if (commands != null) {
                for (var command : commands) {
                    Log.info("Execute command: " + command);

                    controller.serverCommandHandler.execute(command, response -> {
                        if (response.type == ResponseType.unknownCommand) {

                            int minDst = 0;
                            Command closest = null;

                            for (Command cmd : controller.serverCommandHandler.getHandler().getCommandList()) {
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
            context.result();
        });

        app.post("say", context -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting. Host a game first.");
                return;
            }

            String message = context.body();
            Call.sendMessage("[]" + message);

            context.result();
        });

        app.get("workflow/nodes", context -> {
            context.json(controller.workflow.getNodeTypes());
        });

        app.post("workflow", context -> {
            var payload = context.bodyAsClass(WorkflowContext.class);
            try {
                controller.workflow.load(payload);
            } catch (WorkflowError e) {
                Log.err("Failed to load workflow", e);
                context.status(400).result("Failed to load workflow: " + e.getMessage());
                return;
            }
            context.json(controller.workflow.getContext());
        });

        app.get("json", context -> {
            context.future(() -> {
                var future = new CompletableFuture<HashMap<String, Object>>();

                Core.app.post(() -> {

                    var data = new HashMap<String, Object>();

                    data.put("stats", getStats());
                    data.put("session", controller.sessionHandler.get());
                    data.put("hud", HudUtils.menus.asMap());
                    data.put("buildLogs", controller.apiGateway.buildLogs);
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

                    data.put("executors", java.util.Map.of(
                            "backgroundExecutor", Config.BACKGROUND_TASK_EXECUTOR.toString(), //
                            "backgroundScheduler", Config.BACKGROUND_SCHEDULER.toString()//
                    ));

                    data.put("gameStats", gameStats);
                    data.put("locales", Vars.locales);

                    var maps = new ArrayList<HashMap<String, String>>();
                    Vars.maps.all().forEach(map -> {
                        var tags = new HashMap<String, String>();
                        map.tags.each((key, value) -> tags.put(key, value));
                        maps.add(tags);
                    });
                    data.put("maps",
                            Vars.maps.all().map(map -> java.util.Map.of(
                                    "name", map.name(), //
                                    "author", map.author(), //
                                    "file", map.file.absolutePath(),
                                    "tags", map.tags,
                                    "description", map.description(),
                                    "width", map.width,
                                    "height", map.height)).list());
                    data.put("mods", Vars.mods.list().map(mod -> mod.meta.toString()).list());
                    data.put("votes", controller.voteHandler.votes);

                    var settings = new HashMap<String, Object>();

                    Core.settings.keys().forEach(key -> {
                        settings.put(key, Core.settings.get(key, null));
                    });

                    data.put("settings", settings);

                    context.json(data);
                    future.complete(data);
                });

                return future;
            });
        });

        app.exception(Exception.class, (exception, context) -> {
            Log.err(exception);

            var result = java.util.Map.of("message", exception.getMessage());

            context.status(500)
                    .json(result);
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

        if (commands != null && !commands.isBlank()) {
            String[] commandsArray = commands.split("\n");
            for (var command : commandsArray) {
                Log.info("Host command: " + command);
                controller.serverCommandHandler.execute(command, (_ignore) -> {
                });
            }
            return;
        }

        Utils.host(mapName, gameMode);
    }

    private StatsDto getStats() {
        Map map = Vars.state.map;
        String mapName = map != null ? map.name() : "";
        List<ModDto> mods = Vars.mods == null //
                ? List.of()
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
                .setStatus(Vars.state.isGame() ? "HOST" : "UP");
    }

    public byte[] mapPreview() {
        Pixmap pix = null;
        try {
            Map map = Vars.state.map;

            if (map != null) {
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
        if (app != null) {
            app.stop();
            Log.info("Stop http server");
        }
    }
}
