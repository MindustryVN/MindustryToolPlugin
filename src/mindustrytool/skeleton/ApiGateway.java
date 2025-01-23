package mindustrytool.skeleton;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageResquest;
import mindustrytool.messages.request.StartServerMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;
import mindustrytool.messages.response.StatsMessageResponse;
import mindustrytool.utils.HudUtils;

public interface ApiGateway extends java.rmi.Remote {
    void init(
            Function<PlayerMessageRequest, SetPlayerMessageResquest> onPlayerJoin,
            Supplier<Integer> onTotalPlayers,
            Consumer<String> onChatMessage,
            Consumer<PlayerMessageRequest> onPlayerLeave,
            Function<GetServersMessageRequest, GetServersMessageResponse> onGetServers,
            Function<String, Integer> onStartServer) throws RemoteException;

    void discordMessage(String message) throws RemoteException;

    StatsMessageResponse stats() throws RemoteException;

    StatsMessageResponse detailStats() throws RemoteException;

    boolean serverLoaded(String message) throws RemoteException;

    void startServer(StartServerMessageRequest request) throws RemoteException;

    void setPlayer(SetPlayerMessageResquest request) throws RemoteException;

    class ApiGatewayImpl implements ApiGateway {
        private static final ApiGatewayImpl instance = new ApiGatewayImpl();
        private final String TEMP_SAVE_NAME = "TempSave";

        private Function<PlayerMessageRequest, SetPlayerMessageResquest> onPlayerJoin;
        private Supplier<Integer> onTotalPlayers;
        private Consumer<String> onChatMessage;
        private Consumer<PlayerMessageRequest> onPlayerLeave;
        private Function<GetServersMessageRequest, GetServersMessageResponse> onGetServers;
        private Function<String, Integer> onStartServer;

        public SetPlayerMessageResquest onPlayerJoin(PlayerMessageRequest request) {
            return onPlayerJoin.apply(request);
        }

        public int onTotalPlayers() {
            return onTotalPlayers.get();
        }

        public void onChatMessage(String message) {
            onChatMessage.accept(message);
        }

        public void onPlayerLeave(PlayerMessageRequest request) {
            onPlayerLeave.accept(request);
        }

        public GetServersMessageResponse onGetServers(GetServersMessageRequest request) {
            return onGetServers.apply(request);
        }

        public int onStartServer(String request) {
            return onStartServer.apply(request);
        }

        private ApiGatewayImpl() {
        }

        public static ApiGatewayImpl getInstance() {
            return instance;
        }

        @Override
        public void init(
                Function<PlayerMessageRequest, SetPlayerMessageResquest> onPlayerJoin,
                Supplier<Integer> onTotalPlayers,
                Consumer<String> onChatMessage,
                Consumer<PlayerMessageRequest> onPlayerLeave,
                Function<GetServersMessageRequest, GetServersMessageResponse> onGetServers,
                Function<String, Integer> onStartServer) throws RemoteException {
            this.onPlayerJoin = onPlayerJoin;
            this.onTotalPlayers = onTotalPlayers;
            this.onChatMessage = onChatMessage;
            this.onPlayerLeave = onPlayerLeave;
            this.onGetServers = onGetServers;
            this.onStartServer = onStartServer;
        }

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
            return getDetailStats();
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
                throw new RuntimeException("Can not read map file: " + mapName);
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
        public void setPlayer(SetPlayerMessageResquest request) throws RemoteException {
            String uuid = request.getUuid();
            boolean isAdmin = request.isAdmin();

            PlayerInfo target = Vars.netServer.admins.getInfoOptional(uuid);
            Player playert = Groups.player.find(p -> p.getInfo() == target);

            if (target != null) {
                if (isAdmin) {
                    Vars.netServer.admins.adminPlayer(target.id, playert == null ? target.adminUsid : playert.usid());
                } else {
                    Vars.netServer.admins.unAdminPlayer(target.id);
                }
                if (playert != null)
                    playert.admin = isAdmin;
            } else {
                Log.err("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
            }

            HudUtils.closeFollowDisplay(playert, HudUtils.LOGIN_UI);

            playert.sendMessage("[green]Logged in successfully");
            MindustryToolPlugin.eventHandler.addPlayer(request, playert);
        }

        private StatsMessageResponse getStats() {
            var map = Vars.state.map;

            String mapName = "";

            if (map != null) {
                mapName = map.name();
            }

            List<String> mods = Vars.mods.list().map(mod -> mod.name).list();

            int players = Groups.player.size();

            return new StatsMessageResponse()
                    .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                    .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024)
                    .setPlayers(players)
                    .setMapName(mapName)
                    .setMods(mods)
                    .setHosted(Vars.state.isGame());
        }

        private StatsMessageResponse getDetailStats() {
            var map = Vars.state.map;

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
}
