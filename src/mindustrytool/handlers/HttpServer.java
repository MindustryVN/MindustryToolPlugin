package mindustrytool.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.Administration.PlayerInfo;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.messages.request.PlayerDto;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.request.StartServerMessageRequest;
import mindustrytool.messages.response.StatsMessageResponse;
import mindustrytool.type.Team;
import mindustrytool.utils.HudUtils;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class HttpServer {
    private static final String TEMP_SAVE_NAME = "TempSave";

    public void init() {
        System.out.println("Setup http server");

        var app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper//
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)//
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            }));
        });

        app.get("stats", context -> {
            context.json(getStats());
        });

        app.get("detail-stats", context -> {
            context.json(detailStats());
        });

        app.get("ok", (context) -> {
            context.result("Ok");
        });

        app.get("hosting", (context) -> {
            context.json(Vars.state.isPlaying());
        });

        app.post("discord", context -> {
            String message = context.body();

            Call.sendMessage(message);

            context.result("Ok");
        });

        app.post("host", context -> {
            StartServerMessageRequest request = context.bodyAsClass(StartServerMessageRequest.class);

            String mapName = request.getMapName();
            String gameMode = request.getMode();

            if (Vars.state.isGame()) {
                throw new IllegalStateException("Already hosting. Type 'stop' to stop hosting first.");
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
            context.result("Ok");
        });

        app.post("set-player", context -> {
            SetPlayerMessageRequest request = context.bodyAsClass(SetPlayerMessageRequest.class);

            String uuid = request.getUuid();
            boolean isAdmin = request.isAdmin();

            PlayerInfo target = Vars.netServer.admins.getInfoOptional(uuid);
            Player player = Groups.player.find(p -> p.getInfo() == target);

            if (target != null) {
                if (isAdmin) {
                    Vars.netServer.admins.adminPlayer(target.id, player == null ? target.adminUsid : player.usid());
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
            context.result("Ok");
        });

        app.get("players", context -> {
            var players = new ArrayList<Player>();
            Groups.player.forEach(players::add);

            context.json(players.stream()//
                    .map(player -> new PlayerDto()//
                            .setName(player.coloredName())//
                            .setUuid(player.uuid())//
                            .setLocale(player.locale())//
                            .setTeam(new Team()//
                                    .setColor(player.team().color.toString())//
                                    .setName(player.team().name)))
                    .collect(Collectors.toList()));
        });

        app.post("command", context -> {
            String[] commands = context.bodyAsClass(String[].class);
            if (commands != null) {
                for (var command : commands) {
                    Log.info("Execute: " + command);
                    ServerCommandHandler.getHandler().handleMessage(command);
                }
            }

            context.result("Ok");
        });

        System.out.println("Start http server");
        app.start(9999);
        System.out.println("Http server started");

        System.out.println("Setup http server done");

    }

    private StatsMessageResponse getStats() {
        Map map = Vars.state.map;
        String mapName = map != null ? map.name() : "";
        List<String> mods = Vars.mods.list().map(mod -> mod.name).list();
        int players = Groups.player.size();

        return new StatsMessageResponse().setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(players).setMapName(mapName)
                .setMods(mods).setStatus(Vars.state.isGame() ? "HOST" : "UP");
    }

    public StatsMessageResponse detailStats() {
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
