package mindustrytool.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

import arc.Core;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods.ModLoadException;
import mindustry.mod.Mods.ModMeta;
import mindustry.net.Administration.PlayerInfo;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.type.MapDto;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.ModDto;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerCommandDto;
import mindustrytool.type.StartServerDto;
import mindustrytool.type.StatsDto;
import mindustrytool.type.TeamDto;
import mindustrytool.type.ServerCommandDto.CommandParamDto;
import mindustrytool.utils.HudUtils;

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

            app.get("maps", context -> {
                try {
                    var maps = Vars.customMapDirectory.findAll()
                            .map(file -> {
                                try {
                                    return MapIO.createMap(file, true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return new Map(file, 0, 0, new StringMap(), true, 0, Version.build);
                                }
                            }).map(map -> new MapDto()//
                                    .setName(map.name())//
                                    .setFilename(map.file.name())
                                    .setCustom(map.custom)
                                    .setHeight(map.height)
                                    .setWidth(map.width));

                    context.contentType(ContentType.JSON);
                    context.json(maps);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.contentType(ContentType.TEXT_PLAIN);
                    context.status(500);
                    context.result(e.getMessage());
                }
            });

            app.get("mods", context -> {
                try {
                    var modFiles = Vars.modDirectory.findAll();

                    var result = new ArrayList<ModDto>();
                    for (var modFile : modFiles) {
                        try {
                            var mod = loadMod(modFile);
                            result.add(mod);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    context.contentType(ContentType.JSON);
                    context.json(result);
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

    private ModDto loadMod(Fi sourceFile) throws Exception {
        ZipFi rootZip = null;

        try {
            Fi zip = sourceFile.isDirectory() ? sourceFile : (rootZip = new ZipFi(sourceFile));
            if (zip.list().length == 1 && zip.list()[0].isDirectory()) {
                zip = zip.list()[0];
            }

            ModMeta meta = Vars.mods.findMeta(zip);

            if (meta == null) {
                Log.warn("Mod @ doesn't have a '[mod/plugin].[h]json' file, skipping.", zip);
                throw new ModLoadException("Invalid file: No mod.json found.");
            }

            return new ModDto().setFilename(zip.name()).setName(meta.name).setMeta(meta);
        } catch (Exception e) {
            // delete root zip file so it can be closed on windows
            if (rootZip != null)
                rootZip.delete();
            throw e;
        }
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

        Gamemode preset = Gamemode.survival;

        if (gameMode != null) {
            try {
                preset = Gamemode.valueOf(gameMode);
            } catch (IllegalArgumentException e) {
                Log.err("No gamemode '@' found.", gameMode);
                return;
            }
        }

        Map result;
        try {
            if (mapName == null) {
                var maps = Vars.customMapDirectory.list();

                if (maps.length == 0) {
                    Log.err("No custom maps found.");
                    return;
                }
                result = MapIO.createMap(Vars.customMapDirectory.list()[0], true);
            } else {
                result = MapIO.createMap(Vars.customMapDirectory.child(mapName), true);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read map file: " + mapName);
        }
        if (result == null) {
            Log.err("No map with name '@' found.", mapName);
            return;
        }

        Log.info("Loading map...");

        Vars.logic.reset();
        MindustryToolPlugin.eventHandler.lastMode = preset;
        Core.settings.put("lastServerMode", MindustryToolPlugin.eventHandler.lastMode.name());

        try {
            Vars.world.loadMap(result, result.applyRules(preset));
            Vars.state.rules = result.applyRules(preset);
            Vars.logic.play();

            Log.info("Map loaded.");

            Vars.netServer.openServer();

        } catch (MapException e) {
            Log.err("@: @", e.map.plainName(), e.getMessage());
        }
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
        }

        return getStats().setMapData(mapData);
    }
}
