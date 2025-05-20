package mindustrytool.handlers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import arc.Core;
import arc.Events;
import arc.net.Server;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.Timer.Task;
import arc.util.serialization.JsonValue;
import lombok.Data;
import lombok.experimental.Accessors;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods.LoadedMod;
import mindustry.net.Packets.KickReason;
import mindustrytool.Config;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.type.BuildLogDto;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.PaginationRequest;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.ServerDto;
import mindustrytool.type.TeamDto;
import mindustrytool.type.ServerDto.ResponseData;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Session;
import mindustrytool.utils.Utils;
import mindustrytool.utils.HudUtils.Option;
import mindustrytool.utils.HudUtils.PlayerPressCallback;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.WorldReloader;

import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.time.Instant;

public class EventHandler {

    public Task lastTask;

    public Gamemode lastMode;
    public boolean inGameOverWait;

    @Data
    @Accessors(chain = true)
    public static class PlayerMetaData {
        Player player;
        long exp;
        String name;
        boolean isLoggedIn;
        Instant createdAt = Instant.now();
    }

    public static final ConcurrentHashMap<String, PlayerMetaData> playerMeta = new ConcurrentHashMap<>();
    private static final Cache<String, String> translationCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(1000)
            .build();

    private static final Cache<String, ServerDto.ResponseData> serversCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(1000)
            .build();

    private static final List<String> icons = List.of(//
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", ""//
    );

    public void init() {
        System.out.println("Setup event handler");

        try {
            lastMode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
        } catch (Exception e) { // handle enum parse exception
            lastMode = Gamemode.survival;
        }

        if (!Vars.mods.orderedMods().isEmpty()) {
            Log.info("@ mods loaded.", Vars.mods.orderedMods().size);
        }

        int unsupported = Vars.mods.list().count(l -> !l.enabled());

        if (unsupported > 0) {
            Log.err("There were errors loading @ mod(s):", unsupported);
            for (LoadedMod mod : Vars.mods.list().select(l -> !l.enabled())) {
                Log.err("- @ &ly(" + mod.state + ")", mod.meta.name);
            }
        }

        Vars.net.handleServer(Packets.Connect.class, (con, packet) -> {
            Events.fire(new EventType.ConnectionEvent(con));
            Seq<NetConnection> connections = Seq.with(Vars.net.getConnections())
                    .select(other -> other.address.equals(con.address));

            if (connections.size > Config.MAX_IDENTICAL_IPS) {
                Vars.netServer.admins.blacklistDos(con.address);
                connections.each(NetConnection::close);
                Log.info("@ blacklisted because of ip spam", con.address);
            }
        });

        Events.on(GameOverEvent.class, this::onGameOver);
        Events.on(PlayEvent.class, this::onPlay);
        Events.on(PlayerJoin.class, this::onPlayerJoin);
        Events.on(PlayerLeave.class, this::onPlayerLeave);
        Events.on(PlayerChatEvent.class, this::onPlayerChat);
        Events.on(ServerLoadEvent.class, this::onServerLoad);
        Events.on(PlayerConnect.class, this::onPlayerConnect);
        Events.on(BlockBuildEndEvent.class, this::onBuildBlockEnd);

        if (Config.IS_HUB) {
            setupCustomServerDiscovery();
        }

        Timer.schedule(() -> {
            var map = Vars.state.map;

            if (map != null) {
                int page = 0;
                int gap = 40;
                int rows = 4;
                var size = 40;
                int columns = size / rows;
                var request = new PaginationRequest().setPage(page).setSize(size);

                var response = MindustryToolPlugin.apiGateway.getServers(request);
                var servers = response.getServers();

                for (int x = 0; x < columns; x++) {
                    for (int y = 0; y < rows; y++) {
                        var index = x + y * columns;
                        if (index < servers.size()) {
                            int offsetX = (x - columns / 2) * gap * 8;
                            int offsetY = (y - rows / 2) * gap * 8;

                            int placeX = map.width / 2 * 8 + offsetX;
                            int placeY = map.height / 2 * 8 + offsetY;

                            var server = servers.get(index);

                            var serverStatus = server.getStatus().equals("HOST") ? "[green]"
                                    : "[red]" + server.getStatus();

                            var mods = server.getMods();
                            mods.removeIf(m -> m.trim().toLowerCase().equals("mindustrytoolplugin"));

                            String message = //
                                    "%s\n".formatted(server.getName()) //
                                            + "[white]Status: \n".formatted(serverStatus)//
                                            + "[white]Players: \n".formatted(server.getPlayers())//
                                            + "[white]Map: \n".formatted(server.getMapName())//
                                            + "[white]Mode: \n".formatted(server.getMode())//
                                            + "[white]Description: \n".formatted(server.getDescription())//
                                            + "[white]Mods: \n".formatted(mods)//
                            ;

                            Call.label(message, 200000, placeX, placeY);
                        }
                    }
                }

            }
        }, 0, 30);

        System.out.println("Setup event handler done");
    }

    private void onBuildBlockEnd(BlockBuildEndEvent event) {
        try {

            if (event.unit == null || !event.unit.isPlayer()) {
                return;
            }

            if (event.tile == null || event.tile.build == null) {
                return;
            }

            var player = event.unit.getPlayer();
            var playerName = player.plainName();
            var locale = player.locale();
            var team = new TeamDto()//
                    .setColor(player.team().color.toString())
                    .setName(player.team().name);

            var building = event.tile.build;

            var buildLog = new BuildLogDto()//
                    .setPlayer(new PlayerDto()//
                            .setLocale(locale)//
                            .setName(playerName)
                            .setTeam(team)
                            .setUuid(player.uuid()))
                    .setBuilding(new BuildLogDto.BuildingDto()//
                            .setX(building.x())
                            .setY(building.y())
                            .setLastAccess(building.lastAccessed())
                            .setName(building.block() != null ? building.block().name : "Unknow"))
                    .setMessage(event.breaking ? "Breaking" : "Building");

            MindustryToolPlugin.apiGateway.sendBuildLog(buildLog);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setName(Player player, String name, int level) {
        try {
            var icon = getIconBaseOnLevel(level);
            var newName = "[white]%s [%s] %s".formatted(icon, level, name);

            if (!newName.equals(player.name)) {
                var hasLevelInName = player.name.matches("\\[\\d+\\]");

                player.name(newName);

                if (hasLevelInName) {
                    player.sendMessage("You have leveled up to level %s".formatted(level));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getIconBaseOnLevel(int level) {
        var index = (int) (level / 3);

        if (index >= icons.size()) {
            index = icons.size() - 1;
        }

        return icons.get(index);
    }

    private void onPlayerConnect(PlayerConnect event) {
        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            try {
                var player = event.player;

                for (int i = 0; i < player.name().length(); i++) {
                    char ch = player.name().charAt(i);
                    if (ch <= '\u001f') {
                        player.kick("Invalid name");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupCustomServerDiscovery() {
        try {
            var providerField = Net.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = (ArcNetProvider) providerField.get(Vars.net);
            var serverField = ArcNetProvider.class.getDeclaredField("server");
            serverField.setAccessible(true);
            var server = (Server) serverField.get(provider);

            server.setDiscoveryHandler((address, handler) -> {
                String name = mindustry.net.Administration.Config.serverName.string();
                String description = mindustry.net.Administration.Config.desc.string();
                String map = Vars.state.map.name();

                ByteBuffer buffer = ByteBuffer.allocate(500);

                int players = Groups.player.size();

                if (Config.IS_HUB) {
                    try {
                        var serverData = getTopServer();
                        if (serverData != null) {
                            name += " -> " + serverData.name;
                            description += " -> " + serverData.description;
                            map = serverData.mapName == null ? "" : serverData.mapName;
                            players = (int) serverData.players;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                writeString(buffer, name, 100);
                writeString(buffer, map, 64);

                buffer.putInt(Core.settings.getInt("totalPlayers", players));
                buffer.putInt(Vars.state.wave);
                buffer.putInt(Version.build);
                writeString(buffer, Version.type);

                buffer.put((byte) Vars.state.rules.mode().ordinal());
                buffer.putInt(Vars.netServer.admins.getPlayerLimit());

                writeString(buffer, description, 100);
                if (Vars.state.rules.modeName != null) {
                    writeString(buffer, Vars.state.rules.modeName, 50);
                }
                buffer.position(0);
                handler.respond(buffer);
            });

        } catch (Exception e) {
            Log.err(e);
        }
    }

    private static void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    private static void writeString(ByteBuffer buffer, String string, int maxlen) {
        byte[] bytes = string.getBytes(Vars.charset);
        if (bytes.length > maxlen) {
            bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        }

        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    public void onServerLoad(ServerLoadEvent event) {
        Config.isLoaded = true;
    }

    public void onPlayerChat(PlayerChatEvent event) {
        try {

            Player player = event.player;
            String message = event.message;

            // Filter all commands
            if (message.startsWith("/")) {
                return;
            }

            Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
                String chat = Strings.format("[@] => @", player.plainName(), message);

                try {
                    MindustryToolPlugin.apiGateway.sendChatMessage(chat);
                } catch (Exception e) {
                    Log.err(e);
                }
            });

            Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
                Groups.player.each(p -> {
                    if (p.id != player.id) {
                        var locale = p.locale();
                        try {
                            String translatedMessage = translationCache.get(locale + message,
                                    key -> MindustryToolPlugin.apiGateway.translate(message, locale));
                            p.sendMessage("[white][Translation] " + player.name() + "[]: " + translatedMessage);
                        } catch (Exception e) {
                            Log.err(e);
                        }
                    }
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onPlayerLeave(PlayerLeave event) {

        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {

            try {
                var player = event.player;
                var team = player.team();
                var request = new PlayerDto()//
                        .setName(player.coloredName())//
                        .setIp(player.ip())//
                        .setLocale(player.locale())//
                        .setUuid(player.uuid())//
                        .setTeam(new TeamDto()//
                                .setName(team.name)//
                                .setColor(team.color.toString()));

                MindustryToolPlugin.apiGateway.sendPlayerLeave(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            try {

                Player player = event.player;

                Session.remove(player);
                playerMeta.remove(event.player.uuid());

                MindustryToolPlugin.voteHandler.removeVote(player);

                String playerName = event.player != null ? event.player.plainName() : "Unknown";
                String chat = Strings.format("@ leaved the server, current players: @", playerName,
                        Groups.player.size() - 1);

                Timer.schedule(() -> {
                    if (!Vars.state.isPaused() && Groups.player.size() == 0) {
                        Vars.state.set(State.paused);
                        MindustryToolPlugin.apiGateway.sendConsoleMessage("No player: paused");
                    }
                }, 10);

                MindustryToolPlugin.apiGateway.sendChatMessage(chat);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public synchronized ServerDto.ResponseData getTopServer() throws IOException {
        try {
            return serversCache.get("server", ignore -> {
                var request = new PaginationRequest().setPage(0).setSize(1);
                var response = MindustryToolPlugin.apiGateway.getServers(request);
                var servers = response.getServers();

                if (servers.isEmpty()) {
                    return null;
                }

                if (servers.get(0).getId() == null) {
                    return null;
                }

                return servers.get(0);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void onPlayerJoin(PlayerJoin event) {
        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            try {
                if (Vars.state.isPaused()) {
                    Vars.state.set(State.playing);
                    MindustryToolPlugin.apiGateway.sendConsoleMessage("Player join: unpaused");
                }

                var player = event.player;

                Session.put(player);

                if (Config.IS_HUB) {
                    var serverData = getTopServer();

                    if (serverData != null && !serverData.getId().equals(MindustryToolPlugin.SERVER_ID)) {
                        var options = List.of(//
                                HudUtils.option((p, state) -> {
                                    HudUtils.closeFollowDisplay(p, HudUtils.SERVER_REDIRECT);
                                }, "[red]No"),
                                HudUtils.option((p, state) -> {
                                    onServerChoose(p, serverData.id.toString(), serverData.name);
                                    HudUtils.closeFollowDisplay(p, HudUtils.SERVER_REDIRECT);
                                }, "[green]Yes"));
                        HudUtils.showFollowDisplay(player, HudUtils.SERVER_REDIRECT, "Redirect",
                                "Do you want to go to server: " + serverData.getName(), null, options);
                    }
                }

                PlayerInfo target = Vars.netServer.admins.getInfoOptional(player.uuid());

                if (target != null) {
                    Vars.netServer.admins.unAdminPlayer(target.id);
                }

                String playerName = player != null ? player.plainName() : "Unknown";
                String chat = Strings.format("@ joined the server, current players: @", playerName,
                        Groups.player.size());

                var team = player.team();
                var request = new PlayerDto()//
                        .setName(player.coloredName())//
                        .setIp(player.ip())//
                        .setLocale(player.locale())//
                        .setUuid(player.uuid())//
                        .setTeam(new TeamDto()//
                                .setName(team.name)//
                                .setColor(team.color.toString()));

                MindustryToolPlugin.apiGateway.sendChatMessage(chat);

                var playerData = MindustryToolPlugin.apiGateway.setPlayer(request);
                if (Config.IS_HUB) {
                    sendHub(event.player, playerData.getLoginLink());
                } else {
                    if (playerData.getLoginLink() != null) {
                        player.sendMessage("[green]Logged in successfully");
                    } else {
                        player.sendMessage("You are not logged in, consider log in via MindustryTool using /login");
                    }
                }

                var isAdmin = playerData.isAdmin();

                addPlayer(playerData, player);

                Player playert = Groups.player.find(p -> p.getInfo() == target);

                if (target != null) {
                    if (isAdmin) {
                        Vars.netServer.admins.adminPlayer(target.id,
                                playert == null ? target.adminUsid : playert.usid());
                    } else {
                        Vars.netServer.admins.unAdminPlayer(target.id);
                    }
                    if (playert != null)
                        playert.admin = isAdmin;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void onPlay(PlayEvent event) {
        try {
            JsonValue value = JsonIO.json.fromJson(null, Core.settings.getString("globalrules"));
            JsonIO.json.readFields(Vars.state.rules, value);
        } catch (Throwable t) {
            Log.err("Error applying custom rules, proceeding without them.", t);
        }
    }

    public void onGameOver(GameOverEvent event) {
        try {
            if (inGameOverWait) {
                return;
            }

            if (Vars.state.rules.waves) {
                Log.info("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                        Groups.player.size(),
                        Strings.capitalize(Vars.state.map.plainName()));
            } else {
                Log.info("Game over! Team @ is victorious with @ players online on map @.", event.winner.name,
                        Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));
            }

            // set the next map to be played
            Map map = Vars.maps.getNextMap(lastMode, Vars.state.map);
            if (map != null) {
                Call.infoMessage(
                        (Vars.state.rules.pvp ? "[accent]The " + event.winner.coloredName() + " team is victorious![]\n"
                                : "[scarlet]Game over![]\n") + "\nNext selected map: [accent]" + map.name() + "[white]"
                                + (map.hasTag("author") ? " by[accent] " + map.author() + "[white]" : "") + "."
                                + "\nNew game begins in " + mindustry.net.Administration.Config.roundExtraTime.num()
                                + " seconds.");

                Vars.state.gameOver = true;
                Call.updateGameOver(event.winner);

                Log.info("Selected next map to be @.", map.plainName());

                play(() -> Vars.world.loadMap(map, map.applyRules(lastMode)));
            } else {
                Vars.netServer.kickAll(KickReason.gameover);
                Vars.state.set(State.menu);
                Vars.net.closeServer();
            }

            String message = Vars.state.rules.waves
                    ? Strings.format("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                            Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()))
                    : Strings.format("Game over! Team @ is victorious with @ players online on map @.",
                            event.winner.name,
                            Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));

            MindustryToolPlugin.apiGateway.sendChatMessage(message);
        } catch (Exception e) {
            Log.err(e);
            MindustryToolPlugin.apiGateway.sendConsoleMessage(e.getMessage());
        }

    }

    public void sendHub(Player player, String loginLink) {
        var options = new ArrayList<Option>();

        if (loginLink != null && !loginLink.isEmpty()) {
            options.add(HudUtils.option((trigger, state) -> {
                Call.openURI(trigger.con, loginLink);
                HudUtils.closeFollowDisplay(trigger, HudUtils.HUB_UI);

            }, "[green]Login via MindustryTool"));
        }

        options.add(HudUtils.option((p, state) -> Call.openURI(player.con, Config.RULE_URL), "[green]Rules"));
        options.add(
                HudUtils.option((p, state) -> Call.openURI(player.con, Config.MINDUSTRY_TOOL_URL), "[green]Website"));
        options.add(
                HudUtils.option((p, state) -> Call.openURI(player.con, Config.DISCORD_INVITE_URL), "[blue]Discord"));
        options.add(HudUtils.option((p, state) -> {
            sendServerList(player, 0);
            HudUtils.closeFollowDisplay(p, HudUtils.HUB_UI);
        }, "[red]Close"));

        HudUtils.showFollowDisplay(player, HudUtils.HUB_UI, "Servers", Config.HUB_MESSAGE, null, options);

        var map = Vars.state.map;

        if (map != null) {
            Call.label(Config.HUB_MESSAGE, 200000, map.width / 2 * 8, map.height / 2 * 8);
        }
    }

    public void sendServerList(Player player, int page) {
        try {
            var size = 8;
            var request = new PaginationRequest().setPage(page).setSize(size);

            var response = MindustryToolPlugin.apiGateway.getServers(request);
            var servers = response.getServers();

            PlayerPressCallback invalid = (p, s) -> {
                sendServerList(p, (int) s);
                Call.infoToast(p.con, "Please don't click there", 10f);
            };

            List<List<HudUtils.Option>> options = new ArrayList<>();

            servers.stream().sorted(Comparator.comparing(ResponseData::getPlayers).reversed()).forEach(server -> {
                PlayerPressCallback valid = (p, s) -> onServerChoose(p, server.getId().toString(),
                        server.getName());

                var mods = server.getMods();
                mods.removeIf(m -> m.trim().toLowerCase().equals("mindustrytoolplugin"));

                if (server.getMapName() == null) {
                    options.add(List.of(HudUtils.option(valid, "[yellow]%s".formatted(server.getName())),
                            HudUtils.option(valid, "[scarlet]Server offline.")));
                } else {
                    options.add(List.of(HudUtils.option(valid, server.getName()),
                            HudUtils.option(valid, "[lime]Players:[] %d".formatted(server.getPlayers()))));

                    options.add(List.of(
                            HudUtils.option(valid,
                                    "[cyan]Gamemode:[] %s".formatted(server.getMode().toLowerCase())),
                            HudUtils.option(valid, "[blue]Map:[] %s".formatted(server.getMapName()))));
                }

                if (server.getMods() != null && !server.getMods().isEmpty()) {
                    options.add(List
                            .of(HudUtils.option(valid, "[purple]Mods:[] %s".formatted(String.join(", ", mods)))));
                }

                if (server.getDescription() != null && !server.getDescription().trim().isEmpty()) {
                    options.add(List.of(HudUtils.option(valid, "[grey]%s".formatted(server.getDescription()))));
                }

                options.add(List.of(HudUtils.option(invalid, "-----------------")));
            });

            options.add(List.of(page > 0 ? HudUtils.option((p, state) -> {
                sendServerList(player, (int) state - 1);
            }, "[orange]Previous") : HudUtils.option(invalid, "First page"),
                    servers.size() == size ? HudUtils.option((p, state) -> {
                        sendServerList(player, (int) state + 1);
                    }, "[lime]Next") : HudUtils.option(invalid, "No more")));

            options.add(List.of(HudUtils.option((p, state) -> HudUtils.closeFollowDisplay(p, HudUtils.SERVERS_UI),
                    "[scarlet]Close")));

            HudUtils.showFollowDisplays(player, HudUtils.SERVERS_UI, "List of all servers",
                    Config.CHOOSE_SERVER_MESSAGE, Integer.valueOf(page), options);
        } catch (Exception e) {
            Log.err(e);
        }
    }

    public void onServerChoose(Player player, String id, String name) {
        HudUtils.closeFollowDisplay(player, HudUtils.SERVERS_UI);
        Utils.executeExpectError(() -> {

            try {
                player.sendMessage(
                        "[green]Starting server [white]%s, [white]redirection will happen soon".formatted(name));
                Log.info("Send host command to server %s %S".formatted(name, id));
                var data = MindustryToolPlugin.apiGateway.host(id);
                player.sendMessage("[green]Redirecting");
                Call.sendMessage("%s [green]redirecting to server [white]%s, use [green]/servers[white] to follow"
                        .formatted(player.coloredName(), name));

                String host = "";
                int port = 6567;

                var colon = data.lastIndexOf(":");

                if (colon > 0) {
                    host = data.substring(0, colon);
                    port = Integer.parseInt(data.substring(colon + 1).trim());
                } else {
                    host = data;
                }

                Log.info("Redirecting " + player.name + " to " + host + ":" + port);

                Call.connect(player.con, InetAddress.getByName(host.trim()).getHostAddress(), port);
            } catch (Exception e) {
                player.sendMessage("Error: Can not load server");
            }
        });
    }

    public void cancelPlayTask() {
        if (lastTask != null)
            lastTask.cancel();
    }

    public void play(Runnable run) {
        play(true, run);
    }

    public void play(boolean wait, Runnable run) {
        inGameOverWait = true;
        cancelPlayTask();

        Runnable reload = () -> {
            try {
                WorldReloader reloader = new WorldReloader();
                reloader.begin();

                run.run();

                Vars.state.rules = Vars.state.map.applyRules(lastMode);
                Vars.logic.play();

                reloader.end();
                inGameOverWait = false;

            } catch (MapException e) {
                Log.err("@: @", e.map.plainName(), e.getMessage());
                Vars.net.closeServer();
                MindustryToolPlugin.apiGateway.sendConsoleMessage(e.getMessage());
            }
        };

        if (wait) {
            lastTask = Timer.schedule(reload, mindustry.net.Administration.Config.roundExtraTime.num());
        } else {
            reload.run();
        }

        System.gc();
    }

    public void addPlayer(MindustryPlayerDto playerData, Player player) {
        var uuid = playerData.getUuid();
        var exp = playerData.getExp();
        var name = playerData.getName();
        var isLoggedIn = playerData.getLoginLink() == null;

        if (uuid == null) {
            Log.warn("Player with null uuid: " + playerData);
            return;
        }

        playerMeta.put(uuid, new PlayerMetaData()//
                .setExp(exp)//
                .setPlayer(player)//
                .setLoggedIn(isLoggedIn)//
                .setName(name));

        if (isLoggedIn) {
            setName(player, name, (int) Math.sqrt(exp));
            player.sendMessage("Logged in as " + name);
        } else {
            player.sendMessage("You are not logged in, consider log in via MindustryTool using /login");
        }
    }

}
