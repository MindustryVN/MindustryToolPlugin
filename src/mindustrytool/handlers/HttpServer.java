package mindustrytool.handlers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
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
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.ModDto;
import mindustrytool.type.ModDto.ModMetaDto;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerCommandDto;
import mindustrytool.type.StartServerDto;
import mindustrytool.type.StatsDto;
import mindustrytool.type.TeamDto;
import mindustrytool.type.ServerCommandDto.CommandParamDto;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Session;
import mindustrytool.utils.Utils;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.RouteOverviewPlugin;

public class HttpServer {
    private static final String MAP_PREVIEW_FILE_NAME = "MapPreview";

    private Javalin app;

    public void init() {
        System.out.println("Setup http server");
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper//

                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)//
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            }));

            config.registerPlugin(new RouteOverviewPlugin());
        });

        app.get("stats", context -> {
            context.contentType(ContentType.APPLICATION_JSON);
            context.json(getStats());
        });

        app.get("image", context -> {
            context.contentType(ContentType.IMAGE_PNG);
            context.result(mapPreview());
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
            context.contentType(ContentType.TEXT_PLAIN);
            context.result("Ok");
        });

        app.post("pause", context -> {
            if (Vars.state.isPaused()) {
                Vars.state.set(State.paused);
            } else {
                Vars.state.set(State.playing);
            }
        });

        app.post("host", context -> {
            StartServerDto request = context.bodyAsClass(StartServerDto.class);
            host(request);
            context.contentType(ContentType.TEXT_PLAIN);
            context.result("Ok");
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
                ServerController.eventHandler.addPlayer(request, player);
            }
            context.contentType(ContentType.TEXT_PLAIN);
            context.result("Ok");

        });

        app.get("players", context -> {
            var players = new ArrayList<Player>();
            Groups.player.forEach(players::add);

            context.contentType(ContentType.APPLICATION_JSON);
            context.json(players.stream()//
                    .map(player -> new PlayerDto()//
                            .setName(player.coloredName())//
                            .setUuid(player.uuid())//
                            .setIp(player.ip())
                            .setLocale(player.locale())//
                            .setAdmin(player.admin)//
                            .setJoinedAt(Session.contains(player) //
                                    ? Session.get(player).joinedAt
                                    : Instant.now().toEpochMilli())
                            .setTeam(new TeamDto()//
                                    .setColor(player.team().color.toString())//
                                    .setName(player.team().name)))
                    .collect(Collectors.toList()));

        });

        app.get("player-infos", context -> {
            var pageString = context.queryParam("page");
            var sizeString = context.queryParam("size");
            var isBannedString = context.queryParam("banned");

            int page = pageString != null ? Integer.parseInt(pageString) : 0;
            int size = sizeString != null ? Integer.parseInt(sizeString) : 10;
            Boolean isBanned = isBannedString != null ? Boolean.parseBoolean(isBannedString) : null;

            int offset = page * size;

            Seq<PlayerInfo> bans = Vars.netServer.admins.playerInfo.values().toSeq();
            var result = bans.list()//
                    .stream()//
                    .filter(info -> isBanned == null ? true : info.banned == isBanned)//
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

            context.contentType(ContentType.APPLICATION_JSON);
            context.json(result);

        });
        app.get("kicks", context -> {
            var result = new HashMap<>();
            for (var entry : Vars.netServer.admins.kickedIPs.entries()) {
                if (entry.value != 0 && Time.millis() - entry.value < 0) {
                    result.put(entry.key, entry.value);
                }
            }
            context.contentType(ContentType.APPLICATION_JSON);

            context.json(result);

        });

        app.get("commands", context -> {
            var commands = ServerCommandHandler.getHandler()//
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

            context.contentType(ContentType.APPLICATION_JSON);
            context.json(commands);

        });

        app.post("commands", context -> {
            String[] commands = context.bodyAsClass(String[].class);
            if (commands != null) {
                for (var command : commands) {
                    Log.info("Execute command: " + command);
                    ServerCommandHandler.getHandler().handleMessage(command);
                }
            }
            context.contentType(ContentType.TEXT_PLAIN);
            context.result("Ok");
        });

        app.post("say", context -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting. Host a game first.");
                return;
            }

            String message = context.body();
            Call.sendMessage("[]" + message);

            context.contentType(ContentType.TEXT_PLAIN);
            context.result("Ok");
        });

        app.get("json", context -> {
            var data = new HashMap<String, Object>();

            data.put("stats", getStats());
            data.put("session", Session.get());
            data.put("hud", HudUtils.menus.asMap());
            data.put("buildLogs", ServerController.apiGateway.buildLogs);
            data.put("isHub", Config.IS_HUB);
            data.put("ip", Config.SERVER_IP);
            data.put("enemies", Vars.state.enemies);
            data.put("tps", Vars.state.serverTps);

            var gameStats = new HashMap<String, Object>();

            gameStats.put("buildingsBuilt", Vars.state.stats.buildingsBuilt);
            gameStats.put("buildingsDeconstructed", Vars.state.stats.buildingsDeconstructed);
            gameStats.put("buildingsDestroyed", Vars.state.stats.buildingsDestroyed);
            gameStats.put("coreItemCount", Vars.state.stats.coreItemCount);
            gameStats.put("enemyUnitsDestroyed", Vars.state.stats.enemyUnitsDestroyed);
            gameStats.put("placedBlockCount", Vars.state.stats.placedBlockCount);
            gameStats.put("unitsCreated", Vars.state.stats.unitsCreated);
            gameStats.put("wavesLasted", Vars.state.stats.wavesLasted);

            data.put("gameStats", gameStats);
            data.put("locales", Vars.locales);

            var maps = new ArrayList<HashMap<String, String>>();
            Vars.maps.all().forEach(map -> {
                var tags = new HashMap<String, String>();
                map.tags.each((key, value) -> tags.put(key, value));
                maps.add(tags);
            });
            data.put("maps", Vars.maps.all().map(map -> map.tags).list());
            data.put("mods", Vars.mods.list().map(mod -> mod.meta.toString()).list());
            data.put("votes", ServerController.voteHandler.votes);

            var settings = new HashMap<String, Object>();

            Core.settings.keys().forEach(key -> {
                settings.put(key, Core.settings.get(key, null));
            });

            data.put("settings", settings);

            context.json(data);
        });

        app.start(9999);
        System.out.println("Setup http server done");
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
                ServerCommandHandler.getHandler().handleMessage(command);
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
                .setHosting(Vars.state.isGame())
                .setPaused(Vars.state.isPaused())//
                .setKicks(Vars.netServer.admins.kickedIPs.values().toSeq()
                        .select(value -> Time.millis() - value < 0).size)//
                .setStatus(Vars.state.isGame() ? "HOST" : "UP");
    }

    public byte[] mapPreview() {
        Pixmap pix = null;
        try {
            Map map = Vars.state.map;
            byte[] mapData = {};

            if (map != null) {
                pix = MapIO.generatePreview(Vars.world.tiles);
                Fi file = Vars.dataDirectory.child(MAP_PREVIEW_FILE_NAME);
                file.writePng(pix);
                mapData = file.readBytes();
                pix.dispose();
            }

            return mapData;
        } finally {
            if (pix != null) {
                pix.dispose();
            }
        }
    }

    public void unload() {
        if (app != null) {
            app.stop();
        }
    }
}
