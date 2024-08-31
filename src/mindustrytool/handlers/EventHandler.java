package mindustrytool.handlers;

import java.util.ArrayList;
import java.util.Arrays;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.Timer.Task;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerChatEvent;
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
import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Utils;
import mindustry.net.WorldReloader;

public class EventHandler {

    public Task lastTask;

    public Gamemode lastMode;
    public boolean inGameOverWait;

    public void init() {
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

        Events.on(GameOverEvent.class, this::onGameOver);
        Events.on(PlayEvent.class, this::onPlay);
        Events.on(PlayerJoin.class, this::onPlayerJoin);
        Events.on(PlayerLeave.class, this::onPlayerLeave);
        Events.on(PlayerChatEvent.class, this::onPlayerChat);
        Events.on(ServerLoadEvent.class, this::onServerLoad);
        Events.run(EventType.Trigger.update, this::onUpdate);
    }

    public void onUpdate() {
        Groups.player.each(p -> {
            if (p.unit().moving()) {
                Call.effect(Effect.all.first(), p.x, p.y, 0, Color.white);
            }
        });
    }

    public void onServerLoad(ServerLoadEvent event) {
        Config.isLoaded = true;
    }

    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.player;
        String message = event.message;

        String chat = Strings.format("[@] => @", player.plainName(), message);

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
    }

    public void onPlayerLeave(PlayerLeave event) {
        if (!Vars.state.isPaused() && Groups.player.size() == 1) {
            Vars.state.set(State.paused);
        }

        Player player = event.player;
        MindustryToolPlugin.voteHandler.removeVote(player);

        String playerName = event.player != null ? event.player.plainName() : "Unknown";
        String chat = Strings.format("@ leaved the server, current players: @", playerName, Groups.player.size() - 1);

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
    }

    public void onPlayerJoin(PlayerJoin event) {
        if (Vars.state.isPaused()) {
            Vars.state.set(State.playing);
        }

        String playerName = event.player != null ? event.player.plainName() : "Unknown";
        String chat = Strings.format("@ joined the server, current players: @", playerName, Groups.player.size());

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);

        if (Config.isHub()) {
            sendHub(event.player);
        }

        event.player.sendMessage("Server discord: https://discord.gg/72324gpuCd");
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
        if (inGameOverWait) {
            return;
        }

        if (Vars.state.rules.waves) {
            Log.info("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave, Groups.player.size(),
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
                : Strings.format("Game over! Team @ is victorious with @ players online on map @.", event.winner.name,
                        Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", message);
    }

    public void sendHub(Player player) {

        var options = Arrays.asList(//
                HudUtils.option((p) -> Call.openURI(player.con, Config.MINDUSTRY_TOOL_URL), "[green]Website"), //
                HudUtils.option((p) -> Call.openURI(player.con, Config.DISCORD_INVITE_URL), "[blue]Discord"), //
                HudUtils.option((p) -> {
                    HudUtils.closeFollowDisplay(p, HudUtils.HUB_UI);
                    sendServerList(player, 0);
                }, "[red]Close")//
        );
        HudUtils.showFollowDisplay(player, HudUtils.HUB_UI, "Servers", """
                    Command
                    [yellow]/servers[white] to show server list
                    [yellow]/rtv[white] to vote for changing map
                    [yellow]/maps[white] to see map list
                """, options.toArray(HudUtils.Option[]::new));

    }

    public void sendServerList(Player player, int page) {
        Utils.executeExpectError(() -> {
            var request = new GetServersMessageRequest().setPage(page).setSize(10);

            var response = MindustryToolPlugin.apiGateway.execute("SERVERS", request, GetServersMessageResponse.class);
            var servers = response.getServers();
            var options = new ArrayList<>(servers.stream()//
                    .map(server -> HudUtils.option((p) -> onServerChoose(p, server.getId(), server.getName()),
                            "%s [cyan]Players: %s [green]Map: %s".formatted(server.getName(), server.getPlayers(),
                                    server.getMapName() == null ? "[red]Not playing" : server.getMapName())))//
                    .toList());

            options.add(HudUtils.option((p) -> HudUtils.closeFollowDisplay(p, HudUtils.SERVERS_UI), "[red]Close"));

            HudUtils.showFollowDisplay(player, HudUtils.SERVERS_UI, "Servers", "",
                    options.toArray(HudUtils.Option[]::new));
        });
    }

    public void onServerChoose(Player player, String id, String name) {
        HudUtils.closeFollowDisplay(player, HudUtils.SERVERS_UI);
        Utils.executeExpectError(() -> {
            player.sendMessage("Starting server %s, [white]redirection will happen soon".formatted(name));
            var data = MindustryToolPlugin.apiGateway.execute("START_SERVER", id, Integer.class);
            player.sendMessage("Redirecting");
            Call.connect(player.con, Config.SERVER_IP, data);
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
            }
        };

        if (wait)

        {
            lastTask = Timer.schedule(reload, mindustry.net.Administration.Config.roundExtraTime.num());
        } else {
            reload.run();
        }
    }

}
