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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import arc.Core;
import arc.net.Server;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustrytool.Config;
import mindustrytool.ServerController;
import mindustrytool.type.BuildLogDto;
import mindustrytool.type.BuildingDto;
import mindustrytool.type.HudOption;
import mindustrytool.type.MindustryPlayerDto;
import mindustrytool.type.PaginationRequest;
import mindustrytool.type.PlayerDto;
import mindustrytool.type.PlayerPressCallback;
import mindustrytool.type.ServerCore;
import mindustrytool.type.TeamDto;
import mindustrytool.type.ServerResponseData;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Session;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net;
import mindustry.world.Tile;
import mindustry.world.blocks.campaign.Accelerator;

import java.time.Duration;

public class EventHandler {

    private final ServerController controller;

    public EventHandler(ServerController controller) {
        this.controller = controller;
        Log.info("Event handler created");
    }

    private List<ServerResponseData> servers = new ArrayList<>();
    private List<ServerCore> serverCores = new ArrayList<>();

    int page = 0;
    int gap = 50;
    int rows = 4;
    int size = 40;
    int columns = size / rows;

    private final Cache<String, String> translationCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(1000)
            .build();

    private ScheduledFuture<?> updateServerTask, updateServerCore;

    private final List<String> icons = List.of(//
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", "", //
            "", "", "", "", "", "", "", "", "", ""//
    );

    public void init() {
        Log.info("Setup event handler");

        if (Config.IS_HUB) {
            setupCustomServerDiscovery();

            updateServerTask = Config.BACKGROUND_SCHEDULER.scheduleWithFixedDelay(() -> {
                try {
                    var request = new PaginationRequest().setPage(page).setSize(size);

                    var response = controller.apiGateway.getServers(request);
                    servers = response.getServers();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 30, TimeUnit.SECONDS);

            updateServerCore = Config.BACKGROUND_SCHEDULER.scheduleWithFixedDelay(() -> {
                try {
                    var map = Vars.state.map;

                    serverCores.clear();

                    if (map != null) {

                        Call.label(Config.HUB_MESSAGE, 5, map.width / 2 * 8, map.height / 2 * 8);

                        for (int x = 0; x < columns; x++) {
                            for (int y = 0; y < rows; y++) {
                                var index = x + y * columns;
                                int coreX = (x - columns / 2) * gap + map.width / 2;
                                int coreY = (y - rows / 2) * gap + map.height / 2;

                                if (servers != null && index < servers.size()) {
                                    var server = servers.get(index);

                                    int offsetX = (x - columns / 2) * gap * 8;
                                    int offsetY = (y - rows / 2) * gap * 8;

                                    int messageX = map.width / 2 * 8 + offsetX;
                                    int messageY = map.height / 2 * 8 + offsetY + 25;

                                    var serverStatus = server.getStatus().equals("HOST")
                                            ? "[green]" + server.getStatus()
                                            : "[red]" + server.getStatus();

                                    var mods = server.getMods();
                                    mods.removeIf(m -> m.trim().toLowerCase().equals("mindustrytoolplugin"));

                                    String message = //
                                            "%s (Tap core to join)\n\n".formatted(server.getName()) //
                                                    + "[white]Status: %s\n".formatted(serverStatus)//
                                                    + "[white]Players: %s\n".formatted(server.getPlayers())//
                                                    + "[white]Map: %s\n".formatted(server.getMapName())//
                                                    + "[white]Mode: %s\n".formatted(server.getMode())//
                                                    + "[white]Description: %s\n".formatted(server.getDescription())//
                                                    + (mods.isEmpty() ? "" : "[white]Mods: %s".formatted(mods));

                                    Tile tile = Vars.world.tile(coreX, coreY);

                                    if (tile != null) {
                                        if (tile.build == null || !(tile.block() instanceof Accelerator)) {
                                            tile.setBlock(Blocks.interplanetaryAccelerator, Team.sharded, 0);

                                            var block = tile.block();
                                            var build = tile.build;
                                            if (block == null || build == null)
                                                return;

                                            for (var item : Vars.content.items()) {
                                                if (block.consumesItem(item) && build.items.get(item) < 10000) {
                                                    build.items.add(item, 10000);
                                                }
                                            }
                                        }
                                        serverCores.add(new ServerCore(server, coreX, coreY));
                                    }

                                    Call.label(message, 5, messageX, messageY);
                                } else {
                                    Tile tile = Vars.world.tile(coreX, coreY);
                                    if (tile != null && tile.build != null) {
                                        tile.build.kill();
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
        Log.info("Setup event handler done");
    }

    public void unload() {
        if (updateServerTask != null) {
            updateServerTask.cancel(true);
        }

        if (updateServerCore != null) {
            updateServerCore.cancel(true);
        }
    }

    public void onTap(TapEvent event) {
        if (event.tile == null) {
            return;
        }
        var map = Vars.state.map;

        if (map == null) {
            return;
        }

        var tapSize = 4;

        var tapX = event.tile.x;
        var tapY = event.tile.y;

        for (var core : serverCores) {
            if (tapX >= core.x() - tapSize //
                    && tapX <= core.x() + tapSize //
                    && tapY >= core.y() - tapSize
                    && tapY <= core.y() + tapSize//
            ) {
                onServerChoose(event.player, core.server().id.toString(), core.server().name);
            }
        }
    }

    public void onBuildBlockEnd(BlockBuildEndEvent event) {
        try {

            if (event.unit == null || !event.unit.isPlayer()) {
                return;
            }

            var tile = event.tile;

            if (tile == null) {
                return;
            }

            var building = event.tile.build;

            if (building == null) {
                return;
            }

            var player = event.unit.getPlayer();
            var playerName = player.plainName();
            var locale = player.locale();
            var team = new TeamDto()//
                    .setColor(player.team().color.toString())
                    .setName(player.team().name);

            var buildLog = new BuildLogDto()//
                    .setPlayer(new PlayerDto()//
                            .setLocale(locale)//
                            .setName(playerName)
                            .setTeam(team)
                            .setUuid(player.uuid()))
                    .setBuilding(new BuildingDto()//
                            .setX(building.x())
                            .setY(building.y())
                            .setLastAccess(building.lastAccessed())
                            .setName(building.block() != null ? building.block().name : "Unknown"))
                    .setMessage(event.breaking ? "Breaking" : "Building");

            controller.apiGateway.sendBuildLog(buildLog);
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

    public void onPlayerConnect(PlayerConnect event) {
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

                buffer.clear();
            });

        } catch (Exception e) {
            Log.err(e);
        }
    }

    private void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    private void writeString(ByteBuffer buffer, String string, int maxlen) {
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

            String chat = Strings.format("[@] => @", player.plainName(), message);

            // Filter all commands
            if (message.startsWith("/")) {
                return;
            }

            try {
                controller.apiGateway.sendChatMessage(chat);
            } catch (Exception e) {
                Log.err(e);
            }

            Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
                Groups.player.each(p -> {
                    if (p.id != player.id) {
                        var locale = p.locale();
                        try {
                            String translatedMessage = translationCache.get(locale + message,
                                    key -> controller.apiGateway.translate(message, locale));
                            p.sendMessage("[white][Translation] " + player.name() + "[]: " + translatedMessage);
                            controller.apiGateway.sendChatMessage("[white][Translation] " + player.name() + "[]: "
                                    + translatedMessage);

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

            controller.apiGateway.sendPlayerLeave(request);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {

            Player player = event.player;

            Session.remove(player);

            controller.voteHandler.removeVote(player);

            String playerName = event.player != null ? event.player.plainName() : "Unknown";
            String chat = Strings.format("@ leaved the server, current players: @", playerName,
                    Math.max(Groups.player.size() - 1, 0));

            Config.BACKGROUND_SCHEDULER.schedule(() -> {
                if (!Vars.state.isPaused() && Groups.player.size() == 0) {
                    Vars.state.set(State.paused);
                    Log.info("No player: paused");
                }
            }, 10, TimeUnit.SECONDS);

            controller.apiGateway.sendChatMessage(chat);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized ServerResponseData getTopServer() throws IOException {
        try {
            var request = new PaginationRequest().setPage(0).setSize(1);
            var response = controller.apiGateway.getServers(request);
            var servers = response.getServers();

            if (servers.isEmpty()) {
                return null;
            }

            if (servers.get(0).getId() == null) {
                return null;
            }

            return servers.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void onPlayerJoin(PlayerJoin event) {
        try {
            if (Vars.state.isPaused()) {
                Vars.state.set(State.playing);
                Log.info("Player join: unpaused");
            }

            var player = event.player;

            Session.put(player);

            if (Config.IS_HUB) {
                var serverData = getTopServer();

                if (serverData != null //
                        && !serverData.getId().equals(ServerController.SERVER_ID)
                        && serverData.players > 0//
                ) {
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

            controller.apiGateway.sendChatMessage(chat);

            var playerData = controller.apiGateway.setPlayer(request);

            if (Config.IS_HUB) {
                sendHub(event.player, playerData.getLoginLink());
            }

            setPlayerData(playerData, player);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHub(Player player, String loginLink) {
        var options = new ArrayList<HudOption>();

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
    }

    public void sendServerList(Player player, int page) {
        try {
            var size = 8;
            var request = new PaginationRequest().setPage(page).setSize(size);

            var response = controller.apiGateway.getServers(request);
            var servers = response.getServers();

            PlayerPressCallback invalid = (p, s) -> {
                sendServerList(p, (int) s);
                Call.infoToast(p.con, "Please don't click there", 10f);
            };

            List<List<HudOption>> options = new ArrayList<>();

            servers.stream().sorted(Comparator.comparing(ServerResponseData::getPlayers).reversed()).forEach(server -> {
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
        Config.BACKGROUND_TASK_EXECUTOR.submit(() -> {
            try {
                player.sendMessage(
                        "[green]Starting server [white]%s, [white]redirection will happen soon".formatted(name));
                Log.info("Send host command to server %s %S".formatted(name, id));
                var data = controller.apiGateway.host(id);
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

    public void setPlayerData(MindustryPlayerDto playerData, Player player) {
        var uuid = playerData.getUuid();
        var exp = playerData.getExp();
        var name = playerData.getName();
        var isLoggedIn = playerData.getLoginLink() == null;

        PlayerInfo target = Vars.netServer.admins.getInfoOptional(player.uuid());
        var isAdmin = playerData.isAdmin();

        if (uuid == null) {
            Log.warn("Player with null uuid: " + playerData);
            return;
        }

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

        if (isLoggedIn) {
            setName(player, name, (int) Math.sqrt(exp));
            player.sendMessage("Logged in as " + name);
        } else {
            player.sendMessage("You are not logged in, consider log in via MindustryTool using /login");
        }
    }

}
