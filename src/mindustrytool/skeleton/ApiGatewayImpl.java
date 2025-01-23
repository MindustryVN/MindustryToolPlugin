package mindustrytool.skeleton;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
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
import mindustrytool.messages.request.SetPlayerMessageResquet;
import mindustrytool.messages.request.StartServerMessageRequest;
import mindustrytool.messages.response.StatsMessageResponse;
import mindustrytool.utils.HudUtils;

public class ApiGatewayImpl implements ApiGateway {
    private static final String TEMP_SAVE_NAME = "TempSave";

    @Override
    public void discordMessage(String message) throws RemoteException {
        Call.sendMessage(message);
    }

    @Override
    public StatsMessageResponse stats() throws RemoteException {
        return getStats();
    }

    @Override
    public StatsMessageResponse detailStats() throws RemoteException {
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

    @Override
    public boolean serverLoaded(String message) throws RemoteException {
        return true;
    }

    @Override
    public void startServer(StartServerMessageRequest request) throws RemoteException {
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
            result = MapIO.createMap(Vars.customMapDirectory.child(mapName), true);
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

    @Override
    public void setPlayer(SetPlayerMessageResquet request) throws RemoteException {
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
            player.sendMessage("[green]Logged in successfully");
            MindustryToolPlugin.eventHandler.addPlayer(request, player);
        }
    }

    private StatsMessageResponse getStats() {
        Map map = Vars.state.map;
        String mapName = map != null ? map.name() : "";
        List<String> mods = Vars.mods.list().map(mod -> mod.name).list();
        int players = Groups.player.size();

        return new StatsMessageResponse().setRamUsage(Core.app.getJavaHeap() / 1024 / 1024).setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(players).setMapName(mapName).setMods(mods).setHosted(Vars.state.isGame());
    }

}
