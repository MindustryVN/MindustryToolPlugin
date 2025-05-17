package mindustrytool.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.net.Administration.PlayerInfo;
import mindustrytool.Config;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.type.PlayerInfoDto;
import mindustrytool.type.MindustryPlayerDto;
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

public class HttpServer {
    private static final String TEMP_SAVE_NAME = "TempSave";
    private static Javalin app;

    public void init() {
        System.out.println("Setup http server");
        try {
            if (app != null) {
                app.stop();
            }

            app = Javalin.create(config -> {
                config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                    mapper//
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)//
                            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

                }));
            });

            app.get("stats", context -> {
                try {
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(getStats());
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.get("detail-stats", context -> {
                try {
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(detailStats());
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.get("ok", (context) -> {
                try {
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json("Ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.get("plugin-version", context -> {
                context.contentType(ContentType.APPLICATION_JSON);
                context.json(Config.PLUGIN_VERSION);
            });

            app.get("hosting", (context) -> {
                try {
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(Vars.state.isGame());
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.post("discord", context -> {
                try {
                    String message = context.body();

                    Call.sendMessage(message);
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.result("Ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.post("host", context -> {
                try {
                    StartServerDto request = context.bodyAsClass(StartServerDto.class);
                    host(request);
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.result("Ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.post("set-player", context -> {
                try {
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
                        MindustryToolPlugin.eventHandler.addPlayer(request, player);
                    }
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.result("Ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }

            });

            app.get("players", context -> {
                try {
                    var players = new ArrayList<Player>();
                    Groups.player.forEach(players::add);

                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(players.stream()//
                            .map(player -> new PlayerDto()//
                                    .setName(player.coloredName())//
                                    .setUuid(player.uuid())//
                                    .setLocale(player.locale())//
                                    .setAdmin(player.admin)//
                                    .setJoinedAt(Session.get(player).joinedAt)
                                    .setTeam(new TeamDto()//
                                            .setColor(player.team().color.toString())//
                                            .setName(player.team().name)))
                            .collect(Collectors.toList()));
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.post("player-infos", context -> {
                var pageString = context.queryParam("page");
                var sizeString = context.queryParam("size");
                var isBannedString = context.queryParam("banned");

                int page = pageString != null ? Integer.parseInt(pageString) : 0;
                int size = sizeString != null ? Integer.parseInt(sizeString) : 10;
                boolean isBanned = isBannedString!= null? Boolean.parseBoolean(isBannedString) : false;

                int offset = page * size;
                try {
                    Seq<PlayerInfo> bans = Vars.netServer.admins.playerInfo.values().toSeq();
                    var result = bans.list()//
                            .stream()//
                            .filter(info -> info.banned == isBanned)//
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
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });
            app.post("kicks", context -> {
                try {
                    context.contentType(ContentType.APPLICATION_JSON);
                    context.json(Vars.netServer.admins.kickedIPs);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.get("commands", context -> {
                try {
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
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.post("commands", context -> {
                try {
                    String[] commands = context.bodyAsClass(String[].class);
                    if (commands != null) {
                        for (var command : commands) {
                            Log.info("Execute: " + command);
                            ServerCommandHandler.getHandler().handleMessage(command);
                        }
                    }
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.result("Ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            System.out.println("Start http server");
            app.start(9999);
            System.out.println("Http server started");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                Log.info("Execute: " + command);
                ServerCommandHandler.getHandler().handleMessage(command);
            }
            return;
        }

        Utils.host(mapName, gameMode);
    }

    private StatsDto getStats() {
        Map map = Vars.state.map;
        String mapName = map != null ? map.name() : "";
        List<String> mods = Vars.mods.list().map(mod -> mod.name).list();
        int players = Groups.player.size();

        return new StatsDto().setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(players).setMapName(mapName)
                .setMods(mods).setStatus(Vars.state.isGame() ? "HOST" : "UP");
    }

    public StatsDto detailStats() {
        Map map = Vars.state.map;
        byte[] mapData = {};

        if (map != null) {
            var pix = MapIO.generatePreview(Vars.world.tiles);
            Fi file = Fi.tempFile(TEMP_SAVE_NAME);
            file.writePng(pix);
            mapData = file.readBytes();
            pix.dispose();
        }

        return getStats().setMapData(mapData);
    }
}
